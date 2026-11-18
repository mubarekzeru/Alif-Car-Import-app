package com.example

import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.ui.components.formatCompactCurrency
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatMileageDisplay
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
        assertEquals("$89,500", formatCurrency(89500L))
        assertEquals("$1.25M", formatCompactCurrency(1_250_000L))
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
    fun decodeImageModel_returnsNullForBlankOrPlaceholderWithoutTemplateFallback() {
        assertEquals(null, com.example.ui.components.decodeImageModel(""))
        assertEquals(null, com.example.ui.components.decodeImageModel("https://alifcarimport.com/placeholder.jpg"))
        assertEquals("https://example.com/car.jpg", com.example.ui.components.decodeImageModel("https://example.com/car.jpg"))
    }
}
