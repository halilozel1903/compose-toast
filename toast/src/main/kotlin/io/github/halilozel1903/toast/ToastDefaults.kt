package io.github.halilozel1903.toast

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.toast.core.ToastType

/** How toasts are colored. */
public enum class ToastStyle {
    /** The theme's surface with a colored icon badge. Calm, fits any screen. The default. */
    Tonal,

    /** Filled with the type's color. Louder, for apps that want toasts to stand out. */
    Filled,
}

/**
 * The colors of one toast.
 *
 * @property container Background.
 * @property content Title and message.
 * @property secondaryContent Message when there is a title, and the close button.
 * @property accent Icon and action button.
 * @property iconBackground The circle behind the icon.
 */
@Immutable
public class ToastColors(
    public val container: Color,
    public val content: Color,
    public val secondaryContent: Color,
    public val accent: Color,
    public val iconBackground: Color,
) {
    public fun copy(
        container: Color = this.container,
        content: Color = this.content,
        secondaryContent: Color = this.secondaryContent,
        accent: Color = this.accent,
        iconBackground: Color = this.iconBackground,
    ): ToastColors = ToastColors(container, content, secondaryContent, accent, iconBackground)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToastColors) return false
        return container == other.container && content == other.content && secondaryContent == other.secondaryContent &&
            accent == other.accent && iconBackground == other.iconBackground
    }

    override fun hashCode(): Int {
        var result = container.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + secondaryContent.hashCode()
        result = 31 * result + accent.hashCode()
        result = 31 * result + iconBackground.hashCode()
        return result
    }
}

/** Defaults of [ToastHost] and [ToastCard]. */
public object ToastDefaults {

    /** Toasts never get wider than this, so they stay readable on tablets and in landscape. */
    public val MaxWidth: Dp = 560.dp

    /** Space between stacked toasts. */
    public val Spacing: Dp = 8.dp

    /** The theme's large shape (16 dp corners by default). */
    public val shape: Shape
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes.large

    /** The icon of [type]. */
    public fun icon(type: ToastType): ImageVector = when (type) {
        ToastType.Success -> ToastIcons.Success
        ToastType.Error -> ToastIcons.Error
        ToastType.Info -> ToastIcons.Info
        ToastType.Warning -> ToastIcons.Warning
        ToastType.Custom -> ToastIcons.Notification
    }

    /**
     * The main color of [type] for the current theme: green, the theme's error color, blue, amber, or the
     * theme's primary color for [ToastType.Custom]. Light and dark variants are picked from the theme's surface.
     */
    @Composable
    @ReadOnlyComposable
    public fun accentColor(type: ToastType): Color {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.surface.luminance() < 0.5f
        return when (type) {
            ToastType.Success -> if (dark) Color(0xFF7DD99A) else Color(0xFF1B7F3B)
            ToastType.Error -> scheme.error
            ToastType.Info -> if (dark) Color(0xFF8AB4F8) else Color(0xFF1A63D6)
            ToastType.Warning -> if (dark) Color(0xFFF6C35B) else Color(0xFF9A5B00)
            ToastType.Custom -> scheme.primary
        }
    }

    /** Colors of a toast of [type] in [style], from the Material 3 theme. */
    @Composable
    @ReadOnlyComposable
    public fun colors(type: ToastType, style: ToastStyle = ToastStyle.Tonal): ToastColors {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.surface.luminance() < 0.5f
        val accent = accentColor(type)
        return when (style) {
            ToastStyle.Tonal -> ToastColors(
                container = scheme.surfaceContainerHigh,
                content = scheme.onSurface,
                secondaryContent = scheme.onSurfaceVariant,
                accent = accent,
                iconBackground = accent.copy(alpha = if (dark) 0.2f else 0.12f),
            )
            ToastStyle.Filled -> {
                // Light themes: the strong color with white text. Dark themes: a deep container with the light color.
                val container = if (dark) filledDarkContainer(type, scheme.errorContainer, scheme.primaryContainer) else accent
                val content = if (dark) accent.blend(Color.White, 0.55f) else Color.White
                ToastColors(
                    container = container,
                    content = content,
                    secondaryContent = content.copy(alpha = 0.85f),
                    accent = if (dark) accent else Color.White,
                    iconBackground = (if (dark) accent else Color.White).copy(alpha = 0.18f),
                )
            }
        }
    }

    private fun filledDarkContainer(type: ToastType, errorContainer: Color, primaryContainer: Color): Color = when (type) {
        ToastType.Success -> Color(0xFF1C4A2B)
        ToastType.Error -> errorContainer
        ToastType.Info -> Color(0xFF1B3A66)
        ToastType.Warning -> Color(0xFF55390A)
        ToastType.Custom -> primaryContainer
    }

    private fun Color.blend(other: Color, fraction: Float): Color = Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = 1f,
    )
}
