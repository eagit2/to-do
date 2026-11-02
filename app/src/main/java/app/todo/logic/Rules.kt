package app.todo.logic

import app.todo.data.BestTime
import app.todo.data.By
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.Size
import app.todo.data.placeList
import app.todo.data.timeSet
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything the ranking needs to know about "now". Pure data so the rules run in plain unit tests.
 * [place] is a place key such as "home", "out" when away from every saved place, or null when unknown
 * (then items with places still count as fitting, so nothing vanishes while places aren't set up).
 */
data class Ctx(
    val now: LocalDateTime,
    val place: String? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Crossed-off items come back at this hour. A later hour suits late nights. */
    val dayStartHour: Int = 0,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Times nudges were paused. Paused time doesn't count toward pace, so nothing falls behind on a trip. */
    val pauses: List<Pause> = emptyList(),
) {
    val nowMs: Long get() = now.atZone(zone).toInstant().toEpochMilli()

    /** The app's "today", which starts at [dayStartHour]. */
    val today: LocalDate get() = now.minusHours(dayStartHour.toLong()).toLocalDate()

    fun dayOf(ms: Long): LocalDate =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).minusHours(dayStartHour.toLong()).toLocalDate()
}

/** A stretch of time with nudges paused, in epoch ms. */
data class Pause(val from: Long, val until: Long)

/**
 * [possible] is the most still reachable this period. When it's under the target the goal has slid to it
 * ([sliding]): the item shows "2 of 2 possible" and never reads as behind.
 */
data class Pace(val expected: Double, val behind: Double, val left: Int, val possible: Int, val sliding: Boolean)

data class Tag(val kind: TagKind, val text: String, val warm: Boolean = false)

enum class TagKind { GARMIN, DONE, REPEAT, BEHIND, DATE, ASAP, BY, SIZE, TIME, EVENING, PLACE, SNOOZED, HIDDEN }

object Rules {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun periodStart(per: Per, ctx: Ctx): LocalDate = when (per) {
        Per.DAY -> ctx.today
        Per.WEEK -> ctx.today.with(TemporalAdjusters.previousOrSame(ctx.weekStart))
        Per.MONTH -> ctx.today.withDayOfMonth(1)
        Per.YEAR -> ctx.today.withDayOfYear(1)
    }

    fun periodKey(per: Per, ctx: Ctx): String = per.name + ":" + periodStart(per, ctx).format(iso)

    /** The first day of the next period. */
    fun periodEnd(per: Per, ctx: Ctx): LocalDate = periodStart(per, ctx).let {
        when (per) {
            Per.DAY -> it.plusDays(1)
            Per.WEEK -> it.plusWeeks(1)
            Per.MONTH -> it.plusMonths(1)
            Per.YEAR -> it.plusYears(1)
        }
    }

    /** Share of the current period gone, counting the current hour as started. Paused time doesn't count as gone. */
    fun periodShare(per: Per, ctx: Ctx): Double {
        val start = periodStart(per, ctx).atTime(ctx.dayStartHour, 0)
        val end = periodEnd(per, ctx).atTime(ctx.dayStartHour, 0)
        val goneEnd = ctx.now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val total = Duration.between(start, end).toMinutes().toDouble()
        val startMs = start.atZone(ctx.zone).toInstant().toEpochMilli()
        val goneEndMs = goneEnd.atZone(ctx.zone).toInstant().toEpochMilli()
        val paused = ctx.pauses.sumOf { p -> (minOf(p.until, goneEndMs) - maxOf(p.from, startMs)).coerceAtLeast(0) } / 60_000.0
        val gone = Duration.between(start, goneEnd).toMinutes().toDouble() - paused
        return (gone / total).coerceIn(0.0, 1.0)
    }

    /**
     * Starts, changes or ends a pause ([untilMs] null ends it). A pause still running is cut at [nowMs] first, so the
     * history only holds time that was really paused. Pauses older than 400 days are dropped.
     */
    fun pausesWith(pauses: List<Pause>, untilMs: Long?, nowMs: Long): List<Pause> {
        val keepFrom = nowMs - 400L * 86_400_000
        val past = pauses.map { if (it.until > nowMs) it.copy(until = nowMs) else it }.filter { it.until > it.from && it.until > keepFrom }
        return if (untilMs != null && untilMs > nowMs) past + Pause(nowMs, untilMs) else past
    }

