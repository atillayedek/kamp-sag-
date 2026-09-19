package com.kampusagi.android.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.core.designsystem.appColors

/** Aynı içeriği light (solda) ve dark (sağda) temada yan yana çizer; tek görüntüde iki tema doğrulanır. */
@Composable
fun SideBySide(height: Dp = 860.dp, content: @Composable () -> Unit) {
    Row {
        listOf(false, true).forEach { dark ->
            KampusAgiTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .width(411.dp)
                        .height(height)
                        .background(MaterialTheme.appColors.appBg),
                ) { content() }
            }
        }
    }
}

const val SNAPSHOT_QUALIFIERS = "w900dp-h1700dp-xhdpi"
const val SNAPSHOT_DIR = "src/test/snapshots"
