package app.todo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class Bucket { MUST, NICE, SOMEDAY }
enum class Effort { LOW, MED, HIGH }
enum class Where { HOME, OUT, BEFORE_LEAVING, ANYWHERE }
enum class CompletionSource { MANUAL, HEALTH_CONNECT }
enum class NudgeOutcome { DONE, SNOOZED, DISMISSED }

/** A one-line task. IDs are random so an export never reveals ordering or identity. */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val bucket: Bucket = Bucket.NICE,
    val minutes: Int? = null,
    val effort: Effort = Effort.MED,
    val where: Where = Where.ANYWHERE,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null,
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
