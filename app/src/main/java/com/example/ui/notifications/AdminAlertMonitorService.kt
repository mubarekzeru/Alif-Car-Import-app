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
import com.example.data.model.AdminDeviceStatus
import com.example.data.model.CustomerInquiry
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
 * Keeps real-time Firestore listeners alive in the background so customer inquiries
 * and new admin sign-ins (Mobile App & Website) trigger immediate heads-up notifications
 * even when the app is closed or the screen is off.
 */
class AdminAlertMonitorService : Service() {

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var loginAlertsListener: ListenerRegistration? = null
    private var adminSessionsListener: ListenerRegistration? = null
    private var inquiriesListener: ListenerRegistration? = null
    private var observedUid: String? = null
    private var initialSessionsLoaded: Boolean = false
    private val seenActiveSessionIds = mutableSetOf<String>()

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
        initialSessionsLoaded = false
        seenActiveSessionIds.clear()

        val db = runCatching {
            FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        }.getOrNull() ?: return

        val localDeviceId = applicationContext
            .getSharedPreferences(SECURITY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, "")
            .orEmpty()

        // 1. Live Admin Login Alerts listener (web & mobile sign-ins)
        loginAlertsListener = db.collection(COL_LOGIN_ALERTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documents.forEach { doc ->
                    val alert = parseAlert(doc) ?: return@forEach
                    if (alert.decision == LoginAlertDecision.PENDING) {
                        SecurityNotificationHelper.showLoginAlertNotification(applicationContext, alert)
                    }
                }
            }

        // 2. Live Admin Sessions listener (catches website/mobile logins even if alert doc wasn't written)
        adminSessionsListener = db.collection(COL_ADMIN_SESSIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val activeDocs = snapshot.documents.filter { doc ->
                    AdminDeviceStatus.fromValue(doc.getString("status")) == AdminDeviceStatus.ACTIVE
                }
                if (!initialSessionsLoaded) {
                    activeDocs.forEach { seenActiveSessionIds.add(it.id) }
                    initialSessionsLoaded = true
                    return@addSnapshotListener
                }
                activeDocs.forEach { doc ->
                    if (seenActiveSessionIds.add(doc.id)) {
                        val devId = doc.getString("deviceId").orEmpty()
                        if (localDeviceId.isNotBlank() && devId == localDeviceId) return@forEach
                        val loginMillis = (doc.get("loginAtMillis") as? Number)?.toLong()
                            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                            ?: System.currentTimeMillis()
                        val rawPlatform = doc.getString("platform").orEmpty().trim()
                        val platform = when {
                            rawPlatform.isBlank() ||
                                rawPlatform.equals("web", ignoreCase = true) ||
                                rawPlatform.equals("browser", ignoreCase = true) -> "Website"
                            else -> rawPlatform
                        }
                        val synthetic = LoginSecurityAlert(
                            id = "sess_alert_${doc.id.take(48)}_${loginMillis / 60_000L}",
                            sessionId = doc.id,
                            email = doc.getString("email").orEmpty().trim(),
                            deviceName = doc.getString("deviceName").orEmpty().trim().ifBlank { "Admin Device" },
                            platform = platform,
                            location = doc.getString("location").orEmpty().trim().ifBlank { "Unknown location" },
                            timestampMillis = loginMillis,
                            decision = LoginAlertDecision.PENDING
                        )
                        SecurityNotificationHelper.showLoginAlertNotification(applicationContext, synthetic)
                    }
                }
            }

        // 3. Live Customer Inquiries listener (website customer leads)
        inquiriesListener = db.collection(COL_INQUIRIES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val inquiries = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    CustomerInquiry(
                        id = (data["id"] as? String).orEmpty().ifBlank { doc.id },
                        name = (data["name"] as? String).orEmpty(),
                        phone = (data["phone"] as? String).orEmpty(),
                        email = (data["email"] as? String).orEmpty(),
                        vehicleTitle = (data["vehicleTitle"] as? String).orEmpty(),
                        message = (data["message"] as? String).orEmpty(),
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }
                SecurityNotificationHelper.showNewInquiriesIfNeeded(applicationContext, inquiries)
            }
    }

    private fun parseAlert(doc: DocumentSnapshot): LoginSecurityAlert? {
        val data = doc.data ?: return null
        val id = (data["id"] as? String).orEmpty().ifBlank { doc.id }
        val tsMillis = (data["timestampMillis"] as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        return LoginSecurityAlert(
            id = id,
            sessionId = (data["sessionId"] as? String).orEmpty(),
            email = (data["email"] as? String).orEmpty().trim(),
            deviceName = (data["deviceName"] as? String).orEmpty().trim().ifBlank { "Admin Device" },
            platform = (data["platform"] as? String).orEmpty().trim().ifBlank { "Website" },
            location = (data["location"] as? String).orEmpty().trim().ifBlank { "Unknown location" },
            timestampMillis = tsMillis,
            decision = LoginAlertDecision.fromValue(data["decision"] as? String)
        )
    }

    private fun detachListeners() {
        loginAlertsListener?.remove()
        loginAlertsListener = null
        adminSessionsListener?.remove()
        adminSessionsListener = null
        inquiriesListener?.remove()
        inquiriesListener = null
        observedUid = null
        initialSessionsLoaded = false
        seenActiveSessionIds.clear()
    }

    companion object {
        private const val TAG = "AdminAlertMonitorSvc"
        private const val MONITOR_NOTIFICATION_ID = 49101
        private const val ACTION_START = "com.example.action.START_ADMIN_ALERT_MONITOR"
        private const val ACTION_STOP = "com.example.action.STOP_ADMIN_ALERT_MONITOR"

        private const val SECURITY_PREFS_NAME = "alif_security_prefs"
        private const val KEY_DEVICE_ID = "local_device_id"
        private const val COL_LOGIN_ALERTS = "loginAlerts"
        private const val COL_ADMIN_SESSIONS = "adminSessions"
        private const val COL_INQUIRIES = "inquiries"

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
