package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

/** `appBg2` zeminli, aktif segmenti `card` + hafif gölgeli segmented control. */
@Composable
fun SegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val outerShape = RoundedCornerShape(Dimens.segmentedRadius)
    val innerShape = RoundedCornerShape(Dimens.segmentedRadius - Dimens.segmentedPadding)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(colors.appBg2)
            .padding(Dimens.segmentedPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.minTouchTarget - 8.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, innerShape) else Modifier)
                    .clip(innerShape)
                    .background(if (isSelected) colors.card else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.appText.chip.copy(
                        color = if (isSelected) colors.label else colors.label2,
                    ),
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun SegmentedPickerPreview() {
    PreviewSurface {
        SegmentedPicker(
            options = listOf("Genel", "Üniversitem"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
