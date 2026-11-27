package com.example.ui.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Retained for manifest/receiver compatibility after migrating data storage off Firestore.
 * Intentionally attaches zero Firestore listeners and immediately stops itself so no
 * Firestore reads can occur in the background.
 */
class AdminAlertMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun startIfEnabled(context: Context) {
            // Firestore background listeners removed to ensure zero Firestore quota usage.
            stop(context)
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, AdminAlertMonitorService::class.java))
            }
        }
    }
}
