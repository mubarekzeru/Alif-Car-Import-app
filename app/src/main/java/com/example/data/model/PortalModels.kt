package com.example.data.model

import com.google.firebase.Timestamp
import org.json.JSONObject
import java.util.Locale

data class BrandingConfig(
    val companyName: String = "Alif Car Import",
    val logoUrl: String = "",
    val tagline: String = "Trusted direct vehicle imports & showroom"
)

data class HeroConfig(
    val kicker: String = "DIRECT IMPORTS",
    val title: String = "Quality Vehicles Imported Direct",
    val description: String = "Browse verified arrivals with transparent pricing and inspection history.",
    val imageUrl: String = "",
    val ctaLabel: String = "Browse Inventory"
)

data class TikTokSectionConfig(
    val title: String = "TikTok Walkarounds",
    val description: String = "Watch video walkarounds and arrival updates straight from our lot.",
    val profileUrl: String = "",
    val handle: String = "@alifcarimport"
)

data class ContactConfig(
    val addressLine: String = "",
    val cityCountry: String = "",
    val hours: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val telegram: String = "",
    val instagram: String = "",
    val facebook: String = "",
    val x: String = "",
    val youtube: String = "",
    val snapchat: String = "",
    val linkedin: String = "",
    val pinterest: String = "",
    val threads: String = "",
    val reddit: String = "",
    val viber: String = "",
    val imo: String = "",
    val wechat: String = "",
    val discord: String = "",
    val twitch: String = "",
    val website: String = "",
    val email: String = "",
    val mapsUrl: String = "",
    val landmarkNote: String = ""
)

data class WebsiteVisitorStats(
    val totalVisits: Long = 0L,
    val todayVisits: Long = 0L,
    val last7DaysVisits: Long = 0L,
    val uniqueVisitors: Long = 0L,
    val lastVisitAtMillis: Long = 0L,
    val recentVisitRecords: List<PageVisitRecord> = emptyList(),
    val isLoaded: Boolean = false
)

data class ShowroomConfig(
    val branding: BrandingConfig = BrandingConfig(),
    val hero: HeroConfig = HeroConfig(),
    val tiktok: TikTokSectionConfig = TikTokSectionConfig(),
    val contact: ContactConfig = ContactConfig(),
    val updatedAt: Timestamp? = null
) {
    fun toApiJson(): JSONObject {
        val cleanBranding = JSONObject().apply {
            put("companyName", branding.companyName.trim().ifEmpty { "Alif Car Import" }.take(120))
            put("logoUrl", branding.logoUrl.trim().take(VehicleDraft.MAX_IMAGE_FIELD_LENGTH))
            put("tagline", branding.tagline.trim().take(240))
        }
        val cleanHero = JSONObject().apply {
            put("kicker", hero.kicker.trim().take(120))
            put("title", hero.title.trim().ifEmpty { "Quality Vehicles Imported Direct" }.take(200))
            put("description", hero.description.trim().take(1200))
            put("imageUrl", hero.imageUrl.trim().take(VehicleDraft.MAX_IMAGE_FIELD_LENGTH))
            put("ctaLabel", hero.ctaLabel.trim().ifEmpty { "Browse Inventory" }.take(80))
        }
        val cleanTikTokHeader = JSONObject().apply {
            put("title", tiktok.title.trim().ifEmpty { "TikTok Walkarounds" }.take(160))
            put("description", tiktok.description.trim().take(1200))
            put("profileUrl", tiktok.profileUrl.trim().take(500))
            put("handle", tiktok.handle.trim().take(80))
        }
        val cleanContact = JSONObject().apply {
            put("addressLine", contact.addressLine.trim().take(240))
            put("cityCountry", contact.cityCountry.trim().take(160))
            put("hours", contact.hours.trim().take(160))
            put("phone", contact.phone.trim().take(60))
            put("whatsapp", contact.whatsapp.trim().take(300))
            put("email", contact.email.trim().take(254))
            put("mapsUrl", contact.mapsUrl.trim().take(500))
            put("landmarkNote", contact.landmarkNote.trim().take(240))
            put("telegram", contact.telegram.trim().take(300))
            put("instagram", contact.instagram.trim().take(300))
            put("facebook", contact.facebook.trim().take(300))
            put("x", contact.x.trim().take(300))
            put("youtube", contact.youtube.trim().take(300))
            put("snapchat", contact.snapchat.trim().take(300))
            put("linkedin", contact.linkedin.trim().take(300))
            put("pinterest", contact.pinterest.trim().take(300))
            put("threads", contact.threads.trim().take(300))
            put("reddit", contact.reddit.trim().take(300))
            put("viber", contact.viber.trim().take(300))
            put("imo", contact.imo.trim().take(300))
            put("wechat", contact.wechat.trim().take(300))
            put("discord", contact.discord.trim().take(300))
            put("twitch", contact.twitch.trim().take(300))
            put("website", contact.website.trim().take(500))
        }
        return JSONObject().apply {
            put("branding", cleanBranding)
            put("hero", cleanHero)
            put("tiktokHeader", cleanTikTokHeader)
            put("contact", cleanContact)
            put("updatedAt", VehicleDraft.currentIsoTimestamp())
        }
    }
}

