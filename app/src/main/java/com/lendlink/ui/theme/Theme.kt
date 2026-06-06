package com.lendlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SkyBlue       = Color(0xFF29B6F6)
val DeepBlue      = Color(0xFF1565C0)
val DarkBlue      = Color(0xFF0D47A1)
val TealGreen     = Color(0xFF00897B)
val LightBlue     = Color(0xFFE3F2FD)
val WalletBg      = Color(0xFF1565C0)
val AvailGreen    = Color(0xFF2E7D32)
val LentOrange    = Color(0xFFE65100)
val OverdueRed    = Color(0xFFC62828)
val DamagePurple  = Color(0xFFBB86FC)
val PendingBlue   = Color(0xFF64B5F6)
val SurfaceGray   = Color(0xFFF5F5F5)

private val LightColors = lightColorScheme(
    primary        = DeepBlue,
    onPrimary      = Color.White,
    secondary      = TealGreen,
    onSecondary    = Color.White,
    background     = SurfaceGray,
    surface        = Color.White,
    error          = OverdueRed,
    onBackground   = Color(0xFF1A1A1A),
    onSurface      = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(
    primary        = Color(0xFF90CAF9),
    onPrimary      = Color(0xFF0D47A1),
    secondary      = Color(0xFF80CBC4),
    background     = Color(0xFF121212),
    surface        = Color(0xFF1E1E1E),
    error          = Color(0xFFEF9A9A),
    onBackground   = Color(0xFFE0E0E0),
    onSurface      = Color(0xFFE0E0E0),
)

val AppTypography = Typography(
    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun LendLinkTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography  = AppTypography,
        content     = content
    )
}
