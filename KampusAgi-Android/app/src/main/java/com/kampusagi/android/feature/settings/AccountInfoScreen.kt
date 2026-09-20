package com.kampusagi.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.profile.Profile

@Composable
fun AccountInfoScreen(
    onBack: () -> Unit,
    viewModel: AccountInfoViewModel = hiltViewModel(),
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
    AccountInfoContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onFullNameChange = viewModel::onFullNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onDepartmentChange = viewModel::onDepartmentChange,
        onSave = viewModel::save,
    )
}

@Composable
internal fun AccountInfoContent(
    state: AccountInfoUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFullNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onDepartmentChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.account_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            when (val profile = state.profile) {
                Loadable.Loading -> SkeletonCardList(count = 2)
                is Loadable.Failure -> ErrorStateView(message = profile.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> {
                    LabeledField(label = stringResource(R.string.account_email_label), value = state.email, onValueChange = {}, enabled = false)
                    LabeledField(
                        label = stringResource(R.string.register_full_name_label),
                        value = state.fullName,
                        onValueChange = onFullNameChange,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                    val usernameError = state.usernameTaken || (state.username.isNotEmpty() && !state.isUsernameValid)
                    LabeledField(
                        label = stringResource(R.string.register_username_label),
                        value = state.username,
                        onValueChange = onUsernameChange,
                        isError = usernameError,
                        supportingText = when {
                            state.usernameTaken -> stringResource(R.string.account_username_taken)
                            usernameError -> stringResource(R.string.register_username_invalid)
                            else -> stringResource(R.string.register_username_hint)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    LabeledField(
                        label = stringResource(R.string.account_university_label),
                        value = profile.value.universityName.orEmpty(),
                        onValueChange = {},
                        enabled = false,
                        supportingText = stringResource(R.string.account_university_locked),
                    )
                    LabeledField(
                        label = stringResource(R.string.register_department_label),
                        value = state.department,
                        onValueChange = onDepartmentChange,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.account_save),
                        onClick = onSave,
                        enabled = state.canSave,
                        isLoading = state.isSaving,
                    )
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun AccountInfoPreview() {
    val profile = Profile("u1", "ayse_nur", "Ayşe Nur", null, "uni", "Bilgisayar Mühendisliği", "Test Üniversitesi", "TÜ")
    PreviewSurface {
        AccountInfoContent(
            state = AccountInfoUiState(
                profile = Loadable.Success(profile),
                email = "ayse@ogrenci.test",
                fullName = profile.fullName,
                username = profile.username,
                department = profile.department.orEmpty(),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onFullNameChange = {}, onUsernameChange = {}, onDepartmentChange = {}, onSave = {},
        )
    }
}
