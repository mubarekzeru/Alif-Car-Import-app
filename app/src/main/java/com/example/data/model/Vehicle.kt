package com.example.data.model

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class VehicleStatus(val value: String, val label: String) {
    AVAILABLE("available", "Available"),
    RESERVED("reserved", "Reserved"),
    SOLD("sold", "Sold");

    companion object {
        fun fromValue(raw: String?): VehicleStatus {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.value == normalized } ?: AVAILABLE
        }
    }
}

data class Vehicle(
    val id: String = "",
    val title: String = "",
    val make: String = "",
    val model: String = "",
    val year: Long = 2024L,
    val price: Long = 0L,
    val originalPrice: Long? = null,
    val mileage: String = "",
    val transmission: String = "Automatic",
    val fuelType: String = "Petrol",
    val color: String = "",
    val origin: String = "",
    val imageUrl: String = "",
    val galleryImages: List<String> = emptyList(),
    val description: String = "",
    val tiktokUrl: String = "",
    val status: String = VehicleStatus.AVAILABLE.value,
    val featured: Boolean = false,
    val createdAt: String = "",
    val updatedAt: Timestamp? = null
) {
    val statusEnum: VehicleStatus
        get() = VehicleStatus.fromValue(status)

    val displayTitle: String
        get() = title.ifBlank {
            listOfNotNull(
                year.takeIf { it > 0 }?.toString(),
                make.takeIf { it.isNotBlank() },
                model.takeIf { it.isNotBlank() }
            ).joinToString(" ").ifBlank { "Untitled Vehicle" }
        }

    fun toApiJson(nowIso: String = VehicleDraft.currentIsoTimestamp()): JSONObject {
        val cleanGallery = galleryImages
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= VehicleDraft.MAX_IMAGE_FIELD_LENGTH }
            .take(10)

        return JSONObject().apply {
            put("id", id.trim())
            put("title", displayTitle.take(200))
            put("make", make.trim().take(80))
            put("model", model.trim().take(120))
            put("year", year.coerceIn(1950L, 2100L))
            put("price", price.coerceIn(0L, 100_000_000L))
            originalPrice?.takeIf { it in 0L..100_000_000L }?.let {
                put("originalPrice", it)
            } ?: put("originalPrice", JSONObject.NULL)
            put("mileage", mileage.trim().take(60))
            put("transmission", transmission.trim().take(80))
            put("fuelType", fuelType.trim().take(80))
            put("color", color.trim().take(80))
            put("origin", origin.trim().take(100))
            put("imageUrl", imageUrl.trim().take(VehicleDraft.MAX_IMAGE_FIELD_LENGTH))
            put("galleryImages", JSONArray(cleanGallery))
            put("description", description.trim().take(4000))
            put("tiktokUrl", tiktokUrl.trim().take(500))
            put("status", statusEnum.value)
            put("featured", featured)
            put("createdAt", createdAt.trim().ifBlank { nowIso }.take(64))
            put("updatedAt", nowIso)
        }
    }
}

