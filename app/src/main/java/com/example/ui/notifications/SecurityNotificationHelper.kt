package com.example.ui.notifications

import android.Manifest
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
import com.example.data.model.CustomerInquiry
import com.example.data.model.LoginSecurityAlert

object SecurityNotificationHelper {
    const val CHANNEL_LOGIN_ALERTS = "admin_login_alerts"
    const val CHANNEL_INQUIRIES = "customer_inquiries"

    const val EXTRA_OPEN_NOTIFICATIONS_TAB = "extra_open_notifications_tab"
    const val TAB_LOGIN_ALERTS = "login_alerts"
    const val TAB_INQUIRIES = "inquiries"

    private const val PREFS_NAME = "alif_notification_seen_prefs"
    private const val KEY_SEEN_INQUIRY_IDS = "seen_inquiry_ids"
    private const val KEY_NOTIFIED_ALERT_IDS = "notified_alert_ids"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val loginChannel = NotificationChannel(
            CHANNEL_LOGIN_ALERTS,
            "Admin Login Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Confirm or block admin sign-ins on web and mobile app"
        }

        val inquiryChannel = NotificationChannel(
            CHANNEL_INQUIRIES,
            "Customer Inquiries",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when visitors submit vehicle inquiries on the website"
        }

        manager.createNotificationChannel(loginChannel)
        manager.createNotificationChannel(inquiryChannel)
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

    fun showLoginAlertNotification(context: Context, alert: LoginSecurityAlert) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notified = prefs.getStringSet(KEY_NOTIFIED_ALERT_IDS, emptySet()) ?: emptySet()
        if (notified.contains(alert.id)) return
        prefs.edit()
            .putStringSet(KEY_NOTIFIED_ALERT_IDS, (notified + alert.id).toList().takeLast(50).toSet())
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

        val title = "New Admin Sign-In (${alert.platform})"
        val body = "${alert.email.ifBlank { "Admin" }} • ${alert.deviceName} • ${alert.location}. Tap to confirm if this was you."

        val notification = NotificationCompat.Builder(context, CHANNEL_LOGIN_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                alert.id.hashCode(),
                notification
            )
        }
    }

    fun showNewInquiriesIfNeeded(context: Context, inquiries: List<CustomerInquiry>) {
        if (inquiries.isEmpty()) return
        ensureChannels(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasBaseline = prefs.contains(KEY_SEEN_INQUIRY_IDS)
        val seen = prefs.getStringSet(KEY_SEEN_INQUIRY_IDS, emptySet()) ?: emptySet()
        val allIds = inquiries.map { it.id }.take(100).toSet()

        if (!hasBaseline) {
            prefs.edit().putStringSet(KEY_SEEN_INQUIRY_IDS, allIds).apply()
            return
        }

        val fresh = inquiries.filter { !seen.contains(it.id) }
        if (fresh.isEmpty()) return

        prefs.edit().putStringSet(KEY_SEEN_INQUIRY_IDS, (seen + allIds).take(150).toSet()).apply()
        if (!hasNotificationPermission(context)) return

        val latest = fresh.first()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS_TAB, TAB_INQUIRIES)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            latest.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (fresh.size == 1) {
            "New Customer Inquiry from ${latest.name.ifBlank { "Visitor" }}"
        } else {
            "${fresh.size} New Customer Inquiries"
        }
        val vehiclePart = latest.vehicleTitle.takeIf { it.isNotBlank() }?.let { "$it — " } ?: ""
        val body = "$vehiclePart${latest.message.ifBlank { latest.phone }}"

        val notification = NotificationCompat.Builder(context, CHANNEL_INQUIRIES)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                ("inq_" + latest.id).hashCode(),
                notification
            )
        }
    }
}
