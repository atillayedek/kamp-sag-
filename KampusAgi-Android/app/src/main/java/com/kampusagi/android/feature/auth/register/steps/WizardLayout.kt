package com.kampusagi.android.feature.auth.register.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.StepIndicator
import com.kampusagi.android.feature.auth.register.RegisterStep

/** Tüm sihirbaz adımlarının ortak düzeni: adım göstergesi, başlık, içerik ve alt düğme çubuğu. */
@Composable
internal fun WizardStepLayout(
    step: RegisterStep,
    stepTitle: String,
    heading: String,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
    ) {
        StepIndicator(
            currentStep = step.number,
            totalSteps = RegisterStep.TOTAL_STEPS,
            label = stringResource(R.string.register_step_format, step.number, RegisterStep.TOTAL_STEPS, stepTitle),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = heading,
                style = MaterialTheme.appText.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            headingAction?.invoke()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            content = content,
        )
        bottomBar()
    }
}

/** "Geri" (secondary) + "Devam" (primary, daha geniş). `onBack` null ise yalnızca tam genişlikte ana düğme. */
@Composable
internal fun WizardBottomBar(
    continueText: String,
    onContinue: () -> Unit,
    canContinue: Boolean,
    isLoading: Boolean,
    onBack: (() -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) {
            SecondaryButton(
                text = stringResource(R.string.register_back_button),
                onClick = onBack,
                enabled = !isLoading,
                modifier = Modifier.weight(1f),
            )
        }
        PrimaryButton(
            text = continueText,
            onClick = onContinue,
            enabled = canContinue || isLoading,
            isLoading = isLoading,
            modifier = Modifier.weight(if (onBack != null) 1.4f else 1f),
        )
    }
}
