package app.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.todo.data.AppSettings
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.Per
import app.todo.data.Place
import app.todo.data.timeSet
import app.todo.data.BestTime
import app.todo.logic.Ctx
import app.todo.logic.Rules
import kotlin.math.roundToInt

fun placeName(key: String?, places: List<Place>): String = when (key) {
    null -> "Place unknown"
    "out" -> "Out"
    else -> places.firstOrNull { it.id == key }?.label ?: key.replaceFirstChar { it.uppercase() }
}

/** Opens on one suggestion that fits the time and place, with a short list of what else fits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowScreen(
    candidates: List<Item>,
    ctx: Ctx,
    windows: List<FreeWindow>,
    places: List<Place>,
    settings: AppSettings,
    openId: String?,
    onToggleOpen: (String) -> Unit,
    onDone: (Item) -> Unit,
    onNotNow: (Item) -> Unit,
    onEdit: (Item) -> Unit,
    onSignals: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val win = Rules.windowNow(windows, ctx)
    Column {
        FlowRow(
            Modifier.padding(top = 2.dp, bottom = 14.dp).clickable(onClick = onSignals),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CtxChip(AppIcons.pin, placeName(ctx.place, places))
            CtxChip(AppIcons.clock, if (win != null) "Free until " + Rules.clock(win.endMinute / 60, win.endMinute % 60) else "Outside free times")
            CtxChip(AppIcons.calendar, if (settings.useCalendar) "Calendar not read yet" else "Calendar off")
        }
        val pick = candidates.firstOrNull()
        if (pick == null) {
            EmptyCard(
                "Nothing fits right now",
                "No open item matches this time and place. Enjoy the break, or check which signal is in the way.",
            ) {
                FilledTonalButton(onClick = onSignals) {
                    Icon(AppIcons.pulse, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Check signals")
                }
            }
        } else {
            Hero(pick, ctx, onDone = { onDone(pick) }, onNotNow = { onNotNow(pick) }, onEdit = { onEdit(pick) })
        }
        val also = candidates.drop(1).take(5)
        if (also.isNotEmpty()) {
            SectionHead("Also fits now", also.size)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                also.forEach { t ->
                    // Tapping a row shows a tick box beside it (and Edit); tapping again hides them.
                    val open = openId == t.id
                    ItemTile(
                        title = t.title, tags = Rules.tags(t, ctx), checked = false,
                        onTick = if (open) ({ onDone(t) }) else null,
                        onClick = { onToggleOpen(t.id) },
                        highlighted = open,
                        trailing = if (open) ({
                            IconButton(onClick = { onEdit(t) }) { Icon(AppIcons.edit, "Edit " + t.title, Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
                        }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CtxChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(t: Item, ctx: Ctx, onDone: () -> Unit, onNotNow: () -> Unit, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val warm = LocalWarm.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
        Column(Modifier.padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().offset(x = 12.dp).heightIn(max = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Best fit right now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(AppIcons.edit, "Edit " + t.title, Modifier.size(20.dp)) }
            }
            Text(t.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Rules.tags(t, ctx).forEach { g ->
                    Row(
                        Modifier.height(30.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (g.warm) warm.bg else cs.surfaceContainerLowest.copy(alpha = 0.62f)).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val c = if (g.warm) warm.fg else cs.onPrimaryContainer
                        Icon(g.kind.icon(), null, Modifier.size(16.dp), tint = c)
                        Text(g.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.info, null, Modifier.size(18.dp).padding(top = 1.dp))
                Text(Rules.why(t, ctx), fontSize = 14.sp, lineHeight = 20.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDone, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(AppIcons.check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Done", fontSize = 16.sp)
                }
                Button(
                    onClick = onNotNow, modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.surfaceContainerLowest, contentColor = cs.primary),
                ) {
                    Icon(AppIcons.alarm, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Not now", fontSize = 16.sp)
                }
            }
        }
    }
}

/** Everything open, with someday items and not-yet-due yearly items folded at the bottom. */
@Composable
fun ListScreen(
    items: List<Item>,
    ctx: Ctx,
    showLater: Boolean,
    onToggleLater: () -> Unit,
    onTick: (Item) -> Unit,
    onEdit: (Item) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sorted = Rules.sorted(items.filter { Rules.onList(it, ctx) }, ctx)
    val (later, active) = sorted.partition { Rules.isLater(it, ctx) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (active.isEmpty()) EmptyCard("The list is empty", "Type one line below, like \"water plants 1x @home\".")
        active.forEach { t ->
            ItemTile(t.title, Rules.tags(t, ctx), Rules.isChecked(t, ctx), onTick = { onTick(t) }, onClick = { onEdit(t) })
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggleLater)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Someday and later (${later.size})", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
            Icon(if (showLater) AppIcons.chevD else AppIcons.chevR, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        }
        if (showLater) later.forEach { t ->
            ItemTile(t.title, Rules.tags(t, ctx), Rules.isChecked(t, ctx), onTick = { onTick(t) }, onClick = { onEdit(t) })
        }
    }
}

