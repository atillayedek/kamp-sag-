package com.kampusagi.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Dimens.inputRadius),
    medium = RoundedCornerShape(Dimens.buttonRadius),
    large = RoundedCornerShape(Dimens.cardRadius),
    extraLarge = RoundedCornerShape(Dimens.bubbleRadius),
)

/** Token'lardan Material3 ColorScheme türetir; Material bileşenleri (Switch, Snackbar, Sheet…) böylece marka renginde kalır. */
private fun AppColors.toColorScheme(): ColorScheme {
    val secondaryContainer = primary.copy(alpha = 0.14f).compositeOver(appBg)
    return if (isDark) {
        darkColorScheme(
            primary = primary, onPrimary = primaryContrast,
            primaryContainer = secondaryContainer, onPrimaryContainer = primary,
            secondary = primary, onSecondary = primaryContrast,
            secondaryContainer = secondaryContainer, onSecondaryContainer = primary,
            background = appBg, onBackground = label,
            surface = appBg, onSurface = label,
            surfaceVariant = appBg2, onSurfaceVariant = label2,
            surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = appBg2, surfaceContainerHighest = appBg2,
            outline = cardBorder, outlineVariant = cardBorder,
            error = rejected, onError = primaryContrast,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = primaryContrast,
            primaryContainer = secondaryContainer, onPrimaryContainer = primary,
            secondary = primary, onSecondary = primaryContrast,
            secondaryContainer = secondaryContainer, onSecondaryContainer = primary,
            background = appBg, onBackground = label,
            surface = appBg, onSurface = label,
            surfaceVariant = appBg2, onSurfaceVariant = label2,
            surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = appBg2, surfaceContainerHighest = appBg2,
            outline = cardBorder, outlineVariant = cardBorder,
            error = rejected, onError = primaryContrast,
        )
    }
}

/** Dynamic color KASITLI OLARAK KAPALI — marka renkleri kullanılır. */
@Composable
fun KampusAgiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val textStyles = remember(colors) { AppTextStyles(colors) }
    val colorScheme = remember(colors) { colors.toColorScheme() }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTextStyles provides textStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppMaterialTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
