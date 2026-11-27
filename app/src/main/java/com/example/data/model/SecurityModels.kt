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
    val verifiedWithAuthenticator: Boolean = false,
    val failureReason: String = "",
    val attemptStatus: String = ""
) {
    val isAdminPageEvent: Boolean
        get() = isActualLoginAttemptEvent(
            sourcePage = sourcePage,
            platform = platform,
            eventType = if (isUnauthorizedAttempt) "admin_login_attempt" else "admin_login",
            status = attemptStatus,
            reason = failureReason,
            email = email,
            isFailedAttemptRecord = isUnauthorizedAttempt
        )

    val isOwnerAuthenticatorVerified: Boolean
        get() = isOwnerAuthenticatorVerifiedLogin(
            email = email,
            verifiedWithAuthenticator = verifiedWithAuthenticator,
            isUnauthorizedAttempt = isUnauthorizedAttempt
        )

    val isIncorrectCredentialsAttempt: Boolean
        get() = isUnauthorizedAttempt && (
            attemptStatus.equals("failed", ignoreCase = true) ||
                attemptStatus.equals("incorrect", ignoreCase = true) ||
                attemptStatus.equals("wrong_password", ignoreCase = true) ||
                failureReason.contains("password", ignoreCase = true) ||
                failureReason.contains("incorrect", ignoreCase = true) ||
                failureReason.contains("wrong", ignoreCase = true) ||
                failureReason.contains("invalid", ignoreCase = true)
            )

    fun isUnread(readAlertIds: Set<String>): Boolean {
        if (id in readAlertIds) return false
        if (isOwnerAuthenticatorVerified) return false
        return isUnauthorizedAttempt || decision == LoginAlertDecision.PENDING
    }

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
            "site_visit",
            "page_visit",
            "pagevisit",
            "page_open",
            "page_load",
            "site_open",
            "browse",
            "browsing",
            "heartbeat",
            "session_resume",
            "tab_focus",
            "opened_admin",
            "admin_visit",
            "viewed_admin"
        )

        private val PASSIVE_VISIT_REASON_TOKENS = listOf(
            "visited website",
            "visited the website",
            "website visit",
            "page visit",
            "page view",
            "visited admin page",
            "opened admin page",
            "viewed admin page",
            "loaded admin page",
            "browsing",
            "heartbeat"
        )

        private val FAILURE_OR_LOGIN_REASON_TOKENS = listOf(
            "wrong",
            "incorrect",
            "invalid",
            "failed",
            "denied",
            "unauthorized",
            "blocked",
            "password",
            "credential",
            "sign in",
            "sign-in",
            "signin",
            "log in",
            "log-in",
            "login",
            "auth",
            "not approved",
            "not allowed",
            "rejected"
        )

        private val NON_ADMIN_EXACT_PAGES = setOf(
            "/",
            "home",
            "/home",
            "index",
            "/index",
            "index.html",
            "/index.html",
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
            "/vehicles",
            "tiktok",
            "/tiktok",
            "videos",
            "/videos",
            "contact",
            "/contact",
            "about",
            "/about",
            "location",
            "/location",
            "showroom",
            "/showroom",
            "alifcarimport",
            "/alifcarimport"
        )

        private val PUBLIC_PATH_PREFIXES = listOf(
            "/inventory/",
            "/cars/",
            "/vehicles/",
            "/vehicle/",
            "/car/",
            "/tiktok/",
            "/videos/",
            "/contact/",
            "/about/",
            "/showroom/",
            "/visitors/",
            "/visitor/"
        )

        fun normalizePagePath(rawPage: String?): String {
            var cleaned = rawPage.orEmpty().trim().lowercase()
            if (cleaned.isEmpty()) return ""
            // Strip scheme + host if a full URL was logged (e.g. https://mubarekzeru.github.io/AlifCarImport/)
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                val afterScheme = cleaned.substringAfter("://")
                cleaned = if (afterScheme.contains('/')) {
                    "/" + afterScheme.substringAfter('/')
                } else {
                    "/"
                }
            }
            // Strip query string and hash unless hash itself carries a SPA route like #/admin
            val hashPart = cleaned.substringAfter('#', "")
            cleaned = cleaned.substringBefore('#').substringBefore('?')
            if (hashPart.startsWith("/")) {
                cleaned = hashPart.substringBefore('?')
            }
            // Strip GitHub Pages base path prefix if present
            if (cleaned == "/alifcarimport" || cleaned == "/alifcarimport/") {
                return "/"
            }
            if (cleaned.startsWith("/alifcarimport/")) {
                cleaned = "/" + cleaned.removePrefix("/alifcarimport/")
            }
            return cleaned.trimEnd('/').ifEmpty { "/" }
        }

        fun isPublicWebsiteVisitEvent(
            sourcePage: String?,
            platform: String? = null,
            eventType: String? = null,
            status: String? = null,
            reason: String? = null
        ): Boolean {
            val typeClean = eventType.orEmpty().trim().lowercase()
            val platformClean = platform.orEmpty().trim().lowercase()
            val statusClean = status.orEmpty().trim().lowercase()
            val reasonClean = reason.orEmpty().trim().lowercase()
            val normalizedPath = normalizePagePath(sourcePage)

            if (typeClean.isNotEmpty() && NON_ADMIN_PAGE_TOKENS.any { typeClean.contains(it) }) {
                return true
            }
            if (platformClean.isNotEmpty() && NON_ADMIN_PAGE_TOKENS.any { platformClean.contains(it) }) {
                return true
            }
            if (statusClean == "visit" || statusClean == "view" || statusClean == "page_view" || statusClean == "opened") {
                return true
            }
            if (reasonClean.isNotEmpty() && PASSIVE_VISIT_REASON_TOKENS.any { reasonClean.contains(it) }) {
                return true
            }
            if (normalizedPath.isNotEmpty()) {
                if (normalizedPath.contains("admin") || normalizedPath.contains("login") || normalizedPath.contains("signin") || normalizedPath.contains("auth")) {
                    return false
                }
                if (NON_ADMIN_EXACT_PAGES.contains(normalizedPath)) return true
                if (PUBLIC_PATH_PREFIXES.any { normalizedPath.startsWith(it) }) return true
                if (NON_ADMIN_PAGE_TOKENS.any { normalizedPath.contains(it) }) return true
            }
            return false
        }

        fun isAdminPageLoginEvent(
            sourcePage: String?,
            platform: String? = null,
            eventType: String? = null
        ): Boolean {
            if (isPublicWebsiteVisitEvent(sourcePage, platform, eventType)) {
                return false
            }
            val normalizedPath = normalizePagePath(sourcePage)
            val rawClean = sourcePage.orEmpty().trim().lowercase()
            if (rawClean.isNotEmpty()) {
                if (normalizedPath.contains("admin") ||
                    normalizedPath.contains("login") ||
                    normalizedPath.contains("signin") ||
                    normalizedPath.contains("auth") ||
                    rawClean.contains("admin")
                ) {
                    return true
                }
                // Any non-empty page path that is NOT an admin/login route (e.g. "/gallery", "/pricing")
                // is a public website page and must NOT trigger a login alert.
                return false
            }
            return true
        }

        /**
         * Returns true strictly when an event represents an actual login attempt
         * (either successful sign-in with correct credentials, OR failed/incorrect/blocked sign-in attempt)
         * and NOT merely visiting the public website or opening/viewing the admin page.
         */
        fun isActualLoginAttemptEvent(
            sourcePage: String?,
            platform: String? = null,
            eventType: String? = null,
            status: String? = null,
            reason: String? = null,
            email: String? = null,
            isFailedAttemptRecord: Boolean = false
        ): Boolean {
            if (isPublicWebsiteVisitEvent(sourcePage, platform, eventType, status, reason)) {
                return false
            }
            if (!isAdminPageLoginEvent(sourcePage, platform, eventType)) {
                return false
            }
            val statusClean = status.orEmpty().trim().lowercase()
            val reasonClean = reason.orEmpty().trim().lowercase()
            val emailClean = email.orEmpty().trim()

            // For failed/unauthorized attempt docs in adminLoginAttempts:
            // Ignore passive page-load "attempt" logs that have no submitted email and no failed credential reason.
            if (isFailedAttemptRecord) {
                val explicitFailureStatus = statusClean in setOf(
                    "failed",
                    "denied",
                    "incorrect",
                    "wrong_password",
                    "blocked",
                    "unauthorized"
                )
                val hasFailureReason = reasonClean.isNotEmpty() &&
                    FAILURE_OR_LOGIN_REASON_TOKENS.any { reasonClean.contains(it) }
                if (statusClean == "attempt" && emailClean.isEmpty() && !hasFailureReason) {
                    return false
                }
                if (emailClean.isEmpty() && !explicitFailureStatus && !hasFailureReason) {
                    return false
                }
            }
            return true
        }
    }
}

data class AuthenticatorStatus(
    val enabled: Boolean = false,
    val requireOnLogin: Boolean = false,
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
