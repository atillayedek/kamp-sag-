package com.kampusagi.android.feature.communities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.Comment
import com.kampusagi.android.domain.community.Post
import com.kampusagi.android.domain.community.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDetail(val post: Post, val comments: List<Comment>)

data class PostDetailUiState(
    val detail: Loadable<PostDetail> = Loadable.Loading,
    val commentDraft: String = "",
    val isSendingComment: Boolean = false,
    val message: UiText? = null,
) {
    val canSend: Boolean get() = commentDraft.isNotBlank() && !isSendingComment && detail is Loadable.Success
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    private val postEvents: PostEvents,
) : ViewModel() {

    private val postId: String = requireNotNull(savedStateHandle.get<String>("postId")) { "postId gerekli" }

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var likeInFlight = false

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(detail = Loadable.Loading) }
        viewModelScope.launch {
            try {
                // coroutineScope: bir çocuk başarısız olunca istisna BURADAKİ try/catch'e gelir
                // (düz async üst coroutine'e yayılıp uygulamayı çökertirdi).
                val detail = coroutineScope {
                    val post = async { postRepository.getPost(postId) }
                    val comments = async { postRepository.getComments(postId) }
                    PostDetail(post.await(), comments.await())
                }
                _uiState.update { it.copy(detail = Loadable.Success(detail)) }
            } catch (e: AppError) {
                _uiState.update { it.copy(detail = Loadable.Failure(e)) }
            }
        }
    }

    fun onCommentDraftChange(value: String) = _uiState.update { it.copy(commentDraft = value.take(MAX_COMMENT)) }

    fun sendComment() {
        val state = _uiState.value
        if (!state.canSend) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingComment = true) }
            try {
                val comment = postRepository.addComment(postId, state.commentDraft)
                _uiState.update { current ->
                    val detail = (current.detail as? Loadable.Success)?.value
                    current.copy(
                        commentDraft = "",
                        detail = if (detail == null) current.detail else Loadable.Success(
                            detail.copy(
                                post = detail.post.copy(commentCount = detail.post.commentCount + 1),
                                comments = detail.comments + comment,
                            ),
                        ),
                    )
                }
                postEvents.notifyChanged()
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isSendingComment = false) }
            }
        }
    }

    fun toggleLike() {
        val detail = (_uiState.value.detail as? Loadable.Success)?.value ?: return
        if (likeInFlight) return
        likeInFlight = true
        val target = !detail.post.likedByMe
        updatePost { it.withLiked(target) }
        viewModelScope.launch {
            try {
                postRepository.setLiked(postId, target)
                postEvents.notifyChanged()
            } catch (e: AppError) {
                updatePost { it.withLiked(!target) }
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                likeInFlight = false
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun updatePost(transform: (Post) -> Post) {
        _uiState.update { state ->
            val detail = (state.detail as? Loadable.Success)?.value ?: return@update state
            state.copy(detail = Loadable.Success(detail.copy(post = transform(detail.post))))
        }
    }

    companion object {
        const val MAX_COMMENT = 2000
    }
}
