package com.kampusagi.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.Loadable
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.auth.AuthRepository
import com.kampusagi.android.domain.chat.ChatEvent
import com.kampusagi.android.domain.chat.ChatMessage
import com.kampusagi.android.domain.chat.ChatPeer
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.chat.ChatSession
import com.kampusagi.android.domain.common.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

const val CHAT_PAGE_SIZE = 30
const val CHAT_MESSAGE_MAX_LENGTH = 4000

/** Yazıyor sinyali: yazmaya devam edilirken bu aralıkla tazelenir; bu kadar sessizlikte "yazmayı bıraktı" gönderilir. */
internal const val TYPING_REFRESH_MS = 3_000L
internal const val TYPING_IDLE_MS = 4_000L

/** Karşı tarafın "yazıyor" göstergesi, yenilenmezse (sinyal kaybolduysa) bu süre sonra kendiliğinden kapanır. */
internal const val PEER_TYPING_TIMEOUT_MS = 7_000L

private const val RECONNECT_BASE_MS = 2_000L
private const val RECONNECT_MAX_MS = 30_000L

enum class DeliveryState {
    /** Gönderiliyor (henüz sunucuya kaydedilmedi). */
    SENDING,

    /** Gönderilemedi; dokunarak yeniden denenir. */
    FAILED,

    /** Sunucuya kaydedildi. */
    SENT,

    /** Karşı taraf okudu (giden mesajlar için). */
    READ,
}

data class ChatItem(
    val id: String,
    val body: String,
    val isMine: Boolean,
    val createdAt: Instant,
    val state: DeliveryState,
)

/** Henüz sunucuda olmayan (yalnızca bellekte) giden mesaj. `id`, sunucuya da gönderilen idempotency anahtarıdır. */
data class PendingMessage(val id: String, val body: String, val createdAt: Instant, val failed: Boolean = false)

