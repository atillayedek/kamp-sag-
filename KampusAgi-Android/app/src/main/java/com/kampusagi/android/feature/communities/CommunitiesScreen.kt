package com.kampusagi.android.feature.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.kampusagi.android.core.designsystem.component.AppFab
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.SegmentedPicker
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.Post
import java.time.Instant

@Composable
fun CommunitiesScreen(
    onCreatePost: (CommunityScope) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenEvents: () -> Unit,
    viewModel: CommunitiesViewModel = hiltViewModel(),
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

    CommunitiesContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onScopeSelected = viewModel::selectScope,
        onRefresh = { viewModel.refresh() },
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onLikeClick = viewModel::toggleLike,
        onPostClick = { onOpenPost(it.id) },
        onCreatePost = { onCreatePost(uiState.scope) },
        onOpenEvents = onOpenEvents,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunitiesContent(
    state: CommunitiesUiState,
    snackbarHostState: SnackbarHostState,
    onScopeSelected: (CommunityScope) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLikeClick: (Post) -> Unit,
    onPostClick: (Post) -> Unit,
    onCreatePost: () -> Unit,
    onOpenEvents: () -> Unit,
    now: Instant = Instant.now(),
) {
    val colors = MaterialTheme.appColors
    val feed = state.feed
    val listState = rememberLazyListState()

    // Listenin sonuna 3 öğe kala sonraki sayfayı iste.
    LaunchedEffect(listState, feed.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (feed.items.isNotEmpty() && last >= feed.items.size - 3) onLoadMore() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.communities_title),
                actions = {
                    IconButton(onClick = onOpenEvents) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = stringResource(R.string.events_open_cd),
                            tint = MaterialTheme.appColors.label,
                        )
                    }
                },
            )
            SegmentedPicker(
                options = listOf(stringResource(R.string.communities_scope_general), stringResource(R.string.communities_scope_university)),
                selectedIndex = if (state.scope == CommunityScope.GENERAL) 0 else 1,
                onSelect = { onScopeSelected(if (it == 0) CommunityScope.GENERAL else CommunityScope.UNIVERSITY) },
                modifier = Modifier.padding(horizontal = Dimens.screenPaddingH),
            )
            if (state.scope == CommunityScope.UNIVERSITY && !state.universityName.isNullOrBlank()) {
                Text(
                    text = state.universityName,
                    style = MaterialTheme.appText.fieldLabel,
                    modifier = Modifier.padding(horizontal = Dimens.screenPaddingH, vertical = 10.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = feed.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Dimens.screenPaddingH, end = Dimens.screenPaddingH, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    when {
                        feed.isLoading -> item { SkeletonCardList(count = 3) }
                        feed.error != null -> item {
                            ErrorStateView(message = feed.error.toUiText().asString(), onRetry = onRetry)
                        }
                        feed.items.isEmpty() -> item {
                            EmptyStateView(
                                title = stringResource(R.string.communities_empty_title),
                                message = stringResource(R.string.communities_empty_message),
                                icon = Icons.Outlined.Forum,
                            )
                        }
                        else -> {
                            items(feed.items, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    now = now,
                                    onLikeClick = { onLikeClick(post) },
                                    onClick = { onPostClick(post) },
                                    maxBodyLines = 6,
                                )
                            }
                            if (feed.isLoadingMore) item { SkeletonCardList(count = 1) }
                            if (feed.loadMoreError != null) item {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = feed.loadMoreError.toUiText().asString(), style = MaterialTheme.appText.caption)
                                    TextButton(onClick = onLoadMore) {
                                        Text(stringResource(R.string.common_retry), style = MaterialTheme.appText.fieldLabel.copy(color = colors.primary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AppFab(
            onClick = onCreatePost,
            contentDescription = stringResource(R.string.communities_create_post_cd),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Dimens.screenPaddingH, bottom = Dimens.screenPaddingH),
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@LightDarkPreviews
@Composable
private fun CommunitiesPreview() {
    PreviewSurface {
        CommunitiesContent(
            state = CommunitiesUiState(feeds = mapOf(CommunityScope.GENERAL to FeedState(isLoading = false))),
            snackbarHostState = remember { SnackbarHostState() },
            onScopeSelected = {}, onRefresh = {}, onRetry = {}, onLoadMore = {},
            onLikeClick = {}, onPostClick = {}, onCreatePost = {}, onOpenEvents = {},
        )
    }
}
