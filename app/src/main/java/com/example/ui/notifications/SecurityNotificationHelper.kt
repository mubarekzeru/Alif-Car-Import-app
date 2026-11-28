package com.example.ui.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AdminAccessConfig
import com.example.data.model.LoginSecurityAlert

object SecurityNotificationHelper {
    const val CHANNEL_LOGIN_ALERTS = "admin_login_alerts_live"
    const val CHANNEL_LIVE_MONITOR = "admin_live_monitor"

    const val EXTRA_OPEN_NOTIFICATIONS_TAB = "extra_open_notifications_tab"
    const val EXTRA_GRANT_CANDIDATE_EMAIL = "extra_grant_candidate_email"
    const val TAB_LOGIN_ALERTS = "login_alerts"
    const val TAB_INQUIRIES = "inquiries"

    private const val PREFS_NAME = "alif_notification_seen_prefs"
    private const val KEY_NOTIFIED_ALERT_IDS = "notified_alert_ids"
    private const val KEY_INSTANT_ALERTS_ENABLED = "instant_background_alerts_enabled"

    fun isInstantBackgroundAlertsEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INSTANT_ALERTS_ENABLED, true)
    }

    fun setInstantBackgroundAlertsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INSTANT_ALERTS_ENABLED, enabled).apply()
    }

    fun clearLoginAlertNotifications(context: Context, alertIds: Collection<String>) {
        val manager = NotificationManagerCompat.from(context.applicationContext)
        alertIds.forEach { id ->
            if (id.isNotBlank()) {
                runCatching { manager.cancel(id.hashCode()) }
            }
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val loginChannel = NotificationChannel(
            CHANNEL_LOGIN_ALERTS,
            "Admin Page Login Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Instant heads-up alerts only when someone tries to log into the Admin Page"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val monitorChannel = NotificationChannel(
            CHANNEL_LIVE_MONITOR,
            "Live Admin Alert Connection",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps real-time admin login alerts connected in the background"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(loginChannel)
        manager.createNotificationChannel(monitorChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildLiveMonitorNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LIVE_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Alif Car Import • Admin Protection Active")
            .setContentText("Listening only for Admin Page login attempts")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    @Synchronized
    fun showLoginAlertNotification(context: Context, alert: LoginSecurityAlert) {
        if (!alert.isAdminPageEvent) return
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notified = prefs.getStringSet(KEY_NOTIFIED_ALERT_IDS, emptySet()) ?: emptySet()
        val sessionDedupKey = alert.sessionId
            .takeIf { it.isNotBlank() }
            ?.let { "sess_${it}_${alert.timestampMillis / 60_000L}" }
        if (notified.contains(alert.id) || (sessionDedupKey != null && notified.contains(sessionDedupKey))) {
            return
        }
        val keysToStore = buildList {
            add(alert.id)
            if (sessionDedupKey != null) add(sessionDedupKey)
        }
        prefs.edit()
            .putStringSet(KEY_NOTIFIED_ALERT_IDS, (notified + keysToStore).toList().takeLast(80).toSet())
            .apply()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS_TAB, TAB_LOGIN_ALERTS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canOfferGrant = alert.isUnauthorizedAttempt && AdminAccessConfig.isValidAdminEmail(alert.email)
        val isIncorrectCredentials = alert.isUnauthorizedAttempt && (
            alert.attemptStatus.equals("failed", ignoreCase = true) ||
                alert.attemptStatus.equals("incorrect", ignoreCase = true) ||
                alert.attemptStatus.equals("wrong_password", ignoreCase = true) ||
                alert.failureReason.contains("password", ignoreCase = true) ||
                alert.failureReason.contains("incorrect", ignoreCase = true) ||
                alert.failureReason.contains("wrong", ignoreCase = true) ||
                alert.failureReason.contains("invalid", ignoreCase = true)
            )

        val title = when {
            isIncorrectCredentials -> "Incorrect Login Attempt (${alert.platform})"
            alert.isUnauthorizedAttempt -> "Blocked Admin Login Attempt (${alert.platform})"
            alert.isOwnerAuthenticatorVerified -> "Admin Sign-In Alert (${alert.platform})"
            else -> "Admin Login Alert (${alert.platform})"
        }
        val body = when {
            isIncorrectCredentials && canOfferGrant ->
                "${alert.email.trim()} entered incorrect credentials • ${alert.deviceName} • ${alert.location}. Tap to review or grant admin access."
            canOfferGrant ->
                "${alert.email.trim()} tried to log in • ${alert.deviceName} • ${alert.location}. Tap to keep blocked or grant admin access."
            alert.isUnauthorizedAttempt ->
                "${alert.email.ifBlank { "Unknown account" }} tried to log in with incorrect details • ${alert.deviceName} • ${alert.location}."
            alert.isOwnerAuthenticatorVerified ->
                "${alert.email.trim()} signed in on ${alert.platform} • ${alert.deviceName} • ${alert.location}."
            else ->
                "${alert.email.ifBlank { "Admin" }} signed in on ${alert.platform} • ${alert.deviceName} • ${alert.location}. Tap to confirm if this was you."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LOGIN_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (canOfferGrant) {
            val grantIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_NOTIFICATIONS_TAB, TAB_LOGIN_ALERTS)
                putExtra(EXTRA_GRANT_CANDIDATE_EMAIL, alert.email.trim())
            }
            val grantPendingIntent = PendingIntent.getActivity(
                context,
                ("grant_${alert.id}").hashCode(),
                grantIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Grant Admin Access", grantPendingIntent)
        }

        val notification = builder.build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                alert.id.hashCode(),
                notification
            )
        }
    }
}
