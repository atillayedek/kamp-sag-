package com.kampusagi.android.feature.matches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.match.PublicProfile

@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
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

    UserProfileContent(uiState, snackbarHostState, onBack, viewModel::load, viewModel::message)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserProfileContent(
    state: UserProfileUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMessage: () -> Unit,
) {
    val text = MaterialTheme.appText
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.user_profile_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            when (val profile = state.profile) {
                Loadable.Loading -> SkeletonCardList(count = 1)
                is Loadable.Failure -> ErrorStateView(message = profile.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> {
                    val p = profile.value
                    Avatar(name = p.fullName, imageUrl = p.avatarUrl, size = Dimens.avatarLarge)
                    Text(text = p.fullName, style = text.titleMedium)
                    val subtitle = listOfNotNull(p.department?.takeIf { it.isNotBlank() }, p.universityName).joinToString(" · ")
                    if (subtitle.isNotEmpty()) Text(text = subtitle, style = text.bodyCenter)
                    p.latestRequirementTitle?.let { title ->
                        AppCard {
                            Text(text = stringResource(R.string.user_profile_latest_requirement), style = text.reasonLabel)
                            Text(text = title, style = text.body, modifier = Modifier.padding(top = 4.dp))
                            if (p.tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                                ) { p.tags.forEach { Chip(text = it) } }
                            }
                        }
                    }
                    PrimaryButton(
                        text = stringResource(R.string.matches_send_message),
                        onClick = onMessage,
                        isLoading = state.isStartingChat,
                    )
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun UserProfilePreview() {
    PreviewSurface {
        UserProfileContent(
            state = UserProfileUiState(
                profile = Loadable.Success(
                    PublicProfile("u1", "Ayşe Nur", "Bilgisayar Mühendisliği", "Test Üniversitesi", null, "Basketbol için oyuncu arıyorum", listOf("basketbol")),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onMessage = {},
        )
    }
}
