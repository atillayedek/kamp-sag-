package com.kampusagi.android.testutil

import com.kampusagi.android.domain.events.CampusEvent
import com.kampusagi.android.domain.events.EventsRepository
import java.time.Instant

fun testEvent(
    id: String,
    joined: Boolean = false,
    count: Int = 0,
    university: Boolean = true,
    description: String = "Açıklama $id",
) = CampusEvent(
    id = id,
    title = "Etkinlik $id",
    description = description,
    location = "Yer $id",
    startsAt = Instant.parse("2026-09-26T15:00:00Z"),
    endsAt = null,
    isUniversityEvent = university,
    attendeeCount = count,
    isJoined = joined,
)

class FakeEventsRepository : EventsRepository {
    var upcomingBlock: suspend () -> List<CampusEvent> = { emptyList() }
    var attendBlock: suspend (String, Boolean) -> Unit = { _, _ -> }
    val attendCalls = mutableListOf<Pair<String, Boolean>>()
    var upcomingCalls = 0

    override suspend fun getUpcoming(): List<CampusEvent> {
        upcomingCalls++
        return upcomingBlock()
    }

    override suspend fun setAttending(eventId: String, attending: Boolean) {
        attendCalls += eventId to attending
        attendBlock(eventId, attending)
    }
}
