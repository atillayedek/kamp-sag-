package com.kampusagi.android.domain.chat

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class ConversationSummary(
    val id: String,
    val otherUserId: String,
    val otherName: String,
    val otherAvatarUrl: String?,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val lastMessageFromMe: Boolean,
    val unreadCount: Int,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: Instant,
)

/** Sohbet karşı tarafı: kimlik + karşı tarafın en son okuma zamanı (okundu bilgisi için). */
data class ChatPeer(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val lastReadAt: Instant?,
)

/** Canlı sohbet olayları (Supabase Realtime). */
sealed interface ChatEvent {
    data class NewMessage(val message: ChatMessage) : ChatEvent

    /** Karşı taraf sohbeti bu ana kadar okudu. */
    data class PeerRead(val at: Instant) : ChatEvent

    data class PeerTyping(val isTyping: Boolean) : ChatEvent
    data class PeerOnline(val isOnline: Boolean) : ChatEvent
}

/** Açık bir sohbet için canlı oturum: olay akışı + yazıyor sinyali. `close` kanalı kapatır. */
interface ChatSession {
    val events: Flow<ChatEvent>
    suspend fun sendTyping(isTyping: Boolean)
    suspend fun close()
}

/** Tüm metotlar hata durumunda [com.kampusagi.android.domain.common.AppError] fırlatır. */
interface ChatRepository {
    /** Aynı çift için idempotent; varsa mevcut sohbeti döner. */
    suspend fun startConversation(otherUserId: String, requirementId: String?): String

    suspend fun getConversations(): List<ConversationSummary>

    suspend fun getPeer(conversationId: String): ChatPeer

    /** En yeniden eskiye `limit` mesaj; `before` verilirse ondan ESKİ olanlar. */
    suspend fun getMessages(conversationId: String, limit: Int, before: Instant?): List<ChatMessage>

    /** İstemci üretimli `messageId` ile idempotent: yeniden deneme çift mesaj üretmez. */
    suspend fun sendMessage(conversationId: String, messageId: String, body: String): ChatMessage

    suspend fun markRead(conversationId: String)

    fun openSession(conversationId: String, peerUserId: String): ChatSession

    /** Kullanıcının herhangi bir sohbetine mesaj geldiğinde (liste/rozet yenilensin diye) sinyal verir. */
    fun observeInbox(): Flow<Unit>
}
