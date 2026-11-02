package app.todo.parse

import app.todo.data.BestTime
import app.todo.data.By
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.placeList
import app.todo.data.timeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskLineParserTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun plainExample() {
        val t = TaskLineParser.parse("mow lawn 45m home", today)
        assertEquals("mow lawn", t.title)
        assertEquals(45, t.minutes)
        assertEquals(listOf("home"), t.placeList)
        assertEquals(Mode.ONCE, t.mode)
        assertEquals(By.WHENEVER, t.byWhen)
    }

    @Test fun byWhenAndPlace() {
        val t = TaskLineParser.parse("!asap call bank 1h30m @out", today)
        assertEquals("call bank", t.title)
        assertEquals(90, t.minutes)
        assertEquals(listOf("out"), t.placeList)
        assertEquals(By.ASAP, t.byWhen)
    }

    @Test fun decimalHours() = assertEquals(90, TaskLineParser.parse("paint 1.5h", today).minutes)

    @Test fun timesPerWeek() {
        val t = TaskLineParser.parse("workout 3x 45m !morning @home @gym", today)
        assertEquals("workout", t.title)
        assertEquals(Mode.REPEAT, t.mode)
        assertEquals(3, t.target)
        assertEquals(Per.WEEK, t.period)
        assertEquals(setOf(BestTime.MORNING), t.timeSet)
        assertEquals(listOf("home", "gym"), t.placeList)
        assertTrue(t.garmin)
    }

    @Test fun perMonthAndDay() {
        val m = TaskLineParser.parse("meditate 10/month !evening", today)
        assertEquals(10, m.target); assertEquals(Per.MONTH, m.period); assertFalse(m.garmin)
        val d = TaskLineParser.parse("take vitamins 2/day", today)
        assertEquals(2, d.target); assertEquals(Per.DAY, d.period)
    }

    @Test fun yearly() {
        val t = TaskLineParser.parse("renew car insurance !yearly", today)
        assertEquals(Mode.REPEAT, t.mode); assertEquals(Per.YEAR, t.period); assertEquals(1, t.target)
    }

    @Test fun todayAndTomorrow() {
        assertEquals("2026-11-02", TaskLineParser.parse("pay bill !today", today).dueDate)
        val t = TaskLineParser.parse("pay bill !tomorrow", today)
        assertEquals(By.DATE, t.byWhen); assertEquals("2026-11-03", t.dueDate)
    }

    @Test fun severalTimes() {
        val t = TaskLineParser.parse("garage !weekends !afternoon !quarter", today)
        assertEquals(setOf(BestTime.WEEKENDS, BestTime.AFTERNOON), t.timeSet)
        assertEquals(By.THIS_QUARTER, t.byWhen)
    }

    @Test fun someday() = assertEquals(By.SOMEDAY, TaskLineParser.parse("learn knives !someday", today).byWhen)

    @Test fun unknownWordsStayInTitle() {
        val t = TaskLineParser.parse("call mom about the trip", today)
        assertEquals("call mom about the trip", t.title)
        assertNull(t.minutes)
    }

    @Test fun onlyCuesKeepsLineAsTitle() = assertEquals("45m", TaskLineParser.parse("45m", today).title)

    @Test fun minutes() {
        assertEquals(10, TaskLineParser.parseMinutes("10m"))
        assertEquals(10, TaskLineParser.parseMinutes("10min"))
        assertEquals(60, TaskLineParser.parseMinutes("1h"))
        assertNull(TaskLineParser.parseMinutes("hello"))
        assertNull(TaskLineParser.parseMinutes("0m"))
    }
}
