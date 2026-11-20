// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import dev.w0rd3r.morphbar.MainActivity
import dev.w0rd3r.morphbar.R
import dev.w0rd3r.morphbar.core.CutoutGeometry
import dev.w0rd3r.morphbar.core.IsletSettings
import dev.w0rd3r.morphbar.core.LandscapeBehaviour
import dev.w0rd3r.morphbar.core.SettingsStore
import dev.w0rd3r.morphbar.data.IslandContent
import dev.w0rd3r.morphbar.data.IslandPhase
import dev.w0rd3r.morphbar.data.IsletNotificationListener
import dev.w0rd3r.morphbar.data.IsletRepository
import dev.w0rd3r.morphbar.data.MediaMonitor
import dev.w0rd3r.morphbar.data.PowerSnapshot
import dev.w0rd3r.morphbar.ui.island.Island
import dev.w0rd3r.morphbar.ui.island.IslandActions
import dev.w0rd3r.morphbar.ui.island.IslandMargin
import dev.w0rd3r.morphbar.ui.island.IslandTopMargin
import dev.w0rd3r.morphbar.ui.theme.IslandTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Owns the one window that is the island.
 *
 * The window is WRAP_CONTENT with `Gravity.TOP or Gravity.CENTER_HORIZONTAL`
 * and `x` set to the cutout's offset from the centre of the display. That one
 * choice is what makes the alignment hold on every Pixel: centre-gravity means
 * the island stays pinned to the camera as it grows and shrinks, and the `x`
 * offset covers the models whose punch hole is in the top-left rather than
 * centred. Nothing here is hard-coded per device.
 */
class IsletOverlayService : LifecycleService() {

    private lateinit var settings: SettingsStore
    private lateinit var controller: IslandController
    private lateinit var mediaMonitor: MediaMonitor

