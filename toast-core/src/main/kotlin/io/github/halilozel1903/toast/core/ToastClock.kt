package io.github.halilozel1903.toast.core

/**
 * The time source of a [ToastQueue], in milliseconds. Only differences matter, so any monotonic clock works
 * (`SystemClock.elapsedRealtime()` on Android). Tests pass a clock they move by hand.
 */
public fun interface ToastClock {
    public fun nowMillis(): Long
}
