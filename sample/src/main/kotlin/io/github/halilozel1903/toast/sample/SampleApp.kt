package io.github.halilozel1903.toast.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.toast.ToastDefaults
import io.github.halilozel1903.toast.ToastHost
import io.github.halilozel1903.toast.ToastResult
import io.github.halilozel1903.toast.ToastState
import io.github.halilozel1903.toast.ToastStyle
import io.github.halilozel1903.toast.rememberToastState
import io.github.halilozel1903.toast.core.ToastDismissReason
import io.github.halilozel1903.toast.core.ToastDuration
import io.github.halilozel1903.toast.core.ToastPosition
import io.github.halilozel1903.toast.core.ToastPriority
import io.github.halilozel1903.toast.core.ToastQueueConfig
import io.github.halilozel1903.toast.core.ToastType
import kotlinx.coroutines.launch

/** Playground settings the user changes with chips and switches. */
private class Options(
    val position: ToastPosition,
    val style: ToastStyle,
    val withAction: Boolean,
    val longDuration: Boolean,
)

@Composable
fun SampleApp(scene: Scene?) {
    val toasts = rememberToastState(config = ToastQueueConfig(maxVisible = 4))
    var position by rememberSaveable { mutableStateOf(scene?.position ?: ToastPosition.Bottom) }
    var style by rememberSaveable { mutableStateOf(scene?.style ?: ToastStyle.Tonal) }
    var withAction by rememberSaveable { mutableStateOf(false) }
    var longDuration by rememberSaveable { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }

    if (scene != null) {
        LaunchedEffect(scene) { toasts.showScene(scene) }
    }

    val options = Options(position, style, withAction, longDuration)
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Controls(
            toasts = toasts,
            options = options,
            log = log,
            onPositionChange = { position = it },
            onStyleChange = { style = it },
            onWithActionChange = { withAction = it },
            onLongDurationChange = { longDuration = it },
            modifier = modifier,
        )
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth >= 720.dp) {
                // Tablets and unfolded foldables: controls on the left, the activity log on the right.
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    controls(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                    ActivityLog(log, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    controls(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    ActivityLog(log, Modifier.fillMaxWidth().height(240.dp))
                    // Room so the last controls can scroll above the bottom toasts.
                    Spacer(Modifier.height(160.dp))
                }
            }
        }
        ToastHost(toasts, style = style)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(
    toasts: ToastState,
    options: Options,
    log: SnapshotStateList<String>,
    onPositionChange: (ToastPosition) -> Unit,
    onStyleChange: (ToastStyle) -> Unit,
    onWithActionChange: (Boolean) -> Unit,
    onLongDurationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    fun record(text: String) {
        log.add(0, text)
        if (log.size > 40) log.removeAt(log.lastIndex)
    }
    fun show(type: ToastType) {
        val demo = DemoToast.of(type)
        val duration = if (options.longDuration) ToastDuration.Long else ToastDuration.Short
        val action = demo.actionLabel ?: "View".takeIf { options.withAction }
        val id = toasts.show(
            message = demo.message,
            type = type,
            title = demo.title,
            actionLabel = action,
            onAction = { record("Action \"$action\" on ${type.name}") },
            duration = duration,
            position = options.position,
            icon = if (type == ToastType.Custom) SampleIcons.Star else null,
            onDismiss = { reason -> record("${type.name} closed: ${reason.describe()}") },
        )
        record("Showing ${type.name} #$id")
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("compose-toast", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tap a button to show a toast. Swipe one sideways to dismiss it, hold it to stop its timer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Types")
        ButtonRow {
            TypeButton(ToastType.Success, ::show)
            TypeButton(ToastType.Error, ::show)
        }
        ButtonRow {
            TypeButton(ToastType.Info, ::show)
            TypeButton(ToastType.Warning, ::show)
        }
        ButtonRow {
            TypeButton(ToastType.Custom, ::show)
            OutlinedButton(onClick = { toasts.dismissAll() }, modifier = Modifier.weight(1f)) { Text("Dismiss all") }
        }

        SectionTitle("Options")
        ChipRow("Edge") {
            for (edge in ToastPosition.entries) {
                FilterChip(
                    selected = options.position == edge,
                    onClick = { onPositionChange(edge) },
                    label = { Text(edge.name) },
                )
            }
        }
        ChipRow("Style") {
            for (look in ToastStyle.entries) {
                FilterChip(
                    selected = options.style == look,
                    onClick = { onStyleChange(look) },
                    label = { Text(look.name) },
                )
            }
        }
        SwitchRow("Add an action button", options.withAction, onWithActionChange)
        SwitchRow("Long duration", options.longDuration, onLongDurationChange)

        SectionTitle("Queue and behavior")
        ButtonRow {
            FilledTonalButton(
                onClick = {
                    repeat(6) { index ->
                        toasts.show("Toast ${index + 1} of 6", position = options.position, duration = ToastDuration.Short)
                    }
                    record("Queued 6 toasts, ${toasts.config.maxVisible} fit at once")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Queue 6") }
            FilledTonalButton(
                onClick = {
                    toasts.success("Link copied", position = options.position, dedupeKey = "copy")
                    record("Copy: same key, one toast")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Copy link") }
        }
        ButtonRow {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        record("Deleted a message, waiting for Undo")
                        val result = toasts.showAndWait(
                            "Message deleted",
                            actionLabel = "Undo",
                            position = options.position,
                            showCloseButton = true,
                        )
                        record(if (result == ToastResult.ActionPerformed) "Message restored" else "Message gone for good")
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Delete") }
            FilledTonalButton(
                onClick = {
                    toasts.show(
                        "Connection lost. Retrying in 5 seconds.",
                        ToastType.Error,
                        title = "Offline",
                        position = options.position,
                        priority = ToastPriority.Urgent,
                        dedupeKey = "offline",
                    )
                    record("Urgent toast: takes a place at once")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Urgent") }
        }
        Button(
            onClick = {
                toasts.warning(
                    "Battery saver is on. Sync is paused.",
                    duration = ToastDuration.Indefinite,
                    position = options.position,
                    dedupeKey = "battery",
                )
                record("Sticky toast: swipe it away")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sticky warning") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun RowScope.TypeButton(type: ToastType, onClick: (ToastType) -> Unit) {
    val accent = ToastDefaults.accentColor(type)
    OutlinedButton(onClick = { onClick(type) }, modifier = Modifier.weight(1f)) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(type.name)
    }
}

@Composable
private fun ChipRow(label: String, chips: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
        chips()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActivityLog(log: List<String>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            if (log.isEmpty()) {
                Text(
                    "Shown toasts and why they went away appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // A plain column: the log is short and the phone layout already scrolls.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (entry in log.take(12)) {
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Texts for the type buttons. */
private class DemoToast(val message: String, val title: String? = null, val actionLabel: String? = null) {
    companion object {
        fun of(type: ToastType): DemoToast = when (type) {
            ToastType.Success -> DemoToast("Your profile changes are live.", title = "Saved")
            ToastType.Error -> DemoToast("Check your connection and try again.", title = "Upload failed", actionLabel = "Retry")
            ToastType.Info -> DemoToast("Syncing 12 notes in the background.")
            ToastType.Warning -> DemoToast("1.2 GB left on this device.", title = "Storage almost full")
            ToastType.Custom -> DemoToast("Ada sent you a photo")
        }
    }
}

private fun ToastDismissReason.describe(): String = when (this) {
    ToastDismissReason.Timeout -> "time ran out"
    ToastDismissReason.Swiped -> "swiped away"
    ToastDismissReason.ActionClicked -> "action pressed"
    ToastDismissReason.Closed -> "closed"
    ToastDismissReason.Programmatic -> "dismissed in code"
    ToastDismissReason.Preempted -> "replaced by an urgent toast"
    ToastDismissReason.Dropped -> "dropped from a full queue"
}
