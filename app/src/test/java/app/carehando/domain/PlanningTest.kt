package app.carehando.domain

import app.carehando.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlanningTest {
    private val start = LocalDate.parse("2026-10-25")
    private fun entry(id: Long, kind: Kind = Kind.TASK, repeat: Repeat = Repeat.DAILY) =
        Entry(id, 1, kind, "Entry $id", day = start.toString(), time = "09:30", repeat = repeat)

    @Test fun weeklyRecurrenceUsesLocalDatesAcrossClockChangeAndHonoursEndDate() {
        val schedule = entry(1, repeat = Repeat.WEEKLY).copy(endDay = "2026-11-01")
        assertTrue(schedule.occursOn(start))
        assertTrue(schedule.occursOn(start.plusWeeks(1)))
        assertFalse(schedule.occursOn(start.plusDays(1)))
        assertFalse(schedule.occursOn(start.plusWeeks(2)))
    }

    @Test fun selectedWeekdaysRespectBothScheduleBoundaries() {
        val weekdays = java.time.DayOfWeek.MONDAY.mask() or java.time.DayOfWeek.FRIDAY.mask()
        val schedule = entry(1, repeat = Repeat.SELECTED_DAYS).copy(weekdays = weekdays, endDay = "2026-11-02")
        assertFalse(schedule.occursOn(start.minusDays(2)))
        assertFalse(schedule.occursOn(start))
        assertTrue(schedule.occursOn(start.plusDays(1)))
        assertFalse(schedule.occursOn(start.plusDays(2)))
        assertTrue(schedule.occursOn(start.plusDays(5)))
        assertTrue(schedule.occursOn(start.plusDays(8)))
        assertFalse(schedule.occursOn(start.plusDays(12)))
        assertFalse(schedule.copy(weekdays = 0).occursOn(start.plusDays(1)))
    }

    @Test fun correctionOverridesOnlyTheSpecifiedOccurrence() {
        val entry = entry(1, Kind.MEDICATION)
        val taken = ActivityRecord(1, 1, 1, entry.title, entry.kind, start.toString(), Outcome.TAKEN)
        val undone = taken.copy(id = 2, outcome = Outcome.UNCONFIRMED)
        val result = occurrences(listOf(entry), listOf(undone, taken), start, start.plusDays(1))
        assertEquals(listOf(Outcome.UNCONFIRMED, Outcome.UNCONFIRMED), result.map { it.outcome })
        assertTrue(result.first().isLate(start.atTime(10, 0)))
    }

    @Test fun handoverUsesLatestStatusAndSeparatesMedicationFromCompletedTasks() {
        val time = start.atStartOfDay(ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        val task = entry(1)
        val medication = entry(2, Kind.MEDICATION)
        val done = ActivityRecord(1, 1, 1, task.title, task.kind, start.toString(), Outcome.COMPLETED, time + 500)
        val given = done.copy(id = 2, entryId = 2, title = medication.title, kind = Kind.MEDICATION, outcome = Outcome.TAKEN)
        val report = handoverItems(listOf(task, medication), listOf(done, given), time, time + 1000, start, ZoneId.of("Europe/London"))
        assertEquals(2, report.size)
        assertEquals(Outcome.COMPLETED, report.single { it.section == HandoverSection.COMPLETED }.outcome)
        assertEquals(Outcome.TAKEN, report.single { it.section == HandoverSection.MEDICATION }.outcome)
    }

    @Test fun assistantOnlyParsesSupportedCommandsAndRequiresTaskContent() {
        val phrases = mapOf(Command.ADD_TASK to listOf("añadir una tarea"), Command.TODAY to listOf("mostrar hoy"))
        assertEquals(IntentDraft(Command.ADD_TASK, "Comprar fruta"), parseIntent("Añadir una tarea Comprar fruta", phrases))
        assertEquals(IntentDraft(Command.TODAY), parseIntent("mostrar hoy?", phrases))
        assertNull(parseIntent("añadir una tarea", phrases))
        assertNull(parseIntent("delete all records", phrases))
    }

    @Test fun assistantHandlesJapanesePayloadAndApostropheVariants() {
        val japanese = mapOf(Command.ADD_NOTE to listOf("メモを追加"), Command.TODAY to listOf("今日のタスクを表示"))
        assertEquals(IntentDraft(Command.ADD_NOTE, "庭を散歩"), parseIntent("メモを追加：庭を散歩", japanese, true))
        assertEquals(IntentDraft(Command.TODAY), parseIntent("今日のタスクを表示？", japanese, true))
        assertNull(parseIntent("メモを追加", japanese, true))
        val italian = mapOf(Command.ADD_TASK to listOf("aggiungi un’attività"))
        assertEquals(IntentDraft(Command.ADD_TASK, "Preparare il pranzo"), parseIntent("aggiungi un'attività Preparare il pranzo", italian))
    }
}
