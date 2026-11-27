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
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SecuritySessionRepository(
    context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val localDeviceId: String = getOrCreateDeviceId()

    private val _sessions = MutableStateFlow<List<ActiveDeviceSession>>(emptyList())
    val sessions: StateFlow<List<ActiveDeviceSession>> = _sessions.asStateFlow()

    private val _alerts = MutableStateFlow<List<LoginSecurityAlert>>(emptyList())
    val alerts: StateFlow<List<LoginSecurityAlert>> = _alerts.asStateFlow()

    private val _currentLoginAtMillis = MutableStateFlow(loadStoredLoginAt())
    val currentLoginAtMillis: StateFlow<Long> = _currentLoginAtMillis.asStateFlow()

    private val _isCurrentSessionRevoked = MutableStateFlow(false)
    val isCurrentSessionRevoked: StateFlow<Boolean> = _isCurrentSessionRevoked.asStateFlow()

    private val _cloudSyncError = MutableStateFlow<String?>(null)
    val cloudSyncError: StateFlow<String?> = _cloudSyncError.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _authenticatorStatus = MutableStateFlow(AuthenticatorStatus())
    val authenticatorStatus: StateFlow<AuthenticatorStatus> = _authenticatorStatus.asStateFlow()

    private var remoteSessionsListener: ListenerRegistration? = null
    private var remoteAlertsListener: ListenerRegistration? = null
    private var remoteAttemptsListener: ListenerRegistration? = null
    private var securityPolicyListener: ListenerRegistration? = null
    private var authenticatorListener: ListenerRegistration? = null
    private var activeAlertCallback: ((LoginSecurityAlert) -> Unit)? = null
    @Volatile
    private var initialAttemptsLoaded: Boolean = false
    private val seenAttemptIds = mutableSetOf<String>()
    private val sessionWriteMutex = Mutex()
    @Volatile
    private var currentAuthTimeMillis: Long = 0L
    @Volatile
    private var isRegisteringCurrentSession: Boolean = false
    @Volatile
    private var hasCompletedInitialRegistration: Boolean = false
    @Volatile
    private var lastHeartbeatSyncedAtMillis: Long = 0L
    @Volatile
    private var currentSessionActivatedAtMillis: Long = 0L
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
        stopObservingCloudSecurity()
        loadFromLocalPrefs()
        activeAlertCallback = onNewAlert
        _cloudSyncError.value = null

        val mySessionId = currentSessionId(uid)
        runCatching {
            remoteSessionsListener = db.collection(COL_ADMIN_SESSIONS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, COL_ADMIN_SESSIONS)
                        _cloudSyncError.value =
                            "Unable to sync active devices across devices (${error.code.name.lowercase().replace('_', ' ')})."
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    _cloudSyncError.value = null
                    val isFromCache = snapshot.metadata.isFromCache

                    val remoteList = snapshot.documents.mapNotNull { doc ->
                        parseRemoteSession(doc, mySessionId, isFromCache = isFromCache)
                    }

                    val existingAlertSessionIds = _alerts.value.map { it.sessionId }.toSet()
                    mergeRemoteSessions(remoteList, mySessionId)

                    // If another device signed in to the Admin Page and wrote to adminSessions without a matching loginAlerts doc,
                    // surface a pending LoginSecurityAlert immediately so the admin gets notified and can verify/revoke it.
                    remoteList
                        .filter { !it.isCurrentDevice && it.status == AdminDeviceStatus.ACTIVE }
                        .forEach { remoteSession ->
                            if (!existingAlertSessionIds.contains(remoteSession.id)) {
                                val syntheticAlert = LoginSecurityAlert(
                                    id = buildSyntheticAlertId(remoteSession.id, remoteSession.loginAtMillis),
                                    sessionId = remoteSession.id,
                                    email = remoteSession.email,
                                    deviceName = remoteSession.deviceName,
                                    platform = remoteSession.platform,
                                    location = remoteSession.location,
                                    timestampMillis = remoteSession.loginAtMillis.takeIf { it > 0L }
                                        ?: remoteSession.lastActiveMillis,
                                    decision = LoginAlertDecision.PENDING,
                                    sourcePage = "admin"
                                )
                                if (syntheticAlert.isAdminPageEvent &&
                                    _alerts.value.none { it.id == syntheticAlert.id || it.sessionId == remoteSession.id }
                                ) {
                                    prependLocalAlert(syntheticAlert)
                                    activeAlertCallback?.invoke(syntheticAlert)
                                }
                            }
                        }
                }

            remoteAlertsListener = db.collection(COL_LOGIN_ALERTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.LIST, COL_LOGIN_ALERTS)
                        _cloudSyncError.value =
                            "Unable to sync login alerts across devices (${error.code.name.lowercase().replace('_', ' ')})."
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    _cloudSyncError.value = null

                    val existingIds = _alerts.value.map { it.id }.toSet()
                    val remoteAlerts = snapshot.documents.mapNotNull { doc ->
                        parseRemoteAlert(doc)
                    }.filter { it.isAdminPageEvent }

                    mergeRemoteAlerts(remoteAlerts)
                    remoteAlerts
                        .filter {
                            it.decision == LoginAlertDecision.PENDING &&
                                !it.isOwnerAuthenticatorVerified &&
                                !existingIds.contains(it.id)
                        }
                        .forEach { alert ->
                            activeAlertCallback?.invoke(alert)
                        }
                }

            remoteAttemptsListener = db.collection(COL_ADMIN_LOGIN_ATTEMPTS)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            handleFirestoreError(error, OperationType.LIST, COL_ADMIN_LOGIN_ATTEMPTS)
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val parsed = snapshot.documents.mapNotNull { doc ->
                        parseRemoteAttemptAsAlert(doc)
                    }.filter { it.isAdminPageEvent }

                    if (parsed.isNotEmpty()) {
                        mergeRemoteAlerts(parsed)
                    }
                    if (!initialAttemptsLoaded) {
                        parsed.forEach { seenAttemptIds.add(it.id) }
                        initialAttemptsLoaded = true
                        return@addSnapshotListener
                    }
                    parsed.forEach { alert ->
                        if (seenAttemptIds.add(alert.id)) {
                            activeAlertCallback?.invoke(alert)
                        }
                    }
                }
            securityPolicyListener = db.collection(COL_CONFIG)
                .document(DOC_SECURITY)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, "$COL_CONFIG/$DOC_SECURITY")
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    if (isRegisteringCurrentSession || snapshot.metadata.isFromCache) return@addSnapshotListener
                    val cutoffSeconds = snapshot.getTimestamp(
                        "forceSignOutBefore",
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.seconds ?: return@addSnapshotListener
                    val authTimeSeconds = (currentAuthTimeMillis.takeIf { it > 0L }
                        ?: auth.currentUser?.metadata?.lastSignInTimestamp
                        ?: 0L) / 1000L
                    if (authTimeSeconds > 0L && authTimeSeconds < cutoffSeconds) {
                        _isCurrentSessionRevoked.value = true
                    }
                }
            authenticatorListener = db.collection(COL_CONFIG)
                .document(DOC_AUTHENTICATOR)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        handleFirestoreError(error, OperationType.GET, "$COL_CONFIG/$DOC_AUTHENTICATOR")
                        return@addSnapshotListener
                    }
                    applyAuthenticatorSnapshot(snapshot)
                }
        }.onFailure { err ->
            handleFirestoreError(err, OperationType.LIST, COL_ADMIN_SESSIONS)
            _cloudSyncError.value = "Unable to start live device sync."
        }
    }

    fun stopObservingCloudSecurity() {
        remoteSessionsListener?.remove()
        remoteSessionsListener = null
        remoteAlertsListener?.remove()
        remoteAlertsListener = null
        remoteAttemptsListener?.remove()
        remoteAttemptsListener = null
        securityPolicyListener?.remove()
        securityPolicyListener = null
        authenticatorListener?.remove()
        authenticatorListener = null
        activeAlertCallback = null
        initialAttemptsLoaded = false
        seenAttemptIds.clear()
    }

    suspend fun registerCurrentDeviceSignIn(
        user: FirebaseUser,
        verifiedWithAuthenticator: Boolean = false,
        onNewLoginAlert: (LoginSecurityAlert) -> Unit
    ) {
        isRegisteringCurrentSession = true
        _isCurrentSessionRevoked.value = false
        val sessionId = currentSessionId(user.uid)
        val deviceName = buildDeviceDisplayName()
        val email = user.email.orEmpty().trim()
        val now = System.currentTimeMillis()
        currentSessionActivatedAtMillis = now
        val authSignInTs = user.metadata?.lastSignInTimestamp ?: 0L
        val tokenAuthTimeMillis = runCatching {
            user.getIdToken(true).await().authTimestamp * 1000L
        }.recoverCatching {
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
        try {
            mirrorSessionToCloud(user.uid, updatedSession)
            if (!_isCurrentSessionRevoked.value) {
                val syncedAt = System.currentTimeMillis()
                currentSessionActivatedAtMillis = syncedAt
                lastHeartbeatSyncedAtMillis = syncedAt
                hasCompletedInitialRegistration = true
            }
        } finally {
            isRegisteringCurrentSession = false
        }

        if (_isCurrentSessionRevoked.value) return

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
            mirrorAlertToCloud(user.uid, alert)
        }

        // Enrich real approximate city/country in background without requesting GPS permissions
        val ipLocation = fetchIpCityCountry()
        if (ipLocation != null && ipLocation != updatedSession.location) {
            val enrichedSession = updatedSession.copy(
                location = ipLocation,
                lastActiveMillis = System.currentTimeMillis()
            )
            upsertLocalSession(enrichedSession)
            mirrorSessionToCloud(user.uid, enrichedSession)
            if (createdAlertId != null) {
                val updatedAlerts = _alerts.value.map { alert ->
                    if (alert.id == createdAlertId) {
                        alert.copy(location = ipLocation)
                    } else alert
                }
                _alerts.value = updatedAlerts
                saveLocalPrefs()
                updateAlertLocationInCloud(createdAlertId, ipLocation)
            }
        }
    }

    suspend fun refreshHeartbeat() {
        val user = auth.currentUser ?: return
        if (isRegisteringCurrentSession || !hasCompletedInitialRegistration || _isCurrentSessionRevoked.value) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatSyncedAtMillis < HEARTBEAT_MIN_INTERVAL_MILLIS) {
            return
        }
        val sessionId = currentSessionId(user.uid)
        val current = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        val refreshed = current.copy(
            lastActiveMillis = now,
            status = AdminDeviceStatus.ACTIVE
        )
        upsertLocalSession(refreshed)
        mirrorSessionToCloud(user.uid, refreshed)
    }

    suspend fun refreshFromServer() {
        val user = auth.currentUser ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        val mySessionId = currentSessionId(user.uid)
        try {
            val sessionsSnapshot = db.collection(COL_ADMIN_SESSIONS)
                .get(Source.SERVER)
                .await()
            val remoteSessions = sessionsSnapshot.documents.mapNotNull { doc ->
                parseRemoteSession(doc, mySessionId)
            }
            val existingAlertSessionIds = _alerts.value.map { it.sessionId }.toSet()
            mergeRemoteSessions(remoteSessions, mySessionId)

            val alertsSnapshot = db.collection(COL_LOGIN_ALERTS)
                .get(Source.SERVER)
                .await()
            val existingAlertIds = _alerts.value.map { it.id }.toSet()
            val remoteAlerts = alertsSnapshot.documents.mapNotNull { doc ->
                parseRemoteAlert(doc)
            }
            val remoteAttempts = runCatching {
                db.collection(COL_ADMIN_LOGIN_ATTEMPTS)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .mapNotNull { doc -> parseRemoteAttemptAsAlert(doc) }
            }.getOrDefault(emptyList())
            val combinedRemoteAlerts = remoteAlerts + remoteAttempts
            mergeRemoteAlerts(combinedRemoteAlerts)
            _cloudSyncError.value = null

            val knownAlertSessionIds = existingAlertSessionIds + remoteAlerts.map { it.sessionId }
            remoteSessions
                .filter { !it.isCurrentDevice && it.status == AdminDeviceStatus.ACTIVE }
                .forEach { remoteSession ->
                    if (!knownAlertSessionIds.contains(remoteSession.id)) {
                        val syntheticAlert = LoginSecurityAlert(
                            id = buildSyntheticAlertId(remoteSession.id, remoteSession.loginAtMillis),
                            sessionId = remoteSession.id,
                            email = remoteSession.email,
                            deviceName = remoteSession.deviceName,
                            platform = remoteSession.platform,
                            location = remoteSession.location,
                            timestampMillis = remoteSession.loginAtMillis.takeIf { it > 0L }
                                ?: remoteSession.lastActiveMillis,
                            decision = LoginAlertDecision.PENDING,
                            sourcePage = if (remoteSession.isWebsiteSession) "/admin" else "Mobile Admin App"
                        )
                        if (syntheticAlert.isAdminPageEvent &&
                            _alerts.value.none { it.id == syntheticAlert.id || it.sessionId == remoteSession.id }
                        ) {
                            prependLocalAlert(syntheticAlert)
                            activeAlertCallback?.invoke(syntheticAlert)
                        }
                    }
                }

            combinedRemoteAlerts
                .filter {
                    it.isAdminPageEvent &&
                        (it.decision == LoginAlertDecision.PENDING || it.isUnauthorizedAttempt) &&
                        !existingAlertIds.contains(it.id)
                }
                .forEach { alert ->
                    activeAlertCallback?.invoke(alert)
                }

            runCatching {
                val authSnap = db.collection(COL_CONFIG)
                    .document(DOC_AUTHENTICATOR)
                    .get(Source.SERVER)
                    .await()
                applyAuthenticatorSnapshot(authSnap)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_ADMIN_SESSIONS)
            _cloudSyncError.value = "Unable to refresh active devices from cloud right now."
        } finally {
            _isRefreshing.value = false
        }
    }

    fun confirmAlertIsMe(alertId: String) {
        val next = _alerts.value.map { alert ->
            if (alert.id == alertId) {
                val confirmed = alert.copy(decision = LoginAlertDecision.CONFIRMED_ME)
                mirrorAlertDecisionToCloud(confirmed)
                confirmed
            } else alert
        }
        _alerts.value = next
        saveLocalPrefs()
    }

    fun rejectAlertNotMe(alertId: String) {
        var targetSessionId: String? = null
        val nextAlerts = _alerts.value.map { alert ->
            if (alert.id == alertId) {
                targetSessionId = alert.sessionId
                val rejected = alert.copy(decision = LoginAlertDecision.REJECTED_NOT_ME)
                mirrorAlertDecisionToCloud(rejected)
                rejected
            } else alert
        }
        _alerts.value = nextAlerts

        val sessionIdToRevoke = targetSessionId
        if (!sessionIdToRevoke.isNullOrBlank()) {
            revokeSessionImmediate(sessionIdToRevoke)
        } else {
            saveLocalPrefs()
        }
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
        val payload = mapOf(
            "enabled" to true,
            "secretBase32" to cleanSecret,
            "accountLabel" to email,
            "issuer" to TotpAuthenticator.DEFAULT_ISSUER,
            "updatedByEmail" to email,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.collection(COL_CONFIG)
                .document(DOC_AUTHENTICATOR)
                .set(payload)
                .await()
            activeTotpSecret = cleanSecret
            failedTotpAttempts = 0
            totpLockoutUntilMillis = 0L
            _authenticatorStatus.value = AuthenticatorStatus(
                enabled = true,
                accountLabel = email,
                updatedByEmail = email,
                updatedAtMillis = System.currentTimeMillis(),
                isLoaded = true
            )
            val sessionAuthSeconds = (currentAuthTimeMillis / 1000L).takeIf { it > 0L }
                ?: ((user.metadata?.lastSignInTimestamp ?: 0L) / 1000L)
            if (sessionAuthSeconds > 0L) {
                LoginTwoFactorStore.markSessionVerified(appContext, user.uid, sessionAuthSeconds)
            }
            Result.success("Google Authenticator enabled. Signing in with alifcarimports@gmail.com, removing active devices, changing password, and disabling 2FA now require a 6-digit code.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COL_CONFIG/$DOC_AUTHENTICATOR")
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to enable Google Authenticator right now."
                )
            )
        }
    }

    suspend fun disableAuthenticator(verificationCode: String): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in again before disabling Authenticator."))

        // Ensure we have the latest server-side Authenticator state before verifying
        if (!_authenticatorStatus.value.isLoaded || (_authenticatorStatus.value.enabled && activeTotpSecret.isBlank())) {
            runCatching {
                val snap = db.collection(COL_CONFIG)
                    .document(DOC_AUTHENTICATOR)
                    .get(Source.SERVER)
                    .await()
                applyAuthenticatorSnapshot(snap)
            }
        }

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
        val payload = mapOf(
            "enabled" to false,
            "secretBase32" to "",
            "accountLabel" to email,
            "issuer" to TotpAuthenticator.DEFAULT_ISSUER,
            "updatedByEmail" to email,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.collection(COL_CONFIG)
                .document(DOC_AUTHENTICATOR)
                .set(payload)
                .await()
            activeTotpSecret = ""
            failedTotpAttempts = 0
            totpLockoutUntilMillis = 0L
            _authenticatorStatus.value = AuthenticatorStatus(
                enabled = false,
                accountLabel = email,
                updatedByEmail = email,
                updatedAtMillis = System.currentTimeMillis(),
                isLoaded = true
            )
            Result.success("Google Authenticator disabled.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COL_CONFIG/$DOC_AUTHENTICATOR")
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to disable Google Authenticator right now."
                )
            )
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
                IllegalStateException("Authenticator verification is syncing. Refresh Settings and try again.")
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

    private fun applyAuthenticatorSnapshot(snapshot: DocumentSnapshot?) {
        if (snapshot == null || !snapshot.exists()) {
            activeTotpSecret = ""
            _authenticatorStatus.value = AuthenticatorStatus(
                enabled = false,
                isLoaded = true
            )
            return
        }
        val enabled = snapshot.getBoolean("enabled") == true
        val secret = snapshot.getString("secretBase32").orEmpty().trim()
        val effectiveEnabled = enabled && TotpAuthenticator.isValidSecretBase32(secret)
        activeTotpSecret = if (effectiveEnabled) secret else ""
        _authenticatorStatus.value = AuthenticatorStatus(
            enabled = effectiveEnabled,
            accountLabel = snapshot.getString("accountLabel").orEmpty().trim(),
            updatedByEmail = snapshot.getString("updatedByEmail").orEmpty().trim(),
            updatedAtMillis = snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
            isLoaded = true
        )
    }

    private fun revokeSessionImmediate(sessionId: String) {
        val currentId = currentSessionId()
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(status = AdminDeviceStatus.REVOKED)
            } else session
        }.filter { it.status == AdminDeviceStatus.ACTIVE }
        saveLocalPrefs()

        db.collection(COL_ADMIN_SESSIONS).document(sessionId)
            .update(
                mapOf(
                    "status" to AdminDeviceStatus.REVOKED.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, "$COL_ADMIN_SESSIONS/$sessionId")
            }

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
                    "This admin account currently uses Google Sign-In only and does not have an email password set yet. Sign out and tap 'Forgot password?' on the sign-in screen to create one unified password for both the Mobile App and Website Admin page."
                )
            )
        }

        return try {
            val credential = EmailAuthProvider.getCredential(email, oldPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()

            // Force-refresh ID token so current phone holds the freshly re-authenticated auth_time
            val freshAuthSeconds = runCatching {
                user.getIdToken(true).await().authTimestamp
            }.getOrDefault(0L)
            if (freshAuthSeconds > 0L) {
                currentAuthTimeMillis = freshAuthSeconds * 1000L
            }

            // Immediately sign out all OTHER active sessions (including Website admin tabs and other phones)
            // and raise the server-side security cutoff to this phone's fresh auth_time so stale devices
            // must sign in again using the new unified password.
            val revokedOthers = revokeOtherSessionsAfterPasswordChange(
                currentUser = user,
                freshAuthSeconds = freshAuthSeconds
            )

            val successMsg = if (revokedOthers > 0) {
                "Password updated for both the Mobile App and Website Admin page. Signed out $revokedOthers other active session(s)."
            } else {
                "Password updated for both the Mobile App and Website Admin page. Any other device must now sign in with the new password."
            }
            Result.success(successMsg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalStateException("Previous password is incorrect. Enter your current unified App & Website admin password."))
        } catch (e: Exception) {
            Log.w(TAG, "Password change failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to change password right now."
                )
            )
        }
    }

    private suspend fun revokeOtherSessionsAfterPasswordChange(
        currentUser: FirebaseUser,
        freshAuthSeconds: Long
    ): Int {
        val myId = currentSessionId(currentUser.uid)
        var revokedCount = 0

        runCatching {
            val snapshot = withTimeoutOrNull(6000L) {
                runCatching {
                    db.collection(COL_ADMIN_SESSIONS).get(Source.SERVER).await()
                }.getOrElse {
                    db.collection(COL_ADMIN_SESSIONS).get().await()
                }
            }

            val otherActiveRefs = snapshot?.documents
                ?.filter { doc ->
                    doc.id != myId &&
                        (doc.getString("status") ?: "").trim().lowercase() != AdminDeviceStatus.REVOKED.value
                }
                ?.map { it.reference }
                .orEmpty()

            if (otherActiveRefs.isNotEmpty()) {
                val payload = mapOf(
                    "status" to AdminDeviceStatus.REVOKED.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                otherActiveRefs.chunked(50).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { ref -> batch.update(ref, payload) }
                    withTimeoutOrNull(6000L) {
                        batch.commit().await()
                    }
                    revokedCount += chunk.size
                }
            }

            _sessions.value = _sessions.value.filter { it.id == myId || it.isCurrentDevice }
            saveLocalPrefs()

            // Advance server-side cutoff to freshAuthSeconds (whole seconds <= request.time).
            // Current phone's JWT has auth_time == freshAuthSeconds so it passes (auth_time * 1000 >= cutoff),
            // while all earlier website tabs / phones / old APKs are immediately blocked by Firestore Rules.
            if (freshAuthSeconds > 0L) {
                val securityPayload = mapOf(
                    "forceSignOutBefore" to Timestamp(freshAuthSeconds, 0),
                    "triggeredByEmail" to currentUser.email.orEmpty().trim().take(254),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                withTimeoutOrNull(6000L) {
                    db.collection(COL_CONFIG)
                        .document(DOC_SECURITY)
                        .set(securityPayload)
                        .await()
                }
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Post-password-change session revocation encountered an error", err)
        }

        return revokedCount
    }

    suspend fun markCurrentSessionSignedOut() = withContext(NonCancellable) {
        val myId = currentSessionId()
        stopObservingCloudSecurity()
        if (auth.currentUser != null) {
            try {
                withTimeoutOrNull(2500L) {
                    db.collection(COL_ADMIN_SESSIONS).document(myId)
                        .update(
                            mapOf(
                                "status" to AdminDeviceStatus.REVOKED.value,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "$COL_ADMIN_SESSIONS/$myId")
            }
        }
        _sessions.value = _sessions.value.filterNot { it.id == myId }
        prefs.edit()
            .remove(KEY_LOGIN_AT_MILLIS)
            .remove(KEY_LAST_AUTH_SIGNIN_TS)
            .apply()
        _currentLoginAtMillis.value = 0L
        _isCurrentSessionRevoked.value = false
        currentAuthTimeMillis = 0L
        currentSessionActivatedAtMillis = 0L
        lastHeartbeatSyncedAtMillis = 0L
        hasCompletedInitialRegistration = false
        isRegisteringCurrentSession = false
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
        var revokedCount = 0
        var lastError: Exception? = null

        try {
            val snapshot = withTimeoutOrNull(6000L) {
                runCatching {
                    db.collection(COL_ADMIN_SESSIONS).get(Source.SERVER).await()
                }.getOrElse {
                    db.collection(COL_ADMIN_SESSIONS).get().await()
                }
            }

            // Revoke ALL other active Mobile App and Website sessions EXCEPT this current device
            val otherTargetRefs = snapshot?.documents
                ?.filter { doc ->
                    val status = (doc.getString("status") ?: "").trim().lowercase()
                    val devId = doc.getString("deviceId").orEmpty()
                    doc.id != myId &&
                        devId != localDeviceId &&
                        status != AdminDeviceStatus.REVOKED.value
                }
                ?.map { it.reference }
                .orEmpty()

            val payload = mapOf(
                "status" to AdminDeviceStatus.REVOKED.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            otherTargetRefs.chunked(50).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.update(ref, payload) }
                withTimeoutOrNull(6000L) {
                    batch.commit().await()
                }
                revokedCount += chunk.size
            }

            // Advance server-side cutoff safely to this phone's own auth_time (whole seconds)
            // so pre-existing older/un-updated APKs & stale tabs are blocked by Firestore Rules
            // while THIS current phone stays logged in seamlessly (auth_time * 1000 >= cutoff).
            val myAuthSeconds = runCatching {
                currentUser.getIdToken(false).await().authTimestamp
            }.getOrDefault(currentAuthTimeMillis / 1000L)

            if (myAuthSeconds > 0L) {
                currentAuthTimeMillis = myAuthSeconds * 1000L
                val existingCutoffSeconds = runCatching {
                    db.collection(COL_CONFIG)
                        .document(DOC_SECURITY)
                        .get(Source.SERVER)
                        .await()
                        .getTimestamp("forceSignOutBefore")
                        ?.seconds
                        ?: 0L
                }.getOrDefault(0L)

                if (myAuthSeconds > existingCutoffSeconds) {
                    val securityPayload = mapOf(
                        "forceSignOutBefore" to Timestamp(myAuthSeconds, 0),
                        "triggeredByEmail" to currentUser.email.orEmpty().trim().take(254),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    withTimeoutOrNull(6000L) {
                        db.collection(COL_CONFIG)
                            .document(DOC_SECURITY)
                            .set(securityPayload)
                            .await()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            handleFirestoreError(e, OperationType.UPDATE, COL_ADMIN_SESSIONS)
        }

        // Keep only the current device active in local state
        _sessions.value = _sessions.value.filter { it.id == myId || it.isCurrentDevice }
        saveLocalPrefs()

        return if (lastError != null && revokedCount == 0) {
            Result.failure(lastError)
        } else {
            Result.success(revokedCount)
        }
    }

    private suspend fun mirrorSessionToCloud(uid: String, session: ActiveDeviceSession) {
        sessionWriteMutex.withLock {
            val currentUser = auth.currentUser
            if (currentUser == null || currentUser.uid != uid || _isCurrentSessionRevoked.value) {
                return@withLock
            }
            val path = "$COL_ADMIN_SESSIONS/${session.id}"
            try {
                writeSessionToServerOnce(uid, session)
                lastHeartbeatSyncedAtMillis = System.currentTimeMillis()
                _cloudSyncError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val firestoreError = unwrapFirestoreException(e)
                if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    // Refresh JWT token and check global forceSignOutBefore cutoff (always readable by isAdminIdentity()).
                    val freshAuthSeconds = runCatching {
                        currentUser.getIdToken(true).await().authTimestamp
                    }.getOrDefault(0L)
                    if (freshAuthSeconds > 0L) {
                        currentAuthTimeMillis = freshAuthSeconds * 1000L
                    }
                    val serverCutoffSeconds = runCatching {
                        db.collection(COL_CONFIG)
                            .document(DOC_SECURITY)
                            .get(Source.SERVER)
                            .await()
                            .getTimestamp("forceSignOutBefore")
                            ?.seconds
                    }.getOrNull()

                    val effectiveAuthSeconds = freshAuthSeconds.takeIf { it > 0L }
                        ?: (currentAuthTimeMillis / 1000L)
                    if (serverCutoffSeconds != null &&
                        effectiveAuthSeconds > 0L &&
                        effectiveAuthSeconds < serverCutoffSeconds
                    ) {
                        _isCurrentSessionRevoked.value = true
                        _cloudSyncError.value = null
                        return@withLock
                    }

                    // Token may have just refreshed; retry write once before surfacing error.
                    try {
                        writeSessionToServerOnce(uid, session)
                        lastHeartbeatSyncedAtMillis = System.currentTimeMillis()
                        _cloudSyncError.value = null
                        return@withLock
                    } catch (retryError: CancellationException) {
                        throw retryError
                    } catch (retryError: Exception) {
                        handleFirestoreError(retryError, OperationType.WRITE, path)
                        _cloudSyncError.value = "Unable to sync this device to cloud."
                        return@withLock
                    }
                }
                if (firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    // Offline transient failure: avoid noisy error banner while offline.
                    return@withLock
                }
                handleFirestoreError(e, OperationType.WRITE, path)
                _cloudSyncError.value = "Unable to sync this device to cloud."
            }
        }
    }

    private suspend fun writeSessionToServerOnce(uid: String, session: ActiveDeviceSession) {
        val docRef = db.collection(COL_ADMIN_SESSIONS).document(session.id)
        val cleanUid = uid.take(128)
        val cleanDeviceId = session.deviceId.take(128)
        val cleanDeviceName = session.deviceName.trim().ifBlank { "Android Device" }.take(160)
        val cleanPlatform = session.platform.trim().ifBlank { "Mobile App" }.take(60)
        val cleanEmail = session.email.trim().take(254)
        val cleanLocation = session.location.trim().ifBlank { "Unknown location" }.take(160)
        val cleanLoginAt = session.loginAtMillis.coerceAtLeast(0L)
        val cleanLastActive = session.lastActiveMillis.coerceAtLeast(0L)

        val updatePayload = mapOf(
            "deviceName" to cleanDeviceName,
            "platform" to cleanPlatform,
            "email" to cleanEmail,
            "location" to cleanLocation,
            "loginAtMillis" to cleanLoginAt,
            "lastActiveMillis" to cleanLastActive,
            "status" to session.status.value,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val snapshot = try {
            docRef.get(Source.SERVER).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val firestoreError = unwrapFirestoreException(e)
            if (firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                // Offline: only issue a safe partial update if cache confirms the doc exists;
                // never blindly issue set() with createdAt = serverTimestamp() while server state is unknown.
                val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
                if (cached != null && cached.exists() && isConformantOwnedSessionDoc(cached, session.id, cleanUid, cleanDeviceId)) {
                    docRef.update(updatePayload).await()
                }
                return
            }
            throw e
        }

        if (!snapshot.exists()) {
            val createPayload = mapOf(
                "id" to session.id,
                "uid" to cleanUid,
                "deviceId" to cleanDeviceId,
                "deviceName" to cleanDeviceName,
                "platform" to cleanPlatform,
                "email" to cleanEmail,
                "location" to cleanLocation,
                "loginAtMillis" to cleanLoginAt,
                "lastActiveMillis" to cleanLastActive,
                "status" to session.status.value,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(createPayload).await()
        } else if (isConformantOwnedSessionDoc(snapshot, session.id, cleanUid, cleanDeviceId)) {
            docRef.update(updatePayload).await()
        } else if (snapshot.getString("uid") == cleanUid) {
            // Safely repair an older/legacy session document owned by this same admin
            // while strictly preserving any existing valid createdAt timestamp and deviceId.
            val preservedCreatedAt: Any = snapshot.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            val preservedDeviceId = snapshot.getString("deviceId")?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
                ?: cleanDeviceId
            val repairPayload = mapOf(
                "id" to session.id,
                "uid" to cleanUid,
                "deviceId" to preservedDeviceId,
                "deviceName" to cleanDeviceName,
                "platform" to cleanPlatform,
                "email" to cleanEmail,
                "location" to cleanLocation,
                "loginAtMillis" to cleanLoginAt,
                "lastActiveMillis" to cleanLastActive,
                "status" to session.status.value,
                "createdAt" to preservedCreatedAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(repairPayload).await()
        } else {
            // Document exists but belongs to a different uid; attempt normal update only if valid.
            docRef.update(updatePayload).await()
        }
    }

    private fun isConformantOwnedSessionDoc(
        snapshot: DocumentSnapshot,
        expectedId: String,
        expectedUid: String,
        expectedDeviceId: String
    ): Boolean {
        val data = snapshot.data ?: return false
        val keys = data.keys
        if (!keys.containsAll(REQUIRED_SESSION_KEYS)) return false
        if (!ALLOWED_SESSION_KEYS.containsAll(keys)) return false
        return snapshot.getString("id") == expectedId &&
            snapshot.getString("uid") == expectedUid &&
            snapshot.getString("deviceId") == expectedDeviceId &&
            snapshot.getTimestamp("createdAt") != null
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private suspend fun mirrorAlertToCloud(uid: String, alert: LoginSecurityAlert) {
        val path = "$COL_LOGIN_ALERTS/${alert.id}"
        try {
            val payload = buildMap<String, Any> {
                put("id", alert.id)
                put("sessionId", alert.sessionId)
                put("uid", uid.take(128))
                put("email", alert.email.trim().take(254))
                put("deviceName", alert.deviceName.trim().ifBlank { "Android Device" }.take(160))
                put("platform", alert.platform.trim().ifBlank { "Mobile App" }.take(60))
                put("location", alert.location.trim().ifBlank { "Unknown location" }.take(160))
                put("timestampMillis", alert.timestampMillis.coerceAtLeast(0L))
                // Firestore rules enforce decision == 'pending' on initial document create
                put("decision", LoginAlertDecision.PENDING.value)
                put("sourcePage", alert.sourcePage.trim().ifBlank { "admin" }.take(120))
                if (alert.verifiedWithAuthenticator) {
                    put("attemptType", LoginSecurityAlert.ATTEMPT_TYPE_AUTHENTICATOR_VERIFIED)
                }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val docRef = db.collection(COL_LOGIN_ALERTS).document(alert.id)
            docRef.set(payload).await()
            if (alert.decision == LoginAlertDecision.CONFIRMED_ME) {
                runCatching {
                    docRef.update(
                        mapOf(
                            "decision" to LoginAlertDecision.CONFIRMED_ME.value,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    private suspend fun updateAlertLocationInCloud(alertId: String, location: String) {
        if (alertId.startsWith(SYNTHETIC_ALERT_PREFIX)) return
        val path = "$COL_LOGIN_ALERTS/$alertId"
        try {
            db.collection(COL_LOGIN_ALERTS).document(alertId)
                .update(
                    mapOf(
                        "location" to location.trim().ifBlank { "Unknown location" }.take(160),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    private fun mirrorAlertDecisionToCloud(alert: LoginSecurityAlert) {
        if (alert.id.startsWith(SYNTHETIC_ALERT_PREFIX)) return
        val path = "$COL_LOGIN_ALERTS/${alert.id}"
        db.collection(COL_LOGIN_ALERTS).document(alert.id)
            .update(
                mapOf(
                    "decision" to alert.decision.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { err ->
                handleFirestoreError(err, OperationType.UPDATE, path)
            }
    }

    private fun parseRemoteSession(
        doc: DocumentSnapshot,
        mySessionId: String,
        isFromCache: Boolean = false
    ): ActiveDeviceSession? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val devId = (data["deviceId"] as? String).orEmpty()
        val status = AdminDeviceStatus.fromValue(data["status"] as? String)
        val loginMillis = (data["loginAtMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("loginAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        val updatedAtMillis = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        val activeMillis = (data["lastActiveMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: updatedAtMillis
            ?: loginMillis

        // Only revoke the current device if Firestore server explicitly marks it REVOKED
        // AFTER our current sign-in registration finished (prevents stale pre-login revocations
        // from falsely forcing the user to sign in twice).
        val revokedAfterCurrentActivation = updatedAtMillis == null ||
            currentSessionActivatedAtMillis <= 0L ||
            updatedAtMillis >= (currentSessionActivatedAtMillis - 2_000L)
        if (!isRegisteringCurrentSession &&
            !isFromCache &&
            id == mySessionId &&
            status == AdminDeviceStatus.REVOKED &&
            revokedAfterCurrentActivation
        ) {
            _isCurrentSessionRevoked.value = true
        }

        val resolvedDeviceName = (data["deviceName"] as? String).orEmpty().trim()
            .ifBlank { (data["browser"] as? String).orEmpty().trim() }
            .ifBlank { (data["userAgent"] as? String).orEmpty().trim().take(80) }
            .ifBlank { "Admin Device" }

        val rawPlatform = (data["platform"] as? String).orEmpty().trim()
        val normalizedPlatform = when {
            rawPlatform.isBlank() -> "Website"
            rawPlatform.equals("web", ignoreCase = true) ||
                rawPlatform.equals("browser", ignoreCase = true) -> "Website"
            else -> rawPlatform
        }

        val resolvedEmail = (data["email"] as? String).orEmpty().trim()
            .ifBlank { (data["userEmail"] as? String).orEmpty().trim() }
            .ifBlank { (data["adminEmail"] as? String).orEmpty().trim() }

        val resolvedBrowser = ActiveDeviceSession.resolveBrowserName(
            explicitBrowser = data["browser"] as? String,
            userAgent = data["userAgent"] as? String,
            deviceName = resolvedDeviceName,
            platform = normalizedPlatform
        )

        return ActiveDeviceSession(
            id = id,
            deviceId = devId,
            deviceName = resolvedDeviceName,
            platform = normalizedPlatform,
            email = resolvedEmail,
            location = (data["location"] as? String).orEmpty().trim().ifBlank { "Unknown location" },
            loginAtMillis = loginMillis,
            lastActiveMillis = activeMillis,
            status = status,
            isCurrentDevice = devId == localDeviceId || id == mySessionId,
            browser = resolvedBrowser
        )
    }

    private fun parseRemoteAlert(doc: DocumentSnapshot): LoginSecurityAlert? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val sourcePage = (data["sourcePage"] as? String)
            ?: (data["page"] as? String)
            ?: "admin"
        val attemptType = (data["attemptType"] as? String).orEmpty()
        val platform = (data["platform"] as? String).orEmpty().trim().ifBlank { "Website Admin" }
        if (!LoginSecurityAlert.isAdminPageLoginEvent(sourcePage, platform, attemptType)) {
            return null
        }
        val tsMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        val isUnauthorized = attemptType.contains("failed", ignoreCase = true) ||
            attemptType.contains("denied", ignoreCase = true) ||
            attemptType.contains("unauthorized", ignoreCase = true)
        val email = (data["email"] as? String).orEmpty().trim()
        val verifiedByAuth = !isUnauthorized && (
            (data["authenticatorVerified"] as? Boolean == true) ||
                LoginSecurityAlert.isAuthenticatorVerifiedAttemptType(attemptType)
            )
        val rawDecision = LoginAlertDecision.fromValue(data["decision"] as? String)
        val effectiveDecision = if (verifiedByAuth && AdminAuthorizationPolicy.isBootstrapOwnerEmail(email)) {
            LoginAlertDecision.CONFIRMED_ME
        } else {
            rawDecision
        }
        return LoginSecurityAlert(
            id = id,
            sessionId = (data["sessionId"] as? String).orEmpty(),
            email = email,
            deviceName = (data["deviceName"] as? String).orEmpty().trim().ifBlank { "Admin Device" },
            platform = platform,
            location = (data["location"] as? String).orEmpty().trim().ifBlank { "Unknown location" },
            timestampMillis = tsMillis,
            decision = effectiveDecision,
            sourcePage = sourcePage,
            isUnauthorizedAttempt = isUnauthorized,
            verifiedWithAuthenticator = verifiedByAuth
        )
    }

    private fun parseRemoteAttemptAsAlert(doc: DocumentSnapshot): LoginSecurityAlert? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val sourcePage = (data["sourcePage"] as? String).orEmpty().trim()
        val platform = (data["platform"] as? String).orEmpty().trim().ifBlank { "Website Admin" }
        if (!LoginSecurityAlert.isAdminPageLoginEvent(sourcePage, platform, "admin_login_attempt")) {
            return null
        }
        val tsMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        return LoginSecurityAlert(
            id = "attempt_$id",
            sessionId = "",
            email = (data["email"] as? String).orEmpty().trim(),
            deviceName = (data["deviceName"] as? String).orEmpty().trim().ifBlank { "Unknown Device" },
            platform = platform,
            location = (data["location"] as? String).orEmpty().trim().ifBlank { "Unknown location" },
            timestampMillis = tsMillis,
            decision = LoginAlertDecision.REJECTED_NOT_ME,
            sourcePage = sourcePage.ifBlank { "/admin" },
            isUnauthorizedAttempt = true
        )
    }

    private fun mergeRemoteSessions(
        remote: List<ActiveDeviceSession>,
        mySessionId: String
    ) {
        val remoteById = remote.associateBy { it.id }
        val merged = mutableMapOf<String, ActiveDeviceSession>()

        // Authoritative active sessions from Firestore
        for (item in remote) {
            if (item.status == AdminDeviceStatus.ACTIVE) {
                merged[item.id] = item
            }
        }

        // Preserve local current-device session while initial cloud write is in flight,
        // unless Firestore explicitly marked it REVOKED.
        val localCurrent = _sessions.value.firstOrNull { it.id == mySessionId || it.isCurrentDevice }
        if (localCurrent != null && !remoteById.containsKey(localCurrent.id)) {
            merged[localCurrent.id] = localCurrent
        }

        _sessions.value = merged.values
            .filter { it.status == AdminDeviceStatus.ACTIVE }
            .sortedWith(
                compareByDescending<ActiveDeviceSession> { it.isCurrentDevice }
                    .thenByDescending { it.lastActiveMillis }
            )
        saveLocalPrefs()
    }

    private fun mergeRemoteAlerts(remote: List<LoginSecurityAlert>) {
        val realSessionIds = remote.map { it.sessionId }.filter { it.isNotBlank() }.toSet()
        val byId = _alerts.value
            .filterNot { existing ->
                existing.id.startsWith(SYNTHETIC_ALERT_PREFIX) &&
                    realSessionIds.contains(existing.sessionId)
            }
            .associateBy { it.id }
            .toMutableMap()

        for (item in remote) {
            byId[item.id] = item
        }
        _alerts.value = byId.values
            .sortedByDescending { it.timestampMillis }
            .take(40)
        saveLocalPrefs()
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

    private fun loadFromLocalPrefs() {
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
                                    verifiedWithAuthenticator = obj.optBoolean("verifiedWithAuthenticator", false)
                                )
                            )
                        }
                    }
                }
                _alerts.value = list
                    .filter { it.isAdminPageEvent }
                    .sortedByDescending { it.timestampMillis }
                    .take(40)
            }
        }
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

    private fun buildSyntheticAlertId(sessionId: String, loginAtMillis: Long): String {
        val cleanSession = sanitizeIdPart(sessionId).take(64)
        val bucket = (loginAtMillis / 60_000L).coerceAtLeast(0L)
        return "${SYNTHETIC_ALERT_PREFIX}${cleanSession}_$bucket"
    }

    private companion object {
        const val TAG = "SecuritySessionRepo"
        const val PREFS_NAME = "alif_security_prefs"
        const val KEY_DEVICE_ID = "local_device_id"
        const val KEY_LOGIN_AT_MILLIS = "login_at_millis"
        const val KEY_LAST_AUTH_SIGNIN_TS = "last_auth_signin_ts"
        const val KEY_LAST_UID = "last_uid"
        const val KEY_SESSIONS_JSON = "sessions_json"
        const val KEY_ALERTS_JSON = "alerts_json"

        const val COL_ADMIN_SESSIONS = "adminSessions"
        const val COL_LOGIN_ALERTS = "loginAlerts"
        const val COL_ADMIN_LOGIN_ATTEMPTS = "adminLoginAttempts"
        const val COL_CONFIG = "config"
        const val DOC_SECURITY = "security"
        const val DOC_AUTHENTICATOR = "authenticator"
        const val MAX_TOTP_ATTEMPTS_BEFORE_COOLDOWN = 5
        const val TOTP_COOLDOWN_MILLIS = 30_000L
        const val HEARTBEAT_MIN_INTERVAL_MILLIS = 15_000L
        const val SYNTHETIC_ALERT_PREFIX = "sess_alert_"
        val ID_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
        val REQUIRED_SESSION_KEYS = setOf(
            "id",
            "uid",
            "deviceId",
            "deviceName",
            "platform",
            "email",
            "location",
            "loginAtMillis",
            "lastActiveMillis",
            "status",
            "createdAt",
            "updatedAt"
        )
        val ALLOWED_SESSION_KEYS = REQUIRED_SESSION_KEYS + "userAgent"
    }
}
