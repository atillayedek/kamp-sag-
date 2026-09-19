package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** N segmentli adım göstergesi; tamamlananlar `primary`, kalanlar `cardBorder`; altında "N/6 — Adım adı". */
@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.stepBarGap), modifier = Modifier.fillMaxWidth()) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.stepBarHeight)
                        .clip(CircleShape)
                        .background(if (index < currentStep) colors.primary else colors.cardBorder),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.appText.fieldLabel,
            modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { },
        )
    }
}

@LightDarkPreviews
@Composable
private fun StepIndicatorPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepIndicator(currentStep = 1, totalSteps = 6, label = "1/6 — Hesap Bilgileri")
            StepIndicator(currentStep = 6, totalSteps = 6, label = "6/6 — Öğrenci Belgesi")
        }
    }
}
