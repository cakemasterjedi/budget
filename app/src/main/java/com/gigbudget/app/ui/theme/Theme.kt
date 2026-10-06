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
    primary = Color(0xFF8A3FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1DCFF),
    onPrimaryContainer = Color(0xFF2E0050),
    secondary = Color(0xFFC2397E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Color(0xFF3E0022),
    tertiary = Color(0xFF6F4FB8),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF24005A),
    background = Color(0xFFFFF7FB),
    onBackground = Color(0xFF221A22),
    surface = Color(0xFFFFF7FB),
    onSurface = Color(0xFF221A22),
    surfaceVariant = Color(0xFFF3E3F0),
    onSurfaceVariant = Color(0xFF574A57),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F7),
    surfaceContainer = Color(0xFFFBEAF4),
    surfaceContainerHigh = Color(0xFFF7E3EF),
    surfaceContainerHighest = Color(0xFFF1DDEA),
    outline = Color(0xFF8A7A89),
    outlineVariant = Color(0xFFDCC8D9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDDB5FF),
    onPrimary = Color(0xFF47007A),
    primaryContainer = Color(0xFF6A23A3),
    onPrimaryContainer = Color(0xFFF1DCFF),
    secondary = Color(0xFFFFB0CF),
    onSecondary = Color(0xFF5E1140),
    secondaryContainer = Color(0xFF7D2758),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFCFBDFF),
    tertiaryContainer = Color(0xFF55379D),
    onTertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF1E1726),
    onBackground = Color(0xFFEDE0EA),
    surface = Color(0xFF1E1726),
    onSurface = Color(0xFFEDE0EA),
    surfaceVariant = Color(0xFF4B3F4C),
    onSurfaceVariant = Color(0xFFD8C2D5),
    surfaceContainerLowest = Color(0xFF180F1F),
    surfaceContainerLow = Color(0xFF261E2E),
    surfaceContainer = Color(0xFF2B2233),
    surfaceContainerHigh = Color(0xFF362C3E),
    surfaceContainerHighest = Color(0xFF41374A),
    outline = Color(0xFFA08DA0),
    outlineVariant = Color(0xFF4B3F4C),
)

val IncomeGreen = Color(0xFF2E9E4F)
val SpendRed = Color(0xFFD64545)

/** Header gradient: pink into purple. White text on it stays above 4.5:1. */
val PinkPurpleGradient = Brush.linearGradient(listOf(Color(0xFFC2397E), Color(0xFF6F3FB8)))

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
