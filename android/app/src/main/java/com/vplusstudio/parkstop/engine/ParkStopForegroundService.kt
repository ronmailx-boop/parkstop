package com.vplusstudio.parkstop.engine

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Stays alive while a parking session is MONITORING or in ALERT state.
 * Listens for the car's Bluetooth device reconnecting (primary signal)
 * and, as a backup, polls location against the geofence saved when the
 * session started.
 */
class ParkStopForegroundService : Service() {

    companion object {
        private const val LOCATION_INTERVAL_MS = 30_000L
        private const val WATCHDOG_INTERVAL_MS = 60_000L
        private const val GEOFENCE_ALERT_COOLDOWN_MS = 5 * 60_000L
        private const val HEARTBEAT_INTERVAL_MS = 30 * 60_000L

        /** Whether the service object is currently alive. Read by the Status screen -- a
         * state of MONITORING with this false means Android killed the service silently. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var anchorCancellationSource: CancellationTokenSource? = null
    private var receiverRegistered = false
    private val watchdogHandler = Handler(Looper.getMainLooper())

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            maybeLogHeartbeat()
            evaluateConditions()
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun maybeLogHeartbeat() {
        val now = System.currentTimeMillis()
        if (now - EngineStore.getLastHeartbeatAt(applicationContext) < HEARTBEAT_INTERVAL_MS) return
        EngineStore.setLastHeartbeatNow(applicationContext)
        logAndBroadcast("info", "השירות עדיין רץ", EngineStore.EventType.HEARTBEAT)
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val address = device?.address ?: return
            val carAddress = EngineStore.getCarDeviceAddress(applicationContext)
            if (carAddress.isBlank() || !address.equals(carAddress, ignoreCase = true)) return

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    EngineStore.setBluetoothConnected(applicationContext, true)
                    logAndBroadcast("success", "בלוטות׳ הרכב התחבר", EngineStore.EventType.BT_CONNECTED)
                    evaluateConditions()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    EngineStore.setBluetoothConnected(applicationContext, false)
                    logAndBroadcast("info", "בלוטות׳ הרכב התנתק", EngineStore.EventType.BT_DISCONNECTED)
                    postStatus()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NotificationHelper.ensureChannels(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appName = EngineStore.getParkingAppName(applicationContext).ifBlank { "החניה" }
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, NotificationHelper.buildServiceNotification(applicationContext, appName))

        registerBluetoothReceiver()
        captureAnchorIfNeeded()
        startLocationUpdatesIfNeeded()
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)

        if (intent?.action == Actions.ACTION_SIMULATE_BT_CONNECT) {
            logAndBroadcast("success", "בלוטות׳ הרכב התחבר (הדמיית בדיקה)", EngineStore.EventType.BT_CONNECTED)
            evaluateConditions()
        } else {
            logAndBroadcast("info", "מעקב אחרי חזרה לרכב הופעל", EngineStore.EventType.SERVICE_STARTED)
        }

        return START_STICKY
    }

    private fun captureAnchorIfNeeded() {
        if (!EngineStore.useGeofence(applicationContext) || EngineStore.hasAnchor(applicationContext)) return
        val client = fusedLocationClient ?: return

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            logAndBroadcast("warn", "אין הרשאת מיקום — איתות Geofence לא יהיה זמין לחניה זו")
            return
        }

        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
            val cancellationSource = CancellationTokenSource()
            anchorCancellationSource = cancellationSource
            client.getCurrentLocation(request, cancellationSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        EngineStore.setAnchor(applicationContext, location.latitude, location.longitude)
                        logAndBroadcast("success", "מיקום החניה נשמר")
                        postStatus()
                    } else {
                        logAndBroadcast("warn", "לא ניתן היה לאתר מיקום נוכחי לשמירת החניה")
                    }
                }
                .addOnFailureListener {
                    logAndBroadcast("warn", "שגיאה באיתור מיקום החניה")
                }
        } catch (e: SecurityException) {
            logAndBroadcast("error", "אין הרשאת מיקום — לא ניתן לשמור את מיקום החניה")
        }
    }

    override fun onDestroy() {
        isRunning = false
        watchdogHandler.removeCallbacks(watchdogRunnable)
        unregisterBluetoothReceiver()
        stopLocationUpdates()
        anchorCancellationSource?.cancel()
        if (EngineStore.getState(applicationContext) != EngineStore.State.IDLE) {
            logAndBroadcast("warn", "השירות נעצר", EngineStore.EventType.SERVICE_KILLED)
        }
        super.onDestroy()
    }

    /** Called when the user swipes the app away from Recents -- the scenario most likely
     * to actually kill a foreground service, and the one the field-testing protocol asks
     * to be able to see in the log. START_STICKY still restarts the service afterwards. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (EngineStore.getState(applicationContext) != EngineStore.State.IDLE) {
            logAndBroadcast("warn", "האפליקציה הוסרה מהמשימות האחרונות", EngineStore.EventType.SERVICE_KILLED)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered || !EngineStore.useBluetooth(applicationContext)) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterBluetoothReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // already unregistered
        }
        receiverRegistered = false
    }

    private fun startLocationUpdatesIfNeeded() {
        if (!EngineStore.useGeofence(applicationContext)) return
        val client = fusedLocationClient ?: return

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(15_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleLocationUpdate(location)
            }
        }
        locationCallback = callback

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            logAndBroadcast("error", "אין הרשאת מיקום — לא ניתן לעקוב אחרי Geofence")
        }
    }

    private fun stopLocationUpdates() {
        val callback = locationCallback ?: return
        fusedLocationClient?.removeLocationUpdates(callback)
        locationCallback = null
    }

    private fun handleLocationUpdate(location: Location) {
        if (!EngineStore.hasAnchor(applicationContext)) return

        val results = FloatArray(1)
        Location.distanceBetween(
            EngineStore.getAnchorLat(applicationContext),
            EngineStore.getAnchorLng(applicationContext),
            location.latitude,
            location.longitude,
            results
        )
        val distance = results[0]
        EngineStore.updateLocation(applicationContext, location.latitude, location.longitude, distance)
        logAndBroadcast("info", "מיקום עודכן · מרחק מהחניה: ${distance.toInt()} מ׳")

        val radius = EngineStore.getGeofenceRadiusMeters(applicationContext)
        val isInside = distance <= radius
        val wasInside = EngineStore.wasInsideGeofence(applicationContext)
        if (isInside != wasInside) {
            EngineStore.setWasInsideGeofence(applicationContext, isInside)
            if (isInside) {
                logAndBroadcast("info", "נכנסת לרדיוס ה-Geofence", EngineStore.EventType.GEOFENCE_ENTER)
            } else {
                logAndBroadcast("info", "יצאת מרדיוס ה-Geofence", EngineStore.EventType.GEOFENCE_EXIT)
            }
        }

        evaluateConditions()
    }

    private fun evaluateConditions() {
        val state = EngineStore.getState(applicationContext)
        if (state != EngineStore.State.MONITORING) {
            logSkipIfReasonChanged("אין חניה פעילה במעקב (מצב: $state)")
            return
        }

        val now = System.currentTimeMillis()
        if (now < EngineStore.getSnoozeUntil(applicationContext)) {
            logSkipIfReasonChanged("התזכורת נדחתה (Snooze)")
            return
        }

        if (EngineStore.useBluetooth(applicationContext) && EngineStore.isBluetoothConnected(applicationContext)) {
            EngineStore.setLastSkipReason(applicationContext, "")
            triggerAlert(EngineStore.Confidence.HIGH)
            return
        }

        if (EngineStore.useGeofence(applicationContext) && EngineStore.hasAnchor(applicationContext)) {
            val distance = EngineStore.getLastDistanceMeters(applicationContext) ?: -1f
            val radius = EngineStore.getGeofenceRadiusMeters(applicationContext)
            val windowMs = EngineStore.getGeofenceWindowMinutes(applicationContext) * 60_000L
            val startedAt = EngineStore.getStartedAt(applicationContext)
            val withinWindow = (now - startedAt) <= windowMs
            val cooldownOk = (now - EngineStore.getGeofenceAlertedAt(applicationContext)) >= GEOFENCE_ALERT_COOLDOWN_MS

            if (distance >= 0 && distance <= radius && withinWindow && cooldownOk) {
                EngineStore.setGeofenceAlertedNow(applicationContext)
                EngineStore.setLastSkipReason(applicationContext, "")
                triggerAlert(EngineStore.Confidence.LOW)
                return
            }

            val reason = when {
                !withinWindow -> "חלון הגיבוי של Geofence (${EngineStore.getGeofenceWindowMinutes(applicationContext)} דק') פג"
                !cooldownOk -> null // anti-spam cooldown right after an alert -- not worth logging
                distance < 0 -> "עדיין לא התקבל מיקום נוכחי"
                else -> "מחוץ לרדיוס ה-Geofence (${distance.toInt()} מ׳ מתוך $radius מ׳)"
            }
            if (reason != null) logSkipIfReasonChanged(reason)
        } else if (!EngineStore.useBluetooth(applicationContext)) {
            logSkipIfReasonChanged("לא הוגדר אף איתות זיהוי (בלוטות׳ ו-Geofence כבויים)")
        } else if (EngineStore.useGeofence(applicationContext)) {
            logSkipIfReasonChanged("אין עדיין מיקום עוגן לחניה (Geofence)")
        }
    }

    /** ALERT_SKIPPED is logged once per DISTINCT reason, not every watchdog tick -- otherwise
     * a multi-hour parking session floods the log with an identical line every 60 seconds. */
    private fun logSkipIfReasonChanged(reason: String) {
        if (EngineStore.getLastSkipReason(applicationContext) == reason) return
        EngineStore.setLastSkipReason(applicationContext, reason)
        logAndBroadcast("info", "התראה לא נשלחה: $reason", EngineStore.EventType.ALERT_SKIPPED)
    }

    private fun triggerAlert(confidence: String) {
        EngineStore.setState(applicationContext, EngineStore.State.ALERT)
        EngineStore.setAlertConfidence(applicationContext, confidence)
        val label = if (confidence == EngineStore.Confidence.HIGH) "ודאות גבוהה (בלוטות׳)" else "ודאות נמוכה (מיקום)"
        logAndBroadcast("warn", "🔔 זוהתה חזרה לרכב — $label", EngineStore.EventType.ALERT_SENT)

        val notification = NotificationHelper.buildAlertNotification(
            applicationContext,
            confidence,
            EngineStore.getParkingAppName(applicationContext).ifBlank { "אפליקציית החניה" },
            EngineStore.getParkingAppPackage(applicationContext),
            EngineStore.getParkingAppDeepLink(applicationContext)
        )
        NotificationHelper.notify(applicationContext, NotificationHelper.ALERT_NOTIFICATION_ID, notification)
        postStatus()
    }

    private fun logAndBroadcast(level: String, message: String, type: String = EngineStore.EventType.INFO) {
        val entry = EngineStore.appendLog(applicationContext, level, message, type)
        EngineEvents.postLogAdded(entry)
    }

    private fun postStatus() {
        EngineEvents.postStatusChanged(EngineStore.buildStatus(applicationContext))
    }
}
