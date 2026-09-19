package com.kampusagi.android.feature.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.match.MatchCandidate
import com.kampusagi.android.domain.match.MatchRepository
import com.kampusagi.android.domain.match.MatchesResult
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

data class MatchesUiState(
    val result: Loadable<MatchesResult> = Loadable.Loading,
    /** Bu oturumda "geç" denilen adaylar (sunucuda saklanmaz; yenilenince tekrar görünür). */
    val dismissed: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val isStartingChat: Boolean = false,
    val message: UiText? = null,
) {
    val visibleMatches: List<MatchCandidate>
        get() = (result as? Loadable.Success)?.value?.matches.orEmpty().filter { it.matchId !in dismissed }
}

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchesUiState())
    val uiState: StateFlow<MatchesUiState> = _uiState.asStateFlow()

    private val _openChat = Channel<String>(Channel.BUFFERED)

    /** Sohbet başlatılınca sohbet kimliğiyle bir kez yayılır. */
    val openChat: Flow<String> = _openChat.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(result = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(matchRepository.getMatches())
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(result = result) }
        }
    }

    /** Eşleşmeleri sunucuda yeniden hesaplatır (`recompute-matches`) ve listeyi tazeler. */
    fun refresh() {
        val requirement = (_uiState.value.result as? Loadable.Success)?.value?.requirement
        if (requirement == null || _uiState.value.isRefreshing) {
            load()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val refreshed = matchRepository.refreshMatches(requirement.id)
                _uiState.update { it.copy(result = Loadable.Success(refreshed), dismissed = emptySet()) }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun dismiss(match: MatchCandidate) = _uiState.update { it.copy(dismissed = it.dismissed + match.matchId) }

    fun message(match: MatchCandidate) {
        if (_uiState.value.isStartingChat) return
        val requirementId = ((_uiState.value.result as? Loadable.Success)?.value)?.requirement?.id
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingChat = true) }
            try {
                _openChat.send(chatRepository.startConversation(match.userId, requirementId))
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isStartingChat = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
