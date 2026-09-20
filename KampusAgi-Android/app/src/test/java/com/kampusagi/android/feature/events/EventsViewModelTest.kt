package com.kampusagi.android.feature.events

import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.events.CampusEvent
import com.kampusagi.android.testutil.FakeEventsRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.testEvent
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
class EventsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository = FakeEventsRepository()

    private fun events(vm: EventsViewModel): List<CampusEvent> = (vm.uiState.value.events as Loadable.Success).value

    @Test
    fun `etkinlikler yuklenir`() = runTest {
        repository.upcomingBlock = { listOf(testEvent("a"), testEvent("b", joined = true, count = 3)) }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), events(vm).map { it.id })
    }

    @Test
    fun `bos liste hata degil bos durumdur`() = runTest {
        val vm = EventsViewModel(repository)
        advanceUntilIdle()
        assertTrue(events(vm).isEmpty())
    }

    @Test
    fun `yukleme hatasi Failure olur ve tekrar dene calisir`() = runTest {
        repository.upcomingBlock = { throw AppError.Network() }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.events is Loadable.Failure)

        repository.upcomingBlock = { listOf(testEvent("a")) }
        vm.load()
        advanceUntilIdle()
        assertEquals(1, events(vm).size)
    }

    @Test
    fun `yenileme listeyi gunceller ve hatada mevcut listeyi korur`() = runTest {
        repository.upcomingBlock = { listOf(testEvent("a")) }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()

        repository.upcomingBlock = { listOf(testEvent("a"), testEvent("b")) }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, events(vm).size)
        assertFalse(vm.uiState.value.isRefreshing)

        repository.upcomingBlock = { throw AppError.Network() }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, events(vm).size)
        assertNotNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `katil aninda sayaci artirir ve sunucuya yazar`() = runTest {
        repository.upcomingBlock = { listOf(testEvent("a", count = 4)) }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()

        vm.toggleAttendance(events(vm).first())
        // Sunucu yanıtı beklenmeden kart güncellenmiş olmalı.
        assertEquals(true, events(vm).first().isJoined)
        assertEquals(5, events(vm).first().attendeeCount)
        advanceUntilIdle()

        assertEquals(listOf("a" to true), repository.attendCalls)
        assertTrue(vm.uiState.value.pendingEventIds.isEmpty())
    }

    @Test
    fun `ayril sayaci azaltir ve 0 in altina indirmez`() = runTest {
        repository.upcomingBlock = { listOf(testEvent("a", joined = true, count = 0)) }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()

        vm.toggleAttendance(events(vm).first())
        advanceUntilIdle()

        assertEquals(false, events(vm).first().isJoined)
        assertEquals(0, events(vm).first().attendeeCount)
        assertEquals(listOf("a" to false), repository.attendCalls)
    }

    @Test
    fun `sunucu reddederse kart eski haline doner ve hata gosterilir`() = runTest {
        repository.upcomingBlock = { listOf(testEvent("a", count = 4)) }
        repository.attendBlock = { _, _ -> throw AppError.Forbidden() }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()

        vm.toggleAttendance(events(vm).first())
        advanceUntilIdle()

        assertEquals(false, events(vm).first().isJoined)
        assertEquals(4, events(vm).first().attendeeCount)
        assertNotNull(vm.uiState.value.message)
        assertTrue(vm.uiState.value.pendingEventIds.isEmpty())
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `istek surerken ayni etkinlige ikinci dokunus yok sayilir`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.upcomingBlock = { listOf(testEvent("a", count = 1)) }
        repository.attendBlock = { _, _ -> gate.await() }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()
        val event = events(vm).first()

        vm.toggleAttendance(event)
        runCurrent()
        vm.toggleAttendance(events(vm).first())
        runCurrent()

        assertEquals(1, repository.attendCalls.size)
        assertEquals(setOf("a"), vm.uiState.value.pendingEventIds)
        assertEquals(2, events(vm).first().attendeeCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pendingEventIds.isEmpty())
    }

    @Test
    fun `bir etkinligin istegi digerini kilitlemez`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.upcomingBlock = { listOf(testEvent("a"), testEvent("b")) }
        repository.attendBlock = { id, _ -> if (id == "a") gate.await() }
        val vm = EventsViewModel(repository)
        advanceUntilIdle()

        vm.toggleAttendance(events(vm)[0])
        vm.toggleAttendance(events(vm)[1])
        runCurrent()

        assertEquals(listOf("a" to true, "b" to true), repository.attendCalls)
        assertEquals(setOf("a"), vm.uiState.value.pendingEventIds)
        gate.complete(Unit)
        advanceUntilIdle()
    }
}
