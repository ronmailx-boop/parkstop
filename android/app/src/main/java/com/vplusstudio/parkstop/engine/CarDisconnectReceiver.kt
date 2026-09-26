package com.vplusstudio.parkstop.engine

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires whenever ANY Bluetooth device disconnects, even if ParkStop isn't
 * currently monitoring a session. If it's the configured car device and no
 * parking session is active yet, we suggest starting one — this is the
 * "bonus" flow from the spec (disconnect from car -> maybe you just parked).
 *
 * ACTION_ACL_DISCONNECTED is one of the broadcasts Android still delivers to
 * manifest-registered receivers even for apps targeting API 26+, so this
 * works without the foreground service running.
 */
class CarDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val address = try { device?.address } catch (e: SecurityException) { null } ?: return

        if (!EngineStore.useBluetooth(context)) return
        if (EngineStore.getState(context) != EngineStore.State.IDLE) return

        val carAddress = EngineStore.getCarDeviceAddress(context)
        if (carAddress.isBlank() || !address.equals(carAddress, ignoreCase = true)) return
        if (EngineStore.getParkingAppId(context).isBlank()) return // nothing to "quick start" with yet

        NotificationHelper.ensureChannels(context)
        NotificationHelper.notify(
            context,
            NotificationHelper.SUGGEST_START_NOTIFICATION_ID,
            NotificationHelper.buildSuggestStartNotification(context)
        )
    }
}
