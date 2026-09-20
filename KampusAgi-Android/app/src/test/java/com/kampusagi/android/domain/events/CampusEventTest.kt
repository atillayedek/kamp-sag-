package com.kampusagi.android.domain.events

import com.kampusagi.android.testutil.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CampusEventTest {

    @Test
    fun `katilinca sayac artar`() {
        val updated = testEvent("a", joined = false, count = 4).withAttending(true)
        assertEquals(true, updated.isJoined)
        assertEquals(5, updated.attendeeCount)
    }

    @Test
    fun `ayrilinca sayac azalir ve sifirin altina inmez`() {
        assertEquals(3, testEvent("a", joined = true, count = 4).withAttending(false).attendeeCount)
        assertEquals(0, testEvent("a", joined = true, count = 0).withAttending(false).attendeeCount)
    }

    @Test
    fun `durum zaten istenen gibiyse sayac degismez`() {
        val joined = testEvent("a", joined = true, count = 4)
        assertSame(joined, joined.withAttending(true))
        val notJoined = testEvent("b", joined = false, count = 4)
        assertSame(notJoined, notJoined.withAttending(false))
    }

    @Test
    fun `katil ve ayril birbirinin tersidir`() {
        val event = testEvent("a", joined = false, count = 7)
        assertEquals(event, event.withAttending(true).withAttending(false))
    }
}
