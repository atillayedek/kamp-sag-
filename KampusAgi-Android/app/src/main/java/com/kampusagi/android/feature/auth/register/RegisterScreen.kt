package com.kampusagi.android.feature.auth.register

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiPrimaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.core.designsystem.KampusAgiStepIndicator
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.feature.auth.register.components.DocumentStep

@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onDocumentSelected) }

    // Sistem geri tuşu bir önceki adıma dönsün (bkz. tasarım mesajı).
    BackHandler(enabled = uiState.currentStep != RegisterStep.ACCOUNT_INFO) {
        viewModel.goBack()
    }

    RegisterScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = {
            if (uiState.currentStep == RegisterStep.ACCOUNT_INFO) onBackClick() else viewModel.goBack()
        },
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onContinueAccountInfo = viewModel::createAccountAndAdvance,
        onAdvancePlaceholder = viewModel::advance,
        onGoBackStep = { viewModel.goBack() },
        onPickDocument = { documentPickerLauncher.launch(arrayOf("application/pdf")) },
        onClearDocument = viewModel::clearSelectedDocument,
        onToggleHelpSheet = viewModel::toggleHelpSheet,
        onSubmitDocument = viewModel::submitDocument,
    )
}

/** Hilt/ViewModel bağımlılığı olmayan durumsuz içerik — @Preview bunu kullanır. */
@Composable
private fun RegisterScreenContent(
    uiState: RegisterUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinueAccountInfo: () -> Unit,
    onAdvancePlaceholder: () -> Unit,
    onGoBackStep: () -> Unit,
    onPickDocument: () -> Unit,
    onClearDocument: () -> Unit,
    onToggleHelpSheet: (Boolean) -> Unit,
    onSubmitDocument: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_cd))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.currentStep) {
                RegisterStep.ACCOUNT_INFO -> AccountInfoStep(
                    uiState = uiState,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onContinue = onContinueAccountInfo,
                )
                RegisterStep.STUDENT_DOCUMENT -> DocumentStep(
                    isLoading = uiState.isLoading,
                    selectedDocumentName = uiState.selectedDocumentName,
                    isHelpSheetVisible = uiState.isHelpSheetVisible,
                    onToggleHelpSheet = onToggleHelpSheet,
                    onPickDocument = onPickDocument,
                    onClearDocument = onClearDocument,
                    onBack = onGoBackStep,
                    onSubmit = onSubmitDocument,
                    canSubmit = uiState.canSubmitDocument,
                )
                else -> UnclearStep(
                    currentStep = uiState.currentStep,
                    onBack = onGoBackStep,
                    onAdvance = onAdvancePlaceholder,
                )
            }
        }
    }
}

@Composable
private fun AccountInfoStep(
    uiState: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KampusAgiSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(KampusAgiSpacing.itemSpacing),
    ) {
        KampusAgiStepIndicator(
            currentStep = RegisterStep.ACCOUNT_INFO.stepNumber,
            totalSteps = RegisterStep.TOTAL_STEPS,
            label = stringResource(
                R.string.register_step_format,
                RegisterStep.ACCOUNT_INFO.stepNumber,
                RegisterStep.TOTAL_STEPS,
                stringResource(R.string.register_step_account_title),
            ),
        )

        Text(stringResource(R.string.register_account_heading), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email_label)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password_label)) },
            placeholder = { Text(stringResource(R.string.register_password_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        KampusAgiPrimaryButton(
            text = stringResource(R.string.register_continue_button),
            onClick = onContinue,
            enabled = uiState.canSubmitAccountInfo,
            isLoading = uiState.isLoading,
        )
    }
}

/**
 * .ACCOUNT_INFO ve .STUDENT_DOCUMENT dışındaki adımlar için içerik hiçbir
 * kaynakta verilmedi — kasıtlı olarak "netleşmedi" gösterilir, tamamlanmış
 * bir özellik gibi sunulmaz (bkz. RegisterViewModel.kt üstündeki not).
 */
@Composable
private fun UnclearStep(
    currentStep: RegisterStep,
    onBack: () -> Unit,
    onAdvance: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KampusAgiSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(KampusAgiSpacing.itemSpacing),
    ) {
        KampusAgiStepIndicator(
            currentStep = currentStep.stepNumber,
            totalSteps = RegisterStep.TOTAL_STEPS,
            label = stringResource(
                R.string.register_step_format,
                currentStep.stepNumber,
                RegisterStep.TOTAL_STEPS,
                stringResource(R.string.register_step_unclear_title),
            ),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.register_step_unclear_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                com.kampusagi.android.core.designsystem.KampusAgiSecondaryButton(
                    text = stringResource(R.string.register_back_button),
                    onClick = onBack,
                )
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                KampusAgiPrimaryButton(text = "İleri (geçici)", onClick = onAdvance)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RegisterScreenAccountInfoPreview() {
    KampusAgiTheme {
        RegisterScreenContent(
            uiState = RegisterUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onContinueAccountInfo = {},
            onAdvancePlaceholder = {},
            onGoBackStep = {},
            onPickDocument = {},
            onClearDocument = {},
            onToggleHelpSheet = {},
            onSubmitDocument = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RegisterScreenDocumentStepPreview() {
    KampusAgiTheme {
        RegisterScreenContent(
            uiState = RegisterUiState(currentStep = RegisterStep.STUDENT_DOCUMENT, selectedDocumentName = "ogrenci_belgesi.pdf"),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onContinueAccountInfo = {},
            onAdvancePlaceholder = {},
            onGoBackStep = {},
            onPickDocument = {},
            onClearDocument = {},
            onToggleHelpSheet = {},
            onSubmitDocument = {},
        )
    }
}
