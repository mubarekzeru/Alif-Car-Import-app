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
        require(draft.make.trim().length in 1..80) { "Make must be 1 to 80 characters." }
        require(draft.model.trim().length in 1..120) { "Model must be 1 to 120 characters." }
        require(draft.price in 0L..100_000_000L) { "Price must be between 0 and 100,000,000." }
        require(draft.imageUrl.trim().length in 1..550_000) { "Vehicle image is required (max 550 KB)." }
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

private fun DocumentSnapshot.toVehicleSafe(): Vehicle? {
    val data = data ?: return null
    val docId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: id
    val gallery = (data["galleryImages"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
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

    return Vehicle(
        id = docId,
        title = (data["title"] as? String).orEmpty(),
        make = (data["make"] as? String).orEmpty(),
        model = (data["model"] as? String).orEmpty(),
        year = parsedYear,
        price = parsedPrice,
        originalPrice = parsedOriginalPrice,
        mileage = mileageString,
        transmission = (data["transmission"] as? String).orEmpty(),
        fuelType = (data["fuelType"] as? String).orEmpty(),
        color = (data["color"] as? String).orEmpty(),
        origin = (data["origin"] as? String).orEmpty(),
        imageUrl = (data["imageUrl"] as? String).orEmpty(),
        galleryImages = gallery,
        description = (data["description"] as? String).orEmpty(),
        tiktokUrl = (data["tiktokUrl"] as? String).orEmpty(),
        status = (data["status"] as? String) ?: VehicleStatus.AVAILABLE.value,
        featured = parsedFeatured,
        createdAt = createdAtString,
        updatedAt = parsedUpdatedAt
    )
}
