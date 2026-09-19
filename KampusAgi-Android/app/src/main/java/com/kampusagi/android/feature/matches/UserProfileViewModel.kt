package com.kampusagi.android.feature.matches

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.MatchRepository
import com.kampusagi.android.domain.match.PublicProfile
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

data class UserProfileUiState(
    val profile: Loadable<PublicProfile> = Loadable.Loading,
    val isStartingChat: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matchRepository: MatchRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val userId: String = requireNotNull(savedStateHandle.get<String>("userId")) { "userId gerekli" }

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _openChat = Channel<String>(Channel.BUFFERED)
    val openChat: Flow<String> = _openChat.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(profile = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(matchRepository.getPublicProfile(userId))
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(profile = result) }
        }
    }

    fun message() {
        if (_uiState.value.isStartingChat) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingChat = true) }
            try {
                _openChat.send(chatRepository.startConversation(userId, requirementId = null))
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isStartingChat = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
