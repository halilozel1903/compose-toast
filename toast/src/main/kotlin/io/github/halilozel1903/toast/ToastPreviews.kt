package io.github.halilozel1903.toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.toast.core.ToastType

@Composable
private fun ToastGallery(style: ToastStyle) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToastCard("Payment received", type = ToastType.Success, style = style)
        ToastCard(
            "The file is larger than 25 MB.",
            type = ToastType.Error,
            title = "Upload failed",
            style = style,
            actionLabel = "Retry",
        )
        ToastCard("A new version is available.", type = ToastType.Info, style = style, onClose = {})
        ToastCard("Battery below 15 %", type = ToastType.Warning, style = style)
        ToastCard("Ada sent you a photo", type = ToastType.Custom, style = style, actionLabel = "View")
    }
}

@Preview(name = "Tonal", widthDp = 400)
@Composable
private fun TonalPreview() {
    MaterialTheme {
        Surface { ToastGallery(ToastStyle.Tonal) }
    }
}

@Preview(name = "Filled", widthDp = 400)
@Composable
private fun FilledPreview() {
    MaterialTheme {
        Surface { ToastGallery(ToastStyle.Filled) }
    }
}

@Preview(name = "Dark", widthDp = 400)
@Composable
private fun DarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface { ToastGallery(ToastStyle.Tonal) }
    }
}
