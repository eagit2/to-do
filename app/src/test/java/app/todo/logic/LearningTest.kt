package app.todo.logic

import app.todo.data.BestTime
import app.todo.data.By
import app.todo.data.Completion
import app.todo.data.Item
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LearningTest {
    // Monday 2 Nov 2026, 7 pm, at home.
    private val ctx = Ctx(LocalDateTime.of(2026, 11, 2, 19, 0), place = "home", zone = ZoneOffset.UTC)
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun putOff(id: String, at: Long, slot: BestTime = BestTime.EVENING, weekend: Boolean = false, place: String? = "home") =
        Moment(id, at, slot, weekend, place, Moment.Kind.PUT_OFF)

    private fun done(id: String, at: Long, slot: BestTime = BestTime.EVENING, weekend: Boolean = false, place: String? = "home") =
        Moment(id, at, slot, weekend, place, Moment.Kind.DONE)

    @Test fun putOffTwiceHereRanksBelowWhenever() {
        val laundry = Item(id = "laundry", title = "laundry", byWhen = By.THIS_WEEK)
        val read = Item(id = "read", title = "read", byWhen = By.WHENEVER)
        val learn = Learning(listOf(putOff("laundry", ctx.nowMs - day), putOff("laundry", ctx.nowMs - 2 * hour)))
        assertEquals(listOf("read", "laundry"), Rules.candidates(listOf(laundry, read), ctx, learn).map { it.id })
        // Without the history, "this week" comes first.
        assertEquals("laundry", Rules.candidates(listOf(laundry, read), ctx).first().id)
    }

    @Test fun putOffInTheEveningDoesNotCountInTheMorning() {
        val laundry = Item(id = "laundry", title = "laundry", byWhen = By.THIS_WEEK)
        val read = Item(id = "read", title = "read", byWhen = By.WHENEVER)
        val learn = Learning(listOf(putOff("laundry", ctx.nowMs - day, place = null), putOff("laundry", ctx.nowMs - 2 * hour, place = null)))
        val morning = ctx.copy(now = ctx.now.withHour(9))
        assertEquals("laundry", Rules.candidates(listOf(laundry, read), morning, learn).first().id)
    }

    @Test fun putOffAtAPlaceCountsAtAnyTime() {
        val h = Learning(listOf(putOff("x", ctx.nowMs - day, BestTime.MORNING), putOff("x", ctx.nowMs - hour, BestTime.AFTERNOON))).here("x", ctx)
        assertEquals(0.0, h.putOffSpot, 0.0)
        assertTrue(h.putOff)
    }

    @Test fun oldPutOffsFade() {
        val learn = Learning(listOf(putOff("x", ctx.nowMs - 21 * day), putOff("x", ctx.nowMs - 22 * day)))
        assertFalse(learn.here("x", ctx).putOff)
        assertEquals(0.5, Learning.weight(ctx.nowMs - 14 * day, ctx.nowMs), 1e-9)
    }

    @Test fun deadlinesAndBehindPaceAreNotPushedDown() {
        val due = Item(id = "due", title = "due", byWhen = By.DATE, dueDate = "2026-11-03", dueTime = "09:00")
        val behind = Item(id = "behind", title = "behind", mode = app.todo.data.Mode.REPEAT, target = 10)
        val read = Item(id = "read", title = "read")
        val learn = Learning(listOf("due", "behind").flatMap { listOf(putOff(it, ctx.nowMs - hour), putOff(it, ctx.nowMs - 2 * hour)) })
        assertEquals(listOf("due", "behind", "read"), Rules.candidates(listOf(read, behind, due), ctx, learn).map { it.id })
    }

    @Test fun usuallyDoneHereRisesAndSaysSo() {
        val sat = ctx.copy(now = LocalDateTime.of(2026, 11, 7, 9, 0), place = null)
        val garden = Item(id = "garden", title = "garden")
        val read = Item(id = "read", title = "read")
        val learn = Learning(listOf(done("garden", sat.nowMs - 7 * day, BestTime.MORNING, true, null), done("garden", sat.nowMs - 6 * day, BestTime.MORNING, true, null)))
        assertEquals("on weekend mornings", learn.usual("garden", sat))
        assertEquals("garden", Rules.candidates(listOf(read, garden), sat, learn).first().id)
        assertEquals("You usually do this on weekend mornings.", Rules.why(garden, sat, learn))
        // A weekday evening isn't "usually".
        assertNull(learn.usual("garden", ctx))
        assertEquals("It fits this time and place.", Rules.why(garden, ctx, learn))
    }

    @Test fun learnedLineAddsToOtherReasons() {
        val learn = Learning(listOf(done("a", ctx.nowMs - day), done("a", ctx.nowMs - 2 * day)))
        val asap = Item(id = "a", title = "a", byWhen = By.ASAP)
        assertEquals("You marked it ASAP. You usually do this on weekday evenings.", Rules.why(asap, ctx, learn))
    }

    @Test fun usualPlaceWhenSpotsVary() {
        val learn = Learning(listOf(done("g", ctx.nowMs - day, BestTime.MORNING, place = "gym"), done("g", ctx.nowMs - 2 * day, BestTime.AFTERNOON, place = "gym")))
        assertEquals("at the gym", learn.usual("g", ctx.copy(place = "gym")))
    }

    @Test fun fromRowsSkipsDuplicateDonesAndShown() {
        val at = ctx.nowMs - hour
        val learn = Learning.from(
            listOf(Completion(taskId = "x", at = at, slot = "EVENING", placeLabel = "home")),
            listOf(
                NudgeEvent(taskId = "x", at = at, outcome = NudgeOutcome.DONE, slot = "EVENING", placeLabel = "home"),
                NudgeEvent(taskId = "x", at = at, outcome = NudgeOutcome.SHOWN, slot = "EVENING", placeLabel = "home"),
                NudgeEvent(taskId = "x", at = at, outcome = NudgeOutcome.SHOWN, slot = "EVENING", placeLabel = "home"),
            ),
        )
        val h = learn.here("x", ctx)
        assertEquals(1.0, h.doneAll, 0.01)
        assertEquals(0.0, h.putOffSpot + h.putOffPlace, 0.0)
    }

    @Test fun tiesRotateThroughTheDay() {
        val items = ('a'..'j').map { Item(id = it.toString(), title = it.toString(), createdAt = 0) }
        val firsts = listOf(9, 14, 20).flatMap { h -> (2..4).map { d -> ctx.copy(now = LocalDateTime.of(2026, 11, d, h, 0)) } }
            .map { Rules.candidates(items, it).first().id }
        assertTrue(firsts.toSet().size > 1)
        // Stable within a slot.
        assertEquals(Rules.candidates(items, ctx).map { it.id }, Rules.candidates(items, ctx.copy(now = ctx.now.plusMinutes(40))).map { it.id })
    }
}