    fun isYearly(t: Item) = t.mode == Mode.REPEAT && t.period == Per.YEAR

    /** Times done in the current period. A new week (or day, month) starts back at 0. */
    fun doneNow(t: Item, ctx: Ctx): Int = if (t.periodKey == periodKey(t.period, ctx)) t.doneCount else 0

    /**
     * The most a repeating item can still reach this period. Weekly and monthly items cross off for the day once
     * ticked, so at most one more per day left (today counts until it's ticked). Daily items aren't capped.
     */
    fun possible(t: Item, ctx: Ctx): Int {
        if (t.period == Per.DAY || t.period == Per.YEAR) return t.target
        val done = doneNow(t, ctx)
        val days = ChronoUnit.DAYS.between(ctx.today, periodEnd(t.period, ctx)).toInt() - if (doneToday(t, ctx)) 1 else 0
        return minOf(t.target, done + days.coerceAtLeast(0))
    }

    /** A goal slides to what's still reachable, so a slow week never piles up as "3 behind" on Sunday night. */
    fun pace(t: Item, ctx: Ctx): Pace {
        val done = doneNow(t, ctx)
        val possible = possible(t, ctx)
        val sliding = possible < t.target
        val expected = minOf(t.target * periodShare(t.period, ctx), possible.toDouble())
        return Pace(expected, if (sliding) 0.0 else expected - done, (possible - done).coerceAtLeast(0), possible, sliding)
    }

    fun dueDate(t: Item): LocalDate? = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun dueAt(t: Item): LocalDateTime? {
        val d = dueDate(t) ?: return null
        val time = t.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(23, 59)
        return d.atTime(time)
    }

    fun daysLeft(t: Item, ctx: Ctx): Double? =
        dueAt(t)?.let { Duration.between(ctx.now, it).toMinutes() / 1440.0 }

    /** A yearly item stays out of the way until [Item.leadMonths] months before it's due. */
    fun showFrom(t: Item): LocalDate? = dueDate(t)?.minusMonths(t.leadMonths.toLong())

    fun yearlyHidden(t: Item, ctx: Ctx): Boolean = isYearly(t) && showFrom(t)?.let { ctx.today < it } == true

    /** A repeating item ticked today is crossed off for the rest of the day, unless it repeats several times a day. */
    fun doneToday(t: Item, ctx: Ctx): Boolean =
        t.mode == Mode.REPEAT && t.doneDay == ctx.today.format(iso) &&
            !(t.period == Per.DAY && doneNow(t, ctx) < t.target)

    /** Shown with a filled tick and struck-through title. */
    fun isChecked(t: Item, ctx: Ctx): Boolean = when (t.mode) {
        Mode.REPEAT -> doneToday(t, ctx) || (!isYearly(t) && doneNow(t, ctx) >= t.target)
        Mode.ONCE -> t.doneAt != null
    }

    fun snoozed(t: Item, ctx: Ctx) = t.snoozedUntil != null && t.snoozedUntil > ctx.nowMs

    fun slotNow(ctx: Ctx): BestTime = when {
        ctx.now.hour < 12 -> BestTime.MORNING
        ctx.now.hour < 17 -> BestTime.AFTERNOON
        else -> BestTime.EVENING
    }

    fun isWeekend(ctx: Ctx) = ctx.now.dayOfWeek == DayOfWeek.SATURDAY || ctx.now.dayOfWeek == DayOfWeek.SUNDAY

    fun fitsTime(t: Item, ctx: Ctx): Boolean {
        val set = t.timeSet
        val slots = set.filter { it.isSlot }
        val days = set.filter { !it.isSlot }
        val okSlot = slots.isEmpty() || slotNow(ctx) in slots
        val okDay = days.isEmpty() || (if (isWeekend(ctx)) BestTime.WEEKENDS else BestTime.WEEKDAYS) in days
        return okSlot && okDay
    }

    fun fitsPlace(t: Item, ctx: Ctx): Boolean = ctx.place == null || t.placeList.isEmpty() || ctx.place in t.placeList

