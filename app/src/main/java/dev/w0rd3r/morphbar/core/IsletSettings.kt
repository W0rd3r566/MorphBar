// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the island should do when the phone is not upright. */
enum class LandscapeBehaviour { Hide, Follow }

data class IsletSettings(
    val enabled: Boolean = false,
    val startOnBoot: Boolean = true,

    // Fine alignment, in dp, applied on top of the measured cutout.
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val bleedX: Float = 1f,
    val bleedY: Float = 1f,

    // Content sources.
    val showMedia: Boolean = true,
    val showNotifications: Boolean = true,
    val avoidSystemBanners: Boolean = true,
    val showCharging: Boolean = true,

    // Behaviour.
    val peekSeconds: Int = 4,
    val compactWidth: Float = 88f,
    val expandedWidthFraction: Float = 0.92f,
    val tapAtRest: Boolean = false,
    val haptics: Boolean = true,
    val scrimBehindExpanded: Boolean = false,
    val landscape: LandscapeBehaviour = LandscapeBehaviour.Hide,

    // Colour.
    val albumArtAccent: Boolean = true,

    // Diagnostics.
    val alignmentGuide: Boolean = false,
)

/**
 * Plain SharedPreferences behind a StateFlow. The overlay service and the
 * settings UI live in the same process, so this is the whole synchronisation
 * story — no DataStore, no IPC, no surprises when the service reads a value
 * on the main thread during a window update.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("islet", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<IsletSettings> = _state.asStateFlow()

    val current: IsletSettings get() = _state.value

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read() = IsletSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        startOnBoot = prefs.getBoolean(KEY_BOOT, true),
        offsetX = prefs.getFloat(KEY_OFFSET_X, 0f),
        offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f),
        bleedX = prefs.getFloat(KEY_BLEED_X, 1f),
        bleedY = prefs.getFloat(KEY_BLEED_Y, 1f),
        showMedia = prefs.getBoolean(KEY_MEDIA, true),
        showNotifications = prefs.getBoolean(KEY_NOTIFS, true),
        avoidSystemBanners = prefs.getBoolean(KEY_AVOID_BANNERS, true),
        showCharging = prefs.getBoolean(KEY_CHARGING, true),
        peekSeconds = prefs.getInt(KEY_PEEK, 4),
        compactWidth = prefs.getFloat(KEY_COMPACT_W, 88f).coerceIn(74f, 94f),
        expandedWidthFraction = prefs.getFloat(KEY_EXPANDED_W, 0.92f),
        tapAtRest = prefs.getBoolean(KEY_TAP_REST, false),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        scrimBehindExpanded = prefs.getBoolean(KEY_SCRIM, false),
        landscape = if (prefs.getBoolean(KEY_LANDSCAPE_FOLLOW, false)) {
            LandscapeBehaviour.Follow
        } else {
            LandscapeBehaviour.Hide
        },
        albumArtAccent = prefs.getBoolean(KEY_ART_ACCENT, true),
        alignmentGuide = prefs.getBoolean(KEY_GUIDE, false),
    )

    fun update(block: (IsletSettings) -> IsletSettings) {
        val next = block(current)
        prefs.edit {
            putBoolean(KEY_ENABLED, next.enabled)
            putBoolean(KEY_BOOT, next.startOnBoot)
            putFloat(KEY_OFFSET_X, next.offsetX)
            putFloat(KEY_OFFSET_Y, next.offsetY)
            putFloat(KEY_BLEED_X, next.bleedX)
            putFloat(KEY_BLEED_Y, next.bleedY)
            putBoolean(KEY_MEDIA, next.showMedia)
            putBoolean(KEY_NOTIFS, next.showNotifications)
            putBoolean(KEY_AVOID_BANNERS, next.avoidSystemBanners)
            putBoolean(KEY_CHARGING, next.showCharging)
            putInt(KEY_PEEK, next.peekSeconds)
            putFloat(KEY_COMPACT_W, next.compactWidth)
            putFloat(KEY_EXPANDED_W, next.expandedWidthFraction)
            putBoolean(KEY_TAP_REST, next.tapAtRest)
            putBoolean(KEY_HAPTICS, next.haptics)
            putBoolean(KEY_SCRIM, next.scrimBehindExpanded)
            putBoolean(KEY_LANDSCAPE_FOLLOW, next.landscape == LandscapeBehaviour.Follow)
            putBoolean(KEY_ART_ACCENT, next.albumArtAccent)
            putBoolean(KEY_GUIDE, next.alignmentGuide)
        }
        _state.value = next
    }

    fun resetAlignment() = update {
        it.copy(offsetX = 0f, offsetY = 0f, bleedX = 1f, bleedY = 1f)
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BOOT = "start_on_boot"
        private const val KEY_OFFSET_X = "offset_x"
        private const val KEY_OFFSET_Y = "offset_y"
        private const val KEY_BLEED_X = "bleed_x"
        private const val KEY_BLEED_Y = "bleed_y"
        private const val KEY_MEDIA = "show_media"
        private const val KEY_NOTIFS = "show_notifications"
        private const val KEY_AVOID_BANNERS = "avoid_system_banners"
        private const val KEY_CHARGING = "show_charging"
        private const val KEY_PEEK = "peek_seconds"
        private const val KEY_COMPACT_W = "compact_width"
        private const val KEY_EXPANDED_W = "expanded_width"
        private const val KEY_TAP_REST = "tap_at_rest"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SCRIM = "scrim"
        private const val KEY_LANDSCAPE_FOLLOW = "landscape_follow"
        private const val KEY_ART_ACCENT = "album_art_accent"
        private const val KEY_GUIDE = "alignment_guide"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
