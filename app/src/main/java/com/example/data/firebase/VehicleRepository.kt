package com.example.data.firebase

import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.example.data.network.CloudflareApiClient
import com.example.data.network.CloudflareApiException
import com.example.data.network.ShowroomLoadState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import java.util.Locale
import java.util.UUID

class VehicleRepository(
    private val apiClient: CloudflareApiClient,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireSignedInAdmin() {
        if (auth.currentUser == null) {
            throw IllegalStateException("Please sign in before modifying inventory.")
        }
    }

    fun observeShowroomState(): Flow<ShowroomLoadState> = flow {
        val initial = apiClient.showroomState.value
        if (initial is ShowroomLoadState.Loaded) {
            emit(initial)
        }
        apiClient.ensureShowroomLoaded(forceRefresh = false)
        emitAll(apiClient.showroomState)
    }

    fun observeVehicles(): Flow<List<Vehicle>> = flow {
        apiClient.ensureShowroomLoaded(forceRefresh = false)
        emitAll(
            apiClient.showroomState.transform { state ->
                when (state) {
                    is ShowroomLoadState.Loaded -> emit(state.payload.vehicles)
                    is ShowroomLoadState.Error -> throw IllegalStateException(state.message)
                    ShowroomLoadState.Loading -> Unit
                }
            }
        )
    }

    suspend fun refreshVehicles(): Result<List<Vehicle>> {
        return apiClient.ensureShowroomLoaded(forceRefresh = true).map { it.vehicles }
    }

    suspend fun addVehicle(draft: VehicleDraft): Result<String> {
        return try {
            requireSignedInAdmin()
            validateDraftBounds(draft)
            val vehicleId = generateVehicleId(draft.year, draft.make, draft.model)
            val payload = draft.toApiJson(vehicleId = vehicleId)
            apiClient.putVehicle(vehicleId, payload)
            Result.success(vehicleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehiclePrice(vehicleId: String, newPrice: Long): Result<Unit> {
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            require(newPrice in 0L..100_000_000L) { "Price must be between 0 and 100,000,000." }
            val existing = findVehicleOrFetch(vehicleId)
                ?: return Result.failure(IllegalStateException("Vehicle not found."))
            val updated = existing.copy(price = newPrice)
            apiClient.putVehicle(vehicleId, updated.toApiJson())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicleStatus(vehicleId: String, newStatus: VehicleStatus): Result<Unit> {
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val existing = findVehicleOrFetch(vehicleId)
                ?: return Result.failure(IllegalStateException("Vehicle not found."))
            val updated = existing.copy(status = newStatus.value)
            apiClient.putVehicle(vehicleId, updated.toApiJson())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicleFeatured(vehicleId: String, featured: Boolean): Result<Unit> {
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val existing = findVehicleOrFetch(vehicleId)
                ?: return Result.failure(IllegalStateException("Vehicle not found."))
            val updated = existing.copy(featured = featured)
            apiClient.putVehicle(vehicleId, updated.toApiJson())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun updateVehicle(vehicleId: String, draft: VehicleDraft): Result<Unit> {
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            validateDraftBounds(draft)
            val existingCreatedAt = apiClient.currentVehicles()
                .firstOrNull { it.id == vehicleId }
                ?.createdAt
                ?.takeIf { it.isNotBlank() }
                ?: VehicleDraft.currentIsoTimestamp()
            val payload = draft.toApiJson(
                vehicleId = vehicleId,
                createdAtIso = existingCreatedAt
            )
            apiClient.putVehicle(vehicleId, payload)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        return try {
            requireSignedInAdmin()
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            apiClient.deleteVehicle(vehicleId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(friendlyException(e))
        }
    }

    private suspend fun findVehicleOrFetch(vehicleId: String): Vehicle? {
        apiClient.currentVehicles().firstOrNull { it.id == vehicleId }?.let { return it }
        val loaded = apiClient.ensureShowroomLoaded(forceRefresh = true).getOrNull()
        return loaded?.vehicles?.firstOrNull { it.id == vehicleId }
    }

    private fun friendlyException(e: Exception): Exception {
        return if (e is CloudflareApiException && e.isAuthOrPermissionError) {
            IllegalStateException(
                "Admin access required: sign in with an authorized admin account."
            )
        } else {
            e
        }
    }

    private fun validateDraftBounds(draft: VehicleDraft) {
        require(draft.year in 1950L..2100L) { "Model year must be between 1950 and 2100." }
        require(draft.price in 0L..100_000_000L) { "Price must be between 0 and 100,000,000." }
        require(draft.imageUrl.trim().length in 1..VehicleDraft.MAX_IMAGE_FIELD_LENGTH) {
            "Vehicle image is missing or too large."
        }
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
