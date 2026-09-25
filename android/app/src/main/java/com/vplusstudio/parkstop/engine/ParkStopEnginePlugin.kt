package com.vplusstudio.parkstop.engine

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

@CapacitorPlugin(
    name = "ParkStopEngine",
    permissions = [
        Permission(
            alias = "core",
            strings = [
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.POST_NOTIFICATIONS,
            ]
        ),
        Permission(
            alias = "backgroundLocation",
            strings = [Manifest.permission.ACCESS_BACKGROUND_LOCATION]
        ),
    ]
)
class ParkStopEnginePlugin : Plugin(), EngineEvents.Listener {

    override fun load() {
        super.load()
        EngineEvents.register(this)
    }

    override fun handleOnDestroy() {
        EngineEvents.unregister(this)
        super.handleOnDestroy()
    }

    override fun onStatusChanged(status: JSObject) {
        notifyListeners("statusChanged", status)
    }

    override fun onLogAdded(entry: JSObject) {
        notifyListeners("logAdded", entry)
    }

    @PluginMethod
    fun getPairedDevices(call: PluginCall) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val result = JSObject()
        val devices = JSArray()

        if (adapter != null && hasBluetoothConnectPermission()) {
            try {
                adapter.bondedDevices?.forEach { device ->
                    val entry = JSObject()
                    entry.put("name", device.name ?: device.address)
                    entry.put("address", device.address)
                    devices.put(entry)
                }
            } catch (e: SecurityException) {
                // permission revoked between check and call; return what we have (empty)
            }
        }

        result.put("devices", devices)
        call.resolve(result)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @PluginMethod
    fun startMonitoring(call: PluginCall) {
        val params = EngineStore.StartParams(
            useBluetooth = call.getBoolean("useBluetooth", true) ?: true,
            useGeofence = call.getBoolean("useGeofence", true) ?: true,
            carDeviceAddress = call.getString("carDeviceAddress", "") ?: "",
            carDeviceName = call.getString("carDeviceName", "") ?: "",
            geofenceRadiusMeters = call.getInt("geofenceRadiusMeters", 50) ?: 50,
            geofenceBackupWindowMinutes = call.getInt("geofenceBackupWindowMinutes", 10) ?: 10,
            parkingAppId = call.getString("parkingAppId", "") ?: "",
            parkingAppName = call.getString("parkingAppName", "אפליקציית חניה") ?: "אפליקציית חניה",
            parkingAppPackage = call.getString("parkingAppPackage", "") ?: "",
            parkingAppDeepLink = call.getString("parkingAppDeepLink", "") ?: "",
        )

        EngineStore.startSession(context, params)
        ContextCompat.startForegroundService(context, Intent(context, ParkStopForegroundService::class.java))
        val entry = EngineStore.appendLog(context, "success", "חניה הופעלה: ${params.parkingAppName}", EngineStore.EventType.PARKING_STARTED)
        EngineEvents.postLogAdded(entry)
        EngineEvents.postStatusChanged(EngineStore.buildStatus(context))

        call.resolve(EngineStore.buildStatus(context))
    }

    @PluginMethod
    fun stopMonitoring(call: PluginCall) {
        EngineActions.stopParking(context)
        call.resolve(EngineStore.buildStatus(context))
    }

    @PluginMethod
    fun snoozeAlert(call: PluginCall) {
        val minutes = call.getInt("minutes", 5) ?: 5
        EngineActions.snooze(context, minutes)
        call.resolve(EngineStore.buildStatus(context))
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        call.resolve(EngineStore.buildStatus(context))
    }

    @PluginMethod
    fun getLogs(call: PluginCall) {
        val result = JSObject()
        result.put("entries", EngineStore.getLogs(context))
        call.resolve(result)
    }

    @PluginMethod
    fun clearLogs(call: PluginCall) {
        EngineStore.clearLogs(context)
        call.resolve()
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        call.resolve(buildPermissionsStatus())
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        requestPermissionForAlias("core", call, "coreCallback")
    }

    @PermissionCallback
    fun coreCallback(call: PluginCall) {
        requestPermissionForAlias("backgroundLocation", call, "backgroundLocationCallback")
    }

    @PermissionCallback
    fun backgroundLocationCallback(call: PluginCall) {
        call.resolve(buildPermissionsStatus())
    }

    private fun buildPermissionsStatus(): JSObject {
        val status = JSObject()
        status.put("location", permissionStateFor("core"))
        status.put("backgroundLocation", permissionStateFor("backgroundLocation"))
        status.put("bluetooth", permissionStateFor("core"))
        status.put("notifications", permissionStateFor("core"))
        return status
    }

    private fun permissionStateFor(alias: String): String {
        val state = getPermissionState(alias)
        return if (state == PermissionState.GRANTED) "granted" else "denied"
    }