    private var windowContext: Context? = null
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayOwner: ComposeOverlayOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var geometry by mutableStateOf<CutoutGeometry?>(null)
    private var attached = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val charging = intent.action == Intent.ACTION_POWER_CONNECTED
            val manager = context.getSystemService(BatteryManager::class.java)
            val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            IsletRepository.postPower(PowerSnapshot(level = level, charging = charging))
        }
    }

    private val configCallback = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = remeasure()

        @Deprecated("Required by ComponentCallbacks")
        override fun onLowMemory() = Unit
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) remeasure()
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.get(this)
        controller = IslandController(lifecycleScope, settings)
        mediaMonitor = MediaMonitor(this, lifecycleScope)

        val displayManager = getSystemService(DisplayManager::class.java)
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val context = createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        windowContext = context
        windowManager = context.getSystemService(WindowManager::class.java)
        geometry = CutoutGeometry.measure(context)

        context.registerComponentCallbacks(configCallback)
        displayManager.registerDisplayListener(displayListener, null)

        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        observe()
    }

    @SuppressLint("InlinedApi") // ServiceCompat only reads the type on API 34+
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            buildForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        when (intent?.action) {
            ACTION_STOP -> {
                settings.update { it.copy(enabled = false) }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        attach()
        mediaMonitor.start()
        mediaMonitor.refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        detach()
        mediaMonitor.stop()
        runCatching { unregisterReceiver(powerReceiver) }
        runCatching { windowContext?.unregisterComponentCallbacks(configCallback) }
        runCatching {
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        super.onDestroy()
    }

    // --- window ---------------------------------------------------------------

    private fun attach() {
        if (attached) return
        val context = windowContext ?: return
        val wm = windowManager ?: return
        val measured = geometry ?: CutoutGeometry.measure(context).also { geometry = it }

        val owner = ComposeOverlayOwner()
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by controller.state.collectAsState()
                val config by settings.state.collectAsState()
                val current = geometry
                if (current != null) {
                    IslandTheme {
                        Island(
                            state = state,
                            geometry = current,
                            settings = config,
                            actions = islandActions(),
                        )
                    }
                }
            }
        }
        owner.attachTo(view)

        val params = baseParams(measured, settings.current)
        runCatching { wm.addView(view, params) }
            .onFailure {
                Log.e(TAG, "Unable to add overlay window", it)
                owner.destroy()
                stopSelf()
                return
            }

        composeView = view
        overlayOwner = owner
        layoutParams = params
        attached = true
    }

    private fun detach() {
        composeView?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        overlayOwner?.destroy()
        composeView = null
        overlayOwner = null
        layoutParams = null
        attached = false
    }

    private fun baseParams(
        cutout: CutoutGeometry,
        config: IsletSettings,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        applyPosition(this, cutout, config)
    }

    private fun applyPosition(
        params: WindowManager.LayoutParams,
        cutout: CutoutGeometry,
        config: IsletSettings,
    ) {
        val density = cutout.density
        // Horizontal: centre-gravity offset, so growth stays symmetric about the lens.
        params.x = cutout.centerOffsetX + (config.offsetX * density).roundToInt()
        // Vertical: the island's own transparent top margin is subtracted so the
        // painted pill lands exactly on the top edge of the cutout.
        val topMargin = (IslandTopMargin.value * density).roundToInt()
        val bleed = (config.bleedY * density).roundToInt()
        // Expanded cards belong below the system status bar. Its icons are
        // always rendered over TYPE_APPLICATION_OVERLAY on stock Pixels.
        val expanded = controller.state.value.phase == IslandPhase.Expanded
        // Android owns the status bar. A TYPE_APPLICATION_OVERLAY can look
        // aligned with the camera but cannot reliably receive taps there.
        // Keep the idle camera mask at the cutout; move interactive states
        // into the touchable app area just below the system status bar.
        // Compact is visually centred on the real front camera, just like
        // the original Islet. Expanded cards are the only state that moves
        // under the protected Android status bar for reliable controls.
        val anchor = if (expanded) {
            cutout.statusBarHeight + (4f * density).roundToInt()
        } else {
            cutout.top - bleed - topMargin
        }
        params.y = anchor + (config.offsetY * density).roundToInt()
    }

    private fun refreshParams() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val cutout = geometry ?: return
        val config = settings.current
        val state = controller.state.value

        applyPosition(params, cutout, config)

        val resting = state.phase == IslandPhase.Resting
        val passive = resting && !config.tapAtRest
        params.flags = if (passive) {
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            BASE_FLAGS
        }

        val expanded = state.phase == IslandPhase.Expanded
        if (expanded && config.scrimBehindExpanded && state.content is IslandContent.Media) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = 0.38f
        } else {
            params.dimAmount = 0f
        }

        val hidden = !cutout.isUpright && config.landscape == LandscapeBehaviour.Hide
        view.visibility = if (hidden) View.GONE else View.VISIBLE

        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun remeasure() {
        val context = windowContext ?: return
        geometry = CutoutGeometry.measure(context)
        refreshParams()
    }

    // --- wiring ---------------------------------------------------------------

    private fun observe() {
        lifecycleScope.launch {
            controller.state.collectLatest { refreshParams() }
        }
        lifecycleScope.launch {
            settings.state.collectLatest { config ->
                if (!config.enabled) {
                    stopSelf()
                    return@collectLatest
                }
                refreshParams()
            }
        }
        lifecycleScope.launch {
            IsletRepository.listenerConnected.collectLatest { connected ->
                if (connected) mediaMonitor.refresh()
            }
        }
        lifecycleScope.launch {
            IsletRepository.artworkHint.collectLatest {
                if (attached) mediaMonitor.refresh()
            }
        }
    }

    private fun islandActions() = IslandActions(
        onTap = { controller.onTap() },
        onCollapse = { controller.collapse() },
        onExpand = { controller.expand() },
        onPlayPause = { mediaMonitor.togglePlayPause() },
        onNext = { mediaMonitor.next() },
        onPrevious = { mediaMonitor.previous() },
        onSeek = { fraction ->
            IsletRepository.media.value?.let { media ->
                if (media.durationMs > 0) {
                    mediaMonitor.seekTo((media.durationMs * fraction).toLong())
                }
            }
        },
        onOpenMedia = {
            fire(mediaMonitor.sessionActivity())
            controller.collapse()
        },
        onOpenNotice = { notice ->
            fire(notice.contentIntent)
            controller.collapse()
        },
        onNoticeAction = { action ->
            fire(action.intent)
            controller.collapse()
        },
    )

    /**
     * Holding SYSTEM_ALERT_WINDOW is one of the documented exemptions from
     * background-activity-start restrictions, which is why a tap on the island
     * can open the app that posted the notification.
     */
    private fun fire(intent: PendingIntent?) {
        if (intent == null) return
        runCatching { intent.send() }
            .onFailure { Log.w(TAG, "PendingIntent refused", it) }
    }

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_running),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.channel_running_description)
                setShowBadge(false)
            },
        )

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, IsletOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_island)
            .setContentTitle(getString(R.string.foreground_title))
            .setContentText(getString(R.string.foreground_text))
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_tile_island),
                    getString(R.string.action_turn_off),
                    stop,
                ).build(),
            )
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "IsletOverlay"
        private const val CHANNEL_ID = "islet_running"
        private const val FOREGROUND_ID = 1001
        const val ACTION_STOP = "dev.w0rd3r.morphbar.STOP"

        private const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            val intent = Intent(context, IsletOverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IsletOverlayService::class.java))
        }

        fun isListenerEnabled(context: Context): Boolean =
            IsletNotificationListener.isEnabled(context)

        fun listenerComponent(context: Context): ComponentName =
            ComponentName(context, IsletNotificationListener::class.java)
    }
}
