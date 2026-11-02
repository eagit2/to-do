package app.todo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.todo.data.AppSettings
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Place
import app.todo.data.ThemeChoice
import app.todo.device.Device
import app.todo.logic.Ctx
import app.todo.logic.Rules
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SetPage(val title: String) {
    HOME("Settings"), NUDGES("Nudges and notifications"), FREE("Free times"), PLACES("Places"), SIGNALS("Signals"),
    CALENDAR("Calendar"), HEALTH("Garmin and Health Connect"), PERMS("Permissions"), LOOK("Appearance"), DATA("Backup and data"),
}

/** Shared by the Settings pages: what they read, and how they act. */
class SettingsEnv(
    val vm: TodoViewModel,
    val settings: AppSettings,
    val items: List<Item>,
    val places: List<Place>,
    val windows: List<FreeWindow>,
    val ctx: Ctx,
    val candidates: List<Item>,
    val say: (String) -> Unit,
    val confirm: (Confirm) -> Unit,
    val addFreeTime: () -> Unit,
    val go: (SetPage) -> Unit,
)

@Composable
fun SettingsPage(page: SetPage, env: SettingsEnv) {
    // Permission state can change in Android settings while we're away, so re-read it on every resume.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val perms = rememberPerms(resumes)
    when (page) {
        SetPage.HOME -> SettingsHome(env, perms)
        SetPage.NUDGES -> NudgesPage(env)
        SetPage.FREE -> FreePage(env)
        SetPage.PLACES -> PlacesPage(env)
        SetPage.SIGNALS -> SignalsPage(env, perms)
        SetPage.CALENDAR -> CalendarPage(env, perms)
        SetPage.HEALTH -> HealthPage(env)
        SetPage.PERMS -> PermsPage(perms) { resumes++ }
        SetPage.LOOK -> LookPage(env)
        SetPage.DATA -> DataPage(env)
    }
}

data class Perm(val key: String, val title: String, val why: String, val icon: ImageVector, val granted: Boolean, val later: Boolean = false)

