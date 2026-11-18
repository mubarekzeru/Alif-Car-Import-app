package com.example.data.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.AdminDeviceStatus
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.example.data.model.SecurityLockHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    private var remoteSessionsListener: ListenerRegistration? = null
    private var remoteAlertsListener: ListenerRegistration? = null
    private var cloudSyncPermitted = true

    init {
        loadFromLocalPrefs()
    }

    fun currentSessionId(uid: String? = auth.currentUser?.uid): String {
        val cleanUid = uid?.takeIf { it.isNotBlank() } ?: "anon"
        return "sess_${cleanUid.take(10)}_${localDeviceId.take(12)}"
    }

    fun startObservingCloudSecurity(
        uid: String,
        onNewAlert: (LoginSecurityAlert) -> Unit
    ) {
        stopObservingCloudSecurity()
        loadFromLocalPrefs()

        if (!cloudSyncPermitted) return

        val mySessionId = currentSessionId(uid)
        runCatching {
            remoteSessionsListener = db.collection(COL_ADMIN_SESSIONS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            cloudSyncPermitted = false
                            stopObservingCloudSecurity()
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener

                    val remoteList = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
                        val devId = (data["deviceId"] as? String).orEmpty()
                        val status = AdminDeviceStatus.fromValue(data["status"] as? String)
                        val loginMillis = (data["loginAtMillis"] as? Number)?.toLong()
                            ?: SecurityLockHelper.normalizeMillis(doc.getTimestamp("loginAt"))
                        val activeMillis = (data["lastActiveMillis"] as? Number)?.toLong() ?: loginMillis

                        if (id == mySessionId && status == AdminDeviceStatus.REVOKED) {
                            _isCurrentSessionRevoked.value = true
                        }

                        ActiveDeviceSession(
                            id = id,
                            deviceId = devId,
                            deviceName = (data["deviceName"] as? String).orEmpty().ifBlank { "Admin Device" },
                            platform = (data["platform"] as? String).orEmpty().ifBlank { "Website" },
                            email = (data["email"] as? String).orEmpty(),
                            location = (data["location"] as? String).orEmpty().ifBlank { "Unknown location" },
                            loginAtMillis = loginMillis,
                            lastActiveMillis = activeMillis,
                            status = status,
                            isCurrentDevice = devId == localDeviceId || id == mySessionId
                        )
                    }

                    mergeRemoteSessions(remoteList)
                }

            remoteAlertsListener = db.collection(COL_LOGIN_ALERTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            cloudSyncPermitted = false
                            stopObservingCloudSecurity()
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener

                    val existingIds = _alerts.value.map { it.id }.toSet()
                    val remoteAlerts = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
                        LoginSecurityAlert(
                            id = id,
                            sessionId = (data["sessionId"] as? String).orEmpty(),
                            email = (data["email"] as? String).orEmpty(),
                            deviceName = (data["deviceName"] as? String).orEmpty().ifBlank { "Admin Device" },
                            platform = (data["platform"] as? String).orEmpty().ifBlank { "Website" },
                            location = (data["location"] as? String).orEmpty().ifBlank { "Unknown location" },
                            timestampMillis = (data["timestampMillis"] as? Number)?.toLong()
                                ?: SecurityLockHelper.normalizeMillis(doc.getTimestamp("createdAt")),
                            decision = LoginAlertDecision.fromValue(data["decision"] as? String)
                        )
                    }

                    mergeRemoteAlerts(remoteAlerts)
                    remoteAlerts
                        .filter { it.decision == LoginAlertDecision.PENDING && !existingIds.contains(it.id) }
                        .forEach(onNewAlert)
                }
        }
    }

    fun stopObservingCloudSecurity() {
        remoteSessionsListener?.remove()
        remoteSessionsListener = null
        remoteAlertsListener?.remove()
        remoteAlertsListener = null
    }

    suspend fun registerCurrentDeviceSignIn(
        user: FirebaseUser,
        onNewLoginAlert: (LoginSecurityAlert) -> Unit
    ) {
        _isCurrentSessionRevoked.value = false
        val sessionId = currentSessionId(user.uid)
        val deviceName = buildDeviceDisplayName()
        val email = user.email.orEmpty()
        val now = System.currentTimeMillis()
        val authSignInTs = user.metadata?.lastSignInTimestamp ?: 0L
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
            isCurrentDevice = true
        )
        upsertLocalSession(updatedSession)

        if (isFreshSignIn) {
            val alert = LoginSecurityAlert(
                id = "alert_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                sessionId = sessionId,
                email = email,
                deviceName = deviceName,
                platform = "Mobile App",
                location = resolvedLocation,
                timestampMillis = now,
                decision = LoginAlertDecision.PENDING
            )
            prependLocalAlert(alert)
            onNewLoginAlert(alert)
            mirrorAlertToCloud(alert)
        }

        mirrorSessionToCloud(updatedSession)

        // Enrich real approximate city/country in background without requesting GPS permissions
        val ipLocation = fetchIpCityCountry()
        if (ipLocation != null && ipLocation != updatedSession.location) {
            val enrichedSession = updatedSession.copy(location = ipLocation)
            upsertLocalSession(enrichedSession)
            mirrorSessionToCloud(enrichedSession)
            if (isFreshSignIn) {
                val updatedAlerts = _alerts.value.map { alert ->
                    if (alert.sessionId == sessionId && alert.timestampMillis == now) {
                        alert.copy(location = ipLocation)
                    } else alert
                }
                _alerts.value = updatedAlerts
                saveLocalPrefs()
            }
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

    fun deactivateOtherSession(sessionId: String): Result<Unit> {
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

        revokeSessionImmediate(sessionId)
        return Result.success(Unit)
    }

    private fun revokeSessionImmediate(sessionId: String) {
        val currentId = currentSessionId()
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(status = AdminDeviceStatus.REVOKED)
            } else session
        }.filter { it.status == AdminDeviceStatus.ACTIVE }
        saveLocalPrefs()

        if (cloudSyncPermitted) {
            runCatching {
                db.collection(COL_ADMIN_SESSIONS).document(sessionId)
                    .update(
                        mapOf(
                            "status" to AdminDeviceStatus.REVOKED.value,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
            }
        }

        if (sessionId == currentId) {
            _isCurrentSessionRevoked.value = true
        }
    }

    suspend fun changePasswordWith24hGuard(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
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

        return try {
            val credential = EmailAuthProvider.getCredential(email, oldPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Result.success("Password changed successfully.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalStateException("Previous password is incorrect. If you use Google Sign-In only, manage your password in Google Account settings."))
        } catch (e: Exception) {
            Log.w(TAG, "Password change failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "Unable to change password right now."
                )
            )
        }
    }

    fun markCurrentSessionSignedOut() {
        val myId = currentSessionId()
        if (cloudSyncPermitted) {
            runCatching {
                db.collection(COL_ADMIN_SESSIONS).document(myId)
                    .update(
                        mapOf(
                            "status" to AdminDeviceStatus.REVOKED.value,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
            }
        }
        _sessions.value = _sessions.value.filterNot { it.id == myId }
        prefs.edit()
            .remove(KEY_LOGIN_AT_MILLIS)
            .remove(KEY_LAST_AUTH_SIGNIN_TS)
            .apply()
        _currentLoginAtMillis.value = 0L
        _isCurrentSessionRevoked.value = false
        saveLocalPrefs()
    }

    private fun mirrorSessionToCloud(session: ActiveDeviceSession) {
        if (!cloudSyncPermitted) return
        runCatching {
            val payload = mapOf(
                "id" to session.id,
                "deviceId" to session.deviceId,
                "deviceName" to session.deviceName,
                "platform" to session.platform,
                "email" to session.email,
                "location" to session.location,
                "loginAtMillis" to session.loginAtMillis,
                "lastActiveMillis" to session.lastActiveMillis,
                "status" to session.status.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection(COL_ADMIN_SESSIONS).document(session.id).set(payload)
        }
    }

    private fun mirrorAlertToCloud(alert: LoginSecurityAlert) {
        if (!cloudSyncPermitted) return
        runCatching {
            val payload = mapOf(
                "id" to alert.id,
                "sessionId" to alert.sessionId,
                "email" to alert.email,
                "deviceName" to alert.deviceName,
                "platform" to alert.platform,
                "location" to alert.location,
                "timestampMillis" to alert.timestampMillis,
                "decision" to alert.decision.value,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection(COL_LOGIN_ALERTS).document(alert.id).set(payload)
        }
    }

    private fun mirrorAlertDecisionToCloud(alert: LoginSecurityAlert) {
        if (!cloudSyncPermitted) return
        runCatching {
            db.collection(COL_LOGIN_ALERTS).document(alert.id)
                .update(mapOf("decision" to alert.decision.value))
        }
    }

    private fun mergeRemoteSessions(remote: List<ActiveDeviceSession>) {
        val localById = _sessions.value.associateBy { it.id }.toMutableMap()
        for (item in remote) {
            if (item.status == AdminDeviceStatus.REVOKED) {
                localById.remove(item.id)
            } else {
                localById[item.id] = item
            }
        }
        _sessions.value = localById.values
            .filter { it.status == AdminDeviceStatus.ACTIVE }
            .sortedWith(
                compareByDescending<ActiveDeviceSession> { it.isCurrentDevice }
                    .thenByDescending { it.lastActiveMillis }
            )
        saveLocalPrefs()
    }

    private fun mergeRemoteAlerts(remote: List<LoginSecurityAlert>) {
        val byId = _alerts.value.associateBy { it.id }.toMutableMap()
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
                            add(
                                ActiveDeviceSession(
                                    id = id,
                                    deviceId = devId,
                                    deviceName = obj.optString("deviceName", "Android Device"),
                                    platform = obj.optString("platform", "Mobile App"),
                                    email = obj.optString("email"),
                                    location = obj.optString("location", fallbackLocaleLocation()),
                                    loginAtMillis = obj.optLong("loginAtMillis"),
                                    lastActiveMillis = obj.optLong("lastActiveMillis"),
                                    status = status,
                                    isCurrentDevice = devId == localDeviceId
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
                                    decision = LoginAlertDecision.fromValue(obj.optString("decision"))
                                )
                            )
                        }
                    }
                }
                _alerts.value = list.sortedByDescending { it.timestampMillis }.take(40)
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
    }
}
