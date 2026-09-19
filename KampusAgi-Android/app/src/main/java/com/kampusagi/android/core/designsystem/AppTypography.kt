package com.kampusagi.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Şartname §2.2 — tipografi. Sistem fontu (Android varsayılan sans); boyutlar
 * `sp` olduğu için Dynamic Type (yazı tipi ölçeği) otomatik desteklenir.
 * Renk İÇERMEYEN taban stiller; renkli sürümü `AppTextStyles` verir.
 */
internal object AppTypographyBase {
    private val font = FontFamily.Default

    val titleLarge = TextStyle(
        fontFamily = font, fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em, textAlign = TextAlign.Center,
    )
    val titleMedium = TextStyle(
        fontFamily = font, fontSize = 20.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em,
    )
    val navTitle = TextStyle(fontFamily = font, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val button = TextStyle(fontFamily = font, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = font, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Normal)
    val bodyCenter = body.copy(textAlign = TextAlign.Center)
    val fieldLabel = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontFamily = font, fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.Normal)
    val reasonLabel = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em)
    val chip = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val receipt = TextStyle(fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Normal)
}

/** Renkleri tema token'larından gelen isimli metin stilleri. */
@Immutable
class AppTextStyles(colors: AppColors) {
    val titleLarge = AppTypographyBase.titleLarge.copy(color = colors.label)
    val titleMedium = AppTypographyBase.titleMedium.copy(color = colors.label)
    val navTitle = AppTypographyBase.navTitle.copy(color = colors.label)
    val button = AppTypographyBase.button
    val body = AppTypographyBase.body.copy(color = colors.label)
    val bodyCenter = AppTypographyBase.bodyCenter.copy(color = colors.label2)
    val fieldLabel = AppTypographyBase.fieldLabel.copy(color = colors.label2)
    val caption = AppTypographyBase.caption.copy(color = colors.label2)
    val reasonLabel = AppTypographyBase.reasonLabel.copy(color = colors.label2)
    val chip = AppTypographyBase.chip.copy(color = colors.label)
    val receipt = AppTypographyBase.receipt.copy(color = colors.label2)
}

val LocalAppTextStyles = staticCompositionLocalOf { AppTextStyles(LightAppColors) }

val MaterialTheme.appText: AppTextStyles
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTextStyles.current

/** Material bileşenlerinin (TextField, Snackbar, Switch…) iç metinleri için renksiz eşleme. */
internal val AppMaterialTypography = Typography(
    headlineSmall = AppTypographyBase.titleMedium,
    titleLarge = AppTypographyBase.titleLarge.copy(textAlign = TextAlign.Unspecified),
    titleMedium = AppTypographyBase.titleMedium,
    titleSmall = AppTypographyBase.navTitle.copy(textAlign = TextAlign.Unspecified),
    bodyLarge = AppTypographyBase.body,
    bodyMedium = AppTypographyBase.body,
    bodySmall = AppTypographyBase.caption,
    labelLarge = AppTypographyBase.button,
    labelMedium = AppTypographyBase.fieldLabel,
    labelSmall = AppTypographyBase.receipt,
)
