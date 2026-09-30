package io.github.halilozel1903.toast

import android.os.SystemClock
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.halilozel1903.toast.core.ActiveToast
import io.github.halilozel1903.toast.core.RecommendedTimeout
import io.github.halilozel1903.toast.core.Toast
import io.github.halilozel1903.toast.core.ToastAction
import io.github.halilozel1903.toast.core.ToastClock
import io.github.halilozel1903.toast.core.ToastDismissReason
import io.github.halilozel1903.toast.core.ToastDuration
import io.github.halilozel1903.toast.core.ToastPosition
import io.github.halilozel1903.toast.core.ToastPriority
import io.github.halilozel1903.toast.core.ToastQueue
import io.github.halilozel1903.toast.core.ToastQueueConfig
import io.github.halilozel1903.toast.core.ToastRequest
import io.github.halilozel1903.toast.core.ToastType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** How a toast shown with [ToastState.showAndWait] ended. */
public enum class ToastResult {
    /** The user pressed the action button. */
    ActionPerformed,

    /** It went away any other way: timeout, swipe, close button, code, or it was dropped. */
    Dismissed,
}

/**
 * Remembers a [ToastState] for a [ToastHost].
 *
 * ```kotlin
 * val toasts = rememberToastState()
 * Box {
 *     Content(onSaved = { toasts.success("Saved") })
 *     ToastHost(toasts)
 * }
 * ```
 *
 * @param config Queue settings: toasts per edge, waiting line length, duplicates and timeouts. Read once.
 * @param defaultPosition The edge used when `show` gets no position. Read once.
 */
@Composable
public fun rememberToastState(
    config: ToastQueueConfig = ToastQueueConfig(),
    defaultPosition: ToastPosition = ToastPosition.Bottom,
): ToastState = remember { ToastState(config, defaultPosition) }

/**
 * The toasts of one [ToastHost]: show them with one line, from any composable callback or from a
 * coroutine on the main thread.
 *
 * ```kotlin
 * toasts.success("Profile saved")
 * toasts.error("Upload failed", title = "No connection", actionLabel = "Retry", onAction = ::retry)
 * toasts.show("3 new messages", position = ToastPosition.Top, dedupeKey = "inbox")
 * if (toasts.showAndWait("Chat deleted", actionLabel = "Undo") == ToastResult.ActionPerformed) restore()
 * ```
 *
 * Timers run while a [ToastHost] for this state is on screen. Not thread safe: call it from the main thread.
 */
