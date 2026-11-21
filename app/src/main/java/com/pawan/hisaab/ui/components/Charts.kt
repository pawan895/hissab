package com.pawan.hisaab.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.ui.theme.FinTheme
import kotlin.math.max

/** Ratio against a limit: thin line on a hairline track. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = FinTheme.colors.hairline
    Canvas(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50))) {
        drawRect(track)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0) drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}

/** One ranked horizontal bar: square at the baseline, rounded data-end. */
@Composable
fun RankedBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val w = max(size.height, size.width * fraction.coerceIn(0f, 1f))
        val r = CornerRadius(size.height / 2)
        drawPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f, top = 0f, right = w, bottom = size.height,
                        topLeftCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero,
                        topRightCornerRadius = r, bottomRightCornerRadius = r,
                    ),
                )
            },
            color,
        )
    }
}

/**
 * Daily spending columns, single series in ink. Tap a column to select it; the
 * caller shows its value. Hit targets are the full column slot.
 */
@Composable
fun DailyBars(
    values: List<Long>,
    selected: Int?,
    today: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinTheme.colors
    val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .pointerInput(values.size, selected) {
                        detectTapGestures { pos ->
                            val slot = size.width / values.size.toFloat()
                            val i = (pos.x / slot).toInt().coerceIn(0, values.lastIndex)
                            onSelect(if (i == selected) null else i)
                        }
                    }
                    .semantics { contentDescription = "Daily spending, ${values.count { it > 0 }} days with spends" },
            ) {
                val slot = size.width / values.size
                val gap = 3.dp.toPx()
                val r = 2.dp.toPx()
                drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                values.forEachIndexed { i, v ->
                    val left = i * slot + gap / 2
                    val w = slot - gap
                    if (v <= 0) {
                        // Empty days get a faint stub so the month's rhythm stays visible.
                        if (today == null || i <= today) {
                            drawRect(c.hairline, topLeft = Offset(left, size.height - 2.dp.toPx()), size = Size(w, 2.dp.toPx()))
                        }
                        return@forEachIndexed
                    }
                    val h = max(3.dp.toPx(), size.height * v / maxV)
                    val color = if (selected != null && selected != i) c.ink3.copy(alpha = 0.5f) else c.ink
                    drawPath(
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = left, top = size.height - h, right = left + w, bottom = size.height,
                                    topLeftCornerRadius = CornerRadius(minOf(r, w / 2)), topRightCornerRadius = CornerRadius(minOf(r, w / 2)),
                                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                                ),
                            )
                        },
                        color,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = c.ink3)
            Box(Modifier.weight(1f))
            Text("${(values.size + 1) / 2}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
            Box(Modifier.weight(1f))
            Text("${values.size}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
        }
    }
}
