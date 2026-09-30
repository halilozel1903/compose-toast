package io.github.halilozel1903.toast.core

/** What happens when a toast arrives with the [ToastRequest.dedupeKey] of one that is visible or waiting. */
public enum class DuplicatePolicy {
    /** The new toast is ignored. */
    Ignore,

    /** The new toast is ignored, but the visible one starts its time again. The default. */
    RestartTimer,

    /** The existing toast takes the new text, type and action, keeps its place and starts its time again. */
    Replace,
}

/**
 * Settings of a [ToastQueue].
 *
 * @property maxVisible How many toasts can be on screen at each edge at once. More wait in line.
 * @property maxQueued How many toasts can wait. When more arrive, the least important waiting toast is dropped.
 * @property duplicatePolicy What a toast with the key of an existing one does.
 * @property timeouts How durations become milliseconds.
 * @property minRemainingAfterResumeMillis After a touch or pause ends, a toast stays at least this long.
 */
public data class ToastQueueConfig(
    val maxVisible: Int = 3,
    val maxQueued: Int = 32,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.RestartTimer,
    val timeouts: ToastTimeoutPolicy = ToastTimeoutPolicy(),
    val minRemainingAfterResumeMillis: Long = 1_500,
) {
    init {
        require(maxVisible >= 1) { "maxVisible must be at least 1, was $maxVisible." }
        require(maxQueued >= 0) { "maxQueued must not be negative, was $maxQueued." }
        require(minRemainingAfterResumeMillis >= 0) { "minRemainingAfterResumeMillis must not be negative." }
    }
}

/** A toast on screen. [timeoutMillis] is `null` when it never times out. */
public class ActiveToast internal constructor(
    toast: Toast,
    /** When it appeared, on the queue's clock. */
    public val shownAtMillis: Long,
    timeoutMillis: Long?,
) {
    public var toast: Toast = toast
        internal set

    public var timeoutMillis: Long? = timeoutMillis
        internal set

    /** Whether its time is stopped because it is touched. */
    public var isTouched: Boolean = false
        internal set

    /** When it hides itself while its time runs. */
    internal var deadline: Long? = null

    /** The time it has left while its time is stopped. */
    internal var remaining: Long? = null

    public val id: Long get() = toast.id

    override fun toString(): String =
        "ActiveToast(id=$id, message=${toast.message}, timeout=$timeoutMillis, touched=$isTouched)"
}

/**
 * The line of toasts and their timers, without coroutines or threads: every call works on the current time of
 * [clock]. The UI layer calls [tick] when [nextDeadlineMillis] is reached and after its own waits.
 *
 * - Each edge ([ToastPosition]) shows up to [ToastQueueConfig.maxVisible] toasts; the rest wait, highest
 *   [ToastPriority] first, then oldest first. [ToastPriority.Urgent] replaces the least important visible toast.
 * - Toasts with the same [ToastRequest.dedupeKey] are merged ([ToastQueueConfig.duplicatePolicy]).
 * - Touched toasts ([touch]) and a paused queue ([pause]) keep their remaining time.
 * - Timeouts follow [ToastTimeouts] with [screenReaderEnabled] and [recommendedTimeout].
 *
 * Not thread safe: use it from one thread.
 */
