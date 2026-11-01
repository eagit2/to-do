package app.todo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.todo.data.FreeWindow

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private const val WEEKDAYS = 0b0011111
private const val WEEKENDS = 0b1100000

/** Settings. For now: the times you're usually free, which is when nudges are allowed. */
@Composable
fun SettingsScreen(vm: TodoViewModel) {
    val windows by vm.freeWindows.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FreeWindow?>(null) }

    Text("When are you usually free?", style = MaterialTheme.typography.titleMedium)
    Text(
        "Nudges only come during these times, and only when the phone also looks idle.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    if (windows.isEmpty()) Text("No free times yet.")
    windows.forEach { w ->
        Text(
            "${daysLabel(w.days)}  ${clock(w.startMinute)} – ${clock(w.endMinute)}",
            Modifier.fillMaxWidth().clickable { deleting = w }.padding(vertical = 10.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { adding = true }) { Text("Add free time") }

    if (adding) AddFreeWindowDialog(onAdd = { vm.addFreeWindow(it); adding = false }, onDismiss = { adding = false })
    deleting?.let { w ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this free time?") },
            text = { Text("${daysLabel(w.days)}  ${clock(w.startMinute)} – ${clock(w.endMinute)}") },
            confirmButton = { TextButton(onClick = { vm.deleteFreeWindow(w); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddFreeWindowDialog(onAdd: (FreeWindow) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(WEEKDAYS) }
    val start = rememberTimePickerState(initialHour = 18, initialMinute = 0)
    val end = rememberTimePickerState(initialHour = 21, initialMinute = 0)
    val startMinute = start.hour * 60 + start.minute
    val endMinute = end.hour * 60 + end.minute

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add free time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(days == WEEKDAYS, { days = WEEKDAYS }, label = { Text("Weekdays") })
                    FilterChip(days == WEEKENDS, { days = WEEKENDS }, label = { Text("Weekends") })
                    FilterChip(days == 0b1111111, { days = 0b1111111 }, label = { Text("Every day") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLetters.forEachIndexed { i, letter ->
                        val bit = 1 shl i
                        FilterChip(days and bit != 0, { days = days xor bit }, label = { Text(letter) })
                    }
                }
                Text("From", style = MaterialTheme.typography.labelLarge)
                TimeInput(start)
                Text("Until", style = MaterialTheme.typography.labelLarge)
                TimeInput(end)
            }
        },
        confirmButton = {
            TextButton(
                enabled = days != 0 && endMinute != startMinute,
                onClick = { onAdd(FreeWindow(days = days, startMinute = startMinute, endMinute = endMinute)) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun daysLabel(days: Int): String = when (days) {
    0b1111111 -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
}

/** 18:30 -> "6:30 pm". An end earlier than the start means the window runs past midnight. */
private fun clock(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    val suffix = if (h < 12) "am" else "pm"
    return if (m == 0) "$h12 $suffix" else "$h12:${m.toString().padStart(2, '0')} $suffix"
}
