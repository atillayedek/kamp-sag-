package com.kampusagi.android.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Avatar
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SettingsDivider
import com.kampusagi.android.core.designsystem.component.SettingsGroup
import com.kampusagi.android.core.designsystem.component.SettingsRow
import com.kampusagi.android.core.designsystem.component.SettingsRowStyle
import com.kampusagi.android.core.designsystem.component.SettingsSwitchRow
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.settings.ThemeMode

@Composable
fun ProfileScreen(
    onOpenAccountInfo: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPremium: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
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
    ProfileContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        systemInDarkTheme = isSystemInDarkTheme(),
        onRetry = viewModel::load,
        onOpenAccountInfo = onOpenAccountInfo,
        onOpenNotifications = onOpenNotifications,
        onOpenPrivacy = onOpenPrivacy,
        onDarkModeChange = viewModel::onDarkModeChange,
        onUseSystemTheme = viewModel::useSystemTheme,
        onOpenPremium = onOpenPremium,
        onDeleteAccount = viewModel::requestDeleteAccount,
        onSignOut = onSignOut,
        onConfirmDelete = viewModel::confirmDeleteAccount,
        onDismissDelete = viewModel::dismissDeleteDialog,
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    systemInDarkTheme: Boolean,
    onRetry: () -> Unit,
    onOpenAccountInfo: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUseSystemTheme: () -> Unit,
    onOpenPremium: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = stringResource(R.string.profile_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            when (val profile = state.profile) {
                Loadable.Loading -> SkeletonCardList(count = 1)
                is Loadable.Failure -> ErrorStateView(message = profile.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> ProfileHeader(profile.value)
            }

            SettingsGroup {
                SettingsRow(title = stringResource(R.string.settings_account_info), onClick = onOpenAccountInfo)
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_notifications), onClick = onOpenNotifications)
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_privacy), onClick = onOpenPrivacy)
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dark_mode),
                    checked = state.themeMode.isDark(systemInDarkTheme),
                    onCheckedChange = onDarkModeChange,
                )
                if (state.themeMode != ThemeMode.SYSTEM) {
                    SettingsDivider()
                    // Açık/koyu sabitlenmişse sisteme dönüş yolu; chevron göstermemek için boş `trailing`.
                    SettingsRow(title = stringResource(R.string.settings_use_system_theme), onClick = onUseSystemTheme, trailing = {})
                }
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_premium), style = SettingsRowStyle.Premium, onClick = onOpenPremium)
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.settings_delete_account), style = SettingsRowStyle.Danger, onClick = onDeleteAccount)
                SettingsDivider()
                SettingsRow(title = stringResource(R.string.logout_button), style = SettingsRowStyle.Danger, onClick = onSignOut)
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteAccountDialog(isDeleting = state.isDeleting, onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
    }
}

@Composable
private fun ProfileHeader(profile: Profile) {
    val text = MaterialTheme.appText
    val verified = stringResource(R.string.profile_verified)
    // "Bölüm · Üniversite · Onaylı ✓" — eksik alanlar atlanır, "Onaylı ✓" her zaman vardır (ana uygulamaya yalnızca onaylılar girer).
    val subtitle = listOfNotNull(profile.department?.takeIf { it.isNotBlank() }, profile.universityName, verified).joinToString(" · ")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(name = profile.fullName, imageUrl = profile.avatarUrl, size = Dimens.avatarLarge)
        Text(text = profile.fullName, style = text.titleMedium)
        Text(text = subtitle, style = text.bodyCenter)
    }
}

@Composable
private fun DeleteAccountDialog(isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.appColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text(text = stringResource(R.string.delete_account_title), style = MaterialTheme.appText.titleMedium) },
        text = { Text(text = stringResource(R.string.delete_account_message), style = MaterialTheme.appText.body) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), color = colors.rejected, strokeWidth = 2.dp)
                }
                Text(text = stringResource(R.string.delete_account_confirm), color = colors.rejected)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(text = stringResource(R.string.common_cancel), color = colors.primary)
            }
        },
    )
}

@LightDarkPreviews
@Composable
private fun ProfilePreview() {
    PreviewSurface {
        ProfileContent(
            state = ProfileUiState(
                profile = Loadable.Success(Profile("u1", "ayse", "Ayşe Nur", null, "uni", "Bilgisayar Mühendisliği", "Test Üniversitesi", "TÜ")),
                themeMode = ThemeMode.LIGHT,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            systemInDarkTheme = false,
            onRetry = {}, onOpenAccountInfo = {}, onOpenNotifications = {}, onOpenPrivacy = {}, onDarkModeChange = {},
            onUseSystemTheme = {}, onOpenPremium = {}, onDeleteAccount = {}, onSignOut = {}, onConfirmDelete = {}, onDismissDelete = {},
        )
    }
}