public class ToastQueue(
    public val config: ToastQueueConfig = ToastQueueConfig(),
    private val clock: ToastClock,
) {
    /** Gets told about every change, to update the UI and call back the code that showed a toast. */
    public interface Listener {
        public fun onShown(toast: ActiveToast) {}

        /** A visible or waiting toast got new content ([DuplicatePolicy.Replace]). */
        public fun onReplaced(previous: Toast, current: Toast) {}

        /** A toast left the screen or the line. Called once per toast. */
        public fun onDismissed(toast: Toast, reason: ToastDismissReason) {}
    }

    public var listener: Listener? = null

    /** Whether a screen reader such as TalkBack is on. Applies to toasts shown from now on. */
    public var screenReaderEnabled: Boolean = false

    /** The platform's accessibility timeout. Applies to toasts shown from now on. */
    public var recommendedTimeout: RecommendedTimeout? = null

    private val active = mutableListOf<ActiveToast>()
    private val waiting = mutableListOf<Toast>()
    private var nextId = 1L

    /** Whether all timers are stopped, see [pause]. */
    public var isPaused: Boolean = false
        private set

    /** Toasts on screen, in the order they appeared. */
    public val visible: List<ActiveToast> get() = active.toList()

    /** Waiting toasts, in the order they will be shown. */
    public val pending: List<Toast> get() = waiting.sortedWith(showOrder)

    /** Toasts on screen at [position], in the order they appeared. */
    public fun visibleAt(position: ToastPosition): List<ActiveToast> = active.filter { it.toast.position == position }

    /** The visible or waiting toast with [id]. */
    public fun find(id: Long): Toast? = active.firstOrNull { it.id == id }?.toast ?: waiting.firstOrNull { it.id == id }

    /** Whether the toast with [id] is on screen. */
    public fun isVisible(id: Long): Boolean = active.any { it.id == id }

    /**
     * Adds a toast. It shows right away when its edge has room, otherwise it waits.
     * Returns its id, or the id of the existing toast when [request] is a duplicate.
     */
    public fun enqueue(request: ToastRequest): Long {
        request.dedupeKey?.let { key ->
            handleDuplicate(key, request)?.let { return it }
        }
        val toast = Toast(nextId++, request)
        val position = request.position
        when {
            visibleCount(position) < config.maxVisible -> show(toast)
            request.priority == ToastPriority.Urgent && preempt(position) -> show(toast)
            else -> addWaiting(toast)
        }
        return toast.id
    }

    /** Hides the visible or waiting toast with [id]. Returns `false` when there is none. */
    public fun dismiss(id: Long, reason: ToastDismissReason = ToastDismissReason.Programmatic): Boolean {
        val shown = active.firstOrNull { it.id == id }
        if (shown != null) {
            active.remove(shown)
            listener?.onDismissed(shown.toast, reason)
            promote(shown.toast.position)
            return true
        }
        val queued = waiting.firstOrNull { it.id == id } ?: return false
        waiting.remove(queued)
        listener?.onDismissed(queued, reason)
        return true
    }

    /** Hides every toast, visible and waiting. */
    public fun dismissAll(reason: ToastDismissReason = ToastDismissReason.Programmatic) {
        clearPending(reason)
        val shown = active.toList()
        active.clear()
        shown.forEach { listener?.onDismissed(it.toast, reason) }
    }

    /** Removes the waiting toasts and keeps the visible ones. */
    public fun clearPending(reason: ToastDismissReason = ToastDismissReason.Programmatic) {
        val queued = waiting.sortedWith(showOrder)
        waiting.clear()
        queued.forEach { listener?.onDismissed(it, reason) }
    }

    /** Stops the time of the toast with [id] while a finger is on it, or restarts it. */
    public fun touch(id: Long, touched: Boolean) {
        val toast = active.firstOrNull { it.id == id } ?: return
        if (toast.isTouched == touched) return
        toast.isTouched = touched
        syncTimer(toast, resuming = !touched)
    }

    /** Stops all timers, for example while the app is in the background. */
    public fun pause() {
        if (isPaused) return
        isPaused = true
        active.forEach { syncTimer(it, resuming = false) }
    }

    /** Restarts the timers stopped by [pause]. */
    public fun resume() {
        if (!isPaused) return
        isPaused = false
        active.forEach { syncTimer(it, resuming = true) }
    }

    /** The time the toast with [id] has left, or `null` when it is not visible or never times out. */
    public fun remainingMillis(id: Long): Long? {
        val toast = active.firstOrNull { it.id == id } ?: return null
        toast.deadline?.let { return (it - clock.nowMillis()).coerceAtLeast(0) }
        return toast.remaining
    }

    /** When the next toast times out, or `null` when no timer is running. */
    public fun nextDeadlineMillis(): Long? = active.mapNotNull { it.deadline }.minOrNull()

    /** Hides the toasts whose time is up and shows waiting ones in their place. Returns how many were hidden. */
    public fun tick(): Int {
        val now = clock.nowMillis()
        val expired = active.filter { toast -> toast.deadline?.let { it <= now } == true }
        expired.forEach { dismiss(it.id, ToastDismissReason.Timeout) }
        return expired.size
    }

    private fun handleDuplicate(key: String, request: ToastRequest): Long? {
        val shown = active.firstOrNull { it.toast.dedupeKey == key }
        if (shown != null) {
            when (config.duplicatePolicy) {
                DuplicatePolicy.Ignore -> Unit
                DuplicatePolicy.RestartTimer -> restartTimer(shown)
                DuplicatePolicy.Replace -> {
                    val previous = shown.toast
                    val current = Toast(previous.id, request.copy(position = previous.position))
                    shown.toast = current
                    shown.timeoutMillis = timeoutFor(request)
                    restartTimer(shown)
                    listener?.onReplaced(previous, current)
                }
            }
            return shown.id
        }
        val index = waiting.indexOfFirst { it.dedupeKey == key }
        if (index < 0) return null
        val previous = waiting[index]
        if (config.duplicatePolicy == DuplicatePolicy.Replace) {
            val current = Toast(previous.id, request.copy(position = previous.position))
            waiting[index] = current
            listener?.onReplaced(previous, current)
        }
        return previous.id
    }

    private fun show(toast: Toast) {
        val shown = ActiveToast(toast, clock.nowMillis(), timeoutFor(toast.request))
        restartTimer(shown)
        active.add(shown)
        listener?.onShown(shown)
    }

    private fun addWaiting(toast: Toast) {
        waiting.add(toast)
        if (waiting.size > config.maxQueued) {
            val dropped = waiting.sortedWith(showOrder).last()
            waiting.remove(dropped)
            listener?.onDismissed(dropped, ToastDismissReason.Dropped)
        }
    }

    /** Makes room at [position] for an urgent toast. Returns `false` when every visible toast there is urgent too. */
    private fun preempt(position: ToastPosition): Boolean {
        val victim = visibleAt(position)
            .filter { it.toast.priority < ToastPriority.Urgent }
            .minWithOrNull(compareBy<ActiveToast> { it.toast.priority }.thenBy { it.shownAtMillis }.thenBy { it.id })
            ?: return false
        active.remove(victim)
        listener?.onDismissed(victim.toast, ToastDismissReason.Preempted)
        return true
    }

    private fun promote(position: ToastPosition) {
        while (visibleCount(position) < config.maxVisible) {
            val next = waiting.filter { it.position == position }.minWithOrNull(showOrder) ?: return
            waiting.remove(next)
            show(next)
        }
    }

    private fun visibleCount(position: ToastPosition): Int = active.count { it.toast.position == position }

    private fun timeoutFor(request: ToastRequest): Long? =
        ToastTimeouts.timeoutMillis(request, config.timeouts, screenReaderEnabled, recommendedTimeout)

    private fun isRunning(toast: ActiveToast): Boolean = !isPaused && !toast.isTouched

    /** Starts the full time again, running or stopped depending on the toast's state. */
    private fun restartTimer(toast: ActiveToast) {
        val timeout = toast.timeoutMillis
        if (timeout == null) {
            toast.deadline = null
            toast.remaining = null
        } else if (isRunning(toast)) {
            toast.deadline = clock.nowMillis() + timeout
            toast.remaining = null
        } else {
            toast.deadline = null
            toast.remaining = timeout
        }
    }

    private fun syncTimer(toast: ActiveToast, resuming: Boolean) {
        val now = clock.nowMillis()
        if (isRunning(toast)) {
            val left = toast.remaining ?: return
            val minimum = if (resuming) config.minRemainingAfterResumeMillis else 0L
            toast.deadline = now + maxOf(left, minimum)
            toast.remaining = null
        } else {
            val deadline = toast.deadline ?: return
            toast.remaining = (deadline - now).coerceAtLeast(0)
            toast.deadline = null
        }
    }

    private companion object {
        /** Highest priority first, then oldest first (ids grow with time). */
        val showOrder: Comparator<Toast> = compareByDescending<Toast> { it.priority }.thenBy { it.id }
    }
}
