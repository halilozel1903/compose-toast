package io.github.halilozel1903.toast.core

/** What a toast is about. It picks the default icon, colors and how urgently TalkBack announces it. */
public enum class ToastType {
    Success,
    Error,
    Info,
    Warning,

    /** Your own style: the theme's primary color and an icon you pass. */
    Custom,
}

/** The screen edge a toast is shown at. */
public enum class ToastPosition {
    Top,
    Bottom,
}

/**
 * How important a toast is. Waiting toasts are shown highest priority first, then oldest first.
 * An [Urgent] toast does not wait: when its edge is full it replaces the least important visible toast.
 */
public enum class ToastPriority {
    Low,
    Normal,
    High,
    Urgent,
}

/** How long a toast stays on screen before it hides itself. */
public sealed interface ToastDuration {
    /** About 4 seconds, plus reading time for long messages. */
    public data object Short : ToastDuration

    /** About 7 seconds, plus reading time for long messages. */
    public data object Long : ToastDuration

    /** Stays until it is swiped away, closed, its action is used or it is dismissed in code. */
    public data object Indefinite : ToastDuration

    /** Exactly [millis] milliseconds (accessibility settings can still make it longer). */
    public data class Custom(val millis: kotlin.Long) : ToastDuration {
        init {
            require(millis > 0) { "A custom toast duration must be positive, was $millis ms." }
        }
    }
}

/** A button on the toast, such as "Undo" or "Retry". */
public data class ToastAction(val label: String) {
    init {
        require(label.isNotBlank()) { "A toast action needs a label." }
    }
}

/** Why a toast went away. */
public enum class ToastDismissReason {
    /** Its duration ran out. */
    Timeout,

    /** The user swiped it away. */
    Swiped,

    /** The user pressed its action button. */
    ActionClicked,

    /** The user pressed its close button or used the TalkBack "Dismiss" action. */
    Closed,

    /** `dismiss` or `dismissAll` was called in code. */
    Programmatic,

    /** An urgent toast needed its place. */
    Preempted,

    /** It never showed: the waiting line was full and it was the least important toast in it. */
    Dropped,
}

/**
 * Everything needed to show a toast. Pass it to [ToastQueue.enqueue].
 *
 * @property message The main text.
 * @property title An optional bold first line.
 * @property dedupeKey Toasts with the same key are treated as duplicates, see [DuplicatePolicy].
 * @property swipeToDismiss Whether the user may swipe the toast away.
 * @property showCloseButton Whether the toast has a close button.
 * @property payload Anything the UI layer wants to attach (an icon, callbacks). The queue never reads it.
 */
public data class ToastRequest(
    val message: String,
    val type: ToastType = ToastType.Info,
    val title: String? = null,
    val action: ToastAction? = null,
    val duration: ToastDuration = ToastDuration.Short,
    val position: ToastPosition = ToastPosition.Bottom,
    val priority: ToastPriority = ToastPriority.Normal,
    val dedupeKey: String? = null,
    val swipeToDismiss: Boolean = true,
    val showCloseButton: Boolean = false,
    val payload: Any? = null,
)

/** A toast the [ToastQueue] accepted, with its unique [id]. */
public data class Toast(val id: Long, val request: ToastRequest) {
    val message: String get() = request.message
    val type: ToastType get() = request.type
    val title: String? get() = request.title
    val action: ToastAction? get() = request.action
    val duration: ToastDuration get() = request.duration
    val position: ToastPosition get() = request.position
    val priority: ToastPriority get() = request.priority
    val dedupeKey: String? get() = request.dedupeKey
}
