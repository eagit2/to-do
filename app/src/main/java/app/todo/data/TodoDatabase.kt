package app.todo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Item::class, Place::class, Completion::class, NudgeEvent::class, FreeWindow::class],
    version = 3,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun places(): PlaceDao
    abstract fun completions(): CompletionDao
    abstract fun nudges(): NudgeDao
    abstract fun freeWindows(): FreeWindowDao

    companion object {
        /** Home, work and gym exist from the start, unset, so "I'm here now" is one tap away. */
        const val SEED_PLACES =
            "INSERT OR IGNORE INTO `places` (`id`, `label`, `isSet`, `wifi`, `sortOrder`) VALUES " +
                "('home', 'Home', 0, NULL, 0), ('work', 'Work', 0, NULL, 1), ('gym', 'Gym', 0, NULL, 2)"

        fun create(context: Context): TodoDatabase =
            Room.databaseBuilder(context, TodoDatabase::class.java, "todo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(SEED_PLACES)
                })
                .build()

        /**
         * Tasks and goals merge into one items table (design v2). Goals become items that repeat N per week;
         * a repeating task becomes 1 per day, week or month. "home"/"out" carry over as places.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `minutes` INTEGER,
                       `mode` TEXT NOT NULL, `byWhen` TEXT NOT NULL, `dueDate` TEXT, `dueTime` TEXT, `target` INTEGER NOT NULL,
                       `period` TEXT NOT NULL, `doneCount` INTEGER NOT NULL, `periodKey` TEXT, `doneDay` TEXT, `doneAt` INTEGER,
                       `leadMonths` INTEGER NOT NULL, `garmin` INTEGER NOT NULL, `times` TEXT NOT NULL, `places` TEXT NOT NULL,
                       `snoozedUntil` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """INSERT INTO `items` (`id`, `title`, `minutes`, `mode`, `byWhen`, `dueDate`, `dueTime`, `target`, `period`,
                         `doneCount`, `periodKey`, `doneDay`, `doneAt`, `leadMonths`, `garmin`, `times`, `places`, `snoozedUntil`, `createdAt`)
                       SELECT `id`, `title`, `minutes`,
                              CASE `repeat` WHEN 'NONE' THEN 'ONCE' ELSE 'REPEAT' END,
                              CASE `timing` WHEN 'ANYTIME' THEN 'WHENEVER' ELSE `timing` END,
                              NULL, NULL, 1,
                              CASE `repeat` WHEN 'DAILY' THEN 'DAY' WHEN 'MONTHLY' THEN 'MONTH' ELSE 'WEEK' END,
                              0, NULL, NULL,
                              CASE `repeat` WHEN 'NONE' THEN `doneAt` ELSE NULL END,
                              2, 0, '',
                              CASE `where` WHEN 'HOME' THEN 'home' WHEN 'OUT' THEN 'out' ELSE '' END,
                              CASE `repeat` WHEN 'NONE' THEN `snoozedUntil` ELSE NULL END,
                              `createdAt`
                       FROM `tasks`"""
                )
                db.execSQL(
                    """INSERT INTO `items` (`id`, `title`, `minutes`, `mode`, `byWhen`, `dueDate`, `dueTime`, `target`, `period`,
                         `doneCount`, `periodKey`, `doneDay`, `doneAt`, `leadMonths`, `garmin`, `times`, `places`, `snoozedUntil`, `createdAt`)
                       SELECT `id`, `title`, `minutes`, 'REPEAT', 'WHENEVER', NULL, NULL, `targetPerWeek`, 'WEEK',
                              0, NULL, NULL, NULL, 2,
                              CASE WHEN `healthConnectType` IS NULL THEN 0 ELSE 1 END, '',
                              CASE `where` WHEN 'HOME' THEN 'home' WHEN 'OUT' THEN 'out' ELSE '' END,
                              NULL, CAST(strftime('%s', 'now') AS INTEGER) * 1000
                       FROM `goals`"""
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("DROP TABLE `goals`")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `isSet` INTEGER NOT NULL,
                       `wifi` TEXT, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
                )
                db.execSQL(SEED_PLACES)
            }
        }

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
