package app.todo.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.todo.logic.Tag
import app.todo.logic.TagKind

fun TagKind.icon(): ImageVector = when (this) {
    TagKind.GARMIN -> AppIcons.watch
    TagKind.DONE -> AppIcons.check
    TagKind.REPEAT -> AppIcons.repeat
    TagKind.BEHIND -> AppIcons.info
    TagKind.DATE, TagKind.BY -> AppIcons.calendar
    TagKind.ASAP -> AppIcons.bolt
    TagKind.SIZE -> AppIcons.timer
    TagKind.TIME -> AppIcons.sun
    TagKind.EVENING -> AppIcons.moon
    TagKind.PLACE -> AppIcons.pin
    TagKind.SNOOZED -> AppIcons.alarm
    TagKind.HIDDEN -> AppIcons.clock
}

/** Icon + text tags under an item; warm ones get the soft yellow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<Tag>) {
    val warm = LocalWarm.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tags.forEach { t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = if (t.warm) Modifier.clip(RoundedCornerShape(6.dp)).background(warm.bg).padding(horizontal = 6.dp) else Modifier,
            ) {
                val c = if (t.warm) warm.fg else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(t.kind.icon(), null, Modifier.size(14.dp), tint = c)
                Text(t.text, fontSize = 13.sp, lineHeight = 18.sp, color = c, fontWeight = if (t.warm) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** The round tick box. Filled when done. */
@Composable
fun TickRing(checked: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (checked) cs.primary else Color.Transparent)
                .border(2.dp, if (checked) cs.primary else cs.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.check, null, Modifier.size(16.dp), tint = cs.onPrimary)
        }
    }
}

/** One item row: tick, title (struck through when done) and tags. */
@Composable
fun ItemTile(
    title: String,
    tags: List<Tag>,
    checked: Boolean,
    onTick: (() -> Unit)?,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        checked -> Color.Transparent
        highlighted -> cs.surfaceContainerHigh
        else -> cs.surfaceContainerLow
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .then(if (checked) Modifier.border(1.dp, cs.surfaceContainerHigh, RoundedCornerShape(20.dp)) else Modifier)
            .padding(start = if (onTick == null) 12.dp else 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onTick != null) TickRing(checked, (if (checked) "Undo: " else "Done: ") + title, onTick)
        Column(
            Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title, fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = if (checked) FontWeight.Normal else FontWeight.Medium,
                color = if (checked) cs.onSurfaceVariant else cs.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (tags.isNotEmpty()) TagRow(tags)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHead(text: String, count: Int? = null, extra: String? = null) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
        if (count != null) {
            Box(
                Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(cs.secondaryContainer)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(count.toString(), fontSize = 12.sp, color = cs.onSecondaryContainer) }
        }
        if (extra != null) Text(extra, fontSize = 14.sp, color = cs.onSurfaceVariant)
    }
}

/** Multi- or single-select chips that wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(options: List<Pair<T, String>>, isOn: (T) -> Boolean, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = isOn(value)
            FilterChip(
                selected = on, onClick = { onPick(value) }, label = { Text(label) },
                shape = RoundedCornerShape(if (on) 18.dp else 10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

/** Full-width segmented choice; the picked one rounds into a pill. */
@Composable
fun <T> Segs(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val r by animateDpAsState(if (on) 22.dp else 12.dp, label = "seg")
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(r))
                    .background(if (on) cs.primary else cs.surfaceContainerHigh).clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) cs.onPrimary else cs.onSurface)
            }
        }
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) { content() }
    }
}

@Composable
fun TextBlock(title: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
        if (!sub.isNullOrEmpty()) Text(sub, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextBlock(title, sub, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
fun IconBubble(icon: ImageVector, on: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (on) cs.secondaryContainer else cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant) }
}

/** A tappable row with a bubble icon, used on Settings and in the remind sheet. */
@Composable
fun LinkTile(icon: ImageVector, title: String, sub: String? = null, chevron: Boolean = true, iconOn: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerLow)
            .clickable(onClick = onClick).padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBubble(icon, iconOn)
        TextBlock(title, sub, Modifier.weight(1f))
        if (chevron) Icon(AppIcons.chevR, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
    }
}

/** A plain row with a bubble icon and a trailing control. */
@Composable
fun PlainTile(icon: ImageVector, title: String, sub: String?, iconOn: Boolean = true, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBubble(icon, iconOn)
        TextBlock(title, sub, Modifier.weight(1f).padding(vertical = 4.dp))
        trailing()
    }
}

@Composable
fun Stepper(value: Int, onLess: () -> Unit, onMore: () -> Unit, lessLabel: String, moreLabel: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onLess, modifier = Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer)) {
            Icon(AppIcons.minus, lessLabel, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
        }
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 34.dp))
        IconButton(onClick = onMore, modifier = Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer)) {
            Icon(AppIcons.plus, moreLabel, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun EmptyCard(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
