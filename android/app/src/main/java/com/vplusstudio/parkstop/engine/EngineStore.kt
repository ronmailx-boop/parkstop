package com.vplusstudio.parkstop.engine

import android.content.Context
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject

/**
 * Single source of truth for the parking-monitoring state machine.
 *
 * Lives in its own SharedPreferences file (independent from the
 * @capacitor/preferences store used by the web UI) so the foreground
 * service and its BroadcastReceivers can read/write it even when the
 * WebView / JS bridge is not alive.
 */
object EngineStore {
    private const val PREFS_NAME = "parkstop_engine"
    private const val MAX_LOG_ENTRIES = 500

    /** Structured event types, alongside the free-text Hebrew `message` shown in the UI. */
    object EventType {
        const val PARKING_STARTED = "PARKING_STARTED"
        const val PARKING_STOPPED = "PARKING_STOPPED"
        const val BT_CONNECTED = "BT_CONNECTED"
        const val BT_DISCONNECTED = "BT_DISCONNECTED"
        const val GEOFENCE_ENTER = "GEOFENCE_ENTER"
        const val GEOFENCE_EXIT = "GEOFENCE_EXIT"
        const val ALERT_SENT = "ALERT_SENT"
        const val ALERT_SKIPPED = "ALERT_SKIPPED"
        const val SERVICE_STARTED = "SERVICE_STARTED"
        const val SERVICE_KILLED = "SERVICE_KILLED"
        const val BOOT_COMPLETED = "BOOT_COMPLETED"
        const val HEARTBEAT = "HEARTBEAT"
        const val TEST_ALERT = "TEST_ALERT"
        const val INFO = ""
    }

    object State {
        const val IDLE = "IDLE"
        const val MONITORING = "MONITORING"
        const val ALERT = "ALERT"
    }

    object Confidence {
        const val HIGH = "HIGH"
        const val LOW = "LOW"
    }

    private object Keys {
        const val STATE = "state"
        const val USE_BLUETOOTH = "useBluetooth"
        const val USE_GEOFENCE = "useGeofence"
        const val CAR_DEVICE_ADDRESS = "carDeviceAddress"
        const val CAR_DEVICE_NAME = "carDeviceName"
        const val RADIUS_METERS = "geofenceRadiusMeters"
        const val WINDOW_MINUTES = "geofenceBackupWindowMinutes"
        const val PARKING_APP_ID = "parkingAppId"
        const val PARKING_APP_NAME = "parkingAppName"
        const val PARKING_APP_PACKAGE = "parkingAppPackage"
        const val PARKING_APP_DEEPLINK = "parkingAppDeepLink"
        const val STARTED_AT = "startedAt"
        const val ANCHOR_LAT = "anchorLat"
        const val ANCHOR_LNG = "anchorLng"
        const val HAS_ANCHOR = "hasAnchor"
        const val ANCHOR_ADDRESS = "anchorAddress"
        const val BLUETOOTH_CONNECTED = "bluetoothConnected"
        const val LAST_LAT = "lastLat"
        const val LAST_LNG = "lastLng"
        const val HAS_LAST_LOCATION = "hasLastLocation"
        const val LAST_DISTANCE_METERS = "lastDistanceMeters"
        const val LAST_LOCATION_AT = "lastLocationAt"
        const val LAST_UPDATED_AT = "lastUpdatedAt"
        const val GEOFENCE_ALERTED_AT = "geofenceAlertedAt"
        const val SNOOZE_UNTIL = "snoozeUntil"
        const val ALERT_CONFIDENCE = "alertConfidence"
        const val LOGS = "logs"
        const val LAST_HEARTBEAT_AT = "lastHeartbeatAt"
        const val LAST_SKIP_REASON = "lastSkipReason"
        const val WAS_INSIDE_GEOFENCE = "wasInsideGeofence"
    }

