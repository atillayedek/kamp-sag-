package com.kampusagi.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.AppTopBar

/** Salt okunur gizlilik özeti: hangi veri toplanıyor, kimler görüyor, nasıl silinir. Konum verisi toplanmaz. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.privacy_title), onBack = onBack) },
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
    PreviewSurface { PrivacyScreen(onBack = {}) }
}
