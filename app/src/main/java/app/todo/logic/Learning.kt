package app.todo.logic

import app.todo.data.BestTime
import app.todo.data.Completion
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import kotlin.math.pow

/** The numbers Layer 1 runs on, in one place so they're easy to tune after real use. */
object Tuning {
    /** Old counts fade by half every 2 weeks. */
    const val HALF_LIFE_DAYS = 14.0
    /** Rows older than this weigh under 3% and aren't read. */
    const val HISTORY_DAYS = 70L
    /** About 2 recent Not nows in a spot or place, after fading, push an item below "whenever" there. */
    const val PUT_OFF_LIMIT = 1.8
    /** Where a pushed-down item ranks: just below "whenever" (4.0), above someday (5.0). */
    const val BELOW_WHENEVER = 4.5
    /**
     * About 2 Dones in a spot or place within the last 2 weeks, at least half of all its Dones, count as "usually
     * done there". Lower than [PUT_OFF_LIMIT] so a once-a-week habit is noticed after two weeks.
     */
    const val USUAL_DONE = 1.2
    /** How far "usually done there" lifts an item. */
    const val RISE = 0.3
}

/** One logged moment for an item: a Done, a Not now, or a day it was shown as Best fit. */
data class Moment(
    val itemId: String,
    val at: Long,
    val slot: BestTime?,
    val weekend: Boolean,
    val place: String?,
    val kind: Kind,
) {
    enum class Kind { DONE, PUT_OFF, SHOWN }
}

/** What the history says about one item right now. "Spot" is the slot and day type, e.g. weekday evenings. */
data class Here(
    val putOffSpot: Double = 0.0,
    val putOffPlace: Double = 0.0,
    val doneSpot: Double = 0.0,
    val donePlace: Double = 0.0,
    val doneAll: Double = 0.0,
) {
    val putOff get() = (putOffSpot >= Tuning.PUT_OFF_LIMIT && putOffSpot > doneSpot) ||
        (putOffPlace >= Tuning.PUT_OFF_LIMIT && putOffPlace > donePlace)
    val usualSpot get() = doneSpot >= Tuning.USUAL_DONE && doneSpot * 2 >= doneAll && doneSpot > putOffSpot
    val usualPlace get() = donePlace >= Tuning.USUAL_DONE && donePlace * 2 >= doneAll && donePlace > putOffPlace
}

/**
 * Layer 1, "learn quietly": each Done and Not now is kept with its slot, weekday or weekend, and place, and fades
 * by half every two weeks. Runs on the phone only.
 */
class Learning(moments: List<Moment>) {
    private val byItem = moments.groupBy { it.itemId }

    fun here(itemId: String, ctx: Ctx): Here {
        val list = byItem[itemId] ?: return Here()
        val slot = Rules.slotNow(ctx)
        val weekend = Rules.isWeekend(ctx)
        var h = Here()
        for (m in list) {
            if (m.kind == Moment.Kind.SHOWN) continue
            val w = weight(m.at, ctx.nowMs)
            val spot = m.slot == slot && m.weekend == weekend
            val place = ctx.place != null && m.place == ctx.place
            h = if (m.kind == Moment.Kind.DONE) h.copy(
                doneSpot = h.doneSpot + if (spot) w else 0.0,
                donePlace = h.donePlace + if (place) w else 0.0,
                doneAll = h.doneAll + w,
            ) else h.copy(
                putOffSpot = h.putOffSpot + if (spot) w else 0.0,
                putOffPlace = h.putOffPlace + if (place) w else 0.0,
            )
        }
        return h
    }

    /** "on weekend mornings" or "at home", when the item usually gets done right here. Spot wins over place. */
    fun usual(itemId: String, ctx: Ctx): String? {
        val h = here(itemId, ctx)
        return when {
            h.usualSpot -> "on " + (if (Rules.isWeekend(ctx)) "weekend " else "weekday ") + when (Rules.slotNow(ctx)) {
                BestTime.MORNING -> "mornings"
                BestTime.AFTERNOON -> "afternoons"
                else -> "evenings"
            }
            h.usualPlace -> ctx.place?.let { placePhrase(it) }
            else -> null
        }
    }

    companion object {
        val NONE = Learning(emptyList())

        fun weight(at: Long, nowMs: Long): Double =
            0.5.pow(((nowMs - at).coerceAtLeast(0) / 86_400_000.0) / Tuning.HALF_LIFE_DAYS)

        fun placePhrase(key: String) = when (key) {
            "home" -> "at home"
            "work" -> "at work"
            "out" -> "when you're out"
            else -> "at the " + key.replace('-', ' ')
        }

        /** Dones come from completions; the DONE rows in nudges repeat them, so they're skipped. */
        fun from(completions: List<Completion>, nudges: List<NudgeEvent>): Learning {
            fun slot(s: String?) = BestTime.entries.firstOrNull { it.name == s && it.isSlot }
            val done = completions.mapNotNull { c ->
                c.taskId?.let { Moment(it, c.at, slot(c.slot), c.weekend, c.placeLabel, Moment.Kind.DONE) }
            }
            val rest = nudges.mapNotNull { n ->
                val kind = when (n.outcome) {
                    NudgeOutcome.SNOOZED, NudgeOutcome.DISMISSED -> Moment.Kind.PUT_OFF
                    NudgeOutcome.SHOWN -> Moment.Kind.SHOWN
                    NudgeOutcome.DONE -> null
                }
                kind?.let { Moment(n.taskId, n.at, slot(n.slot), n.weekend, n.placeLabel, it) }
            }
            return Learning(done + rest)
        }
    }
}
