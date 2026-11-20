// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.w0rd3r.morphbar.core.SettingsStore
import dev.w0rd3r.morphbar.service.IsletOverlayService

/**
 * Notification access is the single permission that unlocks both halves of the
 * island: the shade feed *and* [android.media.session.MediaSessionManager]
 * active sessions. Islet asks for nothing else.
 */
class IsletNotificationListener : NotificationListenerService() {

    private val labelCache = HashMap<String, String>()
    private var lastKey: String? = null
    private var lastSignature: String? = null
    private var lastAtUptime = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        IsletRepository.setListenerConnected(true)
        // If the user granted access while the island was already switched on,
        // the overlay may be waiting on us — nudge it.
        if (SettingsStore.get(this).current.enabled &&
            android.provider.Settings.canDrawOverlays(this)
        ) {
            IsletOverlayService.start(this)
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        IsletRepository.setListenerConnected(false)
        // Ask the platform to bind us again; Android drops listeners on upgrade.
        requestRebind(ComponentName(this, IsletNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val notice = convert(sbn, rankingMap) ?: return
        IsletRepository.postNotice(notice)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        IsletRepository.dismissNotice(sbn.key)
    }

    private fun convert(sbn: StatusBarNotification, rankingMap: RankingMap?): NoticeSnapshot? {
        if (sbn.packageName == packageName) return null
        val preferences = SettingsStore.get(this).current
        if (!preferences.showNotifications) return null

        val n = sbn.notification ?: return null
        val flags = n.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null
        if (n.category == Notification.CATEGORY_TRANSPORT) return null // media lives on the media path
        if (n.category == Notification.CATEGORY_SERVICE) return null

        // Respect the user's own channel choices: anything they silenced stays silent.
        rankingMap?.let { map ->
            val ranking = Ranking()
            if (map.getRanking(sbn.key, ranking)) {
                if (ranking.isAmbient) return null
                if (ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ranking.isSuspended) return null
                // High importance is eligible for Android's native heads-up banner.
                // Do not draw a second banner unless explicitly requested.
                if (!NotificationPresentationPolicy.shouldMirror(
                        importance = ranking.importance,
                        avoidSystemBanners = preferences.avoidSystemBanners,
                    )
                ) return null
            }
        }

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null

        // Apps re-post the same notification constantly (progress bars, sync
        // ticks). Only peek when something a human would notice actually changed.
        val signature = "$title\u0000$text"
        val now = SystemClock.elapsedRealtime()
        if (sbn.key == lastKey && signature == lastSignature && now - lastAtUptime < DEDUPE_WINDOW_MS) {
            return null
        }
        lastKey = sbn.key
        lastSignature = signature
        lastAtUptime = now

        return NoticeSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = labelFor(sbn.packageName),
            title = title.ifEmpty { labelFor(sbn.packageName) },
            text = text,
            smallIcon = runCatching { n.smallIcon?.loadDrawable(this)?.toSoftwareBitmap() }.getOrNull(),
            largeIcon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toSoftwareBitmap() }.getOrNull(),
            accent = n.color.takeIf { it != Notification.COLOR_DEFAULT },
            contentIntent = n.contentIntent,
            actions = n.actions.orEmpty().take(MAX_ACTIONS).map { action ->
                NoticeAction(
                    title = action.title?.toString().orEmpty(),
                    intent = action.actionIntent,
                    isReply = !action.remoteInputs.isNullOrEmpty(),
                )
            // RemoteInput replies require an input UI and filled PendingIntent;
            // an empty send() would not send the user's message.
            }.filter { it.title.isNotEmpty() && !it.isReply },
        )
    }

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            val pm: PackageManager = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))
    }

    companion object {
        private const val DEDUPE_WINDOW_MS = 1_500L
        private const val MAX_ACTIONS = 3

        @Volatile
        var connected: Boolean = false
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val me = ComponentName(context, IsletNotificationListener::class.java)
            return flat.split(':').any {
                val cn = ComponentName.unflattenFromString(it)
                cn != null && cn == me
            }
        }
    }
}
