package com.pawan.hisaab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.util.Money
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** Ratio against a limit: thin line on a hairline track. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val track = FinTheme.colors.hairline
    Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50))) {
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
        drawPath(barPath(0f, 0f, w, size.height, size.height / 2, horizontal = true), color)
    }
}

/** Rounded at the data end only, square on the baseline. */
private fun barPath(left: Float, top: Float, right: Float, bottom: Float, radius: Float, horizontal: Boolean = false) = Path().apply {
    val r = CornerRadius(radius)
    addRoundRect(
        if (horizontal) {
            RoundRect(left, top, right, bottom, topLeftCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = r, bottomRightCornerRadius = r)
        } else {
            RoundRect(left, top, right, bottom, topLeftCornerRadius = r, topRightCornerRadius = r, bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero)
        },
    )
}

private fun DrawScope.dashedLine(color: Color, y: Float, width: Float = 1.dp.toPx()) = drawLine(
    color, Offset(0f, y), Offset(size.width, y), strokeWidth = width,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
)

data class DonutSlice(val value: Long, val color: Color)

/**
 * Part-to-whole ring. Slices are separated by a hairline gap of the surface; tapping
 * a slice selects it (tap again to clear) and the caller shows its details in [center].
 */
@Composable
fun Donut(
    slices: List<DonutSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    thickness: Dp = 20.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = FinTheme.colors
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(650)) }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(diameter)
                .pointerInput(slices, selected) {
                    detectTapGestures { pos ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dist = hypot(pos.x - cx, pos.y - cy)
                        val outer = size.width / 2f
                        val inner = outer - thickness.toPx() * 2.2f
                        if (dist < inner || dist > outer + 8.dp.toPx()) { onSelect(null); return@detectTapGestures }
                        // 0° at 12 o'clock, clockwise.
                        var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat() + 90f
                        if (deg < 0) deg += 360f
                        var start = 0f
                        val hit = slices.indexOfFirst { s ->
                            val end = start + 360f * s.value / total
                            (deg in start..end).also { start = end }
                        }
                        onSelect(if (hit < 0 || hit == selected) null else hit)
                    }
                }
                .semantics { contentDescription = "Spending by category, ${slices.size} parts" },
        ) {
            val stroke = thickness.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val radius = arcSize.width / 2
            if (slices.isEmpty() || slices.all { it.value <= 0 }) {
                drawArc(c.hairline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            // A 2dp gap between slices, expressed as an angle at this radius.
            val gap = if (slices.count { it.value > 0 } > 1) Math.toDegrees((2.dp.toPx() / radius).toDouble()).toFloat() else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val full = 360f * s.value / total * sweep.value
                val draw = (full - gap).coerceAtLeast(0.5f)
                if (s.value > 0) {
                    val on = selected == null || selected == i
                    val w = if (selected == i) stroke + 6.dp.toPx() else stroke
                    drawArc(
                        if (on) s.color else s.color.copy(alpha = 0.25f),
                        start + gap / 2, draw, false, Offset(inset, inset), arcSize, style = Stroke(w),
                    )
                }
                start += full
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(contentAlignment = Alignment.Center, content = center) }
    }
}

/**
 * Daily spending columns, single series in ink, with the daily average as a dashed
 * line. Tap a column to select it; the caller shows its value.
 */
@Composable
fun DailyBars(
    values: List<Long>,
    selected: Int?,
    today: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    average: Long? = null,
) {
    val c = FinTheme.colors
    val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(average ?: 0L).takeIf { it > 0 } ?: 1L
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
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
            val gap = (slot * 0.28f).coerceIn(2.dp.toPx(), 4.dp.toPx())
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
                val color = when {
                    selected == i -> c.ink
                    selected != null -> c.ink3.copy(alpha = 0.45f)
                    today == i -> c.ink
                    else -> c.ink.copy(alpha = 0.78f)
                }
                drawPath(barPath(left, size.height - h, left + w, size.height, minOf(r, w / 2)), color)
            }
            if (average != null && average > 0) dashedLine(c.ink2, size.height - size.height * average / maxV)
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

/**
 * Running total for the month (solid ink) against the month before (dashed grey) and
 * an optional budget line. Drag or tap to read any day; [onSelect] gets the day index.
 */
