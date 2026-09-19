package com.kampusagi.android.feature.auth.register.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.LabeledField
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.feature.auth.register.RegisterStep
import com.kampusagi.android.feature.auth.register.RegisterUiState

/** Adım 3 — gerçek `universities` tablosundan seçim (arama + liste; yükleniyor/boş/hata durumlarıyla). */
@Composable
internal fun UniversityStep(
    state: RegisterUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (University) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?,
) {
    WizardStepLayout(
        step = RegisterStep.UNIVERSITY,
        stepTitle = stringResource(R.string.register_step_university_title),
        heading = stringResource(R.string.register_university_heading),
        bottomBar = {
            WizardBottomBar(
                continueText = stringResource(R.string.register_continue_button),
                onContinue = onContinue,
                canContinue = state.canContinue,
                isLoading = state.isLoading,
                onBack = onBack,
            )
        },
    ) {
        LabeledField(
            label = stringResource(R.string.register_university_search_label),
            value = state.universityQuery,
            onValueChange = onQueryChange,
            trailing = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.appColors.label2)
            },
        )
        when (val universities = state.universities) {
            Loadable.Loading -> SkeletonCardList(count = 3)
            is Loadable.Failure -> ErrorStateView(message = universities.error.toUiText().asString(), onRetry = onRetry)
            is Loadable.Success -> {
                val visible = state.filteredUniversities
                if (visible.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.register_university_empty_title),
                        message = stringResource(R.string.register_university_empty_message),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { university ->
                            UniversityRow(
                                university = university,
                                isSelected = university.id == state.selectedUniversity?.id,
                                onClick = { onSelect(university) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversityRow(university: University, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    val selectedLabel = stringResource(R.string.register_university_selected_cd)
    AppCard(
        modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        border = BorderStroke(if (isSelected) 1.5.dp else Dimens.cardBorderWidth, if (isSelected) colors.primary else colors.cardBorder),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = university.name, style = MaterialTheme.appText.body)
                Text(text = university.city, style = MaterialTheme.appText.caption)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = selectedLabel,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
