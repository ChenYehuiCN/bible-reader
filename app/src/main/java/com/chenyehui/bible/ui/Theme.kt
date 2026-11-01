package com.chenyehui.bible.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chenyehui.bible.R
import com.chenyehui.bible.data.ThemeMode

val MonoFamily = FontFamily(
    Font(R.font.sarasa_mono_sc_regular, FontWeight.Normal),
    Font(R.font.sarasa_mono_sc_bold, FontWeight.Bold),
)

@Immutable
data class BiblePalette(
    val isDark: Boolean,
    // Reading surface, sampled from the reference reader screenshots.
    val readerBackground: Color,
    val readerText: Color,
    val readerMuted: Color,
    val readerHeading: Color,
    val readerVerse: Color,
    // Menus and controls stay black / white / grey.
    val uiBackground: Color,
    val uiSurface: Color,
    val uiText: Color,
    val uiMuted: Color,
    val uiBorder: Color,
    val uiSelected: Color,
    val uiOnSelected: Color,
)

private val LightPalette = BiblePalette(
    isDark = false,
    readerBackground = Color(0xFFB4EFC3),
    readerText = Color(0xFF1F1F1F),
    readerMuted = Color(0xFF4A4A4A),
    readerHeading = Color(0xFF111111),
    readerVerse = Color(0xFF5A5A5A),
    uiBackground = Color(0xFFEFEFEF),
    uiSurface = Color(0xFFFAFAFA),
    uiText = Color(0xFF171717),
    uiMuted = Color(0xFF666666),
    uiBorder = Color(0xFFD2D2D2),
    uiSelected = Color(0xFF171717),
    uiOnSelected = Color(0xFFFAFAFA),
)

private val DarkPalette = BiblePalette(
    isDark = true,
    readerBackground = Color(0xFF000000),
    readerText = Color(0xFFADADAD),
    readerMuted = Color(0xFF777777),
    readerHeading = Color(0xFFD0D0D0),
    readerVerse = Color(0xFF7C7C7C),
    uiBackground = Color(0xFF111111),
    uiSurface = Color(0xFF1A1A1A),
    uiText = Color(0xFFD0D0D0),
    uiMuted = Color(0xFF8A8A8A),
    uiBorder = Color(0xFF333333),
    uiSelected = Color(0xFFD0D0D0),
    uiOnSelected = Color(0xFF111111),
)

val LocalBiblePalette = staticCompositionLocalOf { LightPalette }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFFDCDCDC),
    onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF4A4A4A),
    onSecondary = Color(0xFFFAFAFA),
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color(0xFF171717),
    background = Color(0xFFB4EFC3),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFD2D2D2),
    outlineVariant = Color(0xFFE2E2E2),
    error = Color(0xFF3A3A3A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD0D0D0),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFADADAD),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFD0D0D0),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFBDBDBD),
)

private fun textStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
) = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private val BibleTypography = Typography(
    displayLarge = textStyle(34, 42),
    displayMedium = textStyle(30, 38),
    displaySmall = textStyle(26, 34),
    headlineLarge = textStyle(26, 34),
    headlineMedium = textStyle(23, 31),
    headlineSmall = textStyle(21, 29),
    titleLarge = textStyle(20, 28, FontWeight.Bold),
    titleMedium = textStyle(17, 25),
    titleSmall = textStyle(15, 22),
    bodyLarge = textStyle(16, 25),
    bodyMedium = textStyle(14, 22),
    bodySmall = textStyle(12, 19),
    labelLarge = textStyle(13, 18, FontWeight.Normal, 0.3f),
    labelMedium = textStyle(11, 16, FontWeight.Normal, 0.4f),
    labelSmall = textStyle(10, 15, FontWeight.Normal, 0.5f),
)

private val BibleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun BibleTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalBiblePalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = BibleTypography,
            shapes = BibleShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
