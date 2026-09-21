package com.vplusstudio.parkstop

import android.os.Bundle
import com.getcapacitor.BridgeActivity
import com.vplusstudio.parkstop.engine.ParkStopEnginePlugin

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(ParkStopEnginePlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
