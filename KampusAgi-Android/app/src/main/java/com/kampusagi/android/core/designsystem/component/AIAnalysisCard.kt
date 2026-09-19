package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** Kesik çizgili (dashed) `aiCardBorder` çerçeveli AI analiz kartı; içerik slot'ta (çipler + açıklama). */
@Composable
fun AIAnalysisCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.appColors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.aiCardBg)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                drawRoundRect(
                    color = colors.aiCardBorder,
                    cornerRadius = CornerRadius(Dimens.cardRadius.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = Dimens.cardPaddingH, vertical = Dimens.cardPaddingV),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = title, style = MaterialTheme.appText.fieldLabel.copy(color = colors.primary))
        }
        content()
    }
}

@LightDarkPreviews
@Composable
private fun AIAnalysisCardPreview() {
    PreviewSurface {
        AIAnalysisCard(title = "AI ANALİZİ", modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("İhtiyacım Var", style = ChipStyle.Need)
                Chip("Ders notu")
                Chip("Bu hafta")
            }
            Text("Kategori ve zaman bilgisi otomatik çıkarılır.", style = MaterialTheme.appText.caption)
        }
    }
}
