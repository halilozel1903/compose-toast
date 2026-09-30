package io.github.halilozel1903.toast.core

import kotlin.math.abs
import kotlin.math.sign

/** The decisions behind swipe to dismiss, in pixels and pixels per second. */
public object ToastSwipe {

    /** Share of the toast's width it must be dragged to be dismissed on release. */
    public const val DEFAULT_DISTANCE_FRACTION: Float = 0.4f

    /**
     * Whether a drag released at [offset] with [velocity] dismisses the toast: it was dragged far enough,
     * or flung fast enough in the direction it was dragged.
     */
    public fun shouldDismiss(
        offset: Float,
        width: Float,
        velocity: Float,
        velocityThreshold: Float,
        distanceFraction: Float = DEFAULT_DISTANCE_FRACTION,
    ): Boolean {
        if (width <= 0f || offset.isNaN() || velocity.isNaN()) return false
        if (abs(offset) >= width * distanceFraction) return true
        return offset != 0f && abs(velocity) >= velocityThreshold && sign(velocity) == sign(offset)
    }

    /** Where the toast settles: `width` or `-width` to leave the screen, 0 to snap back. */
    public fun settleTarget(offset: Float, width: Float, dismiss: Boolean): Float = when {
        !dismiss -> 0f
        offset < 0f -> -width
        else -> width
    }

    /** Opacity while dragging: fully opaque at rest, fading out towards the edge, never below [minAlpha]. */
    public fun alpha(offset: Float, width: Float, minAlpha: Float = 0.2f): Float {
        if (width <= 0f) return 1f
        val progress = (abs(offset) / width).coerceIn(0f, 1f)
        return (1f - progress).coerceAtLeast(minAlpha)
    }
}
