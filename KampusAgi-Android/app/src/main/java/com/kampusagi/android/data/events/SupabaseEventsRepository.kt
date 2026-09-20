package com.kampusagi.android.data.events

import com.kampusagi.android.data.common.PG_UNIQUE_VIOLATION
import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.data.community.parseInstant
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.events.CampusEvent
import com.kampusagi.android.domain.events.EventsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val EVENTS_LIMIT = 50

class SupabaseEventsRepository @Inject constructor(
    private val client: SupabaseClient,
) : EventsRepository {

    @Serializable
    private data class EventRow(
        val id: String,
        val title: String,
        val description: String,
        val location: String,
        @SerialName("starts_at") val startsAt: String,
        @SerialName("ends_at") val endsAt: String? = null,
        @SerialName("university_id") val universityId: String? = null,
        @SerialName("attendee_count") val attendeeCount: Long = 0,
        @SerialName("is_joined") val isJoined: Boolean = false,
    )

    @Serializable
    private data class AttendeeInsert(
        @SerialName("event_id") val eventId: String,
        @SerialName("user_id") val userId: String,
    )

    override suspend fun getUpcoming(): List<CampusEvent> = mapErrors {
        client.postgrest.rpc("list_campus_events", buildJsonObject { put("p_limit", EVENTS_LIMIT) })
            .decodeList<EventRow>()
            .map { row ->
                CampusEvent(
                    id = row.id,
                    title = row.title,
                    description = row.description,
                    location = row.location,
                    startsAt = parseInstant(row.startsAt),
                    endsAt = row.endsAt?.let(::parseInstant),
                    isUniversityEvent = row.universityId != null,
                    attendeeCount = row.attendeeCount.toInt().coerceAtLeast(0),
                    isJoined = row.isJoined,
                )
            }
    }

    override suspend fun setAttending(eventId: String, attending: Boolean) = mapErrors {
        val userId = client.auth.currentUserOrNull()?.id ?: throw AppError.Unauthorized()
        if (attending) {
            try {
                client.from("campus_event_attendees").insert(AttendeeInsert(eventId, userId))
            } catch (e: PostgrestRestException) {
                // Zaten katılıyor (yarış/çift dokunuş): istenen durum zaten sağlanıyor.
                if (e.code != PG_UNIQUE_VIOLATION) throw e
            }
        } else {
            client.from("campus_event_attendees").delete {
                filter {
                    eq("event_id", eventId)
                    eq("user_id", userId)
                }
            }
        }
        Unit
    }
}