data class VehicleDraft(
    val title: String = "",
    val make: String,
    val model: String,
    val year: Long,
    val price: Long,
    val originalPrice: Long? = null,
    val mileage: String = "",
    val transmission: String = "Automatic",
    val fuelType: String = "Petrol",
    val color: String = "",
    val origin: String = "",
    val imageUrl: String,
    val galleryImages: List<String> = emptyList(),
    val description: String = "",
    val tiktokUrl: String = "",
    val status: VehicleStatus = VehicleStatus.AVAILABLE,
    val featured: Boolean = false
) {
    private val resolvedMake: String
        get() {
            val trimmed = make.trim()
            if (trimmed.isNotEmpty()) return trimmed.take(80)
            val titleTokens = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val withoutYear = titleTokens.dropWhile { it.length == 4 && it.all(Char::isDigit) }
            return (withoutYear.firstOrNull() ?: "Vehicle").take(80)
        }

    private val resolvedModel: String
        get() {
            val trimmed = model.trim()
            if (trimmed.isNotEmpty()) return trimmed.take(120)
            val titleTokens = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val withoutYear = titleTokens.dropWhile { it.length == 4 && it.all(Char::isDigit) }
            val remainder = if (make.trim().isEmpty()) withoutYear.drop(1) else withoutYear
            return remainder.joinToString(" ").ifBlank { "Import" }.take(120)
        }

    private val resolvedTitle: String
        get() = title.trim().ifEmpty {
            val yearVal = year.coerceIn(1950L, 2100L)
            if (make.trim().isEmpty() && model.trim().isEmpty()) {
                "New Vehicle Arrival"
            } else {
                "$yearVal $resolvedMake $resolvedModel"
            }
        }.take(200)

    private val resolvedDescription: String
        get() = description.trim().ifEmpty {
            buildString {
                append(resolvedTitle)
                val specs = listOf(transmission.trim(), fuelType.trim(), origin.trim())
                    .filter { it.isNotEmpty() }
                if (specs.isNotEmpty()) {
                    append(" — ")
                    append(specs.joinToString(" • "))
                }
            }
        }.take(4000)

    fun toApiJson(
        vehicleId: String,
        createdAtIso: String = currentIsoTimestamp(),
        updatedAtIso: String = currentIsoTimestamp()
    ): JSONObject {
        val cleanGallery = galleryImages
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_IMAGE_FIELD_LENGTH }
            .take(10)

        return JSONObject().apply {
            put("id", vehicleId)
            put("title", resolvedTitle)
            put("make", resolvedMake)
            put("model", resolvedModel)
            put("year", year.coerceIn(1950L, 2100L))
            put("price", price.coerceIn(0L, 100_000_000L))
            originalPrice?.takeIf { it in 0L..100_000_000L }?.let {
                put("originalPrice", it)
            } ?: put("originalPrice", JSONObject.NULL)
            put("mileage", mileage.trim().take(60))
            put("transmission", transmission.trim().take(80))
            put("fuelType", fuelType.trim().take(80))
            put("color", color.trim().take(80))
            put("origin", origin.trim().take(100))
            put("imageUrl", imageUrl.trim().take(MAX_IMAGE_FIELD_LENGTH))
            put("galleryImages", JSONArray(cleanGallery))
            put("description", resolvedDescription)
            put("tiktokUrl", tiktokUrl.trim().take(500))
            put("status", status.value)
            put("featured", featured)
            put("createdAt", createdAtIso.take(64))
            put("updatedAt", updatedAtIso.take(64))
        }
    }

    fun toCreateMap(vehicleId: String, createdAtIso: String = currentIsoTimestamp()): Map<String, Any> {
        val raw = mutableMapOf<String, Any>(
            "id" to vehicleId,
            "title" to resolvedTitle,
            "make" to resolvedMake,
            "model" to resolvedModel,
            "year" to year.coerceIn(1950L, 2100L),
            "price" to price.coerceIn(0L, 100_000_000L),
            "mileage" to mileage.trim().take(60),
            "transmission" to transmission.trim().take(80),
            "fuelType" to fuelType.trim().take(80),
            "color" to color.trim().take(80),
            "origin" to origin.trim().take(100),
            "imageUrl" to imageUrl.trim().take(MAX_IMAGE_FIELD_LENGTH),
            "description" to resolvedDescription,
            "tiktokUrl" to tiktokUrl.trim().take(500),
            "status" to status.value,
            "featured" to featured,
            "createdAt" to createdAtIso.take(64),
            "updatedAt" to currentIsoTimestamp()
        )
        originalPrice?.takeIf { it in 0L..100_000_000L }?.let {
            raw["originalPrice"] = it
        }
        val cleanGallery = galleryImages
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_IMAGE_FIELD_LENGTH }
            .take(10)
        if (cleanGallery.isNotEmpty()) {
            raw["galleryImages"] = cleanGallery
        }
        return raw
    }

    companion object {
        const val MAX_IMAGE_FIELD_LENGTH = 2_000_000

        fun currentIsoTimestamp(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return formatter.format(Date())
        }
    }
}