@Composable
private fun rememberPerms(@Suppress("UNUSED_PARAMETER") tick: Int): List<Perm> {
    val c = LocalContext.current
    return listOf(
        Perm("notif", "Notifications", "To show nudges and reminders.", AppIcons.bell, Device.notificationsAllowed(c)),
        Perm("activity", "Physical activity", "To tell sitting still from walking or driving.", AppIcons.walk, Device.has(c, Manifest.permission.ACTIVITY_RECOGNITION)),
        Perm("location", "Location while using", "Android needs it to read the Wi-Fi name for places.", AppIcons.wifi, Device.has(c, Manifest.permission.ACCESS_FINE_LOCATION)),
        Perm("bgLocation", "Location all the time", "Only for the optional 150 m circles around places.", AppIcons.pin, Device.has(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
        Perm("calendar", "Calendar", "To skip times you're busy.", AppIcons.calendar, Device.has(c, Manifest.permission.READ_CALENDAR)),
        Perm("health", "Health Connect", "To count Garmin workouts and read steps. Comes in a later build.", AppIcons.watch, false, later = true),
        Perm("battery", "Unrestricted battery", "So Android doesn't put off idle checks.", AppIcons.battery, Device.batteryUnrestricted(c)),
    )
}

@Composable
private fun SettingsHome(env: SettingsEnv, perms: List<Perm>) {
    val s = env.settings
    val notifOn = listOf(s.notifyIdle, s.notifySnooze, s.notifyDeadline, s.notifyOut).count { it }
    val set = env.places.filter { it.isSet }.map { it.label }
    val unset = env.places.filter { !it.isSet }.map { it.label }
    val granted = perms.count { it.granted }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHead("When to nudge")
        LinkTile(AppIcons.bell, SetPage.NUDGES.title, "${s.nudgesPerDay} a day · $notifOn of 4 kinds on" + if (paused(s)) " · paused" else "") { env.go(SetPage.NUDGES) }
        LinkTile(AppIcons.clock, SetPage.FREE.title, "${env.windows.size} set" + if (s.useCalendar) " · calendar busy time taken out" else "") { env.go(SetPage.FREE) }
        LinkTile(AppIcons.pin, SetPage.PLACES.title, (if (set.isEmpty()) "None set" else set.joinToString(", ") + " set") + if (unset.isEmpty()) "" else " · " + unset.joinToString(", ") + " not yet") { env.go(SetPage.PLACES) }
        LinkTile(AppIcons.pulse, SetPage.SIGNALS.title, "What would block a nudge right now") { env.go(SetPage.SIGNALS) }
        SectionHead("Connections")
        LinkTile(AppIcons.calendar, SetPage.CALENDAR.title, if (s.useCalendar) "Busy events count" else "Not used") { env.go(SetPage.CALENDAR) }
        LinkTile(AppIcons.watch, SetPage.HEALTH.title, "Workouts " + (if (s.workoutsCount) "count" else "don't count") + " · steps " + if (s.stepsConfirmIdle) "confirm idle" else "not used") { env.go(SetPage.HEALTH) }
        LinkTile(AppIcons.shield, SetPage.PERMS.title, "$granted of ${perms.size} allowed") { env.go(SetPage.PERMS) }
        SectionHead("App")
        LinkTile(AppIcons.palette, SetPage.LOOK.title, s.theme.name.lowercase().replaceFirstChar { it.uppercase() } + " theme" + (if (s.wallpaperColors) " · wallpaper colors" else "") + " · week starts " + if (s.weekStartsSunday) "Sunday" else "Monday") { env.go(SetPage.LOOK) }
        LinkTile(AppIcons.download, SetPage.DATA.title, "Android backup on") { env.go(SetPage.DATA) }
    }
}

private fun paused(s: AppSettings) = s.pauseUntil != null && s.pauseUntil > System.currentTimeMillis()

@Composable
private fun NudgesPage(env: SettingsEnv) {
    val s = env.settings
    val up = env.vm::updateSettings
    Column {
        SectionHead("Daily limit")
        GroupCard {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextBlock("Idle nudges per day", "Reminders you set yourself don't count.", Modifier.weight(1f))
                Stepper(s.nudgesPerDay, { up { it.copy(nudgesPerDay = (it.nudgesPerDay - 1).coerceAtLeast(1)) } },
                    { up { it.copy(nudgesPerDay = (it.nudgesPerDay + 1).coerceAtMost(5)) } }, "Fewer nudges", "More nudges")
            }
        }
        SectionHead("Pause nudges")
        GroupCard {
            Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val day = 86_400_000L
                val choice = when {
                    !paused(s) -> 0
                    else -> listOf(1, 3, 7).minByOrNull { kotlin.math.abs((s.pauseUntil!! - System.currentTimeMillis()) - it * day) } ?: 0
                }
                ChipGroup(listOf(0 to "Off", 1 to "Until tomorrow", 3 to "3 days", 7 to "1 week"), { choice == it }) { d ->
                    up {
                        val now = System.currentTimeMillis()
                        val until = when (d) {
                            0 -> null
                            1 -> LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            else -> now + d * day
                        }
                        it.copy(pauseUntil = until, pauses = Rules.pausesWith(it.pauses, until, now))
                    }
                }
                Hint("For trips or sick days. Repeating items don't fall behind while paused.")
            }
        }
        SectionHead("What can notify you")
        GroupCard {
            SwitchRow("Idle nudge", "One task that fits, when you unlock and look idle.", s.notifyIdle) { v -> up { it.copy(notifyIdle = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            SwitchRow("Snooze reminders", "At the time you picked in Not now.", s.notifySnooze) { v -> up { it.copy(notifySnooze = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            SwitchRow("Deadline heads-up", "The morning it's due, or 1 hour before a set time.", s.notifyDeadline) { v -> up { it.copy(notifyDeadline = v) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            SwitchRow("While you're out", "Once when you leave home, if errands fit.", s.notifyOut) { v -> up { it.copy(notifyOut = v) } }
        }
        SectionHead("When you count as idle")
        GroupCard {
            Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Still in one spot for at least", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                ChipGroup(listOf(20, 30, 40, 60).map { it to "$it min" }, { s.stillMinutes == it }) { m -> up { it.copy(stillMinutes = m) } }
            }
        }
        GroupCard(Modifier.padding(top = 4.dp)) {
            SwitchRow("Charging means not idle", "Charging at night usually means bed, not free time.", s.chargingMeansBusy) { v -> up { it.copy(chargingMeansBusy = v) } }
        }
        Hint("Notifications themselves arrive with the idle-detection build. These choices are saved now.", Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp))
    }
}

@Composable
private fun FreePage(env: SettingsEnv) {
    val cs = MaterialTheme.colorScheme
    Column {
        Hint("Nudges only come in these times. Calendar events are taken out automatically.", Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Rules.DAY_NAMES.forEachIndexed { i, name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(name.take(1), Modifier.width(14.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                        WeekBar(env.windows.filter { it.days and (1 shl i) != 0 })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(14.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("12a", "6a", "12p", "6p", "12a").forEach { Text(it, fontSize = 11.sp, color = cs.onSurfaceVariant) }
                    }
                }
            }
        }
        SectionHead("Your free times")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            env.windows.forEach { w ->
                val hrs = Rules.clock(w.startMinute / 60, w.startMinute % 60) + " – " + Rules.clock(w.endMinute / 60, w.endMinute % 60)
                PlainTile(AppIcons.clock, Rules.daysLabel(w.days), hrs) {
                    IconButton(onClick = {
                        env.confirm(Confirm("Remove this free time?", Rules.daysLabel(w.days) + "  " + hrs, "Remove") { env.vm.deleteFreeWindow(w) })
                    }) { Icon(AppIcons.trash, "Remove ${Rules.daysLabel(w.days)}, $hrs", Modifier.size(20.dp)) }
                }
            }
        }
        FilledTonalButton(onClick = env.addFreeTime, modifier = Modifier.padding(top = 12.dp)) {
            Icon(AppIcons.plus, null, Modifier.size(20.dp)); Box(Modifier.width(8.dp)); Text("Add free time")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WeekBar(windows: List<FreeWindow>) {
    val cs = MaterialTheme.colorScheme
    var w by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest).onSizeChanged { w = it.width }) {
        val day = 24 * 60f
        windows.flatMap { x ->
            when {
                x.startMinute < x.endMinute -> listOf(x.startMinute to x.endMinute)
                x.startMinute > x.endMinute -> listOf(x.startMinute to 24 * 60, 0 to x.endMinute)
                else -> emptyList()
            }
        }.forEach { (a, b) ->
            with(density) {
                Box(Modifier.offset(x = (w * a / day).toDp()).width((w * (b - a) / day).toDp()).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(cs.primary))
            }
        }
    }
}

@Composable
private fun PlacesPage(env: SettingsEnv) {
    var name by remember { mutableStateOf("") }
    val add = { if (name.isNotBlank()) { env.vm.addPlace(name); name = "" } }
    Column {
        Hint("Stand in a place and tap \"I'm here now\". The app saves that Wi-Fi network. Nothing leaves the phone.", Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            env.places.forEach { p ->
                val how = when {
                    !p.isSet -> "Not set yet"
                    p.wifi != null -> "Wi-Fi \"${p.wifi}\"" + if (env.ctx.place == p.id) " · you're here" else ""
                    else -> "Saved, but no Wi-Fi name (allow Location in Permissions)"
                }
                PlainTile(AppIcons.pin, p.label, how, iconOn = p.isSet) {
                    if (p.isSet) TextButton(onClick = { here(env, p) }) { Text("Update") }
                    else FilledTonalButton(onClick = { here(env, p) }) { Text("I'm here now") }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = name, onValueChange = { name = it }, placeholder = { Text("Add a place, e.g. library") }, singleLine = true,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            )
            FilledTonalButton(onClick = add) { Text("Add") }
        }
        Hint("\"Out\" needs no setup: it means away from every saved place. The optional 150 m circle comes with the idle-detection build.", Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp))
    }
}

private fun here(env: SettingsEnv, p: Place) {
    val wifi = env.vm.setPlaceHere(p)
    env.say(if (wifi != null) "${p.label} saved on Wi-Fi \"$wifi\"." else "${p.label} saved. Allow Location to save the Wi-Fi name too.")
}

private enum class Check { PASS, FAIL, NOT_YET }

@Composable
private fun SignalsPage(env: SettingsEnv, perms: List<Perm>) {
    val c = LocalContext.current
    val s = env.settings
    val win = Rules.windowNow(env.windows, env.ctx)
    val checks = listOf(
        "Idle nudges turned on" to if (s.notifyIdle) Check.PASS else Check.FAIL,
        "Notifications allowed" to if (perms.first { it.key == "notif" }.granted) Check.PASS else Check.FAIL,
        "Nudges not paused" to if (paused(s)) Check.FAIL else Check.PASS,
        "In one of your free times" to if (win != null) Check.PASS else Check.FAIL,
        (if (s.useCalendar) "No calendar event now" else "Calendar not used") to if (s.useCalendar) Check.NOT_YET else Check.PASS,
        "Not driving or moving" to Check.NOT_YET,
        "Do Not Disturb off" to if (Device.dndOn(c)) Check.FAIL else Check.PASS,
        "Something fits this time and place" to if (env.candidates.isNotEmpty()) Check.PASS else Check.FAIL,
        "Under today's nudge limit (0 of ${s.nudgesPerDay})" to Check.PASS,
        ("Not charging" + if (s.chargingMeansBusy) "" else " (ignored)") to if (s.chargingMeansBusy && Device.charging(c)) Check.FAIL else Check.PASS,
        "Looks idle: in one spot ${s.stillMinutes}+ min, phone resting, screen mostly off" to Check.NOT_YET,
    )
    val failing = checks.filter { it.second == Check.FAIL }.map { it.first }
    val warm = LocalWarm.current
    val cs = MaterialTheme.colorScheme
    Column {
        Hint("Live readings from this phone. Lines marked \"not yet\" are measured once idle detection is built.", Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
        val ok = failing.isEmpty()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (ok) cs.primaryContainer else warm.bg).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fg = if (ok) cs.onPrimaryContainer else warm.fg
            Icon(if (ok) AppIcons.check else AppIcons.info, null, Modifier.size(20.dp), tint = fg)
            Text(
                if (ok) "Nothing measured so far would block a nudge" else "Blocked by ${failing.size} check${if (failing.size > 1) "s" else ""}: ${failing.first()}",
                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = fg,
            )
        }
        SectionHead("Right now")
        GroupCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Place: " + placeName(env.ctx.place, env.places) + " · " + Rules.clock(env.ctx.now.hour, env.ctx.now.minute), fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        SectionHead("Checks")
        GroupCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                checks.forEach { (label, state) ->
                    Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val (bg, fg) = when (state) {
                            Check.PASS -> cs.primaryContainer to cs.onPrimaryContainer
                            Check.FAIL -> cs.errorContainer to cs.onErrorContainer
                            Check.NOT_YET -> cs.surfaceContainerHighest to cs.onSurfaceVariant
                        }
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                            Icon(when (state) { Check.PASS -> AppIcons.check; Check.FAIL -> AppIcons.x; Check.NOT_YET -> AppIcons.minus }, null, Modifier.size(14.dp), tint = fg)
                        }
                        Text(label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
                        if (state == Check.NOT_YET) Text("not yet", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPage(env: SettingsEnv, perms: List<Perm>) {
    val s = env.settings
    val up = env.vm::updateSettings
    val c = LocalContext.current
    val allowed = perms.first { it.key == "calendar" }.granted
    val cals = remember(allowed) { Device.calendars(c) }
    val div = @Composable { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh) }
    Column {
        GroupCard { SwitchRow("Use my calendar", "Busy events are taken out of your free times.", s.useCalendar) { v -> up { it.copy(useCalendar = v) } } }
        if (s.useCalendar) {
            SectionHead("Calendars that count as busy")
            GroupCard {
                if (!allowed) Hint("Allow Calendar on the Permissions page to pick calendars.", Modifier.padding(vertical = 16.dp))
                cals.forEachIndexed { i, cal ->
                    if (i > 0) div()
                    SwitchRow(cal.name, null, cal.id !in s.notBusyCalendars) { v ->
                        up { it.copy(notBusyCalendars = if (v) it.notBusyCalendars - cal.id else it.notBusyCalendars + cal.id) }
                    }
                }
            }
            SectionHead("Which events")
            GroupCard {
                SwitchRow("Skip all-day events", "Birthdays and holidays don't make you busy.", s.skipAllDay) { v -> up { it.copy(skipAllDay = v) } }
                div()
                SwitchRow("Skip events marked free", "Only events that show you as busy count.", s.skipFreeEvents) { v -> up { it.copy(skipFreeEvents = v) } }
            }
        }
        SectionHead("Deadlines")
        GroupCard {
            SwitchRow("Put dated deadlines on my calendar", "So they show up next to everything else.", s.deadlinesToCalendar) { v -> up { it.copy(deadlinesToCalendar = v) } }
            if (s.deadlinesToCalendar && cals.isNotEmpty()) {
                div()
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add them to", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Dropdown(cals.map<app.todo.device.PhoneCalendar, Pair<Long?, String>> { it.id to it.name }, s.deadlineCalendarId ?: cals.first().id) { id ->
                        up { it.copy(deadlineCalendarId = id) }
                    }
                }
            }
        }
        Hint("Reading busy times and adding deadlines start with the idle-detection build. These choices are saved now.", Modifier.padding(start = 6.dp, end = 6.dp, top = 14.dp))
    }
}

@Composable
private fun HealthPage(env: SettingsEnv) {
    val s = env.settings
    val up = env.vm::updateSettings
    Column {
        PlainTile(AppIcons.watch, "Garmin via Health Connect", "Not connected yet. Comes in a later build.", iconOn = false) {}
        SectionHead("Workouts")
        GroupCard { SwitchRow("Workouts count toward repeating items", "Only items with Counts Garmin workouts turned on.", s.workoutsCount) { v -> up { it.copy(workoutsCount = v) } } }
        if (s.workoutsCount) {
            GroupCard(Modifier.padding(top = 4.dp)) {
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Shortest workout that counts", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    ChipGroup(listOf(10, 20, 30).map { it to "$it min" }, { s.shortestWorkout == it }) { m -> up { it.copy(shortestWorkout = m) } }
                }
            }
        }
        SectionHead("Idle signals")
        GroupCard { SwitchRow("Use steps to confirm idle", "Garmin syncs late, so steps only confirm and never trigger a nudge.", s.stepsConfirmIdle) { v -> up { it.copy(stepsConfirmIdle = v) } } }
    }
}

@Composable
private fun PermsPage(perms: List<Perm>, refresh: () -> Unit) {
    val c = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val askMany = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    fun open(intent: Intent) = runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        perms.forEach { p ->
            PlainTile(p.icon, p.title, p.why, iconOn = p.granted) {
                when {
                    p.later -> Text("Later", Modifier.padding(end = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    p.granted -> TextButton(onClick = { open(appSettings) }) { Text("Allowed") }
                    else -> Button(onClick = {
                        when (p.key) {
                            "notif" -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                            "activity" -> ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            "location" -> askMany.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            "bgLocation" -> if (perms.first { it.key == "location" }.granted) ask.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                else askMany.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            "calendar" -> ask.launch(Manifest.permission.READ_CALENDAR)
                            "battery" -> open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)))
                        }
                    }) { Text("Allow") }
                }
            }
        }
        Hint("Tap Allowed to change it in Android settings. The app works without any of these, just with fewer signals.", Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp))
    }
}

@Composable
private fun LookPage(env: SettingsEnv) {
    val s = env.settings
    val up = env.vm::updateSettings
    Column {
        SectionHead("Theme")
        Segs(listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark"), s.theme) { t -> up { it.copy(theme = t) } }
        GroupCard(Modifier.padding(top = 8.dp)) { SwitchRow("Use wallpaper colors", "Android's dynamic color, from your wallpaper.", s.wallpaperColors) { v -> up { it.copy(wallpaperColors = v) } } }
        SectionHead("Week starts on")
        Segs(listOf(false to "Monday", true to "Sunday"), s.weekStartsSunday) { v -> up { it.copy(weekStartsSunday = v) } }
        Hint("Weekly counts and pacing reset on this day.", Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp))
        SectionHead("New day starts at")
        ChipGroup(listOf(0 to "Midnight", 3 to "3 am", 5 to "5 am"), { s.dayStartHour == it }) { h -> up { it.copy(dayStartHour = h) } }
        Hint("When crossed-off items come back. A later time suits late nights.", Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp))
    }
}

@Composable
private fun DataPage(env: SettingsEnv) {
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { env.say(if (env.vm.writeBackup(uri)) "Backup saved." else "Couldn't save the backup.") }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.confirm(Confirm("Replace what's on this phone?", "Items, free times, place names and settings come from the file.", "Restore") {
            scope.launch { env.say(if (env.vm.readBackup(uri)) "Restored. Tap \"I'm here now\" in Places again." else "That file couldn't be read.") }
        })
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlainTile(AppIcons.shield, "Android backup is on", "Encrypted with your screen lock and restored on a new phone.") {}
        LinkTile(AppIcons.download, "Save a backup file", "One file you can keep anywhere.", chevron = false) { save.launch("to-do-backup-${LocalDate.now()}.json") }
        LinkTile(AppIcons.upload, "Restore from a file", "Replaces what's on this phone.", chevron = false) { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }
        SectionHead("Later")
        PlainTile(AppIcons.lock, "Weekly review export", "Encrypted, with no names or places, for a Claude review. Comes after the core app.", iconOn = false) {}
        TextButton(onClick = {
            env.confirm(Confirm("Delete everything?", "All items, free times and places are removed from this phone. This can't be undone.", "Delete") {
                env.vm.deleteEverything(); env.say("Everything was deleted.")
            })
        }, modifier = Modifier.padding(top = 16.dp)) {
            Icon(AppIcons.trash, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            Box(Modifier.width(8.dp))
            Text("Delete all data", color = MaterialTheme.colorScheme.error)
        }
    }
}
