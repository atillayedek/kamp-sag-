package com.kampusagi.android.feature.communities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.community.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreatePostUiState(
    val title: String = "",
    val body: String = "",
    val category: PostCategory? = null,
    val isSubmitting: Boolean = false,
    val message: UiText? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            title.trim().length in 1..MAX_TITLE &&
            body.trim().length in 1..MAX_BODY &&
            category != null

    companion object {
        const val MAX_TITLE = 200
        const val MAX_BODY = 5000
    }
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    private val postEvents: PostEvents,
) : ViewModel() {

    private val scope = CommunityScope.valueOf(savedStateHandle.get<String>("scope") ?: CommunityScope.GENERAL.name)

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private val _created = Channel<Unit>(Channel.BUFFERED)

    /** Gönderi başarıyla paylaşılınca bir kez yayılır (ekran kapanır). */
    val created: Flow<Unit> = _created.receiveAsFlow()

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value.take(CreatePostUiState.MAX_TITLE)) }
    fun onBodyChange(value: String) = _uiState.update { it.copy(body = value.take(CreatePostUiState.MAX_BODY)) }
    fun onCategorySelected(category: PostCategory) = _uiState.update { it.copy(category = category) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun submit() {
        val state = _uiState.value
        val category = state.category ?: return
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                postRepository.createPost(scope, state.title, state.body, category)
                postEvents.notifyChanged()
                _created.send(Unit)
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
