package io.github.gumwoo.aiinterview.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/** 통화 화면 색. 초록과 빨강은 전화기의 받기·끊기처럼 상태를 알릴 때만 쓴다. */
object CallColors {
    val Background = Color(0xFF0F1114)
    val Control = Color(0xFF212429)
    val Sheet = Color(0xFF1C1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFF9A9DA3)
    val Dim = Color(0xFF6A6D73)
    val Speaking = Color(0xFF58D68D)
    val Answer = Color(0xFF1E9E5A)
    val Partial = Color(0xFFE8B04A)
    val End = Color(0xFFE5484D)
}

private val ColorScheme = darkColorScheme(
    background = CallColors.Background,
    onBackground = CallColors.Text,
    surface = CallColors.Background,
    onSurface = CallColors.Text,
    onSurfaceVariant = CallColors.Muted,
    surfaceContainerHigh = CallColors.Sheet,
    surfaceContainerHighest = CallColors.Control,
    primaryContainer = CallColors.Control,
    onPrimaryContainer = CallColors.Text,
    secondaryContainer = CallColors.Control,
    onSecondaryContainer = CallColors.Text,
    tertiaryContainer = CallColors.Text,
    onTertiaryContainer = CallColors.Background,
    primary = CallColors.Text,
    onPrimary = CallColors.Background,
    error = CallColors.End,
)

/** 한국어가 어절 중간에서 끊기지 않게 한다. */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

private val CallTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, lineHeight = 76.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.4).sp,
        lineBreak = KoreanLineBreak,
    ),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp, lineBreak = KoreanLineBreak),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, lineBreak = KoreanLineBreak),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, lineBreak = KoreanLineBreak),
)

@Composable
fun CallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = CallTypography, content = content)
}
