package app.todo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** When a task should get done. Ordered from most to least urgent; SOMEDAY is hidden from suggestions. */
enum class Timing { ASAP, THIS_WEEK, THIS_MONTH, THIS_QUARTER, ANYTIME, SOMEDAY }

enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY }

/** Rough size of a task. Stored as minutes; the size is what the user picks. */
enum class Size(val minutes: Int, val label: String) {
    UNDER_10(10, "< 10 min"), UNDER_30(30, "< 30 min"), UNDER_60(60, "< 1 hr"), OVER_60(90, "1 hr +");

    companion object {
        fun of(minutes: Int?): Size? = minutes?.let { m -> entries.firstOrNull { m <= it.minutes } ?: OVER_60 }
    }
}
enum class Effort { LOW, MED, HIGH }
enum class Where { HOME, OUT, BEFORE_LEAVING, ANYWHERE }
enum class CompletionSource { MANUAL, HEALTH_CONNECT }
enum class NudgeOutcome { DONE, SNOOZED, DISMISSED }

/** A one-line task. IDs are random so an export never reveals ordering or identity. */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timing: Timing = Timing.ANYTIME,
    val repeat: Repeat = Repeat.NONE,
    val minutes: Int? = null,
    val effort: Effort = Effort.MED,
    val where: Where = Where.ANYWHERE,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null,
    /** Hidden from suggestions until this time, set by "Not now" or by finishing a repeating task. */
    val snoozedUntil: Long? = null,
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

/** A weekly goal behaves like one repeating task; each completion counts toward the target. */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetPerWeek: Int,
    val minutes: Int? = null,
    val where: Where = Where.ANYWHERE,
    /** Health Connect record type that auto-counts, e.g. "EXERCISE". Null means manual only. */
    val healthConnectType: String? = null,
)

/** One row per Done, for a task or a goal. */
@Entity(tableName = "completions", indices = [Index("goalId"), Index("taskId")])
data class Completion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String? = null,
    val taskId: String? = null,
    val at: Long = System.currentTimeMillis(),
    val source: CompletionSource = CompletionSource.MANUAL,
)

/** What happened when a task was suggested. Feeds ranking now and the optional export later. */
@Entity(tableName = "nudges", indices = [Index("taskId")])
data class NudgeEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val at: Long = System.currentTimeMillis(),
    /** A label such as "home", never coordinates. */
    val placeLabel: String? = null,
    val outcome: NudgeOutcome,
)
