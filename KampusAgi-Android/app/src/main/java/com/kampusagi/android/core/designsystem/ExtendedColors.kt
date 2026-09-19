package com.kampusagi.android.core.designsystem

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material3 ColorScheme'in içermediği durum renkleri (pending/approved) —
 * kullanıcının isteği: "CompositionLocal ile" (bkz. tasarım mesajı).
 * `error` zaten Material3 ColorScheme'in bir parçası olduğu için buraya
 * eklenmedi (Theme.kt'de colorScheme.error kullanılır).
 */
data class ExtendedColors(
    val pending: Color,
    val approved: Color,
)

val LocalExtendedColors = compositionLocalOf {
    ExtendedColors(
        pending = KampusAgiColors.PendingLight,
        approved = KampusAgiColors.ApprovedLight,
    )
}
