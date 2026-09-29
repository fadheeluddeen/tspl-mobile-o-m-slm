package com.technavious.om15.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Orange50 = Color(0xFFFFF7ED)
val Orange100 = Color(0xFFFFEDD5)
val Orange200 = Color(0xFFFED7AA)
val Orange500 = Color(0xFFF97316)
val Orange600 = Color(0xFFEA580C)
val Orange700 = Color(0xFFC2410C)
val Orange800 = Color(0xFF9A3412)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)
val Green100 = Color(0xFFDCFCE7)
val Green700 = Color(0xFF15803D)
val Red50 = Color(0xFFFEF2F2)
val Red100 = Color(0xFFFEE2E2)
val Red600 = Color(0xFFDC2626)
val Red700 = Color(0xFFB91C1C)

private val LightColorScheme = lightColorScheme(
    primary = Orange600,
    onPrimary = Color.White,
    primaryContainer = Orange100,
    onPrimaryContainer = Orange800,
    secondary = Slate800,
    onSecondary = Color.White,
    tertiary = Green700,
    tertiaryContainer = Green100,
    onTertiaryContainer = Green700,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Color.White,
    onSurfaceVariant = Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Slate100,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Red600,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red700,
)

val MeterMono = FontFamily.Monospace

private val OMTypography = Typography(
    displayMedium = TextStyle(fontFamily = MeterMono, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun OM15Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = OMTypography,
        content = content
    )
}
