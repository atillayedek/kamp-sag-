package com.kampusagi.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Dynamic color KASITLI OLARAK KAPALI — marka renkleri kullanılır
 * (kullanıcının açık talimatı). `dynamicLightColorScheme`/`dynamicDarkColorScheme`
 * hiçbir yerde çağrılmaz.
 */
private val LightColors = lightColorScheme(
    primary = KampusAgiColors.PrimaryLight,
    onPrimary = KampusAgiColors.OnPrimaryLight,
    background = KampusAgiColors.BackgroundLight,
    surface = KampusAgiColors.SurfaceLight,
    surfaceVariant = KampusAgiColors.SurfaceVariantLight,
    surfaceContainer = KampusAgiColors.SurfaceContainerLight,
    outlineVariant = KampusAgiColors.OutlineVariantLight,
    onSurfaceVariant = KampusAgiColors.OnSurfaceVariantLight,
    error = KampusAgiColors.ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = KampusAgiColors.PrimaryDark,
    onPrimary = KampusAgiColors.OnPrimaryDark,
    background = KampusAgiColors.BackgroundDark,
    surface = KampusAgiColors.SurfaceDark,
    surfaceVariant = KampusAgiColors.SurfaceVariantDark,
    surfaceContainer = KampusAgiColors.SurfaceContainerDark,
    outlineVariant = KampusAgiColors.OutlineVariantDark,
    onSurfaceVariant = KampusAgiColors.OnSurfaceVariantDark,
    error = KampusAgiColors.ErrorDark,
)

@Composable
fun KampusAgiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) {
        ExtendedColors(pending = KampusAgiColors.PendingDark, approved = KampusAgiColors.ApprovedDark)
    } else {
        ExtendedColors(pending = KampusAgiColors.PendingLight, approved = KampusAgiColors.ApprovedLight)
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KampusAgiTypography,
            shapes = KampusAgiShapes,
            content = content,
        )
    }
}

/** `MaterialTheme.extendedColors.pending` şeklinde kullanım için kısayol. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
