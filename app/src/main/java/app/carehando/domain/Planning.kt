package app.carehando.domain

import app.carehando.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Occurrence(val entry: Entry, val date: LocalDate, val outcome: Outcome = Outcome.UNCONFIRMED) {
    val finished: Boolean get() = outcome != Outcome.UNCONFIRMED

    fun isLate(now: LocalDateTime): Boolean = !finished &&
        if (entry.time.isEmpty()) date < now.toLocalDate() else date.atTime(LocalTime.parse(entry.time)) < now
}

fun DayOfWeek.mask(): Int = 1 shl (value - 1)

fun Entry.occursOn(date: LocalDate): Boolean {
    val start = LocalDate.parse(day)
    if (date < start || (endDay.isNotEmpty() && date > LocalDate.parse(endDay))) return false
    return when (repeat) {
        Repeat.ONCE -> date == start
        Repeat.DAILY -> true
        Repeat.WEEKLY -> ChronoUnit.DAYS.between(start, date) % 7L == 0L
        Repeat.SELECTED_DAYS -> (weekdays and date.dayOfWeek.mask()) != 0
    }
}

fun occurrences(
    entries: List<Entry>,
    activity: List<ActivityRecord>,
    start: LocalDate,
    end: LocalDate,
): List<Occurrence> {
    val outcomes = activity.sortedByDescending { it.id }.distinctBy { it.entryId to it.day }
        .associateBy { it.entryId to it.day }
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.flatMap { date ->
        entries.asSequence().filter { !it.paused && it.kind != Kind.JOURNAL && it.occursOn(date) }.map { entry ->
            Occurrence(entry, date, outcomes[entry.id to date.toString()]?.outcome ?: Outcome.UNCONFIRMED)
        }
    }.sortedWith(compareBy<Occurrence> { it.date }.thenBy { it.entry.time }.thenByDescending { it.entry.priority }).toList()
}

fun handoverItems(
    entries: List<Entry>,
    activity: List<ActivityRecord>,
    since: Long,
    now: Long,
    today: LocalDate,
    zone: ZoneId,
): List<HandoverItem> {
    val previousDate = java.time.Instant.ofEpochMilli(since).atZone(zone).toLocalDate()
    val firstDay = maxOf(today.minusDays(30), minOf(previousDate, today))
    val changes = activity.sortedByDescending { it.id }.distinctBy { it.entryId to it.day }
        .filter { it.at > since && it.at <= now && it.outcome != Outcome.UNCONFIRMED }
    return buildList {
        changes.forEach {
            val section = if (it.kind == Kind.MEDICATION) HandoverSection.MEDICATION else HandoverSection.COMPLETED
            add(HandoverItem(section = section, title = it.title, day = it.day, outcome = it.outcome))
        }
        occurrences(entries, activity, firstDay, today).filter { !it.finished }.forEach {
            val section = when (it.entry.kind) {
                Kind.TASK, Kind.ROUTINE -> HandoverSection.OUTSTANDING
                Kind.MEDICATION -> HandoverSection.MEDICATION
                else -> return@forEach
            }
            add(HandoverItem(section = section, title = it.entry.title, day = it.date.toString(),
                time = it.entry.time, outcome = it.outcome))
        }
        occurrences(entries.filter { it.kind == Kind.APPOINTMENT }, activity, today, today.plusDays(14))
            .filter { !it.finished }.forEach {
                add(HandoverItem(section = HandoverSection.APPOINTMENTS, title = it.entry.title,
                    day = it.date.toString(), time = it.entry.time))
            }
        entries.filter { it.kind == Kind.JOURNAL && it.recordedAt > since && it.recordedAt <= now }.forEach {
            add(HandoverItem(section = HandoverSection.OBSERVATIONS, title = it.title, day = it.day))
        }
    }
}

enum class Command { TODAY, APPOINTMENTS, MEDICATION, HANDOVER, ADD_TASK, ADD_NOTE }

data class IntentDraft(val command: Command, val text: String = "")

fun parseIntent(text: String, phrases: Map<Command, List<String>>, allowUnspacedPayload: Boolean = false): IntentDraft? {
    val input = text.trim().replace('’', '\'')
    for ((command, aliases) in phrases) {
        for (alias in aliases) {
            val prefix = alias.replace('’', '\'')
            if (command == Command.ADD_TASK || command == Command.ADD_NOTE) {
                if (input.startsWith(prefix, ignoreCase = true) &&
                    (allowUnspacedPayload || input.getOrNull(prefix.length)?.isWhitespace() == true)) {
                    val content = text.trim().drop(prefix.length).trimStart { it.isWhitespace() || it in ":：、" }
                    if (content.isNotEmpty()) return IntentDraft(command, content.take(160))
                }
            } else if (input.trimEnd('?', '.', '!', '؟', '。', '？', '！').equals(prefix, ignoreCase = true)) {
                return IntentDraft(command)
            }
        }
    }
    return null
}
