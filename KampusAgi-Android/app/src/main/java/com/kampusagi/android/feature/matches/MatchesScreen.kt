package com.kampusagi.android.feature.matches

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Avatar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.MatchScoreBadge
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.match.MatchCandidate
import com.kampusagi.android.domain.match.MatchesResult
import com.kampusagi.android.domain.match.MyRequirement
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MatchesScreen(
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onCreateRequirement: () -> Unit,
    viewModel: MatchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.openChat.collect(onOpenChat) }
    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    MatchesContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onDismiss = viewModel::dismiss,
        onViewProfile = { onOpenProfile(it.userId) },
        onMessage = viewModel::message,
        onCreateRequirement = onCreateRequirement,
    )
}

@Composable
internal fun MatchesContent(
    state: MatchesUiState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: (MatchCandidate) -> Unit,
    onViewProfile: (MatchCandidate) -> Unit,
    onMessage: (MatchCandidate) -> Unit,
    onCreateRequirement: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = stringResource(R.string.matches_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
        ) {
            when (val result = state.result) {
                Loadable.Loading -> SkeletonCardList(count = 1)
                is Loadable.Failure -> ErrorStateView(message = result.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> {
                    val matches = state.visibleMatches
                    when {
                        result.value.requirement == null -> EmptyStateView(
                            title = stringResource(R.string.matches_no_requirement_title),
                            message = stringResource(R.string.matches_no_requirement_message),
                            icon = Icons.Outlined.FavoriteBorder,
                            actionLabel = stringResource(R.string.requirement_title),
                            onAction = onCreateRequirement,
                        )
                        matches.isEmpty() -> EmptyStateView(
                            title = stringResource(R.string.matches_empty_title),
                            message = stringResource(R.string.matches_empty_message),
                            icon = Icons.Outlined.FavoriteBorder,
                            actionLabel = stringResource(R.string.matches_refresh),
                            onAction = onRefresh,
                        )
                        else -> MatchStack(matches, state.isStartingChat, onDismiss, onViewProfile, onMessage)
                    }
                }
            }
        }
    }
}

/** Üst üste kart yığını: arkadaki kartların kenarı görünür; öndeki kart yatay kaydırılarak geçilir. */
@Composable
private fun MatchStack(
    matches: List<MatchCandidate>,
    isStartingChat: Boolean,
    onDismiss: (MatchCandidate) -> Unit,
    onViewProfile: (MatchCandidate) -> Unit,
    onMessage: (MatchCandidate) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        // Arkadaki en fazla 2 kart (ters sırada çizilir, en uzak en altta).
        matches.drop(1).take(2).reversed().forEachIndexed { reversedIndex, card ->
            val depth = matches.drop(1).take(2).size - reversedIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = (12 * depth).dp, start = (8 * depth).dp, end = (8 * depth).dp)
                    .graphicsLayer { alpha = 1f - 0.25f * depth },
            ) {
                MatchCard(card, isStartingChat, {}, {}, interactive = false)
            }
        }
        SwipeableTopCard(matches.first(), isStartingChat, onDismiss, onViewProfile, onMessage)
    }
}

@Composable
private fun SwipeableTopCard(
    match: MatchCandidate,
    isStartingChat: Boolean,
    onDismiss: (MatchCandidate) -> Unit,
    onViewProfile: (MatchCandidate) -> Unit,
    onMessage: (MatchCandidate) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(match.matchId) { Animatable(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = offsetX.value / 40f }
            .pointerInput(match.matchId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > DISMISS_THRESHOLD) {
                                offsetX.animateTo(if (offsetX.value > 0) 1200f else -1200f)
                                onDismiss(match)
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                ) { _, dragAmount -> scope.launch { offsetX.snapTo(offsetX.value + dragAmount) } }
            },
    ) {
        MatchCard(match, isStartingChat, { onViewProfile(match) }, { onMessage(match) }, interactive = true)
    }
}

private const val DISMISS_THRESHOLD = 280f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(
    match: MatchCandidate,
    isStartingChat: Boolean,
    onViewProfile: () -> Unit,
    onMessage: () -> Unit,
    interactive: Boolean,
) {
    val text = MaterialTheme.appText
    AppCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(name = match.fullName, imageUrl = match.avatarUrl, size = Dimens.avatarLarge)
            Text(text = match.fullName, style = text.titleMedium)
            match.department?.takeIf { it.isNotBlank() }?.let { Text(text = it, style = text.bodyCenter) }
            MatchScoreBadge(score = match.score, isSemantic = match.isSemantic)
        }
        if (match.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { match.tags.forEach { Chip(text = it) } }
        }
        if (interactive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = stringResource(R.string.matches_view_profile), onClick = onViewProfile, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(R.string.matches_send_message),
                    onClick = onMessage,
                    isLoading = isStartingChat,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // Arkadaki kartlar yalnızca görsel derinlik içindir; düğmeleri yerinde tutmak için boşluk bırakılır.
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 25.dp))
        }
    }
}

@LightDarkPreviews
@Composable
private fun MatchesPreview() {
    PreviewSurface {
        MatchesContent(
            state = MatchesUiState(
                result = Loadable.Success(
                    MatchesResult(
                        MyRequirement("r1", "Basketbol"),
                        listOf(
                            MatchCandidate("m1", "u1", "Ayşe Nur", "Bilgisayar Mühendisliği", null, 88, false, listOf("basketbol", "voleybol")),
                            MatchCandidate("m2", "u2", "Mehmet Kaya", "Makine Mühendisliği", null, 71, false, emptyList()),
                        ),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onRetry = {}, onRefresh = {}, onDismiss = {}, onViewProfile = {}, onMessage = {}, onCreateRequirement = {},
        )
    }
}
