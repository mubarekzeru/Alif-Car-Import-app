package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TikTokPost
import com.example.data.model.Vehicle
import com.example.data.network.TikTokThumbnailResolver
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WarmAmberGlow
import com.example.ui.theme.WarmCoralTop
import com.example.ui.viewmodel.DashboardSection

@Composable
fun AccountSectionSwitcherCard(
    selectedSection: DashboardSection,
    vehicleCount: Int,
    tikTokCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectSection: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "account_section_arrow_rotation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpand)
                    .testTag("account_section_switcher_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedSection,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "account_switcher_pill_label"
                    ) { section ->
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Inventory and TikTok Videos selector",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            // Single dynamic icon + posted count pill matching the selected Inventory / TikTok Videos option
            val activeIcon = if (selectedSection == DashboardSection.INVENTORY) {
                Icons.Default.DirectionsCar
            } else {
                Icons.Default.VideoLibrary
            }
            val activeCount = if (selectedSection == DashboardSection.INVENTORY) {
                vehicleCount
            } else {
                tikTokCount
            }
            val activeDescription = if (selectedSection == DashboardSection.INVENTORY) {
                "$vehicleCount vehicles posted"
            } else {
                "$tikTokCount TikTok videos posted"
            }

            Surface(
                onClick = onToggleExpand,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("account_active_section_count_pill")
            ) {
                AnimatedContent(
                    targetState = Triple(activeIcon, activeCount, activeDescription),
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "account_single_section_count_badge"
                ) { (icon, count, description) ->
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
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
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AccountSwitchOptionRow(
                        icon = Icons.Default.DirectionsCar,
                        label = "Inventory",
                        count = vehicleCount,
                        isSelected = selectedSection == DashboardSection.INVENTORY,
                        onClick = { onSelectSection(DashboardSection.INVENTORY) },
                        testTag = "switch_to_inventory_option"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AccountSwitchOptionRow(
                        icon = Icons.Default.VideoLibrary,
                        label = "TikTok Videos",
                        count = tikTokCount,
                        isSelected = selectedSection == DashboardSection.TIKTOK_VIDEOS,
                        onClick = { onSelectSection(DashboardSection.TIKTOK_VIDEOS) },
                        testTag = "switch_to_tiktok_option"
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSwitchOptionRow(
    icon: ImageVector,
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
            tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AccountInventoryGridCard(
    vehicle: Vehicle,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onQuickPriceClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusColors = vehicle.statusEnum.colors()

    val photoCandidates = remember(vehicle.id, vehicle.imageUrl, vehicle.galleryImages) {
        vehicle.allResolvedPhotoUrls()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("account_vehicle_card_${vehicle.id}"),
        onClick = onCardClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                ResilientAppImage(
                    candidateUrls = photoCandidates,
                    contentDescription = vehicle.displayTitle,
                    cacheKeyPrefix = "acc_veh_${vehicle.id}",
                    width = 480,
                    height = 360,
                    crossfadeMillis = 160,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = vehicle.displayTitle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColors.foreground)
                        )
                        Text(
                            text = vehicle.statusEnum.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColors.foreground
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = CircleShape,
                        shadowElevation = 2.dp
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("account_vehicle_menu_${vehicle.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Vehicle options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quick Price Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onQuickPriceClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }

                if (vehicle.featured) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Featured",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(11.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = vehicle.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val formattedPrice = formatOptionalCurrency(vehicle.price)
                if (formattedPrice != null) {
                    Text(
                        text = formattedPrice,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun TikTokCoverImage(
    postId: String,
    tiktokUrl: String,
    storedCoverUrl: String,
    contentDescription: String,
    cacheKeyPrefix: String,
    modifier: Modifier = Modifier,
    width: Int? = 420,
    height: Int? = 580,
    fallback: @Composable () -> Unit
) {
    var resolvedCoverUrl by remember(postId, tiktokUrl) {
        mutableStateOf(TikTokThumbnailResolver.getCachedThumbnail(tiktokUrl).orEmpty())
    }
    var refreshAttempted by remember(postId, tiktokUrl) { mutableStateOf(false) }
    var forceRefreshTrigger by remember(postId, tiktokUrl) { mutableIntStateOf(0) }

    val storedCandidates = remember(postId, storedCoverUrl) {
        resolveCandidateImageUrls(storedCoverUrl)
    }

    LaunchedEffect(tiktokUrl, storedCandidates.isEmpty(), forceRefreshTrigger) {
        if (tiktokUrl.isBlank()) return@LaunchedEffect
        val shouldFetch = storedCandidates.isEmpty() || forceRefreshTrigger > 0
        if (!shouldFetch) return@LaunchedEffect
        val metadata = TikTokThumbnailResolver.resolveMetadata(
            rawUrl = tiktokUrl,
            forceRefresh = forceRefreshTrigger > 0
        )
        if (metadata != null && metadata.thumbnailUrl.isNotBlank()) {
            resolvedCoverUrl = metadata.thumbnailUrl
        }
    }

    val effectiveCandidates = remember(storedCandidates, resolvedCoverUrl, forceRefreshTrigger) {
        buildList {
            if (forceRefreshTrigger > 0 && resolvedCoverUrl.isNotBlank()) {
                add(resolvedCoverUrl)
            }
            addAll(storedCandidates)
            if (resolvedCoverUrl.isNotBlank() && !contains(resolvedCoverUrl)) {
                add(resolvedCoverUrl)
            }
        }
    }

    ResilientAppImage(
        candidateUrls = effectiveCandidates,
        contentDescription = contentDescription,
        cacheKeyPrefix = "${cacheKeyPrefix}_${postId}_r$forceRefreshTrigger",
        width = width,
        height = height,
        crossfadeMillis = 160,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        onAllCandidatesFailed = {
            if (!refreshAttempted && tiktokUrl.isNotBlank()) {
                refreshAttempted = true
                forceRefreshTrigger += 1
            }
        },
        fallback = fallback
    )
}

@Composable
fun HomeTikTokShowcaseSection(
    posts: List<TikTokPost>,
    onCardClick: (TikTokPost) -> Unit,
    onEditClick: (TikTokPost) -> Unit,
    onDeleteClick: (TikTokPost) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    visitorPreviewMode: Boolean = false
) {
    if (posts.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_tiktok_showcase_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "TikTok Videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        text = posts.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("home_tiktok_see_all_button")
            ) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "See all TikTok videos",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = posts,
                key = { "home_tt_${it.id}" }
            ) { post ->
                TikTokInstagramGridCard(
                    post = post,
                    onCardClick = { onCardClick(post) },
                    onEditClick = { onEditClick(post) },
                    onDeleteClick = { onDeleteClick(post) },
                    modifier = Modifier.width(128.dp),
                    visitorPreviewMode = visitorPreviewMode
                )
            }
        }
    }
}

@Composable
fun TikTokInstagramGridCard(
    post: TikTokPost,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    visitorPreviewMode: Boolean = false
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .testTag("tiktok_grid_card_${post.id}"),
        onClick = onCardClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TikTokCoverImage(
                postId = post.id,
                tiktokUrl = post.tiktokUrl,
                storedCoverUrl = post.coverImageUrl,
                contentDescription = post.title.ifBlank { "TikTok video" },
                cacheKeyPrefix = "tiktok_grid",
                width = 420,
                height = 580,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1A1B1E),
                                    Color(0xFF2D2523),
                                    WarmCoralTop.copy(alpha = 0.78f)
                                )
                            )
                        )
                )
            }

            if (!visitorPreviewMode) {
                // Top-left order pill
                Surface(
                    color = Color.Black.copy(alpha = 0.52f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "#${post.sortOrder}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Top-right three-dots menu icon
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.92f),
                        shape = CircleShape,
                        shadowElevation = 2.dp
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("tiktok_menu_${post.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "TikTok video options",
                                tint = Color(0xFF16171A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            },
                            modifier = Modifier.testTag("tiktok_edit_option_${post.id}")
                        )
                        if (post.tiktokUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Open TikTok Link") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    TikTokAppLauncher.openInTikTokApp(context, post.tiktokUrl)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            // Bottom TikTok-style gradient scrim with play indicator and title
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .padding(horizontal = 7.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = post.title.ifBlank { "TikTok Video" },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AccountEmptyShowcaseCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    onActionClick: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onActionClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(44.dp)
                    .testTag(testTag)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TikTokPostDetailDialog(
    post: TikTokPost,
    handle: String,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    visitorPreviewMode: Boolean = false
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Expanded Preview Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    TikTokCoverImage(
                        postId = post.id,
                        tiktokUrl = post.tiktokUrl,
                        storedCoverUrl = post.coverImageUrl,
                        contentDescription = post.title.ifBlank { "TikTok video" },
                        cacheKeyPrefix = "tiktok_detail",
                        width = 720,
                        height = 960,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1A1B1E),
                                            Color(0xFF332623),
                                            WarmAmberGlow
                                        )
                                    )
                                )
                        )
                    }

                    if (!visitorPreviewMode) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Order #${post.sortOrder}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.92f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close detail modal",
                                tint = Color(0xFF16171A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp)
                            .clickable(enabled = post.tiktokUrl.isNotBlank()) {
                                TikTokAppLauncher.openInTikTokApp(
                                    context = context,
                                    videoUrl = post.tiktokUrl,
                                    fallbackHandle = handle
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play on TikTok",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (handle.isNotBlank()) {
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = post.title.ifBlank { "TikTok Walkaround #${post.sortOrder}" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (post.caption.isNotBlank()) {
                        Text(
                            text = post.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (post.tiktokUrl.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = post.tiktokUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (visitorPreviewMode) {
                        if (post.tiktokUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    TikTokAppLauncher.openInTikTokApp(
                                        context = context,
                                        videoUrl = post.tiktokUrl,
                                        fallbackHandle = handle
                                    )
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch on TikTok", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onDeleteClick,
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete TikTok video",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (post.tiktokUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        TikTokAppLauncher.openInTikTokApp(
                                            context = context,
                                            videoUrl = post.tiktokUrl,
                                            fallbackHandle = handle
                                        )
                                    },
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = onEditClick,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("tiktok_detail_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
