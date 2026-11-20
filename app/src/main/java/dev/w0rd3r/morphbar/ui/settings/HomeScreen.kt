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
import androidx.compose.material3.TopAppBar
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
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
                item { SectionHeader("Разрешения") }
                item {
                    PermissionCard(
                        title = "Поверх других приложений",
                        body = "Разрешение для отображения капсулы поверх приложений.",
                        granted = overlayGranted,
                        onGrant = { context.openOverlaySettings() },
                    )
                }
                item {
                    PermissionCard(
                        title = "Доступ к уведомлениям",
                        body = "Нужно для музыки и уведомлений. У MorphBar нет доступа к интернету.",
                        granted = listenerGranted,
                        onGrant = { context.openListenerSettings() },
                    )
                }
            }

            item { SectionHeader("События") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Музыка",
                        subtitle = "Обложка, управление и прогресс",
                        icon = Icons.Rounded.PlayCircle,
                        checked = settings.showMedia,
                    ) { value -> store.update { it.copy(showMedia = value) } }
                    SwitchRow(
                        title = "Уведомления",
                        subtitle = "Компактные события вместо больших баннеров",
                        icon = Icons.Rounded.Notifications,
                        checked = settings.showNotifications,
                    ) { value -> store.update { it.copy(showNotifications = value) } }
                    if (settings.showNotifications) {
                        SwitchRow(
                            title = "Не дублировать баннеры Android",
                            subtitle = "Системные всплывающие уведомления остаются в Android; выключи, если хочешь видеть их и в MorphBar",
                            icon = Icons.Rounded.Layers,
                            checked = settings.avoidSystemBanners,
                        ) { value -> store.update { it.copy(avoidSystemBanners = value) } }
                    }
                    SwitchRow(
                        title = "Зарядка",
                        subtitle = "Показывать подключение зарядки",
                        icon = Icons.Rounded.BatteryChargingFull,
                        checked = settings.showCharging,
                    ) { value -> store.update { it.copy(showCharging = value) } }
                }
            }

            item { SectionHeader("Положение капсулы") }
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

            item { SectionHeader("Внешний вид") }
            item {
                SettingsCard {
                    SliderRow(
                        title = "Ширина капсулы",
                        value = settings.compactWidth,
                        range = 74f..94f,
                        valueLabel = "${settings.compactWidth.roundToInt()} dp",
                    ) { value -> store.update { it.copy(compactWidth = value) } }
                    SliderRow(
                        title = "Ширина карточки",
                        value = settings.expandedWidthFraction,
                        range = 0.55f..0.92f,
                        valueLabel = "${(settings.expandedWidthFraction * 100).roundToInt()}%",
                    ) { value -> store.update { it.copy(expandedWidthFraction = value) } }
                    SliderRow(
                        title = "Время показа",
                        value = settings.peekSeconds.toFloat(),
                        range = 2f..10f,
                        steps = 7,
                        valueLabel = "${settings.peekSeconds} s",
                    ) { value -> store.update { it.copy(peekSeconds = value.roundToInt()) } }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    SwitchRow(
                        title = "Цвет из обложки",
                        subtitle = "Иначе используется палитра обоев",
                        icon = Icons.Rounded.Colorize,
                        checked = settings.albumArtAccent,
                    ) { value -> store.update { it.copy(albumArtAccent = value) } }
                    SwitchRow(
                        title = "Затемнять фон при музыке",
                        icon = Icons.Rounded.Layers,
                        checked = settings.scrimBehindExpanded,
                    ) { value -> store.update { it.copy(scrimBehindExpanded = value) } }
                    SwitchRow(
                        title = "Виброотклик",
                        icon = Icons.Rounded.Vibration,
                        checked = settings.haptics,
                    ) { value -> store.update { it.copy(haptics = value) } }
                }
            }

            item { SectionHeader("Поведение") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Касания в ожидании",
                        subtitle = "Выкл. — свайп для открытия шторки работает как обычно",
                        icon = Icons.Rounded.CenterFocusStrong,
                        checked = settings.tapAtRest,
                    ) { value -> store.update { it.copy(tapAtRest = value) } }
                    SwitchRow(
                        title = "Показывать горизонтально",
                        subtitle = "По умолчанию капсула скрыта при повороте",
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
                        title = "Запуск после перезагрузки",
                        icon = Icons.Rounded.Bolt,
                        checked = settings.startOnBoot,
                    ) { value -> store.update { it.copy(startOnBoot = value) } }
                }
            }

            item { SectionHeader("Проверка") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { IsletRepository.postNotice(demoNotice()) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Тест уведомления") }
                        FilledTonalButton(
                            onClick = {
                                IsletRepository.postPower(
                                    PowerSnapshot(level = 68, charging = true),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Тест зарядки") }
                    }
                }
            }

            item { SectionHeader("О приложении") }
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
                    text = if (enabled) "MorphBar включён" else "MorphBar выключен",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !ready -> "Нужны два разрешения для работы"
                        enabled -> "Капсула активна"
                        else -> "Готов к запуску"
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
                Button(onClick = onGrant) { Text("Разрешить") }
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
                    geometry == null -> "Определяем камеру…"
                    geometry.detected -> "Камера: " +
                        "${geometry.width}×${geometry.height} px, " +
                        "${geometry.centerOffsetX} px from centre"

                    else -> "Камера не определена, используем центр"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Позиция определяется через Android. Ползунки позволяют подстроить положение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SliderRow(
            title = "Сдвиг по X",
            value = offsetX,
            range = -12f..12f,
            valueLabel = "${"%.1f".format(java.util.Locale.US, offsetX)} dp",
            onValueChange = onOffsetX,
        )
        SliderRow(
            title = "Сдвиг по Y",
            value = offsetY,
            range = -12f..12f,
            valueLabel = "${"%.1f".format(java.util.Locale.US, offsetY)} dp",
            onValueChange = onOffsetY,
        )
        SliderRow(
            title = "Ширина вокруг камеры",
            value = bleedX,
            range = 0f..8f,
            valueLabel = "${"%.1f".format(java.util.Locale.US, bleedX)} dp",
            onValueChange = onBleedX,
        )
        SliderRow(
            title = "Высота вокруг камеры",
            value = bleedY,
            range = 0f..8f,
            valueLabel = "${"%.1f".format(java.util.Locale.US, bleedY)} dp",
            onValueChange = onBleedY,
        )
        SwitchRow(
            title = "Границы камеры",
            subtitle = "Показывает границу выреза камеры",
            icon = Icons.Rounded.Visibility,
            checked = guide,
            onCheckedChange = onGuide,
        )
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Сбросить положение")
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
                    Text("MorphBar ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Открытый код · Без рекламы · Без доступа к интернету",
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
    key = "morphbar-demo",
    packageName = "dev.w0rd3r.morphbar",
    appLabel = "MorphBar",
    title = "Тестовое уведомление",
    text = "Нажми на капсулу, чтобы раскрыть. Свайп вверх — закрыть.",
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
