package com.kampusagi.android.feature.auth.register.steps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.SecondaryButton
import com.kampusagi.android.feature.auth.register.RegisterStep
import com.kampusagi.android.feature.auth.register.RegisterUiState

/** Adım 6 — yalnızca PDF öğrenci belgesi seç ve başvuruyu gönder. */
@Composable
internal fun DocumentStep(
    state: RegisterUiState,
    onPickDocument: () -> Unit,
    onClearDocument: () -> Unit,
    onShowHelp: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val colors = MaterialTheme.appColors
    WizardStepLayout(
        step = RegisterStep.STUDENT_DOCUMENT,
        stepTitle = stringResource(R.string.register_document_step_title),
        heading = stringResource(R.string.register_document_upload_title),
        headingAction = {
            IconButton(onClick = { onShowHelp(true) }, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.register_document_help_title),
                    tint = colors.label2,
                )
            }
        },
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_document_submit_button),
                onContinue = onSubmit,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = onBack,
            )
        },
    ) {
        if (state.documentName != null) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = colors.primary)
                    Text(
                        text = state.documentName,
                        style = MaterialTheme.appText.body,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = onClearDocument, enabled = !state.isLoading, modifier = Modifier.size(Dimens.minTouchTarget)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.register_document_remove_cd),
                            tint = colors.label2,
                        )
                    }
                }
            }
        } else {
            SecondaryButton(text = stringResource(R.string.register_document_pick_button), onClick = onPickDocument)
        }

        Text(text = stringResource(R.string.register_document_hint), style = MaterialTheme.appText.caption)
        if (state.isLoading) {
            Text(text = stringResource(R.string.register_document_uploading), style = MaterialTheme.appText.caption)
        }
    }

    if (state.isHelpVisible) {
        AlertDialog(
            onDismissRequest = { onShowHelp(false) },
            title = { Text(stringResource(R.string.register_document_help_title), style = MaterialTheme.appText.titleMedium) },
            text = { Text(stringResource(R.string.register_document_help_body), style = MaterialTheme.appText.body) },
            confirmButton = {
                TextButton(onClick = { onShowHelp(false) }) {
                    Text(stringResource(R.string.common_ok), style = MaterialTheme.appText.fieldLabel.copy(color = colors.primary))
                }
            },
            containerColor = colors.card,
        )
    }
}

@LightDarkPreviews
@Composable
private fun DocumentStepPreview() {
    PreviewSurface {
        DocumentStep(
            state = RegisterUiState(step = RegisterStep.STUDENT_DOCUMENT, documentName = "belge.pdf"),
            onPickDocument = {}, onClearDocument = {}, onShowHelp = {}, onSubmit = {}, onBack = {},
        )
    }
}
