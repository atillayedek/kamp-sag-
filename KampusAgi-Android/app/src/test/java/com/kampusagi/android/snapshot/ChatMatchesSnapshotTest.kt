package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.chat.ChatPeer
import com.kampusagi.android.domain.chat.ConversationSummary
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.MatchCandidate
import com.kampusagi.android.domain.match.MatchesResult
import com.kampusagi.android.domain.match.MyRequirement
import com.kampusagi.android.domain.match.PublicProfile
import com.kampusagi.android.feature.chat.ChatContent
import com.kampusagi.android.feature.chat.ChatItem
import com.kampusagi.android.feature.chat.ChatUiState
import com.kampusagi.android.feature.chat.ConversationsContent
import com.kampusagi.android.feature.chat.ConversationsUiState
import com.kampusagi.android.feature.chat.DeliveryState
import com.kampusagi.android.feature.matches.MatchesContent
import com.kampusagi.android.feature.matches.MatchesUiState
import com.kampusagi.android.feature.matches.UserProfileContent
import com.kampusagi.android.feature.matches.UserProfileUiState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class ChatMatchesSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-09-19T15:00:00Z")
    private lateinit var previousTimeZone: TimeZone

    /** Balon saatleri yerel saat diliminde yazılır; snapshot'lar makineden bağımsız olsun diye UTC sabitlenir. */
    @Before
    fun pinTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(previousTimeZone)

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    private val candidates = listOf(
        MatchCandidate("m1", "u1", "Ayşe Nur", "Bilgisayar Mühendisliği", null, 88, true, listOf("basketbol", "voleybol", "spor")),
        MatchCandidate("m2", "u2", "Mehmet Kaya", "Makine Mühendisliği", null, 71, false, listOf("basketbol")),
        MatchCandidate("m3", "u3", "Zeynep Arslan", "Endüstri Mühendisliği", null, 64, false, emptyList()),
    )

    @Composable
    private fun matches(state: MatchesUiState) = MatchesContent(
        state = state, snackbarHostState = remember { SnackbarHostState() },
        onRetry = {}, onRefresh = {}, onDismiss = {}, onViewProfile = {}, onMessage = {}, onCreateRequirement = {},
    )

    @Test
    fun matchesCardStack() = snap("matches_stack") {
        matches(MatchesUiState(result = Loadable.Success(MatchesResult(MyRequirement("r1", "Basketbol"), candidates))))
    }

    @Test
    fun matchesEmpty() = snap("matches_empty") {
        matches(MatchesUiState(result = Loadable.Success(MatchesResult(MyRequirement("r1", "Basketbol"), emptyList()))))
    }

    @Test
    fun matchesNoRequirement() = snap("matches_no_requirement") {
        matches(MatchesUiState(result = Loadable.Success(MatchesResult(null, emptyList()))))
    }

    @Test
    fun matchesError() = snap("matches_error") {
        matches(MatchesUiState(result = Loadable.Failure(AppError.Network())))
    }

    @Test
    fun userProfile() = snap("user_profile") {
        UserProfileContent(
            state = UserProfileUiState(
                profile = Loadable.Success(
                    PublicProfile("u1", "Ayşe Nur", "Bilgisayar Mühendisliği", "Test Üniversitesi", null, "Basketbol için oyuncu arıyorum", listOf("basketbol", "voleybol")),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onMessage = {},
        )
    }

    @Composable
    private fun conversations(state: ConversationsUiState) =
        ConversationsContent(state = state, onRetry = {}, onOpenConversation = {}, now = now)

    @Test
    fun conversationsList() = snap("conversations_list") {
        conversations(
            ConversationsUiState(
                Loadable.Success(
                    listOf(
                        ConversationSummary("c1", "u1", "Ayşe Nur", null, "Notlar hâlâ duruyor mu?", now.minusSeconds(600), false, 2),
                        ConversationSummary("c2", "u2", "Mehmet Kaya", null, "Yarın kütüphanede görüşürüz", now.minusSeconds(7200), true, 0),
                        ConversationSummary("c3", "u3", "Zeynep Arslan", null, null, null, false, 0),
                    ),
                ),
            ),
        )
    }

    @Test
    fun conversationsEmpty() = snap("conversations_empty") {
        conversations(ConversationsUiState(Loadable.Success(emptyList())))
    }

    private val peer = ChatPeer("u1", "Ayşe Nur", null, null)

    @Composable
    private fun chat(state: ChatUiState) = ChatContent(
        state = state, snackbarHostState = remember { SnackbarHostState() },
        onBack = {}, onRetryLoad = {}, onInputChange = {}, onSend = {}, onRetryMessage = {}, onLoadOlder = {},
    )

    private fun item(id: String, body: String, mine: Boolean, minute: Int, state: DeliveryState = DeliveryState.SENT) =
        ChatItem(id, body, mine, Instant.parse("2026-09-19T11:%02d:00Z".format(minute)), state)

    @Test
    fun chatThreadWithReadReceiptTypingAndOnline() = snap("chat_thread") {
        chat(
            ChatUiState(
                peer = Loadable.Success(peer),
                items = listOf(
                    item("1", "Merhaba, veri yapıları notları hâlâ duruyor mu?", mine = false, minute = 2),
                    item("2", "Evet, yarın kütüphanede verebilirim.", mine = true, minute = 3),
                    item("3", "Süper, saat kaçta uygun olur?", mine = false, minute = 4),
                    item("4", "14:00 gibi olur mu?", mine = true, minute = 5, state = DeliveryState.READ),
                ),
                isPeerOnline = true,
                isPeerTyping = true,
            ),
        )
    }

    @Test
    fun chatWithSendingAndFailedMessages() = snap("chat_pending") {
        chat(
            ChatUiState(
                peer = Loadable.Success(peer),
                items = listOf(
                    item("1", "Merhaba!", mine = false, minute = 2),
                    item("2", "Bu mesaj gönderilemedi", mine = true, minute = 3, state = DeliveryState.FAILED),
                    item("3", "Bu mesaj gönderiliyor", mine = true, minute = 4, state = DeliveryState.SENDING),
                ),
                input = "Yeni bir mesaj",
            ),
        )
    }

    @Test
    fun chatEmpty() = snap("chat_empty") {
        chat(ChatUiState(peer = Loadable.Success(peer)))
    }

    @Test
    fun chatLoadError() = snap("chat_error") {
        chat(ChatUiState(peer = Loadable.Failure(AppError.Network())))
    }
}
