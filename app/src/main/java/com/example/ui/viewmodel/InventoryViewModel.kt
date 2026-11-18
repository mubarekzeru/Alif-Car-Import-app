package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.VehicleRepository
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface InventoryUiState {
    data object Loading : InventoryUiState
    data class Success(
        val allVehicles: List<Vehicle>,
        val filteredVehicles: List<Vehicle>,
        val metrics: LotMetrics
    ) : InventoryUiState
    data class Error(val message: String) : InventoryUiState
}

enum class StatusFilter(val label: String, val status: VehicleStatus?) {
    ALL("All", null),
    AVAILABLE("Available", VehicleStatus.AVAILABLE),
    RESERVED("Reserved", VehicleStatus.RESERVED),
    SOLD("Sold", VehicleStatus.SOLD)
}

enum class VehicleSortOrder(val label: String) {
    NEWEST("Newest"),
    PRICE_DESC("Price: High–Low"),
    PRICE_ASC("Price: Low–High"),
    YEAR_DESC("Year: Newest")
}

data class LotMetrics(
    val totalCount: Int = 0,
    val availableCount: Int = 0,
    val reservedCount: Int = 0,
    val soldCount: Int = 0,
    val activeInventoryValue: Long = 0L,
    val totalLotValue: Long = 0L
)

data class FilterState(
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val searchQuery: String = "",
    val sortOrder: VehicleSortOrder = VehicleSortOrder.NEWEST,
    val isSearchExpanded: Boolean = false
)

class InventoryViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _priceEditVehicle = MutableStateFlow<Vehicle?>(null)
    val priceEditVehicle: StateFlow<Vehicle?> = _priceEditVehicle.asStateFlow()

    private val _formSheetState = MutableStateFlow<FormSheetMode?>(null)
    val formSheetState: StateFlow<FormSheetMode?> = _formSheetState.asStateFlow()

    private val _deleteCandidate = MutableStateFlow<Vehicle?>(null)
    val deleteCandidate: StateFlow<Vehicle?> = _deleteCandidate.asStateFlow()

    private val vehiclesStream = repository.observeVehicles()

    val uiState: StateFlow<InventoryUiState> = combine(
        vehiclesStream,
        _filterState
    ) { vehicles, filters ->
        val metrics = computeLotMetrics(vehicles)
        val filtered = applyFiltersAndSort(vehicles, filters)
        InventoryUiState.Success(
            allVehicles = vehicles,
            filteredVehicles = filtered,
            metrics = metrics
        ) as InventoryUiState
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing vehicles", rootError)
        emit(InventoryUiState.Error(rootError.localizedMessage ?: "Failed to sync vehicle inventory"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = InventoryUiState.Loading
    )

    fun selectStatusFilter(filter: StatusFilter) {
        _filterState.update { it.copy(selectedStatus = filter) }
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchExpanded(expanded: Boolean) {
        _filterState.update {
            it.copy(
                isSearchExpanded = expanded,
                searchQuery = if (!expanded) "" else it.searchQuery
            )
        }
    }

    fun updateSortOrder(order: VehicleSortOrder) {
        _filterState.update { it.copy(sortOrder = order) }
    }

    fun selectVehicle(vehicleId: String?) {
        _selectedVehicleId.value = vehicleId
    }

    fun openQuickPriceEdit(vehicle: Vehicle) {
        _priceEditVehicle.value = vehicle
    }

    fun dismissQuickPriceEdit() {
        _priceEditVehicle.value = null
    }

    fun openAddVehicleSheet() {
        _formSheetState.value = FormSheetMode.Add
    }

    fun openEditVehicleSheet(vehicle: Vehicle) {
        _formSheetState.value = FormSheetMode.Edit(vehicle)
    }

    fun dismissFormSheet() {
        _formSheetState.value = null
    }

    fun requestDeleteVehicle(vehicle: Vehicle) {
        _deleteCandidate.value = vehicle
    }

    fun dismissDeleteDialog() {
        _deleteCandidate.value = null
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun saveQuickPrice(vehicle: Vehicle, newPrice: Long, onSuccess: () -> Unit = {}) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.updateVehiclePrice(vehicle.id, newPrice)
            _isMutating.value = false
            result.onSuccess {
                _priceEditVehicle.value = null
                _snackbarMessage.value = "Updated ${vehicle.displayTitle} price"
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update price"
            }
        }
    }

    fun updateStatus(vehicle: Vehicle, status: VehicleStatus) {
        if (vehicle.statusEnum == status || _isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.updateVehicleStatus(vehicle.id, status)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = "${vehicle.displayTitle} marked ${status.label}"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update status"
            }
        }
    }

    fun toggleFeatured(vehicle: Vehicle) {
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val nextFeatured = !vehicle.featured
            val result = repository.updateVehicleFeatured(vehicle.id, nextFeatured)
            _isMutating.value = false
            result.onSuccess {
                _snackbarMessage.value = if (nextFeatured) {
                    "Pinned ${vehicle.displayTitle} as featured"
                } else {
                    "Removed featured badge from ${vehicle.displayTitle}"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update featured flag"
            }
        }
    }

    fun submitVehicleForm(
        editingVehicleId: String?,
        draft: VehicleDraft,
        onSuccess: () -> Unit = {}
    ) {
        if (_isMutating.value) return
        val summaryName = draft.title.ifBlank {
            listOf(draft.make, draft.model).filter { it.isNotBlank() }.joinToString(" ")
        }.ifBlank { "vehicle" }

        viewModelScope.launch {
            _isMutating.value = true
            val writeDeferred = async {
                if (editingVehicleId == null) {
                    repository.addVehicle(draft).map { }
                } else {
                    repository.updateVehicle(editingVehicleId, draft)
                }
            }
            val fastResult = withTimeoutOrNull(FAST_PUBLISH_GRACE_MILLIS) {
                writeDeferred.await()
            }
            _isMutating.value = false

            if (fastResult != null) {
                fastResult.onSuccess {
                    _formSheetState.value = null
                    _snackbarMessage.value = if (editingVehicleId == null) {
                        "Published $summaryName"
                    } else {
                        "Saved changes to $summaryName"
                    }
                    onSuccess()
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Unable to save vehicle"
                }
            } else {
                _formSheetState.value = null
                _snackbarMessage.value = if (editingVehicleId == null) {
                    "Posted $summaryName — syncing to website"
                } else {
                    "Saved $summaryName — syncing to website"
                }
                onSuccess()
                launch {
                    writeDeferred.await().onFailure { err ->
                        _snackbarMessage.value = err.localizedMessage ?: "Sync error while saving $summaryName"
                    }
                }
            }
        }
    }

    fun confirmDeleteVehicle() {
        val candidate = _deleteCandidate.value ?: return
        if (_isMutating.value) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteVehicle(candidate.id)
            _isMutating.value = false
            _deleteCandidate.value = null
            result.onSuccess {
                if (_selectedVehicleId.value == candidate.id) {
                    _selectedVehicleId.value = null
                }
                _snackbarMessage.value = "Removed ${candidate.displayTitle} from lot"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Unable to delete vehicle"
            }
        }
    }

    private fun computeLotMetrics(vehicles: List<Vehicle>): LotMetrics {
        var available = 0
        var reserved = 0
        var sold = 0
        var activeValue = 0L
        var totalValue = 0L

        for (v in vehicles) {
            totalValue += v.price
            when (v.statusEnum) {
                VehicleStatus.AVAILABLE -> {
                    available++
                    activeValue += v.price
                }
                VehicleStatus.RESERVED -> {
                    reserved++
                    activeValue += v.price
                }
                VehicleStatus.SOLD -> sold++
            }
        }

        return LotMetrics(
            totalCount = vehicles.size,
            availableCount = available,
            reservedCount = reserved,
            soldCount = sold,
            activeInventoryValue = activeValue,
            totalLotValue = totalValue
        )
    }

    private fun applyFiltersAndSort(vehicles: List<Vehicle>, filters: FilterState): List<Vehicle> {
        val query = filters.searchQuery.trim().lowercase()
        val filtered = vehicles.filter { vehicle ->
            val matchesStatus = filters.selectedStatus.status == null ||
                vehicle.statusEnum == filters.selectedStatus.status
            val matchesQuery = query.isEmpty() ||
                vehicle.displayTitle.lowercase().contains(query) ||
                vehicle.make.lowercase().contains(query) ||
                vehicle.model.lowercase().contains(query) ||
                vehicle.origin.lowercase().contains(query) ||
                vehicle.id.lowercase().contains(query) ||
                vehicle.year.toString().contains(query)
            matchesStatus && matchesQuery
        }

        return when (filters.sortOrder) {
            VehicleSortOrder.NEWEST -> filtered.sortedByDescending {
                it.updatedAt ?: Timestamp(0, 0)
            }
            VehicleSortOrder.PRICE_DESC -> filtered.sortedByDescending { it.price }
            VehicleSortOrder.PRICE_ASC -> filtered.sortedBy { it.price }
            VehicleSortOrder.YEAR_DESC -> filtered.sortedByDescending { it.year }
        }
    }

    private companion object {
        const val TAG = "InventoryVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val FAST_PUBLISH_GRACE_MILLIS = 600L
    }
}

sealed interface FormSheetMode {
    data object Add : FormSheetMode
    data class Edit(val vehicle: Vehicle) : FormSheetMode
}
