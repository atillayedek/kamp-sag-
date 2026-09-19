package com.kampusagi.android.feature.verification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiDangerTextButton
import com.kampusagi.android.core.designsystem.KampusAgiPrimaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.core.designsystem.KampusAgiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingReviewScreen(viewModel: VerificationStatusViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PendingReviewScreenContent(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        onLogout = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingReviewScreenContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(KampusAgiSpacing.screenMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Text(text = "⏳", fontSize = 56.sp)
                Text(
                    text = stringResource(R.string.pending_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.pending_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))

                KampusAgiPrimaryButton(
                    text = stringResource(R.string.pending_refresh_button),
                    onClick = onRefresh,
                    isLoading = isRefreshing,
                )
                KampusAgiDangerTextButton(text = stringResource(R.string.logout_button), onClick = onLogout)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingReviewScreenPreview() {
    KampusAgiTheme {
        PendingReviewScreenContent(isRefreshing = false, onRefresh = {}, onLogout = {})
    }
}