    data class StartParams(
        val useBluetooth: Boolean,
        val useGeofence: Boolean,
        val carDeviceAddress: String,
        val carDeviceName: String,
        val geofenceRadiusMeters: Int,
        val geofenceBackupWindowMinutes: Int,
        val parkingAppId: String,
        val parkingAppName: String,
        val parkingAppPackage: String,
        val parkingAppDeepLink: String,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(context: Context): String =
        prefs(context).getString(Keys.STATE, State.IDLE) ?: State.IDLE

    fun startSession(context: Context, params: StartParams) {
        prefs(context).edit().apply {
            putString(Keys.STATE, State.MONITORING)
            putBoolean(Keys.USE_BLUETOOTH, params.useBluetooth)
            putBoolean(Keys.USE_GEOFENCE, params.useGeofence)
            putString(Keys.CAR_DEVICE_ADDRESS, params.carDeviceAddress)
            putString(Keys.CAR_DEVICE_NAME, params.carDeviceName)
            putInt(Keys.RADIUS_METERS, params.geofenceRadiusMeters)
            putInt(Keys.WINDOW_MINUTES, params.geofenceBackupWindowMinutes)
            putString(Keys.PARKING_APP_ID, params.parkingAppId)
            putString(Keys.PARKING_APP_NAME, params.parkingAppName)
            putString(Keys.PARKING_APP_PACKAGE, params.parkingAppPackage)
            putString(Keys.PARKING_APP_DEEPLINK, params.parkingAppDeepLink)
            putLong(Keys.STARTED_AT, System.currentTimeMillis())
            putBoolean(Keys.HAS_ANCHOR, false)
            putBoolean(Keys.BLUETOOTH_CONNECTED, false)
            putBoolean(Keys.HAS_LAST_LOCATION, false)
            remove(Keys.LAST_DISTANCE_METERS)
            remove(Keys.GEOFENCE_ALERTED_AT)
            remove(Keys.SNOOZE_UNTIL)
            remove(Keys.ALERT_CONFIDENCE)
            remove(Keys.LAST_SKIP_REASON)
            remove(Keys.WAS_INSIDE_GEOFENCE)
            remove(Keys.LAST_HEARTBEAT_AT)
            remove(Keys.ANCHOR_ADDRESS)
        }.apply()
    }

    fun setAnchor(context: Context, lat: Double, lng: Double) {
        prefs(context).edit()
            .putBoolean(Keys.HAS_ANCHOR, true)
            .putFloat(Keys.ANCHOR_LAT, lat.toFloat())
            .putFloat(Keys.ANCHOR_LNG, lng.toFloat())
            .apply()
    }

    fun hasAnchor(context: Context): Boolean = prefs(context).getBoolean(Keys.HAS_ANCHOR, false)
    fun getAnchorLat(context: Context): Double = prefs(context).getFloat(Keys.ANCHOR_LAT, 0f).toDouble()
    fun getAnchorLng(context: Context): Double = prefs(context).getFloat(Keys.ANCHOR_LNG, 0f).toDouble()

    fun setAnchorAddress(context: Context, address: String) {
        prefs(context).edit().putString(Keys.ANCHOR_ADDRESS, address).apply()
    }

    fun getAnchorAddress(context: Context): String = prefs(context).getString(Keys.ANCHOR_ADDRESS, "") ?: ""

    fun reset(context: Context) {
        prefs(context).edit().apply {
            putString(Keys.STATE, State.IDLE)
            putBoolean(Keys.BLUETOOTH_CONNECTED, false)
            putBoolean(Keys.HAS_ANCHOR, false)
            putBoolean(Keys.HAS_LAST_LOCATION, false)
            remove(Keys.LAST_DISTANCE_METERS)
            remove(Keys.GEOFENCE_ALERTED_AT)
            remove(Keys.SNOOZE_UNTIL)
            remove(Keys.ALERT_CONFIDENCE)
            remove(Keys.LAST_SKIP_REASON)
            remove(Keys.WAS_INSIDE_GEOFENCE)
            remove(Keys.LAST_HEARTBEAT_AT)
            remove(Keys.ANCHOR_ADDRESS)
        }.apply()
    }

    fun setState(context: Context, state: String) {
        prefs(context).edit().putString(Keys.STATE, state).apply()
    }

    fun setAlertConfidence(context: Context, confidence: String) {
        prefs(context).edit().putString(Keys.ALERT_CONFIDENCE, confidence).apply()
    }

    fun setBluetoothConnected(context: Context, connected: Boolean) {
        prefs(context).edit().putBoolean(Keys.BLUETOOTH_CONNECTED, connected).apply()
    }

    fun isBluetoothConnected(context: Context): Boolean =
        prefs(context).getBoolean(Keys.BLUETOOTH_CONNECTED, false)

    fun updateLocation(context: Context, lat: Double, lng: Double, distanceMeters: Float) {
        val now = System.currentTimeMillis()
        prefs(context).edit().apply {
            putBoolean(Keys.HAS_LAST_LOCATION, true)
            putFloat(Keys.LAST_LAT, lat.toFloat())
            putFloat(Keys.LAST_LNG, lng.toFloat())
            putFloat(Keys.LAST_DISTANCE_METERS, distanceMeters)
            putLong(Keys.LAST_LOCATION_AT, now)
            putLong(Keys.LAST_UPDATED_AT, now)
        }.apply()
    }

    fun getGeofenceRadiusMeters(context: Context): Int = prefs(context).getInt(Keys.RADIUS_METERS, 50)
    fun getGeofenceWindowMinutes(context: Context): Int = prefs(context).getInt(Keys.WINDOW_MINUTES, 10)
    fun getStartedAt(context: Context): Long = prefs(context).getLong(Keys.STARTED_AT, 0L)
    fun useBluetooth(context: Context): Boolean = prefs(context).getBoolean(Keys.USE_BLUETOOTH, true)
    fun useGeofence(context: Context): Boolean = prefs(context).getBoolean(Keys.USE_GEOFENCE, true)
    fun getCarDeviceAddress(context: Context): String = prefs(context).getString(Keys.CAR_DEVICE_ADDRESS, "") ?: ""
    fun getCarDeviceName(context: Context): String = prefs(context).getString(Keys.CAR_DEVICE_NAME, "") ?: ""
    fun getParkingAppId(context: Context): String = prefs(context).getString(Keys.PARKING_APP_ID, "") ?: ""
    fun getParkingAppName(context: Context): String = prefs(context).getString(Keys.PARKING_APP_NAME, "") ?: ""
    fun getParkingAppPackage(context: Context): String = prefs(context).getString(Keys.PARKING_APP_PACKAGE, "") ?: ""
    fun getParkingAppDeepLink(context: Context): String = prefs(context).getString(Keys.PARKING_APP_DEEPLINK, "") ?: ""

    fun getLastDistanceMeters(context: Context): Float? {
        val p = prefs(context)
        if (!p.getBoolean(Keys.HAS_LAST_LOCATION, false)) return null
        return p.getFloat(Keys.LAST_DISTANCE_METERS, -1f)
    }

    fun setGeofenceAlertedNow(context: Context) {
        prefs(context).edit().putLong(Keys.GEOFENCE_ALERTED_AT, System.currentTimeMillis()).apply()
    }

    fun getGeofenceAlertedAt(context: Context): Long = prefs(context).getLong(Keys.GEOFENCE_ALERTED_AT, 0L)

    fun setSnoozeUntil(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(Keys.SNOOZE_UNTIL, timestamp).apply()
    }

    fun getSnoozeUntil(context: Context): Long = prefs(context).getLong(Keys.SNOOZE_UNTIL, 0L)

    fun getLastHeartbeatAt(context: Context): Long = prefs(context).getLong(Keys.LAST_HEARTBEAT_AT, 0L)

    fun setLastHeartbeatNow(context: Context) {
        prefs(context).edit().putLong(Keys.LAST_HEARTBEAT_AT, System.currentTimeMillis()).apply()
    }

    fun getLastSkipReason(context: Context): String = prefs(context).getString(Keys.LAST_SKIP_REASON, "") ?: ""

    fun setLastSkipReason(context: Context, reason: String) {
        prefs(context).edit().putString(Keys.LAST_SKIP_REASON, reason).apply()
    }

    fun wasInsideGeofence(context: Context): Boolean = prefs(context).getBoolean(Keys.WAS_INSIDE_GEOFENCE, false)

    fun setWasInsideGeofence(context: Context, inside: Boolean) {
        prefs(context).edit().putBoolean(Keys.WAS_INSIDE_GEOFENCE, inside).apply()
    }

    fun buildStatus(context: Context): JSObject {
        val p = prefs(context)
        val status = JSObject()
        status.put("state", p.getString(Keys.STATE, State.IDLE))
        status.put("useBluetooth", p.getBoolean(Keys.USE_BLUETOOTH, true))
        status.put("useGeofence", p.getBoolean(Keys.USE_GEOFENCE, true))
        status.put("bluetoothConnected", p.getBoolean(Keys.BLUETOOTH_CONNECTED, false))
        status.put("parkingAppId", p.getString(Keys.PARKING_APP_ID, ""))
        status.put("parkingAppName", p.getString(Keys.PARKING_APP_NAME, ""))
        status.put("parkingAppPackage", p.getString(Keys.PARKING_APP_PACKAGE, ""))
        status.put("parkingAppDeepLink", p.getString(Keys.PARKING_APP_DEEPLINK, ""))
        status.put("startedAt", p.getLong(Keys.STARTED_AT, 0L))
        status.put("alertConfidence", p.getString(Keys.ALERT_CONFIDENCE, ""))
        status.put("snoozeUntil", p.getLong(Keys.SNOOZE_UNTIL, 0L))
        status.put("carDeviceAddress", p.getString(Keys.CAR_DEVICE_ADDRESS, ""))
        status.put("carDeviceName", p.getString(Keys.CAR_DEVICE_NAME, ""))
        status.put("hasAnchor", p.getBoolean(Keys.HAS_ANCHOR, false))
        status.put("anchorAddress", p.getString(Keys.ANCHOR_ADDRESS, ""))
        if (p.getBoolean(Keys.HAS_ANCHOR, false)) {
            status.put("anchorLat", p.getFloat(Keys.ANCHOR_LAT, 0f).toDouble())
            status.put("anchorLng", p.getFloat(Keys.ANCHOR_LNG, 0f).toDouble())
        } else {
            status.put("anchorLat", JSObject.NULL)
            status.put("anchorLng", JSObject.NULL)
        }

        if (p.getBoolean(Keys.HAS_LAST_LOCATION, false)) {
            status.put("lastLat", p.getFloat(Keys.LAST_LAT, 0f).toDouble())
            status.put("lastLng", p.getFloat(Keys.LAST_LNG, 0f).toDouble())
            status.put("lastDistanceMeters", p.getFloat(Keys.LAST_DISTANCE_METERS, -1f).toDouble())
            status.put("lastLocationAt", p.getLong(Keys.LAST_LOCATION_AT, 0L))
        } else {
            status.put("lastLat", JSObject.NULL)
            status.put("lastLng", JSObject.NULL)
            status.put("lastDistanceMeters", JSObject.NULL)
            status.put("lastLocationAt", JSObject.NULL)
        }
        status.put("lastUpdatedAt", p.getLong(Keys.LAST_UPDATED_AT, 0L))
        return status
    }

    fun appendLog(context: Context, level: String, message: String, type: String = EventType.INFO): JSObject {
        val p = prefs(context)
        val raw = p.getString(Keys.LOGS, "[]") ?: "[]"
        val array = try { JSArray(raw) } catch (e: Exception) { JSArray() }

        val entry = JSObject()
        entry.put("timestamp", System.currentTimeMillis())
        entry.put("level", level)
        entry.put("message", message)
        entry.put("type", type)

        val trimmed = JSArray()
        val start = if (array.length() >= MAX_LOG_ENTRIES) array.length() - MAX_LOG_ENTRIES + 1 else 0
        for (i in start until array.length()) {
            trimmed.put(array.get(i))
        }
        trimmed.put(entry)

        p.edit().putString(Keys.LOGS, trimmed.toString()).apply()
        return entry
    }

    fun getLogs(context: Context): JSArray {
        val raw = prefs(context).getString(Keys.LOGS, "[]") ?: "[]"
        return try { JSArray(raw) } catch (e: Exception) { JSArray() }
    }

    fun clearLogs(context: Context) {
        prefs(context).edit().putString(Keys.LOGS, "[]").apply()
    }

    /** Plain-text dump of the log, newest last, for sharing out of the app (e.g. for analysis). */
    fun formatLogsAsText(context: Context): String {
        val entries = getLogs(context)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val lines = StringBuilder("ParkStop – יומן אירועים\n\n")
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val timestamp = entry.optLong("timestamp", 0L)
            val time = if (timestamp > 0) sdf.format(java.util.Date(timestamp)) else "—"
            val type = entry.optString("type", "").ifBlank { "INFO" }
            val level = entry.optString("level", "info")
            val message = entry.optString("message", "")
            lines.append("[$time] $type ($level): $message\n")
        }
        return lines.toString()
    }
}
