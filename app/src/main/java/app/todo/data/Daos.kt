package app.todo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt")
    fun all(): Flow<List<Item>>

    @Query("SELECT * FROM items")
    suspend fun snapshot(): List<Item>

    @Upsert
    suspend fun upsert(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM items")
    suspend fun clear()
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY sortOrder, label")
    fun all(): Flow<List<Place>>

    @Query("SELECT * FROM places")
    suspend fun snapshot(): List<Place>

    @Upsert
    suspend fun upsert(place: Place)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(places: List<Place>)

    @Query("DELETE FROM places")
    suspend fun clear()
}

@Dao
interface CompletionDao {
    @Insert
    suspend fun insert(completion: Completion)

    @Query("DELETE FROM completions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM completions WHERE at >= :since")
    fun since(since: Long): Flow<List<Completion>>

    /** The item's latest Done, removed by "Mark as not done?". */
    @Query("SELECT * FROM completions WHERE taskId = :itemId ORDER BY at DESC LIMIT 1")
    suspend fun latest(itemId: String): Completion?

    @Query("DELETE FROM completions")
    suspend fun clear()
}

@Dao
interface NudgeDao {
    @Insert
    suspend fun insert(event: NudgeEvent)

    @Query("DELETE FROM nudges WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM nudges WHERE at >= :since")
    fun since(since: Long): Flow<List<NudgeEvent>>

    @Query("SELECT COUNT(*) FROM nudges WHERE taskId = :itemId AND outcome = 'SHOWN' AND at >= :since")
    suspend fun shownSince(itemId: String, since: Long): Int

    /** The Done row logged with a completion shares its time, so "Mark as not done?" can remove both. */
    @Query("DELETE FROM nudges WHERE taskId = :itemId AND outcome = 'DONE' AND at = :at")
    suspend fun deleteDoneAt(itemId: String, at: Long)

    @Query("SELECT * FROM nudges WHERE taskId = :itemId ORDER BY at DESC")
    suspend fun forItem(itemId: String): List<NudgeEvent>

    @Query("DELETE FROM nudges")
    suspend fun clear()
}

@Dao
interface FreeWindowDao {
    @Query("SELECT * FROM free_windows ORDER BY startMinute")
    fun all(): Flow<List<FreeWindow>>

    @Query("SELECT * FROM free_windows")
    suspend fun snapshot(): List<FreeWindow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: FreeWindow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(windows: List<FreeWindow>)

    @Delete
    suspend fun delete(window: FreeWindow)

    @Query("DELETE FROM free_windows")
    suspend fun clear()
}
