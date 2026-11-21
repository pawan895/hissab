package com.pawan.hisaab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawan.hisaab.R

/**
 * Clean & minimal: white (or near-black) surface, black ink, one blue accent for
 * actions, green/red reserved for money. Separation comes from whitespace and
 * hairlines rather than tinted cards.
 */
@Immutable
data class FinColors(
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    /** Very light fill for pills and inputs. */
    val tint: Color,
    val accent: Color,
    val credit: Color,
    val over: Color,
    /**
     * Chart colours for categories, always used in this order and always next to a
     * text label. No greens or reds: those mean money in and over budget.
     */
    val series: List<Color>,
    /** "Everything else" slice. */
    val seriesOther: Color,
)

private val LightFin = FinColors(
    ink = Color(0xFF111111),
    ink2 = Color(0xFF6E6E73),
    ink3 = Color(0xFFA1A1A6),
    hairline = Color(0xFFEDEDED),
    tint = Color(0xFFF4F4F5),
    accent = Color(0xFF2563EB),
    credit = Color(0xFF15803D),
    over = Color(0xFFDC2626),
    series = listOf(Color(0xFF3B5BDB), Color(0xFFE67700), Color(0xFFAE3EC9), Color(0xFF0C8599), Color(0xFFD6336C)),
    seriesOther = Color(0xFFADB5BD),
)

private val DarkFin = FinColors(
    ink = Color(0xFFF5F5F7),
    ink2 = Color(0xFF9A9AA1),
    ink3 = Color(0xFF6B6B70),
    hairline = Color(0xFF232326),
    tint = Color(0xFF1C1C1E),
    accent = Color(0xFF6E9BF5),
    credit = Color(0xFF4ADE80),
    over = Color(0xFFF87171),
    series = listOf(Color(0xFF7B93FF), Color(0xFFFFA94D), Color(0xFFDA77F2), Color(0xFF3BC9DB), Color(0xFFF783AC)),
    seriesOther = Color(0xFF5C636A),
)

private fun scheme(c: FinColors, dark: Boolean, bg: Color) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.ink,
    onPrimary = bg,
    primaryContainer = c.tint,
    onPrimaryContainer = c.ink,
    secondary = c.ink2,
    secondaryContainer = c.tint,
    onSecondaryContainer = c.ink,
    tertiary = c.accent,
    background = bg,
    onBackground = c.ink,
    surface = bg,
    onSurface = c.ink,
    surfaceVariant = c.tint,
    onSurfaceVariant = c.ink2,
    surfaceContainerLowest = bg,
    surfaceContainerLow = bg,
    surfaceContainer = bg,
    surfaceContainerHigh = bg,
    surfaceContainerHighest = c.tint,
    surfaceTint = Color.Transparent,
    outline = c.ink3,
    outlineVariant = c.hairline,
    error = c.over,
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val AppTypography = Typography(
    displayLarge = style(44, FontWeight.Medium, 52, -1.2),
    displayMedium = style(36, FontWeight.Medium, 44, -0.8),
    displaySmall = style(30, FontWeight.Medium, 38, -0.6),
    headlineMedium = style(26, FontWeight.SemiBold, 32, -0.5),
    headlineSmall = style(22, FontWeight.SemiBold, 28, -0.3),
    titleLarge = style(19, FontWeight.SemiBold, 26, -0.2),
    titleMedium = style(16, FontWeight.Medium, 22),
    titleSmall = style(14, FontWeight.Medium, 20),
    bodyLarge = style(16, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16, 0.4),
    labelSmall = style(11, FontWeight.Medium, 14, 0.4),
)

/** Tabular figures so amounts line up in lists. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalFinColors = staticCompositionLocalOf { LightFin }

@Composable
fun HisaabTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkFin else LightFin
    val bg = if (dark) Color(0xFF0B0B0C) else Color.White
    CompositionLocalProvider(LocalFinColors provides c) {
        MaterialTheme(
            colorScheme = scheme(c, dark, bg),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object FinTheme {
    val colors: FinColors @Composable get() = LocalFinColors.current
}
