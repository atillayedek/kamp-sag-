package com.kampusagi.android.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.events.CampusEvent
import com.kampusagi.android.domain.events.EventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val events: Loadable<List<CampusEvent>> = Loadable.Loading,
    val isRefreshing: Boolean = false,
    /** Katılım isteği süren etkinlikler (kartın düğmesi kilitlenir, çift dokunuş yok sayılır). */
    val pendingEventIds: Set<String> = emptySet(),
    val message: UiText? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(events = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Success(repository.getUpcoming())
            } catch (e: AppError) {
                Loadable.Failure(e)
            }
            _uiState.update { it.copy(events = result) }
        }
    }

    /** Aşağı çekip yenileme: mevcut liste ekranda kalır; hata olursa mesaj gösterilir. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val fresh = repository.getUpcoming()
                _uiState.update { it.copy(events = Loadable.Success(fresh)) }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** İyimser katıl/ayrıl: kart hemen güncellenir, sunucu reddederse eski haline döner ve hata gösterilir. */
    fun toggleAttendance(event: CampusEvent) {
        if (event.id in _uiState.value.pendingEventIds) return
        val target = !event.isJoined
        _uiState.update {
            it.copy(pendingEventIds = it.pendingEventIds + event.id).replaceEvent(event.id) { e -> e.withAttending(target) }
        }
        viewModelScope.launch {
            try {
                repository.setAttending(event.id, target)
            } catch (e: AppError) {
                _uiState.update { it.replaceEvent(event.id) { current -> current.withAttending(!target) }.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(pendingEventIds = it.pendingEventIds - event.id) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}

private fun EventsUiState.replaceEvent(id: String, transform: (CampusEvent) -> CampusEvent): EventsUiState {
    val current = (events as? Loadable.Success)?.value ?: return this
    return copy(events = Loadable.Success(current.map { if (it.id == id) transform(it) else it }))
}