    /** A dated deadline gets more urgent as it gets close: under a day beats ASAP, under a week ranks with this week. */
    fun dateUrgency(t: Item, ctx: Ctx): Double {
        val days = daysLeft(t, ctx) ?: return 4.0
        return when {
            days < 1 -> -0.5
            days < 3 -> 0.8
            days < 7 -> 1.2
            days < 31 -> 2.2
            else -> 3.2
        }
    }

    private fun byOrder(b: By) = when (b) {
        By.ASAP -> 0.0; By.THIS_WEEK -> 1.0; By.THIS_MONTH -> 2.0; By.THIS_QUARTER -> 3.0
        By.WHENEVER, By.DATE -> 4.0; By.SOMEDAY -> 5.0
    }

    /** Lower = more urgent. Repeating items behind pace rise; one-offs rank by their deadline. Nothing ever turns red. */
    fun urgency(t: Item, ctx: Ctx): Double {
        if (isYearly(t)) return dateUrgency(t, ctx)
        if (t.mode == Mode.REPEAT) {
            val p = pace(t, ctx)
            return when {
                p.left == 0 -> 99.0
                p.sliding || p.behind >= 1 -> 0.5
                p.behind > 0 -> 1.5
                else -> 3.5
            }
        }
        return if (t.byWhen == By.DATE) dateUrgency(t, ctx) else byOrder(t.byWhen)
    }

    fun sorted(items: List<Item>, ctx: Ctx): List<Item> =
        items.sortedWith(compareBy<Item>({ urgency(it, ctx) }, { it.createdAt }))

    /** What could be suggested right now, best first. */
    fun candidates(items: List<Item>, ctx: Ctx, skipped: Set<String> = emptySet()): List<Item> =
        sorted(items, ctx).filter { t ->
            !(t.mode == Mode.ONCE && (t.byWhen == By.SOMEDAY || t.doneAt != null)) &&
                !(t.mode == Mode.REPEAT && !isYearly(t) && pace(t, ctx).left == 0) &&
                !yearlyHidden(t, ctx) && !doneToday(t, ctx) &&
                t.id !in skipped && !snoozed(t, ctx) && fitsTime(t, ctx) && fitsPlace(t, ctx)
        }

    /** Folded into "Someday and later": someday items and yearly items that aren't due to show yet. */
    fun isLater(t: Item, ctx: Ctx): Boolean =
        (t.mode == Mode.ONCE && t.byWhen == By.SOMEDAY && t.doneAt == null) || (yearlyHidden(t, ctx) && !doneToday(t, ctx))

    /** A finished one-off stays on the list, crossed off, for the rest of the day it was done. */
    fun onList(t: Item, ctx: Ctx): Boolean = t.mode != Mode.ONCE || t.doneAt == null || ctx.dayOf(t.doneAt) == ctx.today

    /** Marking done never deletes: a one-off is crossed off, a repeat counts up, a yearly item rolls to next year. */
    fun finished(t: Item, ctx: Ctx): Item {
        val today = ctx.today.format(iso)
        return when {
            isYearly(t) -> t.copy(dueDate = dueDate(t)?.plusYears(1)?.format(iso) ?: t.dueDate, doneDay = today)
            t.mode == Mode.REPEAT -> t.copy(
                doneCount = doneNow(t, ctx) + 1, periodKey = periodKey(t.period, ctx), doneDay = today,
            )
            else -> t.copy(doneAt = ctx.nowMs)
        }
    }

    /** "Mark as not done?" from a crossed-off item, working from the item as it is now. */
    fun reverted(t: Item, ctx: Ctx): Item = when {
        isYearly(t) -> t.copy(dueDate = dueDate(t)?.minusYears(1)?.format(iso) ?: t.dueDate, doneDay = null)
        t.mode == Mode.REPEAT -> t.copy(
            doneCount = (doneNow(t, ctx) - 1).coerceAtLeast(0), periodKey = periodKey(t.period, ctx), doneDay = null,
        )
        else -> t.copy(doneAt = null)
    }

    fun periodText(per: Per) = if (per == Per.DAY) "today" else "this " + per.label

