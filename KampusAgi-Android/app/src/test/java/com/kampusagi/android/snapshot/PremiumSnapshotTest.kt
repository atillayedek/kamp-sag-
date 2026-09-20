package com.kampusagi.android.snapshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.kampusagi.android.core.designsystem.KampusAgiTheme
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.SubscriptionPlan
import com.kampusagi.android.feature.subscription.PremiumContent
import com.kampusagi.android.feature.subscription.PremiumUiState
import com.kampusagi.android.testutil.freePlan
import com.kampusagi.android.testutil.monthlyPrice
import com.kampusagi.android.testutil.premiumPlan
import com.kampusagi.android.testutil.proPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = SNAPSHOT_QUALIFIERS)
class PremiumSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun overview(current: PlanTier, vararg plans: SubscriptionPlan) =
        Loadable.Success(PlansOverview(plans.toList(), current))

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { SideBySide(height = 1100.dp, content = content) }
        composeRule.onRoot().captureRoboImage("$SNAPSHOT_DIR/$name.png")
    }

    @Composable
    private fun premium(state: PremiumUiState, onUpgrade: (SubscriptionPlan) -> Unit = {}) = PremiumContent(
        state = state, snackbarHostState = remember { SnackbarHostState() }, onBack = {}, onRetry = {}, onUpgrade = onUpgrade,
    )

    @Test
    fun threePlansFreeIsCurrent() = snap("premium_plans") {
        premium(PremiumUiState(overview(PlanTier.FREE, freePlan(), premiumPlan(), proPlan())))
    }

    @Test
    fun premiumIsCurrentAndUpgradeInProgress() = snap("premium_current") {
        premium(PremiumUiState(overview(PlanTier.PREMIUM, freePlan(), premiumPlan(), proPlan(price = monthlyPrice)), purchasingTier = PlanTier.COMMUNITY_PRO))
    }

    @Test
    fun onlyFreePlanPublished() = snap("premium_only_free") {
        premium(PremiumUiState(overview(PlanTier.FREE, freePlan())))
    }

    @Test
    fun loadError() = snap("premium_error") {
        premium(PremiumUiState(Loadable.Failure(AppError.Network())))
    }

    @Test
    fun `mevcut plan etiketi Free kartinda gorunur ve premium karti Populer rozetiyle Premium a Gec sunar`() {
        val upgraded = mutableListOf<PlanTier>()
        composeRule.setContent {
            KampusAgiTheme(darkTheme = false) {
                premium(PremiumUiState(overview(PlanTier.FREE, freePlan(), premiumPlan(), proPlan()))) { upgraded += it.tier }
            }
        }
        composeRule.onNodeWithText("Mevcut plan").assertExists()
        composeRule.onNodeWithText("Popüler").assertExists()
        composeRule.onNodeWithText("₺49,99 / ay").assertExists()

        composeRule.onNodeWithText("Premium'a Geç").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf(PlanTier.PREMIUM), upgraded)

        // Play'de ürünü bulunmayan plan: düğme kapalı ve neden yazılı; tıklama geri çağrı üretmez.
        composeRule.onNodeWithText("Community Pro'ya Geç").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Şu an satın alınamıyor.").assertExists()
        composeRule.onNodeWithText("Community Pro'ya Geç").performClick()
        assertEquals(listOf(PlanTier.PREMIUM), upgraded)
    }
}
