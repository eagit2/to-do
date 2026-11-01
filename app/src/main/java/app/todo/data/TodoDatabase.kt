package app.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Task::class, Goal::class, Completion::class, NudgeEvent::class],
    version = 1,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun goals(): GoalDao
    abstract fun nudges(): NudgeDao

    companion object {
        fun create(context: Context): TodoDatabase =
            Room.databaseBuilder(context, TodoDatabase::class.java, "todo.db").build()
    }
}
