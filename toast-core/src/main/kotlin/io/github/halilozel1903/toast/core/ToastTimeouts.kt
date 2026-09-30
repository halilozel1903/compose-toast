package io.github.halilozel1903.toast.core

/**
 * The platform's accessibility timeout, for example Android's "Time to take action" setting.
 * It gets the computed timeout and returns the one the user asked for. Shorter results are ignored.
 */
public fun interface RecommendedTimeout {
    public fun adjust(originalMillis: Long, hasAction: Boolean): Long
}

/**
 * The rules that turn a [ToastDuration] into milliseconds.
 *
 * @property shortMillis Base time of [ToastDuration.Short].
 * @property longMillis Base time of [ToastDuration.Long].
 * @property minWithActionMillis A Short or Long toast with an action stays at least this long, so there is time to press it.
 * @property freeReadingChars Characters of title and message that the base time already covers.
 * @property millisPerExtraChar Reading time added for every character beyond [freeReadingChars].
 * @property maxReadingBonusMillis The most reading time that is added.
 * @property screenReaderFactor A Short or Long toast is shown this many times longer while a screen reader is on.
 * @property keepActionsWithScreenReader Toasts with an action do not time out while a screen reader is on,
 *   because finding the button by swiping through the screen takes a while.
 */
public data class ToastTimeoutPolicy(
    val shortMillis: Long = 4_000,
    val longMillis: Long = 7_000,
    val minWithActionMillis: Long = 8_000,
    val freeReadingChars: Int = 60,
    val millisPerExtraChar: Long = 40,
    val maxReadingBonusMillis: Long = 5_000,
    val screenReaderFactor: Double = 2.0,
    val keepActionsWithScreenReader: Boolean = true,
) {
    init {
        require(shortMillis > 0 && longMillis > 0) { "Toast durations must be positive." }
        require(minWithActionMillis >= 0 && freeReadingChars >= 0 && millisPerExtraChar >= 0 && maxReadingBonusMillis >= 0) {
            "Toast timeout settings must not be negative."
        }
        require(screenReaderFactor >= 1.0) { "screenReaderFactor must be at least 1, was $screenReaderFactor." }
    }
}

/** Computes how long a toast stays visible. */
public object ToastTimeouts {

    /** A timeout at or above this is treated as "never", which is what Android returns for untimed content. */
    public const val NEVER_THRESHOLD: Long = Long.MAX_VALUE / 2

    /**
     * The time [request] stays on screen in milliseconds, or `null` when it never times out.
     *
     * 1. [ToastDuration.Indefinite] never times out; [ToastDuration.Custom] uses its value as is.
     * 2. Short and Long get reading time for long texts, and at least [ToastTimeoutPolicy.minWithActionMillis] with an action.
     * 3. With a screen reader, toasts with an action stay (see [ToastTimeoutPolicy.keepActionsWithScreenReader])
     *    and Short and Long ones last [ToastTimeoutPolicy.screenReaderFactor] times longer.
     * 4. [recommended] can make the result longer, never shorter.
     */
    public fun timeoutMillis(
        request: ToastRequest,
        policy: ToastTimeoutPolicy = ToastTimeoutPolicy(),
        screenReaderEnabled: Boolean = false,
        recommended: RecommendedTimeout? = null,
    ): Long? {
        val hasAction = request.action != null
        var millis = when (val duration = request.duration) {
            ToastDuration.Indefinite -> return null
            is ToastDuration.Custom -> duration.millis
            ToastDuration.Short -> adjustedBase(policy.shortMillis, request, policy)
            ToastDuration.Long -> adjustedBase(policy.longMillis, request, policy)
        }
        if (screenReaderEnabled) {
            if (hasAction && policy.keepActionsWithScreenReader) return null
            if (request.duration !is ToastDuration.Custom) {
                millis = scale(millis, policy.screenReaderFactor)
            }
        }
        if (recommended != null) {
            val adjusted = recommended.adjust(millis, hasAction)
            if (adjusted >= NEVER_THRESHOLD) return null
            millis = maxOf(millis, adjusted)
        }
        return millis
    }

    /** Extra reading time for [chars] characters of text. */
    public fun readingBonusMillis(chars: Int, policy: ToastTimeoutPolicy = ToastTimeoutPolicy()): Long {
        val extra = (chars - policy.freeReadingChars).coerceAtLeast(0)
        return minOf(extra * policy.millisPerExtraChar, policy.maxReadingBonusMillis)
    }

    private fun adjustedBase(base: Long, request: ToastRequest, policy: ToastTimeoutPolicy): Long {
        val chars = request.message.length + (request.title?.length ?: 0)
        val withReading = base + readingBonusMillis(chars, policy)
        return if (request.action != null) maxOf(withReading, policy.minWithActionMillis) else withReading
    }

    private fun scale(millis: Long, factor: Double): Long {
        val scaled = millis * factor
        return if (scaled >= NEVER_THRESHOLD.toDouble()) NEVER_THRESHOLD else scaled.toLong()
    }
}