/** Repeating items only, grouped by period, with a bar and a pace mark. */
@Composable
fun ProgressScreen(items: List<Item>, ctx: Ctx, onPlusOne: (Item) -> Unit, onEdit: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val warm = LocalWarm.current
    val repeating = items.filter { it.mode == Mode.REPEAT && !Rules.isYearly(it) }
    Column {
        if (repeating.isEmpty()) EmptyCard("Nothing repeats yet", "Add one like \"meditate 10/month\" in List.")
        listOf(Per.DAY to "Today", Per.WEEK to "This week", Per.MONTH to "This month").forEach { (per, label) ->
            val group = repeating.filter { it.period == per }
            if (group.isEmpty()) return@forEach
            SectionHead(label)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { t ->
                    val p = Rules.pace(t, ctx)
                    val done = Rules.doneNow(t, ctx)
                    val doneAll = p.left == 0
                    val behind = !doneAll && p.behind >= 1
                    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f).clickable { onEdit(t) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(t.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                                    val tags = buildList {
                                        if (t.garmin) add(app.todo.logic.Tag(app.todo.logic.TagKind.GARMIN, "Garmin counts"))
                                        val ts = t.timeSet
                                        if (ts.isNotEmpty()) add(
                                            app.todo.logic.Tag(
                                                app.todo.logic.TagKind.TIME,
                                                BestTime.entries.filter { it in ts }.joinToString(", ") { it.label },
                                            )
                                        )
                                    }
                                    if (tags.isNotEmpty()) TagRow(tags)
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(done.toString(), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                                    Text(" / ${t.target}", fontSize = 16.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                                }
                            }
                            PaceBar(done.toFloat() / t.target, (p.expected / t.target).toFloat())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (bg, fg) = when {
                                    doneAll -> cs.primaryContainer to cs.onPrimaryContainer
                                    behind -> warm.bg to warm.fg
                                    else -> cs.secondaryContainer to cs.onSecondaryContainer
                                }
                                Box(Modifier.clip(RoundedCornerShape(15.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(
                                        when {
                                            doneAll -> if (per == Per.DAY) "Done for today" else "Done for this " + per.label
                                            behind -> "About ${p.behind.roundToInt()} behind"
                                            else -> "On pace"
                                        },
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(onClick = { onPlusOne(t) }) {
                                    Icon(AppIcons.plus, "Count one more: " + t.title, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("1")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (repeating.isNotEmpty()) Hint(
            "The thin mark on each bar is where you'd be if you were exactly on pace. Behind pace moves an item up on Now.",
            Modifier.padding(start = 6.dp, end = 6.dp, top = 16.dp),
        )
    }
}

@Composable
private fun PaceBar(fill: Float, pace: Float) {
    val cs = MaterialTheme.colorScheme
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(20.dp).onSizeChanged { widthPx = it.width }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest))
        Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.primary))
        val x = with(density) { (widthPx * pace.coerceIn(0.01f, 0.99f)).toDp() } - 2.dp
        Box(Modifier.offset(x = x).width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(cs.onSurface))
    }
}
