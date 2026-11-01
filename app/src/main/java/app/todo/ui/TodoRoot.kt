package app.todo.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.todo.data.Bucket
import app.todo.data.Task
import app.todo.data.TodoDatabase

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
        items(active, key = { it.id }) { TaskRow(it) }
        item {
            TextButton(onClick = { showSomeday = !showSomeday }) {
                Text(if (showSomeday) "Hide someday" else "Someday (${someday.size})")
            }
        }
        if (showSomeday) items(someday, key = { it.id }) { TaskRow(it) }
    }
}

@Composable
private fun TaskRow(task: Task) {
    val meta = listOfNotNull(task.bucket.name.lowercase(), task.minutes?.let { "$it min" }).joinToString(" · ")
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(task.title)
        Text(meta, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GoalsScreen(vm: TodoViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    if (goals.isEmpty()) Text("No weekly goals yet.")
    goals.forEach { Text("${it.title}: target ${it.targetPerWeek} a week", Modifier.padding(vertical = 6.dp)) }
}
