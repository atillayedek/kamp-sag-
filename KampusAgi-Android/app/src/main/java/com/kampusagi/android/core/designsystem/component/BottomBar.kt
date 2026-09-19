package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

class BottomBarItem(val label: String, val icon: ImageVector)

/** Alt gezinme çubuğu: seçili öğe `primary`, diğerleri `label2`; üstte `cardBorder` ayırıcı. */
@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Column(modifier = modifier, verticalArrangement = Arrangement.Top) {
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        NavigationBar(containerColor = colors.appBg, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.appText.receipt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = colors.label2,
                        unselectedTextColor = colors.label2,
                        indicatorColor = colors.iconBadgeBg,
                    ),
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun BottomBarPreview() {
    PreviewSurface {
        AppBottomBar(
            items = listOf(
                BottomBarItem("Topluluklar", Icons.Filled.Groups),
                BottomBarItem("Eşleşmeler", Icons.Filled.Favorite),
                BottomBarItem("İhtiyaç", Icons.Filled.AddCircle),
                BottomBarItem("Sohbet", Icons.AutoMirrored.Filled.Chat),
                BottomBarItem("Profil", Icons.Filled.Person),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
