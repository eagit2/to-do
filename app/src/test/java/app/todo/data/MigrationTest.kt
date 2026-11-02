package app.todo.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Runs each migration on a database built from the committed schema JSON, then checks it matches the next schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TodoDatabase::class.java)

    @Test fun v3ToV4KeepsRowsAndFillsSlots() {
        // Saturday 7 Nov 2026, 9 am phone time.
        val at = java.time.LocalDateTime.of(2026, 11, 7, 9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        helper.createDatabase(name, 3).apply {
            execSQL(
                """INSERT INTO items (id, title, minutes, mode, byWhen, dueDate, dueTime, target, period, doneCount, periodKey,
                   doneDay, doneAt, leadMonths, garmin, times, places, snoozedUntil, createdAt)
                   VALUES ('i1', 'laundry', 30, 'ONCE', 'THIS_WEEK', NULL, NULL, 1, 'WEEK', 0, NULL, NULL, NULL, 2, 0, '', 'home', NULL, $at)"""
            )
            execSQL("INSERT INTO nudges (id, taskId, at, placeLabel, outcome) VALUES ('n1', 'i1', $at, 'home', 'SNOOZED')")
            execSQL("INSERT INTO completions (id, goalId, taskId, at, source) VALUES ('c1', NULL, 'i1', $at, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 4, true, TodoDatabase.MIGRATION_3_4)
        db.query("SELECT title, windowEnd, skippedUntil FROM items").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("laundry", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
        for (table in listOf("nudges", "completions")) {
            db.query("SELECT slot, weekend FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(table, "MORNING", c.getString(0))
                assertEquals(table, 1, c.getInt(1))
            }
        }
        db.query("SELECT placeLabel FROM completions").use { c -> assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)) }
    }
}
