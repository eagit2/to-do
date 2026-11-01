package app.todo.parse

import app.todo.data.Bucket
import app.todo.data.Effort
import app.todo.data.Where
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskLineParserTest {
    @Test fun plainExample() {
        val t = TaskLineParser.parse("mow lawn 45m home")
        assertEquals("mow lawn", t.title)
        assertEquals(45, t.minutes)
        assertEquals(Where.HOME, t.where)
        assertEquals(Bucket.NICE, t.bucket)
    }

    @Test fun allCues() {
        val t = TaskLineParser.parse("!must call bank 1h30m @out ~low")
        assertEquals("call bank", t.title)
        assertEquals(90, t.minutes)
        assertEquals(Where.OUT, t.where)
        assertEquals(Bucket.MUST, t.bucket)
        assertEquals(Effort.LOW, t.effort)
    }

    @Test fun decimalHours() {
        assertEquals(90, TaskLineParser.parseMinutes("1.5h"))
        assertEquals(60, TaskLineParser.parseMinutes("1h"))
        assertNull(TaskLineParser.parseMinutes("m"))
        assertNull(TaskLineParser.parseMinutes("hello"))
    }

    @Test fun bareHomeNotEatenWhenItIsTheWholeTitle() {
        val t = TaskLineParser.parse("home")
        assertEquals("home", t.title)
        assertEquals(Where.ANYWHERE, t.where)
    }

    @Test fun explicitPlaceWins() {
        val t = TaskLineParser.parse("clean out garage @home")
        assertEquals("clean out garage", t.title)
        assertEquals(Where.HOME, t.where)
    }

    @Test fun nothingRecognised() {
        val t = TaskLineParser.parse("  buy milk ")
        assertEquals("buy milk", t.title)
        assertNull(t.minutes)
    }

    @Test fun goalWithCount() {
        val g = TaskLineParser.parseGoal("workout 3x 45m")
        assertEquals("workout", g.title)
        assertEquals(3, g.targetPerWeek)
        assertEquals(45, g.minutes)
        assertEquals("EXERCISE", g.healthConnectType)
    }

    @Test fun goalDefaultsToOnceAWeek() {
        val g = TaskLineParser.parseGoal("call mom @home")
        assertEquals("call mom", g.title)
        assertEquals(1, g.targetPerWeek)
        assertEquals(Where.HOME, g.where)
        assertNull(g.healthConnectType)
    }

    @Test fun goalPerWeekSpelling() {
        assertEquals(2, TaskLineParser.parseGoal("read 2/wk").targetPerWeek)
    }
}
