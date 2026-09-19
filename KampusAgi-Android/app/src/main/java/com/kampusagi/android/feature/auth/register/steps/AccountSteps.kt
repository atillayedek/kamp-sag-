package com.kampusagi.android.feature.auth.register.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.SecureField
import com.kampusagi.android.feature.auth.register.RegisterStep
import com.kampusagi.android.feature.auth.register.RegisterUiState

/** Adım 1 — e-posta + şifre. */
@Composable
internal fun AccountStep(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    WizardStepLayout(
        step = RegisterStep.ACCOUNT_INFO,
        stepTitle = stringResource(R.string.register_step_account_title),
        heading = stringResource(R.string.register_account_heading),
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_continue_button),
                onContinue = onContinue,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = null,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            LabeledField(
                label = stringResource(R.string.login_email_label),
                value = state.email,
                onValueChange = onEmailChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            SecureField(
                label = stringResource(R.string.login_password_label),
                value = state.password,
                onValueChange = onPasswordChange,
                supportingText = stringResource(R.string.register_password_hint),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onContinue() }),
            )
        }
    }
}

/** Supabase e-posta doğrulaması açıkken kayıttan sonra gösterilen bekleme durumu. */
@Composable
internal fun EmailConfirmationStep(
    state: RegisterUiState,
    onConfirmed: () -> Unit,
    onResend: () -> Unit,
    onEditEmail: () -> Unit,
) {
    WizardStepLayout(
        step = RegisterStep.ACCOUNT_INFO,
        stepTitle = stringResource(R.string.register_step_account_title),
        heading = stringResource(R.string.register_confirm_heading),
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.register_confirm_continue_button),
                    onClick = onConfirmed,
                    isLoading = state.isLoading,
                )
                SecondaryButton(
                    text = stringResource(R.string.register_confirm_resend_button),
                    onClick = onResend,
                    enabled = !state.isLoading,
                )
                SecondaryButton(
                    text = stringResource(R.string.register_back_button),
                    onClick = onEditEmail,
                    enabled = !state.isLoading,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.register_confirm_body, state.email),
            style = MaterialTheme.appText.body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Adım 2 — ad soyad + kullanıcı adı. */
@Composable
internal fun PersonalStep(
    state: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
) {
    WizardStepLayout(
        step = RegisterStep.PERSONAL_INFO,
        stepTitle = stringResource(R.string.register_step_personal_title),
        heading = stringResource(R.string.register_personal_heading),
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_continue_button),
                onContinue = onContinue,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = onBack,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            LabeledField(
                label = stringResource(R.string.register_full_name_label),
                value = state.fullName,
                onValueChange = onFullNameChange,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            LabeledField(
                label = stringResource(R.string.register_username_label),
                value = state.username,
                onValueChange = onUsernameChange,
                isError = state.showUsernameError,
                supportingText = stringResource(
                    if (state.showUsernameError) R.string.register_username_invalid else R.string.register_username_hint,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onContinue() }),
            )
        }
    }
}

/** Adım 4 — bölüm. */
@Composable
internal fun DepartmentStep(
    state: RegisterUiState,
    onDepartmentChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
) {
    WizardStepLayout(
        step = RegisterStep.DEPARTMENT,
        stepTitle = stringResource(R.string.register_step_department_title),
        heading = stringResource(R.string.register_department_heading),
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_continue_button),
                onContinue = onContinue,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = onBack,
            )
        },
    ) {
        LabeledField(
            label = stringResource(R.string.register_department_label),
            value = state.department,
            onValueChange = onDepartmentChange,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onContinue() }),
        )
    }
}

/** Adım 5 — girilen bilgilerin özeti; "Kaydet ve Devam Et" profili kaydeder. */
@Composable
internal fun SummaryStep(
    state: RegisterUiState,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
) {
    WizardStepLayout(
        step = RegisterStep.SUMMARY,
        stepTitle = stringResource(R.string.register_step_summary_title),
        heading = stringResource(R.string.register_summary_heading),
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_summary_save_button),
                onContinue = onContinue,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = onBack,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            SummaryRow(stringResource(R.string.login_email_label), state.email)
            SummaryRow(stringResource(R.string.register_full_name_label), state.fullName.trim())
            SummaryRow(stringResource(R.string.register_username_label), state.username.trim())
            SummaryRow(stringResource(R.string.register_summary_university_label), state.selectedUniversity?.name.orEmpty())
            SummaryRow(stringResource(R.string.register_department_label), state.department.trim())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.appText.fieldLabel)
        Text(text = value, style = MaterialTheme.appText.body)
    }
}

@LightDarkPreviews
@Composable
private fun AccountStepPreview() {
    PreviewSurface {
        AccountStep(
            state = RegisterUiState(email = "ornek@kampus.edu.tr", password = "gizli1"),
            onEmailChange = {}, onPasswordChange = {}, onContinue = {},
        )
    }
}
