package com.kampusagi.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.settings.NotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class NotificationSettingsUiState(
    val newMessage: Loadable<Boolean> = Loadable.Loading,
    val message: UiText? = null,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) : ViewModel() {

    /** Hızlı aç-kapa sırasında kayıtlar gönderildikleri sırayla uygulanır (son yazan sunucuda kalır). */
    private val saveLock = Mutex()

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(newMessage = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(repository.isNewMessageEnabled())
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(newMessage = result) }
        }
    }

    /** İyimser güncelleme: anahtar hemen döner; kayıt başarısız olursa eski değere geri alınır ve hata gösterilir. */
    fun onNewMessageChange(enabled: Boolean) {
        val previous = (_uiState.value.newMessage as? Loadable.Success)?.value ?: return
        if (previous == enabled) return
        _uiState.update { it.copy(newMessage = Loadable.Success(enabled)) }
        viewModelScope.launch {
            try {
                saveLock.withLock { repository.setNewMessageEnabled(enabled) }
            } catch (e: AppError) {
                _uiState.update { it.copy(newMessage = Loadable.Success(previous), message = e.toUiText()) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
