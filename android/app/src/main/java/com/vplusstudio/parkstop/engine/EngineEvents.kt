package com.vplusstudio.parkstop.engine

import android.os.Handler
import android.os.Looper
import com.getcapacitor.JSObject

/**
 * Lightweight in-process pub/sub so the foreground service and its
 * BroadcastReceivers can push live updates to the Capacitor plugin
 * (which then forwards them to JS via notifyListeners) without holding
 * a direct reference to the plugin/bridge.
 */
object EngineEvents {
    interface Listener {
        fun onStatusChanged(status: JSObject)
        fun onLogAdded(entry: JSObject)
    }

    private val listeners = mutableSetOf<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun register(listener: Listener) {
        listeners.add(listener)
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun postStatusChanged(status: JSObject) {
        mainHandler.post {
            listeners.toList().forEach { it.onStatusChanged(status) }
        }
    }

    fun postLogAdded(entry: JSObject) {
        mainHandler.post {
            listeners.toList().forEach { it.onLogAdded(entry) }
        }
    }
}
