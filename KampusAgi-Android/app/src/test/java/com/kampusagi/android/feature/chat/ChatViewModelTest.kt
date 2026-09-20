package com.kampusagi.android.feature.chat

import com.kampusagi.android.domain.analytics.AnalyticsEvent
import com.kampusagi.android.testutil.FakeAnalyticsTracker
import androidx.lifecycle.SavedStateHandle
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.domain.chat.ChatEvent
import com.kampusagi.android.domain.chat.ChatMessage
import com.kampusagi.android.domain.chat.ChatPeer
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.testutil.FakeAuthRepository
import com.kampusagi.android.testutil.FakeChatRepository
import com.kampusagi.android.testutil.MainDispatcherRule
import com.kampusagi.android.testutil.MutableClock
import com.kampusagi.android.testutil.testMessage
import com.kampusagi.android.testutil.testUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository = FakeChatRepository()
    private val clock = MutableClock()
    private val analytics = FakeAnalyticsTracker()
    private val me = testUser.uid

    private fun viewModel() = ChatViewModel(
        savedStateHandle = SavedStateHandle(mapOf("conversationId" to "conv-1")),
        chatRepository = repository,
        authRepository = FakeAuthRepository(testUser),
        clock = clock,
        analytics = analytics,
    )

    private val older = testMessage("m1", "peer-1", "2026-09-19T11:00:00Z")
    private val mine = testMessage("m2", me, "2026-09-19T11:05:00Z")
    private val newest = testMessage("m3", "peer-1", "2026-09-19T11:10:00Z")

    private fun serverHas(vararg messages: ChatMessage) {
        // Depo sözleşmesi: en yeniden eskiye.
        repository.messagesBlock = { _, _ -> messages.sortedByDescending { it.createdAt } }
    }

    @Test
    fun `acilista es ve mesajlar eskiden yeniye yuklenir oturum acilir ve okundu isaretlenir`() = runTest {
        repository.peer = ChatPeer("peer-1", "Ayşe Nur", null, null)
        serverHas(newest, older, mine)

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Ayşe Nur", (state.peer as Loadable.Success).value.name)
        assertEquals(listOf("m1", "m2", "m3"), state.items.map { it.id })
        assertFalse(state.canLoadOlder)
        assertEquals(1, repository.markReadCalls)
        assertEquals(1, repository.sessions.size)
    }

    @Test
    fun `giden mesaj karsi tarafin son okuma zamanina gore okundu olur`() = runTest {
        repository.peer = ChatPeer("peer-1", "Ayşe", null, lastReadAt = Instant.parse("2026-09-19T11:06:00Z"))
        val later = testMessage("m4", me, "2026-09-19T11:20:00Z")
        serverHas(older, mine, later)

        val vm = viewModel()
        advanceUntilIdle()

        val byId = vm.uiState.value.items.associateBy { it.id }
        assertEquals(DeliveryState.READ, byId.getValue("m2").state)
        assertEquals(DeliveryState.SENT, byId.getValue("m4").state)
        assertEquals("m4", vm.uiState.value.latestMineId)
    }

    @Test
    fun `yuklenemeyen sohbet hata gosterir ve tekrar dene calisir`() = runTest {
        repository.peerBlock = { throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.peer is Loadable.Failure)

        repository.peerBlock = { repository.peer }
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.peer is Loadable.Success)
    }

    @Test
    fun `gonderilen mesaj aninda gonderiliyor gorunur sonra ayni kimlikle sunucu mesajina donusur`() = runTest {
        serverHas()
        val gate = CompletableDeferred<Unit>()
        repository.sendBlock = { id, body ->
            gate.await()
            testMessage(id, me, "2026-09-19T12:00:05Z", body)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInputChange("  Merhaba  ")
        vm.send()
        runCurrent()

        val pendingItem = vm.uiState.value.items.single()
        assertEquals("Merhaba", pendingItem.body)
        assertEquals(DeliveryState.SENDING, pendingItem.state)
        assertEquals("", vm.uiState.value.input)

        gate.complete(Unit)
        advanceUntilIdle()

        val item = vm.uiState.value.items.single()
        assertEquals(pendingItem.id, item.id)
        assertEquals(DeliveryState.SENT, item.state)
        assertTrue(vm.uiState.value.pending.isEmpty())
        assertEquals(listOf(AnalyticsEvent.MESSAGE_SENT), analytics.events)
    }

    @Test
    fun `bos veya bosluk mesaji gonderilmez`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onInputChange("   ")
        vm.send()
        advanceUntilIdle()
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun `basarisiz gonderim FAILED olur ve yeniden deneme AYNI kimlikle cift mesaj uretmeden basarir`() = runTest {
        serverHas()
        var attempt = 0
        repository.sendBlock = { id, body ->
            attempt++
            if (attempt == 1) throw AppError.Network()
            testMessage(id, me, "2026-09-19T12:00:05Z", body)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInputChange("Selam")
        vm.send()
        advanceUntilIdle()
        val failed = vm.uiState.value.items.single()
        assertEquals(DeliveryState.FAILED, failed.state)
        assertTrue("başarısız gönderim olay üretmez", analytics.events.isEmpty())

        vm.retry(failed.id)
        advanceUntilIdle()

        assertEquals(2, repository.sent.size)
        assertEquals(repository.sent[0].first, repository.sent[1].first)
        val item = vm.uiState.value.items.single()
        assertEquals(DeliveryState.SENT, item.state)
        assertEquals(failed.id, item.id)
        assertEquals("yeniden deneme başarılı olunca TEK olay", listOf(AnalyticsEvent.MESSAGE_SENT), analytics.events)
    }

    @Test
    fun `gonderilmemis olmayan mesaj icin retry hicbir sey yapmaz`() = runTest {
        serverHas(older)
        val vm = viewModel()
        advanceUntilIdle()
        vm.retry("m1")
        advanceUntilIdle()
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun `gercek zamanli yanki HTTP yanitindan once gelirse mesaj cift gorunmez`() = runTest {
        serverHas()
        val gate = CompletableDeferred<Unit>()
        repository.sendBlock = { id, body ->
            gate.await()
            testMessage(id, me, "2026-09-19T12:00:05Z", body)
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onInputChange("Merhaba")
        vm.send()
        runCurrent()
        val id = repository.sent.single().first

        repository.sessions.single().emit(ChatEvent.NewMessage(testMessage(id, me, "2026-09-19T12:00:05Z", "Merhaba")))
        runCurrent()
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(DeliveryState.SENT, vm.uiState.value.items.single().state)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun `karsi taraftan yeni mesaj gelince eklenir yazma gostergesi kapanir ve okundu isaretlenir`() = runTest {
        serverHas(older)
        val vm = viewModel()
        advanceUntilIdle()
        val session = repository.sessions.single()
        session.emit(ChatEvent.PeerTyping(true))
        runCurrent()
        assertTrue(vm.uiState.value.isPeerTyping)

        session.emit(ChatEvent.NewMessage(newest))
        runCurrent()

        assertEquals(listOf("m1", "m3"), vm.uiState.value.items.map { it.id })
        assertFalse(vm.uiState.value.isPeerTyping)
        assertEquals(2, repository.markReadCalls)
    }

    @Test
    fun `kendi mesajimizin yankisi okundu isaretlemeyi tetiklemez`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        repository.sessions.single().emit(ChatEvent.NewMessage(mine))
        runCurrent()
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(1, repository.markReadCalls)
    }

    @Test
    fun `PeerRead olayi giden mesajlari okundu yapar ve geriye gitmez`() = runTest {
        serverHas(mine)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DeliveryState.SENT, vm.uiState.value.items.single().state)
        val session = repository.sessions.single()

        session.emit(ChatEvent.PeerRead(Instant.parse("2026-09-19T11:30:00Z")))
        runCurrent()
        assertEquals(DeliveryState.READ, vm.uiState.value.items.single().state)

        session.emit(ChatEvent.PeerRead(Instant.parse("2026-09-19T10:00:00Z")))
        runCurrent()
        assertEquals(DeliveryState.READ, vm.uiState.value.items.single().state)
    }

    @Test
    fun `yaziyor gostergesi yenilenmezse zaman asimiyla kapanir`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        repository.sessions.single().emit(ChatEvent.PeerTyping(true))
        runCurrent()
        assertTrue(vm.uiState.value.isPeerTyping)

        advanceTimeBy(PEER_TYPING_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(vm.uiState.value.isPeerTyping)
    }

    @Test
    fun `cevrimici durumu olaylara gore degisir`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        val session = repository.sessions.single()
        session.emit(ChatEvent.PeerOnline(true))
        runCurrent()
        assertTrue(vm.uiState.value.isPeerOnline)
        session.emit(ChatEvent.PeerOnline(false))
        runCurrent()
        assertFalse(vm.uiState.value.isPeerOnline)
    }

    @Test
    fun `yazarken yazıyor sinyali sinirli sikliktadir ve sessizlikte kapanir`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        val session = repository.sessions.single()

        vm.onInputChange("M")
        vm.onInputChange("Me")
        vm.onInputChange("Mer")
        runCurrent()
        assertEquals(listOf(true), session.typingSignals)

        clock.advanceMillis(TYPING_REFRESH_MS)
        vm.onInputChange("Merh")
        runCurrent()
        assertEquals(listOf(true, true), session.typingSignals)

        advanceTimeBy(TYPING_IDLE_MS + 1)
        runCurrent()
        assertEquals(listOf(true, true, false), session.typingSignals)
    }

    @Test
    fun `girdi silinince veya mesaj gonderilince yazma bitti sinyali gider`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        val session = repository.sessions.single()

        vm.onInputChange("Merhaba")
        vm.onInputChange("")
        runCurrent()
        assertEquals(listOf(true, false), session.typingSignals)

        vm.onInputChange("Selam")
        vm.send()
        advanceUntilIdle()
        assertEquals(listOf(true, false, true, false), session.typingSignals)
    }

    @Test
    fun `girdi uzunlugu sunucu sinirina kirpilir`() = runTest {
        serverHas()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onInputChange("a".repeat(CHAT_MESSAGE_MAX_LENGTH + 50))
        assertEquals(CHAT_MESSAGE_MAX_LENGTH, vm.uiState.value.input.length)
    }

    @Test
    fun `tam sayfa donerse eskiler yuklenebilir ve en eski mesajdan oncesi istenir`() = runTest {
        val page = (1..CHAT_PAGE_SIZE).map { testMessage("p$it", "peer-1", "2026-09-19T11:%02d:00Z".format(it)) }
        repository.messagesBlock = { _, before ->
            if (before == null) page.sortedByDescending { it.createdAt }
            else listOf(testMessage("old", "peer-1", "2026-09-19T10:00:00Z"))
        }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canLoadOlder)

        vm.loadOlder()
        advanceUntilIdle()

        assertEquals(page.first().createdAt, repository.messageQueries.last())
        assertEquals("old", vm.uiState.value.items.first().id)
        assertEquals(CHAT_PAGE_SIZE + 1, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.canLoadOlder)
    }

    @Test
    fun `eskileri yukleme sirasinda ikinci istek gonderilmez`() = runTest {
        val page = (1..CHAT_PAGE_SIZE).map { testMessage("p$it", "peer-1", "2026-09-19T11:%02d:00Z".format(it)) }
        val gate = CompletableDeferred<Unit>()
        repository.messagesBlock = { _, before ->
            if (before == null) page.sortedByDescending { it.createdAt } else {
                gate.await()
                emptyList()
            }
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        vm.loadOlder()
        runCurrent()
        assertEquals(1, repository.messageQueries.count { it != null })

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoadingOlder)
    }

    @Test
    fun `eskileri yukleme hatasi mesaj gosterir ve liste bozulmaz`() = runTest {
        val page = (1..CHAT_PAGE_SIZE).map { testMessage("p$it", "peer-1", "2026-09-19T11:%02d:00Z".format(it)) }
        repository.messagesBlock = { _, before -> if (before == null) page.reversed() else throw AppError.Network() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadOlder()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertEquals(CHAT_PAGE_SIZE, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.isLoadingOlder)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `baglanti kopunca yeni oturum acilir ve kopukken gelen mesajlar yeniden cekilir`() = runTest {
        serverHas(older)
        val vm = viewModel()
        advanceUntilIdle()
        val first = repository.sessions.single()
        first.emit(ChatEvent.PeerOnline(true))
        runCurrent()
        assertTrue(vm.uiState.value.isPeerOnline)

        // Kopukken karşı taraf mesaj yazdı; REST yeniden çekimi bunu getirmeli.
        serverHas(older, newest)
        first.dropConnection()
        runCurrent()
        assertFalse(vm.uiState.value.isPeerOnline)
        assertTrue(first.closed)

        advanceTimeBy(2_001)
        runCurrent()

        assertEquals(2, repository.sessions.size)
        assertEquals(listOf("m1", "m3"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `oturum acilamazsa uygulama cokmez ve ustel geri cekilmeyle yeniden denenir`() = runTest {
        serverHas()
        repository.openSessionError = AppError.Network()
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.peer is Loadable.Success)
        assertEquals(1, repository.openSessionAttempts)

        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(2, repository.openSessionAttempts)

        // İkinci başarısızlıktan sonra bekleme iki katına çıkar (4 sn).
        advanceTimeBy(3_900)
        runCurrent()
        assertEquals(2, repository.openSessionAttempts)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(3, repository.openSessionAttempts)

        // Bağlantı düzelince mesajlar yine de akmaya başlar.
        repository.openSessionError = null
        advanceTimeBy(8_100)
        runCurrent()
        repository.sessions.last().emit(ChatEvent.NewMessage(newest))
        runCurrent()
        assertEquals(listOf("m3"), vm.uiState.value.items.map { it.id })
    }
}