@Stable
public class ToastState(
    config: ToastQueueConfig = ToastQueueConfig(),
    /** The edge used when `show` gets no position. */
    public val defaultPosition: ToastPosition = ToastPosition.Bottom,
    private val clock: ToastClock = ToastClock { SystemClock.elapsedRealtime() },
) {
    private val queue = ToastQueue(config, clock)
    private val changes = Channel<Unit>(Channel.CONFLATED)

    /** Toasts on screen and toasts still playing their exit animation, in the order they appeared. */
    internal val items = mutableStateListOf<ToastItemState>()

    /** How many toasts are on screen or waiting. Observable from composition. */
    public var count: Int by mutableStateOf(0)
        private set

    /** The toasts on screen, in the order they appeared. Observable from composition. */
    public val visibleToasts: List<Toast>
        get() = items.filter { it.isShowing }.map { it.toast }

    /** The settings this state was created with. */
    public val config: ToastQueueConfig get() = queue.config

    init {
        queue.listener = object : ToastQueue.Listener {
            override fun onShown(toast: ActiveToast) {
                val extras = toast.toast.extras
                items.add(ToastItemState(toast.toast, extras?.icon))
            }

            override fun onReplaced(previous: Toast, current: Toast) {
                // Keep everyone who waits for the old toast.
                val old = previous.extras
                val new = current.extras
                if (old != null && new != null && old !== new) new.onDismiss.addAll(0, old.onDismiss)
                items.firstOrNull { it.id == current.id }?.let {
                    it.toast = current
                    it.icon = new?.icon
                }
            }

            override fun onDismissed(toast: Toast, reason: ToastDismissReason) {
                items.firstOrNull { it.id == toast.id && it.isShowing }?.let {
                    it.dismissReason = reason
                    it.visibility.targetState = false
                }
                toast.extras?.onDismiss?.toList()?.forEach { it(reason) }
            }
        }
    }

    /**
     * Shows a toast and returns its id. When [dedupeKey] matches a toast that is visible or waiting, no second
     * toast appears (see [ToastQueueConfig.duplicatePolicy]) and the existing id is returned.
     *
     * @param message The main text.
     * @param type Picks the icon, colors and how urgently TalkBack announces it.
     * @param title An optional bold first line.
     * @param actionLabel Text of an action button, such as "Undo". The toast hides when it is pressed.
     * @param onAction Called when the action button is pressed.
     * @param duration How long it stays. Accessibility settings can make it longer.
     * @param position Top or bottom edge; [defaultPosition] when `null`.
     * @param priority Order in the waiting line; [ToastPriority.Urgent] replaces a visible toast.
     * @param dedupeKey Toasts with the same key are shown once.
     * @param showCloseButton Adds a close button.
     * @param swipeToDismiss Whether the user can swipe it away.
     * @param icon Replaces the type's icon, required for a nice [ToastType.Custom] toast.
     * @param onDismiss Called once when it goes away, with the reason.
     */
    public fun show(
        message: String,
        type: ToastType = ToastType.Info,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: ToastDuration = ToastDuration.Short,
        position: ToastPosition? = null,
        priority: ToastPriority = ToastPriority.Normal,
        dedupeKey: String? = null,
        showCloseButton: Boolean = false,
        swipeToDismiss: Boolean = true,
        icon: ImageVector? = null,
        onDismiss: ((ToastDismissReason) -> Unit)? = null,
    ): Long {
        val extras = ToastExtras(icon, onAction)
        onDismiss?.let { extras.onDismiss.add(it) }
        val request = ToastRequest(
            message = message,
            type = type,
            title = title,
            action = actionLabel?.let { ToastAction(it) },
            duration = duration,
            position = position ?: defaultPosition,
            priority = priority,
            dedupeKey = dedupeKey,
            swipeToDismiss = swipeToDismiss,
            showCloseButton = showCloseButton,
            payload = extras,
        )
        val id = queue.enqueue(request)
        val existing = queue.find(id)?.extras
        if (existing != null && existing !== extras) {
            // A duplicate that was not replaced: still tell this caller when the toast goes away.
            onDismiss?.let { existing.onDismiss.add(it) }
        }
        // Update the count and wake the timer loop for the new deadline.
        refresh()
        return id
    }

    /** Shows a [ToastType.Success] toast. */
    public fun success(
        message: String,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: ToastDuration = ToastDuration.Short,
        position: ToastPosition? = null,
        dedupeKey: String? = null,
    ): Long = show(message, ToastType.Success, title, actionLabel, onAction, duration, position, dedupeKey = dedupeKey)

    /** Shows a [ToastType.Error] toast. TalkBack announces it right away. */
    public fun error(
        message: String,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: ToastDuration = ToastDuration.Long,
        position: ToastPosition? = null,
        dedupeKey: String? = null,
    ): Long = show(message, ToastType.Error, title, actionLabel, onAction, duration, position, ToastPriority.High, dedupeKey)

    /** Shows a [ToastType.Info] toast. */
    public fun info(
        message: String,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: ToastDuration = ToastDuration.Short,
        position: ToastPosition? = null,
        dedupeKey: String? = null,
    ): Long = show(message, ToastType.Info, title, actionLabel, onAction, duration, position, dedupeKey = dedupeKey)

    /** Shows a [ToastType.Warning] toast. */
    public fun warning(
        message: String,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: ToastDuration = ToastDuration.Long,
        position: ToastPosition? = null,
        dedupeKey: String? = null,
    ): Long = show(message, ToastType.Warning, title, actionLabel, onAction, duration, position, dedupeKey = dedupeKey)

    /**
     * Shows a toast and suspends until it goes away, like `SnackbarHostState.showSnackbar`.
     * Cancelling the coroutine hides the toast.
     *
     * ```kotlin
     * scope.launch {
     *     if (toasts.showAndWait("Chat deleted", actionLabel = "Undo") == ToastResult.ActionPerformed) undo()
     * }
     * ```
     */
    public suspend fun showAndWait(
        message: String,
        type: ToastType = ToastType.Info,
        title: String? = null,
        actionLabel: String? = null,
        duration: ToastDuration = ToastDuration.Short,
        position: ToastPosition? = null,
        priority: ToastPriority = ToastPriority.Normal,
        dedupeKey: String? = null,
        showCloseButton: Boolean = false,
        icon: ImageVector? = null,
    ): ToastResult {
        var id = 0L
        try {
            return suspendCancellableCoroutine { continuation ->
                id = show(
                    message = message,
                    type = type,
                    title = title,
                    actionLabel = actionLabel,
                    duration = duration,
                    position = position,
                    priority = priority,
                    dedupeKey = dedupeKey,
                    showCloseButton = showCloseButton,
                    icon = icon,
                    onDismiss = { reason ->
                        if (continuation.isActive) {
                            continuation.resume(
                                if (reason == ToastDismissReason.ActionClicked) ToastResult.ActionPerformed else ToastResult.Dismissed,
                            )
                        }
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            // Runs on the caller's dispatcher, so the queue stays on one thread.
            dismiss(id)
            throw cancelled
        }
    }

    /** Hides the toast with [id], visible or waiting. Returns `false` when there is none. */
    public fun dismiss(id: Long): Boolean = dismiss(id, ToastDismissReason.Programmatic)

    /** Hides all toasts, visible and waiting. */
    public fun dismissAll() {
        queue.dismissAll(ToastDismissReason.Programmatic)
        refresh()
    }

    /** Hides the toasts at [position] and the ones waiting for it. */
    public fun dismissAll(position: ToastPosition) {
        val ids = queue.visibleAt(position).map { it.id } + queue.pending.filter { it.position == position }.map { it.id }
        ids.forEach { queue.dismiss(it, ToastDismissReason.Programmatic) }
        refresh()
    }

    /** Stops all timers, for example while a dialog covers the toasts. [ToastHost] also pauses while it is not shown. */
    public fun pause() {
        queue.pause()
        refresh()
    }

    /** Restarts the timers stopped by [pause]. */
    public fun resume() {
        queue.resume()
        refresh()
    }

    /** The time the toast with [id] has left in milliseconds, or `null` when it is not visible or never times out. */
    public fun remainingMillis(id: Long): Long? = queue.remainingMillis(id)

    // region Used by ToastHost

    internal fun dismiss(id: Long, reason: ToastDismissReason): Boolean {
        val dismissed = queue.dismiss(id, reason)
        refresh()
        return dismissed
    }

    internal fun performAction(id: Long) {
        val extras = queue.find(id)?.extras ?: return
        extras.onAction?.invoke()
        dismiss(id, ToastDismissReason.ActionClicked)
    }

    internal fun touch(id: Long, touched: Boolean) {
        queue.touch(id, touched)
        refresh()
    }

    internal fun setAccessibility(screenReaderEnabled: Boolean, recommendedTimeout: RecommendedTimeout?) {
        queue.screenReaderEnabled = screenReaderEnabled
        queue.recommendedTimeout = recommendedTimeout
    }

    /** Called when an item finished its exit animation. */
    internal fun remove(item: ToastItemState) {
        items.remove(item)
    }

    /** Runs the timers until cancelled. */
    internal suspend fun runTimers() {
        while (true) {
            if (queue.tick() > 0) refresh()
            val deadline = queue.nextDeadlineMillis()
            if (deadline == null) {
                changes.receive()
            } else {
                val wait = deadline - clock.nowMillis()
                if (wait > 0) withTimeoutOrNull(wait) { changes.receive() }
            }
        }
    }

    private fun refresh() {
        count = queue.visible.size + queue.pending.size
        changes.trySend(Unit)
    }

    // endregion
}

/** What `show` attaches to a toast besides its text. */
internal class ToastExtras(
    val icon: ImageVector?,
    val onAction: (() -> Unit)?,
) {
    val onDismiss: MutableList<(ToastDismissReason) -> Unit> = mutableListOf()
}

internal val Toast.extras: ToastExtras? get() = request.payload as? ToastExtras

/** A toast as the host draws it, including while it animates out. */
internal class ToastItemState(toast: Toast, icon: ImageVector?) {
    val id: Long = toast.id
    var toast: Toast by mutableStateOf(toast)
    var icon: ImageVector? by mutableStateOf(icon)
    var dismissReason: ToastDismissReason? by mutableStateOf(null)
    val visibility: MutableTransitionState<Boolean> = MutableTransitionState(false).apply { targetState = true }

    val isShowing: Boolean get() = visibility.targetState
}
