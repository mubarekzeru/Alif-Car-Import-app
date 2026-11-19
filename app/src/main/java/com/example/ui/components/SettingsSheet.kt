package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TikTokPost
import com.example.data.model.Vehicle
import com.example.ui.viewmodel.DashboardSection

@Composable
fun SettingsTabContent(
    userEmail: String?,
    brandDisplayName: String,
    brandLogoCandidates: List<String>,
    selectedSection: DashboardSection,
    vehicles: List<Vehicle>,
    tikTokPosts: List<TikTokPost>,
    tikTokHandle: String,
    onSelectSection: (DashboardSection) -> Unit,
    onOpenVehicleDetail: (Vehicle) -> Unit,
    onOpenEditVehicle: (Vehicle) -> Unit,
    onOpenQuickPrice: (Vehicle) -> Unit,
    onDeleteVehicle: (Vehicle) -> Unit,
    onOpenAddVehicle: () -> Unit,
    onOpenEditTikTok: (TikTokPost) -> Unit,
    onDeleteTikTok: (TikTokPost) -> Unit,
    onOpenAddTikTok: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    var selectedTikTokDetail by remember { mutableStateOf<TikTokPost?>(null) }
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val vehicleCols = if (maxWidth >= 600.dp) 3 else 2
        val tikTokCols = if (maxWidth >= 600.dp) 4 else 3
        val vehicleRows = remember(vehicles, vehicleCols) { vehicles.chunked(vehicleCols) }
        val tikTokRows = remember(tikTokPosts, tikTokCols) { tikTokPosts.chunked(tikTokCols) }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 108.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        ) {
            // 0. Centered Account Header Card (Logo, Branding Name, Email stacked vertically + top-right Settings icon)
            item(key = "account_header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                        ) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("open_account_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Open Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                border = BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                                ),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(76.dp)
                            ) {
                                ResilientAppImage(
                                    candidateUrls = brandLogoCandidates,
                                    contentDescription = brandDisplayName,
                                    cacheKeyPrefix = "account_brand_logo",
                                    width = 240,
                                    height = 240,
                                    crossfadeMillis = 160,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(5.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                ) {
                                    AsyncImage(
                                        model = R.drawable.img_alif_logo,
                                        contentDescription = brandDisplayName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(5.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = brandDisplayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Inventory / TikTok Videos selector right above the vehicle & media post cards
            item(key = "account_section_switcher") {
                AccountSectionSwitcherCard(
                    selectedSection = selectedSection,
                    vehicleCount = vehicles.size,
                    tikTokCount = tikTokPosts.size,
                    isExpanded = sectionMenuExpanded,
                    onToggleExpand = { sectionMenuExpanded = !sectionMenuExpanded },
                    onSelectSection = { section ->
                        onSelectSection(section)
                        sectionMenuExpanded = false
                    }
                )
            }

            // 2. Post Cards / Instagram-style Grid directly under the selector
            if (selectedSection == DashboardSection.INVENTORY) {
                if (vehicleRows.isEmpty()) {
                    item(key = "account_empty_inventory") {
                        AccountEmptyShowcaseCard(
                            icon = Icons.Default.DirectionsCar,
                            title = "No vehicles posted yet",
                            subtitle = "Add a vehicle to showcase it on your website and account lot.",
                            buttonLabel = "Add Vehicle",
                            onActionClick = onOpenAddVehicle,
                            testTag = "account_empty_add_vehicle"
                        )
                    }
                } else {
                    items(
                        items = vehicleRows,
                        key = { row -> "veh_row_${row.joinToString("_") { it.id }}" }
                    ) { rowVehicles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowVehicles.forEach { vehicle ->
                                AccountInventoryGridCard(
                                    vehicle = vehicle,
                                    onCardClick = { onOpenVehicleDetail(vehicle) },
                                    onEditClick = { onOpenEditVehicle(vehicle) },
                                    onQuickPriceClick = { onOpenQuickPrice(vehicle) },
                                    onDeleteClick = { onDeleteVehicle(vehicle) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(vehicleCols - rowVehicles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                if (tikTokRows.isEmpty()) {
                    item(key = "account_empty_tiktok") {
                        AccountEmptyShowcaseCard(
                            icon = Icons.Default.VideoLibrary,
                            title = "No TikTok videos yet",
                            subtitle = "Publish a TikTok video link to feature it in an Instagram-style grid here and on your website.",
                            buttonLabel = "Add TikTok Link",
                            onActionClick = onOpenAddTikTok,
                            testTag = "account_empty_add_tiktok"
                        )
                    }
                } else {
                    items(
                        items = tikTokRows,
                        key = { row -> "tiktok_row_${row.joinToString("_") { it.id }}" }
                    ) { rowPosts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPosts.forEach { post ->
                                TikTokInstagramGridCard(
                                    post = post,
                                    onCardClick = { selectedTikTokDetail = post },
                                    onEditClick = { onOpenEditTikTok(post) },
                                    onDeleteClick = { onDeleteTikTok(post) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(tikTokCols - rowPosts.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Expanded TikTok Video Detail Modal when tapped
    selectedTikTokDetail?.let { post ->
        TikTokPostDetailDialog(
            post = post,
            handle = tikTokHandle,
            onDismiss = { selectedTikTokDetail = null },
            onEditClick = {
                selectedTikTokDetail = null
                onOpenEditTikTok(post)
            },
            onDeleteClick = {
                selectedTikTokDetail = null
                onDeleteTikTok(post)
            }
        )
    }
}
