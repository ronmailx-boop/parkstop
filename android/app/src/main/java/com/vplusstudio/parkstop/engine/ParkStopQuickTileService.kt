package com.vplusstudio.parkstop.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Notification-shade tile: one tap starts monitoring (reusing the last parking app), one tap stops it. */
class ParkStopQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val state = EngineStore.getState(applicationContext)
        if (state == EngineStore.State.IDLE) {
            EngineActions.startWithLastUsedApp(applicationContext)
        } else {
            EngineActions.stopParking(applicationContext)
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = EngineStore.getState(applicationContext)
        val active = state == EngineStore.State.MONITORING || state == EngineStore.State.ALERT

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "עצור חניה" else "התחל חניה"
        tile.updateTile()
    }
}
