package com.kampusagi.android.data.chat

import com.kampusagi.android.data.common.PG_UNIQUE_VIOLATION
import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.data.community.parseInstant
import com.kampusagi.android.domain.chat.ChatMessage
import com.kampusagi.android.domain.chat.ChatPeer
import com.kampusagi.android.domain.chat.ChatRepository
import com.kampusagi.android.domain.chat.ChatSession
import com.kampusagi.android.domain.chat.ConversationSummary
import com.kampusagi.android.domain.common.AppError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

class SupabaseChatRepository @Inject constructor(
    private val client: SupabaseClient,
) : ChatRepository {

    @Serializable
    internal data class MessageRow(
        val id: String,
        @SerialName("conversation_id") val conversationId: String,
        @SerialName("sender_id") val senderId: String,
        val body: String,
        @SerialName("created_at") val createdAt: String,
    )

    @Serializable
    private data class MessageInsert(
        val id: String,
        @SerialName("conversation_id") val conversationId: String,
        @SerialName("sender_id") val senderId: String,
        val body: String,
    )

    @Serializable
    private data class ConversationRow(
        @SerialName("conversation_id") val conversationId: String,
        @SerialName("other_user_id") val otherUserId: String,
        @SerialName("other_full_name") val otherFullName: String,
        @SerialName("other_avatar_url") val otherAvatarUrl: String? = null,
        @SerialName("last_message_body") val lastMessageBody: String? = null,
        @SerialName("last_message_at") val lastMessageAt: String? = null,
        @SerialName("last_message_sender_id") val lastMessageSenderId: String? = null,
        @SerialName("unread_count") val unreadCount: Long = 0,
    )

    @Serializable
    private data class MemberRow(
        @SerialName("user_id") val userId: String,
        @SerialName("last_read_at") val lastReadAt: String? = null,
    )

    @Serializable
    private data class PeerProfileRow(
        @SerialName("full_name") val fullName: String,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )

    override suspend fun startConversation(otherUserId: String, requirementId: String?): String = mapErrors {
        client.postgrest.rpc(
            "start_conversation",
            buildJsonObject {
                put("p_other_user", otherUserId)
                put("p_requirement_id", requirementId?.let { JsonPrimitive(it) } ?: JsonNull)
            },
        ).decodeAs<String>()
    }

    override suspend fun getConversations(): List<ConversationSummary> = mapErrors {
        val me = requireUserId()
        client.postgrest.rpc("list_my_conversations").decodeList<ConversationRow>().map {
            ConversationSummary(
                id = it.conversationId,
                otherUserId = it.otherUserId,
                otherName = it.otherFullName,
                otherAvatarUrl = it.otherAvatarUrl,
                lastMessage = it.lastMessageBody,
                lastMessageAt = it.lastMessageAt?.let(::parseInstant),
                lastMessageFromMe = it.lastMessageSenderId == me,
                unreadCount = it.unreadCount.toInt(),
            )
        }
    }

    override suspend fun getPeer(conversationId: String): ChatPeer = mapErrors {
        val me = requireUserId()
        val member = client.from("conversation_members")
            .select(Columns.list("user_id", "last_read_at")) {
                filter {
                    eq("conversation_id", conversationId)
                    neq("user_id", me)
                }
            }
            .decodeList<MemberRow>()
            .firstOrNull() ?: throw AppError.NotFound()
        val profile = client.from("profiles")
            .select(Columns.list("full_name", "avatar_url")) { filter { eq("id", member.userId) } }
            .decodeList<PeerProfileRow>()
            .firstOrNull()
        ChatPeer(
            userId = member.userId,
            name = profile?.fullName.orEmpty(),
            avatarUrl = profile?.avatarUrl,
            lastReadAt = member.lastReadAt?.let(::parseInstant),
        )
    }

    override suspend fun getMessages(conversationId: String, limit: Int, before: Instant?): List<ChatMessage> = mapErrors {
        client.from("messages")
            .select {
                filter {
                    eq("conversation_id", conversationId)
                    if (before != null) lt("created_at", before.toString())
                }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<MessageRow>()
            .map { it.toDomain() }
    }

    override suspend fun sendMessage(conversationId: String, messageId: String, body: String): ChatMessage = mapErrors {
        try {
            client.from("messages")
                .insert(MessageInsert(messageId, conversationId, requireUserId(), body.trim())) { select() }
                .decodeSingle<MessageRow>()
                .toDomain()
        } catch (e: PostgrestRestException) {
            // Aynı id ile yeniden deneme: mesaj zaten kaydedilmişse (yanıt kaybolmuştu) mevcut satır döner -> çift mesaj yok.
            if (e.code != PG_UNIQUE_VIOLATION) throw e
            client.from("messages")
                .select { filter { eq("id", messageId) } }
                .decodeSingle<MessageRow>()
                .toDomain()
        }
    }

    override suspend fun markRead(conversationId: String) = mapErrors {
        client.postgrest.rpc("mark_conversation_read", buildJsonObject { put("p_conversation_id", conversationId) })
        Unit
    }

    override fun openSession(conversationId: String, peerUserId: String): ChatSession =
        SupabaseChatSession(client, conversationId, peerUserId, requireUserId())

    /** Gelen kutusu sinyali: RLS yalnızca kullanıcının sohbetlerindeki mesajları iletir. */
    override fun observeInbox(): Flow<Unit> = channelFlow {
        val channel = client.channel("inbox-${requireUserId()}")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            inserts.collect { send(Unit) }
        } finally {
            withContext(NonCancellable) {
                channel.unsubscribe()
                client.realtime.removeChannel(channel)
            }
        }
    }.catch { error ->
        // Realtime kopması canlı güncellemeyi durdurur ama uygulamayı ÇÖKERTMEZ; liste elle/yeniden girişte yenilenir.
        if (error is CancellationException) throw error
        Log.w("ChatRepository", "Gelen kutusu canlı akışı kesildi", error)
    }

    private fun requireUserId(): String =
        client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
}

internal fun SupabaseChatRepository.MessageRow.toDomain() = ChatMessage(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    createdAt = parseInstant(createdAt),
)
