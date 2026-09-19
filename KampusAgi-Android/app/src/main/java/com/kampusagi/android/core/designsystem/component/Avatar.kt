package com.kampusagi.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import java.util.Locale

private val TurkishLocale: Locale = Locale.forLanguageTag("tr-TR")

/** "ayşe nur yılmaz" -> "AY"; tek kelime -> tek harf; boş -> "?". Türkçe büyük harf kuralıyla (i -> İ). */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words.first().first().toString()
    val last = if (words.size > 1) words.last().first().toString() else ""
    return (first + last).uppercase(TurkishLocale)
}

/** Fotoğrafı yoksa `primary` zemin üzerinde baş harfler; varsa fotoğraf üstte (önbellekli) yüklenir. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.avatarList,
    imageUrl: String? = null,
) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primary)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = TextStyle(
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryContrast,
            ),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun AvatarPreview() {
    PreviewSurface {
        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = "Ayşe Nur")
            Avatar(name = "İlker", size = Dimens.avatarLarge)
        }
    }
}
