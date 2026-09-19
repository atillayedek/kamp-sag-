package com.kampusagi.android.feature.auth.login

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiPrimaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSecondaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    LoginScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onForgotPassword = viewModel::sendPasswordReset,
        onSubmit = viewModel::signIn,
        onGoogleClick = {
            scope.launch {
                signInWithGoogle(
                    context = context,
                    onIdToken = viewModel::signInWithGoogleIdToken,
                    onFailure = viewModel::onGoogleSignInFailed,
                )
            }
        },
    )
}

/** Hilt/ViewModel bağımlılığı olmayan durumsuz (stateless) içerik — @Preview bunu kullanır. */
@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
    onGoogleClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back_cd))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(KampusAgiSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(KampusAgiSpacing.itemSpacing),
        ) {
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                placeholder = { Text(stringResource(R.string.login_email_placeholder)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (uiState.isPasswordVisible) R.string.login_password_hide_cd else R.string.login_password_show_cd,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onForgotPassword) {
                    Text(stringResource(R.string.login_forgot_password), color = MaterialTheme.colorScheme.primary)
                }
            }

            KampusAgiPrimaryButton(
                text = stringResource(R.string.login_submit_button),
                onClick = onSubmit,
                enabled = uiState.canSubmit,
                isLoading = uiState.isLoading,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.login_divider_or), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            KampusAgiSecondaryButton(
                text = stringResource(R.string.login_google_button),
                onClick = onGoogleClick,
            )
        }
    }
}

/**
 * NOT — doğrulanmamış API yüzeyi: Credential Manager + Google Identity
 * Services API'si iyi bilinen kalıplara göre yazıldı; bu ortamda (Android
 * SDK yok) derlenip test edilememiştir (bkz. memory-bank/Memory_Bank.md).
 * `setServerClientId` Supabase Dashboard > Authentication > Providers >
 * Google altında kayıtlı WEB OAuth Client ID olmalıdır (Android Client ID
 * DEĞİL) — Google Identity kütüphanesinin gerektirdiği budur. Gerçek değer
 * olmadan bilinemeyeceği için PLACEHOLDER bırakılmıştır, uydurulmamıştır.
 */
private suspend fun signInWithGoogle(
    context: Context,
    onIdToken: (String) -> Unit,
    onFailure: () -> Unit,
) {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId("REPLACE_WITH_GOOGLE_WEB_CLIENT_ID")
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(context, request)
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        onIdToken(googleIdTokenCredential.idToken)
    } catch (e: GetCredentialException) {
        Log.e("LoginScreen", "Google Sign-In başarısız", e)
        onFailure()
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenPreview() {
    KampusAgiTheme {
        LoginScreenContent(
            uiState = LoginUiState(email = "ada.yilmaz@ogr.metu.edu.tr"),
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onForgotPassword = {},
            onSubmit = {},
            onGoogleClick = {},
        )
    }
}
