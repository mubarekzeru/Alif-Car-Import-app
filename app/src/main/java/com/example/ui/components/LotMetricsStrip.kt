package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberReserved
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SlateSold
import com.example.ui.viewmodel.LotMetrics

@Composable
fun LotMetricsStrip(
    metrics: LotMetrics,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MetricCard(
                label = "ACTIVE LOT VALUE",
                value = formatCompactCurrency(metrics.activeInventoryValue),
                sublabel = "${metrics.availableCount + metrics.reservedCount} active units",
                icon = Icons.Default.AttachMoney,
                accentColor = MaterialTheme.colorScheme.primary,
                isFeatured = true
            )
        }
        item {
            MetricCard(
                label = "AVAILABLE",
                value = metrics.availableCount.toString(),
                sublabel = "of ${metrics.totalCount} total",
                icon = Icons.Default.CheckCircle,
                accentColor = EmeraldAvailable
            )
        }
        item {
            MetricCard(
                label = "RESERVED",
                value = metrics.reservedCount.toString(),
                sublabel = "pending deals",
                icon = Icons.Default.Schedule,
                accentColor = AmberReserved
            )
        }
        item {
            MetricCard(
                label = "SOLD UNITS",
                value = metrics.soldCount.toString(),
                sublabel = formatCompactCurrency(metrics.totalLotValue - metrics.activeInventoryValue),
                icon = Icons.Default.DirectionsCar,
                accentColor = SlateSold
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    sublabel: String,
    icon: ImageVector,
    accentColor: Color,
    isFeatured: Boolean = false
) {
    val borderColor = if (isFeatured) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val containerColor = if (isFeatured) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.widthIn(min = 148.dp, max = 196.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = if (isFeatured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
