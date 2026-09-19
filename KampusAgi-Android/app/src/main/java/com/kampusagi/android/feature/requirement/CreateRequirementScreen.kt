package com.kampusagi.android.feature.requirement

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AIAnalysisCard
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.designsystem.component.ChipStyle
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonView
import com.kampusagi.android.core.time.formatDateTime
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.requirement.AnalysisSource
import com.kampusagi.android.domain.requirement.HelpType
import com.kampusagi.android.domain.requirement.NeedAnalysis
import com.kampusagi.android.domain.requirement.NeedUrgency
import com.kampusagi.android.domain.requirement.ParsedNeed
import com.kampusagi.android.domain.requirement.REQUIREMENT_TEXT_MAX_LENGTH
import com.kampusagi.android.feature.communities.labelRes
import java.time.Instant

@StringRes
internal fun HelpType.labelRes(): Int =
    if (isOffer) R.string.need_type_offer else R.string.need_type_need

@StringRes
internal fun NeedUrgency.labelRes(): Int? = when (this) {
    NeedUrgency.HIGH -> R.string.need_urgency_high
    NeedUrgency.URGENT -> R.string.need_urgency_urgent
    else -> null
}

@Composable
fun CreateRequirementScreen(viewModel: CreateRequirementViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val publishedMessage = stringResource(R.string.requirement_published)

    LaunchedEffect(Unit) {
        viewModel.published.collect { snackbarHostState.showSnackbar(publishedMessage) }
    }
    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    CreateRequirementContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onTextChange = viewModel::onTextChange,
        onRetryAnalysis = viewModel::retryAnalysis,
        onPublish = viewModel::publish,
    )
}

@Composable
internal fun CreateRequirementContent(
    state: CreateRequirementUiState,
    snackbarHostState: SnackbarHostState,
    onTextChange: (String) -> Unit,
    onRetryAnalysis: () -> Unit,
    onPublish: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppTopBar(title = stringResource(R.string.requirement_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            LabeledField(
                label = stringResource(R.string.requirement_input_label),
                value = state.text,
                onValueChange = onTextChange,
                supportingText = "${state.text.length}/$REQUIREMENT_TEXT_MAX_LENGTH",
                singleLine = false,
                minLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            AIAnalysisCard(title = stringResource(R.string.requirement_ai_title)) {
                AnalysisContent(state.analysis, onRetryAnalysis)
                Text(text = stringResource(R.string.requirement_ai_description), style = MaterialTheme.appText.caption)
            }

            PrimaryButton(
                text = stringResource(R.string.requirement_publish),
                onClick = onPublish,
                enabled = state.canPublish || state.isPublishing,
                isLoading = state.isPublishing,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisContent(analysis: AnalysisState, onRetry: () -> Unit) {
    val text = MaterialTheme.appText
    val context = LocalContext.current
    when (analysis) {
        AnalysisState.Idle -> Text(text = stringResource(R.string.requirement_ai_idle), style = text.caption)
        AnalysisState.Analyzing -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonView(modifier = Modifier.width(110.dp), height = 26.dp, cornerRadius = 13.dp)
            SkeletonView(modifier = Modifier.width(80.dp), height = 26.dp, cornerRadius = 13.dp)
            SkeletonView(modifier = Modifier.width(64.dp), height = 26.dp, cornerRadius = 13.dp)
        }
        is AnalysisState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = analysis.error.toUiText().asString(), style = text.caption.copy(color = MaterialTheme.appColors.rejected))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry), style = text.fieldLabel.copy(color = MaterialTheme.appColors.primary))
            }
        }
        is AnalysisState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val need = analysis.analysis.need
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(text = stringResource(need.helpType.labelRes()), style = ChipStyle.Need)
                Chip(text = stringResource(need.category.labelRes()))
                need.startsAt?.let { Chip(text = formatDateTime(context, it)) }
                need.urgency.labelRes()?.let { Chip(text = stringResource(it)) }
                need.participantCount?.let { Chip(text = stringResource(R.string.need_participants_format, it)) }
                need.tags.forEach { Chip(text = it) }
            }
            if (analysis.analysis.source == AnalysisSource.BASIC) {
                Text(text = stringResource(R.string.requirement_ai_basic_notice), style = text.caption)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun CreateRequirementPreview() {
    PreviewSurface {
        CreateRequirementContent(
            state = CreateRequirementUiState(
                text = "Bu hafta sonu 4 kişilik basketbol maçı için oyuncu arıyorum",
                analysis = AnalysisState.Ready(
                    NeedAnalysis(
                        ParsedNeed("Basketbol maçı", PostCategory.SPORTS, HelpType.LOOKING_FOR_PEOPLE, listOf("basketbol"), 4, NeedUrgency.NORMAL, Instant.parse("2026-09-26T15:00:00Z"), emptyList()),
                        AnalysisSource.AI,
                    ),
                    forText = "Bu hafta sonu 4 kişilik basketbol maçı için oyuncu arıyorum",
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTextChange = {}, onRetryAnalysis = {}, onPublish = {},
        )
    }
}
