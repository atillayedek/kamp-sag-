package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

private val ButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
private fun ButtonLabel(
    text: String,
    contentColor: Color,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = contentColor, strokeWidth = 2.dp)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Dimens.buttonIconGap))
        }
        Text(text, style = MaterialTheme.appText.button, color = contentColor)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.appColors
    val loadingText = stringResource(R.string.common_loading)
    Button(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonMinHeight)
            .semantics { if (isLoading) stateDescription = loadingText },
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.primaryContrast,
            disabledContainerColor = colors.primary.copy(alpha = 0.4f),
            disabledContentColor = colors.primaryContrast.copy(alpha = 0.8f),
        ),
        contentPadding = ButtonPadding,
    ) {
        ButtonLabel(text, colors.primaryContrast, isLoading, leadingIcon)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.appColors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonMinHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.buttonRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.secondaryBtnBg,
            contentColor = colors.primary,
            disabledContainerColor = colors.secondaryBtnBg,
            disabledContentColor = colors.primary.copy(alpha = 0.4f),
        ),
        border = BorderStroke(1.dp, colors.secondaryBtnBorder),
        contentPadding = ButtonPadding,
    ) {
        ButtonLabel(text, if (enabled) colors.primary else colors.primary.copy(alpha = 0.4f), false, leadingIcon)
    }
}

@Composable
fun TextDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonMinHeight),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.rejected,
            disabledContentColor = colors.rejected.copy(alpha = 0.4f),
        ),
    ) {
        Text(text, style = MaterialTheme.appText.button)
    }
}

/** Secondary stil + renkli Google "G" logosu (marka renkleri tema dışıdır). */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonMinHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.buttonRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.secondaryBtnBg,
            contentColor = colors.primary,
        ),
        border = BorderStroke(1.dp, colors.secondaryBtnBorder),
        contentPadding = ButtonPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Dimens.buttonIconGap))
            Text(text, style = MaterialTheme.appText.button, color = colors.primary)
        }
    }
}

@LightDarkPreviews
@Composable
private fun ButtonsPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "Giriş Yap", onClick = {})
            PrimaryButton(text = "Yükleniyor", onClick = {}, isLoading = true)
            PrimaryButton(text = "Devre dışı", onClick = {}, enabled = false)
            SecondaryButton(text = "Kayıt Ol", onClick = {})
            GoogleSignInButton(text = "Google ile devam et", onClick = {})
            TextDangerButton(text = "Çıkış Yap", onClick = {})
        }
    }
}
