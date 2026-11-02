package app.todo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.todo.TodoApp
import app.todo.data.AppSettings
import app.todo.data.By
import app.todo.data.Completion
import app.todo.data.FreeWindow
import app.todo.data.Item
import app.todo.data.Mode
import app.todo.data.NudgeEvent
import app.todo.data.NudgeOutcome
import app.todo.data.Per
import app.todo.data.Place
import app.todo.data.TodoDatabase
import app.todo.device.Device
import app.todo.logic.Ctx
import app.todo.logic.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TodoApp
    private val db: TodoDatabase = app.db
    private val store = app.settings

    val settings: StateFlow<AppSettings> = store.settings

    val items: StateFlow<List<Item>> =
        db.items().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val places: StateFlow<List<Place>> =
        db.places().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val freeWindows: StateFlow<List<FreeWindow>> =
        db.freeWindows().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Items set aside with "Not now · No reminder". They come back next launch. */
    private val skippedState = MutableStateFlow<Set<String>>(emptySet())
    val skipped: StateFlow<Set<String>> = skippedState.asStateFlow()

    /** Read from Wi-Fi on each resume. Null means we can't tell yet. */
    private val placeState = MutableStateFlow<String?>(null)
    val currentPlace: StateFlow<String?> = placeState.asStateFlow()

    fun refreshPlace() {
        placeState.value = Device.currentPlace(app, places.value)
    }

    fun ctx(now: LocalDateTime = LocalDateTime.now()): Ctx {
        val s = settings.value
        return Ctx(
            now = now,
            place = placeState.value,
            weekStart = if (s.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY,
            dayStartHour = s.dayStartHour,
            pauses = s.pauses,
        )
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) = store.update(change)

    fun save(item: Item) = viewModelScope.launch { db.items().upsert(item) }

    fun delete(item: Item) = viewModelScope.launch { db.items().delete(item) }

    /** What Undo needs to put back: the item as it was, and the history rows to take out again. */
    data class Undo(val before: Item, val completionId: String? = null, val nudgeId: String? = null)

    /** Marks done (never deletes). Undo restores the item and removes the rows logged here, so stats don't overcount. */
    fun finish(item: Item, fromSuggestion: Boolean = false): Undo {
        val done = Rules.finished(item, ctx())
        val completion = Completion(taskId = item.id)
        val nudge = if (fromSuggestion) NudgeEvent(taskId = item.id, at = completion.at, placeLabel = placeState.value, outcome = NudgeOutcome.DONE) else null
        viewModelScope.launch {
            db.withTransaction {
                db.items().upsert(done)
                db.completions().insert(completion)
                nudge?.let { db.nudges().insert(it) }
            }
        }
        return Undo(item, completion.id, nudge?.id)
    }

    fun undo(u: Undo) = viewModelScope.launch {
        db.withTransaction {
            db.items().upsert(u.before)
            u.completionId?.let { db.completions().delete(it) }
            u.nudgeId?.let { db.nudges().delete(it) }
        }
        if (u.nudgeId != null) skippedState.value = skippedState.value - u.before.id
    }

    /** "Mark as not done?" on a crossed-off item. Its latest Done row (and the matching suggestion row) goes too. */
    fun revert(item: Item) {
        val back = Rules.reverted(item, ctx())
        viewModelScope.launch {
            db.withTransaction {
                db.items().upsert(back)
                db.completions().latest(item.id)?.let { c ->
                    db.completions().delete(c.id)
                    db.nudges().deleteDoneAt(item.id, c.at)
                }
            }
        }
    }

    /** "Not now". [remindInMillis] null means no reminder: the item just steps aside until next launch. */
    fun notNow(item: Item, remindInMillis: Long?): Undo {
        val nudge = NudgeEvent(
            taskId = item.id, placeLabel = placeState.value,
            outcome = if (remindInMillis == null) NudgeOutcome.DISMISSED else NudgeOutcome.SNOOZED,
        )
        if (remindInMillis == null) skippedState.value = skippedState.value + item.id
        viewModelScope.launch {
            db.withTransaction {
                if (remindInMillis != null) db.items().upsert(item.copy(snoozedUntil = System.currentTimeMillis() + remindInMillis))
                db.nudges().insert(nudge)
            }
        }
        return Undo(item, nudgeId = nudge.id)
    }

    fun addFreeWindow(window: FreeWindow) = viewModelScope.launch { db.freeWindows().insert(window) }

    fun deleteFreeWindow(window: FreeWindow) = viewModelScope.launch { db.freeWindows().delete(window) }

    /** "I'm here now": saves the Wi-Fi network the phone is on. Returns what was saved, for the message. */
    fun setPlaceHere(place: Place): String? {
        val wifi = Device.wifiName(app)
        viewModelScope.launch {
            db.places().upsert(place.copy(isSet = true, wifi = wifi ?: place.wifi))
            placeState.value = Device.currentPlace(app, db.places().snapshot())
        }
        return wifi
    }

    fun addPlace(name: String) = viewModelScope.launch {
        val label = name.trim().replaceFirstChar { it.uppercase() }
        val id = name.trim().lowercase().replace(Regex("\\s+"), "-")
        if (id.isEmpty() || id == "out" || places.value.any { it.id == id }) return@launch
        db.places().upsert(Place(id = id, label = label, sortOrder = places.value.size))
    }

    fun deletePlace(place: Place) = viewModelScope.launch {
        db.places().clear()
        db.places().insertAll(places.value.filter { it.id != place.id })
    }

    fun deleteEverything() = viewModelScope.launch {
        db.withTransaction {
            db.items().clear(); db.freeWindows().clear(); db.completions().clear(); db.nudges().clear(); db.places().clear()
            db.openHelper.writableDatabase.execSQL(TodoDatabase.SEED_PLACES)
        }
        store.replace(AppSettings())
        skippedState.value = emptySet()
        placeState.value = null
    }

    /** One JSON file with items, free times, place names and settings. Wi-Fi names are left out. */
    suspend fun writeBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject()
                .put("version", 3)
                .put("items", JSONArray(db.items().snapshot().map { itemJson(it) }))
                .put("freeWindows", JSONArray(db.freeWindows().snapshot().map {
                    JSONObject().put("id", it.id).put("days", it.days).put("start", it.startMinute).put("end", it.endMinute)
                }))
                .put("places", JSONArray(db.places().snapshot().map {
                    JSONObject().put("id", it.id).put("label", it.label).put("sortOrder", it.sortOrder)
                }))
                .put("settings", settings.value.toJson())
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(o.toString(2).toByteArray()) }
        }.isSuccess
    }

    /** Replaces what's on the phone with the file's contents. Places come back unset, so tap "I'm here now" again. */
    suspend fun readBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val o = JSONObject(text)
            val items = o.getJSONArray("items").let { a -> (0 until a.length()).map { itemFrom(a.getJSONObject(it)) } }
            val windows = o.getJSONArray("freeWindows").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    FreeWindow(id = it.getString("id"), days = it.getInt("days"), startMinute = it.getInt("start"), endMinute = it.getInt("end"))
                }
            }
            val places = o.getJSONArray("places").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Place(id = it.getString("id"), label = it.getString("label"), sortOrder = it.optInt("sortOrder"))
                }
            }
            db.withTransaction {
                db.items().clear(); db.freeWindows().clear(); db.places().clear()
                db.items().insertAll(items); db.freeWindows().insertAll(windows); db.places().insertAll(places)
            }
            o.optJSONObject("settings")?.let { store.replace(AppSettings.fromJson(it)) }
        }.isSuccess
    }

    private fun itemJson(t: Item) = JSONObject()
        .put("id", t.id).put("title", t.title).put("minutes", t.minutes ?: JSONObject.NULL)
        .put("mode", t.mode.name).put("byWhen", t.byWhen.name)
        .put("dueDate", t.dueDate ?: JSONObject.NULL).put("dueTime", t.dueTime ?: JSONObject.NULL)
        .put("target", t.target).put("period", t.period.name).put("doneCount", t.doneCount)
        .put("periodKey", t.periodKey ?: JSONObject.NULL).put("doneDay", t.doneDay ?: JSONObject.NULL)
        .put("doneAt", t.doneAt ?: JSONObject.NULL).put("leadMonths", t.leadMonths).put("garmin", t.garmin)
        .put("times", t.times).put("places", t.places).put("createdAt", t.createdAt)

    private fun itemFrom(o: JSONObject): Item {
        fun str(k: String) = if (o.isNull(k)) null else o.optString(k)
        return Item(
            id = o.getString("id"), title = o.getString("title"),
            minutes = if (o.isNull("minutes")) null else o.optInt("minutes"),
            mode = runCatching { Mode.valueOf(o.getString("mode")) }.getOrDefault(Mode.ONCE),
            byWhen = runCatching { By.valueOf(o.getString("byWhen")) }.getOrDefault(By.WHENEVER),
            dueDate = str("dueDate"), dueTime = str("dueTime"),
            target = o.optInt("target", 1),
            period = runCatching { Per.valueOf(o.getString("period")) }.getOrDefault(Per.WEEK),
            doneCount = o.optInt("doneCount"), periodKey = str("periodKey"), doneDay = str("doneDay"),
            doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt"),
            leadMonths = o.optInt("leadMonths", 2), garmin = o.optBoolean("garmin"),
            times = o.optString("times"), places = o.optString("places"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}
