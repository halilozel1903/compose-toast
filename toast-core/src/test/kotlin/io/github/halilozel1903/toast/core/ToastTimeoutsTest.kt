package io.github.halilozel1903.toast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ToastTimeoutsTest {

    private val policy = ToastTimeoutPolicy()

    @Test
    fun `short and long use the policy base times`() {
        assertEquals(4_000, ToastTimeouts.timeoutMillis(ToastRequest("Saved", duration = ToastDuration.Short)))
        assertEquals(7_000, ToastTimeouts.timeoutMillis(ToastRequest("Saved", duration = ToastDuration.Long)))
    }

    @Test
    fun `indefinite never times out`() {
        assertNull(ToastTimeouts.timeoutMillis(ToastRequest("Offline", duration = ToastDuration.Indefinite)))
        assertNull(
            ToastTimeouts.timeoutMillis(
                ToastRequest("Offline", duration = ToastDuration.Indefinite),
                recommended = { original, _ -> original * 3 },
            ),
        )
    }

    @Test
    fun `custom durations are used as is`() {
        assertEquals(2_500, ToastTimeouts.timeoutMillis(ToastRequest("x".repeat(500), duration = ToastDuration.Custom(2_500))))
        assertEquals(
            2_500,
            ToastTimeouts.timeoutMillis(
                ToastRequest("Undo?", duration = ToastDuration.Custom(2_500), action = ToastAction("Undo")),
            ),
        )
    }

    @Test
    fun `custom durations must be positive`() {
        assertFailsWith<IllegalArgumentException> { ToastDuration.Custom(0) }
        assertFailsWith<IllegalArgumentException> { ToastDuration.Custom(-5) }
    }

    @Test
    fun `long texts get reading time up to a cap`() {
        assertEquals(0, ToastTimeouts.readingBonusMillis(60, policy))
        assertEquals(40, ToastTimeouts.readingBonusMillis(61, policy))
        assertEquals(4_000, ToastTimeouts.readingBonusMillis(160, policy))
        assertEquals(5_000, ToastTimeouts.readingBonusMillis(10_000, policy))
        // Title and message both count: 50 + 30 characters is 20 over the free 60.
        val request = ToastRequest("m".repeat(50), title = "t".repeat(30))
        assertEquals(4_800, ToastTimeouts.timeoutMillis(request))
    }

    @Test
    fun `toasts with an action stay long enough to press it`() {
        val undo = ToastRequest("Deleted", action = ToastAction("Undo"))
        assertEquals(8_000, ToastTimeouts.timeoutMillis(undo))
        assertEquals(8_000, ToastTimeouts.timeoutMillis(undo.copy(duration = ToastDuration.Long)))
        val relaxed = policy.copy(minWithActionMillis = 5_000)
        assertEquals(7_000, ToastTimeouts.timeoutMillis(undo.copy(duration = ToastDuration.Long), relaxed))
    }

    @Test
    fun `screen readers double the time and keep toasts with actions`() {
        assertEquals(8_000, ToastTimeouts.timeoutMillis(ToastRequest("Saved"), screenReaderEnabled = true))
        assertNull(
            ToastTimeouts.timeoutMillis(ToastRequest("Deleted", action = ToastAction("Undo")), screenReaderEnabled = true),
        )
        val keepTiming = policy.copy(keepActionsWithScreenReader = false)
        assertEquals(
            16_000,
            ToastTimeouts.timeoutMillis(ToastRequest("Deleted", action = ToastAction("Undo")), keepTiming, screenReaderEnabled = true),
        )
        // Custom durations are what the app asked for; only the platform setting changes them.
        assertEquals(
            1_000,
            ToastTimeouts.timeoutMillis(ToastRequest("Tick", duration = ToastDuration.Custom(1_000)), screenReaderEnabled = true),
        )
    }

    @Test
    fun `the platform recommendation can only make a toast longer`() {
        val request = ToastRequest("Saved", action = ToastAction("View"))
        var seenAction = false
        val longer = ToastTimeouts.timeoutMillis(request, recommended = { original, hasAction ->
            seenAction = hasAction
            original + 10_000
        })
        assertEquals(18_000, longer)
        assertEquals(true, seenAction)
        assertEquals(4_000, ToastTimeouts.timeoutMillis(ToastRequest("Saved"), recommended = { _, _ -> 10 }))
        assertNull(ToastTimeouts.timeoutMillis(ToastRequest("Saved"), recommended = { _, _ -> Long.MAX_VALUE }))
    }

    @Test
    fun `policy rejects impossible values`() {
        assertFailsWith<IllegalArgumentException> { ToastTimeoutPolicy(shortMillis = 0) }
        assertFailsWith<IllegalArgumentException> { ToastTimeoutPolicy(millisPerExtraChar = -1) }
        assertFailsWith<IllegalArgumentException> { ToastTimeoutPolicy(screenReaderFactor = 0.5) }
    }
}
