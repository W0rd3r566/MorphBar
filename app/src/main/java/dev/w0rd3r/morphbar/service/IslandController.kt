// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.service

import dev.w0rd3r.morphbar.core.IsletSettings
import dev.w0rd3r.morphbar.core.SettingsStore
import dev.w0rd3r.morphbar.data.IslandContent
import dev.w0rd3r.morphbar.data.IslandPhase
import dev.w0rd3r.morphbar.data.IslandUiState
import dev.w0rd3r.morphbar.data.IsletRepository
import dev.w0rd3r.morphbar.data.MediaSnapshot
import dev.w0rd3r.morphbar.data.NoticeSnapshot
import dev.w0rd3r.morphbar.data.PowerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Turns four independent event sources into one island state.
 *
 * The priority ladder is deliberately shallow, because the island is one object
 * and cannot queue: a transient peek always wins for its few seconds, media
 * holds the compact slot the rest of the time, and an explicit tap outranks
 * both.
 *
 * "Pinned by the user" is tracked separately for the peek and for media. They
 * look like the same idea and are not: if one flag covered both, a peek that
 * arrived while a media card was open would never time out, because the code
 * would read the open media card as the user having pinned the peek.
 */
class IslandController(
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
) {

    private val peek = MutableStateFlow<Peek?>(null)
    private val peekPinned = MutableStateFlow(false)
    private val mediaExpanded = MutableStateFlow(false)

    private var peekJob: Job? = null
    private var idleJob: Job? = null

    val state: StateFlow<IslandUiState> = combine(
        settings.state,
        IsletRepository.media,
        peek,
        peekPinned,
        mediaExpanded,
    ) { config, media, currentPeek, pinned, expanded ->
        resolve(config, media, currentPeek, pinned, expanded)
    }.stateIn(scope, SharingStarted.Eagerly, IslandUiState())

    init {
        scope.launch {
            IsletRepository.notices.collect { notice ->
                if (!settings.current.showNotifications) return@collect
                show(Peek.Notification(notice), settings.current.peekSeconds * 1000L)
            }
        }
        scope.launch {
            IsletRepository.dismissed.collect { key ->
                val current = peek.value
                if (current is Peek.Notification && current.notice.key == key) clearPeek()
            }
        }
        scope.launch {
            IsletRepository.power.collect { snapshot ->
                if (!settings.current.showCharging) return@collect
                show(Peek.Power(snapshot), POWER_PEEK_MS)
            }
        }
        scope.launch {
            // Nothing playing means nothing to stay expanded for. Without this the
            // island would still be open when the next track starts an hour later.
            IsletRepository.media.collect { media ->
                if (media == null) mediaExpanded.value = false
            }
        }
    }

    // --- input ----------------------------------------------------------------

    fun onTap() {
        val currentPeek = peek.value
        when {
            currentPeek != null ->
                if (peekPinned.value) clearPeek() else pinPeek()

            mediaExpanded.value -> mediaExpanded.value = false

            IsletRepository.media.value != null -> expand()

            settings.current.tapAtRest -> peekIdle()
        }
    }

    fun expand() {
        // Expand the visible notice before any background media session.
        if (peek.value != null) {
            pinPeek()
        } else if (IsletRepository.media.value != null) {
            idleJob?.cancel()
            mediaExpanded.value = true
        }
    }

    fun collapse() {
        idleJob?.cancel()
        mediaExpanded.value = false
        clearPeek()
    }

    // --- internals ------------------------------------------------------------

    private fun show(next: Peek, durationMs: Long) {
        peekJob?.cancel()
        peekPinned.value = false
        peek.value = next
        peekJob = scope.launch {
            delay(durationMs)
            if (!peekPinned.value) {
                peek.value = null
            }
        }
    }

    private fun pinPeek() {
        peekJob?.cancel()
        peekPinned.value = true
    }

    private fun clearPeek() {
        peekJob?.cancel()
        peekPinned.value = false
        peek.value = null
    }

    /**
     * Tapping the island with nothing to show opens it briefly and then closes
     * itself. It must never latch: an island stuck wide open around an empty
     * card is worse than one that ignored the tap.
     */
    private fun peekIdle() {
        idleJob?.cancel()
        mediaExpanded.value = true
        idleJob = scope.launch {
            delay(IDLE_PEEK_MS)
            if (IsletRepository.media.value == null) mediaExpanded.value = false
        }
    }

    private fun resolve(
        config: IsletSettings,
        media: MediaSnapshot?,
        currentPeek: Peek?,
        pinned: Boolean,
        expanded: Boolean,
    ): IslandUiState {
        val mediaContent = media
            ?.takeIf { config.showMedia }
            ?.let { IslandContent.Media(it) }
            ?: IslandContent.Empty

        val peekContent = when (currentPeek) {
            is Peek.Notification -> IslandContent.Notice(currentPeek.notice)
            is Peek.Power -> IslandContent.Power(currentPeek.power)
            null -> null
        }

        return when {
            peekContent != null -> IslandUiState(
                // Never auto-expand a second full-size alert over SystemUI.
                phase = if (pinned) IslandPhase.Expanded else IslandPhase.Compact,
                content = peekContent,
                background = mediaContent,
            )

            mediaContent !is IslandContent.Empty -> IslandUiState(
                phase = if (expanded) IslandPhase.Expanded else IslandPhase.Compact,
                content = mediaContent,
                background = mediaContent,
            )

            expanded -> IslandUiState(
                phase = IslandPhase.Compact,
                content = IslandContent.Empty,
                background = IslandContent.Empty,
            )

            else -> IslandUiState(
                phase = IslandPhase.Resting,
                content = IslandContent.Empty,
                background = IslandContent.Empty,
            )
        }
    }

    private sealed interface Peek {
        data class Notification(val notice: NoticeSnapshot) : Peek
        data class Power(val power: PowerSnapshot) : Peek
    }

    private companion object {
        const val POWER_PEEK_MS = 2_600L
        const val IDLE_PEEK_MS = 2_000L
    }
}
