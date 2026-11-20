# Compose already ships its own consumer rules; these cover the bits R8 cannot
# see through because the platform reaches them reflectively.

# Entry points declared in the manifest.
-keep class dev.w0rd3r.morphbar.IsletApp { *; }
-keep class dev.w0rd3r.morphbar.MainActivity { *; }
-keep class dev.w0rd3r.morphbar.data.IsletNotificationListener { *; }
-keep class dev.w0rd3r.morphbar.service.IsletOverlayService { *; }
-keep class dev.w0rd3r.morphbar.service.IsletTileService { *; }
-keep class dev.w0rd3r.morphbar.service.BootReceiver { *; }

# Kotlin coroutines internals.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
