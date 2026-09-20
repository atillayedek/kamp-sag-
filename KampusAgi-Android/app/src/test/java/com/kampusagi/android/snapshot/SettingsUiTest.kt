package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.profile.Profile
import com.kampusagi.android.domain.settings.ThemeMode
import com.kampusagi.android.feature.settings.ProfileContent
import com.kampusagi.android.feature.settings.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Profil ekranının gerçek etkileşimleri: satır tıklamaları, tema anahtarı, hesap silme onay penceresi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class SettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val profile = Profile("u1", "ayse_nur", "Ayşe Nur", null, "uni-1", "Bilgisayar Mühendisliği", "Test Üniversitesi", "TÜ")

    private class Calls {
        val opened = mutableListOf<String>()
        val darkChanges = mutableListOf<Boolean>()
        var systemTheme = 0
        var delete = 0
        var confirm = 0
        var dismiss = 0
        var signOut = 0
    }

    private fun show(state: ProfileUiState, calls: Calls, systemInDarkTheme: Boolean = false) {
        composeRule.setContent {
            KampusAgiTheme(darkTheme = false) {
                ProfileContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    systemInDarkTheme = systemInDarkTheme,
                    onRetry = {},
                    onOpenAccountInfo = { calls.opened += "account" },
                    onOpenNotifications = { calls.opened += "notifications" },
                    onOpenPrivacy = { calls.opened += "privacy" },
                    onDarkModeChange = { calls.darkChanges += it },
                    onUseSystemTheme = { calls.systemTheme++ },
                    onOpenPremium = { calls.opened += "premium" },
                    onDeleteAccount = { calls.delete++ },
                    onSignOut = { calls.signOut++ },
                    onConfirmDelete = { calls.confirm++ },
                    onDismissDelete = { calls.dismiss++ },
                )
            }
        }
    }

    @Test
    fun `basliktaki alt satir Bolum Universite Onayli bicimindedir`() {
        show(ProfileUiState(profile = Loadable.Success(profile)), Calls())
        composeRule.onNodeWithText("Ayşe Nur").assertIsDisplayed()
        composeRule.onNodeWithText("Bilgisayar Mühendisliği · Test Üniversitesi · Onaylı ✓").assertIsDisplayed()
    }

    @Test
    fun `satirlar ilgili ekranlari acar`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile)), calls)

        composeRule.onNodeWithText("Hesap Bilgileri").performClick()
        composeRule.onNodeWithText("Bildirim Ayarları").performClick()
        composeRule.onNodeWithText("Gizlilik ve Konum").performClick()
        composeRule.onNodeWithText("Premium'a Yükselt").performScrollTo().performClick()

        assertEquals(listOf("account", "notifications", "privacy", "premium"), calls.opened)
    }

    @Test
    fun `koyu gorunum anahtari sistem koyuysa acik gorunur ve dokunus tersini ister`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile), themeMode = ThemeMode.SYSTEM), calls, systemInDarkTheme = true)

        composeRule.onNodeWithText("Koyu Görünüm").assertIsOn()
        composeRule.onNodeWithText("Koyu Görünüm").performClick()
        assertEquals(listOf(false), calls.darkChanges)
    }

    @Test
    fun `sabit acik tercihte anahtar kapali ve sisteme donus satiri gorunur`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile), themeMode = ThemeMode.LIGHT), calls, systemInDarkTheme = true)

        composeRule.onNodeWithText("Koyu Görünüm").assertIsOff()
        composeRule.onNodeWithText("Sistem görünümünü kullan").performScrollTo().performClick()
        assertEquals(1, calls.systemTheme)
    }

    @Test
    fun `sistemi izlerken sisteme donus satiri yoktur`() {
        show(ProfileUiState(profile = Loadable.Success(profile), themeMode = ThemeMode.SYSTEM), Calls())
        composeRule.onNodeWithText("Sistem görünümünü kullan").assertDoesNotExist()
    }

    @Test
    fun `hesabi sil satiri onay penceresi istegini tetikler ama dogrudan silmez`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile)), calls)

        composeRule.onNodeWithText("Hesabı Sil").performScrollTo().performClick()

        assertEquals(1, calls.delete)
        assertEquals(0, calls.confirm)
    }

    @Test
    fun `onay penceresi uyariyi gosterir vazgec ve onayla dogru geri cagriyi calistirir`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile), showDeleteDialog = true), calls)

        composeRule.onNodeWithText("Hesabını silmek istiyor musun?").assertIsDisplayed()
        composeRule.onNodeWithText("Bu işlem geri alınamaz.", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Vazgeç").performClick()
        assertEquals(1, calls.dismiss)

        composeRule.onNode(hasText("Hesabı Sil") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, calls.confirm)
    }

    @Test
    fun `cikis yap satiri oturumu kapatma geri cagrisini calistirir`() {
        val calls = Calls()
        show(ProfileUiState(profile = Loadable.Success(profile)), calls)
        composeRule.onNodeWithText("Çıkış Yap").performScrollTo().performClick()
        assertEquals(1, calls.signOut)
        assertTrue(calls.opened.isEmpty())
    }
}
