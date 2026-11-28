package com.example

import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.ui.components.allResolvedPhotoUrls
import com.example.ui.components.formatCompactCurrency
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatMileageDisplay
import com.example.ui.components.formatOptionalCurrency
import com.example.ui.components.resolvedPhotoSlots
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
    fun adminAccessConfig_normalizesEmailsSafely() {
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

    @Test
    fun loginSecurityAlert_allowsOnlyAdminPageEventsAndBlocksVisitorHomeEvents() {
        val check = com.example.data.model.LoginSecurityAlert.Companion::isAdminPageLoginEvent

        // Admin page & admin app login attempts MUST pass
        assertTrue(check("/admin", "Website Admin", "login"))
        assertTrue(check("/admin/login", "Website", "failed_login"))
        assertTrue(check("admin_page", "Website", "admin_login_attempt"))
        assertTrue(check("", "Mobile App", ""))
        assertTrue(check("Mobile Admin App", "Mobile App", ""))

        // Visitor / home / dashboard / public page events MUST be rejected
        assertFalse(check("/", "Website", "visit"))
        assertFalse(check("/home", "Website", "page_view"))
        assertFalse(check("home_dashboard", "Website", ""))
        assertFalse(check("/visitors", "Website", ""))
        assertFalse(check("visitor_page", "Website", ""))
        assertFalse(check("public_storefront", "Website", ""))
        assertFalse(check("showroom", "Website", ""))
        assertFalse(check("/dashboard", "Website", "page_view"))
    }

    @Test
    fun contactConfig_preservesAllSocialPlatforms() {
        val contact = com.example.data.model.ContactConfig(
            phone = "+251911000000",
            youtube = "https://youtube.com/@alifcarimport",
            snapchat = "@alifcarimport",
            linkedin = "https://linkedin.com/company/alifcarimport",
            threads = "@alifcarimport",
            pinterest = "https://pinterest.com/alifcarimport",
            reddit = "https://reddit.com/u/alifcarimport",
            viber = "+251911000000",
            imo = "+251911000000",
            wechat = "alifcarimport_cn",
            discord = "https://discord.gg/alifcarimport",
            twitch = "https://twitch.tv/alifcarimport",
            website = "https://alifcarimport.com",
            instagram = ""
        )
        assertEquals("https://youtube.com/@alifcarimport", contact.youtube)
        assertEquals("@alifcarimport", contact.snapchat)
        assertEquals("https://linkedin.com/company/alifcarimport", contact.linkedin)
        assertEquals("@alifcarimport", contact.threads)
        assertEquals("https://pinterest.com/alifcarimport", contact.pinterest)
        assertEquals("https://reddit.com/u/alifcarimport", contact.reddit)
        assertEquals("+251911000000", contact.viber)
        assertEquals("+251911000000", contact.imo)
        assertEquals("alifcarimport_cn", contact.wechat)
        assertEquals("https://discord.gg/alifcarimport", contact.discord)
        assertEquals("https://twitch.tv/alifcarimport", contact.twitch)
        assertEquals("https://alifcarimport.com", contact.website)
        assertEquals("", contact.instagram)
    }

    @Test
    fun visitorTimeRange_hidesCurrentlySelectedOptionInPicker() {
        val daysWithoutOneDay = com.example.data.model.VisitorTimeRange.optionsForUnitExcluding(
            unit = com.example.data.model.VisitorRangeUnit.DAYS,
            selected = com.example.data.model.VisitorTimeRange.ONE_DAY
        )
        assertFalse(daysWithoutOneDay.contains(com.example.data.model.VisitorTimeRange.ONE_DAY))
        assertTrue(daysWithoutOneDay.contains(com.example.data.model.VisitorTimeRange.SEVEN_DAYS))

        // When user switches to 1 Week, 1 Day reappears under Days and 1 Week disappears under Weeks
        val daysWhenWeekSelected = com.example.data.model.VisitorTimeRange.optionsForUnitExcluding(
            unit = com.example.data.model.VisitorRangeUnit.DAYS,
            selected = com.example.data.model.VisitorTimeRange.ONE_WEEK
        )
        assertTrue(daysWhenWeekSelected.contains(com.example.data.model.VisitorTimeRange.ONE_DAY))

        val weeksWithoutOneWeek = com.example.data.model.VisitorTimeRange.optionsForUnitExcluding(
            unit = com.example.data.model.VisitorRangeUnit.WEEKS,
            selected = com.example.data.model.VisitorTimeRange.ONE_WEEK
        )
        assertFalse(weeksWithoutOneWeek.contains(com.example.data.model.VisitorTimeRange.ONE_WEEK))
        assertTrue(weeksWithoutOneWeek.contains(com.example.data.model.VisitorTimeRange.FOUR_WEEKS))
    }

    @Test
    fun interactionsAnalyticsEngine_computesGrowthBucketsAndTopPagesAcrossTimeframes() {
        val now = 1_760_000_000_000L
        val hour = 60L * 60L * 1000L
        val day = 24L * hour
        val records = listOf(
            // 3 visits inside last 1 day (2 unique visitors, 1 repeat)
            com.example.data.model.PageVisitRecord("v1", "/", "u1", "2026-10-10", now - 1 * hour),
            com.example.data.model.PageVisitRecord("v2", "/inventory", "u1", "2026-10-10", now - 2 * hour),
            com.example.data.model.PageVisitRecord("v3", "/", "u2", "2026-10-10", now - 5 * hour),
            // 1 visit in previous 1-day window -> growth = (3 - 1) / 1 = +200%
            com.example.data.model.PageVisitRecord("v4", "/contact", "u3", "2026-10-09", now - 30 * hour),
            // 1 older visit 20 days ago (included in 1 Year, excluded from 1 Week)
            com.example.data.model.PageVisitRecord("v5", "/tiktok", "u4", "2026-09-20", now - 20 * day)
        )
        val stats = com.example.data.model.WebsiteVisitorStats(
            totalVisits = 5L,
            todayVisits = 3L,
            last7DaysVisits = 4L,
            uniqueVisitors = 4L,
            lastVisitAtMillis = now - 1 * hour,
            recentVisitRecords = records,
            isLoaded = true
        )

        val oneDay = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = stats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.ONE_DAY,
            nowMillis = now
        )
        assertEquals(3L, oneDay.periodVisits)
        assertEquals(1L, oneDay.previousPeriodVisits)
        assertEquals(200, oneDay.growthPercent)
        assertEquals(2L, oneDay.uniqueVisitors)
        assertEquals(1L, oneDay.returningVisits)
        assertEquals(6, oneDay.buckets.size)
        assertEquals("Home Dashboard", oneDay.topPages.first().displayName)
        assertEquals(2L, oneDay.topPages.first().visits)

        val oneYear = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = stats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.ONE_YEAR,
            nowMillis = now
        )
        assertEquals(5L, oneYear.periodVisits)
        assertEquals(12, oneYear.buckets.size)
    }

    @Test
    fun adminAccessConfig_validatesAndMergesGrantedBlockedEmails() {
        val config = com.example.data.model.AdminAccessConfig

        assertTrue(config.isValidAdminEmail("Manager@AlifCarImport.com"))
        assertFalse(config.isValidAdminEmail(""))
        assertFalse(config.isValidAdminEmail("Unknown account"))
        assertFalse(config.isValidAdminEmail("not-an-email"))
        assertFalse(config.isValidAdminEmail("bad@.com"))
        assertFalse(config.isValidAdminEmail("bad@domain..com"))

        // Adding a valid new email normalizes and preserves existing admins
        val added = config.mergeGrantedEmail(
            existingEmails = listOf("Existing@Example.com"),
            rawEmail = " NewAdmin@AlifCarImport.com "
        )
        assertTrue(added is com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.Added)
        val addedResult = added as com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.Added
        assertEquals("newadmin@alifcarimport.com", addedResult.grantedEmail)
        assertEquals(
            listOf("existing@example.com", "newadmin@alifcarimport.com"),
            addedResult.updatedEmails
        )

        // Already-authorized email or bootstrap owner returns AlreadyAuthorized without duplicating
        val duplicate = config.mergeGrantedEmail(
            existingEmails = listOf("newadmin@alifcarimport.com"),
            rawEmail = "NewAdmin@AlifCarImport.com"
        )
        assertTrue(duplicate is com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.AlreadyAuthorized)

        val owner = config.mergeGrantedEmail(
            existingEmails = emptyList(),
            rawEmail = "AlifCarImports@gmail.com"
        )
        assertTrue(owner is com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.AlreadyAuthorized)

        // Invalid email is rejected
        val invalid = config.mergeGrantedEmail(emptyList(), "Unknown user")
        assertTrue(invalid is com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.Invalid)

        // 20-email limit is enforced to match firestore.rules
        val fullList = (1..20).map { "admin$it@alifcarimport.com" }
        val limitHit = config.mergeGrantedEmail(fullList, "admin21@alifcarimport.com")
        assertTrue(limitHit is com.example.data.model.AdminAccessConfig.GrantEmailMergeResult.LimitReached)
    }

    @Test
    fun loginAuthenticatorPolicy_andOwnerVerifiedAlert_behaveSecurely() {
        val validSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // Owner email with Authenticator enabled and unverified session MUST trigger 2FA challenge
        assertTrue(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "AlifCarImports@gmail.com",
                authenticatorEnabled = true,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = false
            )
        )
        // Already verified for this sign-in session -> do not double-prompt
        assertFalse(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "alifcarimports@gmail.com",
                authenticatorEnabled = true,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = true
            )
        )
        // Authenticator disabled -> no challenge
        assertFalse(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "alifcarimports@gmail.com",
                authenticatorEnabled = false,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = false
            )
        )
        // Authenticator enabled, but login code option disabled -> no login challenge
        assertFalse(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "alifcarimports@gmail.com",
                authenticatorEnabled = true,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = false,
                requireOnLogin = false
            )
        )
        // Authenticator enabled and login code option enabled -> requires login challenge
        assertTrue(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "alifcarimports@gmail.com",
                authenticatorEnabled = true,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = false,
                requireOnLogin = true
            )
        )
        // Any authorized admin email MUST also trigger 2FA challenge when enabled & requireOnLogin = true
        assertTrue(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "staff@alifcarimport.com",
                authenticatorEnabled = true,
                secretBase32 = "gezd gnbv gy3t qojq gezd gnbv gy3t qojq",
                alreadyVerifiedForSession = false,
                requireOnLogin = true
            )
        )
        // Blank email -> no challenge
        assertFalse(
            com.example.auth.LoginAuthenticatorPolicy.requiresAuthenticatorChallenge(
                userEmail = "   ",
                authenticatorEnabled = true,
                secretBase32 = validSecret,
                alreadyVerifiedForSession = false
            )
        )

        // Attempt type parsing & owner-verified alert detection (hides "It was me" / "It wasn't me")
        assertTrue(
            com.example.data.model.LoginSecurityAlert.isAuthenticatorVerifiedAttemptType(
                com.example.data.model.LoginSecurityAlert.ATTEMPT_TYPE_AUTHENTICATOR_VERIFIED
            )
        )
        assertTrue(com.example.data.model.LoginSecurityAlert.isAuthenticatorVerifiedAttemptType("totp_2fa"))
        assertFalse(com.example.data.model.LoginSecurityAlert.isAuthenticatorVerifiedAttemptType("password_login"))

        val ownerVerifiedAlert = com.example.data.model.LoginSecurityAlert(
            id = "a1",
            sessionId = "s1",
            email = "alifcarimports@gmail.com",
            deviceName = "Pixel 9",
            platform = "Mobile App",
            location = "Addis Ababa",
            timestampMillis = 1_760_000_000_000L,
            verifiedWithAuthenticator = true
        )
        assertTrue(ownerVerifiedAlert.isOwnerAuthenticatorVerified)

        val ownerUnverifiedAlert = ownerVerifiedAlert.copy(verifiedWithAuthenticator = false)
        assertFalse(ownerUnverifiedAlert.isOwnerAuthenticatorVerified)

        val otherAdminAlert = ownerVerifiedAlert.copy(email = "partner@alifcarimport.com")
        assertFalse(otherAdminAlert.isOwnerAuthenticatorVerified)

        val blockedOwnerAttempt = ownerVerifiedAlert.copy(isUnauthorizedAttempt = true)
        assertFalse(blockedOwnerAttempt.isOwnerAuthenticatorVerified)
    }

    @Test
    fun loginSecurityAlert_separatesWebsiteVisitsFromActualLoginAttempts() {
        val alertCompanion = com.example.data.model.LoginSecurityAlert.Companion

        // Normalizing full URLs and GitHub Pages prefix
        assertEquals("/", alertCompanion.normalizePagePath("https://alifcarimport.com/"))
        assertEquals("/", alertCompanion.normalizePagePath("https://mubarekzeru.github.io/AlifCarImport/"))
        assertEquals("/vehicles/123", alertCompanion.normalizePagePath("https://alifcarimport.com/vehicles/123?ref=ig"))
        assertEquals("/admin/login", alertCompanion.normalizePagePath("https://mubarekzeru.github.io/AlifCarImport/#/admin/login"))

        // Public website visits are counted as website traffic and NEVER trigger login alerts
        assertTrue(alertCompanion.isPublicWebsiteVisitEvent("https://alifcarimport.com/", "Website"))
        assertTrue(alertCompanion.isPublicWebsiteVisitEvent("https://mubarekzeru.github.io/AlifCarImport/", "Website"))
        assertTrue(alertCompanion.isPublicWebsiteVisitEvent("/vehicles/byd-leopard-5", "Website"))
        assertFalse(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "https://alifcarimport.com/",
                platform = "Website",
                eventType = "page_view"
            )
        )
        assertFalse(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/inventory",
                platform = "Website",
                eventType = "admin_login_attempt",
                status = "attempt"
            )
        )

        // Passive opening/viewing of the admin page without attempting to sign in MUST NOT alert
        assertFalse(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/admin",
                platform = "Website Admin",
                eventType = "admin_login_attempt",
                status = "attempt",
                reason = "",
                email = "",
                isFailedAttemptRecord = true
            )
        )
        assertFalse(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/admin",
                platform = "Website Admin",
                eventType = "opened_admin",
                status = "visit",
                reason = "Opened admin page",
                email = "alifcarimports@gmail.com",
                isFailedAttemptRecord = true
            )
        )

        // Real incorrect/failed login attempt MUST alert
        assertTrue(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/admin/login",
                platform = "Website Admin",
                eventType = "admin_login_attempt",
                status = "failed",
                reason = "Incorrect password",
                email = "alifcarimports@gmail.com",
                isFailedAttemptRecord = true
            )
        )
        assertTrue(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/admin",
                platform = "Website Admin",
                eventType = "admin_login_attempt",
                status = "denied",
                reason = "Unauthorized admin email",
                email = "stranger@example.com",
                isFailedAttemptRecord = true
            )
        )

        // Real successful login attempt MUST alert
        assertTrue(
            alertCompanion.isActualLoginAttemptEvent(
                sourcePage = "/admin/login",
                platform = "Website Admin",
                eventType = "admin_login",
                email = "partner@alifcarimport.com",
                isFailedAttemptRecord = false
            )
        )
    }

    @Test
    fun loginSecurityAlert_tracksUnreadStateAndIncorrectCredentialsCleanly() {
        val pendingLogin = com.example.data.model.LoginSecurityAlert(
            id = "alert_1",
            sessionId = "sess_1",
            email = "partner@alifcarimport.com",
            deviceName = "Chrome on Windows",
            platform = "Website",
            location = "Addis Ababa",
            timestampMillis = 1_760_000_000_000L,
            decision = com.example.data.model.LoginAlertDecision.PENDING
        )
        val incorrectAttempt = com.example.data.model.LoginSecurityAlert(
            id = "attempt_2",
            sessionId = "",
            email = "alifcarimports@gmail.com",
            deviceName = "Safari on iPhone",
            platform = "Website Admin",
            location = "Addis Ababa",
            timestampMillis = 1_760_000_050_000L,
            decision = com.example.data.model.LoginAlertDecision.REJECTED_NOT_ME,
            isUnauthorizedAttempt = true,
            failureReason = "Incorrect password",
            attemptStatus = "failed"
        )
        val ownerAuthLogin = pendingLogin.copy(
            id = "alert_3",
            email = "alifcarimports@gmail.com",
            decision = com.example.data.model.LoginAlertDecision.CONFIRMED_ME,
            verifiedWithAuthenticator = true
        )

        assertTrue(incorrectAttempt.isIncorrectCredentialsAttempt)
        assertFalse(pendingLogin.isIncorrectCredentialsAttempt)

        // Before Mark All Read: pending login + incorrect attempt are unread; owner Authenticator login is not unread
        assertTrue(pendingLogin.isUnread(emptySet()))
        assertTrue(incorrectAttempt.isUnread(emptySet()))
        assertFalse(ownerAuthLogin.isUnread(emptySet()))

        // After Mark All Read: all marked IDs become read
        val markedRead = setOf("alert_1", "attempt_2", "alert_3")
        assertFalse(pendingLogin.isUnread(markedRead))
        assertFalse(incorrectAttempt.isUnread(markedRead))
        assertFalse(ownerAuthLogin.isUnread(markedRead))
    }

    @Test
    fun loginSecurityAlert_findCurrentStatusAlertId_keepsOnlyCurrentSessionLatestAlert() {
        val currentSessionId = "uid1_dev1"
        val olderSameDevice = com.example.data.model.LoginSecurityAlert(
            id = "alert_old_phone",
            sessionId = currentSessionId,
            email = "alifcarimports@gmail.com",
            deviceName = "Pixel",
            platform = "Mobile App",
            location = "Addis Ababa",
            timestampMillis = 1_760_000_010_000L
        )
        val currentLogin = olderSameDevice.copy(
            id = "alert_current_phone",
            timestampMillis = 1_760_000_200_000L
        )
        val websiteLogin = olderSameDevice.copy(
            id = "alert_web_other",
            sessionId = "uid1_web9",
            platform = "Website",
            timestampMillis = 1_760_000_300_000L
        )
        val blockedAttemptSameSession = olderSameDevice.copy(
            id = "attempt_blocked",
            isUnauthorizedAttempt = true,
            timestampMillis = 1_760_000_400_000L
        )

        val alerts = listOf(blockedAttemptSameSession, websiteLogin, currentLogin, olderSameDevice)
        assertEquals(
            "alert_current_phone",
            com.example.data.model.LoginSecurityAlert.findCurrentStatusAlertId(alerts, currentSessionId)
        )
        assertEquals(
            null,
            com.example.data.model.LoginSecurityAlert.findCurrentStatusAlertId(alerts, "")
        )
    }

    @Test
    fun cloudflareWorkerMediaUrls_resolveCleanlyForAppAndWebsiteSync() {
        assertEquals(
            "https://alif-car-import-api.alifcarimports.workers.dev/api/media/cars/byd-leopard-5.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("/api/media/cars/byd-leopard-5.jpg")
        )
        assertEquals(
            "https://alif-car-import-api.alifcarimports.workers.dev/media/vehicles/unit1.webp",
            com.example.ui.components.normalizeWebsiteImageUrl("/media/vehicles/unit1.webp")
        )
        assertEquals(
            "https://alif-car-import-api.alifcarimports.workers.dev/r2/photo.jpg",
            com.example.ui.components.normalizeWebsiteImageUrl("alif-car-import-api.alifcarimports.workers.dev/r2/photo.jpg")
        )
    }

    @Test
    fun tikTokAppLauncher_resolvesVideoProfileAndHandleUrlsCleanly() {
        assertEquals(
            "https://www.tiktok.com/@alifcarimport/video/7400000000000000001?is_from_webapp=1",
            com.example.ui.components.TikTokAppLauncher.resolveLaunchUrl(
                videoUrl = "https://www.tiktok.com/@alifcarimport/video/7400000000000000001?is_from_webapp=1",
                fallbackProfileUrl = "https://www.tiktok.com/@alifcarimport",
                fallbackHandle = "@alifcarimport"
            )
        )
        assertEquals(
            "https://www.tiktok.com/@alifcarimport",
            com.example.ui.components.TikTokAppLauncher.resolveLaunchUrl(
                videoUrl = "",
                fallbackProfileUrl = "https://www.tiktok.com/@alifcarimport",
                fallbackHandle = "@alifcarimport"
            )
        )
        assertEquals(
            "https://www.tiktok.com/@alifcarimport",
            com.example.ui.components.TikTokAppLauncher.resolveLaunchUrl(
                videoUrl = "",
                fallbackProfileUrl = "",
                fallbackHandle = "@alifcarimport"
            )
        )
    }

    @Test
    fun interactionsAnalyticsEngine_countsVisitorsAcrossAllRangesWhenOnlySummaryCountersExist() {
        val nowMillis = 1_760_000_000_000L
        val summaryOnlyStats = com.example.data.model.WebsiteVisitorStats(
            totalVisits = 120L,
            todayVisits = 9L,
            last7DaysVisits = 44L,
            uniqueVisitors = 35L,
            lastVisitAtMillis = nowMillis - 60_000L,
            recentVisitRecords = emptyList(),
            isLoaded = true
        )

        val report1d = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = summaryOnlyStats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.ONE_DAY,
            nowMillis = nowMillis
        )
        assertEquals(9L, report1d.periodVisits)
        assertEquals(9L, report1d.buckets.sumOf { it.visits })

        val report3d = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = summaryOnlyStats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.THREE_DAYS,
            nowMillis = nowMillis
        )
        assertEquals(9L, report3d.periodVisits)
        assertEquals(9L, report3d.buckets.sumOf { it.visits })

        val report7d = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = summaryOnlyStats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.SEVEN_DAYS,
            nowMillis = nowMillis
        )
        assertEquals(44L, report7d.periodVisits)
        assertEquals(44L, report7d.buckets.sumOf { it.visits })

        val report30d = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = summaryOnlyStats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.THIRTY_DAYS,
            nowMillis = nowMillis
        )
        assertEquals(120L, report30d.periodVisits)
        assertEquals(120L, report30d.buckets.sumOf { it.visits })

        val report1y = com.example.data.model.InteractionsAnalyticsEngine.computeReport(
            stats = summaryOnlyStats,
            inquiries = emptyList(),
            range = com.example.data.model.VisitorTimeRange.ONE_YEAR,
            nowMillis = nowMillis
        )
        assertEquals(120L, report1y.periodVisits)
        assertEquals(120L, report1y.buckets.sumOf { it.visits })
    }
}
