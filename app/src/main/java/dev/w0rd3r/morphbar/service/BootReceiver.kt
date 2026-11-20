// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.w0rd3r.morphbar.core.SettingsStore

/**
 * Brings the island back after a reboot or an app update. Both are cases where
 * the user would otherwise notice it silently missing and assume it broke.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val settings = SettingsStore.get(context).current
        if (!settings.enabled || !settings.startOnBoot) return
        if (!Settings.canDrawOverlays(context)) return
        IsletOverlayService.start(context)
    }
}
