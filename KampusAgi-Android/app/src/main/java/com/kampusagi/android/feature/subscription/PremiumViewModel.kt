package com.kampusagi.android.feature.subscription

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.domain.analytics.AnalyticsTracker
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.R
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.core.ui.uiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.PurchaseResult
import com.kampusagi.android.domain.subscription.SubscriptionPlan
import com.kampusagi.android.domain.subscription.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(
    val overview: Loadable<PlansOverview> = Loadable.Loading,
    /** Satın alma akışı süren plan (o kartın düğmesi yükleniyor gösterir, diğerleri kilitlenir). */
    val purchasingTier: PlanTier? = null,
    val message: UiText? = null,
) {
    /** Ücretli bir plan yayında değilse (sunucuda etkin değil) kullanıcıya "yakında" notu gösterilir. */
    val hasPaidPlans: Boolean
        get() = (overview as? Loadable.Success)?.value?.plans?.any { it.tier != PlanTier.FREE } == true
}

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(overview = Loadable.Loading) }
        viewModelScope.launch {
            try {
                val overview = repository.getOverview()
                _uiState.update { it.copy(overview = Loadable.Success(overview)) }
                restore(overview)
            } catch (e: AppError) {
                _uiState.update { it.copy(overview = Loadable.Failure(e)) }
            }
        }
    }

    /**
     * Play hesabında zaten var olan abonelik (yenileme, yeni cihaz, yarım kalan doğrulama) sunucuya doğrulatılır ve
     * geçerli plan güncellenir. Plan listesi zaten gösterildiği için hata yalnızca mesaj olarak bildirilir.
     */
    private suspend fun restore(overview: PlansOverview) {
        try {
            val restored = repository.restorePurchases() ?: return
            if (restored != overview.currentTier) {
                _uiState.update { state -> state.copy(overview = Loadable.Success(overview.copy(currentTier = restored))) }
            }
        } catch (e: AppError) {
            _uiState.update { it.copy(message = e.toUiText()) }
        }
    }

    /** `activity` yalnızca Play satın alma penceresini açmak için çağrı boyunca kullanılır; saklanmaz. */
    fun purchase(activity: Activity, plan: SubscriptionPlan) {
        if (_uiState.value.purchasingTier != null || !plan.isPurchasable) return
        _uiState.update { it.copy(purchasingTier = plan.tier) }
        viewModelScope.launch {
            try {
                when (val result = repository.purchase(activity, plan)) {
                    is PurchaseResult.Success -> {
                        analytics.track(AnalyticsEvent.PURCHASE_COMPLETED)
                        _uiState.update { state ->
                            val success = (state.overview as? Loadable.Success)?.value
                            state.copy(
                                overview = if (success != null) Loadable.Success(success.copy(currentTier = result.tier)) else state.overview,
                                message = uiText(R.string.premium_purchase_success),
                            )
                        }
                    }
                    PurchaseResult.Pending -> _uiState.update { it.copy(message = uiText(R.string.premium_purchase_pending)) }
                    PurchaseResult.Canceled -> Unit
                }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(purchasingTier = null) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
