package com.kampusagi.android.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.designsystem.component.ChipStyle
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.time.formatDateTime
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.events.CampusEvent
import java.time.Instant

@Composable
fun EventsScreen(
    onBack: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
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
    EventsContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onToggleAttendance = viewModel::toggleAttendance,
    )
}

@Composable
internal fun EventsContent(
    state: EventsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onToggleAttendance: (CampusEvent) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.events_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                when (val events = state.events) {
                    Loadable.Loading -> item { SkeletonCardList(count = 3) }
                    is Loadable.Failure -> item { ErrorStateView(message = events.error.toUiText().asString(), onRetry = onRetry) }
                    is Loadable.Success -> if (events.value.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = stringResource(R.string.events_empty_title),
                                message = stringResource(R.string.events_empty_message),
                                icon = Icons.Outlined.Event,
                            )
                        }
                    } else {
                        items(events.value, key = { it.id }) { event ->
                            EventCard(event, isPending = event.id in state.pendingEventIds) { onToggleAttendance(event) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CampusEvent, isPending: Boolean, onToggle: () -> Unit) {
    val text = MaterialTheme.appText
    val colors = MaterialTheme.appColors
    val context = LocalContext.current
    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(
            text = stringResource(if (event.isUniversityEvent) R.string.communities_scope_university else R.string.communities_scope_general),
            style = ChipStyle.Need,
        )
        Text(text = event.title, style = text.titleMedium)
        MetaRow(Icons.Outlined.CalendarMonth, formatDateTime(context, event.startsAt))
        MetaRow(Icons.Outlined.Place, event.location)
        if (event.description.isNotBlank()) Text(text = event.description, style = text.body)
        Text(text = stringResource(R.string.events_attendees_format, event.attendeeCount), style = text.caption.copy(color = colors.label2))
        if (event.isJoined) {
            SecondaryButton(text = stringResource(R.string.events_joined), onClick = onToggle, enabled = !isPending)
        } else {
            PrimaryButton(text = stringResource(R.string.events_join), onClick = onToggle, enabled = !isPending, isLoading = isPending)
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.appColors.label2, modifier = Modifier.size(18.dp))
        Text(text = value, style = MaterialTheme.appText.body)
    }
}

@LightDarkPreviews
@Composable
private fun EventsPreview() {
    val start = Instant.parse("2026-09-26T15:00:00Z")
    PreviewSurface {
        EventsContent(
            state = EventsUiState(
                Loadable.Success(
                    listOf(
                        CampusEvent("1", "Python Başlangıç Atölyesi", "Sıfırdan Python öğrenmek isteyenler için 3 saatlik atölye.", "Bilişim Amfi 2", start, null, true, 5, true),
                        CampusEvent("2", "Kampüs Buluşması", "", "Merkez Yemekhane", start.plusSeconds(86_400), null, false, 0, false),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onRefresh = {}, onToggleAttendance = {},
        )
    }
}
