package com.kampusagi.android.feature.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.LightDarkPreviews
import com.kampusagi.android.core.designsystem.PreviewSurface
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.PlanCard
import com.kampusagi.android.core.designsystem.component.PrimaryButton
import com.kampusagi.android.core.designsystem.component.SkeletonCardList
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.subscription.BillingPeriod
import com.kampusagi.android.domain.subscription.BillingUnit
import com.kampusagi.android.domain.subscription.PlanPrice
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.SubscriptionPlan

@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = uiState.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }
    val activity = LocalContext.current.findActivity()
    PremiumContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::load,
        onUpgrade = { plan -> activity?.let { viewModel.purchase(it, plan) } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun PremiumContent(
    state: PremiumUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpgrade: (SubscriptionPlan) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        topBar = { AppTopBar(title = stringResource(R.string.premium_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPaddingH, vertical = Dimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            when (val overview = state.overview) {
                Loadable.Loading -> SkeletonCardList(count = 3)
                is Loadable.Failure -> ErrorStateView(message = overview.error.toUiText().asString(), onRetry = onRetry)
                is Loadable.Success -> {
                    overview.value.plans.forEach { plan ->
                        PlanItem(plan, overview.value, state.purchasingTier, onUpgrade)
                    }
                    Text(
                        text = stringResource(if (state.hasPaidPlans) R.string.premium_terms else R.string.premium_more_soon),
                        style = MaterialTheme.appText.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanItem(
    plan: SubscriptionPlan,
    overview: PlansOverview,
    purchasingTier: PlanTier?,
    onUpgrade: (SubscriptionPlan) -> Unit,
) {
    val caption = MaterialTheme.appText.caption
    PlanCard(
        name = plan.title,
        features = plan.features,
        priceText = plan.price?.asText(),
        isFeatured = plan.isFeatured,
        badgeText = if (plan.isFeatured) stringResource(R.string.plan_popular) else null,
        footer = when {
            plan.tier == overview.currentTier -> ({ Text(text = stringResource(R.string.plan_current), style = caption) })
            plan.tier == PlanTier.FREE -> null
            else -> ({
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryButton(
                        text = stringResource(upgradeLabel(plan.tier)),
                        onClick = { onUpgrade(plan) },
                        enabled = plan.isPurchasable && purchasingTier == null,
                        isLoading = purchasingTier == plan.tier,
                    )
                    if (!plan.isPurchasable) Text(text = stringResource(R.string.plan_unavailable), style = caption)
                }
            })
        },
    )
}

private fun upgradeLabel(tier: PlanTier): Int = when (tier) {
    PlanTier.COMMUNITY_PRO -> R.string.plan_upgrade_community_pro
    else -> R.string.plan_upgrade_premium
}

/** "₺49,99 / ay" — fiyat Play'den yerelleştirilmiş gelir; dönem yalnızca tanınıyorsa yazılır. */
@Composable
private fun PlanPrice.asText(): String {
    val period = period ?: return formattedPrice
    val unit = stringResource(period.unit.labelRes())
    return if (period.count == 1) {
        stringResource(R.string.plan_price_per, formattedPrice, unit)
    } else {
        stringResource(R.string.plan_price_multi, formattedPrice, period.count, unit)
    }
}

private fun BillingUnit.labelRes(): Int = when (this) {
    BillingUnit.DAY -> R.string.plan_period_day
    BillingUnit.WEEK -> R.string.plan_period_week
    BillingUnit.MONTH -> R.string.plan_period_month
    BillingUnit.YEAR -> R.string.plan_period_year
}

@LightDarkPreviews
@Composable
private fun PremiumPreview() {
    PreviewSurface {
        PremiumContent(
            state = PremiumUiState(
                overview = Loadable.Success(
                    PlansOverview(
                        plans = listOf(
                            SubscriptionPlan(PlanTier.FREE, "Free", listOf("Topluluklara katıl", "Eşleşmeleri gör"), false, null, null),
                            SubscriptionPlan(
                                PlanTier.PREMIUM, "Premium", listOf("Öncelikli eşleşme"), true, "kampusagi_premium",
                                PlanPrice("₺49,99", BillingPeriod(1, BillingUnit.MONTH)),
                            ),
                            SubscriptionPlan(PlanTier.COMMUNITY_PRO, "Community Pro", listOf("Topluluk yönetimi"), false, "kampusagi_community_pro", null),
                        ),
                        currentTier = PlanTier.FREE,
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onRetry = {}, onUpgrade = {},
        )
    }
}
