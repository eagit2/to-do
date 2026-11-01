package app.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Goal::class, Completion::class, NudgeEvent::class, FreeWindow::class],
    version = 2,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun goals(): GoalDao
    abstract fun nudges(): NudgeDao
    abstract fun freeWindows(): FreeWindowDao

    companion object {
        fun create(context: Context): TodoDatabase =
            Room.databaseBuilder(context, TodoDatabase::class.java, "todo.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** must/nice/someday becomes "when": must -> ASAP, nice -> ANYTIME, someday -> SOMEDAY. Adds repeat, snooze and free windows. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE `tasks_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `timing` TEXT NOT NULL,
                       `repeat` TEXT NOT NULL, `minutes` INTEGER, `effort` TEXT NOT NULL, `where` TEXT NOT NULL,
                       `createdAt` INTEGER NOT NULL, `doneAt` INTEGER, `snoozedUntil` INTEGER, PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """INSERT INTO `tasks_new` (`id`, `title`, `timing`, `repeat`, `minutes`, `effort`, `where`, `createdAt`, `doneAt`, `snoozedUntil`)
                       SELECT `id`, `title`,
                              CASE `bucket` WHEN 'MUST' THEN 'ASAP' WHEN 'SOMEDAY' THEN 'SOMEDAY' ELSE 'ANYTIME' END,
                              'NONE', `minutes`, `effort`, `where`, `createdAt`, `doneAt`, NULL
                       FROM `tasks`"""
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `free_windows` (`id` TEXT NOT NULL, `days` INTEGER NOT NULL,
                       `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
                )
            }
        }
    }
}
