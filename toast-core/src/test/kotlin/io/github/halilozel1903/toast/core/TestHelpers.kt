package io.github.halilozel1903.toast.core

/** A clock that only moves when the test says so. */
class FakeClock(var now: Long = 1_000L) : ToastClock {
    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }
}

/** Records everything a [ToastQueue] reports. */
class RecordingListener : ToastQueue.Listener {
    val shown = mutableListOf<Long>()
    val dismissed = mutableListOf<Pair<Long, ToastDismissReason>>()
    val replaced = mutableListOf<Pair<Toast, Toast>>()

    override fun onShown(toast: ActiveToast) {
        shown += toast.id
    }

    override fun onReplaced(previous: Toast, current: Toast) {
        replaced += previous to current
    }

    override fun onDismissed(toast: Toast, reason: ToastDismissReason) {
        dismissed += toast.id to reason
    }
}

fun queue(
    clock: FakeClock,
    config: ToastQueueConfig = ToastQueueConfig(),
    listener: RecordingListener = RecordingListener(),
): ToastQueue = ToastQueue(config, clock).also { it.listener = listener }

fun request(
    message: String = "Saved",
    duration: ToastDuration = ToastDuration.Custom(1_000),
    position: ToastPosition = ToastPosition.Bottom,
    priority: ToastPriority = ToastPriority.Normal,
    dedupeKey: String? = null,
    type: ToastType = ToastType.Info,
    action: ToastAction? = null,
): ToastRequest = ToastRequest(
    message = message,
    type = type,
    action = action,
    duration = duration,
    position = position,
    priority = priority,
    dedupeKey = dedupeKey,
)

fun ToastQueue.visibleIds(position: ToastPosition? = null): List<Long> =
    (if (position == null) visible else visibleAt(position)).map { it.id }
