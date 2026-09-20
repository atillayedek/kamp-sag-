package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.feature.settings.AccountInfoContent
import com.kampusagi.android.feature.settings.AccountInfoUiState
import com.kampusagi.android.feature.settings.NotificationSettingsContent
import com.kampusagi.android.feature.settings.NotificationSettingsUiState
import com.kampusagi.android.feature.settings.PrivacyContent
import com.kampusagi.android.feature.settings.PrivacyUiState
import com.kampusagi.android.feature.settings.ProfileContent
import com.kampusagi.android.feature.settings.ProfileUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class SettingsSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val profile = Profile("u1", "ayse_nur", "Ayşe Nur", null, "uni-1", "Bilgisayar Mühendisliği", "Test Üniversitesi", "TÜ")

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
        composeRule.assertAccessibleInteractions()
    }

    @Composable
    private fun profileScreen(state: ProfileUiState, systemInDarkTheme: Boolean = false) = ProfileContent(
        state = state, snackbarHostState = remember { SnackbarHostState() }, systemInDarkTheme = systemInDarkTheme,
        onRetry = {}, onOpenAccountInfo = {}, onOpenNotifications = {}, onOpenPrivacy = {}, onDarkModeChange = {},
        onUseSystemTheme = {}, onOpenPremium = {}, onDeleteAccount = {}, onSignOut = {}, onConfirmDelete = {}, onDismissDelete = {},
    )

    @Test
    fun profileFollowingSystemTheme() = snap("settings_profile_system") {
        profileScreen(ProfileUiState(profile = Loadable.Success(profile), themeMode = ThemeMode.SYSTEM))
    }

    @Test
    fun profileWithPinnedThemeShowsSystemRow() = snap("settings_profile_pinned") {
        profileScreen(ProfileUiState(profile = Loadable.Success(profile), themeMode = ThemeMode.DARK), systemInDarkTheme = false)
    }

    @Test
    fun profileError() = snap("settings_profile_error") {
        profileScreen(ProfileUiState(profile = Loadable.Failure(AppError.Network())))
    }

    @Test
    fun accountInfo() = snap("settings_account_info") {
        AccountInfoContent(
            state = AccountInfoUiState(
                profile = Loadable.Success(profile), email = "ayse@ogrenci.test",
                fullName = profile.fullName, username = profile.username, department = "Makine Mühendisliği",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onFullNameChange = {}, onUsernameChange = {}, onDepartmentChange = {}, onSave = {},
        )
    }

    @Test
    fun accountInfoUsernameTaken() = snap("settings_account_username_taken") {
        AccountInfoContent(
            state = AccountInfoUiState(
                profile = Loadable.Success(profile), email = "ayse@ogrenci.test",
                fullName = profile.fullName, username = "alinmis", department = profile.department.orEmpty(), usernameTaken = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onFullNameChange = {}, onUsernameChange = {}, onDepartmentChange = {}, onSave = {},
        )
    }

    @Test
    fun notificationSettings() = snap("settings_notifications") {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(Loadable.Success(true)),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onNewMessageChange = {},
        )
    }

    @Test
    fun privacy() = snap("settings_privacy") {
        PrivacyContent(PrivacyUiState(analyticsEnabled = true), remember { SnackbarHostState() }, onBack = {}, onAnalyticsChange = {})
    }
}
