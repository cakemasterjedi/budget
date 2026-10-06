package com.gigbudget.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gigbudget.app.data.Money

data class Slice(val label: String, val value: Long, val color: Color)

/**
 * Donut chart. Slices are separated by a thin gap in the surface color so neighbours never
 * blur together, and every slice is also named in the legend next to it.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    thickness: Dp = 26.dp,
) {
    val total = slices.sumOf { it.value.coerceAtLeast(0) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = if (total == 0L) "No data yet" else slices.filter { it.value > 0 }.joinToString {
        "${it.label} ${it.value * 100 / total}%"
    }
    Box(modifier.size(diameter).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            if (total > 0) {
                val radius = arcSize.width / 2
                val gapDegrees = (2.dp.toPx() / (2 * Math.PI.toFloat() * radius)) * 360f
                val visible = slices.filter { it.value > 0 }
                var start = -90f
                visible.forEach { slice ->
                    val sweep = slice.value.toFloat() / total * 360f
                    val drawn = if (visible.size > 1) (sweep - gapDegrees).coerceAtLeast(0.5f) else sweep
                    drawArc(slice.color, start, drawn, false, topLeft, arcSize, style = Stroke(strokePx))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(thickness + 4.dp)) {
            Text(centerTop, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(centerBottom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LegendDot(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** One legend line: colored dot, name, amount and share. Text stays in normal ink colors. */
@Composable
fun LegendRow(color: Color, label: String, cents: Long, percent: Long?, bold: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else null)
        Text(Money.format(cents), fontWeight = if (bold) FontWeight.SemiBold else null)
        if (percent != null) {
            Text(
                "$percent%",
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Rounded bar showing how much of a planned amount is used; turns error-red past 100%. */
@Composable
fun BudgetBar(used: Long, planned: Long, color: Color) {
    val over = planned > 0 && used > planned
    val fraction = when {
        planned <= 0 -> if (used > 0) 1f else 0f
        else -> (used.toFloat() / planned).coerceIn(0f, 1f)
    }
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(if (over) MaterialTheme.colorScheme.error else color)
        )
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
