package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.draw.rotate
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
import com.example.data.model.TikTokPost
import com.example.data.model.Vehicle
import com.example.ui.components.AddEditTikTokSheet
import com.example.ui.components.AddEditVehicleSheet
import com.example.ui.components.AdminAccessSheet
import com.example.ui.components.LotMetricsStrip
import com.example.ui.components.NotificationsSheet
import com.example.ui.components.QuickPriceEditSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.ShowroomConfigSheet
import com.example.ui.components.TikTokPostCard
import com.example.ui.components.VehicleInventoryCard
import com.example.ui.components.decodeImageModel
import com.example.ui.notifications.SecurityNotificationHelper
import com.example.ui.viewmodel.ActivePortalSheet
import com.example.ui.viewmodel.DashboardSection
import com.example.ui.viewmodel.FormSheetMode
import com.example.ui.viewmodel.InventoryUiState
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.LotMetrics
import com.example.ui.viewmodel.PortalViewModel
import com.example.ui.viewmodel.StatusFilter
import com.example.ui.viewmodel.TikTokFormMode
import com.example.ui.viewmodel.VehicleSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: InventoryViewModel,
    portalViewModel: PortalViewModel,
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

    val dashboardSection by portalViewModel.dashboardSection.collectAsStateWithLifecycle()
    val activePortalSheet by portalViewModel.activeSheet.collectAsStateWithLifecycle()
    val notificationTab by portalViewModel.notificationTab.collectAsStateWithLifecycle()
    val tikTokFormMode by portalViewModel.tikTokFormMode.collectAsStateWithLifecycle()
    val deleteTikTokCandidate by portalViewModel.deleteTikTokCandidate.collectAsStateWithLifecycle()
    val isPortalMutating by portalViewModel.isMutating.collectAsStateWithLifecycle()
    val isChangingPassword by portalViewModel.isChangingPassword.collectAsStateWithLifecycle()
    val portalSnackbar by portalViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val showroomConfig by portalViewModel.showroomConfig.collectAsStateWithLifecycle()
    val tikTokPosts by portalViewModel.tikTokPosts.collectAsStateWithLifecycle()
    val inquiriesState by portalViewModel.inquiriesState.collectAsStateWithLifecycle()
    val adminAccessState by portalViewModel.adminAccessState.collectAsStateWithLifecycle()
    val sessions by portalViewModel.sessions.collectAsStateWithLifecycle()
    val loginAlerts by portalViewModel.loginAlerts.collectAsStateWithLifecycle()
    val currentLoginAtMillis by portalViewModel.currentLoginAtMillis.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    var addMenuExpanded by remember { mutableStateOf(false) }

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
    val totalNotificationBadge = pendingAlertsCount + inquiryCount

    val vehicleCount = (uiState as? InventoryUiState.Success)?.allVehicles?.size ?: 0

    val brandDisplayName = showroomConfig.branding.companyName
        .trim()
        .ifBlank { stringResource(R.string.app_name) }
    val brandLogoUrl = showroomConfig.branding.logoUrl.trim()
    val brandLogoRequest = remember(brandLogoUrl) {
        val model: Any = decodeImageModel(brandLogoUrl) ?: R.drawable.img_alif_logo
        ImageRequest.Builder(context)
            .data(model)
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

    val switcherArrowRotation by animateFloatAsState(
        targetValue = if (sectionMenuExpanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "section_arrow_rotation"
    )

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
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { sectionMenuExpanded = !sectionMenuExpanded }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("section_switcher_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedContent(
                            targetState = dashboardSection,
                            transitionSpec = {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                            },
                            label = "header_section_label"
                        ) { section ->
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch dashboard view",
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(switcherArrowRotation)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSearchExpanded(!filterState.isSearchExpanded) },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (filterState.isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (dashboardSection == DashboardSection.INVENTORY) {
                                "Search vehicles"
                            } else {
                                "Search TikTok videos"
                            }
                        )
                    }

                    if (dashboardSection == DashboardSection.INVENTORY) {
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
                    }

                    IconButton(
                        onClick = { portalViewModel.openNotifications() },
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (totalNotificationBadge > 0) {
                                    Badge(
                                        containerColor = if (pendingAlertsCount > 0) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    ) {
                                        Text(
                                            text = if (totalNotificationBadge > 99) "99+" else totalNotificationBadge.toString()
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
                                contentDescription = "Notifications"
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.testTag("account_menu_button")
                        ) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                ),
                                modifier = Modifier.size(34.dp)
                            ) {
                                AsyncImage(
                                    model = brandLogoRequest,
                                    contentDescription = "$brandDisplayName profile menu",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = Color.White,
                                    shape = CircleShape,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    AsyncImage(
                                        model = brandLogoRequest,
                                        contentDescription = brandDisplayName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column {
                                    Text(
                                        text = brandDisplayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ADMIN PORTAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!userEmail.isNullOrBlank()) {
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    portalViewModel.openSheet(ActivePortalSheet.SETTINGS)
                                },
                                modifier = Modifier.testTag("open_settings_menu_item")
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = addMenuExpanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(180)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut(tween(130))
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = {
                                addMenuExpanded = false
                                portalViewModel.openAddTikTokForm()
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_tiktok_option")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Add TikTok Video Link",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                addMenuExpanded = false
                                viewModel.openAddVehicleSheet()
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_vehicle_option")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Add Vehicle",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = { addMenuExpanded = !addMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_menu_fab"),
                    icon = {
                        Icon(
                            imageVector = if (addMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = if (addMenuExpanded) "Close" else "Add",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
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
                // Sliding down section switcher under "Inventory v"
                AnimatedVisibility(
                    visible = sectionMenuExpanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(180)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut(tween(140))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionSwitchOptionRow(
                                icon = Icons.Default.DirectionsCar,
                                label = "Inventory",
                                count = vehicleCount,
                                isSelected = dashboardSection == DashboardSection.INVENTORY,
                                onClick = {
                                    sectionMenuExpanded = false
                                    portalViewModel.selectDashboardSection(DashboardSection.INVENTORY)
                                },
                                testTag = "switch_to_inventory_option"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            SectionSwitchOptionRow(
                                icon = Icons.Default.VideoLibrary,
                                label = "TikTok Videos",
                                count = tikTokPosts.size,
                                isSelected = dashboardSection == DashboardSection.TIKTOK_VIDEOS,
                                onClick = {
                                    sectionMenuExpanded = false
                                    portalViewModel.selectDashboardSection(DashboardSection.TIKTOK_VIDEOS)
                                },
                                testTag = "switch_to_tiktok_option"
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = filterState.isSearchExpanded,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(180)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(tween(140))
                ) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = {
                            Text(
                                if (dashboardSection == DashboardSection.INVENTORY) {
                                    stringResource(R.string.action_search_vehicles)
                                } else {
                                    "Search TikTok video title, caption, or link"
                                }
                            )
                        },
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

                AnimatedContent(
                    targetState = dashboardSection,
                    transitionSpec = {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            expandVertically(tween(220))) togetherWith fadeOut(tween(150))
                    },
                    label = "dashboard_section_content"
                ) { activeSection ->
                    when (activeSection) {
                        DashboardSection.TIKTOK_VIDEOS -> {
                            val query = filterState.searchQuery.trim().lowercase()
                            val filteredPosts = remember(tikTokPosts, query) {
                                if (query.isEmpty()) {
                                    tikTokPosts
                                } else {
                                    tikTokPosts.filter {
                                        it.title.lowercase().contains(query) ||
                                            it.caption.lowercase().contains(query) ||
                                            it.tiktokUrl.lowercase().contains(query)
                                    }
                                }
                            }
                            TikTokVideosDashboardContent(
                                posts = filteredPosts,
                                handle = showroomConfig.tiktok.handle,
                                isGrid = isMediumGrid || isExpandedSplit,
                                onEditClick = portalViewModel::openEditTikTokForm,
                                onDeleteClick = portalViewModel::requestDeleteTikTokPost,
                                onAddTikTokClick = portalViewModel::openAddTikTokForm
                            )
                        }

                        DashboardSection.INVENTORY -> {
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
                                label = "inventory_state_transition"
                            ) { state ->
                                when (state) {
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
                                        Column(modifier = Modifier.fillMaxSize()) {
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

    tikTokFormMode?.let { mode ->
        val editing = (mode as? TikTokFormMode.Edit)?.post
        AddEditTikTokSheet(
            editingPost = editing,
            defaultSortOrder = tikTokPosts.size.toLong(),
            isSaving = isPortalMutating,
            onDismiss = portalViewModel::dismissTikTokForm,
            onSave = { postId, draft -> portalViewModel.saveTikTokPost(postId, draft) }
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

    when (activePortalSheet) {
        ActivePortalSheet.NONE -> Unit
        ActivePortalSheet.SETTINGS -> {
            SettingsSheet(
                userEmail = userEmail,
                sessions = sessions,
                loginAtMillis = currentLoginAtMillis,
                isMutatingPassword = isChangingPassword,
                onDeactivateDevice = portalViewModel::deactivateOtherDevice,
                onChangePassword = portalViewModel::changePassword,
                onOpenShowroomConfig = {
                    portalViewModel.openSheet(ActivePortalSheet.SHOWROOM)
                },
                onOpenAdminAccess = {
                    portalViewModel.openSheet(ActivePortalSheet.ADMIN_ACCESS)
                },
                onSignOutClick = {
                    portalViewModel.dismissSheet()
                    onSignOutClick()
                },
                onDismiss = portalViewModel::dismissSheet
            )
        }
        ActivePortalSheet.NOTIFICATIONS -> {
            NotificationsSheet(
                activeTab = notificationTab,
                alerts = loginAlerts,
                inquiriesState = inquiriesState,
                onSelectTab = portalViewModel::selectNotificationTab,
                onConfirmItWasMe = portalViewModel::confirmLoginAlertIsMe,
                onRejectNotMe = portalViewModel::rejectLoginAlertNotMe,
                onDismiss = portalViewModel::dismissSheet
            )
        }
        ActivePortalSheet.SHOWROOM -> {
            ShowroomConfigSheet(
                config = showroomConfig,
                isSaving = isPortalMutating,
                onDismiss = { portalViewModel.openSheet(ActivePortalSheet.SETTINGS) },
                onSave = portalViewModel::saveShowroomConfig
            )
        }
        ActivePortalSheet.ADMIN_ACCESS -> {
            AdminAccessSheet(
                state = adminAccessState,
                isSaving = isPortalMutating,
                onDismiss = { portalViewModel.openSheet(ActivePortalSheet.SETTINGS) },
                onSaveEmails = portalViewModel::saveAdminEmails
            )
        }
    }
}

@Composable
private fun SectionSwitchOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TikTokVideosDashboardContent(
    posts: List<TikTokPost>,
    handle: String,
    isGrid: Boolean,
    onEditClick: (TikTokPost) -> Unit,
    onDeleteClick: (TikTokPost) -> Unit,
    onAddTikTokClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "TIKTOK VIDEO SHOWCASE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${posts.size} Published Video Links",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (handle.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        if (posts.isEmpty()) {
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
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Text(
                        text = "No TikTok videos yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Tap Add TikTok Video Link to publish a video URL directly to your website showcase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onAddTikTokClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add TikTok Video Link", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = posts, key = { it.id }) { post ->
                    TikTokPostCard(
                        post = post,
                        onEditClick = { onEditClick(post) },
                        onDeleteClick = { onDeleteClick(post) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = posts, key = { it.id }) { post ->
                    TikTokPostCard(
                        post = post,
                        onEditClick = { onEditClick(post) },
                        onDeleteClick = { onDeleteClick(post) }
                    )
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
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                animationSpec = tween(180),
                label = "chip_container"
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "chip_label"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                animationSpec = tween(180),
                label = "chip_border"
            )
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

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    if (isGrid) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
