package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/**
 * Abonelik planı kartı. Fiyat/dönem metni Play Billing'den yerelleştirilmiş olarak
 * gelir (koda yazılmaz); `priceText` null ise fiyat satırı hiç gösterilmez.
 * `isFeatured` -> `featuredPlanBg` zemin, `primary` çerçeve ve isteğe bağlı rozet ("Popüler").
 */
@Composable
fun PlanCard(
    name: String,
    features: List<String>,
    modifier: Modifier = Modifier,
    priceText: String? = null,
    isFeatured: Boolean = false,
    badgeText: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.appColors
    val text = MaterialTheme.appText
    AppCard(
        modifier = modifier,
        background = if (isFeatured) colors.featuredPlanBg else colors.card,
        border = BorderStroke(if (isFeatured) 1.5.dp else Dimens.cardBorderWidth, if (isFeatured) colors.primary else colors.cardBorder),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = text.titleMedium, modifier = Modifier.weight(1f))
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    style = text.chip.copy(color = colors.primaryContrast),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.primary)
                        .padding(horizontal = Dimens.chipPaddingH, vertical = Dimens.chipPaddingV),
                )
            }
        }
        if (priceText != null) {
            Text(text = priceText, style = text.body.copy(color = colors.label))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.approved,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = feature, style = text.body, modifier = Modifier.weight(1f))
                }
            }
        }
        if (footer != null) {
            Box { footer() }
        }
    }
}

@LightDarkPreviews
@Composable
private fun PlanCardPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlanCard(name = "Free", features = listOf("Topluluklara katılım", "Temel eşleşme"), footer = {
                Text("Mevcut plan", style = MaterialTheme.appText.caption)
            })
            PlanCard(
                name = "Premium",
                priceText = "₺ örnek fiyat / ay",
                features = listOf("Sınırsız ilan", "Öncelikli eşleşme"),
                isFeatured = true,
                badgeText = "Popüler",
            )
        }
    }
}
