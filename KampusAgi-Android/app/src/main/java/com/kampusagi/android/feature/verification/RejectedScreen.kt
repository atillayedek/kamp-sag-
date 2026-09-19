package com.kampusagi.android.feature.verification

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiDangerTextButton
import com.kampusagi.android.core.designsystem.KampusAgiSecondaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.core.designsystem.KampusAgiTheme

@Composable
fun RejectedScreen(
    reason: String?,
    viewModel: VerificationStatusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::resubmitDocument) }

    RejectedScreenContent(
        reason = uiState.rejectionReason ?: reason,
        isResubmitting = uiState.isResubmitting,
        snackbarHostState = snackbarHostState,
        onPickDocument = { documentPickerLauncher.launch(arrayOf("application/pdf")) },
        onLogout = viewModel::signOut,
    )
}

@Composable
private fun RejectedScreenContent(
    reason: String?,
    isResubmitting: Boolean,
    snackbarHostState: SnackbarHostState,
    onPickDocument: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(KampusAgiSpacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "❌", fontSize = 56.sp)
            Text(
                text = stringResource(R.string.rejected_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = KampusAgiSpacing.itemSpacing),
            )

            OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.rejected_reason_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = reason ?: stringResource(R.string.rejected_default_reason),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = KampusAgiSpacing.itemSpacing))

            Text(
                text = stringResource(R.string.rejected_retry_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.padding(top = KampusAgiSpacing.itemSpacing))

            KampusAgiSecondaryButton(
                text = stringResource(R.string.register_document_pick_button),
                onClick = onPickDocument,
                enabled = !isResubmitting,
            )
            KampusAgiDangerTextButton(text = stringResource(R.string.logout_button), onClick = onLogout)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RejectedScreenPreview() {
    KampusAgiTheme {
        RejectedScreenContent(
            reason = "Belge okunamıyor, geçersiz veya güncel değil.",
            isResubmitting = false,
            snackbarHostState = remember { SnackbarHostState() },
            onPickDocument = {},
            onLogout = {},
        )
    }
}
