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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.todo.data.Bucket
import app.todo.data.Goal
import app.todo.data.Task
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
                    showSettings -> Text("Home Wi-Fi, quiet hours, nudge threshold and permissions go here.")
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
    // Skipped tasks reset when the app restarts; ranking will use snooze history instead.
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val pick = vm.suggestion(tasks, skipped)

    if (pick == null) {
        Text("Nothing to do right now.", style = MaterialTheme.typography.titleMedium)
    } else {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(pick.title, style = MaterialTheme.typography.headlineSmall)
                pick.minutes?.let { Text("$it min", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.done(pick) }) { Text("Done") }
                    OutlinedButton(onClick = { vm.notNow(pick); skipped = skipped + pick.id }) { Text("Not now") }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Today", style = MaterialTheme.typography.titleSmall)
    tasks.filter { it.bucket == Bucket.MUST && it.id != pick?.id }.take(5).forEach {
        Text("• ${it.title}", Modifier.padding(vertical = 4.dp))
    }
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
    val (someday, active) = tasks.partition { it.bucket == Bucket.SOMEDAY }
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
    val meta = listOfNotNull(
        task.bucket.name.lowercase(),
        task.minutes?.let { "$it min" },
        task.where.takeIf { it != Where.ANYWHERE }?.label(),
    ).joinToString(" · ")
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
    var minutes by remember { mutableStateOf(task.minutes?.toString() ?: "") }
    var bucket by remember { mutableStateOf(task.bucket) }
    var where by remember { mutableStateOf(task.where) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true)
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bucket.entries.forEach { b ->
                        FilterChip(bucket == b, { bucket = b }, label = { Text(b.name.lowercase()) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Where.entries.forEach { w ->
                        FilterChip(where == w, { where = w }, label = { Text(w.label()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(task.copy(title = title.trim(), minutes = minutes.toIntOrNull(), bucket = bucket, where = where))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete") } },
    )
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
