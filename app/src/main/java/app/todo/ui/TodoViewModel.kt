package app.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.todo.data.Completion
import app.todo.data.FreeWindow
import app.todo.data.Goal
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import app.todo.data.Repeat
import app.todo.data.Task
import app.todo.data.Timing
import app.todo.data.TodoDatabase
import app.todo.parse.TaskLineParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class TodoViewModel(private val db: TodoDatabase) : ViewModel() {
    val openTasks: StateFlow<List<Task>> =
        db.tasks().openTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Weeks start on Monday. Recomputed when the view model is created, which is each app launch. */
    private val weekStart: Long = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val goals = db.goals().progress(weekStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val freeWindows = db.freeWindows().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Placeholder ranking: most urgent "when" first, oldest first. Real ranking adds time, place and snooze history. */
    fun suggestion(tasks: List<Task>, skipped: Set<String>, now: Long = System.currentTimeMillis()): Task? =
        tasks.filter { it.timing != Timing.SOMEDAY && it.id !in skipped && !it.isSnoozed(now) }
            .minByOrNull { it.timing.ordinal }

    fun add(line: String) {
        if (line.isBlank()) return
        viewModelScope.launch { db.tasks().insert(TaskLineParser.parse(line)) }
    }

    /** Finished from the Tasks tab, not from a suggestion, so no nudge event is logged. */
    fun complete(task: Task) = viewModelScope.launch { db.tasks().update(finished(task)) }

    /** A repeating task hides until its next period instead of being marked done. */
    private fun finished(task: Task): Task {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val next = when (task.repeat) {
            Repeat.NONE -> return task.copy(doneAt = now)
            Repeat.DAILY -> today.plusDays(1)
            Repeat.WEEKLY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            Repeat.MONTHLY -> today.with(TemporalAdjusters.firstDayOfNextMonth())
        }
        return task.copy(snoozedUntil = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    fun save(task: Task) = viewModelScope.launch { db.tasks().update(task) }

    fun delete(task: Task) = viewModelScope.launch { db.tasks().delete(task) }

    fun addGoal(line: String) {
        if (line.isBlank()) return
        viewModelScope.launch { db.goals().insert(TaskLineParser.parseGoal(line)) }
    }

    fun goalDone(goal: Goal) = viewModelScope.launch {
        db.goals().insertCompletion(Completion(goalId = goal.id))
    }

    fun deleteGoal(goal: Goal) = viewModelScope.launch {
        db.goals().deleteCompletions(goal.id)
        db.goals().delete(goal)
    }

    fun done(task: Task) = viewModelScope.launch {
        db.tasks().update(finished(task))
        db.nudges().insert(NudgeEvent(taskId = task.id, outcome = NudgeOutcome.DONE))
    }

    /** "Not now". [remindInMillis] null means no new reminder: the task just steps aside for now. */
    fun notNow(task: Task, remindInMillis: Long?) = viewModelScope.launch {
        if (remindInMillis != null) {
            db.tasks().update(task.copy(snoozedUntil = System.currentTimeMillis() + remindInMillis))
        }
        db.nudges().insert(
            NudgeEvent(
                taskId = task.id,
                outcome = if (remindInMillis == null) NudgeOutcome.DISMISSED else NudgeOutcome.SNOOZED,
            )
        )
    }

    fun addFreeWindow(window: FreeWindow) = viewModelScope.launch { db.freeWindows().insert(window) }

    fun deleteFreeWindow(window: FreeWindow) = viewModelScope.launch { db.freeWindows().delete(window) }
}

fun Task.isSnoozed(now: Long = System.currentTimeMillis()) = snoozedUntil != null && snoozedUntil > now
