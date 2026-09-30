package io.github.halilozel1903.toast.core

/**
 * The words screen readers use for a toast. The icon and color show the type, so the type is spoken too.
 * Pass your own [labels] to localize them.
 */
public object ToastAnnouncement {

    /** Spoken names of the types. [ToastType.Custom] has none by default. */
    public data class Labels(
        val success: String = "Success",
        val error: String = "Error",
        val info: String = "Info",
        val warning: String = "Warning",
        val custom: String = "",
        /** Added when the toast has an action; `%s` is the action label. */
        val actionHint: String = "Action available: %s",
    )

    /** The spoken name of [type], or an empty text. */
    public fun typeLabel(type: ToastType, labels: Labels = Labels()): String = when (type) {
        ToastType.Success -> labels.success
        ToastType.Error -> labels.error
        ToastType.Info -> labels.info
        ToastType.Warning -> labels.warning
        ToastType.Custom -> labels.custom
    }

    /** For example "Error. Upload failed. The file is too large. Action available: Retry". */
    public fun text(toast: ToastRequest, labels: Labels = Labels()): String = buildList<String> {
        typeLabel(toast.type, labels).takeIf { it.isNotBlank() }?.let { add(it) }
        toast.title?.takeIf { it.isNotBlank() }?.let { add(it) }
        add(toast.message)
        toast.action?.let { add(labels.actionHint.replace("%s", it.label)) }
    }.joinToString(separator = ". ") { it.trim().trimEnd('.') }

    /** Errors interrupt the screen reader, everything else waits until it is done speaking. */
    public fun isAssertive(type: ToastType): Boolean = type == ToastType.Error
}
