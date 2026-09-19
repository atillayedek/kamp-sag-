package com.kampusagi.android.feature.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
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
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.TextDangerButton

@Composable
fun PendingReviewScreen(viewModel: VerificationStatusViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    PendingReviewScreenContent(
        isRefreshing = uiState.isRefreshing,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onLogout = viewModel::signOut,
    )
}

@Composable
internal fun PendingReviewScreenContent(
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Scaffold(containerColor = colors.appBg, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = colors.pending,
                    modifier = Modifier.size(64.dp),
                )
                Text(text = stringResource(R.string.pending_title), style = MaterialTheme.appText.titleLarge)
                Text(text = stringResource(R.string.pending_message), style = MaterialTheme.appText.bodyCenter)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.pending_refresh_button),
                    onClick = onRefresh,
                    isLoading = isRefreshing,
                )
                TextDangerButton(text = stringResource(R.string.logout_button), onClick = onLogout)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun PendingReviewScreenPreview() {
    PreviewSurface {
        PendingReviewScreenContent(
            isRefreshing = false,
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onLogout = {},
        )
    }
}
