package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AdminAccessState
import com.example.data.firebase.InquiriesState
import com.example.data.firebase.PortalRepository
import com.example.data.firebase.SecuritySessionRepository
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.AuthenticatorStatus
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.PendingAuthenticatorSetup
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.data.model.WebsiteVisitorStats
import com.example.ui.components.BottomNavTab
import com.example.ui.components.NotificationCenterTab
import com.example.ui.notifications.SecurityNotificationHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DashboardSection(val label: String) {
    INVENTORY("Inventory"),
    TIKTOK_VIDEOS("TikTok Videos")
}

class PortalViewModel(
    private val appContext: Context,
    private val repository: PortalRepository,
    private val securityRepository: SecuritySessionRepository
) : ViewModel() {

    private val _bottomNavTab = MutableStateFlow(BottomNavTab.HOME)
    val bottomNavTab: StateFlow<BottomNavTab> = _bottomNavTab.asStateFlow()

    private val _dashboardSection = MutableStateFlow(DashboardSection.INVENTORY)
    val dashboardSection: StateFlow<DashboardSection> = _dashboardSection.asStateFlow()

    private val _notificationTab = MutableStateFlow(NotificationCenterTab.LOGIN_ALERTS)
    val notificationTab: StateFlow<NotificationCenterTab> = _notificationTab.asStateFlow()

    private val _deleteTikTokCandidate = MutableStateFlow<TikTokPost?>(null)
    val deleteTikTokCandidate: StateFlow<TikTokPost?> = _deleteTikTokCandidate.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _isChangingPassword = MutableStateFlow(false)
    val isChangingPassword: StateFlow<Boolean> = _isChangingPassword.asStateFlow()

    private val _isSigningOutAll = MutableStateFlow(false)
    val isSigningOutAll: StateFlow<Boolean> = _isSigningOutAll.asStateFlow()

    private val _isDeletingPreviousAlerts = MutableStateFlow(false)
    val isDeletingPreviousAlerts: StateFlow<Boolean> = _isDeletingPreviousAlerts.asStateFlow()

    private val _isMutatingAuthenticator = MutableStateFlow(false)
    val isMutatingAuthenticator: StateFlow<Boolean> = _isMutatingAuthenticator.asStateFlow()

    private val _isRefreshingVisitorStats = MutableStateFlow(false)
    val isRefreshingVisitorStats: StateFlow<Boolean> = _isRefreshingVisitorStats.asStateFlow()

    private val _manualVisitorStats = MutableStateFlow<WebsiteVisitorStats?>(null)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val isBootstrapOwner: Boolean
        get() = repository.isBootstrapOwner()

    val sessions: StateFlow<List<ActiveDeviceSession>> = securityRepository.sessions
    val loginAlerts: StateFlow<List<LoginSecurityAlert>> = securityRepository.alerts
    val readAlertIds: StateFlow<Set<String>> = securityRepository.readAlertIds
    val currentLoginAtMillis: StateFlow<Long> = securityRepository.currentLoginAtMillis
    val isCurrentSessionRevoked: StateFlow<Boolean> = securityRepository.isCurrentSessionRevoked
    val securitySyncError: StateFlow<String?> = securityRepository.cloudSyncError
    val isRefreshingSessions: StateFlow<Boolean> = securityRepository.isRefreshing
    val authenticatorStatus: StateFlow<AuthenticatorStatus> = securityRepository.authenticatorStatus

    val currentSessionId: String
        get() = securityRepository.currentSessionId()

    val showroomConfig: StateFlow<ShowroomConfig> = repository.observeShowroomConfig()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing showroom config", error)
            emit(ShowroomConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShowroomConfig()
        )

    val tikTokPosts: StateFlow<List<TikTokPost>> = repository.observeTikTokPosts()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing tiktok posts", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val inquiriesState: StateFlow<InquiriesState> = repository.observeInquiries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = InquiriesState.Loading
        )

    val adminAccessState: StateFlow<AdminAccessState> = repository.observeAdminAccess()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AdminAccessState.Loading
        )

    val websiteVisitorStats: StateFlow<WebsiteVisitorStats> = kotlinx.coroutines.flow.combine(
        repository.observeWebsiteVisitorStats(),
        _manualVisitorStats
    ) { live, manual ->
        manual ?: live
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Error observing website visitor stats", error)
        emit(WebsiteVisitorStats(isLoaded = true))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WebsiteVisitorStats()
    )

    fun refreshWebsiteVisitorStats() {
        if (_isRefreshingVisitorStats.value) return
        viewModelScope.launch {
            _isRefreshingVisitorStats.value = true
            repository.refreshWebsiteVisitorStats()
                .onSuccess { stats ->
                    _manualVisitorStats.value = stats
                }
            _isRefreshingVisitorStats.value = false
        }
    }

    fun refreshPortalDataOnResume() {
        viewModelScope.launch {
            repository.refreshShowroomData()
            repository.refreshInquiries()
            repository.refreshWebsiteVisitorStats()
                .onSuccess { stats ->
                    _manualVisitorStats.value = stats
                }
        }
    }

    fun registerAuthenticatedSession(
        user: FirebaseUser,
        verifiedWithAuthenticator: Boolean = false
    ) {
        viewModelScope.launch {
            // Register this phone's session as ACTIVE in Firestore FIRST before attaching
            // snapshot listeners so a stale pre-login "revoked" doc never kicks the user out twice.
            securityRepository.registerCurrentDeviceSignIn(
                user = user,
                verifiedWithAuthenticator = verifiedWithAuthenticator
            ) { alert ->
                SecurityNotificationHelper.showLoginAlertNotification(appContext, alert)
            }
            securityRepository.startObservingCloudSecurity(
                uid = user.uid,
                onNewAlert = { alert ->
                    SecurityNotificationHelper.showLoginAlertNotification(appContext, alert)
                }
            )
        }
    }

    fun refreshSessionHeartbeat() {
        viewModelScope.launch {
            securityRepository.refreshHeartbeat()
        }
    }

    fun refreshActiveSessions() {
        viewModelScope.launch {
            securityRepository.refreshFromServer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        securityRepository.stopObservingCloudSecurity()
    }

    fun selectBottomNavTab(tab: BottomNavTab) {
        _bottomNavTab.value = tab
    }

    fun selectDashboardSection(section: DashboardSection) {
        _dashboardSection.value = section
    }

    fun selectNotificationTab(tab: NotificationCenterTab) {
        _notificationTab.value = tab
    }

    fun requestDeleteTikTokPost(post: TikTokPost) {
        _deleteTikTokCandidate.value = post
    }

    fun dismissDeleteTikTokDialog() {
        _deleteTikTokCandidate.value = null
    }

    fun confirmLoginAlertIsMe(alertId: String) {
        securityRepository.confirmAlertIsMe(alertId)
        _snackbarMessage.value = "Confirmed login — session stays active"
    }

    fun rejectLoginAlertNotMe(alertId: String) {
        securityRepository.rejectAlertNotMe(alertId)
        _snackbarMessage.value = "Unrecognized session logged out immediately"
    }

    fun markAllLoginAlertsRead() {
        val markedIds = securityRepository.markAllAlertsRead()
        if (markedIds.isNotEmpty()) {
            SecurityNotificationHelper.clearLoginAlertNotifications(appContext, markedIds)
            _snackbarMessage.value = "All login alerts marked as read"
        }
    }

    fun deletePreviousLoginAlertsExceptCurrent() {
        if (_isDeletingPreviousAlerts.value) return
        viewModelScope.launch {
            _isDeletingPreviousAlerts.value = true
            val result = securityRepository.deletePreviousAlertsExceptCurrent()
            _isDeletingPreviousAlerts.value = false
            result.onSuccess { deletedCount ->
                _snackbarMessage.value = if (deletedCount > 0) {
                    "Deleted $deletedCount previous login notification(s); kept current login status"
                } else {
                    "Only your current login status notification remains"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to delete previous notifications"
            }
        }
    }

    fun deactivateOtherDevice(
        sessionId: String,
        totpCode: String = ""
    ): Result<Unit> {
        val result = securityRepository.deactivateOtherSession(
            sessionId = sessionId,
            totpCode = totpCode
        )
        result.onSuccess {
            _snackbarMessage.value = "Removed active device session"
        }
        return result
    }

    fun createPendingAuthenticatorSetup(): Result<PendingAuthenticatorSetup> =
        securityRepository.createPendingAuthenticatorSetup()

    fun enableAuthenticator(
        secretBase32: String,
        verificationCode: String,
        onComplete: (Result<String>) -> Unit
    ) {
        if (_isMutatingAuthenticator.value) return
        viewModelScope.launch {
            _isMutatingAuthenticator.value = true
            val result = securityRepository.enableAuthenticator(
                secretBase32 = secretBase32,
                verificationCode = verificationCode
            )
            _isMutatingAuthenticator.value = false
            result.onSuccess { _snackbarMessage.value = "Google Authenticator enabled" }
            onComplete(result)
        }
    }

    fun disableAuthenticator(
        verificationCode: String,
        onComplete: (Result<String>) -> Unit
    ) {
        if (_isMutatingAuthenticator.value) return
        viewModelScope.launch {
            _isMutatingAuthenticator.value = true
            val result = securityRepository.disableAuthenticator(verificationCode = verificationCode)
            _isMutatingAuthenticator.value = false
            result.onSuccess { _snackbarMessage.value = "Google Authenticator disabled" }
            onComplete(result)
        }
    }

    fun setRequireAuthenticatorOnLogin(
        requireOnLogin: Boolean,
        verificationCode: String = "",
        onComplete: (Result<String>) -> Unit
    ) {
        if (_isMutatingAuthenticator.value) return
        viewModelScope.launch {
            _isMutatingAuthenticator.value = true
            val result = securityRepository.setRequireAuthenticatorOnLogin(
                requireOnLogin = requireOnLogin,
                verificationCode = verificationCode
            )
            _isMutatingAuthenticator.value = false
            result.onSuccess {
                _snackbarMessage.value = if (requireOnLogin) {
                    "Authentication code for login enabled"
                } else {
                    "Authentication code for login disabled"
                }
            }
            onComplete(result)
        }
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        totpCode: String = "",
        onComplete: (Result<String>) -> Unit
    ) {
        if (_isChangingPassword.value) return
        viewModelScope.launch {
            _isChangingPassword.value = true
            val result = securityRepository.changePasswordWith24hGuard(
                oldPassword = oldPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword,
                totpCode = totpCode
            )
            _isChangingPassword.value = false
            onComplete(result)
        }
    }

    suspend fun prepareSignOut() {
        securityRepository.markCurrentSessionSignedOut()
    }

    suspend fun removeAllOtherActiveDevices(totpCode: String = ""): Result<Int> {
        if (_isSigningOutAll.value) return Result.success(0)
        _isSigningOutAll.value = true
        return try {
            val result = securityRepository.revokeAllOtherActiveSessions(totpCode = totpCode)
            result.onSuccess { count ->
                _snackbarMessage.value = if (count > 0) {
                    "Removed $count other logged-in device(s) across app & website. This device stays signed in."
                } else {
                    "All other logged-in devices (app & website) removed. This device stays signed in."
                }
            }
            result
        } finally {
            _isSigningOutAll.value = false
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun saveShowroomConfig(config: ShowroomConfig, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveShowroomConfig(config)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = "Contact, social media & location synced to website"
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save contact & location"
            }
        }
    }

    fun saveTikTokPost(
        editingPostId: String?,
        draft: TikTokPostDraft,
        onSuccess: () -> Unit = {}
    ) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val normalizedUrl = com.example.data.network.TikTokThumbnailResolver
                .normalizeTikTokUrl(draft.tiktokUrl)
                ?: draft.tiktokUrl.trim()
            val resolvedCover = draft.coverImageUrl.trim().ifBlank {
                if (normalizedUrl.isNotBlank()) {
                    kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                        com.example.data.network.TikTokThumbnailResolver
                            .resolveMetadata(normalizedUrl)
                            ?.thumbnailUrl
                    }.orEmpty()
                } else {
                    ""
                }
            }
            val enrichedDraft = draft.copy(
                tiktokUrl = normalizedUrl,
                coverImageUrl = resolvedCover
            )
            val result = if (editingPostId == null) {
                repository.addTikTokPost(enrichedDraft).map { }
            } else {
                repository.updateTikTokPost(editingPostId, enrichedDraft)
            }
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = if (editingPostId == null) {
                    "Published TikTok video link to website"
                } else {
                    "Updated TikTok video link"
                }
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save TikTok video link"
            }
        }
    }

    fun confirmDeleteTikTokPost() {
        val candidate = _deleteTikTokCandidate.value ?: return
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteTikTokPost(candidate.id)
            _isMutating.value = false
            _deleteTikTokCandidate.value = null
            result.onSuccess {
                _snackbarMessage.value = "Removed TikTok video link"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to delete TikTok video link"
            }
        }
    }

    fun saveAdminEmails(emails: List<String>, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveAdminEmails(emails)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = "Updated allowed admin emails"
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update admin emails"
            }
        }
    }

    fun grantAdminAccessFromBlockedAlert(email: String, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        val normalized = email.trim().lowercase(java.util.Locale.US)
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.grantAdminAccessEmail(normalized)
            _isMutating.value = false
            result.onSuccess { newlyAdded ->
                _snackbarMessage.value = if (newlyAdded) {
                    "Granted admin access to $normalized"
                } else {
                    "$normalized already has admin access"
                }
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to grant admin access"
            }
        }
    }

    fun revokeAdminAccessFromAlert(email: String, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        val normalized = email.trim().lowercase(java.util.Locale.US)
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.revokeAdminAccessEmail(normalized)
            _isMutating.value = false
            result.onSuccess { removed ->
                _snackbarMessage.value = if (removed) {
                    "Revoked admin access for $normalized"
                } else {
                    "$normalized is already blocked"
                }
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to revoke admin access"
            }
        }
    }

    fun removeUserFromLoggedIn(email: String, sessionId: String = "", onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        val normalized = email.trim().lowercase(java.util.Locale.US)
        viewModelScope.launch {
            _isMutating.value = true
            val result = securityRepository.revokeActiveSessionsForEmail(
                email = normalized,
                sessionId = sessionId
            )
            _isMutating.value = false
            result.onSuccess { count ->
                _snackbarMessage.value = if (count > 0) {
                    "Removed $count active logged-in session(s) for ${normalized.ifBlank { "this device" }}"
                } else {
                    "No other active logged-in sessions found for ${normalized.ifBlank { "this account" }}"
                }
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to remove logged-in session"
            }
        }
    }

    fun unreadAlertsCount(
        alerts: List<LoginSecurityAlert>,
        readIds: Set<String> = readAlertIds.value
    ): Int = alerts.count { it.isUnread(readIds) }

    fun pendingAlertsCount(alerts: List<LoginSecurityAlert>): Int =
        unreadAlertsCount(alerts, readAlertIds.value)

    private companion object {
        const val TAG = "PortalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
