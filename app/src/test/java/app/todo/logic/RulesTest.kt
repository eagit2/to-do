package app.todo.logic

import app.todo.data.BestTime
import app.todo.data.By
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.withPlaces
import app.todo.data.withTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RulesTest {
    // Monday 2 Nov 2026, 7 pm, at home.
    private val ctx = Ctx(LocalDateTime.of(2026, 11, 2, 19, 0), place = "home", zone = ZoneOffset.UTC)

    @Test fun repeatCountsUpAndCrossesOffForTheDay() {
        val w = Item(title = "workout", mode = Mode.REPEAT, target = 3, period = Per.WEEK)
        val done = Rules.finished(w, ctx)
        assertEquals(1, Rules.doneNow(done, ctx))
        assertTrue(Rules.doneToday(done, ctx))
        assertTrue(Rules.isChecked(done, ctx))
        assertFalse(Rules.candidates(listOf(done), ctx).contains(done))
        // Next day it is back, with the count kept for the week.
        val tue = ctx.copy(now = ctx.now.plusDays(1))
        assertFalse(Rules.doneToday(done, tue))
        assertEquals(1, Rules.doneNow(done, tue))
        // A new week starts back at 0.
        assertEquals(0, Rules.doneNow(done, ctx.copy(now = ctx.now.plusDays(7))))
    }

    @Test fun severalTimesADayStaysUntilAllDone() {
        val v = Item(title = "vitamins", mode = Mode.REPEAT, target = 2, period = Per.DAY)
        val once = Rules.finished(v, ctx)
        assertFalse(Rules.doneToday(once, ctx))
        assertTrue(Rules.doneToday(Rules.finished(once, ctx), ctx))
    }

    @Test fun revertUndoesACount() {
        val w = Rules.finished(Item(title = "w", mode = Mode.REPEAT, target = 3), ctx)
        val back = Rules.reverted(w, ctx)
        assertEquals(0, Rules.doneNow(back, ctx))
        assertFalse(Rules.isChecked(back, ctx))
    }

    @Test fun oneOffCrossedOffOnlyForTheDay() {
        val t = Rules.finished(Item(title = "bill"), ctx)
        assertTrue(Rules.isChecked(t, ctx))
        assertTrue(Rules.onList(t, ctx))
        assertFalse(Rules.onList(t, ctx.copy(now = ctx.now.plusDays(1))))
        assertNull(Rules.reverted(t, ctx).doneAt)
    }

    @Test fun yearlyHiddenUntilLeadThenRolls() {
        val far = Item(title = "tax", mode = Mode.REPEAT, period = Per.YEAR, dueDate = "2027-04-01")
        assertTrue(Rules.yearlyHidden(far, ctx))
        assertTrue(Rules.isLater(far, ctx))
        val near = far.copy(dueDate = "2026-12-12")
        assertFalse(Rules.yearlyHidden(near, ctx))
        val done = Rules.finished(near, ctx)
        assertEquals("2027-12-12", done.dueDate)
        assertTrue(Rules.doneToday(done, ctx))
        assertEquals("2026-12-12", Rules.reverted(done, ctx).dueDate)
    }

    @Test fun timesAndPlacesFilter() {
        val morning = Item(title = "m").withTimes(setOf(BestTime.MORNING))
        val evening = Item(title = "e").withTimes(setOf(BestTime.EVENING, BestTime.WEEKDAYS))
        val gym = Item(title = "g").withPlaces(listOf("gym"))
        val c = Rules.candidates(listOf(morning, evening, gym), ctx)
        assertEquals(listOf("e"), c.map { it.title })
        // Place unknown: place-tagged items still fit.
        assertEquals(2, Rules.candidates(listOf(evening, gym), ctx.copy(place = null)).size)
    }

    @Test fun behindPaceRanksFirst() {
        // Monday evening: 1/7 of the week gone, so 10 a week expects about 1.5 done.
        val behind = Item(title = "b", mode = Mode.REPEAT, target = 10, period = Per.WEEK)
        val whenever = Item(title = "w", byWhen = By.WHENEVER, createdAt = 0)
        assertEquals("b", Rules.candidates(listOf(whenever, behind), ctx).first().title)
    }

    @Test fun deadlineTomorrowBeatsAsapWhenUnderADay() {
        val due = Item(title = "d", byWhen = By.DATE, dueDate = "2026-11-03", dueTime = "09:00")
        val asap = Item(title = "a", byWhen = By.ASAP, createdAt = 0)
        assertEquals("d", Rules.candidates(listOf(asap, due), ctx).first().title)
    }

    @Test fun somedayNeverSuggested() {
        assertTrue(Rules.candidates(listOf(Item(title = "s", byWhen = By.SOMEDAY)), ctx).isEmpty())
    }

    @Test fun freeWindowAcrossMidnight() {
        val w = FreeWindow(days = Rules.EVERYDAY, startMinute = 22 * 60, endMinute = 60)
        assertNotNull(Rules.windowNow(listOf(w), ctx.copy(now = ctx.now.withHour(23))))
        assertNull(Rules.windowNow(listOf(w), ctx))
    }

    @Test fun dayStartsLater() {
        val late = ctx.copy(now = LocalDateTime.of(2026, 11, 3, 2, 0), dayStartHour = 3)
        assertEquals("2026-11-02", late.today.toString())
    }
}
