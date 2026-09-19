package com.kampusagi.android.feature.auth.register

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.feature.auth.register.steps.AccountStep
import com.kampusagi.android.feature.auth.register.steps.DepartmentStep
import com.kampusagi.android.feature.auth.register.steps.DocumentStep
import com.kampusagi.android.feature.auth.register.steps.EmailConfirmationStep
import com.kampusagi.android.feature.auth.register.steps.PersonalStep
import com.kampusagi.android.feature.auth.register.steps.SummaryStep
import com.kampusagi.android.feature.auth.register.steps.UniversityStep

@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onDocumentPicked(uri)
    }

    // Sistem geri tuşu bir önceki adıma dönsün; ilk adımdaysa (veya doğrulama beklerken) normal davranış.
    BackHandler(enabled = uiState.canGoBack) { viewModel.onBack() }

    RegisterScreenContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        actions = RegisterActions(
            onTopBack = { if (!viewModel.onBack()) onBackClick() },
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onFullNameChange = viewModel::onFullNameChange,
            onUsernameChange = viewModel::onUsernameChange,
            onUniversityQueryChange = viewModel::onUniversityQueryChange,
            onUniversitySelected = viewModel::onUniversitySelected,
            onRetryUniversities = viewModel::loadUniversities,
            onDepartmentChange = viewModel::onDepartmentChange,
            onContinue = viewModel::onContinue,
            onBack = { viewModel.onBack() },
            onConfirmEmail = viewModel::confirmEmailAndContinue,
            onResendConfirmation = viewModel::resendConfirmation,
            onEditAccountInfo = viewModel::editAccountInfo,
            onPickDocument = { documentPicker.launch(arrayOf("application/pdf")) },
            onClearDocument = viewModel::clearDocument,
            onShowHelp = viewModel::setHelpVisible,
            onSignOut = viewModel::signOut,
        ),
    )
}

internal class RegisterActions(
    val onTopBack: () -> Unit,
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onFullNameChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onUniversityQueryChange: (String) -> Unit,
    val onUniversitySelected: (com.kampusagi.android.domain.university.University) -> Unit,
    val onRetryUniversities: () -> Unit,
    val onDepartmentChange: (String) -> Unit,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
    val onConfirmEmail: () -> Unit,
    val onResendConfirmation: () -> Unit,
    val onEditAccountInfo: () -> Unit,
    val onPickDocument: () -> Unit,
    val onClearDocument: () -> Unit,
    val onShowHelp: (Boolean) -> Unit,
    val onSignOut: () -> Unit,
)

/** Hilt/ViewModel bağımlılığı olmayan durumsuz içerik — @Preview ve testler bunu kullanır. */
@Composable
internal fun RegisterScreenContent(
    state: RegisterUiState,
    snackbarHostState: SnackbarHostState,
    actions: RegisterActions,
) {
    val colors = MaterialTheme.appColors
    Scaffold(
        containerColor = colors.appBg,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.register_title),
                onBack = actions.onTopBack.takeIf { !state.isSignedIn || state.canGoBack },
                actions = {
                    if (state.isSignedIn) {
                        TextButton(onClick = actions.onSignOut) {
                            Text(
                                text = stringResource(R.string.logout_button),
                                style = MaterialTheme.appText.fieldLabel.copy(color = colors.rejected),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val onBack = actions.onBack.takeIf { state.canGoBack }

        Box(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
        when {
            state.step == RegisterStep.ACCOUNT_INFO && state.awaitingEmailConfirmation ->
                EmailConfirmationStep(
                    state = state,
                    onConfirmed = actions.onConfirmEmail,
                    onResend = actions.onResendConfirmation,
                    onEditEmail = actions.onEditAccountInfo,
                )
            state.step == RegisterStep.ACCOUNT_INFO ->
                AccountStep(state, actions.onEmailChange, actions.onPasswordChange, actions.onContinue)
            state.step == RegisterStep.PERSONAL_INFO ->
                PersonalStep(state, actions.onFullNameChange, actions.onUsernameChange, actions.onContinue, onBack)
            state.step == RegisterStep.UNIVERSITY ->
                UniversityStep(
                    state, actions.onUniversityQueryChange, actions.onUniversitySelected,
                    actions.onRetryUniversities, actions.onContinue, onBack,
                )
            state.step == RegisterStep.DEPARTMENT ->
                DepartmentStep(state, actions.onDepartmentChange, actions.onContinue, onBack)
            state.step == RegisterStep.SUMMARY ->
                SummaryStep(state, actions.onContinue, onBack)
            else ->
                DocumentStep(
                    state, actions.onPickDocument, actions.onClearDocument, actions.onShowHelp,
                    actions.onContinue, onBack,
                )
        }
        }
    }
}

@LightDarkPreviews
@Composable
private fun RegisterScreenPreview() {
    PreviewSurface {
        RegisterScreenContent(
            state = RegisterUiState(email = "ornek@kampus.edu.tr", password = "gizli1"),
            snackbarHostState = remember { SnackbarHostState() },
            actions = RegisterActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
