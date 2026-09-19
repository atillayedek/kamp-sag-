package com.kampusagi.android.feature.auth.register.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiPrimaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSecondaryButton
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.core.designsystem.KampusAgiStepIndicator
import com.kampusagi.android.feature.auth.register.RegisterStep

@Composable
fun DocumentStep(
    isLoading: Boolean,
    selectedDocumentName: String?,
    isHelpSheetVisible: Boolean,
    onToggleHelpSheet: (Boolean) -> Unit,
    onPickDocument: () -> Unit,
    onClearDocument: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(KampusAgiSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(KampusAgiSpacing.itemSpacing),
    ) {
        KampusAgiStepIndicator(
            currentStep = RegisterStep.STUDENT_DOCUMENT.stepNumber,
            totalSteps = RegisterStep.TOTAL_STEPS,
            label = stringResource(
                R.string.register_step_format,
                RegisterStep.STUDENT_DOCUMENT.stepNumber,
                RegisterStep.TOTAL_STEPS,
                stringResource(R.string.register_document_step_title),
            ),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.register_document_upload_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onToggleHelpSheet(true) }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = stringResource(R.string.register_document_help_title))
            }
        }

        if (selectedDocumentName != null) {
            OutlinedCard(shape = MaterialTheme.shapes.large) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(selectedDocumentName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearDocument) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.register_document_remove_cd))
                    }
                }
            }
        } else {
            KampusAgiSecondaryButton(
                text = stringResource(R.string.register_document_pick_button),
                onClick = onPickDocument,
            )
        }

        Text(
            text = stringResource(R.string.register_document_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                KampusAgiSecondaryButton(text = stringResource(R.string.register_back_button), onClick = onBack)
            }
            Box(modifier = Modifier.weight(1.4f)) {
                KampusAgiPrimaryButton(
                    text = stringResource(R.string.register_document_submit_button),
                    onClick = onSubmit,
                    enabled = canSubmit,
                    isLoading = isLoading,
                )
            }
        }
    }

    if (isHelpSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onToggleHelpSheet(false) },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.padding(KampusAgiSpacing.screenMargin)) {
                Text(stringResource(R.string.register_document_help_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.register_document_help_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(bottom = KampusAgiSpacing.itemSpacing))
            }
        }
    }
}
