package app.todo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE doneAt IS NULL ORDER BY bucket, createdAt")
    fun openTasks(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}

/** A goal with how many times it was done since the start of the week. */
data class GoalProgress(
    @Embedded val goal: Goal,
    val doneThisWeek: Int,
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY title")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT COUNT(*) FROM completions WHERE goalId = :goalId AND at >= :since")
    fun countSince(goalId: String, since: Long): Flow<Int>

    @Query(
        """SELECT goals.*, (SELECT COUNT(*) FROM completions c
               WHERE c.goalId = goals.id AND c.at >= :weekStart) AS doneThisWeek
           FROM goals ORDER BY title"""
    )
    fun progress(weekStart: Long): Flow<List<GoalProgress>>

    @Delete
    suspend fun delete(goal: Goal)

    @Query("DELETE FROM completions WHERE goalId = :goalId")
    suspend fun deleteCompletions(goalId: String)

    @Insert
    suspend fun insert(goal: Goal)

    @Insert
    suspend fun insertCompletion(completion: Completion)
}

@Dao
interface NudgeDao {
    @Insert
    suspend fun insert(event: NudgeEvent)

    @Query("SELECT * FROM nudges WHERE taskId = :taskId ORDER BY at DESC")
    suspend fun forTask(taskId: String): List<NudgeEvent>
}
