package com.vplusstudio.parkstop.engine

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
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
        private const val LOCATION_ACCURACY_THRESHOLD_METERS = 100f
        private const val ANCHOR_LOW_CONFIDENCE_THRESHOLD_METERS = 40f
        private const val ANCHOR_REFINEMENT_WINDOW_MS = 3 * 60_000L
        private const val ANCHOR_REFINEMENT_MAX_DISTANCE_METERS = 150f
        private const val ANCHOR_REFINEMENT_MIN_IMPROVEMENT_METERS = 15f

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
            val address = try { device?.address } catch (e: SecurityException) { null } ?: return
            val name = try { device?.name } catch (e: SecurityException) { null } ?: address
            val carAddress = EngineStore.getCarDeviceAddress(applicationContext)
            if (carAddress.isBlank() || !address.equals(carAddress, ignoreCase = true)) {
                // Diagnostic only, logged while a session is actively monitoring: if the car's
                // Bluetooth never seems to "connect", this tells us whether the ACL event fires
                // at all (for ANY device) and whether its address actually matches what's saved
                // in Settings -- instead of the receiver just silently doing nothing either way.
                val actionLabel = when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> "התחבר"
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> "התנתק"
                    else -> return
                }
                val mismatch = if (carAddress.isBlank()) "לא נבחר מכשיר רכב בהגדרות" else "מכשיר הרכב שהוגדר: $carAddress"
                logAndBroadcast("info", "אירוע בלוטות׳: \"$name\" ($address) $actionLabel — $mismatch")
                return
            }

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

    /** Captures the parking spot's location -- used as the Geofence detection anchor when that
     * signal is enabled, and always used to resolve + display a human-readable address, even
     * for Bluetooth-only sessions, so the user can find their way back in an unfamiliar area. */
    private fun captureAnchorIfNeeded() {
        if (EngineStore.hasAnchor(applicationContext)) return
        val client = fusedLocationClient ?: return

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            logAndBroadcast("warn", "אין הרשאת מיקום — כתובת החניה ואיתות Geofence לא יהיו זמינים לחניה זו")
            return
        }

        try {
            // High accuracy (not balanced) since this is a one-shot call at parking start,
            // not a recurring poll -- a coarse/cached fix here would seed a bad anchor.
            // maxUpdateAgeMillis(0) forces a fresh fix instead of accepting a stale cached
            // one, which is otherwise a common source of a wildly-off starting point.
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build()
            val cancellationSource = CancellationTokenSource()
            anchorCancellationSource = cancellationSource
            client.getCurrentLocation(request, cancellationSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        EngineStore.setAnchor(applicationContext, location.latitude, location.longitude, location.accuracy)
                        // Seed the baseline as "inside" -- you're standing at this exact spot
                        // when the anchor is captured, so the next location update must not
                        // read as an ENTER transition (that previously fired the alert
                        // immediately on starting a session, before you'd even left the car).
                        EngineStore.setWasInsideGeofence(applicationContext, true)
                        logAndBroadcast("success", "מיקום החניה נשמר (דיוק ±${location.accuracy.toInt()} מ׳)")
                        postStatus()
                        resolveAnchorAddress(location.latitude, location.longitude, location.accuracy)
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

    /** Reverse-geocodes the parking spot into a human-readable street address, so the user can
     * find their way back to the car in an unfamiliar area while the session is active. Best
     * effort: silently gives up if Geocoder is unavailable or resolves nothing (the raw
     * coordinates + the "navigate" button still work either way). */
    private fun resolveAnchorAddress(lat: Double, lng: Double, accuracyMeters: Float = 0f) {
        if (!Geocoder.isPresent()) return

        fun onAddressesResolved(addresses: List<Address>?) {
            // The session may have been stopped while this was resolving in the background.
            if (EngineStore.getState(applicationContext) == EngineStore.State.IDLE) return
            val address = addresses?.firstOrNull() ?: return

            val street = address.thoroughfare
            val houseNumber = address.subThoroughfare
            val city = address.locality
            val parts = mutableListOf<String>()
            if (!street.isNullOrBlank()) {
                parts.add(if (!houseNumber.isNullOrBlank()) "$street $houseNumber" else street)
            }
            if (!city.isNullOrBlank()) parts.add(city)
            val formatted = if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0)
            if (formatted.isNullOrBlank()) return

            // GPS fixes taken in covered/underground parking (common for Israeli parking
            // garages) can be off by dozens of meters, resolving to the wrong street address
            // entirely -- flag that clearly instead of presenting a low-confidence guess as fact.
            val finalAddress = if (accuracyMeters > ANCHOR_LOW_CONFIDENCE_THRESHOLD_METERS) {
                "$formatted (מיקום משוער, דיוק ±${accuracyMeters.toInt()} מ׳)"
            } else {
                formatted
            }

            EngineStore.setAnchorAddress(applicationContext, finalAddress)
            logAndBroadcast("info", "כתובת החניה זוהתה: $finalAddress")
            postStatus()
        }

        val geocoder = Geocoder(applicationContext, Locale("iw", "IL"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1) { addresses -> onAddressesResolved(addresses) }
        } else {
            Thread {
                @Suppress("DEPRECATION")
                val addresses = try {
                    geocoder.getFromLocation(lat, lng, 1)
                } catch (e: Exception) {
                    null
                }
                watchdogHandler.post { onAddressesResolved(addresses) }
            }.start()
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
        if (EngineStore.getCarDeviceAddress(applicationContext).isBlank()) {
            logAndBroadcast("warn", "לא נבחר מכשיר בלוטות׳ לרכב בהגדרות — איתות הבלוטות׳ לא יזהה חיבור/ניתוק")
        }
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

        if (location.accuracy > LOCATION_ACCURACY_THRESHOLD_METERS) {
            logAndBroadcast("info", "התעלמות מעדכון מיקום לא מדויק (±${location.accuracy.toInt()} מ׳)")
            return
        }

        maybeRefineAnchor(location)

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
        logAndBroadcast("info", "מיקום עודכן · מרחק מהחניה: ${distance.toInt()} מ׳ (דיוק ±${location.accuracy.toInt()} מ׳)")

        val radius = EngineStore.getGeofenceRadiusMeters(applicationContext)
        val isInside = distance <= radius
        val wasInside = EngineStore.wasInsideGeofence(applicationContext)
        val justEntered = isInside && !wasInside
        if (isInside != wasInside) {
            EngineStore.setWasInsideGeofence(applicationContext, isInside)
            if (isInside) {
                logAndBroadcast("info", "נכנסת לרדיוס ה-Geofence", EngineStore.EventType.GEOFENCE_ENTER)
            } else {
                logAndBroadcast("info", "יצאת מרדיוס ה-Geofence", EngineStore.EventType.GEOFENCE_EXIT)
            }
        }

        evaluateConditions(justEntered)
    }

    /** The one-shot anchor fix taken at parking-start is sometimes a poor/cached fix (e.g. GPS
     * hasn't locked on yet in a covered garage). If a meaningfully more accurate fix arrives
     * nearby soon after, silently upgrade the anchor + re-resolve its address instead of leaving
     * the session stuck with a wrong starting point for its whole duration. Guarded by distance
     * so this can't fire once the user has actually walked away from the car. */
    private fun maybeRefineAnchor(location: Location) {
        if (System.currentTimeMillis() - EngineStore.getStartedAt(applicationContext) > ANCHOR_REFINEMENT_WINDOW_MS) return

        val anchorAccuracy = EngineStore.getAnchorAccuracyMeters(applicationContext)
        if (anchorAccuracy <= 0f || location.accuracy > anchorAccuracy - ANCHOR_REFINEMENT_MIN_IMPROVEMENT_METERS) return

        val results = FloatArray(1)
        Location.distanceBetween(
            EngineStore.getAnchorLat(applicationContext),
            EngineStore.getAnchorLng(applicationContext),
            location.latitude,
            location.longitude,
            results
        )
        if (results[0] > ANCHOR_REFINEMENT_MAX_DISTANCE_METERS) return

        EngineStore.setAnchor(applicationContext, location.latitude, location.longitude, location.accuracy)
        logAndBroadcast("info", "מיקום החניה עודכן לדיוק טוב יותר (±${anchorAccuracy.toInt()} מ׳ ⟵ ±${location.accuracy.toInt()} מ׳)")
        postStatus()
        resolveAnchorAddress(location.latitude, location.longitude, location.accuracy)
    }

    /** [geofenceJustEntered] gates the geofence alert on an actual RETURN transition (you were
     * outside the radius, now you're back inside) rather than "currently inside" -- otherwise
     * the alert fired immediately on starting a session, since you're standing next to the car
     * (inside the radius) the moment you press start, before you've gone anywhere. */
    private fun evaluateConditions(geofenceJustEntered: Boolean = false) {
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

            if (geofenceJustEntered && withinWindow && cooldownOk) {
                EngineStore.setGeofenceAlertedNow(applicationContext)
                EngineStore.setLastSkipReason(applicationContext, "")
                triggerAlert(EngineStore.Confidence.LOW)
                return
            }

            val reason = when {
                !withinWindow -> "חלון הגיבוי של Geofence (${EngineStore.getGeofenceWindowMinutes(applicationContext)} דק') פג"
                !cooldownOk -> null // anti-spam cooldown right after an alert -- not worth logging
                distance < 0 -> "עדיין לא התקבל מיקום נוכחי"
                distance <= radius -> "בתוך רדיוס ה-Geofence — ממתין ליציאה וכניסה מחדש כדי לזהות חזרה"
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
