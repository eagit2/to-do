package app.todo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The preview's stroke icons (24 px grid, 1.8 px stroke), so the app looks like what was approved. */
object AppIcons {
    private fun icon(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val check: ImageVector by lazy { icon("check", "M5 12.5l4.5 4.5L19 7.5") }
    val timer: ImageVector by lazy { icon("timer", "M12 5a8 8 0 1 1 0 16 8 8 0 1 1 0-16zM12 9v4l2.5 2M10 2h4") }
    val repeat: ImageVector by lazy { icon("repeat", "M17 2l3 3-3 3M4 11V9a4 4 0 0 1 4-4h12M7 22l-3-3 3-3M20 13v2a4 4 0 0 1-4 4H4") }
    val calendar: ImageVector by lazy { icon("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 3v4M16 3v4") }
    val pin: ImageVector by lazy { icon("pin", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z") }
    val sun: ImageVector by lazy { icon("sun", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4") }
    val moon: ImageVector by lazy { icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val clock: ImageVector by lazy { icon("clock", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2") }
    val watch: ImageVector by lazy { icon("watch", "M8 7h8a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM9 7l1-4h4l1 4M9 17l1 4h4l1-4") }
    val info: ImageVector by lazy { icon("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v5M12 8h.01") }
    val alarm: ImageVector by lazy { icon("alarm", "M12 21a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM12 10.5V14l2 1.5M5 3L2 6M19 3l3 3") }
    val edit: ImageVector by lazy { icon("edit", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4") }
    val plus: ImageVector by lazy { icon("plus", "M12 5v14M5 12h14") }
    val minus: ImageVector by lazy { icon("minus", "M5 12h14") }
    val back: ImageVector by lazy { icon("back", "M20 12H4M10 6l-6 6 6 6") }
    val chevR: ImageVector by lazy { icon("chevR", "M9 6l6 6-6 6") }
    val chevL: ImageVector by lazy { icon("chevL", "M15 6l-6 6 6 6") }
    val chevD: ImageVector by lazy { icon("chevD", "M6 9l6 6 6-6") }
    val bolt: ImageVector by lazy { icon("bolt", "M13 2L4.5 13.5H11L10 22l8.5-11.5H12L13 2z") }
    val list: ImageVector by lazy { icon("list", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01") }
    val chart: ImageVector by lazy { icon("chart", "M4 20V11M10 20V5M16 20v-6M21 20H3") }
    val gear: ImageVector by lazy { icon("gear", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z") }
    val bell: ImageVector by lazy { icon("bell", "M6 9a6 6 0 1 1 12 0c0 6 2.5 8 2.5 8h-17S6 15 6 9zM10.3 20.5a1.9 1.9 0 0 0 3.4 0") }
    val pulse: ImageVector by lazy { icon("pulse", "M3 12h4l3-8 4 16 3-8h4") }
    val shield: ImageVector by lazy { icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3zM9 12l2 2 4-4") }
    val palette: ImageVector by lazy { icon("palette", "M12 21a9 9 0 1 1 0-18c5 0 9 3.6 9 8 0 2.8-2.2 4-4.5 4H15a2 2 0 0 0-1.5 3.3c.6.8.1 2.7-1.5 2.7zM7.5 11.5h.01M10 7.5h.01M15 7.5h.01") }
    val download: ImageVector by lazy { icon("download", "M12 3v12M7 10l5 5 5-5M5 21h14") }
    val upload: ImageVector by lazy { icon("upload", "M12 15V3M7 8l5-5 5 5M5 21h14") }
    val trash: ImageVector by lazy { icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }
    val help: ImageVector by lazy { icon("help", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17h.01") }
    val x: ImageVector by lazy { icon("x", "M6 6l12 12M18 6L6 18") }
    val lock: ImageVector by lazy { icon("lock", "M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4") }
    val wifi: ImageVector by lazy { icon("wifi", "M2 9a15 15 0 0 1 20 0M5.5 12.5a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0M12 19.5h.01") }
    val walk: ImageVector by lazy { icon("walk", "M13 5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM9 22l2.5-7.5L15 17v5M7 12l2.5-4.5 4 .5 2 3.5 3 1M9.5 7.5L7 9.5 6.5 13") }
    val battery: ImageVector by lazy { icon("battery", "M8 5h8a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM10 2h4M12.5 9l-2 4h3l-2 4") }
    val skip: ImageVector by lazy { icon("skip", "M6 5l9 7-9 7V5zM18 5v14") }
    val unlock: ImageVector by lazy { icon("unlock", "M6 11h12v10H6zM8 11V7a4 4 0 0 1 7.5-2") }
}
