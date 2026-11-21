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
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPost
import com.example.data.model.TikTokPostDraft
import com.example.ui.components.BottomNavTab
import com.example.ui.components.NotificationCenterTab
import com.example.ui.notifications.SecurityNotificationHelper
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DashboardSection(val label: String) {
    INVENTORY("Inventory"),
    TIKTOK_VIDEOS("TikTok Videos")
}

enum class ActivePortalSheet {
    NONE,
    SETTINGS,
    NOTIFICATIONS,
    SHOWROOM,
    ADMIN_ACCESS
}

sealed interface TikTokFormMode {
    data object Add : TikTokFormMode
    data class Edit(val post: TikTokPost) : TikTokFormMode
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

    private val _activeSheet = MutableStateFlow(ActivePortalSheet.NONE)
    val activeSheet: StateFlow<ActivePortalSheet> = _activeSheet.asStateFlow()

    private val _notificationTab = MutableStateFlow(NotificationCenterTab.LOGIN_ALERTS)
    val notificationTab: StateFlow<NotificationCenterTab> = _notificationTab.asStateFlow()

    private val _tikTokFormMode = MutableStateFlow<TikTokFormMode?>(null)
    val tikTokFormMode: StateFlow<TikTokFormMode?> = _tikTokFormMode.asStateFlow()

    private val _deleteTikTokCandidate = MutableStateFlow<TikTokPost?>(null)
    val deleteTikTokCandidate: StateFlow<TikTokPost?> = _deleteTikTokCandidate.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _isChangingPassword = MutableStateFlow(false)
    val isChangingPassword: StateFlow<Boolean> = _isChangingPassword.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val isBootstrapOwner: Boolean
        get() = repository.isBootstrapOwner()

    val sessions: StateFlow<List<ActiveDeviceSession>> = securityRepository.sessions
    val loginAlerts: StateFlow<List<LoginSecurityAlert>> = securityRepository.alerts
    val currentLoginAtMillis: StateFlow<Long> = securityRepository.currentLoginAtMillis
    val isCurrentSessionRevoked: StateFlow<Boolean> = securityRepository.isCurrentSessionRevoked
    val securitySyncError: StateFlow<String?> = securityRepository.cloudSyncError
    val isRefreshingSessions: StateFlow<Boolean> = securityRepository.isRefreshing

    val showroomConfig: StateFlow<ShowroomConfig> = repository.observeShowroomConfig()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing showroom config", firestoreError ?: error)
            emit(ShowroomConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShowroomConfig()
        )

    val tikTokPosts: StateFlow<List<TikTokPost>> = repository.observeTikTokPosts()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing tiktok posts", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val inquiriesState: StateFlow<InquiriesState> = repository.observeInquiries()
        .onEach { state ->
            if (state is InquiriesState.Success) {
                SecurityNotificationHelper.showNewInquiriesIfNeeded(appContext, state.inquiries)
            }
        }
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

    init {
        inquiriesState.launchIn(viewModelScope)
    }

    fun registerAuthenticatedSession(user: FirebaseUser) {
        securityRepository.startObservingCloudSecurity(
            uid = user.uid,
            onNewAlert = { alert ->
                SecurityNotificationHelper.showLoginAlertNotification(appContext, alert)
            }
        )
        viewModelScope.launch {
            securityRepository.registerCurrentDeviceSignIn(user) { alert ->
                SecurityNotificationHelper.showLoginAlertNotification(appContext, alert)
            }
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

    fun openSheet(sheet: ActivePortalSheet) {
        _activeSheet.value = sheet
    }

    fun openNotifications(tab: NotificationCenterTab = _notificationTab.value) {
        _notificationTab.value = tab
        _activeSheet.value = ActivePortalSheet.NOTIFICATIONS
    }

    fun selectNotificationTab(tab: NotificationCenterTab) {
        _notificationTab.value = tab
    }

    fun dismissSheet() {
        _activeSheet.value = ActivePortalSheet.NONE
    }

    fun openAddTikTokForm() {
        _tikTokFormMode.value = TikTokFormMode.Add
    }

    fun openEditTikTokForm(post: TikTokPost) {
        _tikTokFormMode.value = TikTokFormMode.Edit(post)
    }

    fun dismissTikTokForm() {
        _tikTokFormMode.value = null
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

    fun deactivateOtherDevice(sessionId: String): Result<Unit> {
        val result = securityRepository.deactivateOtherSession(sessionId)
        result.onSuccess {
            _snackbarMessage.value = "Removed active device session"
        }
        return result
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        onComplete: (Result<String>) -> Unit
    ) {
        if (_isChangingPassword.value) return
        viewModelScope.launch {
            _isChangingPassword.value = true
            val result = securityRepository.changePasswordWith24hGuard(
                oldPassword = oldPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword
            )
            _isChangingPassword.value = false
            onComplete(result)
        }
    }

    suspend fun prepareSignOut() {
        securityRepository.markCurrentSessionSignedOut()
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
                _activeSheet.value = ActivePortalSheet.NONE
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
            val result = if (editingPostId == null) {
                repository.addTikTokPost(draft).map { }
            } else {
                repository.updateTikTokPost(editingPostId, draft)
            }
            _isMutating.value = false
            result.onSuccess {
                _tikTokFormMode.value = null
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

    fun pendingAlertsCount(alerts: List<LoginSecurityAlert>): Int =
        alerts.count { it.decision == LoginAlertDecision.PENDING }

    private companion object {
        const val TAG = "PortalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
