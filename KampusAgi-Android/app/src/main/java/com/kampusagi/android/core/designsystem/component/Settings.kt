package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

enum class SettingsRowStyle { Normal, Premium, Danger }

/** Kart içinde sıralanan ayar satırlarının kabı (satırlar arası `SettingsDivider`). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(0.dp), content = content)
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Dimens.cardPaddingH),
        thickness = 1.dp,
        color = MaterialTheme.appColors.divider,
    )
}

/** Normal / Premium (`gold`, kalın) / Danger (`rejected`) ayar satırı; sağda › veya özel öğe (ör. switch). */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    style: SettingsRowStyle = SettingsRowStyle.Normal,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.appColors
    val textStyle = when (style) {
        SettingsRowStyle.Normal -> MaterialTheme.appText.body
        SettingsRowStyle.Premium -> MaterialTheme.appText.body.copy(color = colors.gold, fontWeight = FontWeight.Bold)
        SettingsRowStyle.Danger -> MaterialTheme.appText.body.copy(color = colors.rejected)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.cardPaddingH, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = textStyle, modifier = Modifier.weight(1f))
        when {
            trailing != null -> trailing()
            onClick != null && style != SettingsRowStyle.Danger -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.label2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Anahtarlı ayar satırı ("Koyu Görünüm" gibi); satırın tamamı dokunulabilir. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Dimens.cardPaddingH, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.appText.body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.primaryContrast,
                uncheckedTrackColor = colors.appBg2,
                uncheckedThumbColor = colors.label2,
                uncheckedBorderColor = colors.cardBorder,
            ),
        )
    }
}

@LightDarkPreviews
@Composable
private fun SettingsPreview() {
    PreviewSurface {
        var dark by remember { mutableStateOf(false) }
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup {
                SettingsRow(title = "Hesap Bilgileri", onClick = {})
                SettingsDivider()
                SettingsSwitchRow(title = "Koyu Görünüm", checked = dark, onCheckedChange = { dark = it })
                SettingsDivider()
                SettingsRow(title = "Premium'a Yükselt", style = SettingsRowStyle.Premium, onClick = {})
                SettingsDivider()
                SettingsRow(title = "Hesabı Sil", style = SettingsRowStyle.Danger, onClick = {})
            }
        }
    }
}
