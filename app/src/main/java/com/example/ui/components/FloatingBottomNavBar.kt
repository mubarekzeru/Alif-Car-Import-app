package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class BottomNavTab {
    HOME,
    INQUIRIES,
    SETTINGS
}

@Composable
fun FloatingBottomNavBar(
    selectedTab: BottomNavTab,
    isAddExpanded: Boolean,
    inquiriesBadgeCount: Int,
    onSelectTab: (BottomNavTab) -> Unit,
    onToggleAddMenu: () -> Unit,
    onAddVehicleClick: () -> Unit,
    onAddTikTokClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated "+ Add" action chooser floating above the dock
        AnimatedVisibility(
            visible = isAddExpanded,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(180)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + scaleOut(targetScale = 0.92f, animationSpec = tween(140)) + fadeOut(tween(140))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onAddVehicleClick,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_vehicle_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Add Vehicle",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add Vehicle",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    onClick = onAddTikTokClick,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("add_tiktok_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Add TikTok Video Link",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add TikTok Link",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Icon-only floating capsule bar
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 14.dp,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconOnlyNavItem(
                    selected = selectedTab == BottomNavTab.HOME && !isAddExpanded,
                    selectedIcon = Icons.Rounded.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    contentDescription = "Home",
                    testTag = "bottom_nav_home",
                    onClick = { onSelectTab(BottomNavTab.HOME) }
                )

                val addRotation by animateFloatAsState(
                    targetValue = if (isAddExpanded) 45f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "add_icon_rotation"
                )
                IconOnlyNavItem(
                    selected = isAddExpanded,
                    selectedIcon = Icons.Default.Add,
                    unselectedIcon = Icons.Default.Add,
                    contentDescription = if (isAddExpanded) "Close Add Menu" else "Add",
                    iconRotation = addRotation,
                    testTag = "add_menu_fab",
                    onClick = onToggleAddMenu
                )

                IconOnlyNavItem(
                    selected = selectedTab == BottomNavTab.INQUIRIES && !isAddExpanded,
                    selectedIcon = Icons.Rounded.ChatBubble,
                    unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Customer Inquiries",
                    badgeCount = inquiriesBadgeCount,
                    testTag = "bottom_nav_inquiries",
                    onClick = { onSelectTab(BottomNavTab.INQUIRIES) }
                )

                IconOnlyNavItem(
                    selected = selectedTab == BottomNavTab.SETTINGS && !isAddExpanded,
                    selectedIcon = Icons.Rounded.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    contentDescription = "Settings",
                    testTag = "bottom_nav_settings",
                    onClick = { onSelectTab(BottomNavTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun IconOnlyNavItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    contentDescription: String,
    testTag: String,
    badgeCount: Int = 0,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_pill_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "nav_icon_tint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_scale"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .clip(CircleShape)
            .background(pillColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString()
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
                    .rotate(iconRotation)
            )
        }
    }
}
