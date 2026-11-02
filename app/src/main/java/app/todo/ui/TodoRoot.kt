package app.todo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.logic.Rules
import app.todo.parse.TaskLineParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String) { NOW("Now"), LIST("List"), PROGRESS("Progress") }

private data class Draft(val item: Item, val isNew: Boolean)

@Composable
fun TodoRoot(vm: TodoViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    TodoTheme(settings.theme, settings.wallpaperColors) { Shell(vm) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shell(vm: TodoViewModel) {
    val cs = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val windows by vm.freeWindows.collectAsStateWithLifecycle()
    val skipped by vm.skipped.collectAsStateWithLifecycle()
    val place by vm.currentPlace.collectAsStateWithLifecycle()

    // The clock ticks every 30 s so pacing, "today" and free times stay current while the app is open.
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPlace() }
    LaunchedEffect(places) { vm.refreshPlace() }
    val ctx = remember(now, settings, place) { vm.ctx(now) }
    val candidates = remember(items, ctx, skipped) { Rules.candidates(items, ctx, skipped) }

    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var page by rememberSaveable { mutableStateOf<SetPage?>(null) }
    var pageFromApp by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var remindFor by remember { mutableStateOf<Item?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var addingWindow by remember { mutableStateOf(false) }
    var nowOpen by remember { mutableStateOf<String?>(null) }
    var showLater by rememberSaveable { mutableStateOf(false) }
    var line by rememberSaveable { mutableStateOf("") }
    var showCues by rememberSaveable { mutableStateOf(false) }

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun say(text: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(text, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    /** Done never deletes. A second tap on a crossed-off item asks before undoing. */
    fun tick(t: Item, fromSuggestion: Boolean = false) {
        if (Rules.isChecked(t, ctx)) {
            val text = when {
                Rules.isYearly(t) -> "“${t.title}” goes back to due " + (Rules.dueDate(t)?.minusYears(1)?.let { Rules.shortDate(it) } ?: "") + "."
                t.mode == Mode.REPEAT -> "“${t.title}” goes back to ${(Rules.doneNow(t, ctx) - 1).coerceAtLeast(0)} of ${t.target} ${Rules.periodText(t.period)}."
                else -> "“${t.title}” goes back on your list."
            }
            confirm = Confirm("Mark as not done?", text, "Undo") { vm.revert(t) }
            return
        }
        val before = vm.finish(t, fromSuggestion)
        nowOpen = null
        var msg = "Done: " + t.title
        if (Rules.isYearly(t)) Rules.showFrom(Rules.finished(t, ctx))?.let { msg += " · back " + Rules.shortDate(it) + " next year" }
        say(msg) { vm.save(before) }
    }

    fun edit(t: Item) { draft = Draft(t, isNew = false) }

    fun addLine() {
        if (line.isBlank()) return
        draft = Draft(TaskLineParser.parse(line, ctx.today), isNew = true)
        line = ""
        showCues = false
    }

    fun keepDraft() {
        val d = draft ?: return
        draft = null
        if (d.item.title.isBlank()) return
        vm.save(d.item.copy(title = d.item.title.trim()))
    }

    BackHandler(enabled = page != null) {
        page = if (page != SetPage.HOME && !pageFromApp) SetPage.HOME else null
        pageFromApp = false
    }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            val p = page
            if (p == null) {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tab.label, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
                        Text(subtitle(tab, items, ctx), fontSize = 14.sp, lineHeight = 20.sp, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { page = SetPage.HOME; pageFromApp = false }) { Icon(AppIcons.gear, "Settings") }
                }
            } else {
                Row(Modifier.statusBarsPadding().heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        page = if (p != SetPage.HOME && !pageFromApp) SetPage.HOME else null
                        pageFromApp = false
                    }) { Icon(AppIcons.back, "Back") }
                    Text(p.title, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        bottomBar = {
            if (page == null) Column(Modifier.imePadding()) {
                if (tab == Tab.LIST) AddDock(line, { line = it }, showCues, { showCues = !showCues }, ::addLine, ctx.today)
                NavigationBar(containerColor = cs.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; nowOpen = null },
                            icon = { Icon(when (t) { Tab.NOW -> AppIcons.bolt; Tab.LIST -> AppIcons.list; Tab.PROGRESS -> AppIcons.chart }, null) },
                            label = { Text(t.label, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = cs.secondaryContainer),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            val p = page
            if (p != null) {
                SettingsPage(
                    p,
                    SettingsEnv(
                        vm, settings, items, places, windows, ctx, candidates,
                        say = { say(it) }, confirm = { confirm = it }, addFreeTime = { addingWindow = true },
                        go = { page = it; pageFromApp = false },
                    ),
                )
            } else when (tab) {
                Tab.NOW -> NowScreen(
                    candidates, ctx, windows, places, settings, nowOpen,
                    onToggleOpen = { id -> nowOpen = if (nowOpen == id) null else id },
                    onDone = { tick(it, fromSuggestion = it.id == candidates.firstOrNull()?.id) },
                    onNotNow = { remindFor = it },
                    onEdit = ::edit,
                    onSignals = { page = SetPage.SIGNALS; pageFromApp = true },
                )
                Tab.LIST -> ListScreen(items, ctx, showLater, { showLater = !showLater }, { tick(it) }, ::edit)
                Tab.PROGRESS -> ProgressScreen(items, ctx, onPlusOne = { t ->
                    val before = vm.finish(t)
                    say("Done: " + t.title) { vm.save(before) }
                }, onEdit = ::edit)
            }
        }
    }

    draft?.let { d ->
        ItemSheet(
            draft = d.item, isNew = d.isNew,
            placeKeys = places.map { it.id } + "out",
            onChange = { draft = d.copy(item = it) },
            onKeep = ::keepDraft,
            onCancelOrDelete = {
                draft = null
                if (!d.isNew) {
                    val gone = items.firstOrNull { it.id == d.item.id }
                    if (gone != null) {
                        vm.delete(gone)
                        say("Deleted: " + gone.title) { vm.save(gone) }
                    }
                }
            },
        )
    }
    remindFor?.let { t ->
        RemindSheet(t.title, onPick = { label, ms ->
            remindFor = null
            vm.notNow(t, ms)
            say(if (ms == null) "Skipped: ${t.title}" else "Snoozed: ${t.title} · ${label.lowercase()}") { vm.undoNotNow(t) }
        }, onDismiss = { remindFor = null })
    }
    if (addingWindow) FreeTimeSheet(onAdd = { vm.addFreeWindow(it) }, onDismiss = { addingWindow = false })
    confirm?.let { c -> ConfirmDialog(c) { confirm = null } }
}

private fun subtitle(tab: Tab, items: List<Item>, ctx: app.todo.logic.Ctx): String = when (tab) {
    Tab.NOW -> ctx.now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())) + ", " + Rules.clock(ctx.now.hour, ctx.now.minute)
    Tab.LIST -> {
        val active = items.filter { Rules.onList(it, ctx) && !Rules.isLater(it, ctx) }
        val done = active.count { Rules.isChecked(it, ctx) }
        "${active.size - done} to do" + if (done > 0) " · $done done today" else ""
    }
    Tab.PROGRESS -> {
        val rep = items.filter { it.mode == Mode.REPEAT && it.period != Per.YEAR }
        val behind = rep.count { val p = Rules.pace(it, ctx); p.left > 0 && p.behind >= 1 }
        "${rep.size} repeating · " + if (behind > 0) "$behind behind pace" else "all on pace"
    }
}

/** The add box above the tabs on List: typing shortcuts, a live preview of what the line will fill in, and Add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDock(
    line: String,
    onLine: (String) -> Unit,
    showCues: Boolean,
    toggleCues: () -> Unit,
    onAdd: () -> Unit,
    today: java.time.LocalDate,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(cs.background).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = cs.surfaceContainerHigh)
        AnimatedVisibility(showCues) {
            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow) {
                Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Typing shortcuts", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = toggleCues) { Icon(AppIcons.x, "Close shortcuts", Modifier.size(20.dp)) }
                    }
                    TaskLineParser.help.forEach { (cue, means) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                cue, Modifier.clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 1.dp).weight(1f, fill = false),
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            )
                            Text(means, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        }
                    }
                    Hint("Anything else stays in the title. Every cue can be fixed on the next screen.")
                }
            }
        }
        if (line.isNotBlank()) {
            val p = TaskLineParser.parse(line, today)
            val chips = buildList {
                p.minutes?.let { add(if (it < 60) "$it min" else "${it / 60} hr" + if (it % 60 != 0) " ${it % 60} min" else "") }
                if (p.mode == Mode.REPEAT) add(if (p.period == Per.YEAR) "yearly" else "${p.target}× per ${p.period.label}")
                else if (p.byWhen == app.todo.data.By.DATE) add(Rules.dueLabel(p))
                else if (p.byWhen != app.todo.data.By.WHENEVER) add(p.byWhen.label)
                app.todo.data.BestTime.entries.filter { it.name in p.times.split(',') }.forEach { add(it.label) }
                p.places.split(',').filter { it.isNotEmpty() }.forEach { add("@$it") }
                if (p.garmin) add("Garmin counts")
            }
            FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Hint("Adds", Modifier.align(Alignment.CenterVertically))
                PChip(p.title, strong = true)
                chips.forEach { PChip(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = toggleCues) { Icon(AppIcons.help, "Typing shortcuts", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
            Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                if (line.isEmpty()) Text("Add: read 20m !evening", fontSize = 16.sp, color = cs.onSurfaceVariant)
                BasicTextField(
                    value = line, onValueChange = onLine, singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary),
            ) { Icon(AppIcons.plus, "Add item", tint = cs.onPrimary) }
        }
    }
}

@Composable
private fun PChip(text: String, strong: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (strong) cs.primaryContainer else cs.tertiaryContainer).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (strong) cs.onPrimaryContainer else cs.onTertiaryContainer) }
}
