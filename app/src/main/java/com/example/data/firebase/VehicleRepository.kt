package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class VehicleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val vehiclesCollection
        get() = db.collection("vehicles")

    private fun requireSignedInAdmin() {
        val user = auth.currentUser
        if (user == null) {
            throw IllegalStateException("Please sign in with Google before modifying inventory.")
        }
    }

    fun observeVehicles(): Flow<List<Vehicle>> = flow {
        val path = "vehicles"
        emitAll(
            vehiclesCollection
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toVehicleSafe() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addVehicle(draft: VehicleDraft): Result<String> {
        var docPath = "vehicles"
        return try {
            requireSignedInAdmin()
            validateDraftBounds(draft)
            val vehicleId = generateVehicleId(draft.year, draft.make, draft.model)
            val docRef = vehiclesCollection.document(vehicleId)
            docPath = docRef.path
            docRef.set(draft.toCreateMap(vehicleId)).await()
            Result.success(vehicleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehiclePrice(vehicleId: String, newPrice: Long): Result<Unit> {
        val docPath = "vehicles/$vehicleId"
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            require(newPrice in 0L..100_000_000L) { "Price must be between 0 and 100,000,000." }
            vehiclesCollection.document(vehicleId)
                .update(
                    mapOf(
                        "price" to newPrice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicleStatus(vehicleId: String, newStatus: VehicleStatus): Result<Unit> {
        val docPath = "vehicles/$vehicleId"
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            vehiclesCollection.document(vehicleId)
                .update(
                    mapOf(
                        "status" to newStatus.value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicleFeatured(vehicleId: String, featured: Boolean): Result<Unit> {
        val docPath = "vehicles/$vehicleId"
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            vehiclesCollection.document(vehicleId)
                .update(
                    mapOf(
                        "featured" to featured,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicle(vehicleId: String, draft: VehicleDraft): Result<Unit> {
        val docPath = "vehicles/$vehicleId"
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            validateDraftBounds(draft)
            vehiclesCollection.document(vehicleId)
                .update(draft.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        val docPath = "vehicles/$vehicleId"
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            vehiclesCollection.document(vehicleId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(friendlyException(e))
        }
    }

    private fun friendlyException(e: Exception): Exception {
        val firestoreEx = generateSequence(e as Throwable) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return if (firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            IllegalStateException(
                "Admin access required: sign in with alifcarimports@gmail.com or add your email in config/adminAccess."
            )
        } else {
            e
        }
    }

    private fun validateDraftBounds(draft: VehicleDraft) {
        require(draft.year in 1950L..2100L) { "Model year must be between 1950 and 2100." }
        require(draft.price in 0L..100_000_000L) { "Price must be between 0 and 100,000,000." }
        require(draft.imageUrl.trim().length in 1..550_000) { "Vehicle image is too large (max 550 KB)." }
    }

    private fun generateVehicleId(year: Long, make: String, model: String): String {
        val slug = "${year}_${make}_${model}"
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifEmpty { "unit" }
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        return "veh_${slug}_$suffix"
    }
}

private val JSON_STRING_ARRAY_ITEM_REGEX = Regex(""""((?:[^"\\]|\\.)+)"""")
private val IMAGE_VALUE_HINT_REGEX = Regex(
    """^(?:data:image/|https?://|gs://|/|./)|(?:uploads/|images/|cars/|alifcarimport/)|\.(?:jpe?g|png|webp|gif|avif|bmp)(?:[?#].*)?$""",
    RegexOption.IGNORE_CASE
)
private val NON_IMAGE_FIELDS = setOf(
    "id", "title", "make", "brand", "model", "year", "price", "originalPrice",
    "mileage", "transmission", "fuelType", "fuel", "color", "exteriorColor",
    "origin", "description", "tiktokUrl", "tiktok", "videoUrl", "status",
    "featured", "createdAt", "updatedAt"
)

private fun extractImageUrls(raw: Any?): List<String> = when (raw) {
    is String -> {
        val trimmed = raw.trim()
        when {
            trimmed.isEmpty() -> emptyList()
            trimmed.startsWith("data:", ignoreCase = true) -> listOf(trimmed)
            trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                JSON_STRING_ARRAY_ITEM_REGEX.findAll(trimmed)
                    .map { it.groupValues[1].replace("\\/", "/").trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
            trimmed.contains('\n') -> {
                trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
            else -> listOf(trimmed)
        }
    }
    is List<*> -> raw.flatMap { extractImageUrls(it) }
    is Map<*, *> -> {
        val candidateKeys = listOf(
            "url", "URL", "imageUrl", "imageURL", "image_url",
            "downloadURL", "downloadUrl", "download_url",
            "src", "secure_url", "secureUrl", "uri", "href", "link",
            "path", "image", "photo", "thumbnail", "thumbnailUrl",
            "coverUrl", "preview", "data", "base64"
        )
        candidateKeys.firstNotNullOfOrNull { key ->
            extractImageUrls(raw[key]).firstOrNull()
        }?.let { listOf(it) } ?: emptyList()
    }
    else -> emptyList()
}

private fun String.isPlaceholderImageUrl(): Boolean {
    val t = trim().lowercase(Locale.US)
    return t.isEmpty() ||
        t == "null" ||
        t == "undefined" ||
        t == "https://alifcarimport.com/placeholder.jpg" ||
        t == "https://mubarekzeru.github.io/alifcarimport/placeholder.jpg" ||
        t == "/placeholder.jpg" ||
        t == "placeholder.jpg" ||
        t.endsWith("/placeholder.jpg") ||
        t.endsWith("/placeholder.png") ||
        t.startsWith("blob:")
}

private fun String.looksLikeRawBase64Image(): Boolean {
    val compact = replace("\\s+".toRegex(), "")
    return compact.length >= 64 && (
        compact.startsWith("/9j/") ||
            compact.startsWith("iVBORw0KGgo") ||
            compact.startsWith("UklGR") ||
            compact.startsWith("R0lGOD")
        )
}

private fun DocumentSnapshot.toVehicleSafe(): Vehicle? {
    val data = data ?: return null
    val docId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: id
    val galleryKeys = listOf(
        "galleryImages", "gallery", "galleryUrls", "images", "imageUrls",
        "photos", "photoUrls", "pictures", "media", "additionalImages",
        "extraImages", "files", "attachments"
    )
    val gallery = buildList {
        galleryKeys.forEach { key ->
            extractImageUrls(data[key]).forEach { url ->
                if (!url.isPlaceholderImageUrl() && url !in this) add(url)
            }
        }
    }.toMutableList()

    val primaryKeys = listOf(
        "imageUrl", "imageURL", "image_url", "image", "img", "imgUrl", "img_url",
        "mainImage", "mainImageUrl", "primaryImage", "primaryImageUrl",
        "featuredImage", "featuredImageUrl", "coverImage", "coverImageUrl",
        "coverUrl", "cover", "thumbnailUrl", "thumbnail", "thumbUrl", "thumb",
        "photoUrl", "photoURL", "photo_url", "photo", "pictureUrl", "picture",
        "carImage", "carImageUrl", "vehicleImage", "vehicleImageUrl",
        "heroImage", "imageSrc", "imageData", "imageBase64",
        "photoData", "photoBase64", "dataUrl"
    )
    val primaryCandidates = primaryKeys.flatMap { extractImageUrls(data[it]) }

    // Universal fallback scan in case the website admin stores image(s) under a custom key name
    if (primaryCandidates.none { !it.isPlaceholderImageUrl() } && gallery.isEmpty()) {
        data.forEach { (key, value) ->
            if (key !in NON_IMAGE_FIELDS) {
                extractImageUrls(value).forEach { candidate ->
                    if (!candidate.isPlaceholderImageUrl() &&
                        (IMAGE_VALUE_HINT_REGEX.containsMatchIn(candidate) || candidate.looksLikeRawBase64Image()) &&
                        candidate !in gallery
                    ) {
                        gallery.add(candidate)
                    }
                }
            }
        }
    }

    val resolvedPrimaryImage = primaryCandidates.firstOrNull { !it.isPlaceholderImageUrl() }
        ?: gallery.firstOrNull()
        ?: primaryCandidates.firstOrNull().orEmpty()

    val rawMileage = data["mileage"]
    val mileageString = when (rawMileage) {
        is String -> rawMileage
        is Number -> "${rawMileage.toLong()} km"
        else -> ""
    }
    val rawCreatedAt = data["createdAt"]
    val createdAtString = when (rawCreatedAt) {
        is String -> rawCreatedAt
        is com.google.firebase.Timestamp -> rawCreatedAt.toDate().toString()
        else -> ""
    }
    val parsedYear = when (val raw = data["year"]) {
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull() ?: 2024L
        else -> 2024L
    }
    val parsedPrice = when (val raw = data["price"]) {
        is Number -> raw.toLong()
        is String -> raw.replace(",", "").trim().toLongOrNull() ?: 0L
        else -> 0L
    }
    val parsedOriginalPrice = when (val raw = data["originalPrice"]) {
        is Number -> raw.toLong()
        is String -> raw.replace(",", "").trim().toLongOrNull()
        else -> null
    }
    val parsedFeatured = when (val raw = data["featured"]) {
        is Boolean -> raw
        is String -> raw.equals("true", ignoreCase = true)
        else -> false
    }
    val parsedUpdatedAt = runCatching {
        getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.getOrNull()

    val parsedMake = (data["make"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["brand"] as? String).orEmpty()
    val parsedFuelType = (data["fuelType"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["fuel"] as? String).orEmpty()
    val parsedColor = (data["color"] as? String)?.takeIf { it.isNotBlank() }
        ?: (data["exteriorColor"] as? String).orEmpty()

    return Vehicle(
        id = docId,
        title = (data["title"] as? String).orEmpty(),
        make = parsedMake,
        model = (data["model"] as? String).orEmpty(),
        year = parsedYear,
        price = parsedPrice,
        originalPrice = parsedOriginalPrice,
        mileage = mileageString,
        transmission = (data["transmission"] as? String).orEmpty(),
        fuelType = parsedFuelType,
        color = parsedColor,
        origin = (data["origin"] as? String).orEmpty(),
        imageUrl = resolvedPrimaryImage,
        galleryImages = gallery,
        description = (data["description"] as? String).orEmpty(),
        tiktokUrl = (data["tiktokUrl"] as? String).orEmpty(),
        status = (data["status"] as? String) ?: VehicleStatus.AVAILABLE.value,
        featured = parsedFeatured,
        createdAt = createdAtString,
        updatedAt = parsedUpdatedAt
    )
}
