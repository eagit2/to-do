package app.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.todo.data.Bucket
import app.todo.data.Completion
import app.todo.data.Goal
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import app.todo.data.Task
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

    /** Placeholder ranking: first must, then first nice. Real ranking uses time, place and snooze history. */
    fun suggestion(tasks: List<Task>, skipped: Set<String>): Task? =
        tasks.filter { it.bucket != Bucket.SOMEDAY && it.id !in skipped }
            .minByOrNull { it.bucket.ordinal }

    fun add(line: String) {
        if (line.isBlank()) return
        viewModelScope.launch { db.tasks().insert(TaskLineParser.parse(line)) }
    }

    /** Finished from the Tasks tab, not from a suggestion, so no nudge event is logged. */
    fun complete(task: Task) = viewModelScope.launch {
        db.tasks().update(task.copy(doneAt = System.currentTimeMillis()))
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
        db.tasks().update(task.copy(doneAt = System.currentTimeMillis()))
        db.nudges().insert(NudgeEvent(taskId = task.id, outcome = NudgeOutcome.DONE))
    }

    fun notNow(task: Task) = viewModelScope.launch {
        db.nudges().insert(NudgeEvent(taskId = task.id, outcome = NudgeOutcome.SNOOZED))
    }
}
