package com.kampusagi.android.data.chat

import android.util.Log
import com.kampusagi.android.data.community.parseInstant
import com.kampusagi.android.domain.chat.ChatEvent
import com.kampusagi.android.domain.chat.ChatSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "ChatSession"

/**
 * Bir sohbet için tek özel (private) Realtime kanalı: `chat:<id>` — Realtime Authorization politikaları
 * (supabase/migrations/202609190007) yalnızca sohbet üyelerine izin verir. Kanal üç iş yapar:
 * mesaj INSERT'leri ve karşı tarafın okuma zamanı (Postgres Changes, tablo RLS'i geçerli), yazıyor sinyali
 * (broadcast) ve çevrimiçi durumu (presence).
 */
internal class SupabaseChatSession(
    private val client: SupabaseClient,
    private val conversationId: String,
    private val peerUserId: String,
    private val myUserId: String,
) : ChatSession {

    @Serializable
    private data class TypingPayload(@SerialName("user_id") val userId: String, val typing: Boolean)

    @Serializable
    private data class PresencePayload(@SerialName("user_id") val userId: String)

    @Serializable
    private data class MemberPayload(
        @SerialName("user_id") val userId: String,
        @SerialName("last_read_at") val lastReadAt: String? = null,
    )

    private val realtimeChannel: RealtimeChannel = client.channel("chat:$conversationId") { isPrivate = true }

    override val events: Flow<ChatEvent> = channelFlow {
        val inserts = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }
        val memberUpdates = realtimeChannel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "conversation_members"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }
        val typing = realtimeChannel.broadcastFlow<TypingPayload>("typing")
        val presence = realtimeChannel.presenceChangeFlow()

        try {
            realtimeChannel.subscribe(blockUntilSubscribed = true)
            realtimeChannel.track(PresencePayload(myUserId))

            launch {
                inserts.collect { send(ChatEvent.NewMessage(it.decodeRecord<SupabaseChatRepository.MessageRow>().toDomain())) }
            }
            launch {
                memberUpdates.collect { action ->
                    val member = action.decodeRecord<MemberPayload>()
                    val readAt = member.lastReadAt
                    if (member.userId == peerUserId && readAt != null) send(ChatEvent.PeerRead(parseInstant(readAt)))
                }
            }
            launch {
                typing.collect { if (it.userId == peerUserId) send(ChatEvent.PeerTyping(it.typing)) }
            }
            launch {
                presence.collect { action ->
                    if (action.decodeJoinsAs<PresencePayload>().any { it.userId == peerUserId }) send(ChatEvent.PeerOnline(true))
                    if (action.decodeLeavesAs<PresencePayload>().any { it.userId == peerUserId }) send(ChatEvent.PeerOnline(false))
                }
            }
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { release() }
        }
    }.catch { error ->
        // Realtime kopması canlı güncellemeyi durdurur ama uygulamayı ÇÖKERTMEZ.
        if (error is CancellationException) throw error
        Log.w(TAG, "Sohbet canlı akışı kesildi", error)
    }

    override suspend fun sendTyping(isTyping: Boolean) {
        // Yazıyor sinyali en iyi gayretle gönderilir; başarısızlığı sohbeti etkilemez.
        try {
            realtimeChannel.broadcast("typing", TypingPayload(myUserId, isTyping))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Yazıyor sinyali gönderilemedi", e)
        }
    }

    override suspend fun close() = release()

    private suspend fun release() {
        try {
            realtimeChannel.unsubscribe()
            client.realtime.removeChannel(realtimeChannel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Kanal kapatılırken hata", e)
        }
    }
}
