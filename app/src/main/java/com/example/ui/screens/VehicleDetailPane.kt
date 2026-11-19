package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Vehicle
import com.example.data.model.VehicleStatus
import com.example.ui.components.colors
import com.example.ui.components.decodeImageModel
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatMileageDisplay
import com.example.ui.components.formatOptionalCurrency
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun VehicleDetailScreen(
    vehicle: Vehicle?,
    onBack: () -> Unit,
    onQuickPriceClick: (Vehicle) -> Unit,
    onStatusChange: (Vehicle, VehicleStatus) -> Unit,
    onToggleFeatured: (Vehicle) -> Unit,
    onEditClick: (Vehicle) -> Unit,
    onDeleteClick: (Vehicle) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 2.dp
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to inventory",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Vehicle Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (vehicle != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 2.dp
                    ) {
                        IconButton(
                            onClick = { onToggleFeatured(vehicle) },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("detail_featured_toggle")
                        ) {
                            Icon(
                                imageVector = if (vehicle.featured) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (vehicle.featured) "Remove featured" else "Mark featured",
                                tint = if (vehicle.featured) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
        }
    ) { innerPadding ->
        if (vehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Vehicle not found or removed from lot.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            VehicleDetailPane(
                vehicle = vehicle,
                onQuickPriceClick = { onQuickPriceClick(vehicle) },
                onStatusChange = { status -> onStatusChange(vehicle, status) },
                onToggleFeatured = { onToggleFeatured(vehicle) },
                onEditClick = { onEditClick(vehicle) },
                onDeleteClick = { onDeleteClick(vehicle) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
fun VehicleDetailPane(
    vehicle: Vehicle,
    onQuickPriceClick: () -> Unit,
    onStatusChange: (VehicleStatus) -> Unit,
    onToggleFeatured: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = vehicle.statusEnum.colors()
    val allPhotos = remember(vehicle.id, vehicle.imageUrl, vehicle.galleryImages) {
        buildList {
            if (vehicle.imageUrl.isNotBlank()) add(vehicle.imageUrl)
            addAll(vehicle.galleryImages.filter { it.isNotBlank() && it != vehicle.imageUrl })
        }
    }
    var selectedPhotoIndex by remember(vehicle.id, allPhotos.size) { mutableIntStateOf(0) }
    val activePhotoUrl = allPhotos.getOrNull(selectedPhotoIndex) ?: vehicle.imageUrl
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Showcase Hero Card matching Screen 3 ("Car Details")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AnimatedContent(
                            targetState = activePhotoUrl,
                            transitionSpec = {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                            },
                            label = "detail_hero_photo"
                        ) { photoUrl ->
                            val decodedModel = remember(photoUrl) { decodeImageModel(photoUrl) }
                            if (decodedModel != null) {
                                val detailImageRequest = remember(vehicle.id, photoUrl, decodedModel) {
                                    ImageRequest.Builder(context)
                                        .data(decodedModel)
                                        .memoryCacheKey("veh_detail_${vehicle.id}_${photoUrl.hashCode()}")
                                        .crossfade(180)
                                        .build()
                                }
                                AsyncImage(
                                    model = detailImageRequest,
                                    contentDescription = vehicle.displayTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = vehicle.displayTitle,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = CircleShape,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColors.foreground)
                                )
                                Text(
                                    text = vehicle.statusEnum.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColors.foreground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (vehicle.featured) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Featured",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Featured",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (allPhotos.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(allPhotos) { index, photo ->
                                val isSelected = index == selectedPhotoIndex
                                val thumbBorder by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    animationSpec = tween(180),
                                    label = "thumb_border"
                                )
                                val thumbRequest = remember(vehicle.id, photo) {
                                    ImageRequest.Builder(context)
                                        .data(decodeImageModel(photo))
                                        .memoryCacheKey("veh_thumb_${vehicle.id}_${photo.hashCode()}")
                                        .size(228, 168)
                                        .crossfade(150)
                                        .build()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 52.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = thumbBorder,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .clickable { selectedPhotoIndex = index }
                                ) {
                                    AsyncImage(
                                        model = thumbRequest,
                                        contentDescription = "Vehicle photo ${index + 1}",
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    // Title + Price side-by-side row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = vehicle.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subtitleParts = listOfNotNull(
                                vehicle.year.takeIf { it > 0 }?.toString(),
                                vehicle.make.takeIf { it.isNotBlank() },
                                vehicle.model.takeIf { it.isNotBlank() },
                                vehicle.origin.takeIf { it.isNotBlank() }
                            )
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val formattedPrice = formatOptionalCurrency(vehicle.price)
                        if (formattedPrice != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formattedPrice,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val oldPrice = vehicle.originalPrice
                                if (oldPrice != null && oldPrice > vehicle.price) {
                                    Text(
                                        text = formatCurrency(oldPrice),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Availability Status & Featured Control
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Availability Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleStatus.entries.forEach { option ->
                        val isSelected = vehicle.statusEnum == option
                        val optionColors = option.colors()
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStatusChange(option) },
                            shape = CircleShape,
                            label = { Text(option.label, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = optionColors.background,
                                selectedLabelColor = optionColors.foreground,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) optionColors.foreground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("detail_status_${option.value}")
                        )
                    }
                }

                FilterChip(
                    selected = vehicle.featured,
                    onClick = onToggleFeatured,
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(
                            imageVector = if (vehicle.featured) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (vehicle.featured) "Featured on Website Showcase" else "Pin as Featured on Website",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("detail_featured_chip")
                )
            }

            // Key Specs 2-column grid matching Screen 3 ("Key Specs")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Key Specs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeySpecTile(
                        icon = Icons.Default.Speed,
                        label = "Mileage",
                        value = formatMileageDisplay(vehicle.mileage),
                        mono = true,
                        modifier = Modifier.weight(1f)
                    )
                    KeySpecTile(
                        icon = Icons.Default.Settings,
                        label = "Transmission",
                        value = vehicle.transmission.ifBlank { "Not specified" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeySpecTile(
                        icon = Icons.Default.LocalGasStation,
                        label = "Fuel Type",
                        value = vehicle.fuelType.ifBlank { "Not specified" },
                        modifier = Modifier.weight(1f)
                    )
                    KeySpecTile(
                        icon = Icons.Default.Palette,
                        label = "Exterior Color",
                        value = vehicle.color.ifBlank { "Not specified" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeySpecTile(
                        icon = Icons.Default.Public,
                        label = "Import Origin",
                        value = vehicle.origin.ifBlank { "Not specified" },
                        modifier = Modifier.weight(1f)
                    )
                    KeySpecTile(
                        icon = Icons.Default.DirectionsCar,
                        label = "Photos",
                        value = "${(vehicle.galleryImages.size + if (vehicle.imageUrl.isNotBlank()) 1 else 0)} photo(s)",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KeySpecTile(
                        icon = Icons.Default.Tag,
                        label = "Listing ID",
                        value = vehicle.id.ifBlank { "Pending" },
                        mono = true,
                        modifier = Modifier.weight(1f)
                    )
                    KeySpecTile(
                        icon = Icons.Default.Schedule,
                        label = "Last Updated",
                        value = formatTimestamp(vehicle.updatedAt, vehicle.createdAt),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (vehicle.description.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Listing Description",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = vehicle.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (vehicle.tiktokUrl.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TikTok Walkaround",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = vehicle.tiktokUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Pinned Luxury Bottom Action Bar matching Screen 3
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDeleteClick,
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("detail_delete_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove vehicle",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = onQuickPriceClick,
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("detail_quick_price_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sell,
                            contentDescription = "Adjust price",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
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
                        .height(50.dp)
                        .testTag("detail_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Details", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KeySpecTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    fontFamily = if (mono) JetBrainsMonoFontFamily else MaterialTheme.typography.titleSmall.fontFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
