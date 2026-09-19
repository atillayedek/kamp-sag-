package com.kampusagi.android.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.kampusagi.android.core.designsystem.component.ChatBubble
import com.kampusagi.android.core.designsystem.component.ChatInputBar
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.designsystem.component.TypingIndicator
import com.kampusagi.android.core.time.formatClockTime
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.chat.ChatPeer
import java.time.Instant

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
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
    ChatContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetryLoad = viewModel::load,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onRetryMessage = viewModel::retry,
        onLoadOlder = viewModel::loadOlder,
    )
}

@Composable
internal fun ChatContent(
    state: ChatUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val peer = (state.peer as? Loadable.Success)?.value
    Scaffold(
        containerColor = colors.appBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = peer?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.conversations_title),
                onBack = onBack,
                subtitle = if (state.isPeerOnline) ({ OnlineIndicator() }) else null,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (peer != null) {
                Box(modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    ChatInputBar(value = state.input, onValueChange = onInputChange, onSend = onSend)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val loaded = state.peer) {
                Loadable.Loading -> Box(modifier = Modifier.padding(Dimens.screenPaddingH, Dimens.sectionSpacing)) { SkeletonCardList(count = 3) }
                is Loadable.Failure -> ErrorStateView(message = loaded.error.toUiText().asString(), onRetry = onRetryLoad)
                is Loadable.Success -> MessageList(state, onRetryMessage, onLoadOlder)
            }
        }
    }
}

@Composable
private fun OnlineIndicator() {
    val colors = MaterialTheme.appColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).background(colors.approved, CircleShape))
        Text(text = stringResource(R.string.chat_online), style = MaterialTheme.appText.receipt.copy(color = colors.approved))
    }
}

@Composable
private fun MessageList(state: ChatUiState, onRetryMessage: (String) -> Unit, onLoadOlder: () -> Unit) {
    if (state.items.isEmpty() && !state.canLoadOlder) {
        EmptyStateView(
            title = stringResource(R.string.chat_empty_title),
            message = stringResource(R.string.chat_empty_message),
            icon = Icons.AutoMirrored.Filled.Chat,
        )
        return
    }

    val listState = rememberLazyListState()
    val newestId = state.items.lastOrNull()?.id
    // Yeni mesaj gelince, kullanıcı en altın yakınındaysa (veya mesajı kendisi gönderdiyse) listeyi aşağı kaydır.
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    // `reverseLayout`: en yeni mesaj en altta; öğeler yeniden eskiye doğru verilir.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = Dimens.screenPaddingH, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isPeerTyping) {
            item(key = "typing") { TypingIndicator() }
        }
        items(state.items.asReversed(), key = { it.id }) { item ->
            val status = when {
                item.state == DeliveryState.FAILED -> stringResource(R.string.chat_status_failed)
                item.state == DeliveryState.SENDING -> stringResource(R.string.chat_status_sending)
                item.isMine && item.id == state.latestMineId ->
                    stringResource(if (item.state == DeliveryState.READ) R.string.chat_status_read else R.string.chat_status_sent)
                else -> null
            }
            ChatBubble(
                text = item.body,
                isOutgoing = item.isMine,
                timeText = formatClockTime(item.createdAt),
                statusText = status,
                modifier = if (item.state == DeliveryState.FAILED) {
                    Modifier.clickable(role = Role.Button) { onRetryMessage(item.id) }
                } else {
                    Modifier
                },
            )
        }
        if (state.canLoadOlder) {
            item(key = "older") {
                LaunchedEffect(Unit) { onLoadOlder() }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SecondaryButton(
                        text = stringResource(if (state.isLoadingOlder) R.string.common_loading else R.string.chat_load_older),
                        onClick = onLoadOlder,
                    )
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun ChatPreview() {
    val t0 = Instant.parse("2026-09-19T11:02:00Z")
    val items = listOf(
        ChatItem("1", "Merhaba, notlar hâlâ duruyor mu?", isMine = false, createdAt = t0, state = DeliveryState.SENT),
        ChatItem("2", "Evet, yarın kütüphanede verebilirim.", isMine = true, createdAt = t0.plusSeconds(60), state = DeliveryState.READ),
    )
    PreviewSurface {
        ChatContent(
            state = ChatUiState(
                peer = Loadable.Success(ChatPeer("u1", "Ayşe Nur", null, t0.plusSeconds(120))),
                items = items,
                isPeerOnline = true,
                isPeerTyping = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetryLoad = {}, onInputChange = {}, onSend = {}, onRetryMessage = {}, onLoadOlder = {},
        )
    }
}
