package com.kampusagi.android.feature.verification

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
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
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.core.designsystem.component.TextDangerButton

/** `reason` moderatörün veritabanına yazdığı red gerekçesidir (sabit metin değil); yoksa nötr bir açıklama gösterilir. */
@Composable
fun RejectedScreen(
    reason: String?,
    viewModel: VerificationStatusViewModel = hiltViewModel(),
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

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.resubmitDocument(uri)
    }

    RejectedScreenContent(
        reason = reason,
        isResubmitting = uiState.isResubmitting,
        snackbarHostState = snackbarHostState,
        onPickDocument = { documentPicker.launch(arrayOf("application/pdf")) },
        onLogout = viewModel::signOut,
    )
}

@Composable
internal fun RejectedScreenContent(
    reason: String?,
    isResubmitting: Boolean,
    snackbarHostState: SnackbarHostState,
    onPickDocument: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = colors.rejected,
                modifier = Modifier.size(64.dp),
            )
            Text(text = stringResource(R.string.rejected_title), style = MaterialTheme.appText.titleLarge)

            AppCard {
                Text(text = stringResource(R.string.rejected_reason_label), style = MaterialTheme.appText.reasonLabel)
                Text(
                    text = reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rejected_default_reason),
                    style = MaterialTheme.appText.body,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(text = stringResource(R.string.rejected_retry_hint), style = MaterialTheme.appText.bodyCenter)

            if (isResubmitting) {
                PrimaryButton(
                    text = stringResource(R.string.register_document_pick_button),
                    onClick = {},
                    isLoading = true,
                )
            } else {
                SecondaryButton(text = stringResource(R.string.register_document_pick_button), onClick = onPickDocument)
            }
            TextDangerButton(text = stringResource(R.string.logout_button), onClick = onLogout)
        }
    }
}

@LightDarkPreviews
@Composable
private fun RejectedScreenPreview() {
    PreviewSurface {
        RejectedScreenContent(
            reason = "Belge okunamıyor.",
            isResubmitting = false,
            snackbarHostState = remember { SnackbarHostState() },
            onPickDocument = {},
            onLogout = {},
        )
    }
}
