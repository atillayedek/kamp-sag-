package com.kampusagi.android.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText

private fun bubbleShape(isOutgoing: Boolean) = RoundedCornerShape(
    topStart = Dimens.bubbleRadius,
    topEnd = Dimens.bubbleRadius,
    bottomStart = if (isOutgoing) Dimens.bubbleRadius else Dimens.bubbleTailRadius,
    bottomEnd = if (isOutgoing) Dimens.bubbleTailRadius else Dimens.bubbleRadius,
)

/** Gelen (`appBg2`) / giden (`primary`) balon; köşe 18pt, kuyruk tarafı 6pt. */
@Composable
fun ChatBubble(
    text: String,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    timeText: String? = null,
    statusText: String? = null,
) {
    val colors = MaterialTheme.appColors
    val appText = MaterialTheme.appText
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.78f
        Column(
            modifier = Modifier.align(if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = text,
                style = appText.body.copy(color = if (isOutgoing) colors.primaryContrast else colors.label),
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(bubbleShape(isOutgoing))
                    .background(if (isOutgoing) colors.primary else colors.appBg2)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            val meta = listOfNotNull(timeText, statusText).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(text = meta, style = appText.receipt, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
}

/** Karşı taraf yazıyor göstergesi (3 nokta, kademeli animasyon). */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val transition = rememberInfiniteTransition(label = "typing")
    val description = stringResource(R.string.chat_typing_cd)
    Row(
        modifier = modifier
            .clip(bubbleShape(isOutgoing = false))
            .background(colors.appBg2)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(colors.label2, CircleShape),
            )
        }
    }
}

/** Capsule mesaj yazma çubuğu; sağda 32pt gönder butonu (dokunma alanı 44dp). */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.chat_input_placeholder),
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    val text = MaterialTheme.appText
    val canSend = enabled && value.isNotBlank()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.appBg2)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = placeholder },
            enabled = enabled,
            textStyle = text.body,
            cursorBrush = SolidColor(colors.primary),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(text = placeholder, style = text.body.copy(color = colors.label2))
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(CircleShape)
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.sendButton)
                    .clip(CircleShape)
                    .background(if (canSend) colors.primary else colors.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.chat_send_cd),
                    tint = colors.primaryContrast,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun ChatPreview() {
    PreviewSurface {
        var input by remember { mutableStateOf("") }
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChatBubble(text = "Merhaba, notlar hâlâ duruyor mu?", isOutgoing = false, timeText = "14:02")
            ChatBubble(text = "Evet, yarın kütüphanede verebilirim.", isOutgoing = true, timeText = "14:03", statusText = "İletildi ✓✓")
            TypingIndicator()
            ChatInputBar(value = input, onValueChange = { input = it }, onSend = {})
        }
    }
}
