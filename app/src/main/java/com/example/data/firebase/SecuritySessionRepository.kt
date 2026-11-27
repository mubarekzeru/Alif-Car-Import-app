package com.example.data.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.auth.AdminAuthorizationPolicy
import com.example.auth.LoginTwoFactorStore
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.AdminDeviceStatus
import com.example.data.model.AuthenticatorStatus
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.PendingAuthenticatorSetup
import com.example.data.model.SecurityLockHelper
import com.example.data.security.TotpAuthenticator
import com.example.ui.notifications.SecurityNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class LocalAuthenticatorConfig(
    val enabled: Boolean = false,
    val requireOnLogin: Boolean = false,
    val secretBase32: String = "",
    val accountLabel: String = "",
    val updatedByEmail: String = "",
    val updatedAtMillis: Long = 0L
)

/**
 * Local device session, alert history, Authenticator 2FA, and Firebase Auth password manager.
 * Operates completely without Firestore so the app never incurs Firestore read/write quota.
 */
class SecuritySessionRepository(
    context: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val localDeviceId: String = getOrCreateDeviceId()

    private val _sessions = MutableStateFlow<List<ActiveDeviceSession>>(emptyList())
    val sessions: StateFlow<List<ActiveDeviceSession>> = _sessions.asStateFlow()

    private val _alerts = MutableStateFlow<List<LoginSecurityAlert>>(emptyList())
    val alerts: StateFlow<List<LoginSecurityAlert>> = _alerts.asStateFlow()

    private val _readAlertIds = MutableStateFlow<Set<String>>(emptySet())
    val readAlertIds: StateFlow<Set<String>> = _readAlertIds.asStateFlow()

    private val _currentLoginAtMillis = MutableStateFlow(loadStoredLoginAt())
    val currentLoginAtMillis: StateFlow<Long> = _currentLoginAtMillis.asStateFlow()

    private val _isCurrentSessionRevoked = MutableStateFlow(false)
    val isCurrentSessionRevoked: StateFlow<Boolean> = _isCurrentSessionRevoked.asStateFlow()

    private val _cloudSyncError = MutableStateFlow<String?>(null)
    val cloudSyncError: StateFlow<String?> = _cloudSyncError.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _authenticatorStatus = MutableStateFlow(AuthenticatorStatus(isLoaded = true))
    val authenticatorStatus: StateFlow<AuthenticatorStatus> = _authenticatorStatus.asStateFlow()

    @Volatile
    private var currentAuthTimeMillis: Long = 0L

    @Volatile
    private var activeTotpSecret: String = ""

    @Volatile
    private var failedTotpAttempts: Int = 0

    @Volatile
    private var totpLockoutUntilMillis: Long = 0L

    init {
        loadFromLocalPrefs()
    }

    fun currentSessionId(uid: String? = auth.currentUser?.uid): String {
        val cleanUid = sanitizeIdPart(uid).ifBlank { "anon" }
        val cleanDevice = sanitizeIdPart(localDeviceId).ifBlank { "device" }
        return "sess_${cleanUid.take(10)}_${cleanDevice.take(12)}"
    }

    fun startObservingCloudSecurity(
        uid: String,
        onNewAlert: (LoginSecurityAlert) -> Unit
    ) {
        // Local-only security state — intentionally does not attach any Firestore snapshot listeners.
        loadFromLocalPrefs()
        _cloudSyncError.value = null
    }

    fun stopObservingCloudSecurity() {
        // No remote listeners to detach.
    }

    suspend fun registerCurrentDeviceSignIn(
        user: FirebaseUser,
        verifiedWithAuthenticator: Boolean = false,
        onNewLoginAlert: (LoginSecurityAlert) -> Unit
    ) {
        _isCurrentSessionRevoked.value = false
        val sessionId = currentSessionId(user.uid)
        val deviceName = buildDeviceDisplayName()
        val email = user.email.orEmpty().trim()
        val now = System.currentTimeMillis()
        val authSignInTs = user.metadata?.lastSignInTimestamp ?: 0L
        val tokenAuthTimeMillis = runCatching {
            user.getIdToken(false).await().authTimestamp * 1000L
        }.getOrDefault(0L)
        currentAuthTimeMillis = tokenAuthTimeMillis.takeIf { it > 0L } ?: authSignInTs

        val lastRecordedAuthTs = prefs.getLong(KEY_LAST_AUTH_SIGNIN_TS, 0L)
        val lastRecordedUid = prefs.getString(KEY_LAST_UID, null)

        val isFreshSignIn = (lastRecordedUid != user.uid) ||
            (authSignInTs > 0L && authSignInTs != lastRecordedAuthTs)

        val loginAt = if (isFreshSignIn || _currentLoginAtMillis.value <= 0L) {
            now
        } else {
            _currentLoginAtMillis.value
        }

        if (isFreshSignIn) {
            prefs.edit()
                .putLong(KEY_LOGIN_AT_MILLIS, loginAt)
                .putLong(KEY_LAST_AUTH_SIGNIN_TS, authSignInTs)
                .putString(KEY_LAST_UID, user.uid)
                .apply()
            _currentLoginAtMillis.value = loginAt
        } else if (_currentLoginAtMillis.value <= 0L) {
            prefs.edit().putLong(KEY_LOGIN_AT_MILLIS, loginAt).apply()
            _currentLoginAtMillis.value = loginAt
        }

        val initialLocation = fallbackLocaleLocation()
        val existing = _sessions.value.firstOrNull { it.id == sessionId }
        val resolvedLocation = existing?.location?.takeIf {
            it.isNotBlank() && !it.startsWith("Timezone:")
        } ?: initialLocation

        val updatedSession = ActiveDeviceSession(
            id = sessionId,
            deviceId = localDeviceId,
            deviceName = deviceName,
            platform = "Mobile App",
            email = email,
            location = resolvedLocation,
            loginAtMillis = loginAt,
            lastActiveMillis = now,
            status = AdminDeviceStatus.ACTIVE,
            isCurrentDevice = true,
            browser = "Mobile App"
        )
        upsertLocalSession(updatedSession)

        var createdAlertId: String? = null
        if (isFreshSignIn) {
            val isOwnerAuthVerified = verifiedWithAuthenticator &&
                AdminAuthorizationPolicy.isBootstrapOwnerEmail(email)
            val alert = LoginSecurityAlert(
                id = "alert_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                sessionId = sessionId,
                email = email,
                deviceName = deviceName,
                platform = "Mobile App",
                location = resolvedLocation,
                timestampMillis = now,
                decision = if (isOwnerAuthVerified) {
                    LoginAlertDecision.CONFIRMED_ME
                } else {
                    LoginAlertDecision.PENDING
                },
                verifiedWithAuthenticator = isOwnerAuthVerified
            )
            createdAlertId = alert.id
            prependLocalAlert(alert)
            if (!isOwnerAuthVerified) {
                onNewLoginAlert(alert)
            }
        }

        // Enrich approximate city/country in background without GPS permissions
        val ipLocation = fetchIpCityCountry()
        if (ipLocation != null && ipLocation != updatedSession.location) {
            val enrichedSession = updatedSession.copy(
                location = ipLocation,
                lastActiveMillis = System.currentTimeMillis()
            )
            upsertLocalSession(enrichedSession)
            if (createdAlertId != null) {
                _alerts.value = _alerts.value.map { alert ->
                    if (alert.id == createdAlertId) alert.copy(location = ipLocation) else alert
                }
                saveLocalPrefs()
            }
        }
    }

    suspend fun refreshHeartbeat() {
        val user = auth.currentUser ?: return
        val sessionId = currentSessionId(user.uid)
        val current = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        upsertLocalSession(
            current.copy(
                lastActiveMillis = System.currentTimeMillis(),
                status = AdminDeviceStatus.ACTIVE
            )
        )
    }

    suspend fun refreshFromServer() {
        _isRefreshing.value = true
        try {
            loadFromLocalPrefs()
            refreshHeartbeat()
            _cloudSyncError.value = null
        } finally {
            _isRefreshing.value = false
        }
    }

    fun confirmAlertIsMe(alertId: String) {
        _alerts.value = _alerts.value.map { alert ->
            if (alert.id == alertId) {
                alert.copy(decision = LoginAlertDecision.CONFIRMED_ME)
            } else alert
        }
        markAlertRead(alertId)
        saveLocalPrefs()
    }

    fun rejectAlertNotMe(alertId: String) {
        var targetSessionId: String? = null
        _alerts.value = _alerts.value.map { alert ->
            if (alert.id == alertId) {
                targetSessionId = alert.sessionId
                alert.copy(decision = LoginAlertDecision.REJECTED_NOT_ME)
            } else alert
        }
        markAlertRead(alertId)

        val sessionIdToRevoke = targetSessionId
        if (!sessionIdToRevoke.isNullOrBlank()) {
            revokeSessionImmediate(sessionIdToRevoke)
        } else {
            saveLocalPrefs()
        }
    }

    fun markAlertRead(alertId: String) {
        if (alertId.isBlank()) return
        val updated = (_readAlertIds.value + alertId).toList().takeLast(MAX_STORED_READ_ALERT_IDS).toSet()
        _readAlertIds.value = updated
        prefs.edit().putStringSet(KEY_READ_ALERT_IDS, updated).apply()
    }

    fun markAllAlertsRead(): List<String> {
        val allIds = _alerts.value.map { it.id }.filter { it.isNotBlank() }
        if (allIds.isEmpty()) return emptyList()
        val updated = (_readAlertIds.value + allIds).toList().takeLast(MAX_STORED_READ_ALERT_IDS).toSet()
        _readAlertIds.value = updated
        prefs.edit().putStringSet(KEY_READ_ALERT_IDS, updated).apply()
        return allIds
    }

    suspend fun deletePreviousAlertsExceptCurrent(): Result<Int> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before deleting notifications."))
        val mySessionId = currentSessionId(currentUser.uid)

        val keepAlertId = LoginSecurityAlert.findCurrentStatusAlertId(_alerts.value, mySessionId)
        val deletedIds = _alerts.value
            .filter { it.id != keepAlertId }
            .map { it.id }
            .toSet()

        _alerts.value = _alerts.value.filter { it.id == keepAlertId }
        val updatedReadIds = if (keepAlertId != null && _readAlertIds.value.contains(keepAlertId)) {
            setOf(keepAlertId)
        } else {
            emptySet()
        }
        _readAlertIds.value = updatedReadIds
        prefs.edit().putStringSet(KEY_READ_ALERT_IDS, updatedReadIds).apply()
        saveLocalPrefs()

        if (deletedIds.isNotEmpty()) {
            SecurityNotificationHelper.clearLoginAlertNotifications(appContext, deletedIds)
        }
        return Result.success(deletedIds.size)
    }

    fun deactivateOtherSession(
        sessionId: String,
        totpCode: String = ""
    ): Result<Unit> {
        val target = _sessions.value.firstOrNull { it.id == sessionId }
            ?: return Result.failure(IllegalStateException("Device session not found."))

        if (target.isCurrentDevice || target.deviceId == localDeviceId) {
            return Result.failure(
                IllegalStateException(
                    "This is your current device. Use Sign Out in Settings if you want to log out here."
                )
            )
        }

        val loginAt = _currentLoginAtMillis.value
        if (!SecurityLockHelper.isUnlockedAfter24Hours(loginAt)) {
            val remaining = SecurityLockHelper.formatUnlockCountdown(
                SecurityLockHelper.remainingLockMillis(loginAt)
            )
            return Result.failure(
                IllegalStateException("Access will unlock after 24 hours ($remaining).")
            )
        }

        val totpCheck = verifyProtectedActionTotp(totpCode, "remove an active device")
        if (totpCheck.isFailure) return totpCheck

        revokeSessionImmediate(sessionId)
        return Result.success(Unit)
    }

    fun createPendingAuthenticatorSetup(): Result<PendingAuthenticatorSetup> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before setting up Authenticator."))
        val email = user.email?.trim()?.takeIf { it.isNotEmpty() } ?: "admin"
        val secret = TotpAuthenticator.generateSecretBase32()
        val uri = TotpAuthenticator.buildOtpAuthUri(
            accountEmail = email,
            secretBase32 = secret,
            issuer = TotpAuthenticator.DEFAULT_ISSUER
        )
        return Result.success(
            PendingAuthenticatorSetup(
                secretBase32 = secret,
                formattedSecret = TotpAuthenticator.formatSecretForDisplay(secret),
                otpAuthUri = uri,
                accountEmail = email,
                issuer = TotpAuthenticator.DEFAULT_ISSUER
            )
        )
    }

    suspend fun enableAuthenticator(
        secretBase32: String,
        verificationCode: String
    ): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before enabling Authenticator."))
        val cleanSecret = TotpAuthenticator.normalizeBase32(secretBase32)
        if (!TotpAuthenticator.isValidSecretBase32(cleanSecret)) {
            return Result.failure(IllegalArgumentException("Setup key is invalid. Generate a fresh Authenticator link and try again."))
        }
        val cleanCode = TotpAuthenticator.sanitizeCodeInput(verificationCode)
        if (cleanCode.length != TotpAuthenticator.DEFAULT_DIGITS) {
            return Result.failure(IllegalArgumentException("Paste or enter the 6-digit code from Google Authenticator."))
        }
        if (!TotpAuthenticator.verifyCode(cleanSecret, cleanCode)) {
            return Result.failure(IllegalArgumentException("Incorrect 6-digit code. Make sure you added this link/key to Google Authenticator and enter the current code."))
        }

        val email = user.email.orEmpty().trim().take(254)
        val now = System.currentTimeMillis()
        persistLocalAuthenticator(
            LocalAuthenticatorConfig(
                enabled = true,
                requireOnLogin = true,
                secretBase32 = cleanSecret,
                accountLabel = email,
                updatedByEmail = email,
                updatedAtMillis = now
            )
        )
        activeTotpSecret = cleanSecret
        failedTotpAttempts = 0
        totpLockoutUntilMillis = 0L
        _authenticatorStatus.value = AuthenticatorStatus(
            enabled = true,
            requireOnLogin = true,
            accountLabel = email,
            updatedByEmail = email,
            updatedAtMillis = now,
            isLoaded = true
        )
        val sessionAuthSeconds = (currentAuthTimeMillis / 1000L).takeIf { it > 0L }
            ?: ((user.metadata?.lastSignInTimestamp ?: 0L) / 1000L)
        if (sessionAuthSeconds > 0L) {
            LoginTwoFactorStore.markSessionVerified(appContext, user.uid, sessionAuthSeconds)
        }
        return Result.success("Google Authenticator enabled on this device.")
    }

    suspend fun disableAuthenticator(verificationCode: String): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before disabling Authenticator."))

        if (!_authenticatorStatus.value.enabled) {
            return Result.success("Google Authenticator is already disabled.")
        }

        val totpCheck = verifyProtectedActionTotp(verificationCode, "disable Google Authenticator")
        if (totpCheck.isFailure) {
            return Result.failure(
                totpCheck.exceptionOrNull()
                    ?: IllegalArgumentException("Valid 6-digit Google Authenticator code is required to disable.")
            )
        }

        val email = user.email.orEmpty().trim().take(254)
        val now = System.currentTimeMillis()
        persistLocalAuthenticator(
            LocalAuthenticatorConfig(
                enabled = false,
                requireOnLogin = false,
                secretBase32 = "",
                accountLabel = email,
                updatedByEmail = email,
                updatedAtMillis = now
            )
        )
        activeTotpSecret = ""
        failedTotpAttempts = 0
        totpLockoutUntilMillis = 0L
        _authenticatorStatus.value = AuthenticatorStatus(
            enabled = false,
            requireOnLogin = false,
            accountLabel = email,
            updatedByEmail = email,
            updatedAtMillis = now,
            isLoaded = true
        )
        return Result.success("Google Authenticator disabled.")
    }

    suspend fun setRequireAuthenticatorOnLogin(
        requireOnLogin: Boolean,
        verificationCode: String = ""
    ): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before updating login authentication settings."))

        val currentStatus = _authenticatorStatus.value
        val secret = activeTotpSecret.trim()
        if (!currentStatus.enabled || !TotpAuthenticator.isValidSecretBase32(secret)) {
            return Result.failure(
                IllegalStateException("Enable Google Authenticator first before changing login code verification.")
            )
        }

        if (currentStatus.requireOnLogin == requireOnLogin) {
            return Result.success(
                if (requireOnLogin) {
                    "Authentication code for login is already enabled."
                } else {
                    "Authentication code for login is already disabled."
                }
            )
        }

        if (!requireOnLogin) {
            val totpCheck = verifyProtectedActionTotp(
                verificationCode,
                "disable authentication code for login"
            )
            if (totpCheck.isFailure) {
                return Result.failure(
                    totpCheck.exceptionOrNull()
                        ?: IllegalArgumentException("Valid 6-digit Google Authenticator code is required.")
                )
            }
        }

        val email = user.email.orEmpty().trim().take(254)
        val now = System.currentTimeMillis()
        persistLocalAuthenticator(
            LocalAuthenticatorConfig(
                enabled = true,
                requireOnLogin = requireOnLogin,
                secretBase32 = secret,
                accountLabel = currentStatus.accountLabel.ifBlank { email }.take(254),
                updatedByEmail = email,
                updatedAtMillis = now
            )
        )
        _authenticatorStatus.value = currentStatus.copy(
            requireOnLogin = requireOnLogin,
            updatedByEmail = email,
            updatedAtMillis = now,
            isLoaded = true
        )
        return if (requireOnLogin) {
            val sessionAuthSeconds = (currentAuthTimeMillis / 1000L).takeIf { it > 0L }
                ?: ((user.metadata?.lastSignInTimestamp ?: 0L) / 1000L)
            if (sessionAuthSeconds > 0L) {
                LoginTwoFactorStore.markSessionVerified(appContext, user.uid, sessionAuthSeconds)
            }
            Result.success("Authentication code for login enabled.")
        } else {
            Result.success("Authentication code for login disabled.")
        }
    }

    private fun verifyProtectedActionTotp(
        totpCode: String,
        actionDescription: String
    ): Result<Unit> {
        if (!_authenticatorStatus.value.enabled) {
            return Result.success(Unit)
        }

        val now = System.currentTimeMillis()
        if (now < totpLockoutUntilMillis) {
            val secondsLeft = ((totpLockoutUntilMillis - now + 999L) / 1000L).coerceAtLeast(1L)
            return Result.failure(
                IllegalStateException("Too many incorrect authenticator codes. Try again in ${secondsLeft}s.")
            )
        }

        val cleanCode = TotpAuthenticator.sanitizeCodeInput(totpCode)
        if (cleanCode.length != TotpAuthenticator.DEFAULT_DIGITS) {
            return Result.failure(
                IllegalArgumentException("Enter or paste the 6-digit Google Authenticator code to $actionDescription.")
            )
        }

        val secret = activeTotpSecret
        if (secret.isBlank()) {
            return Result.failure(
                IllegalStateException("Authenticator secret unavailable. Re-enable Authenticator in Settings.")
            )
        }

        val valid = TotpAuthenticator.verifyCode(secretBase32 = secret, userCode = cleanCode, timeMillis = now)
        if (!valid) {
            failedTotpAttempts += 1
            if (failedTotpAttempts >= MAX_TOTP_ATTEMPTS_BEFORE_COOLDOWN) {
                totpLockoutUntilMillis = now + TOTP_COOLDOWN_MILLIS
                failedTotpAttempts = 0
                return Result.failure(
                    IllegalStateException("Too many incorrect codes. Wait 30 seconds and paste a fresh code from Google Authenticator.")
                )
            }
            return Result.failure(
                IllegalArgumentException("Incorrect Google Authenticator code. Check the current 6-digit code and try again.")
            )
        }

        failedTotpAttempts = 0
        totpLockoutUntilMillis = 0L
        return Result.success(Unit)
    }

    private fun revokeSessionImmediate(sessionId: String) {
        val currentId = currentSessionId()
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        saveLocalPrefs()
        if (sessionId == currentId) {
            _isCurrentSessionRevoked.value = true
        }
    }

    suspend fun changePasswordWith24hGuard(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
        totpCode: String = ""
    ): Result<String> {
        val loginAt = _currentLoginAtMillis.value
        if (!SecurityLockHelper.isUnlockedAfter24Hours(loginAt)) {
            val remaining = SecurityLockHelper.formatUnlockCountdown(
                SecurityLockHelper.remainingLockMillis(loginAt)
            )
            return Result.failure(
                IllegalStateException("Access will unlock after 24 hours ($remaining).")
            )
        }

        val totpCheck = verifyProtectedActionTotp(totpCode, "change the admin password")
        if (totpCheck.isFailure) {
            return Result.failure(
                totpCheck.exceptionOrNull()
                    ?: IllegalArgumentException("Google Authenticator code is required.")
            )
        }

        if (oldPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your previous password first."))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("New password must be at least 6 characters."))
        }
        if (newPassword != confirmPassword) {
            return Result.failure(IllegalArgumentException("New password and confirmation do not match."))
        }
        if (oldPassword == newPassword) {
            return Result.failure(IllegalArgumentException("Choose a new password different from your previous password."))
        }

        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before changing password."))
        val email = user.email?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(IllegalStateException("Account email not available for re-authentication."))

        val hasPasswordProvider = user.providerData.any {
            it.providerId == EmailAuthProvider.PROVIDER_ID
        }
        if (!hasPasswordProvider) {
            return Result.failure(
                IllegalStateException(
                    "This admin account currently uses Google Sign-In only and does not have an email password set yet. Sign out and tap 'Forgot password?' on the sign-in screen to set a password."
                )
            )
        }

        return try {
            val credential = EmailAuthProvider.getCredential(email, oldPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()

            val freshAuthSeconds = runCatching {
                user.getIdToken(true).await().authTimestamp
            }.getOrDefault(0L)
            if (freshAuthSeconds > 0L) {
                currentAuthTimeMillis = freshAuthSeconds * 1000L
            }

            Result.success("Password updated for both the Mobile App and Website Admin page.")
        } catch (e: CancellationException) {
            throw e
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalStateException("Previous password is incorrect. Enter your current admin password."))
        } catch (e: Exception) {
            Log.w(TAG, "Password change failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to change password right now."
                )
            )
        }
    }

    suspend fun markCurrentSessionSignedOut() = withContext(NonCancellable) {
        val myId = currentSessionId()
        stopObservingCloudSecurity()
        _sessions.value = _sessions.value.filterNot { it.id == myId }
        prefs.edit()
            .remove(KEY_LOGIN_AT_MILLIS)
            .remove(KEY_LAST_AUTH_SIGNIN_TS)
            .apply()
        _currentLoginAtMillis.value = 0L
        _isCurrentSessionRevoked.value = false
        currentAuthTimeMillis = 0L
        LoginTwoFactorStore.clear(appContext)
        saveLocalPrefs()
    }

    suspend fun revokeAllOtherActiveSessions(totpCode: String = ""): Result<Int> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before managing active devices."))

        val totpCheck = verifyProtectedActionTotp(totpCode, "remove all other active devices")
        if (totpCheck.isFailure) {
            return Result.failure(
                totpCheck.exceptionOrNull()
                    ?: IllegalArgumentException("Google Authenticator code is required.")
            )
        }

        val myId = currentSessionId(currentUser.uid)
        val beforeCount = _sessions.value.size
        _sessions.value = _sessions.value.filter { it.id == myId || it.isCurrentDevice }
        saveLocalPrefs()
        val removed = (beforeCount - _sessions.value.size).coerceAtLeast(0)
        return Result.success(removed)
    }

    suspend fun revokeActiveSessionsForEmail(
        email: String,
        sessionId: String = ""
    ): Result<Int> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before removing logged-in devices."))
        val normalizedTargetEmail = email.trim().lowercase(Locale.US)
        val cleanTargetSessionId = sessionId.trim()
        if (normalizedTargetEmail.isEmpty() && cleanTargetSessionId.isEmpty()) {
            return Result.failure(IllegalArgumentException("No account or session identified to remove."))
        }

        val myId = currentSessionId(currentUser.uid)
        val beforeCount = _sessions.value.size
        _sessions.value = _sessions.value.filterNot { session ->
            !session.isCurrentDevice &&
                session.id != myId &&
                session.deviceId != localDeviceId &&
                (
                    (cleanTargetSessionId.isNotEmpty() && session.id == cleanTargetSessionId) ||
                        (normalizedTargetEmail.isNotEmpty() && session.email.trim().equals(normalizedTargetEmail, ignoreCase = true))
                    )
        }
        saveLocalPrefs()
        val removed = (beforeCount - _sessions.value.size).coerceAtLeast(0)
        return Result.success(removed)
    }

    private fun upsertLocalSession(session: ActiveDeviceSession) {
        val filtered = _sessions.value.filterNot { it.id == session.id }
        _sessions.value = (listOf(session) + filtered)
            .filter { it.status == AdminDeviceStatus.ACTIVE }
            .sortedWith(
                compareByDescending<ActiveDeviceSession> { it.isCurrentDevice }
                    .thenByDescending { it.lastActiveMillis }
            )
        saveLocalPrefs()
    }

    private fun prependLocalAlert(alert: LoginSecurityAlert) {
        val filtered = _alerts.value.filterNot { it.id == alert.id }
        _alerts.value = (listOf(alert) + filtered)
            .sortedByDescending { it.timestampMillis }
            .take(40)
        saveLocalPrefs()
    }

    private fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun loadStoredLoginAt(): Long = prefs.getLong(KEY_LOGIN_AT_MILLIS, 0L)

    private fun persistLocalAuthenticator(config: LocalAuthenticatorConfig) {
        prefs.edit()
            .putBoolean(KEY_AUTH_ENABLED, config.enabled)
            .putBoolean(KEY_AUTH_REQUIRE_ON_LOGIN, config.requireOnLogin)
            .putString(KEY_AUTH_SECRET, config.secretBase32)
            .putString(KEY_AUTH_LABEL, config.accountLabel)
            .putString(KEY_AUTH_UPDATED_BY, config.updatedByEmail)
            .putLong(KEY_AUTH_UPDATED_AT, config.updatedAtMillis)
            .apply()
    }

    private fun loadFromLocalPrefs() {
        val authCfg = readLocalAuthenticatorConfig(appContext)
        activeTotpSecret = if (authCfg.enabled) authCfg.secretBase32 else ""
        _authenticatorStatus.value = AuthenticatorStatus(
            enabled = authCfg.enabled,
            requireOnLogin = authCfg.requireOnLogin,
            accountLabel = authCfg.accountLabel,
            updatedByEmail = authCfg.updatedByEmail,
            updatedAtMillis = authCfg.updatedAtMillis,
            isLoaded = true
        )

        val sessionsRaw = prefs.getString(KEY_SESSIONS_JSON, null)
        if (!sessionsRaw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(sessionsRaw)
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id")
                        val devId = obj.optString("deviceId")
                        val status = AdminDeviceStatus.fromValue(obj.optString("status"))
                        if (id.isNotBlank() && status == AdminDeviceStatus.ACTIVE) {
                            val devName = obj.optString("deviceName", "Android Device")
                            val plat = obj.optString("platform", "Mobile App")
                            val savedBrowser = ActiveDeviceSession.resolveBrowserName(
                                explicitBrowser = obj.optString("browser"),
                                userAgent = null,
                                deviceName = devName,
                                platform = plat
                            )
                            add(
                                ActiveDeviceSession(
                                    id = id,
                                    deviceId = devId,
                                    deviceName = devName,
                                    platform = plat,
                                    email = obj.optString("email"),
                                    location = obj.optString("location", fallbackLocaleLocation()),
                                    loginAtMillis = obj.optLong("loginAtMillis"),
                                    lastActiveMillis = obj.optLong("lastActiveMillis"),
                                    status = status,
                                    isCurrentDevice = devId == localDeviceId,
                                    browser = savedBrowser
                                )
                            )
                        }
                    }
                }
                _sessions.value = list.sortedWith(
                    compareByDescending<ActiveDeviceSession> { it.isCurrentDevice }
                        .thenByDescending { it.lastActiveMillis }
                )
            }
        }

        val alertsRaw = prefs.getString(KEY_ALERTS_JSON, null)
        if (!alertsRaw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(alertsRaw)
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id")
                        if (id.isNotBlank()) {
                            add(
                                LoginSecurityAlert(
                                    id = id,
                                    sessionId = obj.optString("sessionId"),
                                    email = obj.optString("email"),
                                    deviceName = obj.optString("deviceName", "Admin Device"),
                                    platform = obj.optString("platform", "Mobile App"),
                                    location = obj.optString("location", fallbackLocaleLocation()),
                                    timestampMillis = obj.optLong("timestampMillis"),
                                    decision = LoginAlertDecision.fromValue(obj.optString("decision")),
                                    sourcePage = obj.optString("sourcePage", "admin"),
                                    isUnauthorizedAttempt = obj.optBoolean("isUnauthorizedAttempt", false),
                                    verifiedWithAuthenticator = obj.optBoolean("verifiedWithAuthenticator", false),
                                    failureReason = obj.optString("failureReason", ""),
                                    attemptStatus = obj.optString("attemptStatus", "")
                                )
                            )
                        }
                    }
                }
                _alerts.value = list
                    .filter { it.isAdminPageEvent && !it.id.startsWith(SYNTHETIC_ALERT_PREFIX) }
                    .sortedByDescending { it.timestampMillis }
                    .take(40)
            }
        }

        val savedReadIds = prefs.getStringSet(KEY_READ_ALERT_IDS, emptySet()).orEmpty()
        _readAlertIds.value = savedReadIds.toSet()
    }

    private fun saveLocalPrefs() {
        val sessionsArr = JSONArray()
        _sessions.value.forEach { s ->
            sessionsArr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("deviceId", s.deviceId)
                    put("deviceName", s.deviceName)
                    put("platform", s.platform)
                    put("email", s.email)
                    put("location", s.location)
                    put("loginAtMillis", s.loginAtMillis)
                    put("lastActiveMillis", s.lastActiveMillis)
                    put("status", s.status.value)
                    put("browser", s.resolvedBrowserLabel)
                }
            )
        }

        val alertsArr = JSONArray()
        _alerts.value.take(40).forEach { a ->
            alertsArr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("sessionId", a.sessionId)
                    put("email", a.email)
                    put("deviceName", a.deviceName)
                    put("platform", a.platform)
                    put("location", a.location)
                    put("timestampMillis", a.timestampMillis)
                    put("decision", a.decision.value)
                    put("sourcePage", a.sourcePage)
                    put("isUnauthorizedAttempt", a.isUnauthorizedAttempt)
                    put("verifiedWithAuthenticator", a.verifiedWithAuthenticator)
                    put("failureReason", a.failureReason)
                    put("attemptStatus", a.attemptStatus)
                }
            )
        }

        prefs.edit()
            .putString(KEY_SESSIONS_JSON, sessionsArr.toString())
            .putString(KEY_ALERTS_JSON, alertsArr.toString())
            .apply()
    }

    private fun buildDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val model = Build.MODEL.orEmpty().trim()
        val combined = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
        return "${combined.ifBlank { "Android Device" }} (Android ${Build.VERSION.RELEASE})"
    }

    private fun fallbackLocaleLocation(): String {
        val country = Locale.getDefault().displayCountry.takeIf { it.isNotBlank() }
        val tzCity = TimeZone.getDefault().id.substringAfter('/', "").replace('_', ' ')
        return listOfNotNull(
            tzCity.takeIf { it.isNotBlank() },
            country
        ).joinToString(", ").ifBlank { TimeZone.getDefault().id }
    }

    private suspend fun fetchIpCityCountry(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://ipapi.co/json/").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty("User-Agent", "AlifCarImportAdmin/1.0")
            }
            try {
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val city = json.optString("city").trim()
                    val country = json.optString("country_name").trim()
                    listOfNotNull(
                        city.takeIf { it.isNotEmpty() },
                        country.takeIf { it.isNotEmpty() }
                    ).joinToString(", ").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun sanitizeIdPart(raw: String?): String =
        raw.orEmpty().replace(ID_SANITIZE_REGEX, "")

    companion object {
        private const val TAG = "SecuritySessionRepo"
        private const val PREFS_NAME = "alif_security_prefs"
        private const val KEY_DEVICE_ID = "local_device_id"
        private const val KEY_LOGIN_AT_MILLIS = "login_at_millis"
        private const val KEY_LAST_AUTH_SIGNIN_TS = "last_auth_signin_ts"
        private const val KEY_LAST_UID = "last_uid"
        private const val KEY_SESSIONS_JSON = "sessions_json"
        private const val KEY_ALERTS_JSON = "alerts_json"
        private const val KEY_READ_ALERT_IDS = "read_alert_ids"
        private const val KEY_AUTH_ENABLED = "local_auth_enabled"
        private const val KEY_AUTH_REQUIRE_ON_LOGIN = "local_auth_require_on_login"
        private const val KEY_AUTH_SECRET = "local_auth_secret"
        private const val KEY_AUTH_LABEL = "local_auth_label"
        private const val KEY_AUTH_UPDATED_BY = "local_auth_updated_by"
        private const val KEY_AUTH_UPDATED_AT = "local_auth_updated_at"
        private const val MAX_STORED_READ_ALERT_IDS = 200

        private const val MAX_TOTP_ATTEMPTS_BEFORE_COOLDOWN = 5
        private const val TOTP_COOLDOWN_MILLIS = 30_000L
        private const val SYNTHETIC_ALERT_PREFIX = "sess_alert_"
        private val ID_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")

        fun readLocalAuthenticatorConfig(context: Context): LocalAuthenticatorConfig {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_AUTH_ENABLED, false)
            val secret = prefs.getString(KEY_AUTH_SECRET, "").orEmpty().trim()
            val effectiveEnabled = enabled && TotpAuthenticator.isValidSecretBase32(secret)
            val requireOnLogin = if (effectiveEnabled) {
                prefs.getBoolean(KEY_AUTH_REQUIRE_ON_LOGIN, true)
            } else {
                false
            }
            return LocalAuthenticatorConfig(
                enabled = effectiveEnabled,
                requireOnLogin = requireOnLogin,
                secretBase32 = if (effectiveEnabled) secret else "",
                accountLabel = prefs.getString(KEY_AUTH_LABEL, "").orEmpty().trim(),
                updatedByEmail = prefs.getString(KEY_AUTH_UPDATED_BY, "").orEmpty().trim(),
                updatedAtMillis = prefs.getLong(KEY_AUTH_UPDATED_AT, 0L)
            )
        }
    }
}
