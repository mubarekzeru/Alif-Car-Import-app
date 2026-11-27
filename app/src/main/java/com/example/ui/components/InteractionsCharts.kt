package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrafficChartBucket
import com.example.data.model.VisitorRangeUnit
import com.example.data.model.VisitorTimeRange
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class InteractionsChartMode(val label: String) {
    LINE("Trend"),
    BAR("Bars")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitorTimeframeSelectorCard(
    selectedRange: VisitorTimeRange,
    onSelectRange: (VisitorTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPickerExpanded by remember { mutableStateOf(false) }
    var activeBrowseUnit by remember { mutableStateOf(selectedRange.unit) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isPickerExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "timeframe_chevron_rotation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPickerExpanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Primary trigger pill: shows currently active period (default: 1 Day Visitors).
            // Tapping opens/closes Days, Weeks & Years options while hiding the currently chosen option.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isPickerExpanded) {
                            activeBrowseUnit = selectedRange.unit
                        }
                        isPickerExpanded = !isPickerExpanded
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("interactions_timeframe_trigger"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (selectedRange.unit) {
                                    VisitorRangeUnit.DAYS -> Icons.Default.Today
                                    VisitorRangeUnit.WEEKS -> Icons.Default.DateRange
                                    VisitorRangeUnit.YEARS -> Icons.Default.CalendarMonth
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "${selectedRange.label} Visitors",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPickerExpanded) {
                                "Choose a different Days, Weeks, or Years range"
                            } else {
                                "Tap to switch Days, Weeks, or Years"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedRange.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isPickerExpanded) "Hide timeframe options" else "Show timeframe options",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isPickerExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category selector: Days | Weeks | Years
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VisitorRangeUnit.entries.forEach { unit ->
                            val isSelectedUnit = activeBrowseUnit == unit
                            Surface(
                                color = if (isSelectedUnit) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .clickable { activeBrowseUnit = unit }
                                    .testTag("timeframe_unit_${unit.name.lowercase(Locale.US)}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = unit.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelectedUnit) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Selectable options for the chosen category, strictly hiding the already-active range
                    val availableOptions = remember(activeBrowseUnit, selectedRange) {
                        VisitorTimeRange.optionsForUnitExcluding(
                            unit = activeBrowseUnit,
                            selected = selectedRange
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableOptions.forEach { option ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onSelectRange(option)
                                        isPickerExpanded = false
                                    }
                                    .testTag("timeframe_option_${option.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun TrafficTrendChartCard(
    buckets: List<TrafficChartBucket>,
    rangeLabel: String,
    modifier: Modifier = Modifier
) {
    val numberFormatter = remember { NumberFormat.getIntegerInstance(Locale.US) }
    var chartMode by remember { mutableStateOf(InteractionsChartMode.LINE) }
    val defaultIndex = remember(buckets) {
        if (buckets.isEmpty()) -1
        else buckets.indices.maxByOrNull { buckets[it].visits } ?: (buckets.size - 1)
    }
    var selectedBucketIndex by remember(buckets) { mutableIntStateOf(defaultIndex) }
    val safeIndex = selectedBucketIndex.coerceIn(-1, buckets.lastIndex)
    val highlightedBucket = buckets.getOrNull(safeIndex)
    val hasAnyTraffic = remember(buckets) { buckets.any { it.visits > 0L } }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = EmeraldAvailable
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactions_traffic_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Visitor Traffic Graph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$rangeLabel timeline • Tap graph to inspect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Segmented Line vs Bar toggle
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ChartModePill(
                            icon = Icons.Default.ShowChart,
                            contentDescription = "Trend line graph",
                            selected = chartMode == InteractionsChartMode.LINE,
                            onClick = { chartMode = InteractionsChartMode.LINE },
                            testTag = "chart_mode_line"
                        )
                        ChartModePill(
                            icon = Icons.Default.BarChart,
                            contentDescription = "Bar graph",
                            selected = chartMode == InteractionsChartMode.BAR,
                            onClick = { chartMode = InteractionsChartMode.BAR },
                            testTag = "chart_mode_bar"
                        )
                    }
                }
            }

            // Active point/bar inspection bar
            if (highlightedBucket != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = highlightedBucket.fullLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${numberFormatter.format(highlightedBucket.visits)} visits • ${numberFormatter.format(highlightedBucket.uniqueVisitors)} unique",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Interactive Canvas graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(buckets) {
                            detectTapGestures { offset ->
                                if (buckets.isEmpty() || size.width <= 0) return@detectTapGestures
                                val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val idx = if (buckets.size == 1) {
                                    0
                                } else {
                                    (ratio * (buckets.size - 1)).roundToInt()
                                        .coerceIn(0, buckets.lastIndex)
                                }
                                selectedBucketIndex = idx
                            }
                        }
                ) {
                    val topPad = 14.dp.toPx()
                    val bottomPad = 10.dp.toPx()
                    val horizontalPad = 10.dp.toPx()
                    val chartWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val baselineY = topPad + chartHeight

                    // Dashed horizontal reference lines
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (lineIdx in 0..3) {
                        val y = topPad + (chartHeight / 3f) * lineIdx
                        drawLine(
                            color = outlineColor.copy(alpha = 0.65f),
                            start = Offset(horizontalPad, y),
                            end = Offset(size.width - horizontalPad, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (lineIdx == 3) null else dashEffect
                        )
                    }

                    if (buckets.isEmpty()) return@Canvas
                    val maxVisits = buckets.maxOf { it.visits }.coerceAtLeast(1L).toFloat()

                    if (chartMode == InteractionsChartMode.BAR) {
                        val slotWidth = chartWidth / buckets.size.toFloat()
                        val barWidth = (slotWidth * 0.56f).coerceIn(8.dp.toPx(), 32.dp.toPx())
                        buckets.forEachIndexed { idx, bucket ->
                            val centerX = horizontalPad + slotWidth * idx + slotWidth / 2f
                            val fraction = (bucket.visits.toFloat() / maxVisits).coerceIn(0f, 1f)
                            val rawBarHeight = if (bucket.visits > 0L) {
                                (chartHeight * fraction).coerceAtLeast(6.dp.toPx())
                            } else {
                                3.dp.toPx()
                            }
                            val isActive = idx == safeIndex
                            val barColor = when {
                                bucket.visits == 0L -> outlineColor.copy(alpha = 0.5f)
                                isActive -> primaryColor
                                else -> primaryColor.copy(alpha = 0.42f)
                            }
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(centerX - barWidth / 2f, baselineY - rawBarHeight),
                                size = Size(barWidth, rawBarHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    } else {
                        val stepX = if (buckets.size <= 1) 0f else chartWidth / (buckets.size - 1).toFloat()
                        val points = buckets.mapIndexed { idx, bucket ->
                            val x = if (buckets.size == 1) {
                                horizontalPad + chartWidth / 2f
                            } else {
                                horizontalPad + stepX * idx
                            }
                            val fraction = if (hasAnyTraffic) {
                                (bucket.visits.toFloat() / maxVisits).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val y = baselineY - (chartHeight * fraction)
                            Offset(x, y)
                        }

                        if (points.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.lastIndex) {
                                    val p0 = points[i]
                                    val p1 = points[i + 1]
                                    val midX = (p0.x + p1.x) / 2f
                                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                                }
                            }
                            val areaPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, baselineY)
                                lineTo(points.first().x, baselineY)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.28f),
                                        primaryColor.copy(alpha = 0.02f)
                                    ),
                                    startY = topPad,
                                    endY = baselineY
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = primaryColor,
                                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Highlight selected vertical guideline & nodes
                        points.forEachIndexed { idx, pt ->
                            val isActive = idx == safeIndex
                            if (isActive) {
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.28f),
                                    start = Offset(pt.x, topPad),
                                    end = Offset(pt.x, baselineY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.2f),
                                    radius = 9.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = secondaryColor,
                                    radius = 3.5.dp.toPx(),
                                    center = pt
                                )
                            } else if (buckets[idx].visits > 0L) {
                                drawCircle(
                                    color = primaryColor,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                if (!hasAnyTraffic) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "No website visits recorded in this period yet — live traffic plots here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // X-axis bucket labels
            if (buckets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    buckets.forEachIndexed { idx, bucket ->
                        val isActive = idx == safeIndex
                        Text(
                            text = bucket.label,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartModePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
