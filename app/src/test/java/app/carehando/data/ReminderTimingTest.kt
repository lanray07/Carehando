package app.carehando.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTimingTest {
    @Test fun previousDayReminderRemainsEligibleJustAfterMidnight() {
        val reminder = Entry(recipientId = 1, kind = Kind.TASK, title = "Example", day = "2026-11-11",
            time = "23:50", reminder = true)
        assertEquals(listOf(LocalDate.parse("2026-11-11")), reminder.dueReminderDates(LocalDateTime.parse("2026-11-12T00:10")))
        assertTrue(reminder.dueReminderDates(LocalDateTime.parse("2026-11-12T02:00")).isEmpty())
    }

    @Test fun quietHoursCanSpanMidnightAndEndOnTheHour() {
        assertTrue(inQuietHours(22, 22, 7))
        assertTrue(inQuietHours(0, 22, 7))
        assertFalse(inQuietHours(7, 22, 7))
        assertFalse(inQuietHours(22, 22, 22))
    }
}
