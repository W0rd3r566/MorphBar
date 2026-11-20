// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.service

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.w0rd3r.morphbar.core.SettingsStore

/** Quick Settings toggle, so the island can be dropped for a game or a film. */
class IsletTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        sync()
    }

    override fun onClick() {
        super.onClick()
        val store = SettingsStore.get(this)
        val turningOn = !store.current.enabled
        if (turningOn && !Settings.canDrawOverlays(this)) {
            sync()
            return
        }
        store.update { it.copy(enabled = turningOn) }
        if (turningOn) IsletOverlayService.start(this) else IsletOverlayService.stop(this)
        sync()
    }

    private fun sync() {
        val tile = qsTile ?: return
        tile.state = if (SettingsStore.get(this).current.enabled) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
