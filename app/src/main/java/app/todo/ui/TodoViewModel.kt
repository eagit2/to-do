package app.todo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.todo.data.Bucket
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import app.todo.data.Task
import app.todo.data.TodoDatabase
import app.todo.parse.TaskLineParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(private val db: TodoDatabase) : ViewModel() {
    val openTasks: StateFlow<List<Task>> =
        db.tasks().openTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals = db.goals().goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Placeholder ranking: first must, then first nice. Real ranking uses time, place and snooze history. */
    fun suggestion(tasks: List<Task>, skipped: Set<String>): Task? =
        tasks.filter { it.bucket != Bucket.SOMEDAY && it.id !in skipped }
            .minByOrNull { it.bucket.ordinal }

    fun add(line: String) {
        if (line.isBlank()) return
        viewModelScope.launch { db.tasks().insert(TaskLineParser.parse(line)) }
    }

    fun done(task: Task) = viewModelScope.launch {
        db.tasks().update(task.copy(doneAt = System.currentTimeMillis()))
        db.nudges().insert(NudgeEvent(taskId = task.id, outcome = NudgeOutcome.DONE))
    }

    fun notNow(task: Task) = viewModelScope.launch {
        db.nudges().insert(NudgeEvent(taskId = task.id, outcome = NudgeOutcome.SNOOZED))
    }
}
