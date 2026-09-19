package com.kampusagi.android.feature.communities

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppCard
import com.kampusagi.android.core.designsystem.component.Avatar
import com.kampusagi.android.core.designsystem.component.Chip
import com.kampusagi.android.core.time.formatRelativeTime
import com.kampusagi.android.domain.community.Post
import com.kampusagi.android.domain.community.PostCategory
import java.time.Instant

@StringRes
internal fun PostCategory.labelRes(): Int = when (this) {
    PostCategory.SPORTS -> R.string.category_sports
    PostCategory.ACADEMIC -> R.string.category_academic
    PostCategory.SOCIAL -> R.string.category_social
    PostCategory.HOUSING -> R.string.category_housing
    PostCategory.TRANSPORT -> R.string.category_transport
    PostCategory.OTHER -> R.string.category_other
}

/** Gönderi kartı: avatar, ad, göreli zaman, metin, ♥ beğeni ve 💬 yorum sayısı. */
@Composable
internal fun PostCard(
    post: Post,
    now: Instant,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    maxBodyLines: Int = Int.MAX_VALUE,
) {
    val colors = MaterialTheme.appColors
    val text = MaterialTheme.appText
    val context = LocalContext.current
    val meta = listOfNotNull(
        post.authorDepartment?.takeIf { it.isNotBlank() },
        post.authorUniversityShortName?.takeIf { it.isNotBlank() },
        formatRelativeTime(context, post.createdAt, now),
    ).joinToString(" · ")

    AppCard(modifier = modifier, onClick = onClick, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = post.authorName, imageUrl = post.authorAvatarUrl)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = post.authorName,
                    style = text.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = meta, style = text.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(text = post.title, style = text.body.copy(fontWeight = FontWeight.Bold))
        Text(text = post.body, style = text.body, maxLines = maxBodyLines, overflow = TextOverflow.Ellipsis)
        Chip(text = stringResource(post.category.labelRes()))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val likeDescription = stringResource(
                if (post.likedByMe) R.string.post_unlike_cd else R.string.post_like_cd,
                post.likeCount,
            )
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onLikeClick)
                    .semantics { contentDescription = likeDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (post.likedByMe) colors.primary else colors.label2,
                    modifier = Modifier.size(22.dp),
                )
                Text(text = post.likeCount.toString(), style = text.caption)
            }
            val commentDescription = stringResource(R.string.post_comments_cd, post.commentCount)
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.minTouchTarget)
                    .semantics { contentDescription = commentDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = colors.label2, modifier = Modifier.size(22.dp))
                Text(text = post.commentCount.toString(), style = text.caption)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun PostCardPreview() {
    PreviewSurface {
        val now = Instant.parse("2026-09-19T12:00:00Z")
        PostCard(
            post = Post(
                id = "1", authorId = "a", authorName = "Ayşe Nur", authorDepartment = "Bilgisayar Müh.",
                authorUniversityShortName = "TÜ", authorAvatarUrl = null, title = "Ders notu arıyorum",
                body = "Veri yapıları dersinin notlarına ihtiyacım var.", category = PostCategory.ACADEMIC,
                createdAt = now.minusSeconds(7200), likeCount = 3, commentCount = 1, likedByMe = true,
            ),
            now = now,
            onLikeClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
