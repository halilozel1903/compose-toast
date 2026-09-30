package io.github.halilozel1903.toast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToastQueueTest {

    private val clock = FakeClock()
    private val events = RecordingListener()

    @Test
    fun `a toast shows at once and times out on tick`() {
        val queue = queue(clock, listener = events)
        val id = queue.enqueue(request(duration = ToastDuration.Custom(3_000)))

        assertEquals(listOf(id), queue.visibleIds())
        assertEquals(listOf(id), events.shown)
        assertEquals(clock.now + 3_000, queue.nextDeadlineMillis())

        clock.advance(2_999)
        assertEquals(0, queue.tick())
        assertEquals(1, queue.remainingMillis(id))

        clock.advance(1)
        assertEquals(1, queue.tick())
        assertTrue(queue.visible.isEmpty())
        assertEquals(listOf(id to ToastDismissReason.Timeout), events.dismissed)
        assertNull(queue.nextDeadlineMillis())
    }

    @Test
    fun `ids are unique and increasing`() {
        val queue = queue(clock)
        val ids = List(5) { queue.enqueue(request()) }
        assertEquals(ids.sorted(), ids)
        assertEquals(5, ids.toSet().size)
    }

    @Test
    fun `indefinite toasts have no deadline`() {
        val queue = queue(clock, listener = events)
        val id = queue.enqueue(request(duration = ToastDuration.Indefinite))
        clock.advance(1_000_000)
        queue.tick()
        assertTrue(queue.isVisible(id))
        assertNull(queue.nextDeadlineMillis())
        assertNull(queue.remainingMillis(id))
    }

    @Test
    fun `each edge shows at most maxVisible and the rest wait`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 2), events)
        val a = queue.enqueue(request("a"))
        val b = queue.enqueue(request("b"))
        val c = queue.enqueue(request("c"))
        val top = queue.enqueue(request("top", position = ToastPosition.Top))

        assertEquals(listOf(a, b), queue.visibleIds(ToastPosition.Bottom))
        assertEquals(listOf(top), queue.visibleIds(ToastPosition.Top))
        assertEquals(listOf(c), queue.pending.map { it.id })

        queue.dismiss(a, ToastDismissReason.Swiped)
        assertEquals(listOf(b, c), queue.visibleIds(ToastPosition.Bottom))
        assertTrue(queue.pending.isEmpty())
        assertEquals(listOf(a, b, top, c), events.shown)
    }

    @Test
    fun `a promoted toast gets its full time from when it appears`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1))
        queue.enqueue(request(duration = ToastDuration.Custom(1_000)))
        val waiting = queue.enqueue(request(duration = ToastDuration.Custom(5_000)))
        clock.advance(1_000)
        queue.tick()
        assertEquals(listOf(waiting), queue.visibleIds())
        assertEquals(5_000, queue.remainingMillis(waiting))
    }

    @Test
    fun `waiting toasts show by priority, then oldest first`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1))
        val first = queue.enqueue(request("first", priority = ToastPriority.Low))
        val low = queue.enqueue(request("low", priority = ToastPriority.Low))
        val normal = queue.enqueue(request("normal"))
        val high1 = queue.enqueue(request("high1", priority = ToastPriority.High))
        val high2 = queue.enqueue(request("high2", priority = ToastPriority.High))

        assertEquals(listOf(high1, high2, normal, low), queue.pending.map { it.id })
        val order = mutableListOf(queue.visibleIds().single())
        repeat(4) {
            queue.dismiss(order.last())
            order += queue.visibleIds().single()
        }
        assertEquals(listOf(first, high1, high2, normal, low), order)
    }

    @Test
    fun `an urgent toast replaces the least important visible toast`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 2), events)
        val normal = queue.enqueue(request("normal"))
        val low = queue.enqueue(request("low", priority = ToastPriority.Low))
        val urgent = queue.enqueue(request("urgent", priority = ToastPriority.Urgent))

        assertEquals(listOf(normal, urgent), queue.visibleIds())
        assertEquals(listOf(low to ToastDismissReason.Preempted), events.dismissed)
    }

    @Test
    fun `urgent toasts replace the oldest of equally important ones and wait behind other urgent ones`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 2))
        val a = queue.enqueue(request("a"))
        clock.advance(10)
        val b = queue.enqueue(request("b"))
        val u1 = queue.enqueue(request("u1", priority = ToastPriority.Urgent))
        assertEquals(listOf(b, u1), queue.visibleIds())
        val u2 = queue.enqueue(request("u2", priority = ToastPriority.Urgent))
        assertEquals(listOf(u1, u2), queue.visibleIds())
        val u3 = queue.enqueue(request("u3", priority = ToastPriority.Urgent))
        assertEquals(listOf(u1, u2), queue.visibleIds())
        assertEquals(listOf(u3), queue.pending.map { it.id })
        assertFalse(queue.isVisible(a))
    }

    @Test
    fun `urgent toasts only preempt at their own edge`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1))
        val bottom = queue.enqueue(request("bottom"))
        val top = queue.enqueue(request("top", position = ToastPosition.Top, priority = ToastPriority.Urgent))
        assertEquals(listOf(bottom, top), queue.visibleIds())
    }

    @Test
    fun `a full line drops its least important toast`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1, maxQueued = 2), events)
        queue.enqueue(request("visible"))
        val low = queue.enqueue(request("low", priority = ToastPriority.Low))
        val normal = queue.enqueue(request("normal"))
        val high = queue.enqueue(request("high", priority = ToastPriority.High))

        assertEquals(listOf(high, normal), queue.pending.map { it.id })
        assertEquals(listOf(low to ToastDismissReason.Dropped), events.dismissed)

        val newest = queue.enqueue(request("normal2"))
        assertEquals(listOf(high, normal), queue.pending.map { it.id })
        assertEquals(newest to ToastDismissReason.Dropped, events.dismissed.last())
    }

    @Test
    fun `maxQueued zero drops everything that cannot show`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1, maxQueued = 0), events)
        queue.enqueue(request())
        val dropped = queue.enqueue(request())
        assertTrue(queue.pending.isEmpty())
        assertEquals(listOf(dropped to ToastDismissReason.Dropped), events.dismissed)
    }

    @Test
    fun `dismiss works for visible and waiting toasts and reports once`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1), events)
        val a = queue.enqueue(request("a"))
        val b = queue.enqueue(request("b"))
        val c = queue.enqueue(request("c"))

        assertTrue(queue.dismiss(c, ToastDismissReason.Closed))
        assertFalse(queue.dismiss(c))
        assertTrue(queue.dismiss(a, ToastDismissReason.ActionClicked))
        assertFalse(queue.dismiss(a))
        assertFalse(queue.dismiss(999))
        assertEquals(listOf(b), queue.visibleIds())
        assertEquals(
            listOf(c to ToastDismissReason.Closed, a to ToastDismissReason.ActionClicked),
            events.dismissed,
        )
    }

    @Test
    fun `dismissAll clears the screen and the line without promoting`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1), events)
        val a = queue.enqueue(request("a"))
        val b = queue.enqueue(request("b", priority = ToastPriority.Low))
        val c = queue.enqueue(request("c"))
        queue.dismissAll()
        assertTrue(queue.visible.isEmpty())
        assertTrue(queue.pending.isEmpty())
        assertEquals(listOf(a, c, b).sorted(), events.dismissed.map { it.first }.sorted())
        assertEquals(listOf(a), events.shown)
    }

    @Test
    fun `clearPending keeps the visible toasts`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1))
        val a = queue.enqueue(request("a"))
        queue.enqueue(request("b"))
        queue.clearPending()
        assertEquals(listOf(a), queue.visibleIds())
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `restart timer is the default for duplicates`() {
        val queue = queue(clock, listener = events)
        val id = queue.enqueue(request("Copied", duration = ToastDuration.Custom(2_000), dedupeKey = "copy"))
        clock.advance(1_500)
        val again = queue.enqueue(request("Copied", duration = ToastDuration.Custom(2_000), dedupeKey = "copy"))

        assertEquals(id, again)
        assertEquals(1, queue.visible.size)
        assertEquals(2_000, queue.remainingMillis(id))
        clock.advance(1_999)
        queue.tick()
        assertTrue(queue.isVisible(id))
    }

    @Test
    fun `ignore keeps the first toast untouched`() {
        val queue = queue(clock, ToastQueueConfig(duplicatePolicy = DuplicatePolicy.Ignore))
        val id = queue.enqueue(request("Copied", duration = ToastDuration.Custom(2_000), dedupeKey = "copy"))
        clock.advance(1_500)
        assertEquals(id, queue.enqueue(request("Copied again", dedupeKey = "copy")))
        assertEquals(500, queue.remainingMillis(id))
        assertEquals("Copied", queue.find(id)?.message)
    }

    @Test
    fun `replace swaps the content in place and keeps the edge`() {
        val queue = queue(clock, ToastQueueConfig(duplicatePolicy = DuplicatePolicy.Replace), events)
        val other = queue.enqueue(request("Other"))
        val id = queue.enqueue(request("Uploading 1 file", duration = ToastDuration.Indefinite, dedupeKey = "upload"))
        clock.advance(5_000)
        val again = queue.enqueue(
            request(
                "Uploaded 3 files",
                type = ToastType.Success,
                duration = ToastDuration.Custom(3_000),
                position = ToastPosition.Top,
                dedupeKey = "upload",
            ),
        )

        assertEquals(id, again)
        assertEquals(listOf(other, id), queue.visibleIds())
        val toast = queue.find(id)!!
        assertEquals("Uploaded 3 files", toast.message)
        assertEquals(ToastType.Success, toast.type)
        assertEquals(ToastPosition.Bottom, toast.position)
        assertEquals(3_000, queue.remainingMillis(id))
        assertEquals("Uploading 1 file", events.replaced.single().first.message)
    }

    @Test
    fun `duplicates of waiting toasts do not queue twice`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1, duplicatePolicy = DuplicatePolicy.Replace), events)
        queue.enqueue(request("visible"))
        val waiting = queue.enqueue(request("Offline", dedupeKey = "net"))
        assertEquals(waiting, queue.enqueue(request("Still offline", dedupeKey = "net")))
        assertEquals(listOf("Still offline"), queue.pending.map { it.message })
        assertEquals(1, events.replaced.size)
    }

    @Test
    fun `a dismissed key can show again as a new toast`() {
        val queue = queue(clock)
        val first = queue.enqueue(request(dedupeKey = "k"))
        queue.dismiss(first)
        val second = queue.enqueue(request(dedupeKey = "k"))
        assertTrue(second > first)
        assertTrue(queue.isVisible(second))
    }

    @Test
    fun `touching a toast stops its time and letting go restarts it`() {
        val queue = queue(clock, ToastQueueConfig(minRemainingAfterResumeMillis = 0))
        val id = queue.enqueue(request(duration = ToastDuration.Custom(4_000)))
        clock.advance(1_000)
        queue.touch(id, true)
        assertTrue(queue.visible.single().isTouched)
        assertNull(queue.nextDeadlineMillis())

        clock.advance(60_000)
        assertEquals(0, queue.tick())
        assertEquals(3_000, queue.remainingMillis(id))

        queue.touch(id, false)
        assertEquals(clock.now + 3_000, queue.nextDeadlineMillis())
        clock.advance(3_000)
        assertEquals(1, queue.tick())
    }

    @Test
    fun `a toast stays a moment after it is let go`() {
        val queue = queue(clock)
        val id = queue.enqueue(request(duration = ToastDuration.Custom(1_000)))
        clock.advance(900)
        queue.touch(id, true)
        queue.touch(id, false)
        assertEquals(1_500, queue.remainingMillis(id))
    }

    @Test
    fun `touch is ignored for unknown toasts and repeated calls`() {
        val queue = queue(clock, ToastQueueConfig(minRemainingAfterResumeMillis = 0))
        val id = queue.enqueue(request(duration = ToastDuration.Custom(1_000)))
        queue.touch(42, true)
        queue.touch(id, false)
        assertEquals(1_000, queue.remainingMillis(id))
        queue.touch(id, true)
        clock.advance(300)
        queue.touch(id, true)
        assertEquals(1_000, queue.remainingMillis(id))
    }

    @Test
    fun `pause stops every timer and resume restarts them`() {
        val queue = queue(clock, ToastQueueConfig(minRemainingAfterResumeMillis = 0))
        val a = queue.enqueue(request(duration = ToastDuration.Custom(2_000)))
        val b = queue.enqueue(request(duration = ToastDuration.Custom(5_000)))
        clock.advance(1_000)
        queue.pause()
        assertTrue(queue.isPaused)
        clock.advance(10_000)
        queue.tick()
        assertEquals(listOf(a, b), queue.visibleIds())

        queue.resume()
        assertEquals(clock.now + 1_000, queue.nextDeadlineMillis())
        clock.advance(1_000)
        queue.tick()
        assertEquals(listOf(b), queue.visibleIds())
        assertEquals(3_000, queue.remainingMillis(b))
    }

    @Test
    fun `a paused toast stays paused while touched even when the queue resumes`() {
        val queue = queue(clock, ToastQueueConfig(minRemainingAfterResumeMillis = 0))
        val id = queue.enqueue(request(duration = ToastDuration.Custom(2_000)))
        queue.pause()
        queue.touch(id, true)
        queue.resume()
        assertNull(queue.nextDeadlineMillis())
        queue.touch(id, false)
        assertEquals(clock.now + 2_000, queue.nextDeadlineMillis())
    }

    @Test
    fun `toasts shown while paused wait with their full time`() {
        val queue = queue(clock, ToastQueueConfig(minRemainingAfterResumeMillis = 0))
        queue.pause()
        val id = queue.enqueue(request(duration = ToastDuration.Custom(2_000)))
        clock.advance(5_000)
        queue.tick()
        assertEquals(2_000, queue.remainingMillis(id))
        queue.resume()
        assertEquals(clock.now + 2_000, queue.nextDeadlineMillis())
    }

    @Test
    fun `accessibility settings apply to toasts shown later`() {
        val queue = queue(clock)
        val before = queue.enqueue(request(duration = ToastDuration.Short))
        queue.screenReaderEnabled = true
        val after = queue.enqueue(request(duration = ToastDuration.Short))
        val undo = queue.enqueue(request(duration = ToastDuration.Short, action = ToastAction("Undo")))
        assertEquals(4_000, queue.visible.first { it.id == before }.timeoutMillis)
        assertEquals(8_000, queue.visible.first { it.id == after }.timeoutMillis)
        assertNull(queue.visible.first { it.id == undo }.timeoutMillis)

        queue.recommendedTimeout = RecommendedTimeout { original, _ -> original * 5 }
        queue.dismissAll()
        val slow = queue.enqueue(request(duration = ToastDuration.Short))
        assertEquals(40_000, queue.remainingMillis(slow))
    }

    @Test
    fun `timeouts across several toasts fire in deadline order`() {
        val queue = queue(clock, listener = events)
        val slow = queue.enqueue(request(duration = ToastDuration.Custom(3_000)))
        val fast = queue.enqueue(request(duration = ToastDuration.Custom(1_000)))
        assertEquals(clock.now + 1_000, queue.nextDeadlineMillis())
        clock.advance(1_000)
        queue.tick()
        assertEquals(clock.now + 2_000, queue.nextDeadlineMillis())
        clock.advance(5_000)
        queue.tick()
        assertEquals(listOf(fast, slow), events.dismissed.map { it.first })
    }

    @Test
    fun `listener changes during callbacks do not break the queue`() {
        val queue = queue(clock, ToastQueueConfig(maxVisible = 1))
        queue.listener = object : ToastQueue.Listener {
            override fun onDismissed(toast: Toast, reason: ToastDismissReason) {
                if (toast.message == "chain") queue.enqueue(request("follow up"))
            }
        }
        val id = queue.enqueue(request("chain"))
        queue.dismiss(id)
        assertEquals(listOf("follow up"), queue.visible.map { it.toast.message })
    }

    @Test
    fun `config rejects impossible values`() {
        assertFailsWith<IllegalArgumentException> { ToastQueueConfig(maxVisible = 0) }
        assertFailsWith<IllegalArgumentException> { ToastQueueConfig(maxQueued = -1) }
        assertFailsWith<IllegalArgumentException> { ToastQueueConfig(minRemainingAfterResumeMillis = -1) }
    }
}
