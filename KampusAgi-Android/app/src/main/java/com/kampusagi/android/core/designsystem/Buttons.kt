package com.kampusagi.android.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Kullanıcının tasarım spesifikasyonu mesajından birebir buton stilleri:
 * min yükseklik 48dp, köşe 12dp, tam genişlik, 15sp SemiBold.
 */

@Composable
fun KampusAgiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(KampusAgiSpacing.buttonMinHeight),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(KampusAgiSpacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = KampusAgiButtonTextStyle)
        }
    }
}

@Composable
fun KampusAgiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(KampusAgiSpacing.buttonMinHeight),
        enabled = enabled,
        shape = RoundedCornerShape(KampusAgiSpacing.buttonCornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Text(text, style = KampusAgiButtonTextStyle)
    }
}

@Composable
fun KampusAgiDangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = MaterialTheme.colorScheme.error, style = KampusAgiButtonTextStyle)
    }
}
