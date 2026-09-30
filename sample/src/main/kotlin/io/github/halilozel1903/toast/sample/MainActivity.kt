package io.github.halilozel1903.toast.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Taps can't be timed reliably through adb, so `scripts/screenshots.sh` starts the app with `--es scene <scene>`
 * to show a fixed set of toasts that never time out:
 *
 * - `styles`: success, info, warning and error toasts stacked at the bottom
 * - `top`: filled toasts at the top of the screen
 * - `action`: toasts with Undo and Retry buttons and a close button
 * - `tablet`: toasts at both edges over the two pane tablet layout
 *
 * Without the extra the app is a playground with a button for every kind of toast.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = Scene.from(intent.getStringExtra(EXTRA_SCENE))
        setContent {
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(
                    primary = Color(0xFFC5BCFF),
                    onPrimary = Color(0xFF2A1A7A),
                    primaryContainer = Color(0xFF3F2F9E),
                    onPrimaryContainer = Color(0xFFE6E0FF),
                    secondaryContainer = Color(0xFF34304A),
                    onSecondaryContainer = Color(0xFFE4DFF9),
                    background = Color(0xFF111018),
                    surface = Color(0xFF111018),
                    surfaceContainerLow = Color(0xFF1A1823),
                    surfaceContainer = Color(0xFF1E1C28),
                    surfaceContainerHigh = Color(0xFF292633),
                    outlineVariant = Color(0xFF3D3A4A),
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF5646D8),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE6E0FF),
                    onPrimaryContainer = Color(0xFF1C0F6B),
                    secondaryContainer = Color(0xFFE7E2F7),
                    onSecondaryContainer = Color(0xFF1D1A2C),
                    background = Color(0xFFF7F5FB),
                    surface = Color(0xFFF7F5FB),
                    surfaceContainerLow = Color(0xFFFFFFFF),
                    surfaceContainer = Color(0xFFF1EEF8),
                    surfaceContainerHigh = Color(0xFFFFFFFF),
                    outlineVariant = Color(0xFFDCD8E6),
                )
            }
            MaterialTheme(colorScheme = colors) {
                // The Surface makes text default to onBackground, so it stays readable in dark mode.
                Surface(color = MaterialTheme.colorScheme.background) {
                    SampleApp(scene)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
    }
}
