package dev.w0rd3r.morphbar.data

/** Conservative approximation for avoiding duplicate SystemUI heads-up banners. */
internal object NotificationPresentationPolicy {
    // Android NotificationManager.IMPORTANCE_HIGH.
    private const val IMPORTANCE_HIGH = 4

    fun shouldMirror(importance: Int?, avoidSystemBanners: Boolean): Boolean =
        !avoidSystemBanners || importance == null || importance < IMPORTANCE_HIGH
}
