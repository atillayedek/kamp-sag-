package com.kampusagi.android.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** Veri yokken gösterilen boş durum (ikon opsiyonel, eylem opsiyonel). */
@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPaddingH, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.label2, modifier = Modifier.size(44.dp))
        }
        Text(text = title, style = MaterialTheme.appText.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Text(text = message, style = MaterialTheme.appText.bodyCenter)
        if (actionLabel != null && onAction != null) {
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Hata durumu + "Tekrar Dene". */
@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error_title),
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPaddingH, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.rejected, modifier = Modifier.size(44.dp))
        Text(text = title, style = MaterialTheme.appText.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        Text(text = message, style = MaterialTheme.appText.bodyCenter)
        SecondaryButton(text = stringResource(R.string.common_retry), onClick = onRetry)
    }
}

/** Yüklenirken gösterilen nabız animasyonlu yer tutucu blok. */
@Composable
fun SkeletonView(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp,
) {
    val colors = MaterialTheme.appColors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .graphicsLayer { this.alpha = alpha }
            .background(colors.appBg2),
    )
}

/** Liste ekranları için kart biçimli iskelet satırlar. */
@Composable
fun SkeletonCardList(count: Int = 3, modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.common_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
    ) {
        repeat(count) {
            AppCard(modifier = Modifier.clearAndSetSemantics { }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonView(modifier = Modifier.size(Dimens.avatarList).clip(CircleShape), height = Dimens.avatarList, cornerRadius = 20.dp)
                    Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonView(modifier = Modifier.width(140.dp), height = 12.dp)
                        SkeletonView(modifier = Modifier.width(90.dp), height = 10.dp)
                    }
                }
                SkeletonView(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), height = 12.dp)
                SkeletonView(modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp), height = 12.dp)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun StateViewsPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyStateView(title = "Henüz gönderi yok", message = "İlk gönderiyi sen paylaş.")
            ErrorStateView(message = "Bağlantı kurulamadı.", onRetry = {})
            SkeletonCardList(count = 1)
        }
    }
}
