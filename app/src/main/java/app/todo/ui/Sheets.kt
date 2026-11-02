package app.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.todo.data.By
import app.todo.data.BestTime
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.Size
import app.todo.data.placeList
import app.todo.data.timeSet
import app.todo.data.withPlaces
import app.todo.data.withTimes
import app.todo.logic.Rules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val sheetTitle = 24.sp

@Composable
private fun FieldLabel(text: String, note: String? = null) {
    Row(Modifier.padding(top = 8.dp)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) Text(" · $note", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The one sheet for adding and editing an item. Swiping it down or tapping outside keeps what you changed,
 * the same as Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    draft: Item,
    isNew: Boolean,
    placeKeys: List<String>,
    onChange: (Item) -> Unit,
    onKeep: () -> Unit,
    onCancelOrDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var calendarOpen by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onKeep, sheetState = state, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isNew) "New item" else "Edit item", fontSize = sheetTitle, fontWeight = FontWeight.SemiBold)
            TextField(
                value = draft.title, onValueChange = { onChange(draft.copy(title = it)) },
                placeholder = { Text("What needs doing?") }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            FieldLabel("How long")
            ChipGroup(Size.entries.map { it to it.label }, { Size.of(draft.minutes) == it }) {
                onChange(draft.copy(minutes = if (Size.of(draft.minutes) == it) null else it.minutes))
            }
            FieldLabel("How often")
            Segs(listOf(Mode.ONCE to "Once", Mode.REPEAT to "Repeats"), draft.mode) { onChange(draft.copy(mode = it)) }
            if (draft.mode == Mode.ONCE) {
                FieldLabel("By when")
                ChipGroup(By.entries.map { it to it.label }, { draft.byWhen == it }) { b ->
                    when {
                        b == By.DATE -> {
                            onChange(draft.copy(byWhen = By.DATE, dueDate = draft.dueDate ?: LocalDate.now().plusDays(1).toString()))
                            calendarOpen = true
                        }
                        draft.byWhen == By.DATE -> onChange(draft.copy(byWhen = b, dueDate = null, dueTime = null))
                        else -> onChange(draft.copy(byWhen = b))
                    }
                }
            } else {
                if (draft.period != Per.YEAR) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper(
                            draft.target,
                            onLess = { onChange(draft.copy(target = (draft.target - 1).coerceAtLeast(1))) },
                            onMore = { onChange(draft.copy(target = (draft.target + 1).coerceAtMost(31))) },
                            lessLabel = "Fewer times", moreLabel = "More times",
                        )
                        Text("times per", fontSize = 15.sp, color = cs.onSurfaceVariant)
                    }
                }
                ChipGroup(Per.entries.map { it to it.label }, { draft.period == it }) { p ->
                    onChange(
                        if (p == Per.YEAR) draft.copy(period = p, target = 1, dueDate = draft.dueDate ?: LocalDate.now().plusMonths(3).toString())
                        else draft.copy(period = p)
                    )
                }
                ChipGroup(listOf(true to "Counts Garmin workouts"), { draft.garmin }) { onChange(draft.copy(garmin = !draft.garmin)) }
                if (draft.period == Per.YEAR) {
                    FieldLabel("Start reminding me")
                    ChipGroup(listOf(1 to "1 month before", 2 to "2 months before", 3 to "3 months before"), { draft.leadMonths == it }) {
                        onChange(draft.copy(leadMonths = it))
                    }
                    Rules.showFrom(draft)?.let { Hint("Hidden until ${Rules.shortDate(it)}, then it moves up as the date gets close.") }
                }
            }
            val showDate = if (draft.mode == Mode.ONCE) draft.byWhen != By.SOMEDAY else draft.period == Per.YEAR
            if (showDate) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainerHigh).clickable { calendarOpen = true }.padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(AppIcons.calendar, null, Modifier.size(20.dp), tint = cs.primary)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(if (draft.mode == Mode.REPEAT) "Due each year on" else "Deadline", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Text(if (draft.dueDate != null) Rules.dueLabel(draft).removePrefix("due ") else "Pick a date", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(AppIcons.chevR, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                }
            }
            FieldLabel("Best times", "none means any free time")
            val times = draft.timeSet
            ChipGroup(BestTime.entries.map { it to it.label }, { it in times }) {
                onChange(draft.withTimes(if (it in times) times - it else times + it))
            }
            FieldLabel("Places", "none means anywhere")
            val places = draft.placeList
            ChipGroup(placeKeys.map { it to it }, { it in places }) {
                onChange(draft.withPlaces(if (it in places) places - it else places + it))
            }
            Spacer(Modifier.size(8.dp))
        }
        HorizontalDivider(color = cs.surfaceContainerHigh)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancelOrDelete) {
                Text(if (isNew) "Cancel" else "Delete", color = if (isNew) cs.primary else cs.error)
            }
            Button(onClick = onKeep, enabled = draft.title.isNotBlank()) { Text(if (isNew) "Add" else "Save") }
        }
    }
    if (calendarOpen) DueDateDialog(draft, onChange) { calendarOpen = false }
}

