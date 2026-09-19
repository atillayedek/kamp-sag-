package com.kampusagi.android.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Her bileşen/ekran için light + dark önizleme (Preview'larda örnek veri serbesttir). */
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Preview(
    name = "Dark",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class LightDarkPreviews

@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    KampusAgiTheme(darkTheme = isSystemInDarkTheme()) {
        Box(modifier = Modifier.background(androidx.compose.material3.MaterialTheme.appColors.appBg)) {
            content()
        }
    }
}
