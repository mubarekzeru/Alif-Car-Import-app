package com.example

import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.ui.components.allResolvedPhotoUrls
import com.example.ui.components.formatCompactCurrency
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatMileageDisplay
import com.example.ui.components.formatOptionalCurrency
import com.example.ui.components.resolvedPhotoSlots
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun vehicleStatusFromValue_handlesCaseAndUnknownSafely() {
        assertEquals(VehicleStatus.AVAILABLE, VehicleStatus.fromValue("available"))
        assertEquals(VehicleStatus.RESERVED, VehicleStatus.fromValue("RESERVED"))
        assertEquals(VehicleStatus.SOLD, VehicleStatus.fromValue("Sold"))
        assertEquals(VehicleStatus.AVAILABLE, VehicleStatus.fromValue("unexpected"))
    }

    @Test
    fun vehicleDraftToCreateMap_matchesWebsiteVehicleSchema() {
        val draft = VehicleDraft(
            title = "",
            make = " Toyota ",
            model = "Land Cruiser",
            year = 2024L,
            price = 89500L,
            originalPrice = null,
            mileage = "12,400",
            transmission = "Automatic",
            fuelType = "Hybrid",
            color = "Pearl White",
            origin = "Japan",
            imageUrl = "https://example.com/lc300.jpg",
            galleryImages = emptyList(),
            description = "",
            tiktokUrl = "",
            status = VehicleStatus.RESERVED,
            featured = true
        )

        val map = draft.toCreateMap("veh_2024_toyota_land_cruiser_ab12cd", "2026-11-17T10:00:00.000Z")
        assertEquals("veh_2024_toyota_land_cruiser_ab12cd", map["id"])
        assertEquals("2024 Toyota Land Cruiser", map["title"])
        assertEquals("Toyota", map["make"])
        assertEquals("Land Cruiser", map["model"])
        assertEquals(2024L, map["year"])
        assertEquals(89500L, map["price"])
        assertEquals("12,400", map["mileage"])
        assertEquals("Automatic", map["transmission"])
        assertEquals("Hybrid", map["fuelType"])
        assertEquals("Pearl White", map["color"])
        assertEquals("Japan", map["origin"])
        assertEquals("https://example.com/lc300.jpg", map["imageUrl"])
        assertEquals("2024 Toyota Land Cruiser — Automatic • Hybrid • Japan", map["description"])
        assertEquals("", map["tiktokUrl"])
        assertEquals("reserved", map["status"])
        assertEquals(true, map["featured"])
        assertEquals("2026-11-17T10:00:00.000Z", map["createdAt"])
        assertTrue(map.containsKey("updatedAt"))
        assertFalse(map.containsKey("originalPrice"))
        assertFalse(map.containsKey("galleryImages"))
    }

    @Test
    fun vehicleDraftToUpdateMap_includesOptionalFieldsOrDeleteSentinels() {
        val draft = VehicleDraft(
            title = "2024 Lexus RX 350 F Sport",
            make = "Lexus",
            model = "RX 350",
            year = 2024L,
            price = 68500L,
            originalPrice = 72000L,
            mileage = "8,200 km",
            transmission = "Automatic",
            fuelType = "Petrol",
            color = "Obsidian",
            origin = "UAE",
            imageUrl = "data:image/jpeg;base64,/9j/4AAQ",
            galleryImages = listOf("https://example.com/rx1.jpg"),
            description = "Clean import with full service history.",
            tiktokUrl = "https://www.tiktok.com/@alifcarimport/video/123",
            status = VehicleStatus.AVAILABLE,
            featured = false
        )

        val map = draft.toUpdateMap()
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("createdAt"))
        assertEquals(72000L, map["originalPrice"])
        assertEquals(listOf("https://example.com/rx1.jpg"), map["galleryImages"])
        assertTrue(map["updatedAt"] is FieldValue)

        val clearedDraft = draft.copy(originalPrice = null, galleryImages = emptyList())
        val clearedMap = clearedDraft.toUpdateMap()
        assertTrue(clearedMap["originalPrice"] is FieldValue)
        assertTrue(clearedMap["galleryImages"] is FieldValue)
    }

    @Test
    fun formatters_formatCurrencyAndMileageCleanly() {
        assertEquals("ETB 89,500", formatCurrency(89500L))
        assertEquals("ETB 1.25M", formatCompactCurrency(1_250_000L))
        assertEquals(null, formatOptionalCurrency(0L))
        assertEquals("ETB 2,500,000", formatOptionalCurrency(2_500_000L))
        assertEquals("12,400 km", formatMileageDisplay("12,400"))
        assertEquals("8,200 mi", formatMileageDisplay("8,200 mi"))
        assertEquals("0 km", formatMileageDisplay("  "))
    }

    @Test
    fun portalModels_matchWebsiteFirestoreRulesSchema() {
        val showroom = com.example.data.model.ShowroomConfig(
            branding = com.example.data.model.BrandingConfig(companyName = " ", logoUrl = "", tagline = "Tag"),
            contact = com.example.data.model.ContactConfig(phone = "+251911000000", telegram = "@alifcar")
        ).toFirestoreMap()
        val branding = showroom["branding"] as Map<*, *>
        val contact = showroom["contact"] as Map<*, *>
        assertEquals("Alif Car Import", branding["companyName"])
        assertEquals("@alifcar", contact["telegram"])
        assertFalse(contact.containsKey("instagram"))
        assertTrue(showroom["updatedAt"] is FieldValue)

        val ttCreate = com.example.data.model.TikTokPostDraft(
            title = "LC300 Arrival",
            caption = "Fresh arrival",
            tiktokUrl = "https://www.tiktok.com/@alif/video/1",
            coverImageUrl = "",
            sortOrder = 2L
        ).toCreateMap("tt_lc300_123456")
        assertEquals("tt_lc300_123456", ttCreate["id"])
        assertEquals(2L, ttCreate["sortOrder"])
        assertTrue(ttCreate["updatedAt"] is FieldValue)

        val normalized = com.example.data.model.AdminAccessConfig.normalizeEmails(
            listOf(" User@Example.com ", "user@example.com", "", "Admin@Site.com")
        )
        assertEquals(listOf("user@example.com", "admin@site.com"), normalized)
    }

    @Test
    fun vehicleDraftWithBlankFields_producesValidFirestoreSchema() {
        val blankDraft = VehicleDraft(
            title = "",
            make = "",
            model = "",
            year = 2024L,
            price = 0L,
            imageUrl = "data:image/jpeg;base64,/9j/4AAQ",
            transmission = "",
            fuelType = "",
            mileage = "",
            origin = "",
            color = "",
            description = ""
        )

        val map = blankDraft.toCreateMap("veh_blank_test_123", "2026-11-17T10:00:00.000Z")
        assertEquals("New Vehicle Arrival", map["title"])
        assertEquals("Vehicle", map["make"])
        assertEquals("Import", map["model"])
        assertEquals("New Vehicle Arrival", map["description"])
        assertEquals(2024L, map["year"])
        assertEquals(0L, map["price"])
    }

    @Test
    fun securityLockHelper_enforcesExact24HourUnlockWindow() {
        val loginAt = 1_700_000_000_000L
        val after12Hours = loginAt + (12L * 60L * 60L * 1000L)
        val after24Hours = loginAt + com.example.data.model.SecurityLockHelper.TWENTY_FOUR_HOURS_MILLIS

        assertFalse(com.example.data.model.SecurityLockHelper.isUnlockedAfter24Hours(loginAt, after12Hours))
        assertTrue(com.example.data.model.SecurityLockHelper.isUnlockedAfter24Hours(loginAt, after24Hours))
        assertEquals(
            "12h 0m remaining",
            com.example.data.model.SecurityLockHelper.formatUnlockCountdown(
                com.example.data.model.SecurityLockHelper.remainingLockMillis(loginAt, after12Hours)
            )
        )
        assertEquals(
            "Unlocked",
            com.example.data.model.SecurityLockHelper.formatUnlockCountdown(
                com.example.data.model.SecurityLockHelper.remainingLockMillis(loginAt, after24Hours)
            )
        )
    }

    @Test
    fun decodeImageModel_resolvesWebsiteUploadsNextImageAndFallbacks() {
        assertEquals(null, com.example.ui.components.decodeImageModel(""))
        assertEquals(null, com.example.ui.components.decodeImageModel("https://alifcarimport.com/placeholder.jpg"))
        assertEquals(null, com.example.ui.components.decodeImageModel("/placeholder.jpg"))
        assertEquals(
            "https://example.com/car.jpg",
            com.example.ui.components.decodeImageModel("https://example.com/car.jpg")
        )
        assertEquals(
            "https://alifcarimport.com/uploads/leopard5.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("/uploads/leopard5.jpg")
        )
        assertEquals(
            "https://alifcarimport.com/uploads/Leopard%205.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("/uploads/Leopard 5.jpg")
        )
        assertEquals(
            "https://alifcarimport.com/uploads/cars/byd.webp",
            com.example.ui.components.normalizeWebsiteImageUrl("uploads/cars/byd.webp")
        )
        assertEquals(
            "https://mubarekzeru.github.io/AlifCarImport/images/song-plus.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("./images/song-plus.jpg")
        )
        assertEquals(
            "https://mubarekzeru.github.io/AlifCarImport/cars/bz3x.webp",
            com.example.ui.components.normalizeWebsiteImageUrl("/AlifCarImport/cars/bz3x.webp")
        )
        assertTrue(
            com.example.ui.components.resolveCandidateImageUrls("song-plus.jpg")
                .contains("https://mubarekzeru.github.io/AlifCarImport/song-plus.jpg")
        )
        assertTrue(
            com.example.ui.components.resolveCandidateImageUrls("https://alifcarimport.com/uploads/leopard5.jpg")
                .contains("https://mubarekzeru.github.io/AlifCarImport/uploads/leopard5.jpg")
        )
        assertEquals(
            "https://raw.githubusercontent.com/mubarekzeru/AlifCarImport/main/public/cars/leopard5.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl(
                "https://github.com/mubarekzeru/AlifCarImport/blob/main/public/cars/leopard5.jpg"
            )
        )
        val nextImageCandidates = com.example.ui.components.resolveCandidateImageUrls(
            "/_next/image?url=%2Fuploads%2Fleopard5.jpg&amp;w=1080&amp;q=75"
        )
        assertEquals("https://alifcarimport.com/uploads/leopard5.jpg", nextImageCandidates.first())
        assertTrue(nextImageCandidates.any { it.contains("/_next/image?url=%2Fuploads%2Fleopard5.jpg&w=1080&q=75") })

        assertEquals(
            "https://cdn.example.com/car.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl(
                "https://alifcarimport.com/_next/image?url=https%3A%2F%2Fcdn.example.com%2Fcar.jpg&w=828&q=75"
            )
        )
        assertEquals(
            "https://alifcarimport.com/uploads/car.png",
            com.example.ui.components.normalizeWebsiteImageUrl("//alifcarimport.com/uploads/car.png")
        )
        assertEquals(
            "https://alifcarimport.com/uploads/car.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("http://alifcarimport.com/uploads/car.jpg")
        )
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/alif-bucket/o/vehicles%2Fcar.jpg?alt=media",
            com.example.ui.components.normalizeWebsiteImageUrl("gs://alif-bucket/vehicles/car.jpg")
        )
        val driveCandidates = com.example.ui.components.resolveCandidateImageUrls(
            "https://drive.google.com/file/d/ABC123xyz/view?usp=sharing"
        )
        assertEquals("https://drive.google.com/thumbnail?id=ABC123xyz&sz=w1200", driveCandidates.first())
        assertTrue(driveCandidates.contains("https://drive.google.com/uc?export=view&id=ABC123xyz"))

        val rawJpegBase64 = "/9j/" + "A".repeat(80)
        assertTrue(
            com.example.ui.components.normalizeWebsiteImageUrl(rawJpegBase64)
                .orEmpty()
                .startsWith("data:image/jpeg;base64,")
        )

        val vehicleWithGalleryOnly = com.example.data.model.Vehicle(
            id = "veh_1",
            imageUrl = "https://alifcarimport.com/placeholder.jpg",
            galleryImages = listOf("/uploads/leopard-front.jpg", "/uploads/leopard-side.jpg")
        )
        val slots = vehicleWithGalleryOnly.resolvedPhotoSlots()
        assertEquals(2, slots.size)
        assertEquals("https://alifcarimport.com/uploads/leopard-front.jpg", slots[0].first())
        assertTrue(slots[0].contains("https://mubarekzeru.github.io/AlifCarImport/uploads/leopard-front.jpg"))
        assertEquals("https://alifcarimport.com/uploads/leopard-side.jpg", slots[1].first())
        assertTrue(vehicleWithGalleryOnly.allResolvedPhotoUrls().contains("https://alifcarimport.com/uploads/leopard-front.jpg"))
    }

    @Test
    fun adminAuthorizationPolicy_allowsOnlyOwnerAndApprovedVerifiedAdmins() {
        // Bootstrap owner allowed when verified (case-insensitive)
        assertTrue(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = " AlifCarImports@gmail.com ",
                isEmailVerified = true,
                isExplicitUidAdmin = false,
                allowlistedEmails = emptyList()
            )
        )
        // Bootstrap owner blocked if unverified
        assertFalse(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = "alifcarimports@gmail.com",
                isEmailVerified = false,
                isExplicitUidAdmin = false,
                allowlistedEmails = emptyList()
            )
        )
        // Approved email in Admin Access allowlist allowed when verified
        assertTrue(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = "partner@alifcarimport.com",
                isEmailVerified = true,
                isExplicitUidAdmin = false,
                allowlistedEmails = listOf("Partner@AlifCarImport.com")
            )
        )
        // Explicit UID admin allowed when verified
        assertTrue(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = "staff@example.com",
                isEmailVerified = true,
                isExplicitUidAdmin = true,
                allowlistedEmails = emptyList()
            )
        )
        // Random unapproved email is strictly denied
        assertFalse(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = "stranger@gmail.com",
                isEmailVerified = true,
                isExplicitUidAdmin = false,
                allowlistedEmails = listOf("partner@alifcarimport.com")
            )
        )
        // Blank or null email is denied
        assertFalse(
            com.example.auth.AdminAuthorizationPolicy.isAuthorizedAdmin(
                userEmail = "",
                isEmailVerified = true,
                isExplicitUidAdmin = false,
                allowlistedEmails = listOf("partner@alifcarimport.com")
            )
        )
    }

    @Test
    fun activeDeviceSession_identifiesWebsiteVsMobilePlatforms() {
        assertTrue(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Website"))
        assertTrue(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Web Admin"))
        assertTrue(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Browser"))
        assertTrue(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Desktop Portal"))
        assertFalse(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Mobile App"))
        assertFalse(com.example.data.model.ActiveDeviceSession.isWebsitePlatform("Android App"))
        assertFalse(com.example.data.model.ActiveDeviceSession.isWebsitePlatform(""))
    }

    @Test
    fun activeDeviceSession_resolvesBrowserNamesFromUserAgentAndDeviceInfo() {
        val resolve = com.example.data.model.ActiveDeviceSession.Companion::resolveBrowserName
        assertEquals(
            "Google Chrome",
            resolve(
                null,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
                "Windows PC",
                "Website"
            )
        )
        assertEquals(
            "Microsoft Edge",
            resolve(
                null,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
                "Windows PC",
                "Website"
            )
        )
        assertEquals(
            "Safari",
            resolve(
                null,
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
                "iPhone",
                "Website"
            )
        )
        assertEquals(
            "Samsung Internet",
            resolve(
                null,
                "Mozilla/5.0 (Linux; Android 15; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36",
                "Samsung Phone",
                "Website"
            )
        )
        assertEquals(
            "Firefox",
            resolve(
                null,
                null,
                "Firefox on macOS",
                "Website"
            )
        )
        assertEquals(
            "Mobile App",
            resolve(
                null,
                null,
                "Samsung SM-M166P (Android 16)",
                "Mobile App"
            )
        )
    }

    @Test
    fun totpAuthenticator_matchesRfc6238AndGoogleAuthenticatorFormat() {
        // Official RFC 6238 HMAC-SHA1 test secret "12345678901234567890" in Base32
        val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", com.example.data.security.TotpAuthenticator.generateCode(rfcSecret, 59_000L))
        assertEquals("081804", com.example.data.security.TotpAuthenticator.generateCode(rfcSecret, 1_111_111_109_000L))
        assertEquals("005924", com.example.data.security.TotpAuthenticator.generateCode(rfcSecret, 1_234_567_890_000L))

        // Verifies pasted codes with spaces or dashes and ±30s clock skew
        assertTrue(com.example.data.security.TotpAuthenticator.verifyCode(rfcSecret, "287 082", 59_000L))
        assertTrue(com.example.data.security.TotpAuthenticator.verifyCode(rfcSecret, "287-082", 89_000L))
        assertFalse(com.example.data.security.TotpAuthenticator.verifyCode(rfcSecret, "000000", 59_000L))
        assertFalse(com.example.data.security.TotpAuthenticator.verifyCode(rfcSecret, "12345", 59_000L))

        // Generated secret & Google Authenticator otpauth:// URI
        val generated = com.example.data.security.TotpAuthenticator.generateSecretBase32()
        assertTrue(com.example.data.security.TotpAuthenticator.isValidSecretBase32(generated))
        val uri = com.example.data.security.TotpAuthenticator.buildOtpAuthUri(
            accountEmail = "alifcarimports@gmail.com",
            secretBase32 = generated
        )
        assertTrue(uri.startsWith("otpauth://totp/Alif%20Car%20Import:alifcarimports%40gmail.com?secret=$generated"))
        assertTrue(uri.contains("&issuer=Alif%20Car%20Import&algorithm=SHA1&digits=6&period=30"))
    }

    @Test
    fun tikTokThumbnailResolver_normalizesLinksAndParsesCoverMetadata() {
        val resolver = com.example.data.network.TikTokThumbnailResolver

        // Accepts canonical, short, bare, and share-text TikTok links
        assertEquals(
            "https://www.tiktok.com/@alifcarimport/video/7412345678901234567",
            resolver.normalizeTikTokUrl("https://www.tiktok.com/@alifcarimport/video/7412345678901234567")
        )
        assertEquals(
            "https://vm.tiktok.com/ZM8abc123/",
            resolver.normalizeTikTokUrl("Check out this car! https://vm.tiktok.com/ZM8abc123/")
        )
        assertEquals(
            "https://vt.tiktok.com/ZS2xyz999/",
            resolver.normalizeTikTokUrl("vt.tiktok.com/ZS2xyz999/")
        )

        // Rejects non-TikTok hosts, credentials, and blank strings
        assertEquals(null, resolver.normalizeTikTokUrl(""))
        assertEquals(null, resolver.normalizeTikTokUrl("https://evil.com/tiktok.com/video/1"))
        assertEquals(null, resolver.normalizeTikTokUrl("https://faketiktok.com/video/1"))
        assertEquals(null, resolver.normalizeTikTokUrl("https://user:pass@www.tiktok.com/@a/video/1"))
        assertEquals(null, resolver.normalizeTikTokUrl("http://127.0.0.1/video/1"))

        // Parses oEmbed JSON with escaped slashes and unicode ampersands
        val sampleOEmbed = """
            {
              "version": "1.0",
              "type": "video",
              "title": "BYD Leopard 5 Arrival #alifcarimport",
              "author_name": "Alif Car Import",
              "thumbnail_url": "https:\/\/p16-sign-va.tiktokcdn.com\/obj\/tos-maliva-p-0068\/cover.jpeg?x-expires=1799999999\u0026x-signature=abc123"
            }
        """.trimIndent()
        val parsed = resolver.parseOEmbedJson(sampleOEmbed)
        assertEquals(
            "https://p16-sign-va.tiktokcdn.com/obj/tos-maliva-p-0068/cover.jpeg?x-expires=1799999999&x-signature=abc123",
            parsed?.thumbnailUrl
        )
        assertEquals("BYD Leopard 5 Arrival #alifcarimport", parsed?.title)
        assertEquals("Alif Car Import", parsed?.authorName)

        // Parses OpenGraph fallback HTML
        val sampleHtml = """
            <html><head>
              <meta property="og:image" content="https://p16-sign-sg.tiktokcdn.com/cover.jpg?a=1&amp;b=2" />
            </head></html>
        """.trimIndent()
        val parsedHtml = resolver.extractCoverFromHtml(sampleHtml)
        assertEquals(
            "https://p16-sign-sg.tiktokcdn.com/cover.jpg?a=1&b=2",
            parsedHtml?.thumbnailUrl
        )

        // Recognizes TikTok CDN hosts for Referer injection
        assertTrue(resolver.isTikTokCdnHost("p16-sign-va.tiktokcdn.com"))
        assertTrue(resolver.isTikTokCdnHost("p19-pu-sign-useast8.tiktokcdn-us.com"))
        assertFalse(resolver.isTikTokCdnHost("example.com"))
    }
}
