package app.todo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** An item happens once (by some time) or repeats a number of times per period. */
enum class Mode { ONCE, REPEAT }

/** When a one-off item should get done. DATE uses [Item.dueDate]; SOMEDAY is folded away and never suggested. */
enum class By(val label: String) {
    DATE("on a date"), ASAP("ASAP"), THIS_WEEK("this week"), THIS_MONTH("this month"),
    THIS_QUARTER("this quarter"), WHENEVER("whenever idle"), SOMEDAY("someday"),
}

enum class Per(val label: String) { DAY("day"), WEEK("week"), MONTH("month"), YEAR("year") }

/** Best times to suggest an item. None picked means any free time. */
enum class BestTime(val label: String) {
    MORNING("morning"), AFTERNOON("afternoon"), EVENING("evening"), WEEKDAYS("weekdays"), WEEKENDS("weekends");

    val isSlot get() = this == MORNING || this == AFTERNOON || this == EVENING
}

/** Rough size of an item. Stored as minutes; the size is what the user picks. */
enum class Size(val minutes: Int, val label: String) {
    UNDER_10(10, "< 10 min"), UNDER_30(30, "< 30 min"), UNDER_60(60, "< 1 hr"), OVER_60(90, "1 hr +");

    companion object {
        fun of(minutes: Int?): Size? = minutes?.let { m -> entries.firstOrNull { m <= it.minutes } ?: OVER_60 }
    }
}

enum class CompletionSource { MANUAL, HEALTH_CONNECT }
enum class NudgeOutcome { DONE, SNOOZED, DISMISSED }

/**
 * The one kind of thing on the list. A "goal" is just an item that repeats, e.g. 3 per week.
 * IDs are random so an export never reveals ordering or identity.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val minutes: Int? = null,
    val mode: Mode = Mode.ONCE,
    val byWhen: By = By.WHENEVER,
    /** yyyy-MM-dd. A deadline for a one-off, or the due day each year for a yearly item. */
    val dueDate: String? = null,
    /** HH:mm, optional, one-offs only. */
    val dueTime: String? = null,
    /** How many times per [period] for a repeating item. */
    val target: Int = 1,
    val period: Per = Per.WEEK,
    /** Times done in the period named by [periodKey]. A new period starts back at 0. */
    val doneCount: Int = 0,
    val periodKey: String? = null,
    /** The day (yyyy-MM-dd) a repeating item was last ticked, so it shows crossed off for the rest of that day. */
    val doneDay: String? = null,
    /** When a one-off was finished. Kept, never deleted, so it can be undone. */
    val doneAt: Long? = null,
    /** Yearly items stay hidden until this many months before they're due. */
    val leadMonths: Int = 2,
    /** Garmin workouts from Health Connect count toward this item. */
    val garmin: Boolean = false,
    /** Comma-separated [BestTime] names. */
    val times: String = "",
    /** Comma-separated place keys, e.g. "home,gym". "out" means away from every saved place. */
    val places: String = "",
    val snoozedUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

val Item.timeSet: Set<BestTime>
    get() = times.split(',').mapNotNull { n -> BestTime.entries.firstOrNull { it.name == n } }.toSet()

val Item.placeList: List<String>
    get() = places.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun Item.withTimes(set: Set<BestTime>) = copy(times = BestTime.entries.filter { it in set }.joinToString(",") { it.name })
fun Item.withPlaces(list: List<String>) = copy(places = list.distinct().joinToString(","))

/** A saved place. Set by standing there and tapping "I'm here now". The Wi-Fi name never leaves the phone. */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey val id: String,
    val label: String,
    val isSet: Boolean = false,
    val wifi: String? = null,
    val sortOrder: Int = 0,
)

/** A time the user says they are usually free, e.g. weekdays 18:00-21:00. Nudges only go out inside one. */
@Entity(tableName = "free_windows")
data class FreeWindow(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val days: Int,
    /** Minutes after midnight. */
    val startMinute: Int,
    val endMinute: Int,
)

/** One row per Done. [goalId] is kept from v1 rows; new rows use [taskId] for the item id. */
@Entity(tableName = "completions", indices = [Index("goalId"), Index("taskId")])
data class Completion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String? = null,
    val taskId: String? = null,
    val at: Long = System.currentTimeMillis(),
    val source: CompletionSource = CompletionSource.MANUAL,
)

/** What happened when an item was suggested. Feeds ranking now and the optional export later. */
@Entity(tableName = "nudges", indices = [Index("taskId")])
data class NudgeEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** The item id. */
    val taskId: String,
    val at: Long = System.currentTimeMillis(),
    /** A label such as "home", never coordinates. */
    val placeLabel: String? = null,
    val outcome: NudgeOutcome,
)
