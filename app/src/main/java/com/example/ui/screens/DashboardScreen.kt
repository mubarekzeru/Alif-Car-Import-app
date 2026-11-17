package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Vehicle
import com.example.ui.components.AddEditVehicleSheet
import com.example.ui.components.LotMetricsStrip
import com.example.ui.components.QuickPriceEditSheet
import com.example.ui.components.VehicleInventoryCard
import com.example.ui.viewmodel.FormSheetMode
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.LotMetrics
import com.example.ui.viewmodel.StatusFilter
import com.example.ui.viewmodel.VehicleSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: InventoryViewModel,
    userEmail: String?,
    onOpenVehicleDetail: (String) -> Unit,
    onSignOutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val priceEditVehicle by viewModel.priceEditVehicle.collectAsStateWithLifecycle()
    val formSheetState by viewModel.formSheetState.collectAsStateWithLifecycle()
    val deleteCandidate by viewModel.deleteCandidate.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            AsyncImage(
                                model = R.drawable.img_alif_logo,
                                contentDescription = "Alif Car Import logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.dashboard_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "ADMIN PORTAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSearchExpanded(!filterState.isSearchExpanded) },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (filterState.isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search vehicles"
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort vehicles"
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            VehicleSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label,
                                            fontWeight = if (filterState.sortOrder == order) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = if (filterState.sortOrder == order) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.updateSortOrder(order)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.testTag("account_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account menu"
                            )
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            if (!userEmail.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sign_out)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    onSignOutClick()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddVehicleSheet() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_vehicle_fab"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.action_add_vehicle),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedSplit = maxWidth >= 840.dp
            val isMediumGrid = maxWidth in 600.dp..839.dp

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = filterState.isSearchExpanded) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text(stringResource(R.string.action_search_vehicles)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_vehicles_input")
                    )
                }

                when (val state = uiState) {
                    InventoryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Syncing cloud lot inventory…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is InventoryUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.error_load_inventory),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is InventoryUiState.Success -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        LotMetricsStrip(metrics = state.metrics)
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusFilterRow(
                            selected = filterState.selectedStatus,
                            metrics = state.metrics,
                            onSelect = viewModel::selectStatusFilter
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isExpandedSplit) {
                            val selectedVehicle = state.filteredVehicles.firstOrNull {
                                it.id == selectedVehicleId
                            } ?: state.filteredVehicles.firstOrNull()

                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                ) {
                                    InventoryFeedContent(
                                        vehicles = state.filteredVehicles,
                                        selectedVehicleId = selectedVehicle?.id,
                                        isGrid = false,
                                        onCardClick = { viewModel.selectVehicle(it.id) },
                                        onQuickPriceClick = viewModel::openQuickPriceEdit,
                                        onStatusChange = viewModel::updateStatus,
                                        onToggleFeatured = viewModel::toggleFeatured,
                                        onEditClick = viewModel::openEditVehicleSheet,
                                        onDeleteClick = viewModel::requestDeleteVehicle,
                                        onAddVehicleClick = viewModel::openAddVehicleSheet
                                    )
                                }

                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Box(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .fillMaxHeight()
                                ) {
                                    if (selectedVehicle != null) {
                                        VehicleDetailPane(
                                            vehicle = selectedVehicle,
                                            onQuickPriceClick = {
                                                viewModel.openQuickPriceEdit(selectedVehicle)
                                            },
                                            onStatusChange = { status ->
                                                viewModel.updateStatus(selectedVehicle, status)
                                            },
                                            onToggleFeatured = {
                                                viewModel.toggleFeatured(selectedVehicle)
                                            },
                                            onEditClick = {
                                                viewModel.openEditVehicleSheet(selectedVehicle)
                                            },
                                            onDeleteClick = {
                                                viewModel.requestDeleteVehicle(selectedVehicle)
                                            }
                                        )
                                    } else {
                                        EmptyLotState(onAddClick = viewModel::openAddVehicleSheet)
                                    }
                                }
                            }
                        } else {
                            InventoryFeedContent(
                                vehicles = state.filteredVehicles,
                                selectedVehicleId = null,
                                isGrid = isMediumGrid,
                                onCardClick = { onOpenVehicleDetail(it.id) },
                                onQuickPriceClick = viewModel::openQuickPriceEdit,
                                onStatusChange = viewModel::updateStatus,
                                onToggleFeatured = viewModel::toggleFeatured,
                                onEditClick = viewModel::openEditVehicleSheet,
                                onDeleteClick = viewModel::requestDeleteVehicle,
                                onAddVehicleClick = viewModel::openAddVehicleSheet
                            )
                        }
                    }
                }
            }
        }
    }

    priceEditVehicle?.let { vehicle ->
        QuickPriceEditSheet(
            vehicle = vehicle,
            isSaving = isMutating,
            onDismiss = viewModel::dismissQuickPriceEdit,
            onSavePrice = { newPrice -> viewModel.saveQuickPrice(vehicle, newPrice) }
        )
    }

    formSheetState?.let { mode ->
        val editing = (mode as? FormSheetMode.Edit)?.vehicle
        AddEditVehicleSheet(
            editingVehicle = editing,
            isSaving = isMutating,
            onDismiss = viewModel::dismissFormSheet,
            onSubmit = { draft -> viewModel.submitVehicleForm(editing?.id, draft) }
        )
    }

    deleteCandidate?.let { vehicle ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove Vehicle?") },
            text = {
                Text(
                    "Remove ${vehicle.displayTitle} from your cloud lot inventory? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteVehicle,
                    enabled = !isMutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissDeleteDialog,
                    enabled = !isMutating
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: StatusFilter,
    metrics: LotMetrics,
    onSelect: (StatusFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StatusFilter.entries) { filter ->
            val count = when (filter) {
                StatusFilter.ALL -> metrics.totalCount
                StatusFilter.AVAILABLE -> metrics.availableCount
                StatusFilter.RESERVED -> metrics.reservedCount
                StatusFilter.SOLD -> metrics.soldCount
            }
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = "${filter.label} ($count)",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("status_tab_${filter.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun InventoryFeedContent(
    vehicles: List<Vehicle>,
    selectedVehicleId: String?,
    isGrid: Boolean,
    onCardClick: (Vehicle) -> Unit,
    onQuickPriceClick: (Vehicle) -> Unit,
    onStatusChange: (Vehicle, com.example.data.model.VehicleStatus) -> Unit,
    onToggleFeatured: (Vehicle) -> Unit,
    onEditClick: (Vehicle) -> Unit,
    onDeleteClick: (Vehicle) -> Unit,
    onAddVehicleClick: () -> Unit
) {
    if (vehicles.isEmpty()) {
        EmptyLotState(onAddClick = onAddVehicleClick)
        return
    }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(vehicles, key = { it.id }) { vehicle ->
                VehicleInventoryCard(
                    vehicle = vehicle,
                    isSelected = vehicle.id == selectedVehicleId,
                    onCardClick = { onCardClick(vehicle) },
                    onQuickPriceClick = { onQuickPriceClick(vehicle) },
                    onStatusChange = { status -> onStatusChange(vehicle, status) },
                    onToggleFeatured = { onToggleFeatured(vehicle) },
                    onEditClick = { onEditClick(vehicle) },
                    onDeleteClick = { onDeleteClick(vehicle) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(vehicles, key = { it.id }) { vehicle ->
                VehicleInventoryCard(
                    vehicle = vehicle,
                    isSelected = vehicle.id == selectedVehicleId,
                    onCardClick = { onCardClick(vehicle) },
                    onQuickPriceClick = { onQuickPriceClick(vehicle) },
                    onStatusChange = { status -> onStatusChange(vehicle, status) },
                    onToggleFeatured = { onToggleFeatured(vehicle) },
                    onEditClick = { onEditClick(vehicle) },
                    onDeleteClick = { onDeleteClick(vehicle) }
                )
            }
        }
    }
}

@Composable
private fun EmptyLotState(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_lot_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_lot_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_add_vehicle_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_vehicle), fontWeight = FontWeight.Bold)
            }
        }
    }
}
