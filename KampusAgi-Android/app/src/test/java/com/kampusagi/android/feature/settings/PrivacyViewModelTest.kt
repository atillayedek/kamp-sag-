package com.kampusagi.android.feature.settings

import com.kampusagi.android.testutil.FakePrivacyPreferenceRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val preferences = FakePrivacyPreferenceRepository()

    @Test
    fun `varsayilan olarak istatistik paylasimi acik gorunur`() = runTest {
        val vm = PrivacyViewModel(preferences)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.analyticsEnabled)
    }

    @Test
    fun `kayitli tercih yansir ve anahtar tercihi kaydeder`() = runTest {
        val disabled = PrivacyViewModel(FakePrivacyPreferenceRepository(initial = false))
        advanceUntilIdle()
        assertFalse(disabled.uiState.value.analyticsEnabled)

        val vm2 = PrivacyViewModel(preferences)
        advanceUntilIdle()
        vm2.onAnalyticsChange(false)
        advanceUntilIdle()
        assertEquals(listOf(false), preferences.saved)
        assertFalse(vm2.uiState.value.analyticsEnabled)

        vm2.onAnalyticsChange(true)
        advanceUntilIdle()
        assertTrue(vm2.uiState.value.analyticsEnabled)
    }

    @Test
    fun `tercih yazilamazsa mesaj gosterilir ve uygulama cokmez`() = runTest {
        preferences.setBlock = { throw IOException("disk dolu") }
        val vm = PrivacyViewModel(preferences)
        advanceUntilIdle()

        vm.onAnalyticsChange(false)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertTrue("yazılamayan tercih arayüzde değişmiş görünmemeli", vm.uiState.value.analyticsEnabled)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }
}
