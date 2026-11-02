package app.todo.parse

import app.todo.data.BestTime
import app.todo.data.By
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.withPlaces
import app.todo.data.withTimes
import java.time.LocalDate

/**
 * Reads one typed line into an item, fully on the phone. The full sheet opens next, so anything missed is one tap away.
 *
 * Cues can go anywhere in the line and are removed from the title:
 *  - how long: 45m, 1h, 1h30m, 1.5h
 *  - repeats: 3x (3 a week), 2/day, 10/month, !daily, !weekly, !monthly, !yearly (or !annual)
 *  - by when: !today, !tomorrow, !asap (or asap), !week, !month, !quarter, !whenever, !someday
 *  - best times: !morning, !afternoon, !evening, !weekday(s), !weekend(s)
 *  - places: @home, @gym, @out, any @name
 * A bare trailing "home" or "out" also counts as a place, so "mow lawn 45m home" works.
 * Words like workout, run or gym on a repeating item make Garmin workouts count.
 */
object TaskLineParser {
    private val time = Regex("""^(?:(\d+(?:\.\d+)?)h)?(?:(\d+)m(?:in)?)?$""", RegexOption.IGNORE_CASE)
    private val perPeriod = Regex("""^(\d+)x?/(day|d|week|wk|w|month|mo|m|year|yr|y)$""")
    private val timesWeek = Regex("""^(\d+)x$""")

    private val byCues = mapOf(
        "!asap" to By.ASAP, "asap" to By.ASAP, "!now" to By.ASAP, "!must" to By.ASAP,
        "!week" to By.THIS_WEEK, "!month" to By.THIS_MONTH, "!quarter" to By.THIS_QUARTER,
        "!whenever" to By.WHENEVER, "!anytime" to By.WHENEVER, "!someday" to By.SOMEDAY,
    )
    private val timeCues = mapOf(
        "!morning" to BestTime.MORNING, "!afternoon" to BestTime.AFTERNOON, "!evening" to BestTime.EVENING,
        "!weekday" to BestTime.WEEKDAYS, "!weekdays" to BestTime.WEEKDAYS,
        "!weekend" to BestTime.WEEKENDS, "!weekends" to BestTime.WEEKENDS,
    )
    private val repeatCues = mapOf(
        "!daily" to Per.DAY, "!weekly" to Per.WEEK, "!monthly" to Per.MONTH, "!yearly" to Per.YEAR, "!annual" to Per.YEAR,
    )
    private val exercise = Regex("""^(workout|run|gym|lift|swim|bike|ride|exercise)""", RegexOption.IGNORE_CASE)

    /** The typing shortcuts, as shown in the app's help panel. */
    val help = listOf(
        "45m  1h30m" to "How long it takes",
        "3x" to "3 times a week",
        "2/day  10/month" to "Times per day, week or month",
        "!daily  !weekly  !monthly  !yearly" to "Repeats",
        "!today  !tomorrow" to "Due that day",
        "!asap  !week  !month  !quarter  !someday" to "By when",
        "!morning  !afternoon  !evening  !weekdays  !weekends" to "Best times",
        "@home  @gym  @out" to "Places",
    )

    fun parse(line: String, today: LocalDate = LocalDate.now()): Item {
        var minutes: Int? = null
        var mode = Mode.ONCE
        var by = By.WHENEVER
        var due: LocalDate? = null
        var target = 1
        var per = Per.WEEK
        val times = mutableSetOf<BestTime>()
        val places = mutableListOf<String>()
        val kept = mutableListOf<String>()

        for (word in line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val lower = word.lowercase()
            val pp = perPeriod.matchEntire(lower)
            val tw = timesWeek.matchEntire(lower)
            when {
                lower.startsWith("@") && lower.length > 1 -> lower.drop(1).takeIf { it != "any" }?.let { places += it }
                lower == "!today" || lower == "!tomorrow" -> {
                    mode = Mode.ONCE; by = By.DATE; due = if (lower == "!today") today else today.plusDays(1)
                }
                lower in byCues -> { mode = Mode.ONCE; by = byCues.getValue(lower) }
                lower in timeCues -> times += timeCues.getValue(lower)
                lower in repeatCues -> { mode = Mode.REPEAT; per = repeatCues.getValue(lower); target = 1 }
                pp != null -> {
                    mode = Mode.REPEAT
                    target = pp.groupValues[1].toInt().coerceIn(1, 31)
                    per = when (pp.groupValues[2][0]) { 'd' -> Per.DAY; 'w' -> Per.WEEK; 'y' -> Per.YEAR; else -> Per.MONTH }
                    if (per == Per.YEAR) target = 1
                }
                tw != null -> { mode = Mode.REPEAT; target = tw.groupValues[1].toInt().coerceIn(1, 31); per = Per.WEEK }
                minutes == null && parseMinutes(lower) != null -> minutes = parseMinutes(lower)
                else -> kept += word
            }
        }

        // "mow lawn 45m home": a bare place word at the end, only when no @place was given.
        if (places.isEmpty() && kept.size > 1) {
            val last = kept.last().lowercase()
            if (last == "home" || last == "out") {
                places += last
                kept.removeAt(kept.lastIndex)
            }
        }

        val title = kept.joinToString(" ").ifEmpty { line.trim() }
        return Item(
            title = title,
            minutes = minutes,
            mode = mode,
            byWhen = by,
            dueDate = due?.toString(),
            target = target,
            period = per,
            garmin = mode == Mode.REPEAT && exercise.containsMatchIn(title),
        ).withTimes(times).withPlaces(places)
    }

    internal fun parseMinutes(word: String): Int? {
        val m = time.matchEntire(word) ?: return null
        val (h, min) = m.destructured
        if (h.isEmpty() && min.isEmpty()) return null
        val total = (h.toDoubleOrNull() ?: 0.0) * 60 + (min.toIntOrNull() ?: 0)
        return total.toInt().takeIf { it > 0 }
    }
}
