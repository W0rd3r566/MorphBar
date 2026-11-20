// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration and branding.
package dev.w0rd3r.morphbar.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.w0rd3r.morphbar.BuildConfig
import dev.w0rd3r.morphbar.core.CutoutGeometry
import dev.w0rd3r.morphbar.core.LandscapeBehaviour
import dev.w0rd3r.morphbar.core.SettingsStore
import dev.w0rd3r.morphbar.data.IsletNotificationListener
import dev.w0rd3r.morphbar.data.IsletRepository
import dev.w0rd3r.morphbar.data.NoticeSnapshot
import dev.w0rd3r.morphbar.data.PowerSnapshot
import dev.w0rd3r.morphbar.service.IsletOverlayService
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsState()

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var listenerGranted by remember { mutableStateOf(IsletNotificationListener.isEnabled(context)) }
    var geometry by remember { mutableStateOf<CutoutGeometry?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                listenerGranted = IsletNotificationListener.isEnabled(context)
                geometry = runCatching { CutoutGeometry.measure(context) }.getOrNull()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = overlayGranted && listenerGranted
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("MorphBar") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item {
                MasterCard(
                    enabled = settings.enabled,
                    ready = ready,
                    onToggle = { wanted ->
                        if (wanted && !ready) return@MasterCard
                        store.update { it.copy(enabled = wanted) }
                        if (wanted) {
                            IsletOverlayService.start(context)
                        } else {
                            IsletOverlayService.stop(context)
                        }
                    },
                )
            }

            if (!ready) {
                item { SectionHeader("Set up") }
                item {
                    PermissionCard(
                        title = "Display over other apps",
                        body = "Lets MorphBar paint the pill on top of whatever you are doing. " +
                            "It draws one small window and nothing else.",
                        granted = overlayGranted,
                        onGrant = { context.openOverlaySettings() },
                    )
                }
                item {
                    PermissionCard(
                        title = "Notification access",
                        body = "One permission covers both halves of the island: what is playing " +
                            "and what just arrived. MorphBar has no internet permission, so nothing " +
                            "it reads can leave the phone.",
                        granted = listenerGranted,
                        onGrant = { context.openListenerSettings() },
                    )
                }
            }

            item { SectionHeader("What it shows") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Now playing",
                        subtitle = "Artwork, controls and a live progress bar",
                        icon = Icons.Rounded.PlayCircle,
                        checked = settings.showMedia,
                    ) { value -> store.update { it.copy(showMedia = value) } }
                    SwitchRow(
                        title = "Notifications",
                        subtitle = "Peek at anything your channels let through",
                        icon = Icons.Rounded.Notifications,
                        checked = settings.showNotifications,
                    ) { value -> store.update { it.copy(showNotifications = value) } }
                    SwitchRow(
                        title = "Charging",
                        subtitle = "A short battery peek when you plug in",
                        icon = Icons.Rounded.BatteryChargingFull,
                        checked = settings.showCharging,
                    ) { value -> store.update { it.copy(showCharging = value) } }
                }
            }

            item { SectionHeader("Alignment") }
            item {
                AlignmentCard(
                    geometry = geometry,
                    offsetX = settings.offsetX,
                    offsetY = settings.offsetY,
                    bleedX = settings.bleedX,
                    bleedY = settings.bleedY,
                    guide = settings.alignmentGuide,
                    onOffsetX = { value -> store.update { it.copy(offsetX = value) } },
                    onOffsetY = { value -> store.update { it.copy(offsetY = value) } },
                    onBleedX = { value -> store.update { it.copy(bleedX = value) } },
                    onBleedY = { value -> store.update { it.copy(bleedY = value) } },
                    onGuide = { value -> store.update { it.copy(alignmentGuide = value) } },
                    onReset = { store.resetAlignment() },
                )
            }

            item { SectionHeader("Shape and feel") }
            item {
                SettingsCard {
                    SliderRow(
                        title = "Compact width",
                        value = settings.compactWidth,
                        range = 110f..260f,
                        valueLabel = "${settings.compactWidth.roundToInt()} dp",
                    ) { value -> store.update { it.copy(compactWidth = value) } }
                    SliderRow(
                        title = "Expanded width",
                        value = settings.expandedWidthFraction,
                        range = 0.7f..1f,
                        valueLabel = "${(settings.expandedWidthFraction * 100).roundToInt()}%",
                    ) { value -> store.update { it.copy(expandedWidthFraction = value) } }
                    SliderRow(
                        title = "Peek length",
                        value = settings.peekSeconds.toFloat(),
                        range = 2f..10f,
                        steps = 7,
                        valueLabel = "${settings.peekSeconds} s",
                    ) { value -> store.update { it.copy(peekSeconds = value.roundToInt()) } }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    SwitchRow(
                        title = "Colour from album art",
                        subtitle = "Otherwise the island borrows your wallpaper palette",
                        icon = Icons.Rounded.Colorize,
                        checked = settings.albumArtAccent,
                    ) { value -> store.update { it.copy(albumArtAccent = value) } }
                    SwitchRow(
                        title = "Dim behind when expanded",
                        icon = Icons.Rounded.Layers,
                        checked = settings.scrimBehindExpanded,
                    ) { value -> store.update { it.copy(scrimBehindExpanded = value) } }
                    SwitchRow(
                        title = "Haptics",
                        icon = Icons.Rounded.Vibration,
                        checked = settings.haptics,
                    ) { value -> store.update { it.copy(haptics = value) } }
                }
            }

            item { SectionHeader("Behaviour") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Tappable when idle",
                        subtitle = "Off means the island ignores touches with nothing to show, " +
                            "so swiping down over the camera works exactly as it does now",
                        icon = Icons.Rounded.CenterFocusStrong,
                        checked = settings.tapAtRest,
                    ) { value -> store.update { it.copy(tapAtRest = value) } }
                    SwitchRow(
                        title = "Follow into landscape",
                        subtitle = "Off hides the island when the phone is not upright",
                        icon = Icons.Rounded.ScreenRotation,
                        checked = settings.landscape == LandscapeBehaviour.Follow,
                    ) { value ->
                        store.update {
                            it.copy(
                                landscape = if (value) {
                                    LandscapeBehaviour.Follow
                                } else {
                                    LandscapeBehaviour.Hide
                                },
                            )
                        }
                    }
                    SwitchRow(
                        title = "Start after reboot",
                        icon = Icons.Rounded.Bolt,
                        checked = settings.startOnBoot,
                    ) { value -> store.update { it.copy(startOnBoot = value) } }
                }
            }

            item { SectionHeader("Try it") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { IsletRepository.postNotice(demoNotice()) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Fake alert") }
                        FilledTonalButton(
                            onClick = {
                                IsletRepository.postPower(
                                    PowerSnapshot(level = 68, charging = true),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Fake charge") }
                    }
                }
            }

            item { SectionHeader("About") }
            item { AboutCard(geometry) }
        }
    }
}

