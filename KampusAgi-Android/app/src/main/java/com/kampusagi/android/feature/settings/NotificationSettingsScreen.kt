package com.kampusagi.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SettingsGroup
import com.kampusagi.android.core.designsystem.component.SettingsSwitchRow
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
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
    NotificationSettingsContent(uiState, snackbarHostState, onBack, viewModel::load, viewModel::onNewMessageChange)
}

@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNewMessageChange: (Boolean) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.notif_title), onBack = onBack) },
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
            when (val newMessage = state.newMessage) {
                Loadable.Loading -> SkeletonCardList(count = 1)
                is Loadable.Failure -> ErrorStateView(message = newMessage.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> {
                    SettingsGroup {
                        SettingsSwitchRow(
                            title = stringResource(R.string.notif_new_message),
                            checked = newMessage.value,
                            onCheckedChange = onNewMessageChange,
                        )
                    }
                    Text(text = stringResource(R.string.notif_footer), style = MaterialTheme.appText.caption)
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun NotificationSettingsPreview() {
    PreviewSurface {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(Loadable.Success(true)),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onNewMessageChange = {},
        )
    }
}
