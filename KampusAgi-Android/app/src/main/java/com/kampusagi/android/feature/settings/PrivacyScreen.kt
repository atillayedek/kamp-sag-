package com.kampusagi.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.kampusagi.android.core.designsystem.component.SettingsGroup
import com.kampusagi.android.core.designsystem.component.SettingsSwitchRow

/**
 * Gizlilik özeti: hangi veri toplanıyor, kimler görüyor, nasıl silinir. Konum verisi toplanmaz.
 * Tek ayar: kullanım istatistiği paylaşımı (yalnızca olay adı; kapatılınca hiçbir şey gönderilmez).
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
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
    PrivacyContent(uiState, snackbarHostState, onBack, viewModel::onAnalyticsChange)
}

@Composable
internal fun PrivacyContent(
    state: PrivacyUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.privacy_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            PrivacySection(R.string.privacy_location_heading, R.string.privacy_location_body)
            PrivacySection(R.string.privacy_data_heading, R.string.privacy_data_body)
            PrivacySection(R.string.privacy_visibility_heading, R.string.privacy_visibility_body)
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_analytics_title),
                    checked = state.analyticsEnabled,
                    onCheckedChange = onAnalyticsChange,
                )
            }
            Text(text = stringResource(R.string.privacy_analytics_body), style = MaterialTheme.appText.caption)
            PrivacySection(R.string.privacy_delete_heading, R.string.privacy_delete_body)
        }
    }
}

@Composable
private fun PrivacySection(headingRes: Int, bodyRes: Int) {
    AppCard {
        Text(
            text = stringResource(headingRes),
            style = MaterialTheme.appText.reasonLabel,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(bodyRes), style = MaterialTheme.appText.body, modifier = Modifier.padding(top = 6.dp))
    }
}

@LightDarkPreviews
@Composable
private fun PrivacyPreview() {
    PreviewSurface {
        PrivacyContent(PrivacyUiState(), remember { SnackbarHostState() }, onBack = {}, onAnalyticsChange = {})
    }
}
