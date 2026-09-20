package com.kampusagi.android.feature.subscription

import android.app.Activity
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.subscription.PlanTier
import com.kampusagi.android.domain.subscription.PlansOverview
import com.kampusagi.android.domain.subscription.PurchaseResult
import com.kampusagi.android.testutil.FakeSubscriptionRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.freePlan
import com.kampusagi.android.testutil.premiumPlan
import com.kampusagi.android.testutil.proPlan
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository = FakeSubscriptionRepository()
    private val activity = mockk<Activity>(relaxed = true)

    private fun overview(vm: PremiumViewModel) = (vm.uiState.value.overview as Loadable.Success).value

    @Test
    fun `planlar ve mevcut plan yuklenir acilista satin almalar dogrulatilir`() = runTest {
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(PlanTier.FREE, PlanTier.PREMIUM), overview(vm).plans.map { it.tier })
        assertEquals(PlanTier.FREE, overview(vm).currentTier)
        assertEquals(1, repository.restoreCalls)
        assertTrue(vm.uiState.value.hasPaidPlans)
    }

    @Test
    fun `yalnizca ucretsiz plan yayindaysa ucretli plan yok sayilir`() = runTest {
        repository.overviewBlock = { PlansOverview(listOf(freePlan()), PlanTier.FREE) }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasPaidPlans)
    }

    @Test
    fun `yukleme hatasi Failure olur ve tekrar dene calisir`() = runTest {
        repository.overviewBlock = { throw AppError.Network() }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.overview is Loadable.Failure)
        assertEquals(0, repository.restoreCalls)

        repository.overviewBlock = { PlansOverview(listOf(freePlan()), PlanTier.FREE) }
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.overview is Loadable.Success)
    }

    @Test
    fun `Play hesabinda mevcut abonelik varsa gecerli plan guncellenir`() = runTest {
        repository.restoreBlock = { PlanTier.PREMIUM }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()
        assertEquals(PlanTier.PREMIUM, overview(vm).currentTier)
    }

    @Test
    fun `dogrulama hatasi mesaj olur ama plan listesi ekranda kalir`() = runTest {
        repository.restoreBlock = { throw AppError.Server("Satın alma doğrulanamadı.") }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.overview is Loadable.Success)
        assertNotNull(vm.uiState.value.message)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `basarili satin alma plani gunceller ve basari mesaji gosterir`() = runTest {
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, premiumPlan())
        advanceUntilIdle()

        assertEquals(listOf(premiumPlan()), repository.purchases)
        assertEquals(PlanTier.PREMIUM, overview(vm).currentTier)
        assertNotNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.purchasingTier)
    }

    @Test
    fun `satin alma surerken ikinci dokunus yok sayilir ve kart yukleniyor gosterir`() = runTest {
        val gate = CompletableDeferred<PurchaseResult>()
        repository.purchaseBlock = { gate.await() }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, premiumPlan())
        runCurrent()
        vm.purchase(activity, premiumPlan())
        runCurrent()

        assertEquals(1, repository.purchases.size)
        assertEquals(PlanTier.PREMIUM, vm.uiState.value.purchasingTier)

        gate.complete(PurchaseResult.Canceled)
        advanceUntilIdle()
        assertNull(vm.uiState.value.purchasingTier)
    }

    @Test
    fun `iptal edilen satin alma mesaj uretmez ve plani degistirmez`() = runTest {
        repository.purchaseBlock = { PurchaseResult.Canceled }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, premiumPlan())
        advanceUntilIdle()

        assertNull(vm.uiState.value.message)
        assertEquals(PlanTier.FREE, overview(vm).currentTier)
    }

    @Test
    fun `bekleyen odeme bilgi mesaji verir ve plani degistirmez`() = runTest {
        repository.purchaseBlock = { PurchaseResult.Pending }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, premiumPlan())
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertEquals(PlanTier.FREE, overview(vm).currentTier)
    }

    @Test
    fun `satin alma hatasi mesaj gosterir ve plani degistirmez`() = runTest {
        repository.purchaseBlock = { throw AppError.Server("Bu satın alma başka bir hesaba tanımlı.") }
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, premiumPlan())
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertEquals(PlanTier.FREE, overview(vm).currentTier)
        assertNull(vm.uiState.value.purchasingTier)
    }

    @Test
    fun `Play fiyati olmayan plan satin alinamaz`() = runTest {
        val vm = PremiumViewModel(repository)
        advanceUntilIdle()

        vm.purchase(activity, proPlan(price = null))
        advanceUntilIdle()

        assertTrue(repository.purchases.isEmpty())
    }
}
