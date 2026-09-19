package com.kampusagi.android.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.BuildConfig
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.GoogleSignInButton
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecureField
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    LoginScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onForgotPassword = viewModel::sendPasswordReset,
        onSubmit = viewModel::signIn,
        onGoogleClick = {
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                viewModel.onGoogleNotConfigured()
            } else {
                scope.launch {
                    when (val result = requestGoogleIdToken(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                        is GoogleSignInResult.Token -> viewModel.onGoogleIdToken(result.idToken)
                        GoogleSignInResult.Cancelled -> Unit
                        GoogleSignInResult.Failed -> viewModel.onGoogleFailed()
                    }
                }
            }
        },
    )
}

/** Hilt/ViewModel bağımlılığı olmayan durumsuz içerik — @Preview ve testler bunu kullanır. */
@Composable
internal fun LoginScreenContent(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
    onGoogleClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Scaffold(
        containerColor = colors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.login_title), onBack = onBackClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            LabeledField(
                label = stringResource(R.string.login_email_label),
                value = uiState.email,
                onValueChange = onEmailChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )

            SecureField(
                label = stringResource(R.string.login_password_label),
                value = uiState.password,
                onValueChange = onPasswordChange,
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onForgotPassword) {
                    Text(
                        text = stringResource(R.string.login_forgot_password),
                        style = MaterialTheme.appText.fieldLabel.copy(color = colors.primary),
                    )
                }
            }

            PrimaryButton(
                text = stringResource(R.string.login_submit_button),
                onClick = onSubmit,
                enabled = uiState.canSubmit || uiState.isLoading,
                isLoading = uiState.isLoading,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
                Text(text = stringResource(R.string.login_divider_or), style = MaterialTheme.appText.caption)
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
            }

            GoogleSignInButton(
                text = stringResource(R.string.login_google_button),
                onClick = onGoogleClick,
                enabled = !uiState.isLoading,
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun LoginScreenPreview() {
    PreviewSurface {
        LoginScreenContent(
            uiState = LoginUiState(email = "ornek@kampus.edu.tr", password = "gizli"),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onForgotPassword = {},
            onSubmit = {},
            onGoogleClick = {},
        )
    }
}