    fun clock(hour: Int, minute: Int = 0): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return h12.toString() + (if (minute == 0) "" else ":" + minute.toString().padStart(2, '0')) + if (hour < 12) " am" else " pm"
    }

    fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

    fun dueLabel(t: Item): String {
        val at = dueAt(t) ?: return "pick a date"
        var txt = "due " + at.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        if (t.dueTime != null) txt += ", " + clock(at.hour, at.minute)
        return txt
    }

    /** Small tags under an item. Warm ones (due within 3 days, behind pace, ASAP) stand out without ever turning red. */
    fun tags(t: Item, ctx: Ctx): List<Tag> {
        val out = mutableListOf<Tag>()
        if (doneToday(t, ctx) || (t.mode == Mode.ONCE && t.doneAt != null)) out += Tag(TagKind.DONE, "done today")
        when {
            isYearly(t) -> {
                val dl = daysLeft(t, ctx)
                out += Tag(TagKind.REPEAT, "yearly · " + dueLabel(t), dl != null && dl < 14 && !doneToday(t, ctx))
                if (yearlyHidden(t, ctx)) showFrom(t)?.let { out += Tag(TagKind.HIDDEN, "shows from " + shortDate(it)) }
            }
            t.mode == Mode.REPEAT -> {
                val p = pace(t, ctx)
                out += Tag(TagKind.REPEAT, "${doneNow(t, ctx)} of " + (if (p.sliding) "${p.possible} possible" else "${t.target}") + " " + periodText(t.period))
                if (p.left > 0 && p.behind >= 1 && !doneToday(t, ctx)) {
                    out += Tag(TagKind.BEHIND, "about ${p.behind.roundToInt()} behind", warm = true)
                }
            }
            t.doneAt == null -> when (t.byWhen) {
                By.DATE -> out += Tag(TagKind.DATE, dueLabel(t), (daysLeft(t, ctx) ?: 99.0) < 3)
                By.ASAP -> out += Tag(TagKind.ASAP, "ASAP", warm = true)
                else -> out += Tag(TagKind.BY, t.byWhen.label)
            }
        }
        Size.of(t.minutes)?.let { out += Tag(TagKind.SIZE, it.label) }
        val times = t.timeSet
        if (times.isNotEmpty()) {
            val slots = times.filter { it.isSlot }
            val evening = slots.isNotEmpty() && slots.all { it == BestTime.EVENING }
            out += Tag(if (evening) TagKind.EVENING else TagKind.TIME, BestTime.entries.filter { it in times }.joinToString(", ") { it.label })
        }
        if (t.placeList.isNotEmpty()) out += Tag(TagKind.PLACE, t.placeList.joinToString(", "))
        if (snoozed(t, ctx)) out += Tag(TagKind.SNOOZED, "snoozed")
        return out
    }

    /** The one line under the top pick that says why it's first. */
    fun why(t: Item, ctx: Ctx): String {
        if (isYearly(t)) return "Yearly, due " + (dueDate(t)?.let { shortDate(it) } ?: "soon") + ". It moves up as the date gets close."
        if (t.mode == Mode.REPEAT) {
            val p = pace(t, ctx)
            return if (p.sliding) "${p.left} more still fits ${periodText(t.period)}, so it's first."
            else if (p.behind >= 1) "About ${p.behind.roundToInt()} behind pace ${periodText(t.period)}, so it's first."
            else "On pace, and it fits this time and place."
        }
        return when (t.byWhen) {
            By.DATE -> "Deadline " + dueLabel(t).removePrefix("due ") + "."
            By.ASAP -> "You marked it ASAP."
            By.WHENEVER -> "It fits this time and place."
            else -> "Due " + t.byWhen.label + "."
        }
    }

    /** The free time we're in right now, if any. Windows can run past midnight. */
    fun windowNow(windows: List<FreeWindow>, ctx: Ctx): FreeWindow? {
        val dayBit = 1 shl (ctx.now.dayOfWeek.value - 1)
        val minute = ctx.now.hour * 60 + ctx.now.minute
        return windows.firstOrNull { w ->
            w.days and dayBit != 0 && when {
                w.startMinute == w.endMinute -> false
                w.startMinute < w.endMinute -> minute >= w.startMinute && minute < w.endMinute
                else -> minute >= w.startMinute || minute < w.endMinute
            }
        }
    }

    const val WEEKDAYS = 31
    const val WEEKENDS = 96
    const val EVERYDAY = 127
    val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun daysLabel(d: Int): String = when (d) {
        EVERYDAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> DAY_NAMES.filterIndexed { i, _ -> d and (1 shl i) != 0 }.joinToString(" ")
    }
}
