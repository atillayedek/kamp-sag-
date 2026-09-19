package com.kampusagi.android.testutil

import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.chat.ChatEvent
import com.kampusagi.android.domain.chat.ChatMessage
import com.kampusagi.android.domain.chat.ChatPeer
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.chat.ChatSession
import com.kampusagi.android.domain.chat.ConversationSummary
import com.kampusagi.android.domain.match.MatchCandidate
import com.kampusagi.android.domain.match.MatchRepository
import com.kampusagi.android.domain.match.MatchesResult
import com.kampusagi.android.domain.match.MyRequirement
import com.kampusagi.android.domain.match.PublicProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Testin ilerlettiği saat. */
class MutableClock(var now: Instant = Instant.parse("2026-09-19T12:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
    fun advanceMillis(millis: Long) {
        now = now.plusMillis(millis)
    }
}

fun testMessage(
    id: String,
    senderId: String,
    at: String = "2026-09-19T12:00:00Z",
    body: String = "mesaj $id",
    conversationId: String = "conv-1",
) = ChatMessage(id, conversationId, senderId, body, Instant.parse(at))

fun testMatch(id: String, userId: String = "user-$id", score: Int = 80, isSemantic: Boolean = false) =
    MatchCandidate(id, userId, "Aday $id", "Bölüm", null, score, isSemantic, listOf("etiket"))

class FakeMatchRepository : MatchRepository {
    var matchesBlock: suspend () -> MatchesResult = { MatchesResult(MyRequirement("req-1", "İhtiyaç"), emptyList()) }
    var refreshBlock: suspend (String) -> MatchesResult = { matchesBlock() }
    var profileBlock: suspend (String) -> PublicProfile = { PublicProfile(it, "Ayşe", "Bölüm", "Üni", null, "İlan", listOf("etiket")) }
    val refreshed = mutableListOf<String>()
    var getMatchesCalls = 0

    override suspend fun getMatches(): MatchesResult {
        getMatchesCalls++
        return matchesBlock()
    }

    override suspend fun refreshMatches(requirementId: String): MatchesResult {
        refreshed += requirementId
        return refreshBlock(requirementId)
    }

    override suspend fun getPublicProfile(userId: String) = profileBlock(userId)
}

/** Kapatılabilen (bağlantı kopmasını taklit eden) sahte canlı oturum. */
class FakeChatSession : ChatSession {
    private val channel = Channel<ChatEvent>(Channel.UNLIMITED)
    override val events: Flow<ChatEvent> = channel.receiveAsFlow()
    val typingSignals = mutableListOf<Boolean>()
    var closed = false
        private set

    fun emit(event: ChatEvent) {
        channel.trySend(event)
    }

    /** Akışı normal biçimde bitirir (Realtime kopması gibi). */
    fun dropConnection() {
        channel.close()
    }

    override suspend fun sendTyping(isTyping: Boolean) {
        typingSignals += isTyping
    }

    override suspend fun close() {
        closed = true
    }
}

class FakeChatRepository : ChatRepository {
    var peer = ChatPeer("peer-1", "Ayşe Nur", null, null)
    var peerBlock: suspend () -> ChatPeer = { peer }

    /** Her zaman EN YENİDEN ESKİYE döner (gerçek sözleşme). */
    var messagesBlock: suspend (Int, Instant?) -> List<ChatMessage> = { _, _ -> emptyList() }
    var sendBlock: suspend (String, String) -> ChatMessage? = { _, _ -> null }
    var conversationsBlock: suspend () -> List<ConversationSummary> = { emptyList() }
    var startBlock: suspend (String, String?) -> String = { _, _ -> "conv-1" }
    var markReadBlock: suspend () -> Unit = {}

    val inbox = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessions = mutableListOf<FakeChatSession>()
    val sent = mutableListOf<Pair<String, String>>()
    val messageQueries = mutableListOf<Instant?>()
    val started = mutableListOf<Pair<String, String?>>()
    var markReadCalls = 0
    var conversationsCalls = 0

    /** Doluysa `openSession` bu hatayı fırlatır (oturum açılamıyor). */
    var openSessionError: AppError? = null
    var openSessionAttempts = 0

    override suspend fun startConversation(otherUserId: String, requirementId: String?): String {
        started += otherUserId to requirementId
        return startBlock(otherUserId, requirementId)
    }

    override suspend fun getConversations(): List<ConversationSummary> {
        conversationsCalls++
        return conversationsBlock()
    }

    override suspend fun getPeer(conversationId: String) = peerBlock()

    override suspend fun getMessages(conversationId: String, limit: Int, before: Instant?): List<ChatMessage> {
        messageQueries += before
        return messagesBlock(limit, before)
    }

    override suspend fun sendMessage(conversationId: String, messageId: String, body: String): ChatMessage {
        sent += messageId to body
        return sendBlock(messageId, body) ?: ChatMessage(messageId, conversationId, "user-1", body, Instant.parse("2026-09-19T12:00:30Z"))
    }

    override suspend fun markRead(conversationId: String) {
        markReadCalls++
        markReadBlock()
    }

    override fun openSession(conversationId: String, peerUserId: String): ChatSession {
        openSessionAttempts++
        openSessionError?.let { throw it }
        return FakeChatSession().also { sessions += it }
    }

    override fun observeInbox(): Flow<Unit> = inbox
}

fun testConversation(id: String, unread: Int = 0, lastFromMe: Boolean = false) =
    ConversationSummary(id, "u-$id", "Kişi $id", null, "son mesaj $id", Instant.parse("2026-09-19T12:00:00Z"), lastFromMe, unread)