@Composable
private fun MasterCard(enabled: Boolean, ready: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "Island is on" else "Island is off",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !ready -> "Grant the two permissions below to switch it on"
                        enabled -> "Sitting on your camera, quietly"
                        else -> "Ready when you are"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = ready)
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    SettingsCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}

@Composable
private fun AlignmentCard(
    geometry: CutoutGeometry?,
    offsetX: Float,
    offsetY: Float,
    bleedX: Float,
    bleedY: Float,
    guide: Boolean,
    onOffsetX: (Float) -> Unit,
    onOffsetY: (Float) -> Unit,
    onBleedX: (Float) -> Unit,
    onBleedY: (Float) -> Unit,
    onGuide: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    SettingsCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = when {
                    geometry == null -> "Measuring…"
                    geometry.detected -> "Cutout detected: " +
                        "${geometry.width}×${geometry.height} px, " +
                        "${geometry.centerOffsetX} px from centre"

                    else -> "No cutout reported by this display — Islet is using a centred pill"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Position comes from the display itself, so it is already right on every " +
                    "Pixel. These nudges exist for screen protectors and for taste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SliderRow(
            title = "Horizontal nudge",
            value = offsetX,
            range = -12f..12f,
            valueLabel = "${"%.1f".format(offsetX)} dp",
            onValueChange = onOffsetX,
        )
        SliderRow(
            title = "Vertical nudge",
            value = offsetY,
            range = -12f..12f,
            valueLabel = "${"%.1f".format(offsetY)} dp",
            onValueChange = onOffsetY,
        )
        SliderRow(
            title = "Horizontal bleed",
            value = bleedX,
            range = 0f..8f,
            valueLabel = "${"%.1f".format(bleedX)} dp",
            onValueChange = onBleedX,
        )
        SliderRow(
            title = "Vertical bleed",
            value = bleedY,
            range = 0f..8f,
            valueLabel = "${"%.1f".format(bleedY)} dp",
            onValueChange = onBleedY,
        )
        SwitchRow(
            title = "Show alignment guide",
            subtitle = "Outlines the detected cutout so you can see what Islet sees",
            icon = Icons.Rounded.Visibility,
            checked = guide,
            onCheckedChange = onGuide,
        )
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset alignment")
            }
        }
    }
}

@Composable
private fun AboutCard(geometry: CutoutGeometry?) {
    SettingsCard {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Islet ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Free, open source, no ads, no accounts, no network permission",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (geometry != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Display ${geometry.displayWidth}×${geometry.displayHeight} px  ·  " +
                        "status bar ${geometry.statusBarHeight} px  ·  " +
                        "density ${"%.2f".format(geometry.density)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun demoNotice() = NoticeSnapshot(
    key = "islet-demo",
    packageName = "dev.w0rd3r.morphbar",
    appLabel = "Islet",
    title = "This is what an alert looks like",
    text = "Tap the island to pin it open, swipe up on it to send it away.",
    smallIcon = null,
    largeIcon = null,
    accent = null,
    contentIntent = null,
    actions = emptyList(),
)

private fun Context.openOverlaySettings() {
    startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openListenerSettings() {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            IsletOverlayService.listenerComponent(this).flattenToString(),
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(detail) }.onFailure { startActivity(fallback) }
}
