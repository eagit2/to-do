package app.todo.parse

import app.todo.data.Effort
import app.todo.data.Goal
import app.todo.data.Repeat
import app.todo.data.Task
import app.todo.data.Timing
import app.todo.data.Where

/**
 * Reads one typed line into a task, fully on the phone.
 *
 * Cues can go anywhere in the line and are removed from the title:
 *  - time: 45m, 1h, 1h30m, 1.5h
 *  - place: @home, @out, @leaving (before leaving), @any
 *  - when: !asap, !week, !month, !quarter, !anytime, !someday (also "asap" on its own)
 *  - repeat: !daily, !weekly, !monthly
 *  - effort: ~low, ~med, ~high
 * A bare trailing "home" or "out" also counts as a place, so "mow lawn 45m home" works.
 * Anything not recognised stays in the title and can be fixed with one tap.
 */
object TaskLineParser {
    private val time = Regex("""^(?:(\d+(?:\.\d+)?)h)?(?:(\d+)m(?:in)?)?$""", RegexOption.IGNORE_CASE)

    private val places = mapOf(
        "@home" to Where.HOME, "@out" to Where.OUT,
        "@leaving" to Where.BEFORE_LEAVING, "@any" to Where.ANYWHERE,
    )
    private val bareTrailingPlaces = mapOf("home" to Where.HOME, "out" to Where.OUT)
    private val timings = mapOf(
        "!asap" to Timing.ASAP, "asap" to Timing.ASAP, "!now" to Timing.ASAP, "!must" to Timing.ASAP,
        "!week" to Timing.THIS_WEEK, "!month" to Timing.THIS_MONTH, "!quarter" to Timing.THIS_QUARTER,
        "!anytime" to Timing.ANYTIME, "!nice" to Timing.ANYTIME, "!someday" to Timing.SOMEDAY,
    )
    private val repeats = mapOf("!daily" to Repeat.DAILY, "!weekly" to Repeat.WEEKLY, "!monthly" to Repeat.MONTHLY)
    private val efforts = mapOf("~low" to Effort.LOW, "~med" to Effort.MED, "~high" to Effort.HIGH)

    fun parse(line: String): Task {
        var minutes: Int? = null
        var where = Where.ANYWHERE
        var timing = Timing.ANYTIME
        var repeat = Repeat.NONE
        var effort = Effort.MED
        val kept = mutableListOf<String>()

        for (word in line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val lower = word.lowercase()
            when {
                lower in places -> where = places.getValue(lower)
                lower in timings -> timing = timings.getValue(lower)
                lower in repeats -> repeat = repeats.getValue(lower)
                lower in efforts -> effort = efforts.getValue(lower)
                minutes == null && parseMinutes(lower) != null -> minutes = parseMinutes(lower)
                else -> kept += word
            }
        }

        // "mow lawn 45m home": a bare place word at the end, only when no @place was given.
        if (where == Where.ANYWHERE && kept.size > 1) {
            bareTrailingPlaces[kept.last().lowercase()]?.let {
                where = it
                kept.removeAt(kept.lastIndex)
            }
        }

        return Task(
            title = kept.joinToString(" ").ifEmpty { line.trim() },
            timing = timing,
            repeat = repeat,
            minutes = minutes,
            effort = effort,
            where = where,
        )
    }

    private val perWeek = Regex("""^(\d+)(?:x|/wk|/week)$""", RegexOption.IGNORE_CASE)

    /**
     * Reads a weekly goal line such as "workout 3x 45m". The count ("3x", "3/wk") defaults to 1.
     * Words like "workout", "run" or "gym" make Garmin workouts count automatically.
     */
    fun parseGoal(line: String): Goal {
        var target = 1
        val rest = line.trim().split(Regex("\\s+")).filter { word ->
            perWeek.matchEntire(word)?.let { target = it.groupValues[1].toInt().coerceIn(1, 21); false } ?: true
        }
        val task = parse(rest.joinToString(" "))
        val exercise = task.title.lowercase().split(" ").any { it in exerciseWords }
        return Goal(
            title = task.title,
            targetPerWeek = target,
            minutes = task.minutes,
            where = task.where,
            healthConnectType = if (exercise) "EXERCISE" else null,
        )
    }

    private val exerciseWords = setOf("workout", "workouts", "run", "runs", "gym", "lift", "swim", "bike", "ride", "exercise")

    internal fun parseMinutes(word: String): Int? {
        val m = time.matchEntire(word) ?: return null
        val (h, min) = m.destructured
        if (h.isEmpty() && min.isEmpty()) return null
        val total = (h.toDoubleOrNull() ?: 0.0) * 60 + (min.toIntOrNull() ?: 0)
        return total.toInt().takeIf { it > 0 }
    }
}
