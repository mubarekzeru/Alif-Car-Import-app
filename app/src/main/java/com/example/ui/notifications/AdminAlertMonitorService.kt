package com.example.ui.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.auth.isUserVerified
import com.example.data.model.LoginAlertDecision
import com.example.data.model.LoginSecurityAlert
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Keeps real-time Firestore listeners alive in the background so Admin Page sign-ins
 * and failed/unauthorized Admin Page login attempts trigger immediate heads-up notifications
 * even when the app is closed or the screen is off.
 * Public website home dashboard / visitor page activity does NOT trigger notifications.
 */
class AdminAlertMonitorService : Service() {

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var loginAlertsListener: ListenerRegistration? = null
    private var adminAttemptsListener: ListenerRegistration? = null
    private var observedUid: String? = null
    private var initialAttemptsLoaded: Boolean = false
    private val seenAttemptIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            detachListeners()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!SecurityNotificationHelper.isInstantBackgroundAlertsEnabled(this)) {
            detachListeners()
            stopSelf()
            return START_NOT_STICKY
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null || !isUserVerified(currentUser)) {
            detachListeners()
            stopSelf()
            return START_NOT_STICKY
        }

        val promoted = runCatching {
            val notification = SecurityNotificationHelper.buildLiveMonitorNotification(this)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
            ServiceCompat.startForeground(this, MONITOR_NOTIFICATION_ID, notification, serviceType)
        }.isSuccess

        if (!promoted) {
            Log.w(TAG, "Unable to promote AdminAlertMonitorService to foreground")
            stopSelf()
            return START_NOT_STICKY
        }

        ensureAuthObserver()
        if (observedUid != currentUser.uid) {
            attachFirestoreListeners(currentUser.uid)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        detachListeners()
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        authListener = null
        super.onDestroy()
    }

    private fun ensureAuthObserver() {
        if (authListener != null) return
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null || !isUserVerified(user) || !SecurityNotificationHelper.isInstantBackgroundAlertsEnabled(this)) {
                detachListeners()
                stopSelf()
            } else if (observedUid != user.uid) {
                attachFirestoreListeners(user.uid)
            }
        }
        authListener = listener
        Firebase.auth.addAuthStateListener(listener)
    }

    private fun attachFirestoreListeners(uid: String) {
        detachListeners()
        observedUid = uid
        initialAttemptsLoaded = false
        seenAttemptIds.clear()

        val db = runCatching {
            FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        }.getOrNull() ?: return

        // 1. Live Admin Login Alerts listener (actual sign-in attempts with correct credentials)
        loginAlertsListener = db.collection(COL_LOGIN_ALERTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documents.forEach { doc ->
                    val alert = parseAlert(doc) ?: return@forEach
                    if ((alert.decision == LoginAlertDecision.PENDING || alert.isUnauthorizedAttempt) &&
                        !alert.isOwnerAuthenticatorVerified &&
                        alert.isAdminPageEvent
                    ) {
                        SecurityNotificationHelper.showLoginAlertNotification(applicationContext, alert)
                    }
                }
            }

        // 2. Live Admin Login Attempts listener (actual login attempts with incorrect credentials or unauthorized email)
        adminAttemptsListener = db.collection(COL_ADMIN_LOGIN_ATTEMPTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val docs = snapshot.documents
                if (!initialAttemptsLoaded) {
                    docs.forEach { seenAttemptIds.add(it.id) }
                    initialAttemptsLoaded = true
                    return@addSnapshotListener
                }
                docs.forEach { doc ->
                    if (seenAttemptIds.add(doc.id)) {
                        val alert = parseAttemptAsAlert(doc) ?: return@forEach
                        if (alert.isAdminPageEvent) {
                            SecurityNotificationHelper.showLoginAlertNotification(applicationContext, alert)
                        }
                    }
                }
            }
    }

    private fun parseAlert(doc: DocumentSnapshot): LoginSecurityAlert? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val sourcePage = (data["sourcePage"] as? String)
            ?: (data["page"] as? String)
            ?: "admin"
        val attemptType = (data["attemptType"] as? String).orEmpty()
        val status = (data["status"] as? String).orEmpty()
        val reason = (data["reason"] as? String).orEmpty().trim()
        val platform = (data["platform"] as? String).orEmpty().trim().ifBlank { "Website Admin" }
        val email = (data["email"] as? String).orEmpty().trim()
        val isUnauthorized = attemptType.contains("failed", ignoreCase = true) ||
            attemptType.contains("denied", ignoreCase = true) ||
            attemptType.contains("unauthorized", ignoreCase = true) ||
            attemptType.contains("incorrect", ignoreCase = true) ||
            status.equals("failed", ignoreCase = true) ||
            status.equals("denied", ignoreCase = true) ||
            status.equals("incorrect", ignoreCase = true)
        if (!LoginSecurityAlert.isActualLoginAttemptEvent(
                sourcePage = sourcePage,
                platform = platform,
                eventType = attemptType.ifBlank { if (isUnauthorized) "admin_login_attempt" else "admin_login" },
                status = status,
                reason = reason,
                email = email,
                isFailedAttemptRecord = isUnauthorized
            )
        ) {
            return null
        }
        val tsMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        val verifiedByAuth = !isUnauthorized && (
            (data["authenticatorVerified"] as? Boolean == true) ||
                LoginSecurityAlert.isAuthenticatorVerifiedAttemptType(attemptType)
            )
        val rawDecision = LoginAlertDecision.fromValue(data["decision"] as? String)
        val effectiveDecision = if (verifiedByAuth &&
            email.equals(com.example.data.model.AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)
        ) {
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
            verifiedWithAuthenticator = verifiedByAuth,
            failureReason = reason,
            attemptStatus = status
        )
    }

    private fun parseAttemptAsAlert(doc: DocumentSnapshot): LoginSecurityAlert? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val sourcePage = (data["sourcePage"] as? String)
            ?: (data["page"] as? String)
            ?: ""
        val platform = (data["platform"] as? String).orEmpty().trim().ifBlank { "Website Admin" }
        val status = (data["status"] as? String).orEmpty().trim()
        val reason = (data["reason"] as? String).orEmpty().trim()
        val attemptType = (data["attemptType"] as? String).orEmpty().trim()
        val email = (data["email"] as? String).orEmpty().trim()
        if (!LoginSecurityAlert.isActualLoginAttemptEvent(
                sourcePage = sourcePage,
                platform = platform,
                eventType = attemptType.ifBlank { "admin_login_attempt" },
                status = status,
                reason = reason,
                email = email,
                isFailedAttemptRecord = true
            )
        ) {
            return null
        }
        val tsMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        return LoginSecurityAlert(
            id = "attempt_$id",
            sessionId = (data["sessionId"] as? String).orEmpty(),
            email = email,
            deviceName = (data["deviceName"] as? String).orEmpty().trim().ifBlank { "Unknown Device" },
            platform = platform,
            location = (data["location"] as? String).orEmpty().trim().ifBlank { "Unknown location" },
            timestampMillis = tsMillis,
            decision = LoginAlertDecision.REJECTED_NOT_ME,
            sourcePage = sourcePage.ifBlank { "/admin" },
            isUnauthorizedAttempt = true,
            failureReason = reason,
            attemptStatus = status
        )
    }

    private fun detachListeners() {
        loginAlertsListener?.remove()
        loginAlertsListener = null
        adminAttemptsListener?.remove()
        adminAttemptsListener = null
        observedUid = null
        initialAttemptsLoaded = false
        seenAttemptIds.clear()
    }

    companion object {
        private const val TAG = "AdminAlertMonitorSvc"
        private const val MONITOR_NOTIFICATION_ID = 49101
        private const val ACTION_START = "com.example.action.START_ADMIN_ALERT_MONITOR"
        private const val ACTION_STOP = "com.example.action.STOP_ADMIN_ALERT_MONITOR"

        private const val COL_LOGIN_ALERTS = "loginAlerts"
        private const val COL_ADMIN_LOGIN_ATTEMPTS = "adminLoginAttempts"

        fun startIfEnabled(context: Context) {
            val appCtx = context.applicationContext
            if (!SecurityNotificationHelper.isInstantBackgroundAlertsEnabled(appCtx)) return
            val user = Firebase.auth.currentUser
            if (user == null || !isUserVerified(user)) return

            runCatching {
                val intent = Intent(appCtx, AdminAlertMonitorService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure { err ->
                Log.w(TAG, "Unable to start AdminAlertMonitorService: ${err.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, AdminAlertMonitorService::class.java))
            }
        }
    }
}
