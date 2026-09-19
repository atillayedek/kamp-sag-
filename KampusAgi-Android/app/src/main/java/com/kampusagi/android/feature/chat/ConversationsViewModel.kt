package com.kampusagi.android.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.chat.ConversationSummary
import com.kampusagi.android.domain.common.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationsUiState(
    val conversations: Loadable<List<ConversationSummary>> = Loadable.Loading,
) {
    /** Alt çubuktaki Sohbet rozeti: tüm sohbetlerdeki okunmamış mesaj toplamı. */
    val unreadTotal: Int
        get() = (conversations as? Loadable.Success)?.value?.sumOf { it.unreadCount } ?: 0
}

/**
 * Gelen kutusu: sohbet listesi + okunmamış sayacı. Ana ekran düzeyinde yaşar; hem Sohbet sekmesi hem de
 * alt çubuk rozeti aynı örneği kullanır. Yeni mesaj gelince (Realtime) ve ekran her öne geldiğinde tazelenir.
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            // Akış hatada kendiliğinden sonlanır (repository yakalar); canlı güncelleme kesilirse `refresh` kurtarır.
            chatRepository.observeInbox().collect { fetch(showLoading = false) }
        }
    }

    fun load() {
        _uiState.update { it.copy(conversations = Loadable.Loading) }
        viewModelScope.launch { fetch(showLoading = true) }
    }

    /** Sessiz tazeleme (ekran öne geldiğinde / sohbetten dönünce): mevcut liste ekranda kalır. */
    fun refresh() {
        viewModelScope.launch { fetch(showLoading = false) }
    }

    private suspend fun fetch(showLoading: Boolean) {
        try {
            val list = chatRepository.getConversations()
            _uiState.update { it.copy(conversations = Loadable.Success(list)) }
        } catch (e: AppError) {
            // Sessiz tazelemede mevcut listeyi hata ekranıyla ezme; yalnızca ilk yükleme başarısızsa hata göster.
            if (showLoading || _uiState.value.conversations !is Loadable.Success) {
                _uiState.update { it.copy(conversations = Loadable.Failure(e)) }
            }
        }
    }
}
