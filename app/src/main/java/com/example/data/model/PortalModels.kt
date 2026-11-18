package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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
    val email: String = "",
    val mapsUrl: String = "",
    val landmarkNote: String = ""
)

data class ShowroomConfig(
    val branding: BrandingConfig = BrandingConfig(),
    val hero: HeroConfig = HeroConfig(),
    val tiktok: TikTokSectionConfig = TikTokSectionConfig(),
    val contact: ContactConfig = ContactConfig(),
    val updatedAt: Timestamp? = null
) {
    fun toFirestoreMap(): Map<String, Any> {
        val cleanBranding = mapOf(
            "companyName" to branding.companyName.trim().ifEmpty { "Alif Car Import" }.take(120),
            "logoUrl" to branding.logoUrl.trim().take(550_000),
            "tagline" to branding.tagline.trim().take(240)
        )
        val cleanHero = mapOf(
            "kicker" to hero.kicker.trim().take(120),
            "title" to hero.title.trim().ifEmpty { "Quality Vehicles Imported Direct" }.take(200),
            "description" to hero.description.trim().take(1200),
            "imageUrl" to hero.imageUrl.trim().take(550_000),
            "ctaLabel" to hero.ctaLabel.trim().ifEmpty { "Browse Inventory" }.take(80)
        )
        val cleanTikTok = mapOf(
            "title" to tiktok.title.trim().ifEmpty { "TikTok Walkarounds" }.take(160),
            "description" to tiktok.description.trim().take(1200),
            "profileUrl" to tiktok.profileUrl.trim().take(500),
            "handle" to tiktok.handle.trim().take(80)
        )
        val cleanContact = buildMap<String, Any> {
            put("addressLine", contact.addressLine.trim().take(240))
            put("cityCountry", contact.cityCountry.trim().take(160))
            put("hours", contact.hours.trim().take(160))
            put("phone", contact.phone.trim().take(60))
            put("whatsapp", contact.whatsapp.trim().take(200))
            put("email", contact.email.trim().take(254))
            put("mapsUrl", contact.mapsUrl.trim().take(500))
            put("landmarkNote", contact.landmarkNote.trim().take(240))
            contact.telegram.trim().take(200).takeIf { it.isNotEmpty() }?.let { put("telegram", it) }
            contact.instagram.trim().take(200).takeIf { it.isNotEmpty() }?.let { put("instagram", it) }
            contact.facebook.trim().take(200).takeIf { it.isNotEmpty() }?.let { put("facebook", it) }
            contact.x.trim().take(200).takeIf { it.isNotEmpty() }?.let { put("x", it) }
        }
        return mapOf(
            "branding" to cleanBranding,
            "hero" to cleanHero,
            "tiktok" to cleanTikTok,
            "contact" to cleanContact,
            "updatedAt" to FieldValue.serverTimestamp()
        )
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
    fun toCreateMap(postId: String): Map<String, Any> = mapOf(
        "id" to postId,
        "title" to title.trim().take(200),
        "caption" to caption.trim().take(2000),
        "tiktokUrl" to tiktokUrl.trim().take(500),
        "coverImageUrl" to coverImageUrl.trim().take(550_000),
        "sortOrder" to sortOrder.coerceIn(0L, 1000L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(200),
        "caption" to caption.trim().take(2000),
        "tiktokUrl" to tiktokUrl.trim().take(500),
        "coverImageUrl" to coverImageUrl.trim().take(550_000),
        "sortOrder" to sortOrder.coerceIn(0L, 1000L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
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
    companion object {
        const val BOOTSTRAP_OWNER_EMAIL = "alifcarimports@gmail.com"

        fun normalizeEmails(emails: List<String>): List<String> = emails
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() && it.length <= 254 }
            .distinct()
            .take(20)

        fun toFirestoreMap(emails: List<String>): Map<String, Any> = mapOf(
            "adminEmails" to normalizeEmails(emails),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
