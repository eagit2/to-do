package app.todo.data

import androidx.room.Dao
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
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY title")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT COUNT(*) FROM completions WHERE goalId = :goalId AND at >= :since")
    fun countSince(goalId: String, since: Long): Flow<Int>

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
