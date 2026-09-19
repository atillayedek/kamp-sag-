package com.kampusagi.android.feature.requirement

import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.PostCategory
import com.kampusagi.android.domain.requirement.AnalysisSource
import com.kampusagi.android.domain.requirement.HelpType
import com.kampusagi.android.domain.requirement.NeedAnalysis
import com.kampusagi.android.domain.requirement.NeedUrgency
import com.kampusagi.android.domain.requirement.ParsedNeed
import com.kampusagi.android.domain.requirement.REQUIREMENT_TEXT_MAX_LENGTH
import com.kampusagi.android.domain.requirement.RequirementRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRequirementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private class FakeRequirementRepository : RequirementRepository {
        var analyzeBlock: suspend (String) -> NeedAnalysis = { NeedAnalysis(need("Başlık"), AnalysisSource.AI) }
        var publishBlock: suspend (String, ParsedNeed) -> String = { _, _ -> "req-1" }
        val analyzed = mutableListOf<String>()
        val published = mutableListOf<Pair<String, ParsedNeed>>()

        override suspend fun analyze(rawText: String): NeedAnalysis {
            analyzed += rawText
            return analyzeBlock(rawText)
        }

        override suspend fun publish(rawText: String, need: ParsedNeed): String {
            published += rawText to need
            return publishBlock(rawText, need)
        }
    }

    private companion object {
        fun need(title: String) = ParsedNeed(title, PostCategory.ACADEMIC, HelpType.LOOKING_FOR_PEOPLE, emptyList(), null, NeedUrgency.NORMAL, null, emptyList())
        const val TEXT = "Veri yapıları için çalışma arkadaşı arıyorum"
    }

    private val repository = FakeRequirementRepository()
    private fun viewModel() = CreateRequirementViewModel(repository)

    private fun TestScope.debounced() = advanceTimeBy(CreateRequirementViewModel.DEBOUNCE_MS + 1)

    @Test
    fun `yazma durunca bir kez analiz edilir`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange(TEXT)
        assertEquals(AnalysisState.Idle, vm.uiState.value.analysis)

        debounced()
        advanceUntilIdle()

        assertEquals(listOf(TEXT), repository.analyzed)
        assertTrue(vm.uiState.value.analysis is AnalysisState.Ready)
    }

    @Test
    fun `hizli yazimda yalnizca son metin analiz edilir`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange("Ders notu arıyorum")
        advanceTimeBy(400)
        vm.onTextChange("Ders notu arıyorum, matematik")
        advanceTimeBy(400)
        vm.onTextChange("Ders notu arıyorum, matematik ve fizik")
        debounced()
        advanceUntilIdle()

        assertEquals(listOf("Ders notu arıyorum, matematik ve fizik"), repository.analyzed)
    }

    @Test
    fun `kisa metinde analiz istegi atilmaz`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange("kısa")
        debounced()
        advanceUntilIdle()
        assertTrue(repository.analyzed.isEmpty())
        assertEquals(AnalysisState.Idle, vm.uiState.value.analysis)
    }

    @Test
    fun `analiz sirasinda metin degisirse eski sonuc yok sayilir`() = runTest(testDispatcher) {
        val vm = viewModel()
        repository.analyzeBlock = { text -> kotlinx.coroutines.delay(2000); NeedAnalysis(need(if (text == TEXT) "ESKİ" else "YENİ"), AnalysisSource.AI) }
        vm.onTextChange(TEXT)
        debounced()
        advanceTimeBy(500) // analiz sürüyor
        assertEquals(AnalysisState.Analyzing, vm.uiState.value.analysis)

        vm.onTextChange("$TEXT ve fizik")
        advanceUntilIdle()

        val state = vm.uiState.value.analysis
        assertTrue(state is AnalysisState.Ready && state.forText == "$TEXT ve fizik")
        assertEquals("YENİ", (state as AnalysisState.Ready).analysis.need.title)
    }

    @Test
    fun `metin analizden sonra degisirse paylasim kapanir`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange(TEXT)
        debounced(); advanceUntilIdle()
        assertTrue(vm.uiState.value.canPublish)

        vm.onTextChange("$TEXT!")
        assertFalse(vm.uiState.value.canPublish)
        assertEquals(AnalysisState.Idle, vm.uiState.value.analysis)
    }

    @Test
    fun `bosluk degisikligi gecerli analizi bozmaz`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange(TEXT)
        debounced(); advanceUntilIdle()

        vm.onTextChange("$TEXT   ")
        assertTrue(vm.uiState.value.canPublish)
        advanceUntilIdle()
        assertEquals(1, repository.analyzed.size)
    }

    @Test
    fun `analiz hatasi gosterilir ve tekrar denenebilir`() = runTest(testDispatcher) {
        var attempts = 0
        repository.analyzeBlock = { if (++attempts == 1) throw AppError.Server("Çok fazla istek gönderdiniz.") else NeedAnalysis(need("Başlık"), AnalysisSource.AI) }
        val vm = viewModel()
        vm.onTextChange(TEXT)
        debounced(); advanceUntilIdle()
        val failed = vm.uiState.value.analysis
        assertTrue(failed is AnalysisState.Failed && (failed.error as AppError.Server).userMessage == "Çok fazla istek gönderdiniz.")
        assertFalse(vm.uiState.value.canPublish)

        vm.retryAnalysis()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canPublish)
    }

    @Test
    fun `paylasim taslagi ve metni yollar formu temizler ve basari yayar`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange(TEXT)
        debounced(); advanceUntilIdle()

        val publishedEvent = launch(start = CoroutineStart.UNDISPATCHED) { vm.published.first() }
        vm.publish()
        advanceUntilIdle()
        publishedEvent.join()

        assertEquals(TEXT, repository.published.single().first)
        assertEquals("Başlık", repository.published.single().second.title)
        assertEquals("", vm.uiState.value.text)
        assertEquals(AnalysisState.Idle, vm.uiState.value.analysis)
        assertFalse(vm.uiState.value.isPublishing)
    }

    @Test
    fun `paylasim hatasi mesaj gosterir ve formu korur`() = runTest(testDispatcher) {
        repository.publishBlock = { _, _ -> throw AppError.Server("Üniversite bilginiz eksik görünüyor.") }
        val vm = viewModel()
        vm.onTextChange(TEXT)
        debounced(); advanceUntilIdle()

        vm.publish()
        advanceUntilIdle()

        assertEquals(UiText.Plain("Üniversite bilginiz eksik görünüyor."), vm.uiState.value.message)
        assertEquals(TEXT, vm.uiState.value.text)
        assertTrue(vm.uiState.value.canPublish)
    }

    @Test
    fun `metin sunucudaki sinirla kesilir`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange("a".repeat(REQUIREMENT_TEXT_MAX_LENGTH + 100))
        assertEquals(REQUIREMENT_TEXT_MAX_LENGTH, vm.uiState.value.text.length)
        assertNotNull(vm.uiState)
    }

    @Test
    fun `analiz olmadan paylasim yapilamaz`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onTextChange(TEXT)
        vm.publish()
        advanceUntilIdle()
        assertTrue(repository.published.isEmpty())
    }
}
