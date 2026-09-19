package com.kampusagi.android.feature.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.kampusagi.android.core.designsystem.component.ChatInputBar
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.time.formatRelativeTime
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.community.Comment
import java.time.Instant

@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel(),
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

    PostDetailContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onLikeClick = viewModel::toggleLike,
        onCommentDraftChange = viewModel::onCommentDraftChange,
        onSendComment = viewModel::sendComment,
    )
}

@Composable
internal fun PostDetailContent(
    state: PostDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentDraftChange: (String) -> Unit,
    onSendComment: () -> Unit,
    now: Instant = Instant.now(),
) {
    val colors = MaterialTheme.appColors
    Scaffold(
        containerColor = colors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.post_detail_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(modifier = Modifier.weight(1f)) {
                when (val detail = state.detail) {
                    Loadable.Loading -> Column(Modifier.padding(Dimens.screenPaddingH)) { SkeletonCardList(count = 2) }
                    is Loadable.Failure -> ErrorStateView(message = detail.error.toUiText().asString(), onRetry = onRetry)
                    is Loadable.Success -> LazyColumn(
                        contentPadding = PaddingValues(Dimens.screenPaddingH),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                    ) {
                        item { PostCard(post = detail.value.post, now = now, onLikeClick = onLikeClick) }
                        item {
                            Text(
                                text = stringResource(R.string.post_comments_heading),
                                style = MaterialTheme.appText.fieldLabel,
                            )
                        }
                        if (detail.value.comments.isEmpty()) {
                            item { Text(text = stringResource(R.string.post_comments_empty), style = MaterialTheme.appText.caption) }
                        } else {
                            items(detail.value.comments, key = { it.id }) { comment -> CommentRow(comment, now) }
                        }
                    }
                }
            }
            if (state.detail is Loadable.Success) {
                ChatInputBar(
                    value = state.commentDraft,
                    onValueChange = onCommentDraftChange,
                    onSend = onSendComment,
                    placeholder = stringResource(R.string.post_comment_placeholder),
                    enabled = !state.isSendingComment,
                    modifier = Modifier.padding(horizontal = Dimens.screenPaddingH, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, now: Instant) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(name = comment.authorName, imageUrl = comment.authorAvatarUrl, size = 32.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = comment.authorName, style = MaterialTheme.appText.body.copy(fontWeight = FontWeight.SemiBold))
                Text(text = formatRelativeTime(context, comment.createdAt, now), style = MaterialTheme.appText.receipt)
            }
            Text(text = comment.body, style = MaterialTheme.appText.body)
        }
    }
}

@LightDarkPreviews
@Composable
private fun PostDetailPreview() {
    PreviewSurface {
        PostDetailContent(
            state = PostDetailUiState(detail = Loadable.Loading),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onLikeClick = {}, onCommentDraftChange = {}, onSendComment = {},
        )
    }
}
