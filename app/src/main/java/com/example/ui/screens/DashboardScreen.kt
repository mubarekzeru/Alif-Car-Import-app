package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.firebase.InquiriesState
import com.example.data.model.Vehicle
import com.example.data.model.VehicleStatus
import com.example.ui.components.BottomNavTab
import com.example.ui.components.CustomerInquiriesTabContent
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.LotMetricsStrip
import com.example.ui.components.SettingsTabContent
import com.example.ui.components.VehicleInventoryCard
import com.example.ui.components.decodeImageModel
import com.example.ui.notifications.SecurityNotificationHelper
import com.example.ui.theme.WarmCoralTop
import com.example.ui.theme.WarmPeachWash
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.LotMetrics
import com.example.ui.viewmodel.PortalViewModel
import com.example.ui.viewmodel.StatusFilter
import com.example.ui.viewmodel.VehicleSortOrder

private val TabSlideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun DashboardScreen(
    viewModel: InventoryViewModel,
    portalViewModel: PortalViewModel,
    userEmail: String?,
    onOpenVehicleDetail: (String) -> Unit,
    onOpenQuickPrice: (String) -> Unit,
    onOpenAddVehicle: () -> Unit,
    onOpenEditVehicle: (String) -> Unit,
    onOpenAddTikTok: () -> Unit,
    onOpenEditTikTok: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenContactSocialLocation: () -> Unit,
    onOpenAdminAccess: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val deleteCandidate by viewModel.deleteCandidate.collectAsStateWithLifecycle()

    val selectedBottomTab by portalViewModel.bottomNavTab.collectAsStateWithLifecycle()
    val dashboardSection by portalViewModel.dashboardSection.collectAsStateWithLifecycle()
    val deleteTikTokCandidate by portalViewModel.deleteTikTokCandidate.collectAsStateWithLifecycle()
    val isPortalMutating by portalViewModel.isMutating.collectAsStateWithLifecycle()
    val isChangingPassword by portalViewModel.isChangingPassword.collectAsStateWithLifecycle()
    val portalSnackbar by portalViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val showroomConfig by portalViewModel.showroomConfig.collectAsStateWithLifecycle()
    val tikTokPosts by portalViewModel.tikTokPosts.collectAsStateWithLifecycle()
    val inquiriesState by portalViewModel.inquiriesState.collectAsStateWithLifecycle()
    val loginAlerts by portalViewModel.loginAlerts.collectAsStateWithLifecycle()
    val sessions by portalViewModel.sessions.collectAsStateWithLifecycle()
    val currentLoginAtMillis by portalViewModel.currentLoginAtMillis.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }

    // Ordered from lowest priority to highest priority (last enabled BackHandler wins)
    BackHandler(enabled = selectedBottomTab != BottomNavTab.HOME) {
        portalViewModel.selectBottomNavTab(BottomNavTab.HOME)
    }
    BackHandler(enabled = addMenuExpanded) {
        addMenuExpanded = false
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !SecurityNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pendingAlertsCount = portalViewModel.pendingAlertsCount(loginAlerts)
    val inquiryCount = (inquiriesState as? InquiriesState.Success)?.inquiries?.size ?: 0
    val allVehicles = (uiState as? InventoryUiState.Success)?.allVehicles.orEmpty()

    val brandDisplayName = showroomConfig.branding.companyName
        .trim()
        .ifBlank { stringResource(R.string.app_name) }
    val brandLogoUrl = showroomConfig.branding.logoUrl.trim()
    val brandLogoModel: Any = remember(brandLogoUrl) {
        decodeImageModel(brandLogoUrl) ?: R.drawable.img_alif_logo
    }
    val brandLogoRequest = remember(brandLogoModel) {
        ImageRequest.Builder(context)
            .data(brandLogoModel)
            .crossfade(160)
            .build()
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    LaunchedEffect(portalSnackbar) {
        val message = portalSnackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        portalViewModel.consumeSnackbar()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = selectedBottomTab,
            transitionSpec = {
                val movingForward = targetState.ordinal > initialState.ordinal
                val enterSlide = slideInHorizontally(
                    animationSpec = tween(300, easing = TabSlideEasing),
                    initialOffsetX = { width -> if (movingForward) width / 5 else -width / 5 }
                ) + fadeIn(tween(240, easing = TabSlideEasing))
                val exitSlide = slideOutHorizontally(
                    animationSpec = tween(260, easing = TabSlideEasing),
                    targetOffsetX = { width -> if (movingForward) -width / 6 else width / 6 }
                ) + fadeOut(tween(180))
                enterSlide togetherWith exitSlide
            },
            label = "bottom_nav_tab_transition",
            modifier = Modifier.fillMaxSize()
        ) { activeTab ->
            when (activeTab) {
                BottomNavTab.HOME -> {
                    HomeDashboardTab(
                        uiState = uiState,
                        filterState = filterState,
                        selectedVehicleId = selectedVehicleId,
                        sortMenuExpanded = sortMenuExpanded,
                        pendingAlertsCount = pendingAlertsCount,
                        brandDisplayName = brandDisplayName,
                        brandLogoRequest = brandLogoRequest,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onToggleSortMenu = { sortMenuExpanded = it },
                        onSelectSortOrder = { order ->
                            sortMenuExpanded = false
                            viewModel.updateSortOrder(order)
                        },
                        onSelectStatusFilter = viewModel::selectStatusFilter,
                        onOpenNotifications = onOpenNotifications,
                        onOpenSettingsTab = {
                            portalViewModel.selectBottomNavTab(BottomNavTab.SETTINGS)
                        },
                        onSelectVehicleSplit = viewModel::selectVehicle,
                        onOpenVehicleDetail = onOpenVehicleDetail,
                        onOpenQuickPrice = onOpenQuickPrice,
                        onStatusChange = viewModel::updateStatus,
                        onToggleFeatured = viewModel::toggleFeatured,
                        onOpenEditVehicle = onOpenEditVehicle,
                        onDeleteVehicle = viewModel::requestDeleteVehicle,
                        onOpenAddVehicle = onOpenAddVehicle
                    )
                }

                BottomNavTab.INQUIRIES -> {
                    CustomerInquiriesTabContent(state = inquiriesState)
                }

                BottomNavTab.SETTINGS -> {
                    SettingsTabContent(
                        userEmail = userEmail,
                        brandDisplayName = brandDisplayName,
                        brandLogoModel = brandLogoRequest,
                        selectedSection = dashboardSection,
                        vehicles = allVehicles,
                        tikTokPosts = tikTokPosts,
                        tikTokHandle = showroomConfig.tiktok.handle,
                        sessions = sessions,
                        loginAtMillis = currentLoginAtMillis,
                        isMutatingPassword = isChangingPassword,
                        onSelectSection = portalViewModel::selectDashboardSection,
                        onOpenVehicleDetail = { vehicle -> onOpenVehicleDetail(vehicle.id) },
                        onOpenEditVehicle = { vehicle -> onOpenEditVehicle(vehicle.id) },
                        onOpenQuickPrice = { vehicle -> onOpenQuickPrice(vehicle.id) },
                        onDeleteVehicle = viewModel::requestDeleteVehicle,
                        onOpenAddVehicle = onOpenAddVehicle,
                        onOpenEditTikTok = { post -> onOpenEditTikTok(post.id) },
                        onDeleteTikTok = portalViewModel::requestDeleteTikTokPost,
                        onOpenAddTikTok = onOpenAddTikTok,
                        onDeactivateDevice = portalViewModel::deactivateOtherDevice,
                        onChangePassword = portalViewModel::changePassword,
                        onOpenContactSocialLocation = onOpenContactSocialLocation,
                        onOpenAdminAccess = onOpenAdminAccess,
                        onSignOutClick = onSignOutClick
                    )
                }
            }
        }

        // Click-outside scrim when "+ Add" menu is expanded
        AnimatedVisibility(
            visible = addMenuExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { addMenuExpanded = false }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 84.dp)
        )

        FloatingBottomNavBar(
            selectedTab = selectedBottomTab,
            isAddExpanded = addMenuExpanded,
            inquiriesBadgeCount = inquiryCount,
            onSelectTab = { tab ->
                addMenuExpanded = false
                portalViewModel.selectBottomNavTab(tab)
            },
            onToggleAddMenu = { addMenuExpanded = !addMenuExpanded },
            onAddVehicleClick = {
                addMenuExpanded = false
                onOpenAddVehicle()
            },
            onAddTikTokClick = {
                addMenuExpanded = false
                onOpenAddTikTok()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
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

    deleteTikTokCandidate?.let { post ->
        AlertDialog(
            onDismissRequest = portalViewModel::dismissDeleteTikTokDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove TikTok Video Link?") },
            text = {
                Text(
                    "Remove ${post.title.ifBlank { "this TikTok video link" }} from your website showcase?"
                )
            },
            confirmButton = {
                Button(
                    onClick = portalViewModel::confirmDeleteTikTokPost,
                    enabled = !isPortalMutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = portalViewModel::dismissDeleteTikTokDialog,
                    enabled = !isPortalMutating
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HomeDashboardTab(
    uiState: InventoryUiState,
    filterState: FilterState,
    selectedVehicleId: String?,
    sortMenuExpanded: Boolean,
    pendingAlertsCount: Int,
    brandDisplayName: String,
    brandLogoRequest: ImageRequest,
    onSearchQueryChange: (String) -> Unit,
    onToggleSortMenu: (Boolean) -> Unit,
    onSelectSortOrder: (VehicleSortOrder) -> Unit,
    onSelectStatusFilter: (StatusFilter) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onSelectVehicleSplit: (String) -> Unit,
    onOpenVehicleDetail: (String) -> Unit,
    onOpenQuickPrice: (String) -> Unit,
    onStatusChange: (Vehicle, VehicleStatus) -> Unit,
    onToggleFeatured: (Vehicle) -> Unit,
    onOpenEditVehicle: (String) -> Unit,
    onDeleteVehicle: (Vehicle) -> Unit,
    onOpenAddVehicle: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedSplit = maxWidth >= 840.dp
        val isMediumGrid = maxWidth in 600.dp..839.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Warm Coral-Orange Header Wash matching Screen 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                WarmCoralTop,
                                WarmPeachWash,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Row: Clean Inventory Title (Left) + Bell & Profile Logo (Right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inventory",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                shadowElevation = 3.dp
                            ) {
                                IconButton(
                                    onClick = onOpenNotifications,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("notifications_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (pendingAlertsCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(
                                                        text = if (pendingAlertsCount > 99) "99+" else pendingAlertsCount.toString()
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (pendingAlertsCount > 0) {
                                                Icons.Default.NotificationsActive
                                            } else {
                                                Icons.Default.Notifications
                                            },
                                            contentDescription = "Login alerts",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = onOpenSettingsTab,
                                color = Color.White,
                                shape = CircleShape,
                                border = BorderStroke(1.5.dp, Color.White),
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("account_menu_button")
                            ) {
                                AsyncImage(
                                    model = brandLogoRequest,
                                    contentDescription = "$brandDisplayName account",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Header Metrics inside the warm gradient wash
                    if (uiState is InventoryUiState.Success) {
                        LotMetricsStrip(metrics = uiState.metrics)
                    }
                }
            }

            // Capsule Search Pill + Sort button matching Screen 1
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.action_search_vehicles),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                IconButton(
                                    onClick = { onToggleSortMenu(true) },
                                    modifier = Modifier.testTag("sort_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort vehicles",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { onToggleSortMenu(false) }
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
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        },
                                        onClick = { onSelectSortOrder(order) }
                                    )
                                }
                            }
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("search_vehicles_input")
            )

            AnimatedContent(
                targetState = uiState,
                contentKey = { state ->
                    when (state) {
                        InventoryUiState.Loading -> "loading"
                        is InventoryUiState.Error -> "error"
                        is InventoryUiState.Success -> "success"
                    }
                },
                transitionSpec = {
                    fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(160))
                },
                label = "inventory_state_transition",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    InventoryUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 108.dp),
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
                                .padding(24.dp)
                                .padding(bottom = 108.dp),
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            StatusFilterRow(
                                selected = filterState.selectedStatus,
                                metrics = state.metrics,
                                onSelect = onSelectStatusFilter
                            )
                            Spacer(modifier = Modifier.height(6.dp))

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
                                            onCardClick = { onSelectVehicleSplit(it.id) },
                                            onQuickPriceClick = { onOpenQuickPrice(it.id) },
                                            onStatusChange = onStatusChange,
                                            onToggleFeatured = onToggleFeatured,
                                            onEditClick = { onOpenEditVehicle(it.id) },
                                            onDeleteClick = onDeleteVehicle,
                                            onAddVehicleClick = onOpenAddVehicle
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
                                                    onOpenQuickPrice(selectedVehicle.id)
                                                },
                                                onStatusChange = { status ->
                                                    onStatusChange(selectedVehicle, status)
                                                },
                                                onToggleFeatured = {
                                                    onToggleFeatured(selectedVehicle)
                                                },
                                                onEditClick = {
                                                    onOpenEditVehicle(selectedVehicle.id)
                                                },
                                                onDeleteClick = {
                                                    onDeleteVehicle(selectedVehicle)
                                                }
                                            )
                                        } else {
                                            EmptyLotState(onAddClick = onOpenAddVehicle)
                                        }
                                    }
                                }
                            } else {
                                InventoryFeedContent(
                                    vehicles = state.filteredVehicles,
                                    selectedVehicleId = null,
                                    isGrid = isMediumGrid,
                                    onCardClick = { onOpenVehicleDetail(it.id) },
                                    onQuickPriceClick = { onOpenQuickPrice(it.id) },
                                    onStatusChange = onStatusChange,
                                    onToggleFeatured = onToggleFeatured,
                                    onEditClick = { onOpenEditVehicle(it.id) },
                                    onDeleteClick = onDeleteVehicle,
                                    onAddVehicleClick = onOpenAddVehicle
                                )
                            }
                        }
                    }
                }
            }
        }
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
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                animationSpec = tween(200),
                label = "chip_container"
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "chip_label"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                animationSpec = tween(200),
                label = "chip_border"
            )
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                shape = CircleShape,
                label = {
                    Text(
                        text = "${filter.label} ($count)",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = containerColor,
                    selectedLabelColor = labelColor,
                    containerColor = containerColor,
                    labelColor = labelColor
                ),
                border = BorderStroke(width = 1.dp, color = borderColor),
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
    onStatusChange: (Vehicle, VehicleStatus) -> Unit,
    onToggleFeatured: (Vehicle) -> Unit,
    onEditClick: (Vehicle) -> Unit,
    onDeleteClick: (Vehicle) -> Unit,
    onAddVehicleClick: () -> Unit
) {
    if (vehicles.isEmpty()) {
        EmptyLotState(onAddClick = onAddVehicleClick)
        return
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    if (isGrid) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = vehicles,
                key = { it.id },
                contentType = { "vehicle_card" }
            ) { vehicle ->
                VehicleInventoryCard(
                    vehicle = vehicle,
                    isSelected = vehicle.id == selectedVehicleId,
                    onCardClick = { onCardClick(vehicle) },
                    onQuickPriceClick = { onQuickPriceClick(vehicle) },
                    onStatusChange = { status -> onStatusChange(vehicle, status) },
                    onToggleFeatured = { onToggleFeatured(vehicle) },
                    onEditClick = { onEditClick(vehicle) },
                    onDeleteClick = { onDeleteClick(vehicle) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(200),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(160)
                    )
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = vehicles,
                key = { it.id },
                contentType = { "vehicle_card" }
            ) { vehicle ->
                VehicleInventoryCard(
                    vehicle = vehicle,
                    isSelected = vehicle.id == selectedVehicleId,
                    onCardClick = { onCardClick(vehicle) },
                    onQuickPriceClick = { onQuickPriceClick(vehicle) },
                    onStatusChange = { status -> onStatusChange(vehicle, status) },
                    onToggleFeatured = { onToggleFeatured(vehicle) },
                    onEditClick = { onEditClick(vehicle) },
                    onDeleteClick = { onDeleteClick(vehicle) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(200),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(160)
                    )
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
            .padding(24.dp)
            .padding(bottom = 108.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
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
                shape = CircleShape,
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
