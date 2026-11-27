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
    val isCurrentDevice: Boolean = false,
    val browser: String = ""
) {
    val isWebsiteSession: Boolean
        get() = isWebsitePlatform(platform)

    val resolvedBrowserLabel: String
        get() = resolveBrowserName(
            explicitBrowser = browser,
            userAgent = null,
            deviceName = deviceName,
            platform = platform
        )

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

        fun resolveBrowserName(
            explicitBrowser: String?,
            userAgent: String?,
            deviceName: String?,
            platform: String?
        ): String {
            val cleanedExplicit = explicitBrowser.orEmpty().trim()
            if (cleanedExplicit.isNotEmpty()) {
                detectBrowserFromText(cleanedExplicit)?.let { return it }
                if (cleanedExplicit.length <= 40 && !cleanedExplicit.startsWith("Mozilla/", ignoreCase = true)) {
                    return cleanedExplicit
                }
            }

            val ua = userAgent.orEmpty().trim()
            if (ua.isNotEmpty()) {
                detectBrowserFromUserAgent(ua)?.let { return it }
            }

            val device = deviceName.orEmpty().trim()
            if (device.isNotEmpty()) {
                detectBrowserFromText(device)?.let { return it }
            }

            val plat = platform.orEmpty().trim()
            if (plat.isNotEmpty()) {
                detectBrowserFromText(plat)?.let { return it }
            }

            return if (isWebsitePlatform(plat)) "Web Browser" else "Mobile App"
        }

        private fun detectBrowserFromUserAgent(ua: String): String? {
            val lower = ua.lowercase()
            return when {
                lower.contains("edg/") || lower.contains("edga/") || lower.contains("edgios/") || lower.contains("edge/") -> "Microsoft Edge"
                lower.contains("samsungbrowser/") -> "Samsung Internet"
                lower.contains("opr/") || lower.contains("opera") -> "Opera"
                lower.contains("brave") -> "Brave"
                lower.contains("vivaldi/") -> "Vivaldi"
                lower.contains("ucbrowser/") -> "UC Browser"
                lower.contains("yabrowser/") -> "Yandex Browser"
                lower.contains("firefox/") || lower.contains("fxios/") -> "Firefox"
                lower.contains("crios/") -> "Google Chrome"
                lower.contains("chrome/") || lower.contains("chromium/") -> "Google Chrome"
                lower.contains("safari/") -> "Safari"
                lower.contains("; wv)") -> "Android WebView"
                else -> null
            }
        }

        private fun detectBrowserFromText(text: String): String? {
            val lower = text.lowercase()
            return when {
                lower.contains("microsoft edge") || lower.contains("msedge") || Regex("\\bedge\\b").containsMatchIn(lower) -> "Microsoft Edge"
                lower.contains("samsung internet") || lower.contains("samsungbrowser") -> "Samsung Internet"
                lower.contains("opera") -> "Opera"
                lower.contains("brave") -> "Brave"
                lower.contains("vivaldi") -> "Vivaldi"
                lower.contains("uc browser") || lower.contains("ucbrowser") -> "UC Browser"
                lower.contains("yandex") -> "Yandex Browser"
                lower.contains("firefox") -> "Firefox"
                lower.contains("chrome") || lower.contains("chromium") -> "Google Chrome"
                lower.contains("safari") -> "Safari"
                lower.contains("webview") -> "Android WebView"
                else -> null
            }
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
    val decision: LoginAlertDecision = LoginAlertDecision.PENDING,
    val sourcePage: String = "admin",
    val isUnauthorizedAttempt: Boolean = false,
    val verifiedWithAuthenticator: Boolean = false
) {
    val isAdminPageEvent: Boolean
        get() = isAdminPageLoginEvent(
            sourcePage = sourcePage,
            platform = platform,
            eventType = if (isUnauthorizedAttempt) "admin_login_attempt" else "admin_login"
        )

    val isOwnerAuthenticatorVerified: Boolean
        get() = isOwnerAuthenticatorVerifiedLogin(
            email = email,
            verifiedWithAuthenticator = verifiedWithAuthenticator,
            isUnauthorizedAttempt = isUnauthorizedAttempt
        )

    companion object {
        const val ATTEMPT_TYPE_AUTHENTICATOR_VERIFIED = "authenticator_verified"

        fun isAuthenticatorVerifiedAttemptType(attemptType: String?): Boolean {
            val clean = attemptType.orEmpty().trim().lowercase()
            if (clean.isEmpty()) return false
            return clean.contains("authenticator") ||
                clean.contains("totp") ||
                clean.contains("2fa") ||
                clean.contains("mfa")
        }

        fun isOwnerAuthenticatorVerifiedLogin(
            email: String?,
            verifiedWithAuthenticator: Boolean,
            isUnauthorizedAttempt: Boolean
        ): Boolean {
            if (isUnauthorizedAttempt || !verifiedWithAuthenticator) return false
            return email.orEmpty().trim().equals(AdminAccessConfig.BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)
        }
        private val NON_ADMIN_PAGE_TOKENS = listOf(
            "visitor",
            "visitors",
            "home_dashboard",
            "homedashboard",
            "public_home",
            "storefront",
            "showroom",
            "catalog",
            "page_view",
            "pageview",
            "website_visit",
            "site_visit"
        )

        private val NON_ADMIN_EXACT_PAGES = setOf(
            "/",
            "home",
            "/home",
            "index",
            "/index",
            "dashboard",
            "/dashboard",
            "visitors",
            "/visitors",
            "visitor",
            "/visitor",
            "public",
            "/public",
            "inventory",
            "/inventory",
            "cars",
            "/cars",
            "vehicles",
            "/vehicles"
        )

        fun isAdminPageLoginEvent(
            sourcePage: String?,
            platform: String? = null,
            eventType: String? = null
        ): Boolean {
            val pageClean = sourcePage.orEmpty().trim().lowercase()
            val typeClean = eventType.orEmpty().trim().lowercase()
            val platformClean = platform.orEmpty().trim().lowercase()

            if (typeClean.isNotEmpty() && NON_ADMIN_PAGE_TOKENS.any { typeClean.contains(it) }) {
                return false
            }
            if (platformClean.isNotEmpty() && NON_ADMIN_PAGE_TOKENS.any { platformClean.contains(it) }) {
                return false
            }
            if (pageClean.isNotEmpty()) {
                if (pageClean.contains("admin")) return true
                if (NON_ADMIN_EXACT_PAGES.contains(pageClean)) return false
                if (NON_ADMIN_PAGE_TOKENS.any { pageClean.contains(it) }) return false
            }
            return true
        }
    }
}

data class PasswordChangeOutcome(
    val message: String
)

data class AuthenticatorStatus(
    val enabled: Boolean = false,
    val accountLabel: String = "",
    val updatedByEmail: String = "",
    val updatedAtMillis: Long = 0L,
    val isLoaded: Boolean = false
)

data class PendingAuthenticatorSetup(
    val secretBase32: String,
    val formattedSecret: String,
    val otpAuthUri: String,
    val accountEmail: String,
    val issuer: String
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
