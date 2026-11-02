package app.todo.data

import android.content.Context
import app.todo.logic.Pause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Every choice on the Settings pages. Kept as one small JSON blob in SharedPreferences, and in backup files. */
data class AppSettings(
    // Nudges and notifications
    val nudgesPerDay: Int = 1,
    val pauseUntil: Long? = null,
    /** Every pause, so paused days stay out of pacing. The last one may still be running. */
    val pauses: List<Pause> = emptyList(),
    val notifyIdle: Boolean = true,
    val notifySnooze: Boolean = true,
    val notifyDeadline: Boolean = true,
    val notifyOut: Boolean = true,
    val stillMinutes: Int = 40,
    val chargingMeansBusy: Boolean = true,
    // Calendar
    val useCalendar: Boolean = true,
    val skipAllDay: Boolean = true,
    val skipFreeEvents: Boolean = true,
    val deadlinesToCalendar: Boolean = true,
    val deadlineCalendarId: Long? = null,
    /** Calendar ids that do not count as busy. */
    val notBusyCalendars: Set<Long> = emptySet(),
    // Garmin and Health Connect
    val workoutsCount: Boolean = true,
    val shortestWorkout: Int = 20,
    val stepsConfirmIdle: Boolean = true,
    // Appearance
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val wallpaperColors: Boolean = false,
    val weekStartsSunday: Boolean = false,
    val dayStartHour: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("nudgesPerDay", nudgesPerDay).put("pauseUntil", pauseUntil ?: JSONObject.NULL)
        .put("pauses", JSONArray(pauses.map { JSONArray().put(it.from).put(it.until) }))
        .put("notifyIdle", notifyIdle).put("notifySnooze", notifySnooze)
        .put("notifyDeadline", notifyDeadline).put("notifyOut", notifyOut)
        .put("stillMinutes", stillMinutes).put("chargingMeansBusy", chargingMeansBusy)
        .put("useCalendar", useCalendar).put("skipAllDay", skipAllDay).put("skipFreeEvents", skipFreeEvents)
        .put("deadlinesToCalendar", deadlinesToCalendar).put("deadlineCalendarId", deadlineCalendarId ?: JSONObject.NULL)
        .put("notBusyCalendars", JSONArray(notBusyCalendars.toList()))
        .put("workoutsCount", workoutsCount).put("shortestWorkout", shortestWorkout).put("stepsConfirmIdle", stepsConfirmIdle)
        .put("theme", theme.name).put("wallpaperColors", wallpaperColors)
        .put("weekStartsSunday", weekStartsSunday).put("dayStartHour", dayStartHour)

    companion object {
        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            fun long(k: String): Long? = if (o.isNull(k)) null else o.optLong(k)
            val cals = o.optJSONArray("notBusyCalendars")
            val ps = o.optJSONArray("pauses")
            return AppSettings(
                nudgesPerDay = o.optInt("nudgesPerDay", d.nudgesPerDay),
                pauseUntil = long("pauseUntil"),
                pauses = if (ps == null) emptyList() else (0 until ps.length()).mapNotNull { i ->
                    ps.optJSONArray(i)?.let { Pause(it.getLong(0), it.getLong(1)) }
                },
                notifyIdle = o.optBoolean("notifyIdle", d.notifyIdle),
                notifySnooze = o.optBoolean("notifySnooze", d.notifySnooze),
                notifyDeadline = o.optBoolean("notifyDeadline", d.notifyDeadline),
                notifyOut = o.optBoolean("notifyOut", d.notifyOut),
                stillMinutes = o.optInt("stillMinutes", d.stillMinutes),
                chargingMeansBusy = o.optBoolean("chargingMeansBusy", d.chargingMeansBusy),
                useCalendar = o.optBoolean("useCalendar", d.useCalendar),
                skipAllDay = o.optBoolean("skipAllDay", d.skipAllDay),
                skipFreeEvents = o.optBoolean("skipFreeEvents", d.skipFreeEvents),
                deadlinesToCalendar = o.optBoolean("deadlinesToCalendar", d.deadlinesToCalendar),
                deadlineCalendarId = long("deadlineCalendarId"),
                notBusyCalendars = if (cals == null) emptySet() else (0 until cals.length()).map { cals.getLong(it) }.toSet(),
                workoutsCount = o.optBoolean("workoutsCount", d.workoutsCount),
                shortestWorkout = o.optInt("shortestWorkout", d.shortestWorkout),
                stepsConfirmIdle = o.optBoolean("stepsConfirmIdle", d.stepsConfirmIdle),
                theme = runCatching { ThemeChoice.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                wallpaperColors = o.optBoolean("wallpaperColors", d.wallpaperColors),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                dayStartHour = o.optInt("dayStartHour", d.dayStartHour),
            )
        }
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state

    private fun load(): AppSettings =
        prefs.getString("json", null)?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: AppSettings()

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(state.value)
        prefs.edit().putString("json", next.toJson().toString()).apply()
        state.value = next
    }

    fun replace(next: AppSettings) = update { next }
}
