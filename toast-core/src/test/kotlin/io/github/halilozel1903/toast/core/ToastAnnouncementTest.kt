package io.github.halilozel1903.toast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToastAnnouncementTest {

    @Test
    fun `announcement names the type, title, message and action`() {
        val request = ToastRequest(
            message = "The file is too large.",
            type = ToastType.Error,
            title = "Upload failed",
            action = ToastAction("Retry"),
        )
        assertEquals("Error. Upload failed. The file is too large. Action available: Retry", ToastAnnouncement.text(request))
    }

    @Test
    fun `custom toasts have no type label by default`() {
        assertEquals("New message from Ada", ToastAnnouncement.text(ToastRequest("New message from Ada", type = ToastType.Custom)))
        assertEquals("Success. Saved", ToastAnnouncement.text(ToastRequest("Saved", type = ToastType.Success, title = " ")))
    }

    @Test
    fun `labels can be localized`() {
        val turkish = ToastAnnouncement.Labels(warning = "Uyarı", actionHint = "Eylem: %s")
        val request = ToastRequest("Pil zayıf", type = ToastType.Warning, action = ToastAction("Ayarlar"))
        assertEquals("Uyarı. Pil zayıf. Eylem: Ayarlar", ToastAnnouncement.text(request, turkish))
    }

    @Test
    fun `only errors are assertive`() {
        assertTrue(ToastAnnouncement.isAssertive(ToastType.Error))
        ToastType.entries.filter { it != ToastType.Error }.forEach { assertFalse(ToastAnnouncement.isAssertive(it)) }
    }

    @Test
    fun `actions need a label`() {
        assertFailsWith<IllegalArgumentException> { ToastAction(" ") }
    }

    @Test
    fun `toast exposes its request`() {
        val toast = Toast(7, ToastRequest("Hi", type = ToastType.Success, title = "T", position = ToastPosition.Top, dedupeKey = "k"))
        assertEquals("Hi", toast.message)
        assertEquals(ToastType.Success, toast.type)
        assertEquals("T", toast.title)
        assertEquals(ToastPosition.Top, toast.position)
        assertEquals("k", toast.dedupeKey)
        assertEquals(ToastDuration.Short, toast.duration)
        assertEquals(ToastPriority.Normal, toast.priority)
    }
}
