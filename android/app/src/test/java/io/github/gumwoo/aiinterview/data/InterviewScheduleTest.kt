package io.github.gumwoo.aiinterview.data

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterviewScheduleTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val weekdays = InterviewSchedule(enabled = true, hour = 7, minute = 30)

    private fun at(text: String, zone: ZoneId = seoul) = ZonedDateTime.of(
        java.time.LocalDateTime.parse(text),
        zone,
    )

    @Test
    fun `rings today when time has not passed`() {
        // 2026-11-02는 월요일이다.
        assertEquals(at("2026-11-02T07:30:00"), weekdays.nextOccurrence(at("2026-11-02T07:00:00")))
    }

    @Test
    fun `moves to next day once time has passed`() {
        assertEquals(at("2026-11-03T07:30:00"), weekdays.nextOccurrence(at("2026-11-02T07:30:00")))
    }

    @Test
    fun `skips days that are not selected`() {
        // 금요일 아침이 지나면 다음 월요일이다.
        assertEquals(at("2026-11-09T07:30:00"), weekdays.nextOccurrence(at("2026-11-06T08:00:00")))
    }

    @Test
    fun `single day repeats a week later`() {
        val monday = weekdays.copy(days = setOf(DayOfWeek.MONDAY))

        assertEquals(at("2026-11-09T07:30:00"), monday.nextOccurrence(at("2026-11-02T08:00:00")))
    }

    @Test
    fun `no alarm when disabled or no days`() {
        assertNull(weekdays.copy(enabled = false).nextOccurrence(at("2026-11-02T07:00:00")))
        assertNull(weekdays.copy(days = emptySet()).nextOccurrence(at("2026-11-02T07:00:00")))
    }

    @Test
    fun `uses local time of the zone`() {
        val utc = ZoneId.of("UTC")

        assertEquals(at("2026-11-02T07:30:00", utc), weekdays.nextOccurrence(at("2026-11-02T07:00:00", utc)))
    }

    @Test
    fun `handles clock jump in daylight saving zone`() {
        val newYork = ZoneId.of("America/New_York")
        val sunday = InterviewSchedule(enabled = true, hour = 2, minute = 30, days = setOf(DayOfWeek.SUNDAY))

        // 2027-03-14 02:30은 뉴욕에 없는 시각이라 03:30으로 밀린다.
        assertEquals(3, sunday.nextOccurrence(at("2027-03-14T00:00:00", newYork))?.hour)
    }
}
