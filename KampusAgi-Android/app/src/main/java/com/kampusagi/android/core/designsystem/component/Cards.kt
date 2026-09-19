package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** `card` zemin + 1pt `cardBorder`, köşe 14pt, iç boşluk 14×16pt (şartname §2.3). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: androidx.compose.ui.graphics.Color = MaterialTheme.appColors.card,
    border: BorderStroke = BorderStroke(Dimens.cardBorderWidth, MaterialTheme.appColors.cardBorder),
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.cardPaddingH, vertical = Dimens.cardPaddingV),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@LightDarkPreviews
@Composable
private fun AppCardPreview() {
    PreviewSurface {
        AppCard(modifier = Modifier.padding(20.dp)) {
            Text("SEBEP", style = MaterialTheme.appText.reasonLabel)
            Text("Belge okunamıyor.", style = MaterialTheme.appText.body)
        }
    }
}
