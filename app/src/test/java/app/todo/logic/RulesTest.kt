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
import java.time.LocalDate
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

    @Test fun goalSlidesToWhatIsStillReachable() {
        val w = Item(title = "workout", mode = Mode.REPEAT, target = 3, period = Per.WEEK)
        // Sunday 9 pm, none done: one day left, so 1 is possible and nothing reads as behind.
        val sun = ctx.copy(now = LocalDateTime.of(2026, 11, 8, 21, 0))
        val p = Rules.pace(w, sun)
        assertTrue(p.sliding)
        assertEquals(1, p.possible)
        assertEquals(1, p.left)
        assertEquals(0.0, p.behind, 0.0)
        assertTrue(Rules.tags(w, sun).none { it.kind == TagKind.BEHIND })
        assertTrue(Rules.tags(w, sun).any { it.text == "0 of 1 possible this week" })
        assertEquals("1 more still fits this week, so it's first.", Rules.why(w, sun))
        // Ticked on Sunday: nothing more possible, so it leaves Now.
        val done = Rules.finished(w, sun)
        assertEquals(0, Rules.pace(done, sun).left)
        assertTrue(Rules.tags(done, sun).any { it.text == "1 of 1 possible this week" })
        // Early in the week the full target is still reachable.
        assertFalse(Rules.pace(w, ctx).sliding)
    }

    @Test fun dailyTargetsNeverSlide() {
        val v = Item(title = "vitamins", mode = Mode.REPEAT, target = 2, period = Per.DAY)
        assertFalse(Rules.pace(v, ctx.copy(now = ctx.now.withHour(23))).sliding)
    }

    @Test fun pausedTimeDoesNotCountTowardPace() {
        val w = Item(title = "w", mode = Mode.REPEAT, target = 7, period = Per.WEEK)
        // Thursday 11 pm: four days gone, about 4 expected.
        val thu = ctx.copy(now = LocalDateTime.of(2026, 11, 5, 23, 0))
        assertEquals(4.0, Rules.pace(w, thu).expected, 0.01)
        // Paused Tuesday through Thursday: only Monday counts.
        val day = 86_400_000L
        val mon = LocalDateTime.of(2026, 11, 2, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val paused = thu.copy(pauses = listOf(Pause(mon + day, mon + 4 * day)))
        assertEquals(1.0, Rules.pace(w, paused).expected, 0.01)
    }

    @Test fun pausesKeepOnlyTimeReallyPaused() {
        val now = 1_000_000_000L
        val started = Rules.pausesWith(emptyList(), now + 5_000, now)
        assertEquals(listOf(Pause(now, now + 5_000)), started)
        // Turned off early: the running pause ends now.
        assertEquals(listOf(Pause(now, now + 1_000)), Rules.pausesWith(started, null, now + 1_000))
        // Changed: the old one ends, a new one starts.
        assertEquals(2, Rules.pausesWith(started, now + 9_000, now + 1_000).size)
    }

    @Test fun windowEndsAreSavedAndShownOncePassed() {
        assertEquals("2026-11-08", Rules.windowEndFor(By.THIS_WEEK, ctx).toString())
        assertEquals("2026-11-30", Rules.windowEndFor(By.THIS_MONTH, ctx).toString())
        assertEquals("2026-12-31", Rules.windowEndFor(By.THIS_QUARTER, ctx).toString())
        assertEquals("2026-03-31", Rules.windowEndFor(By.THIS_QUARTER, ctx, LocalDate.of(2026, 2, 10)).toString())
        val t = Rules.withWindow(Item(title = "t", byWhen = By.THIS_WEEK), ctx)
        assertEquals("2026-11-08", t.windowEnd)
        // Kept on later edits, cleared for a choice with no window.
        assertEquals("2026-11-08", Rules.withWindow(t.copy(title = "t2"), ctx.copy(now = ctx.now.plusDays(9))).windowEnd)
        assertNull(Rules.withWindow(t.copy(byWhen = By.ASAP), ctx).windowEnd)
        assertEquals("this week", Rules.tags(t, ctx).first { it.kind == TagKind.BY }.text)
        val later = ctx.copy(now = ctx.now.plusDays(9))
        assertTrue(Rules.windowPassed(t, later))
        assertEquals("set for week of Nov 2", Rules.tags(t, later).first { it.kind == TagKind.BY }.text)
        assertEquals("It was set for the week of Nov 2.", Rules.why(t, later))
        val m = Rules.withWindow(Item(title = "m", byWhen = By.THIS_MONTH), ctx)
        assertEquals("set for November", Rules.tags(m, ctx.copy(now = ctx.now.plusMonths(1))).first { it.kind == TagKind.BY }.text)
    }

    @Test fun noReminderSetsAsideUntilTheNextDay() {
        val late = ctx.copy(dayStartHour = 3)
        val next = Rules.nextDayStartMs(late)
        assertEquals(LocalDateTime.of(2026, 11, 3, 3, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), next)
        val t = Item(title = "t", skippedUntil = next)
        assertTrue(Rules.candidates(listOf(t), late).isEmpty())
        assertTrue(Rules.candidates(listOf(t), late.copy(now = LocalDateTime.of(2026, 11, 3, 2, 0))).isEmpty())
        assertEquals(1, Rules.candidates(listOf(t), late.copy(now = LocalDateTime.of(2026, 11, 3, 3, 0))).size)
    }

    @Test fun dayStartsLater() {
        val late = ctx.copy(now = LocalDateTime.of(2026, 11, 3, 2, 0), dayStartHour = 3)
        assertEquals("2026-11-02", late.today.toString())
    }
}
