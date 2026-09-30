package io.github.halilozel1903.toast.sample

import io.github.halilozel1903.toast.ToastState
import io.github.halilozel1903.toast.ToastStyle
import io.github.halilozel1903.toast.core.ToastDuration
import io.github.halilozel1903.toast.core.ToastPosition
import io.github.halilozel1903.toast.core.ToastType

/** Fixed screenshot setups. Every toast is indefinite, so nothing times out before the capture. */
enum class Scene(val key: String, val position: ToastPosition, val style: ToastStyle) {
    Styles("styles", ToastPosition.Bottom, ToastStyle.Tonal),
    TopEdge("top", ToastPosition.Top, ToastStyle.Filled),
    Actions("action", ToastPosition.Bottom, ToastStyle.Tonal),
    Tablet("tablet", ToastPosition.Bottom, ToastStyle.Tonal),
    ;

    companion object {
        fun from(key: String?): Scene? = entries.firstOrNull { it.key == key }
    }
}

private val Forever = ToastDuration.Indefinite

fun ToastState.showScene(scene: Scene) {
    when (scene) {
        Scene.Styles -> {
            show("€48.00 from Ada Lovelace", ToastType.Success, title = "Payment received", duration = Forever)
            show("Your photos are backing up in the background.", ToastType.Info, duration = Forever)
            show("1.2 GB left on this device.", ToastType.Warning, title = "Storage almost full", duration = Forever)
            show(
                "Check your connection and try again.",
                ToastType.Error,
                title = "Upload failed",
                actionLabel = "Retry",
                duration = Forever,
            )
        }
        Scene.TopEdge -> {
            val top = ToastPosition.Top
            show("12 of 40 notes updated", ToastType.Info, title = "Syncing", duration = Forever, position = top)
            show("Settings saved", ToastType.Success, duration = Forever, position = top)
            show(
                "Ada sent you a photo",
                ToastType.Custom,
                actionLabel = "View",
                duration = Forever,
                position = top,
                icon = SampleIcons.Star,
            )
        }
        Scene.Actions -> {
            show("You're offline. Changes will sync later.", ToastType.Warning, duration = Forever, showCloseButton = true)
            show("Couldn't send the message.", ToastType.Error, actionLabel = "Retry", duration = Forever)
            show("Conversation archived", ToastType.Info, actionLabel = "Undo", duration = Forever, showCloseButton = true)
        }
        Scene.Tablet -> {
            show(
                "12 of 40 notes updated",
                ToastType.Info,
                title = "Syncing",
                duration = Forever,
                position = ToastPosition.Top,
            )
            show("€48.00 from Ada Lovelace", ToastType.Success, title = "Payment received", duration = Forever)
            show("Conversation archived", ToastType.Info, actionLabel = "Undo", duration = Forever, showCloseButton = true)
        }
    }
}
