package com.kampusagi.android.feature.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.designsystem.component.ChipStyle
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.domain.community.PostCategory

@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    viewModel: CreatePostViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.created.collect { onBack() } }

    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    CreatePostContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTitleChange = viewModel::onTitleChange,
        onBodyChange = viewModel::onBodyChange,
        onCategorySelected = viewModel::onCategorySelected,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreatePostContent(
    state: CreatePostUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onCategorySelected: (PostCategory) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.create_post_title), onBack = onBack) },
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
                label = stringResource(R.string.create_post_title_label),
                value = state.title,
                onValueChange = onTitleChange,
                supportingText = "${state.title.length}/${CreatePostUiState.MAX_TITLE}",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            LabeledField(
                label = stringResource(R.string.create_post_body_label),
                value = state.body,
                onValueChange = onBodyChange,
                supportingText = "${state.body.length}/${CreatePostUiState.MAX_BODY}",
                singleLine = false,
                minLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.create_post_category_label), style = MaterialTheme.appText.fieldLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostCategory.entries.forEach { category ->
                        Chip(
                            text = stringResource(category.labelRes()),
                            style = if (state.category == category) ChipStyle.Selected else ChipStyle.Default,
                            onClick = { onCategorySelected(category) },
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.create_post_submit),
                onClick = onSubmit,
                enabled = state.canSubmit || state.isSubmitting,
                isLoading = state.isSubmitting,
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun CreatePostPreview() {
    PreviewSurface {
        CreatePostContent(
            state = CreatePostUiState(title = "Ders notu", body = "Notlara ihtiyacım var.", category = PostCategory.ACADEMIC),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onTitleChange = {}, onBodyChange = {}, onCategorySelected = {}, onSubmit = {},
        )
    }
}
