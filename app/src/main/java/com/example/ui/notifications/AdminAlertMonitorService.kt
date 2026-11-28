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
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.SecuritySessionRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Lightweight foreground monitor that listens exclusively to bounded `loginAlerts` and
 * `adminLoginAttempts` documents in Firestore while the admin is signed in, so login alerts
 * and unauthorized email attempts from other devices or the website pop up immediately even
 * when the app is in the background.
 */
class AdminAlertMonitorService : Service() {

    private var loginAlertsReg: ListenerRegistration? = null
    private var attemptsReg: ListenerRegistration? = null
    private val seenIds = HashSet<String>()
    private var alertsInitialized = false
    private var attemptsInitialized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null || !SecurityNotificationHelper.isInstantBackgroundAlertsEnabled(applicationContext)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = SecurityNotificationHelper.buildLiveMonitorNotification(applicationContext)
        val startedForeground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to enter foreground for AdminAlertMonitorService", err)
        }.isSuccess

        if (!startedForeground) {
            stopSelf()
            return START_NOT_STICKY
        }

        attachListeners(currentUser.uid)
        return START_STICKY
    }

    private fun attachListeners(uid: String) {
        detachListeners()
        val mySessionId = SecuritySessionRepository.getCurrentSessionIdForDevice(applicationContext, uid)
        val db = FirestoreProvider.get(applicationContext)
        alertsInitialized = false
        attemptsInitialized = false
        synchronized(seenIds) { seenIds.clear() }

        loginAlertsReg = db.collection(SecuritySessionRepository.COLLECTION_LOGIN_ALERTS)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(15)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                val isInitial = !alertsInitialized
                alertsInitialized = true

                snapshot.documents.forEach { doc ->
                    val alert = SecuritySessionRepository.parseLoginAlertDoc(doc) ?: return@forEach
                    if (!alert.isAdminPageEvent) return@forEach
                    val isNew = synchronized(seenIds) { seenIds.add(alert.id) }
                    if (!isNew) return@forEach

                    val isFromOtherDeviceOrWebsite = alert.sessionId.isNotBlank() && alert.sessionId != mySessionId
                    val isRecent = alert.timestampMillis > 0L && (now - alert.timestampMillis) <= 5L * 60L * 1000L
                    if (isFromOtherDeviceOrWebsite && (!isInitial || isRecent)) {
                        SecurityNotificationHelper.showLoginAlertNotification(applicationContext, alert)
                    }
                }
            }

        attemptsReg = db.collection(SecuritySessionRepository.COLLECTION_ADMIN_LOGIN_ATTEMPTS)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(15)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                val isInitial = !attemptsInitialized
                attemptsInitialized = true

                snapshot.documents.forEach { doc ->
                    val alert = SecuritySessionRepository.parseAdminAttemptDoc(doc) ?: return@forEach
                    if (!alert.isAdminPageEvent) return@forEach
                    val isNew = synchronized(seenIds) { seenIds.add(alert.id) }
                    if (!isNew) return@forEach

                    val isRecent = alert.timestampMillis > 0L && (now - alert.timestampMillis) <= 10L * 60L * 1000L
                    if (!isInitial || isRecent) {
                        SecurityNotificationHelper.showLoginAlertNotification(applicationContext, alert)
                    }
                }
            }
    }

    private fun detachListeners() {
        runCatching { loginAlertsReg?.remove() }
        runCatching { attemptsReg?.remove() }
        loginAlertsReg = null
        attemptsReg = null
    }

    override fun onDestroy() {
        detachListeners()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdminAlertMonitorSvc"
        private const val FOREGROUND_NOTIFICATION_ID = 9001

        fun startIfEnabled(context: Context) {
            val appCtx = context.applicationContext
            if (Firebase.auth.currentUser == null ||
                !SecurityNotificationHelper.isInstantBackgroundAlertsEnabled(appCtx)
            ) {
                stop(appCtx)
                return
            }
            val intent = Intent(appCtx, AdminAlertMonitorService::class.java)
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure { err ->
                Log.w(TAG, "Deferred starting AdminAlertMonitorService (background restriction): ${err.message}")
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
