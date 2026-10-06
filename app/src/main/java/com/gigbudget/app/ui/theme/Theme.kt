package com.gigbudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gigbudget.app.data.Bucket

private val LightColors = lightColorScheme(
    primary = Color(0xFF8E24AA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D6FF),
    onPrimaryContainer = Color(0xFF34004A),
    secondary = Color(0xFFD81B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD3E5),
    onSecondaryContainer = Color(0xFF4A0021),
    tertiary = Color(0xFFAB47BC),
    tertiaryContainer = Color(0xFFF6DBFF),
    onTertiaryContainer = Color(0xFF3B0050),
    background = Color(0xFFFBEFFF),
    onBackground = Color(0xFF241628),
    surface = Color(0xFFFBEFFF),
    onSurface = Color(0xFF241628),
    surfaceVariant = Color(0xFFF2DDF4),
    onSurfaceVariant = Color(0xFF5B4560),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF3FA),
    surfaceContainer = Color(0xFFF7E1F5),
    surfaceContainerHigh = Color(0xFFF3D9F1),
    surfaceContainerHighest = Color(0xFFEED1EC),
    outline = Color(0xFF8E7491),
    outlineVariant = Color(0xFFE2C6E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3A6FF),
    onPrimary = Color(0xFF4C0068),
    primaryContainer = Color(0xFF6E1B8C),
    onPrimaryContainer = Color(0xFFF7DCFF),
    secondary = Color(0xFFFF9EC3),
    onSecondary = Color(0xFF65002E),
    secondaryContainer = Color(0xFF8E1A4B),
    onSecondaryContainer = Color(0xFFFFD9E6),
    tertiary = Color(0xFFD9B2FF),
    tertiaryContainer = Color(0xFF5C2D8A),
    onTertiaryContainer = Color(0xFFF1DBFF),
    background = Color(0xFF1C1022),
    onBackground = Color(0xFFF1DEF2),
    surface = Color(0xFF1C1022),
    onSurface = Color(0xFFF1DEF2),
    surfaceVariant = Color(0xFF4D3A52),
    onSurfaceVariant = Color(0xFFDCC1DE),
    surfaceContainerLowest = Color(0xFF2A1A31),
    surfaceContainerLow = Color(0xFF26172C),
    surfaceContainer = Color(0xFF2E1D35),
    surfaceContainerHigh = Color(0xFF392740),
    surfaceContainerHighest = Color(0xFF45314C),
    outline = Color(0xFFA78CA9),
    outlineVariant = Color(0xFF4D3A52),
)

/** Money coming in: purple. */
val MoneyInColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE3A6FF) else Color(0xFF7B1FA2)

/** Money going out: hot pink. (Over-budget warnings use the theme's error red instead.) */
val MoneyOutColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8AB8) else Color(0xFFC2185B)

/** Soft lavender-to-pink wash behind every screen. */
val BackgroundWash: Brush
    @Composable get() = if (isSystemInDarkTheme()) {
        Brush.verticalGradient(listOf(Color(0xFF1C1022), Color(0xFF26101F)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF6EAFF), Color(0xFFFFEDF6)))
    }

/** Header gradient: pink into purple. White text on it stays above 4.5:1. */
val PinkPurpleGradient = Brush.linearGradient(listOf(Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF6A1B9A)))

/**
 * One fixed color per budget bucket, used by every chart, bar and legend dot. The order
 * (needs, wants, giving, savings, debt) is the pie order; it was checked for colorblind-safe
 * separation between neighbouring slices in both light and dark mode.
 */
@Composable
fun bucketColor(bucket: Bucket): Color {
    val dark = isSystemInDarkTheme()
    return when (bucket) {
        Bucket.NEEDS -> if (dark) Color(0xFF9085E9) else Color(0xFF4A3AA7)
        Bucket.WANTS -> if (dark) Color(0xFFD55181) else Color(0xFFE87BA4)
        Bucket.GIVING -> if (dark) Color(0xFFC98500) else Color(0xFFEDA100)
        Bucket.SAVINGS -> if (dark) Color(0xFF199E70) else Color(0xFF1BAF7A)
        Bucket.DEBT -> if (dark) Color(0xFFE66767) else Color(0xFFE34948)
    }
}

@Composable
fun GigBudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