/** Month calendar plus an optional time. "Add" or tapping outside keeps the date and goes back to the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateDialog(item: Item, onChange: (Item) -> Unit, onClose: () -> Unit) {
    val initial = remember { Rules.dueDate(item)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
    fun withPicked(base: Item): Item {
        val ms = picker.selectedDateMillis ?: return base
        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
        return if (base.mode == Mode.ONCE) base.copy(dueDate = d, byWhen = By.DATE) else base.copy(dueDate = d)
    }
    val keep = { onChange(withPicked(item)); onClose() }
    DatePickerDialog(
        onDismissRequest = keep,
        confirmButton = { Button(onClick = keep) { Text("Add") } },
        dismissButton = {
            if (item.dueDate != null) TextButton(onClick = {
                onChange(item.copy(dueDate = null, dueTime = null, byWhen = if (item.byWhen == By.DATE) By.WHENEVER else item.byWhen))
                onClose()
            }) { Text("Clear date") }
        },
    ) {
        DatePicker(
            state = picker, showModeToggle = false,
            title = {
                Text(
                    if (item.mode == Mode.REPEAT) "Due each year on" else "Deadline",
                    Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
        if (item.mode == Mode.ONCE) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val options = listOf<Pair<String?, String>>(null to "No time") + (6..23).map { h -> "%02d:00".format(h) to Rules.clock(h) }
                Dropdown(options, item.dueTime) { onChange(withPicked(item).copy(dueTime = it)) }
            }
        }
    }
}

@Composable
fun <T> Dropdown(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "", fontSize = 15.sp)
            Icon(AppIcons.chevD, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onPick(v); open = false }) }
        }
    }
}

/** "Not now": pick when to be reminded, or no reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindSheet(title: String, onPick: (label: String, millis: Long?) -> Unit, onDismiss: () -> Unit) {
    val min = 60_000L
    val options = listOf(
        Triple("In 15 minutes", 15 * min, AppIcons.alarm), Triple("In 1 hour", 60 * min, AppIcons.alarm),
        Triple("Tomorrow", 24 * 60 * min, AppIcons.sun), Triple("Next week", 7 * 24 * 60 * min, AppIcons.calendar),
        Triple("No reminder", null, AppIcons.skip),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Remind me again", fontSize = sheetTitle, fontWeight = FontWeight.SemiBold)
            Hint("“$title”", Modifier.padding(bottom = 6.dp))
            options.forEach { (label, ms, icon) -> LinkTile(icon, label, chevron = false) { onPick(label, ms) } }
        }
    }
}

/** Add a free time: days and hours. Swiping down keeps it, like Add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeTimeSheet(onAdd: (FreeWindow) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var days by remember { mutableStateOf(Rules.WEEKDAYS) }
    var start by remember { mutableStateOf(18) }
    var end by remember { mutableStateOf(21) }
    val valid = days != 0 && start != end
    val keep = { if (valid) onAdd(FreeWindow(days = days, startMinute = start * 60, endMinute = end * 60)); onDismiss() }
    val hours = (0..23).map { it to Rules.clock(it) }
    ModalBottomSheet(onDismissRequest = keep, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = cs.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add free time", fontSize = sheetTitle, fontWeight = FontWeight.SemiBold)
            ChipGroup(listOf(Rules.WEEKDAYS to "Weekdays", Rules.WEEKENDS to "Weekends", Rules.EVERYDAY to "Every day"), { days == it }) { days = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Rules.DAY_NAMES.forEachIndexed { i, name ->
                    val bit = 1 shl i
                    val on = days and bit != 0
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (on) cs.primary else Color.Transparent)
                            .border(1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape).clickable { days = days xor bit },
                        contentAlignment = Alignment.Center,
                    ) { Text(name.take(1), fontWeight = FontWeight.SemiBold, color = if (on) cs.onPrimary else cs.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("From", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Dropdown(hours, start) { start = it }
                Text("until", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Dropdown(hours, end) { end = it }
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = cs.surfaceContainerHigh)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = keep, enabled = valid) { Text("Add") }
        }
    }
}

data class Confirm(val title: String, val text: String, val verb: String, val onYes: () -> Unit)

@Composable
fun ConfirmDialog(c: Confirm, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = { Text(c.text) },
        confirmButton = { Button(onClick = { c.onYes(); onDismiss() }) { Text(c.verb) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