data class TikTokPost(
    val id: String = "",
    val title: String = "",
    val caption: String = "",
    val tiktokUrl: String = "",
    val coverImageUrl: String = "",
    val sortOrder: Long = 0L,
    val updatedAt: Timestamp? = null
)

data class TikTokPostDraft(
    val title: String,
    val caption: String,
    val tiktokUrl: String,
    val coverImageUrl: String,
    val sortOrder: Long
) {
    fun toApiJson(postId: String): JSONObject = JSONObject().apply {
        put("id", postId)
        put("title", title.trim().take(200))
        put("caption", caption.trim().take(2000))
        put("tiktokUrl", tiktokUrl.trim().take(500))
        put("coverImageUrl", coverImageUrl.trim().take(VehicleDraft.MAX_IMAGE_FIELD_LENGTH))
        put("sortOrder", sortOrder.coerceIn(0L, 1000L))
        put("updatedAt", VehicleDraft.currentIsoTimestamp())
    }
}

data class CustomerInquiry(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val vehicleTitle: String = "",
    val message: String = "",
    val createdAt: Timestamp? = null
)

data class AdminAccessConfig(
    val adminEmails: List<String> = emptyList(),
    val updatedAt: Timestamp? = null
) {
    sealed interface GrantEmailMergeResult {
        data class Added(val updatedEmails: List<String>, val grantedEmail: String) : GrantEmailMergeResult
        data class AlreadyAuthorized(val email: String) : GrantEmailMergeResult
        data class Invalid(val reason: String) : GrantEmailMergeResult
        data class LimitReached(val maxCount: Int) : GrantEmailMergeResult
    }

    companion object {
        const val BOOTSTRAP_OWNER_EMAIL = "alifcarimports@gmail.com"
        const val MAX_ADMIN_EMAILS = 20

        fun isValidAdminEmail(rawEmail: String): Boolean {
            val trimmed = rawEmail.trim()
            if (trimmed.length !in 3..254) return false
            if (trimmed.any { it.isWhitespace() }) return false
            val atIndex = trimmed.indexOf('@')
            if (atIndex <= 0 || atIndex != trimmed.lastIndexOf('@')) return false
            val domain = trimmed.substring(atIndex + 1)
            if (domain.length < 3 || !domain.contains('.')) return false
            if (domain.startsWith('.') || domain.endsWith('.') || domain.contains("..")) return false
            return true
        }

        fun normalizeEmails(emails: List<String>): List<String> = emails
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() && it.length <= 254 }
            .distinct()
            .take(MAX_ADMIN_EMAILS)

        fun isEmailAuthorized(rawEmail: String, allowlistedEmails: List<String>): Boolean {
            val normalized = rawEmail.trim().lowercase(Locale.US)
            if (normalized.isEmpty()) return false
            if (normalized == BOOTSTRAP_OWNER_EMAIL) return true
            return allowlistedEmails.any { it.trim().lowercase(Locale.US) == normalized }
        }

        fun mergeGrantedEmail(
            existingEmails: List<String>,
            rawEmail: String
        ): GrantEmailMergeResult {
            if (!isValidAdminEmail(rawEmail)) {
                return GrantEmailMergeResult.Invalid("This alert does not contain a valid email address.")
            }
            val normalizedTarget = rawEmail.trim().lowercase(Locale.US)
            val normalizedExisting = normalizeEmails(existingEmails)
            if (normalizedTarget == BOOTSTRAP_OWNER_EMAIL || normalizedTarget in normalizedExisting) {
                return GrantEmailMergeResult.AlreadyAuthorized(normalizedTarget)
            }
            if (normalizedExisting.size >= MAX_ADMIN_EMAILS) {
                return GrantEmailMergeResult.LimitReached(MAX_ADMIN_EMAILS)
            }
            return GrantEmailMergeResult.Added(
                updatedEmails = normalizedExisting + normalizedTarget,
                grantedEmail = normalizedTarget
            )
        }
    }
}
