package com.kampusagi.android.feature.requirement

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.domain.analytics.AnalyticsTracker
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.requirement.NeedAnalysis
import com.kampusagi.android.domain.requirement.REQUIREMENT_TEXT_MAX_LENGTH
import com.kampusagi.android.domain.requirement.RequirementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Metin için AI analizinin durumu; `forText` analizin hangi (kırpılmış) metne ait olduğunu söyler. */
sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Analyzing : AnalysisState
    data class Ready(val analysis: NeedAnalysis, val forText: String) : AnalysisState
    data class Failed(val error: AppError, val forText: String) : AnalysisState
}

data class CreateRequirementUiState(
    val text: String = "",
    val analysis: AnalysisState = AnalysisState.Idle,
    val isPublishing: Boolean = false,
    val message: UiText? = null,
) {
    /** Yalnızca GÜNCEL metne ait, başarılı bir analiz varsa paylaşılabilir. */
    val canPublish: Boolean
        get() {
            val ready = analysis as? AnalysisState.Ready ?: return false
            return !isPublishing && ready.forText == text.trim()
        }
}

@HiltViewModel
class CreateRequirementViewModel @Inject constructor(
    private val requirementRepository: RequirementRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRequirementUiState())
    val uiState: StateFlow<CreateRequirementUiState> = _uiState.asStateFlow()

    private val _published = Channel<Unit>(Channel.BUFFERED)

    /** İlan yayınlanınca bir kez yayılır (ekran başarı mesajı gösterir). */
    val published: Flow<Unit> = _published.receiveAsFlow()

    private var analysisJob: Job? = null

    fun onTextChange(value: String) {
        val text = value.take(REQUIREMENT_TEXT_MAX_LENGTH)
        val trimmed = text.trim()
        val current = _uiState.value.analysis
        val stillValid = current is AnalysisState.Ready && current.forText == trimmed
        _uiState.update { it.copy(text = text, analysis = if (stillValid) current else AnalysisState.Idle) }
        if (stillValid) return

        // Debounce: yazma durunca analiz et. Kısa metinde ve arka arkaya değişimde istek atılmaz (hız sınırı + maliyet).
        analysisJob?.cancel()
        if (trimmed.length < MIN_ANALYZE_LENGTH) return
        analysisJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            analyze(trimmed)
        }
    }

    fun retryAnalysis() {
        val trimmed = _uiState.value.text.trim()
        if (trimmed.length < MIN_ANALYZE_LENGTH) return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch { analyze(trimmed) }
    }

    fun publish() {
        val state = _uiState.value
        val ready = state.analysis as? AnalysisState.Ready ?: return
        if (!state.canPublish) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true) }
            try {
                requirementRepository.publish(ready.forText, ready.analysis.need)
                analytics.track(AnalyticsEvent.REQUIREMENT_PUBLISHED)
                analysisJob?.cancel()
                _uiState.update { CreateRequirementUiState() }
                _published.send(Unit)
            } catch (e: AppError) {
                _uiState.update { it.copy(isPublishing = false, message = e.toUiText()) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun analyze(trimmed: String) {
        _uiState.update { it.copy(analysis = AnalysisState.Analyzing) }
        val result = try {
            AnalysisState.Ready(requirementRepository.analyze(trimmed), forText = trimmed)
        } catch (e: AppError) {
            AnalysisState.Failed(e, forText = trimmed)
        }
        // Bu arada metin değiştiyse (eski analiz) sonuç yok sayılır.
        _uiState.update { state -> if (state.text.trim() == trimmed) state.copy(analysis = result) else state }
    }

    companion object {
        const val MIN_ANALYZE_LENGTH = 10
        const val DEBOUNCE_MS = 900L
    }
}
