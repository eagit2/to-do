package app.todo.device

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.todo.data.Place

/** A calendar on the phone, for the "count as busy" switches. */
data class PhoneCalendar(val id: Long, val name: String)

/** Readings straight from the phone. Each one fails soft: no permission means "unknown", never a crash. */
object Device {
    fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The Wi-Fi network name, or null when off Wi-Fi or Android won't tell (it needs location permission). */
    @Suppress("DEPRECATION")
    fun wifiName(context: Context): String? {
        if (!has(context, Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wm.connectionInfo?.ssid }.getOrNull() ?: return null
        val clean = ssid.trim('"')
        return clean.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

    /**
     * Where the phone is, by Wi-Fi: a saved place on that network, "out" when no saved network is in reach,
     * or null when we can't tell (no permission, or no place has a network saved yet).
     */
    fun currentPlace(context: Context, places: List<Place>): String? {
        val saved = places.filter { it.isSet && it.wifi != null }
        if (saved.isEmpty() || !has(context, Manifest.permission.ACCESS_FINE_LOCATION)) return null
        val wifi = wifiName(context)
        return saved.firstOrNull { it.wifi == wifi }?.id ?: "out"
    }

    fun notificationsAllowed(context: Context) =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    fun dndOn(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun charging(context: Context): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun batteryUnrestricted(context: Context) =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun calendars(context: Context): List<PhoneCalendar> {
        if (!has(context, Manifest.permission.READ_CALENDAR)) return emptyList()
        val out = mutableListOf<PhoneCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { c ->
                while (c.moveToNext()) out += PhoneCalendar(c.getLong(0), c.getString(1) ?: "Calendar")
            }
        }
        return out
    }
}
