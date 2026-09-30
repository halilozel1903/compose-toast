package io.github.halilozel1903.toast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToastSwipeTest {

    @Test
    fun `far enough dismisses in either direction`() {
        assertTrue(ToastSwipe.shouldDismiss(offset = 160f, width = 400f, velocity = 0f, velocityThreshold = 1_000f))
        assertTrue(ToastSwipe.shouldDismiss(offset = -170f, width = 400f, velocity = 0f, velocityThreshold = 1_000f))
        assertFalse(ToastSwipe.shouldDismiss(offset = 159f, width = 400f, velocity = 0f, velocityThreshold = 1_000f))
    }

    @Test
    fun `a fling dismisses only in the drag direction`() {
        assertTrue(ToastSwipe.shouldDismiss(offset = 20f, width = 400f, velocity = 1_500f, velocityThreshold = 1_000f))
        assertTrue(ToastSwipe.shouldDismiss(offset = -20f, width = 400f, velocity = -1_500f, velocityThreshold = 1_000f))
        assertFalse(ToastSwipe.shouldDismiss(offset = 20f, width = 400f, velocity = -1_500f, velocityThreshold = 1_000f))
        assertFalse(ToastSwipe.shouldDismiss(offset = 20f, width = 400f, velocity = 900f, velocityThreshold = 1_000f))
        assertFalse(ToastSwipe.shouldDismiss(offset = 0f, width = 400f, velocity = 5_000f, velocityThreshold = 1_000f))
    }

    @Test
    fun `unmeasured or broken input never dismisses`() {
        assertFalse(ToastSwipe.shouldDismiss(offset = 100f, width = 0f, velocity = 0f, velocityThreshold = 1f))
        assertFalse(ToastSwipe.shouldDismiss(offset = Float.NaN, width = 400f, velocity = 0f, velocityThreshold = 1f))
    }

    @Test
    fun `settle target leaves on the dragged side or snaps back`() {
        assertEquals(400f, ToastSwipe.settleTarget(offset = 200f, width = 400f, dismiss = true))
        assertEquals(-400f, ToastSwipe.settleTarget(offset = -200f, width = 400f, dismiss = true))
        assertEquals(0f, ToastSwipe.settleTarget(offset = 200f, width = 400f, dismiss = false))
    }

    @Test
    fun `alpha fades with the drag and keeps a floor`() {
        assertEquals(1f, ToastSwipe.alpha(0f, 400f))
        assertEquals(0.5f, ToastSwipe.alpha(-200f, 400f))
        assertEquals(0.2f, ToastSwipe.alpha(1_000f, 400f))
        assertEquals(1f, ToastSwipe.alpha(100f, 0f))
    }
}
