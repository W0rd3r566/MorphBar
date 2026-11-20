// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follows whichever media session is actually in front of the user and turns it
 * into a [MediaSnapshot]. Notification-listener access is what authorises
 * [MediaSessionManager.getActiveSessions]; there is no second permission and no
 * per-app integration, so every player that publishes a session works.
 */
class MediaMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val sessionManager: MediaSessionManager? =
        context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, IsletNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var active: MediaController? = null
    private var artKey: String? = null
    private var artwork: Bitmap? = null
    private var accent: Int? = null
    private var artworkLoading = false
    private var lastArtworkAttemptMs = 0L

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers.orEmpty())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            active = null
            refresh()
        }
    }

    fun start() {
        val manager = sessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            bind(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access not granted yet", e)
        }
    }

    fun stop() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        active?.unregisterCallback(controllerCallback)
        active = null
        artwork = null
        artKey = null
        accent = null
        artworkLoading = false
        lastArtworkAttemptMs = 0L
        IsletRepository.setMedia(null)
    }

    fun refresh() {
        val manager = sessionManager ?: return
        runCatching { manager.getActiveSessions(listenerComponent) }
            .onSuccess { bind(it) }
    }

    // --- transport ------------------------------------------------------------

    fun togglePlayPause() {
        val controller = active ?: return
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }

    fun next() {
        active?.transportControls?.skipToNext()
    }

    fun previous() {
        active?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        active?.transportControls?.seekTo(positionMs)
    }

    fun sessionActivity() = active?.sessionActivity

    // --- internals ------------------------------------------------------------

    private fun bind(controllers: List<MediaController>) {
        val chosen = pick(controllers)
        if (chosen?.sessionToken == active?.sessionToken) {
            publish()
            return
        }
        active?.unregisterCallback(controllerCallback)
        active = chosen
        artKey = null
        artwork = null
        accent = null
        artworkLoading = false
        lastArtworkAttemptMs = 0L
        if (chosen == null) {
            IsletRepository.setMedia(null)
            return
        }
        chosen.registerCallback(controllerCallback, handler)
        publish()
    }

    /**
     * "The one playing" beats "the one that was playing" beats "the first one".
     * Anything without usable metadata is skipped so a stale session cannot
     * squat on the island with an empty title.
     */
    private fun pick(controllers: List<MediaController>): MediaController? {
        val usable = controllers.filter { it.metadata != null }
        return usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: usable.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
            }
            ?: usable.firstOrNull()
    }

    private fun publish() {
        val controller = active ?: run {
            IsletRepository.setMedia(null)
            return
        }
        val metadata = controller.metadata ?: run {
            IsletRepository.setMedia(null)
            return
        }
        val state = controller.playbackState

        val title = metadata.text(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return
        val artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)

        val hint = IsletRepository.artworkHint.value
            ?.takeIf { it.packageName == controller.packageName }
        // Artwork URI or notification artwork can arrive after the track title.
        val key = ArtworkRequestIdentity.key(
            packageName = controller.packageName,
            title = title, artist = artist, durationMs = duration,
            uris = listOf(
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI).orEmpty(),
                metadata.getString(MediaMetadata.METADATA_KEY_ART_URI).orEmpty(),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).orEmpty(),
            ),
            hintRevision = hint?.revision ?: 0L,
        )
        val changed = key != artKey
        if (changed) {
            artKey = key
            artwork = null
            accent = null
            artworkLoading = false
            lastArtworkAttemptMs = 0L
        }
        val now = SystemClock.elapsedRealtime()
        if (artwork == null && !artworkLoading &&
            (changed || now - lastArtworkAttemptMs >= 2_000L)) {
            artworkLoading = true
            lastArtworkAttemptMs = now
            scope.launch {
                val cover = withContext(Dispatchers.IO) {
                    runCatching { MediaArtworkLoader.load(context, metadata, hint?.bitmap) }.getOrNull()
                }
                val colour = withContext(Dispatchers.Default) { cover?.accentColor() }
                if (artKey == key && active?.sessionToken == controller.sessionToken) {
                    artworkLoading = false
                    artwork = cover
                    accent = colour
                    emit(controller, title, artist, duration, controller.playbackState)
                }
            }
        }
        emit(controller, title, artist, duration, state)
    }

    private fun emit(
        controller: MediaController,
        title: String,
        artist: String,
        duration: Long,
        state: PlaybackState?,
    ) {
        val actions = state?.actions ?: 0L
        IsletRepository.setMedia(
            MediaSnapshot(
                packageName = controller.packageName,
                appLabel = appLabel(controller.packageName),
                title = title,
                artist = artist,
                artwork = artwork,
                accent = accent,
                playing = state?.state == PlaybackState.STATE_PLAYING,
                positionMs = state?.position ?: 0L,
                positionUpdatedAtUptime = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
                speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                durationMs = duration,
                canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
            ),
        )
    }

    private val labels = HashMap<String, String>()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))
    }

    private fun MediaMetadata.text(key: String): String? =
        getString(key)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "IsletMedia"
    }
}
