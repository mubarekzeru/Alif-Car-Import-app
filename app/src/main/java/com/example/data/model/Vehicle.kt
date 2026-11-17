package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
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

enum class StudioPreset(val label: String) {
    SUV("SUV"),
    SEDAN("Sedan"),
    TRUCK("Truck"),
    COUPE("Coupe")
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
    private val resolvedTitle: String
        get() = title.trim().ifEmpty { "$year ${make.trim()} ${model.trim()}" }.take(200)

    private val resolvedDescription: String
        get() = description.trim().ifEmpty {
            buildString {
                append("$year ${make.trim()} ${model.trim()}")
                val specs = listOf(transmission.trim(), fuelType.trim(), origin.trim())
                    .filter { it.isNotEmpty() }
                if (specs.isNotEmpty()) {
                    append(" — ")
                    append(specs.joinToString(" • "))
                }
            }
        }.take(4000)

    fun toCreateMap(vehicleId: String, createdAtIso: String = currentIsoTimestamp()): Map<String, Any> {
        val raw = mutableMapOf<String, Any>(
            "id" to vehicleId,
            "title" to resolvedTitle,
            "make" to make.trim().take(80),
            "model" to model.trim().take(120),
            "year" to year.coerceIn(1950L, 2100L),
            "price" to price.coerceIn(0L, 100_000_000L),
            "mileage" to mileage.trim().take(60),
            "transmission" to transmission.trim().take(80),
            "fuelType" to fuelType.trim().take(80),
            "color" to color.trim().take(80),
            "origin" to origin.trim().take(100),
            "imageUrl" to imageUrl.trim().take(550_000),
            "description" to resolvedDescription,
            "tiktokUrl" to tiktokUrl.trim().take(500),
            "status" to status.value,
            "featured" to featured,
            "createdAt" to createdAtIso.take(64),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        originalPrice?.takeIf { it in 0L..100_000_000L }?.let {
            raw["originalPrice"] = it
        }
        val cleanGallery = galleryImages.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 550_000 }.take(10)
        if (cleanGallery.isNotEmpty()) {
            raw["galleryImages"] = cleanGallery
        }
        return raw
    }

    fun toUpdateMap(): Map<String, Any> {
        val cleanGallery = galleryImages.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 550_000 }.take(10)
        return mapOf(
            "title" to resolvedTitle,
            "make" to make.trim().take(80),
            "model" to model.trim().take(120),
            "year" to year.coerceIn(1950L, 2100L),
            "price" to price.coerceIn(0L, 100_000_000L),
            "originalPrice" to (originalPrice?.takeIf { it in 0L..100_000_000L } ?: FieldValue.delete()),
            "mileage" to mileage.trim().take(60),
            "transmission" to transmission.trim().take(80),
            "fuelType" to fuelType.trim().take(80),
            "color" to color.trim().take(80),
            "origin" to origin.trim().take(100),
            "imageUrl" to imageUrl.trim().take(550_000),
            "galleryImages" to (cleanGallery.takeIf { it.isNotEmpty() } ?: FieldValue.delete()),
            "description" to resolvedDescription,
            "tiktokUrl" to tiktokUrl.trim().take(500),
            "status" to status.value,
            "featured" to featured,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun currentIsoTimestamp(): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return formatter.format(Date())
        }
    }
}