    @PluginMethod
    fun isIgnoringBatteryOptimizations(call: PluginCall) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = JSObject()
        result.put("ignoring", pm.isIgnoringBatteryOptimizations(context.packageName))
        call.resolve(result)
    }

    @PluginMethod
    fun openBatteryOptimizationSettings(call: PluginCall) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e2: Exception) {
                call.reject("לא ניתן לפתוח את הגדרות הסוללה")
                return
            }
        }
        call.resolve()
    }

    @PluginMethod
    fun openAppDeepLink(call: PluginCall) {
        val packageName = call.getString("packageName", "") ?: ""
        val deepLink = call.getString("deepLink", "") ?: ""
        val intent = AppLauncher.buildLaunchIntent(context, packageName, deepLink)
        if (intent == null) {
            call.reject("לא ניתן לפתוח את אפליקציית החניה")
            return
        }
        context.startActivity(intent)
        call.resolve()
    }

    @PluginMethod
    fun openNavigationToParkingSpot(call: PluginCall) {
        if (!EngineStore.hasAnchor(context)) {
            call.reject("מיקום החניה עדיין לא ידוע")
            return
        }
        val lat = EngineStore.getAnchorLat(context)
        val lng = EngineStore.getAnchorLng(context)
        val label = Uri.encode(EngineStore.getAnchorAddress(context).ifBlank { "החניה שלי" })
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("לא נמצאה אפליקציית ניווט במכשיר")
        }
    }

    /** Per-permission granted/denied, independent of the Capacitor "core" alias which
     * bundles location+bluetooth+notifications together and can't tell them apart. */
    @PluginMethod
    fun getDetailedPermissionStatus(call: PluginCall) {
        fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val result = JSObject()
        result.put("location", granted(Manifest.permission.ACCESS_FINE_LOCATION))
        result.put(
            "backgroundLocation",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
        )
        result.put(
            "bluetooth",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) granted(Manifest.permission.BLUETOOTH_CONNECT) else true
        )
        result.put(
            "notifications",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) granted(Manifest.permission.POST_NOTIFICATIONS) else true
        )
        call.resolve(result)
    }

    @PluginMethod
    fun restartServiceIfNeeded(call: PluginCall) {
        val restarted = EngineActions.restartServiceIfDead(context)
        val result = JSObject()
        result.put("restarted", restarted)
        call.resolve(result)
    }

    @PluginMethod
    fun isServiceRunning(call: PluginCall) {
        val result = JSObject()
        result.put("running", ParkStopForegroundService.isRunning)
        call.resolve(result)
    }

    @PluginMethod
    fun isSamsungDevice(call: PluginCall) {
        val result = JSObject()
        result.put("isSamsung", Build.MANUFACTURER.equals("samsung", ignoreCase = true))
        call.resolve(result)
    }

    /** The generic Android "ignore battery optimizations" dialog (openBatteryOptimizationSettings)
     * is a DIFFERENT setting from Samsung's own "Background usage limits" / sleeping-apps list --
     * granting one does not grant the other, and once the generic one is already granted, firing
     * that intent again shows no UI at all (which looked like a dead button). This opens Samsung's
     * own Device Care app instead, where the user can navigate to Background usage limits. */
    @PluginMethod
    fun openSamsungDeviceCare(call: PluginCall) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.samsung.android.lool")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve()
            return
        }
        try {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            call.resolve()
        } catch (e: Exception) {
            call.reject("לא ניתן לפתוח את אפליקציית טיפול במכשיר")
        }
    }

    @PluginMethod
    fun openAppSettings(call: PluginCall) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        call.resolve()
    }

    @PluginMethod
    fun sendTestAlert(call: PluginCall) {
        NotificationHelper.ensureChannels(context)
        val notification = NotificationHelper.buildAlertNotification(
            context,
            EngineStore.Confidence.HIGH,
            EngineStore.getParkingAppName(context).ifBlank { "אפליקציית חניה" },
            EngineStore.getParkingAppPackage(context),
            EngineStore.getParkingAppDeepLink(context)
        )
        NotificationHelper.notify(context, NotificationHelper.ALERT_NOTIFICATION_ID, notification)
        val entry = EngineStore.appendLog(context, "info", "נשלחה התראת בדיקה", EngineStore.EventType.TEST_ALERT)
        EngineEvents.postLogAdded(entry)
        call.resolve()
    }

    @PluginMethod
    fun scheduleTestAlert(call: PluginCall) {
        val delaySeconds = call.getInt("delaySeconds", 30) ?: 30
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, AlertActionReceiver::class.java).setAction(Actions.ACTION_TEST_ALERT)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 6, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)

        val entry = EngineStore.appendLog(
            context, "info", "התראת בדיקה מתוזמנת בעוד $delaySeconds שניות", EngineStore.EventType.TEST_ALERT
        )
        EngineEvents.postLogAdded(entry)
        call.resolve()
    }

    @PluginMethod
    fun simulateBluetoothConnect(call: PluginCall) {
        if (EngineStore.getState(context) != EngineStore.State.MONITORING) {
            call.reject("אין חניה פעילה במעקב — אי אפשר להדמות חיבור בלוטות׳")
            return
        }
        val intent = Intent(context, ParkStopForegroundService::class.java).setAction(Actions.ACTION_SIMULATE_BT_CONNECT)
        ContextCompat.startForegroundService(context, intent)
        call.resolve()
    }

    @PluginMethod
    fun shareLogs(call: PluginCall) {
        val text = EngineStore.formatLogsAsText(context)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "ParkStop – יומן אירועים")
        }
        val chooser = Intent.createChooser(sendIntent, "שתף יומן").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        call.resolve()
    }
}