@Composable
fun PaceChart(
    current: List<Long>,
    previous: List<Long>,
    days: Int,
    budget: Long?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.ink3)
    val maxV = listOfNotNull(current.maxOrNull(), previous.maxOrNull(), budget).maxOrNull()?.takeIf { it > 0 } ?: 1L
    val top = (maxV * 1.12).toLong()
    val surface = MaterialTheme.colorScheme.background

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(156.dp)
                .pointerInput(days, current.size) {
                    fun dayAt(x: Float) = ((x / size.width) * (days - 1)).let { kotlin.math.round(it).toInt() }.coerceIn(0, days - 1)
                    detectTapGestures { onSelect(dayAt(it.x)) }
                }
                .pointerInput(days, current.size) {
                    fun dayAt(x: Float) = ((x / size.width) * (days - 1)).let { kotlin.math.round(it).toInt() }.coerceIn(0, days - 1)
                    detectHorizontalDragGestures(onDragEnd = {}) { change, _ -> onSelect(dayAt(change.position.x)) }
                }
                .semantics { contentDescription = "Running total of spending this month compared with last month" },
        ) {
            val h = size.height
            fun x(i: Int) = if (days <= 1) 0f else size.width * i / (days - 1)
            fun y(v: Long) = h - h * v / top

            // Recessive grid: baseline and a halfway line, labelled on the left.
            drawLine(c.hairline, Offset(0f, h), Offset(size.width, h), strokeWidth = 1.dp.toPx())
            drawLine(c.hairline, Offset(0f, y(top / 2)), Offset(size.width, y(top / 2)), strokeWidth = 1.dp.toPx())
            drawText(measurer, Money.compact(top / 2), Offset(0f, y(top / 2) - 16.dp.toPx()), labelStyle)

            if (budget != null && budget > 0) {
                dashedLine(c.over.copy(alpha = 0.7f), y(budget))
                val t = measurer.measure("Budget ${Money.compact(budget)}", labelStyle.copy(color = c.over))
                drawText(t, topLeft = Offset(size.width - t.size.width, y(budget) - t.size.height - 2.dp.toPx()))
            }

            fun line(values: List<Long>): Path = Path().apply {
                values.take(days).forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            if (previous.size > 1) {
                drawPath(
                    line(previous), c.ink3,
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            if (current.isNotEmpty()) {
                val path = line(current)
                val fill = Path().apply {
                    addPath(path)
                    lineTo(x(current.lastIndex.coerceAtMost(days - 1)), h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(fill, c.ink.copy(alpha = 0.06f))
                drawPath(path, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = current.lastIndex.coerceAtMost(days - 1)
                if (selected == null) {
                    drawCircle(surface, 6.dp.toPx(), Offset(x(last), y(current[last])))
                    drawCircle(c.ink, 4.dp.toPx(), Offset(x(last), y(current[last])))
                }
            }

            selected?.let { i ->
                drawLine(c.ink3, Offset(x(i), 0f), Offset(x(i), h), strokeWidth = 1.dp.toPx())
                previous.getOrNull(i)?.let { v ->
                    drawCircle(surface, 6.dp.toPx(), Offset(x(i), y(v)))
                    drawCircle(c.ink3, 4.dp.toPx(), Offset(x(i), y(v)))
                }
                current.getOrNull(i)?.let { v ->
                    drawCircle(surface, 6.dp.toPx(), Offset(x(i), y(v)))
                    drawCircle(c.ink, 4.dp.toPx(), Offset(x(i), y(v)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = c.ink3)
            Box(Modifier.weight(1f))
            Text("${(days + 1) / 2}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
            Box(Modifier.weight(1f))
            Text("$days", style = MaterialTheme.typography.labelSmall, color = c.ink3)
        }
    }
}

/** Legend swatch: a short solid or dashed line. */
@Composable
fun LineKey(color: Color, dashed: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 18.dp, height = 10.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier.size(size)) { drawCircle(color) }
}

/**
 * A few months side by side. The [highlighted] month is solid ink, the rest grey,
 * each with its value on top (so no average line to collide with). Tap a bar to pick that month.
 */
@Composable
fun MonthBars(
    values: List<Long>,
    labels: List<String>,
    highlighted: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FinTheme.colors
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall
    val maxV = (values.maxOrNull() ?: 0L).takeIf { it > 0 } ?: 1L
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(values.size) {
                    detectTapGestures { pos ->
                        val slot = size.width / values.size.toFloat()
                        onSelect((pos.x / slot).toInt().coerceIn(0, values.lastIndex))
                    }
                }
                .semantics { contentDescription = "Spending in each of the last ${values.size} months" },
        ) {
            val labelSpace = 18.dp.toPx()
            val h = size.height - labelSpace
            val slot = size.width / values.size
            val w = (slot * 0.46f).coerceAtMost(34.dp.toPx())
            drawLine(c.hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val left = i * slot + (slot - w) / 2
                val bh = if (v <= 0) 0f else max(3.dp.toPx(), h * v / maxV)
                val on = i == highlighted
                if (bh > 0) drawPath(barPath(left, size.height - bh, left + w, size.height, 4.dp.toPx()), if (on) c.ink else c.ink3.copy(alpha = 0.45f))
                val t = measurer.measure(if (v > 0) Money.compact(v) else "–", valueStyle.copy(color = if (on) c.ink else c.ink3))
                drawText(t, topLeft = Offset(i * slot + (slot - t.size.width) / 2, size.height - bh - t.size.height - 3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlighted) c.ink else c.ink3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
