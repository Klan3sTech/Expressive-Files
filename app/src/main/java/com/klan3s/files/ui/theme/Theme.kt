package com.klan3s.files.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.klan3s.files.data.Settings
import com.klan3s.files.data.ThemeMode

// ------------------------------------------------------------ seed → scheme (HSL-аппроксимация тональных палитр)

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return Hsl(h, s, l)
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

fun schemeFromSeed(seed: Color, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val base = seed.toHsl()
    val h = base.h
    val s = base.s.coerceIn(0.35f, 0.9f)
    val sSec = s * 0.35f
    val hTer = h + 60f
    val sNeu = 0.10f
    return if (!dark) lightColorScheme(
        primary = hsl(h, s, 0.40f), onPrimary = Color.White,
        primaryContainer = hsl(h, s, 0.90f), onPrimaryContainer = hsl(h, s, 0.12f),
        inversePrimary = hsl(h, s, 0.80f),
        secondary = hsl(h, sSec, 0.40f), onSecondary = Color.White,
        secondaryContainer = hsl(h, sSec + 0.15f, 0.88f), onSecondaryContainer = hsl(h, sSec, 0.12f),
        tertiary = hsl(hTer, s * 0.7f, 0.40f), onTertiary = Color.White,
        tertiaryContainer = hsl(hTer, s * 0.7f, 0.90f), onTertiaryContainer = hsl(hTer, s * 0.7f, 0.12f),
        background = hsl(h, sNeu, 0.985f), onBackground = hsl(h, sNeu, 0.11f),
        surface = hsl(h, sNeu, 0.985f), onSurface = hsl(h, sNeu, 0.11f),
        surfaceVariant = hsl(h, sNeu + 0.08f, 0.90f), onSurfaceVariant = hsl(h, sNeu + 0.05f, 0.30f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, sNeu, 0.965f),
        surfaceContainer = hsl(h, sNeu, 0.945f),
        surfaceContainerHigh = hsl(h, sNeu, 0.92f),
        surfaceContainerHighest = hsl(h, sNeu, 0.90f),
        surfaceDim = hsl(h, sNeu, 0.87f), surfaceBright = hsl(h, sNeu, 0.985f),
        outline = hsl(h, sNeu, 0.48f), outlineVariant = hsl(h, sNeu, 0.80f),
        inverseSurface = hsl(h, sNeu, 0.20f), inverseOnSurface = hsl(h, sNeu, 0.95f),
    ) else darkColorScheme(
        primary = hsl(h, s, 0.80f), onPrimary = hsl(h, s, 0.20f),
        primaryContainer = hsl(h, s, 0.30f), onPrimaryContainer = hsl(h, s, 0.90f),
        inversePrimary = hsl(h, s, 0.40f),
        secondary = hsl(h, sSec + 0.1f, 0.80f), onSecondary = hsl(h, sSec, 0.20f),
        secondaryContainer = hsl(h, sSec + 0.1f, 0.30f), onSecondaryContainer = hsl(h, sSec, 0.90f),
        tertiary = hsl(hTer, s * 0.7f, 0.80f), onTertiary = hsl(hTer, s * 0.7f, 0.20f),
        tertiaryContainer = hsl(hTer, s * 0.7f, 0.30f), onTertiaryContainer = hsl(hTer, s * 0.7f, 0.90f),
        background = if (amoled) Color.Black else hsl(h, sNeu, 0.08f), onBackground = hsl(h, sNeu, 0.90f),
        surface = if (amoled) Color.Black else hsl(h, sNeu, 0.08f), onSurface = hsl(h, sNeu, 0.90f),
        surfaceVariant = hsl(h, sNeu + 0.05f, 0.27f), onSurfaceVariant = hsl(h, sNeu + 0.05f, 0.78f),
        surfaceContainerLowest = if (amoled) Color.Black else hsl(h, sNeu, 0.04f),
        surfaceContainerLow = if (amoled) hsl(h, sNeu, 0.06f) else hsl(h, sNeu, 0.11f),
        surfaceContainer = if (amoled) hsl(h, sNeu, 0.09f) else hsl(h, sNeu, 0.13f),
        surfaceContainerHigh = if (amoled) hsl(h, sNeu, 0.12f) else hsl(h, sNeu, 0.17f),
        surfaceContainerHighest = if (amoled) hsl(h, sNeu, 0.15f) else hsl(h, sNeu, 0.21f),
        surfaceDim = hsl(h, sNeu, 0.08f), surfaceBright = hsl(h, sNeu, 0.24f),
        outline = hsl(h, sNeu, 0.56f), outlineVariant = hsl(h, sNeu, 0.28f),
        inverseSurface = hsl(h, sNeu, 0.90f), inverseOnSurface = hsl(h, sNeu, 0.20f),
    )
}

// ------------------------------------------------------------ контраст текста по яркости фона

val TextDark = Color(0xFF111111)
val TextLight = Color(0xFFFAFAFA)

/** Чёрный текст на светлом фоне, белый — на тёмном. */
fun onColorFor(bg: Color): Color = if (bg.luminance() > 0.42f) TextDark else TextLight

/** Приводит все пары «фон / текст» к читаемому контрасту (в т.ч. для Dynamic Color). */
fun ColorScheme.fixContrast(): ColorScheme = copy(
    onPrimary = onColorFor(primary),
    onPrimaryContainer = onColorFor(primaryContainer),
    onSecondary = onColorFor(secondary),
    onSecondaryContainer = onColorFor(secondaryContainer),
    onTertiary = onColorFor(tertiary),
    onTertiaryContainer = onColorFor(tertiaryContainer),
    onError = onColorFor(error),
    onErrorContainer = onColorFor(errorContainer),
    onBackground = onColorFor(background),
    onSurface = onColorFor(surface),
    onSurfaceVariant = if (surface.luminance() > 0.42f) Color(0xFF3A3A3A) else Color(0xFFD0D0D0),
    inverseOnSurface = onColorFor(inverseSurface),
)

private val ExpressiveTypography = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun isDark(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFilesTheme(settings: Settings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isDark(settings)
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        settings.seedArgb == 0L && dynamicOk -> {
            val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            if (dark && settings.amoled) s.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else s
        }
        else -> schemeFromSeed(Color(if (settings.seedArgb == 0L) 0xFF6750A4 else settings.seedArgb), dark, settings.amoled)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme.fixContrast(),
        motionScheme = MotionScheme.expressive(),
        typography = ExpressiveTypography,
        content = content,
    )
}

