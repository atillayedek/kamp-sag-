package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** Üstte BÜYÜK HARF etiketli, `appBg2` zeminli giriş kutusu (şartname §2.3). */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.appColors
    val text = MaterialTheme.appText
    val shape = RoundedCornerShape(Dimens.inputRadius)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = text.fieldLabel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.appBg2)
                .then(if (isError) Modifier.border(1.dp, colors.rejected, shape) else Modifier)
                .padding(horizontal = Dimens.inputPaddingH, vertical = Dimens.inputPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label },
                    enabled = enabled,
                    textStyle = text.body,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(text = placeholder, style = text.body.copy(color = colors.label2))
                            }
                            inner()
                        }
                    },
                )
            }
            trailing?.invoke()
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = text.caption.copy(color = if (isError) colors.rejected else colors.label2),
            )
        }
    }
}

/** Göz ikonuyla göster/gizle özellikli şifre alanı. */
@Composable
fun SecureField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.appColors

    LabeledField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { isVisible = !isVisible }, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (isVisible) R.string.field_password_hide_cd else R.string.field_password_show_cd,
                    ),
                    tint = colors.label2,
                )
            }
        },
    )
}

@LightDarkPreviews
@Composable
private fun FieldsPreview() {
    PreviewSurface {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("gizli") }
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledField(label = "E-POSTA", value = email, onValueChange = { email = it }, placeholder = "E-posta adresin")
            SecureField(label = "ŞİFRE", value = password, onValueChange = { password = it })
            LabeledField(
                label = "KULLANICI ADI", value = "ab", onValueChange = {},
                isError = true, supportingText = "En az 3 karakter olmalı.",
            )
        }
    }
}
