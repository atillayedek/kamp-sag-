package com.kampusagi.android.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Avatar
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.time.formatRelativeTime
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.chat.ConversationSummary
import java.time.Instant

@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpenConversation: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Sohbetten geri dönünce okunmamış sayaçları güncel olsun.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ConversationsContent(uiState, viewModel::load, onOpenConversation)
}

@Composable
internal fun ConversationsContent(
    state: ConversationsUiState,
    onRetry: () -> Unit,
    onOpenConversation: (String) -> Unit,
    now: Instant = Instant.now(),
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = stringResource(R.string.conversations_title)) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val conversations = state.conversations) {
                Loadable.Loading -> Column(modifier = Modifier.padding(Dimens.screenPaddingH, Dimens.sectionSpacing)) {
                    SkeletonCardList(count = 4)
                }
                is Loadable.Failure -> ErrorStateView(message = conversations.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> if (conversations.value.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.conversations_empty_title),
                        message = stringResource(R.string.conversations_empty_message),
                        icon = Icons.AutoMirrored.Filled.Chat,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(conversations.value, key = { it.id }) { conversation ->
                            ConversationRow(conversation, now) { onOpenConversation(conversation.id) }
                            HorizontalDivider(color = MaterialTheme.appColors.divider, modifier = Modifier.padding(start = Dimens.screenPaddingH + Dimens.avatarList + 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummary, now: Instant, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    val text = MaterialTheme.appText
    val context = LocalContext.current
    val hasUnread = conversation.unreadCount > 0
    val preview = conversation.lastMessage?.let {
        if (conversation.lastMessageFromMe) context.getString(R.string.conversations_mine_prefix, it) else it
    } ?: stringResource(R.string.conversations_no_messages)
    val unreadDescription = if (hasUnread) stringResource(R.string.conversations_unread_cd, conversation.unreadCount) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.screenPaddingH, vertical = 12.dp)
            .semantics(mergeDescendants = true) { unreadDescription?.let { contentDescription = "${conversation.otherName}. $preview. $it" } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(name = conversation.otherName, imageUrl = conversation.otherAvatarUrl)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = conversation.otherName, style = text.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = preview,
                style = if (hasUnread) text.body else text.bodyCenter,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            conversation.lastMessageAt?.let { Text(text = formatRelativeTime(context, it, now), style = text.receipt) }
            if (hasUnread) {
                Box(
                    modifier = Modifier.size(20.dp).background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                        style = text.receipt.copy(color = colors.primaryContrast),
                    )
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun ConversationsPreview() {
    val now = remember { Instant.parse("2026-09-19T15:00:00Z") }
    PreviewSurface {
        ConversationsContent(
            state = ConversationsUiState(
                Loadable.Success(
                    listOf(
                        ConversationSummary("c1", "u1", "Ayşe Nur", null, "Notlar hâlâ duruyor mu?", now.minusSeconds(600), false, 2),
                        ConversationSummary("c2", "u2", "Mehmet Kaya", null, "Yarın görüşürüz", now.minusSeconds(7200), true, 0),
                    ),
                ),
            ),
            onRetry = {},
            onOpenConversation = {},
            now = now,
        )
    }
}
