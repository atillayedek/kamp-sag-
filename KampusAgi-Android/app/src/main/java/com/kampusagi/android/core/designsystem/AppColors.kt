package com.kampusagi.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Şartname §2.1 — renk token'ları (hex değerleri AYNEN). Ekranlarda ham hex
 * yazılmaz; her yerde `MaterialTheme.appColors` üzerinden okunur.
 */
@Immutable
class AppColors(
    val isDark: Boolean,
    val appBg: Color,
    val appBg2: Color,
    val card: Color,
    val cardBorder: Color,
    val label: Color,
    val label2: Color,
    val primary: Color,
    val primaryContrast: Color,
    val secondaryBtnBg: Color,
    val pending: Color,
    val approved: Color,
    val rejected: Color,
    val gold: Color,
) {
    val divider: Color get() = cardBorder

    // Türetilmiş renkler (opaklık karışımları).
    val secondaryBtnBorder: Color get() = primary.copy(alpha = 0.35f)
    val iconBadgeBg: Color get() = primary.copy(alpha = 0.14f)
    val aiCardBg: Color get() = primary.copy(alpha = 0.07f).compositeOver(card)
    val aiCardBorder: Color get() = primary.copy(alpha = 0.45f)
    val featuredPlanBg: Color get() = primary.copy(alpha = 0.06f).compositeOver(card)
    val matchScoreBg: Color get() = approved.copy(alpha = 0.16f)
    val aiChipNeedBorder: Color get() = primary.copy(alpha = 0.40f)
}

val LightAppColors = AppColors(
    isDark = false,
    appBg = Color(0xFFFFFFFF),
    appBg2 = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE5E5EA),
    label = Color(0xFF1C1C1E),
    label2 = Color(0xFF6C6C70),
    primary = Color(0xFF4F46E5),
    primaryContrast = Color(0xFFFFFFFF),
    secondaryBtnBg = Color(0xFFF2F2F7),
    pending = Color(0xFFF59E0B),
    approved = Color(0xFF16A34A),
    rejected = Color(0xFFDC2626),
    gold = Color(0xFFB7862B),
)

val DarkAppColors = AppColors(
    isDark = true,
    appBg = Color(0xFF000000),
    appBg2 = Color(0xFF1C1C1E),
    card = Color(0xFF2C2C2E),
    cardBorder = Color(0xFF3A3A3C),
    label = Color(0xFFFFFFFF),
    label2 = Color(0xFF98989F),
    primary = Color(0xFF818CF8),
    primaryContrast = Color(0xFF0B0B10),
    secondaryBtnBg = Color(0xFF1C1C1E),
    pending = Color(0xFFFBBF24),
    approved = Color(0xFF4ADE80),
    rejected = Color(0xFFF87171),
    gold = Color(0xFFE3B457),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
