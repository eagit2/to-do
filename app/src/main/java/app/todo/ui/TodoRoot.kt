package app.todo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.todo.data.Goal
import app.todo.data.Repeat
import app.todo.data.Size
import app.todo.data.Task
import app.todo.data.Timing
import app.todo.data.TodoDatabase
import app.todo.data.Where

private enum class Tab(val label: String, val icon: ImageVector) {
    NOW("Now", Icons.Filled.Home),
    TASKS("Tasks", Icons.Filled.List),
    GOALS("Goals", Icons.Filled.Star),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoRoot(db: TodoDatabase) {
    val vm: TodoViewModel = viewModel(factory = viewModelFactory { initializer { TodoViewModel(db) } })
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (showSettings) "Settings" else tab.label) },
                    actions = {
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = !showSettings && tab == t,
                            onClick = { tab = t; showSettings = false },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val tasks by vm.openTasks.collectAsStateWithLifecycle()
            Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                when {
                    showSettings -> SettingsScreen(vm)
                    tab == Tab.NOW -> NowScreen(vm, tasks)
                    tab == Tab.TASKS -> TasksScreen(vm, tasks)
                    tab == Tab.GOALS -> GoalsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun NowScreen(vm: TodoViewModel, tasks: List<Task>) {
    // "No reminder" skips a task until the app restarts; timed reminders are stored on the task.
    var skipped by remember { mutableStateOf(setOf<String>()) }
    var askingWhen by remember { mutableStateOf(false) }
    val pick = vm.suggestion(tasks, skipped)

    if (pick == null) {
        Text("Nothing to do right now.", style = MaterialTheme.typography.titleMedium)
    } else {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(pick.title, style = MaterialTheme.typography.headlineSmall)
                Text(pick.meta(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.done(pick) }) { Text("Done") }
                    OutlinedButton(onClick = { askingWhen = true }) { Text("Not now") }
                }
            }
        }
        if (askingWhen) {
            RemindAgainDialog(
                onPick = { millis ->
                    vm.notNow(pick, millis)
                    if (millis == null) skipped = skipped + pick.id
                    askingWhen = false
                },
                onDismiss = { askingWhen = false },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Up next", style = MaterialTheme.typography.titleSmall)
    tasks.filter { it.timing <= Timing.THIS_WEEK && it.id != pick?.id && !it.isSnoozed() }
        .sortedBy { it.timing.ordinal }.take(5).forEach {
            Text("• ${it.title}", Modifier.padding(vertical = 4.dp))
        }
}

private const val MINUTE = 60_000L

@Composable
private fun RemindAgainDialog(onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "In 15 minutes" to 15 * MINUTE,
        "In 1 hour" to 60 * MINUTE,
        "Tomorrow" to 24 * 60 * MINUTE,
        "Next week" to 7 * 24 * 60 * MINUTE,
        "No reminder" to null,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me again") },
        text = {
            Column {
                options.forEach { (label, millis) ->
                    TextButton(onClick = { onPick(millis) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TasksScreen(vm: TodoViewModel, tasks: List<Task>) {
    var line by rememberSaveable { mutableStateOf("") }
    var showSomeday by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    OutlinedTextField(
        value = line,
        onValueChange = { line = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("mow lawn 45m home") },
        trailingIcon = { TextButton(onClick = { vm.add(line); line = "" }) { Text("Add") } },
    )
    Spacer(Modifier.height(12.dp))
    val (someday, active) = tasks.sortedBy { it.timing.ordinal }.partition { it.timing == Timing.SOMEDAY }
    LazyColumn {
        items(active, key = { it.id }) { TaskRow(it, onDone = { vm.complete(it) }, onEdit = { editing = it }) }
        item {
            TextButton(onClick = { showSomeday = !showSomeday }) {
                Text(if (showSomeday) "Hide someday" else "Someday (${someday.size})")
            }
        }
        if (showSomeday) items(someday, key = { it.id }) { TaskRow(it, onDone = { vm.complete(it) }, onEdit = { editing = it }) }
    }

    editing?.let { task ->
        EditTaskDialog(
            task = task,
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(task); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TaskRow(task: Task, onDone: () -> Unit, onEdit: () -> Unit) {
    val meta = task.meta()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { onDone() })
        Column {
            Text(task.title)
            Text(meta, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Task.meta(): String = listOfNotNull(
    if (repeat != Repeat.NONE) repeat.label() else timing.label(),
    Size.of(minutes)?.label,
    where.takeIf { it != Where.ANYWHERE }?.label(),
    "snoozed".takeIf { isSnoozed() },
).joinToString(" · ")

private fun Timing.label() = when (this) {
    Timing.ASAP -> "ASAP"
    Timing.THIS_WEEK -> "this week"
    Timing.THIS_MONTH -> "this month"
    Timing.THIS_QUARTER -> "this quarter"
    Timing.ANYTIME -> "anytime idle"
    Timing.SOMEDAY -> "someday"
}

private fun Repeat.label() = when (this) {
    Repeat.NONE -> "once"
    Repeat.DAILY -> "daily"
    Repeat.WEEKLY -> "weekly"
    Repeat.MONTHLY -> "monthly"
}

private fun Where.label() = when (this) {
    Where.HOME -> "home"
    Where.OUT -> "out"
    Where.BEFORE_LEAVING -> "before leaving"
    Where.ANYWHERE -> "anywhere"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTaskDialog(task: Task, onSave: (Task) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var size by remember { mutableStateOf(Size.of(task.minutes)) }
    var timing by remember { mutableStateOf(task.timing) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var where by remember { mutableStateOf(task.where) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true)
                ChipGroup("How long", Size.entries, size, { it.label }) { size = if (size == it) null else it }
                ChipGroup("When", Timing.entries, timing, { it.label() }) { timing = it }
                ChipGroup("Repeat", Repeat.entries, repeat, { it.label() }) { repeat = it }
                ChipGroup("Where", Where.entries, where, { it.label() }) { where = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    // Keep a typed exact time if it still falls in the chosen size.
                    val minutes = if (Size.of(task.minutes) == size) task.minutes else size?.minutes
                    onSave(task.copy(title = title.trim(), minutes = minutes, timing = timing, repeat = repeat, where = where))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(title: String, options: List<T>, selected: T?, label: (T) -> String, onClick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected == option, { onClick(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun GoalsScreen(vm: TodoViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    var line by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Goal?>(null) }

    OutlinedTextField(
        value = line,
        onValueChange = { line = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("workout 3x 45m") },
        trailingIcon = { TextButton(onClick = { vm.addGoal(line); line = "" }) { Text("Add") } },
    )
    Spacer(Modifier.height(12.dp))
    if (goals.isEmpty()) Text("No weekly goals yet. Type one above, like \"workout 3x\".")
    LazyColumn {
        items(goals, key = { it.goal.id }) { (goal, done) ->
            Column(Modifier.fillMaxWidth().clickable { deleting = goal }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(goal.title)
                        Text("$done of ${goal.targetPerWeek} this week", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.goalDone(goal) }) { Text("+1") }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / goal.targetPerWeek).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(goal.title) },
            text = { Text("Delete this goal and its history?") },
            confirmButton = { TextButton(onClick = { vm.deleteGoal(goal); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
