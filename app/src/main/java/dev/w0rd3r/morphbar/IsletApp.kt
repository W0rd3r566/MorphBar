// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar

import android.app.Application
import dev.w0rd3r.morphbar.core.SettingsStore

class IsletApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warm the store so the notification listener, which can be bound by the
        // system before any Activity exists, never reads a cold SharedPreferences
        // off the main thread at an awkward moment.
        SettingsStore.get(this)
    }
}
