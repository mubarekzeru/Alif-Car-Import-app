package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BrandingConfig
import com.example.data.model.ContactConfig
import com.example.data.model.HeroConfig
import com.example.data.model.ShowroomConfig
import com.example.data.model.TikTokPostDraft
import com.example.data.model.TikTokSectionConfig
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.data.network.CloudflareApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Alif Car Import", context.getString(R.string.app_name))
    }

    @Test
    fun `parse showroom endpoint JSON keys vehicles tiktokPosts branding hero tiktokHeader contact`() {
        val json = """
            {
              "vehicles": [
                {
                  "id": "veh_2025_toyota_lc300",
                  "title": "2025 Toyota Land Cruiser",
                  "make": "Toyota",
                  "model": "Land Cruiser",
                  "year": 2025,
                  "price": 12500000,
                  "imageUrl": "https://cdn.alifcarimport.com/lc300.jpg",
                  "galleryImages": ["https://cdn.alifcarimport.com/lc300-2.jpg"],
                  "status": "available",
                  "featured": true
                }
              ],
              "tiktokPosts": [
                {
                  "id": "tt_walkaround_1",
                  "title": "LC300 Walkaround",
                  "caption": "Fresh arrival",
                  "tiktokUrl": "https://www.tiktok.com/@alifcarimport/video/123",
                  "coverImageUrl": "https://cdn.alifcarimport.com/tt1.jpg",
                  "sortOrder": 1
                }
              ],
              "branding": {
                "companyName": "Alif Car Import",
                "logoUrl": "https://cdn.alifcarimport.com/logo.png",
                "tagline": "Direct Ethiopia Imports"
              },
              "hero": {
                "kicker": "PREMIUM IMPORTS",
                "title": "Luxury SUVs & Sedans",
                "description": "Direct from UAE and Japan",
                "imageUrl": "https://cdn.alifcarimport.com/hero.jpg",
                "ctaLabel": "Explore Lot"
              },
              "tiktokHeader": {
                "title": "Showroom Reels",
                "description": "Watch walkarounds",
                "profileUrl": "https://www.tiktok.com/@alifcarimport",
                "handle": "@alifcarimport"
              },
              "contact": {
                "phone": "+251911000000",
                "whatsapp": "+251911000000",
                "telegram": "@alifcarimport",
                "cityCountry": "Addis Ababa, Ethiopia"
              }
            }
        """.trimIndent()

        val payload = CloudflareApiClient.parseShowroomPayload(json)
        assertEquals(1, payload.vehicles.size)
        assertEquals("veh_2025_toyota_lc300", payload.vehicles.first().id)
        assertEquals("https://cdn.alifcarimport.com/lc300.jpg", payload.vehicles.first().imageUrl)
        assertEquals(1, payload.vehicles.first().galleryImages.size)

        assertEquals(1, payload.tiktokPosts.size)
        assertEquals("tt_walkaround_1", payload.tiktokPosts.first().id)

        assertEquals("Alif Car Import", payload.config.branding.companyName)
        assertEquals("Luxury SUVs & Sedans", payload.config.hero.title)
        assertEquals("Showroom Reels", payload.config.tiktok.title)
        assertEquals("@alifcarimport", payload.config.tiktok.handle)
        assertEquals("+251911000000", payload.config.contact.phone)
        assertEquals("@alifcarimport", payload.config.contact.telegram)
    }

    @Test
    fun `serialize vehicle config and tiktok drafts for PUT admin endpoints`() {
        val draft = VehicleDraft(
            title = "2024 Lexus LX 600",
            make = "Lexus",
            model = "LX 600",
            year = 2024L,
            price = 18500000L,
            imageUrl = "data:image/jpeg;base64,/9j/4AAQ",
            galleryImages = listOf("data:image/jpeg;base64,/9j/gallery1"),
            status = VehicleStatus.RESERVED,
            featured = true
        )
        val vehicleJson = draft.toApiJson("veh_lx600")
        assertEquals("veh_lx600", vehicleJson.getString("id"))
        assertEquals("data:image/jpeg;base64,/9j/4AAQ", vehicleJson.getString("imageUrl"))
        assertEquals("data:image/jpeg;base64,/9j/gallery1", vehicleJson.getJSONArray("galleryImages").getString(0))
        assertEquals("reserved", vehicleJson.getString("status"))

        val configJson = ShowroomConfig(
            branding = BrandingConfig(companyName = "Alif Car Import", tagline = "Trusted"),
            hero = HeroConfig(title = "Direct Imports"),
            tiktok = TikTokSectionConfig(title = "Walkarounds", handle = "@alif"),
            contact = ContactConfig(phone = "+251911000000", instagram = "@alifcar")
        ).toApiJson()
        assertEquals("Walkarounds", configJson.getJSONObject("tiktokHeader").getString("title"))
        assertEquals("@alifcar", configJson.getJSONObject("contact").getString("instagram"))

        val tiktokJson = TikTokPostDraft(
            title = "New Arrival",
            caption = "Fresh on the lot",
            tiktokUrl = "https://www.tiktok.com/@alif/video/99",
            coverImageUrl = "https://cdn.alifcarimport.com/cover.jpg",
            sortOrder = 3L
        ).toApiJson("tt_99")
        assertEquals("tt_99", tiktokJson.getString("id"))
        assertEquals(3L, tiktokJson.getLong("sortOrder"))
    }

    @Test
    fun `parse admin inquiries and analytics responses`() {
        val inquiriesJson = """
            {
              "inquiries": [
                {
                  "id": "inq_1",
                  "name": "Dawit Bekele",
                  "phone": "+251911223344",
                  "vehicleTitle": "2025 Toyota Land Cruiser",
                  "message": "Is this still available?",
                  "createdAt": "2026-11-26T10:15:00.000Z"
                }
              ]
            }
        """.trimIndent()
        val inquiries = CloudflareApiClient.parseInquiriesResponse(inquiriesJson)
        assertEquals(1, inquiries.size)
        assertEquals("Dawit Bekele", inquiries.first().name)
        assertTrue((inquiries.first().createdAt?.seconds ?: 0L) > 0L)

        val analyticsJson = """
            {
              "totalVisits": 142,
              "todayVisits": 12,
              "last7DaysVisits": 54,
              "uniqueVisitors": 89,
              "visits": [
                {
                  "id": "v1",
                  "page": "/inventory",
                  "visitorId": "vis_1",
                  "createdAt": "2026-11-27T08:00:00.000Z"
                }
              ]
            }
        """.trimIndent()
        val stats = CloudflareApiClient.parseAnalyticsResponse(analyticsJson)
        assertEquals(142L, stats.totalVisits)
        assertEquals(12L, stats.todayVisits)
        assertEquals(54L, stats.last7DaysVisits)
        assertEquals(89L, stats.uniqueVisitors)
        assertEquals(1, stats.recentVisitRecords.size)
        assertTrue(stats.isLoaded)
    }
}
