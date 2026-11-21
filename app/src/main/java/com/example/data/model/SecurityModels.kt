package com.example.data.model

import com.google.firebase.Timestamp

enum class AdminDeviceStatus(val value: String, val label: String) {
    ACTIVE("active", "Active"),
    REVOKED("revoked", "Signed Out");

    companion object {
        fun fromValue(raw: String?): AdminDeviceStatus =
            entries.firstOrNull { it.value.equals(raw?.trim(), ignoreCase = true) } ?: ACTIVE
    }
}

enum class LoginAlertDecision(val value: String, val label: String) {
    PENDING("pending", "Awaiting Confirmation"),
    CONFIRMED_ME("confirmed_me", "Confirmed — It Was Me"),
    REJECTED_NOT_ME("rejected_not_me", "Blocked — Wasn't Me");

    companion object {
        fun fromValue(raw: String?): LoginAlertDecision =
            entries.firstOrNull { it.value.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class ActiveDeviceSession(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val email: String,
    val location: String,
    val loginAtMillis: Long,
    val lastActiveMillis: Long,
    val status: AdminDeviceStatus = AdminDeviceStatus.ACTIVE,
    val isCurrentDevice: Boolean = false
) {
    val isWebsiteSession: Boolean
        get() = isWebsitePlatform(platform)

    companion object {
        fun isWebsitePlatform(platform: String): Boolean {
            val normalized = platform.trim().lowercase()
            if (normalized.isEmpty()) return false
            if (normalized.contains("mobile") || normalized.contains("android") || normalized.contains("ios")) {
                return false
            }
            return normalized.contains("web") ||
                normalized.contains("browser") ||
                normalized.contains("site") ||
                normalized.contains("desktop") ||
                normalized.contains("portal") ||
                normalized.contains("chrome") ||
                normalized.contains("safari") ||
                normalized.contains("firefox") ||
                normalized.contains("edge")
        }
    }
}

data class LoginSecurityAlert(
    val id: String,
    val sessionId: String,
    val email: String,
    val deviceName: String,
    val platform: String,
    val location: String,
    val timestampMillis: Long,
    val decision: LoginAlertDecision = LoginAlertDecision.PENDING
)

data class PasswordChangeOutcome(
    val message: String
)

object SecurityLockHelper {
    const val TWENTY_FOUR_HOURS_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun isUnlockedAfter24Hours(
        loginAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (loginAtMillis <= 0L) return false
        return (nowMillis - loginAtMillis) >= TWENTY_FOUR_HOURS_MILLIS
    }

    fun remainingLockMillis(
        loginAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (loginAtMillis <= 0L) return TWENTY_FOUR_HOURS_MILLIS
        return (TWENTY_FOUR_HOURS_MILLIS - (nowMillis - loginAtMillis)).coerceAtLeast(0L)
    }

    fun formatUnlockCountdown(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "Unlocked"
        val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            "${hours}h ${minutes}m remaining"
        } else {
            "${minutes}m remaining"
        }
    }

    fun normalizeMillis(timestamp: Timestamp?): Long =
        timestamp?.toDate()?.time ?: System.currentTimeMillis()
}