data class ChatUiState(
    val peer: Loadable<ChatPeer> = Loadable.Loading,
    val serverMessages: List<ChatMessage> = emptyList(),
    val pending: List<PendingMessage> = emptyList(),
    val peerLastReadAt: Instant? = null,
    /** Eskiden yeniye sıralı, ekranda gösterilen mesajlar (sunucu + bekleyen). */
    val items: List<ChatItem> = emptyList(),
    val input: String = "",
    val isPeerTyping: Boolean = false,
    val isPeerOnline: Boolean = false,
    val canLoadOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val message: UiText? = null,
) {
    /** "İletildi ✓✓" gibi durum metni yalnızca son giden mesajda gösterilir; başarısız mesajlar her zaman gösterilir. */
    val latestMineId: String? get() = items.lastOrNull { it.isMine }?.id
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val conversationId: String = requireNotNull(savedStateHandle.get<String>("conversationId")) { "conversationId gerekli" }
    private val myUserId: String = authRepository.currentUser?.uid.orEmpty()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var activeSession: ChatSession? = null
    private var typingIdleJob: Job? = null
    private var peerTypingJob: Job? = null
    private var lastTypingSentAt: Long? = null

    init {
        load()
    }

    fun load() {
        sessionJob?.cancel()
        _uiState.update { it.copy(peer = Loadable.Loading) }
        viewModelScope.launch {
            try {
                val peer = chatRepository.getPeer(conversationId)
                val page = chatRepository.getMessages(conversationId, CHAT_PAGE_SIZE, before = null)
                mutate {
                    it.copy(
                        peer = Loadable.Success(peer),
                        serverMessages = page.sortedForDisplay(),
                        peerLastReadAt = peer.lastReadAt,
                        canLoadOlder = page.size >= CHAT_PAGE_SIZE,
                    )
                }
                markRead()
                sessionJob = viewModelScope.launch { runSession(peer.userId) }
            } catch (e: AppError) {
                _uiState.update { it.copy(peer = Loadable.Failure(e)) }
            }
        }
    }

    fun onInputChange(text: String) {
        val bounded = text.take(CHAT_MESSAGE_MAX_LENGTH)
        _uiState.update { it.copy(input = bounded) }
        if (bounded.isBlank()) {
            stopTyping()
            return
        }
        val now = clock.millis()
        val last = lastTypingSentAt
        if (last == null || now - last >= TYPING_REFRESH_MS) {
            lastTypingSentAt = now
            viewModelScope.launch { activeSession?.sendTyping(true) }
        }
        typingIdleJob?.cancel()
        typingIdleJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            stopTyping()
        }
    }

    fun send() {
        val body = _uiState.value.input.trim()
        if (body.isEmpty()) return
        val id = UUID.randomUUID().toString()
        mutate { it.copy(input = "", pending = it.pending + PendingMessage(id, body, clock.instant())) }
        stopTyping()
        deliver(id, body)
    }

    /** Gönderilemeyen mesajı AYNI kimlikle yeniden dener: mesaj sunucuya ulaşmış olsa bile çift kayıt oluşmaz. */
    fun retry(messageId: String) {
        val pending = _uiState.value.pending.firstOrNull { it.id == messageId && it.failed } ?: return
        mutate { state -> state.copy(pending = state.pending.map { if (it.id == messageId) it.copy(failed = false) else it }) }
        deliver(pending.id, pending.body)
    }

    fun loadOlder() {
        val state = _uiState.value
        if (!state.canLoadOlder || state.isLoadingOlder) return
        val oldest = state.serverMessages.firstOrNull()?.createdAt ?: return
        _uiState.update { it.copy(isLoadingOlder = true) }
        viewModelScope.launch {
            try {
                val page = chatRepository.getMessages(conversationId, CHAT_PAGE_SIZE, before = oldest)
                mutate { current ->
                    current.copy(
                        serverMessages = (current.serverMessages + page).sortedForDisplay(),
                        canLoadOlder = page.size >= CHAT_PAGE_SIZE,
                    )
                }
            } catch (e: AppError) {
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                _uiState.update { it.copy(isLoadingOlder = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun deliver(id: String, body: String) {
        viewModelScope.launch {
            try {
                val saved = chatRepository.sendMessage(conversationId, id, body)
                mutate { state -> state.copy(pending = state.pending.filterNot { it.id == id }).withServerMessage(saved) }
            } catch (e: AppError) {
                mutate { state -> state.copy(pending = state.pending.map { if (it.id == id) it.copy(failed = true) else it }) }
            }
        }
    }

    /**
     * Canlı oturum: bağlantı kopup akış kendiliğinden biterse üstel geri çekilmeyle yeni oturum açılır ve
     * kopukluk sırasında kaçan mesajlar yeniden çekilir. VM temizlenince (iptal) kanal kapatılır.
     */
    private suspend fun runSession(peerUserId: String) {
        var attempt = 0
        while (true) {
            try {
                val session = chatRepository.openSession(conversationId, peerUserId)
                activeSession = session
                try {
                    session.events.collect { event ->
                        attempt = 0
                        handle(event)
                    }
                } finally {
                    activeSession = null
                    withContext(NonCancellable) { session.close() }
                }
            } catch (_: AppError) {
                // Oturum açılamadı/koptu: aşağıda geri çekilmeyle yeniden denenir (mesajlar REST ile yine gönderilebilir).
            }
            // Akış iptal olmadan bittiyse bağlantı kopmuştur.
            peerTypingJob?.cancel()
            mutate { it.copy(isPeerTyping = false, isPeerOnline = false) }
            delay(min(RECONNECT_MAX_MS, RECONNECT_BASE_MS shl min(attempt, 4)))
            attempt++
            resync()
        }
    }

    private suspend fun resync() {
        try {
            val page = chatRepository.getMessages(conversationId, CHAT_PAGE_SIZE, before = null)
            mutate { current -> page.fold(current) { state, message -> state.withServerMessage(message) } }
            markRead()
        } catch (e: AppError) {
            _uiState.update { it.copy(message = e.toUiText()) }
        }
    }

    private fun handle(event: ChatEvent) {
        when (event) {
            is ChatEvent.NewMessage -> {
                mutate { state ->
                    state.copy(pending = state.pending.filterNot { it.id == event.message.id }).withServerMessage(event.message)
                }
                if (event.message.senderId != myUserId) {
                    peerTypingJob?.cancel()
                    mutate { it.copy(isPeerTyping = false) }
                    viewModelScope.launch { markRead() }
                }
            }
            is ChatEvent.PeerRead -> mutate { state ->
                state.copy(peerLastReadAt = listOfNotNull(state.peerLastReadAt, event.at).max())
            }
            is ChatEvent.PeerTyping -> {
                peerTypingJob?.cancel()
                mutate { it.copy(isPeerTyping = event.isTyping) }
                if (event.isTyping) {
                    peerTypingJob = viewModelScope.launch {
                        delay(PEER_TYPING_TIMEOUT_MS)
                        mutate { it.copy(isPeerTyping = false) }
                    }
                }
            }
            is ChatEvent.PeerOnline -> mutate { it.copy(isPeerOnline = event.isOnline) }
        }
    }

    private fun stopTyping() {
        typingIdleJob?.cancel()
        if (lastTypingSentAt != null) {
            lastTypingSentAt = null
            viewModelScope.launch { activeSession?.sendTyping(false) }
        }
    }

    private suspend fun markRead() {
        // Okundu bilgisi en iyi gayretle gönderilir; başarısızlığı sohbeti engellemez, sonraki mesajda yeniden denenir.
        try {
            chatRepository.markRead(conversationId)
        } catch (_: AppError) {
        }
    }

    private fun mutate(block: (ChatUiState) -> ChatUiState) {
        _uiState.update { block(it).withItems(myUserId) }
    }
}

private fun List<ChatMessage>.sortedForDisplay(): List<ChatMessage> =
    distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })

private fun ChatUiState.withServerMessage(message: ChatMessage): ChatUiState =
    copy(serverMessages = (serverMessages.filterNot { it.id == message.id } + message).sortedForDisplay())

/** Sunucu + bekleyen mesajlardan ekran listesini türetir; okundu durumu karşı tarafın `last_read_at` değerinden gelir. */
internal fun ChatUiState.withItems(myUserId: String): ChatUiState {
    val fromServer = serverMessages.map { message ->
        val mine = message.senderId == myUserId
        val read = mine && peerLastReadAt?.let { !message.createdAt.isAfter(it) } == true
        ChatItem(
            id = message.id,
            body = message.body,
            isMine = mine,
            createdAt = message.createdAt,
            state = if (read) DeliveryState.READ else DeliveryState.SENT,
        )
    }
    val fromPending = pending.map {
        ChatItem(it.id, it.body, isMine = true, createdAt = it.createdAt, state = if (it.failed) DeliveryState.FAILED else DeliveryState.SENDING)
    }
    return copy(items = (fromServer + fromPending).sortedWith(compareBy<ChatItem> { it.createdAt }.thenBy { it.id }))
}
