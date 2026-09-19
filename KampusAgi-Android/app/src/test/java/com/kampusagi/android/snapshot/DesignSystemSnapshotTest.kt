package com.kampusagi.android.snapshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AIAnalysisCard
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.AppFab
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Avatar
import com.kampusagi.android.core.designsystem.component.ChatBubble
import com.kampusagi.android.core.designsystem.component.ChatInputBar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.designsystem.component.ChipStyle
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.GoogleSignInButton
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.MatchScoreBadge
import com.kampusagi.android.core.designsystem.component.PlanCard
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.SecureField
import com.kampusagi.android.core.designsystem.component.SegmentedPicker
import com.kampusagi.android.core.designsystem.component.SettingsDivider
import com.kampusagi.android.core.designsystem.component.SettingsGroup
import com.kampusagi.android.core.designsystem.component.SettingsRow
import com.kampusagi.android.core.designsystem.component.SettingsRowStyle
import com.kampusagi.android.core.designsystem.component.SettingsSwitchRow
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.designsystem.component.StepIndicator
import com.kampusagi.android.core.designsystem.component.TextDangerButton
import com.kampusagi.android.core.designsystem.component.TypingIndicator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tasarım sistemi bileşenlerinin light + dark ekran görüntüleri (test verisi yalnızca burada kullanılır). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class DesignSystemSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(height = 1500.dp, content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    @Test
    fun buttonsAndFields() = snap("ds_buttons_fields") {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("gizli") }
        Column(Modifier.padding(Dimens.screenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)) {
            PrimaryButton(text = "Giriş Yap", onClick = {})
            PrimaryButton(text = "Yükleniyor", onClick = {}, isLoading = true)
            PrimaryButton(text = "Devre dışı", onClick = {}, enabled = false)
            SecondaryButton(text = "Kayıt Ol", onClick = {})
            GoogleSignInButton(text = "Google ile devam et", onClick = {})
            TextDangerButton(text = "Çıkış Yap", onClick = {})
            LabeledField(label = "E-POSTA", value = email, onValueChange = { email = it }, placeholder = "E-posta adresin")
            SecureField(label = "ŞİFRE", value = password, onValueChange = { password = it })
            LabeledField(label = "KULLANICI ADI", value = "Ab", onValueChange = {}, isError = true, supportingText = "En az 3 karakter olmalı.")
            LabeledField(label = "İHTİYACINI ANLAT", value = "Ders notlarına ihtiyacım var", onValueChange = {}, singleLine = false, minLines = 3)
        }
    }

    @Test
    fun cardsChipsAndNavigation() = snap("ds_cards_chips") {
        Column(Modifier.padding(Dimens.screenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)) {
            AppTopBar(title = "Topluluklar", onBack = {})
            StepIndicator(currentStep = 2, totalSteps = 6, label = "2/6 — Kişisel Bilgiler")
            SegmentedPicker(options = listOf("Genel", "Üniversitem"), selectedIndex = 1, onSelect = {})
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(name = "Ayşe Nur")
                    Column {
                        Text("Ayşe Nur", style = MaterialTheme.appText.body)
                        Text("2 saat önce", style = MaterialTheme.appText.caption)
                    }
                }
                Text("SEBEP", style = MaterialTheme.appText.reasonLabel)
                Text("Kart içeriği burada.", style = MaterialTheme.appText.body)
            }
            AIAnalysisCard(title = "AI ANALİZİ") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("İhtiyacım Var", style = ChipStyle.Need)
                    Chip("Ders notu")
                    Chip("Bu hafta")
                }
                Text("Kategori ve zaman bilgisi otomatik çıkarılır.", style = MaterialTheme.appText.caption)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Varsayılan")
                Chip("Seçili", style = ChipStyle.Selected)
                MatchScoreBadge(score = 90)
                MatchScoreBadge(score = 90, isSemantic = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(name = "İlker Şahin", size = Dimens.avatarLarge)
                AppFab(onClick = {}, contentDescription = "Gönderi oluştur")
            }
        }
    }

    @Test
    fun chatSettingsPlansAndStates() = snap("ds_chat_settings_states") {
        var input by remember { mutableStateOf("") }
        var dark by remember { mutableStateOf(false) }
        Column(Modifier.padding(Dimens.screenPaddingH), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChatBubble(text = "Merhaba, notlar hâlâ duruyor mu?", isOutgoing = false, timeText = "14:02")
            ChatBubble(text = "Evet, yarın kütüphanede verebilirim.", isOutgoing = true, timeText = "14:03", statusText = "İletildi ✓✓")
            TypingIndicator()
            ChatInputBar(value = input, onValueChange = { input = it }, onSend = {})
            SettingsGroup {
                SettingsRow(title = "Hesap Bilgileri", onClick = {})
                SettingsDivider()
                SettingsSwitchRow(title = "Koyu Görünüm", checked = dark, onCheckedChange = { dark = it })
                SettingsDivider()
                SettingsRow(title = "Premium'a Yükselt", style = SettingsRowStyle.Premium, onClick = {})
                SettingsDivider()
                SettingsRow(title = "Hesabı Sil", style = SettingsRowStyle.Danger, onClick = {})
            }
            PlanCard(name = "Free", features = listOf("Topluluklara katılım", "Temel eşleşme"), footer = {
                Text("Mevcut plan", style = MaterialTheme.appText.caption)
            })
            PlanCard(name = "Premium", features = listOf("Sınırsız ilan", "Öncelikli eşleşme"), isFeatured = true, badgeText = "Popüler")
            EmptyStateView(title = "Henüz gönderi yok", message = "İlk gönderiyi sen paylaş.")
            ErrorStateView(message = "Bağlantı kurulamadı.", onRetry = {})
            SkeletonCardList(count = 1)
        }
    }
}
