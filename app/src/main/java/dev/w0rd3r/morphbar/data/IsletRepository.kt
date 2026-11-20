// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single in-process bus between the notification listener, the media
 * monitor, the power receiver and the overlay. All four live in the same
 * process, so a plain object with flows is the whole architecture.
 */
object IsletRepository {

    private val _media = MutableStateFlow<MediaSnapshot?>(null)
    val media: StateFlow<MediaSnapshot?> = _media.asStateFlow()

    /** Plug/unplug moments. Also a peek, so it is an event and not a state. */
    private val _power = MutableSharedFlow<PowerSnapshot>(extraBufferCapacity = 4)
    val power: SharedFlow<PowerSnapshot> = _power.asSharedFlow()

    /** Fires once per notification worth peeking at. Replay 0: a peek is a moment. */
    private val _notices = MutableSharedFlow<NoticeSnapshot>(extraBufferCapacity = 8)
    val notices: SharedFlow<NoticeSnapshot> = _notices.asSharedFlow()

    /** Keys currently live in the shade, so a dismissal can cancel an in-flight peek. */
    private val _dismissed = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val dismissed: SharedFlow<String> = _dismissed.asSharedFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun setMedia(snapshot: MediaSnapshot?) {
        _media.value = snapshot
    }

    fun postPower(snapshot: PowerSnapshot) {
        _power.tryEmit(snapshot)
    }

    fun postNotice(notice: NoticeSnapshot) {
        _notices.tryEmit(notice)
    }

    fun dismissNotice(key: String) {
        _dismissed.tryEmit(key)
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
        if (!connected) _media.value = null
    }
}
