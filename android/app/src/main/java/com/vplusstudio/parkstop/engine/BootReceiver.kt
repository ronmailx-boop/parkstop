package com.vplusstudio.parkstop.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the monitoring service after a reboot if a parking session was left running. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val state = EngineStore.getState(context)
        if (state == EngineStore.State.MONITORING || state == EngineStore.State.ALERT) {
            EngineStore.setState(context, EngineStore.State.MONITORING)
            ContextCompat.startForegroundService(context, Intent(context, ParkStopForegroundService::class.java))
        }
    }
}
