package com.vplusstudio.parkstop.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Shared start/stop/snooze logic used by the notification receiver, the Quick Settings tile, and boot restore. */
object EngineActions {

    fun stopParking(context: Context) {
        EngineStore.reset(context)
        NotificationHelper.cancel(context, NotificationHelper.ALERT_NOTIFICATION_ID)
        context.stopService(Intent(context, ParkStopForegroundService::class.java))
        logAndBroadcast(context, "success", "החניה נעצרה", EngineStore.EventType.PARKING_STOPPED)
        broadcastStatus(context)
    }

    fun snooze(context: Context, minutes: Int) {
        EngineStore.setState(context, EngineStore.State.MONITORING)
        EngineStore.setSnoozeUntil(context, System.currentTimeMillis() + minutes * 60_000L)
        NotificationHelper.cancel(context, NotificationHelper.ALERT_NOTIFICATION_ID)
        logAndBroadcast(context, "info", "התזכורת נדחתה ב-$minutes דקות")
        broadcastStatus(context)
    }

    /** Starts a new session reusing the most recently used parking app + settings. Returns false if none saved yet. */
    fun startWithLastUsedApp(context: Context): Boolean {
        if (EngineStore.getParkingAppId(context).isBlank()) return false

        val params = EngineStore.StartParams(
            useBluetooth = EngineStore.useBluetooth(context),
            useGeofence = EngineStore.useGeofence(context),
            carDeviceAddress = EngineStore.getCarDeviceAddress(context),
            carDeviceName = EngineStore.getCarDeviceName(context),
            geofenceRadiusMeters = EngineStore.getGeofenceRadiusMeters(context),
            geofenceBackupWindowMinutes = EngineStore.getGeofenceWindowMinutes(context),
            parkingAppId = EngineStore.getParkingAppId(context),
            parkingAppName = EngineStore.getParkingAppName(context),
            parkingAppPackage = EngineStore.getParkingAppPackage(context),
            parkingAppDeepLink = EngineStore.getParkingAppDeepLink(context),
        )
        EngineStore.startSession(context, params)
        ContextCompat.startForegroundService(context, Intent(context, ParkStopForegroundService::class.java))
        logAndBroadcast(context, "success", "חניה הופעלה (${params.parkingAppName})", EngineStore.EventType.PARKING_STARTED)
        broadcastStatus(context)
        return true
    }

    /** Restarts the service process WITHOUT touching the saved session (anchor, started-at,
     * app info) -- used by the Status screen's "restart" fix when the state is still
     * MONITORING/ALERT but Android killed the actual Service object. */
    fun restartServiceIfDead(context: Context): Boolean {
        val state = EngineStore.getState(context)
        if (state != EngineStore.State.MONITORING && state != EngineStore.State.ALERT) return false
        if (ParkStopForegroundService.isRunning) return true

        ContextCompat.startForegroundService(context, Intent(context, ParkStopForegroundService::class.java))
        logAndBroadcast(context, "info", "השירות הופעל מחדש ידנית", EngineStore.EventType.SERVICE_STARTED)
        broadcastStatus(context)
        return true
    }

    private fun logAndBroadcast(context: Context, level: String, message: String, type: String = EngineStore.EventType.INFO) {
        val entry = EngineStore.appendLog(context, level, message, type)
        EngineEvents.postLogAdded(entry)
    }

    private fun broadcastStatus(context: Context) {
        EngineEvents.postStatusChanged(EngineStore.buildStatus(context))
    }
}
