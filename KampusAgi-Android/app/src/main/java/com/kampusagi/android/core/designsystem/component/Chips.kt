package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import androidx.compose.ui.res.stringResource

enum class ChipStyle { Default, Need, Selected }

/** Tam yuvarlak (capsule) etiket; padding 5×10pt, 1pt sınır (şartname §2.3). */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Default,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.appColors
    val (background, border, content) = when (style) {
        ChipStyle.Default -> Triple(colors.card, colors.cardBorder, colors.label)
        ChipStyle.Need -> Triple(colors.iconBadgeBg, colors.aiChipNeedBorder, colors.primary)
        ChipStyle.Selected -> Triple(colors.primary, colors.primary, colors.primaryContrast)
    }
    Text(
        text = text,
        style = MaterialTheme.appText.chip.copy(color = content),
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, border), CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.chipPaddingH, vertical = Dimens.chipPaddingV),
    )
}

/**
 * Eşleşme kartı rozeti (`approved` %16 zemin): "%N Anlamsal Eşleşme" YALNIZCA skor gerçekten embedding benzerliği
 * içeriyorsa; aksi halde "%N Eşleşme" (yanlış iddia yok, bkz. docs/BLOCKERS.md B3).
 */
@Composable
fun MatchScoreBadge(score: Int, modifier: Modifier = Modifier, isSemantic: Boolean = false) {
    val colors = MaterialTheme.appColors
    Text(
        text = stringResource(
            if (isSemantic) R.string.match_score_semantic_format else R.string.match_score_format,
            score.coerceIn(0, 100),
        ),
        style = MaterialTheme.appText.chip.copy(color = colors.approved),
        modifier = modifier
            .clip(CircleShape)
            .background(colors.matchScoreBg)
            .padding(horizontal = Dimens.chipPaddingH, vertical = Dimens.chipPaddingV),
    )
}

@LightDarkPreviews
@Composable
private fun ChipsPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("İhtiyacım Var", style = ChipStyle.Need)
                Chip("Ders notu")
                Chip("Bu hafta", style = ChipStyle.Selected)
            }
            MatchScoreBadge(score = 90, isSemantic = true)
        }
    }
}
