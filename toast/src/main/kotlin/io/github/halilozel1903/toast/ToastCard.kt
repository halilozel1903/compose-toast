package io.github.halilozel1903.toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.toast.core.ToastType

/**
 * The look of a toast without any behavior: an icon badge, an optional title, the message, an optional action
 * button and an optional close button. [ToastHost] draws it; use it directly to build your own toast content or
 * to show a toast in a preview.
 *
 * @param message The main text.
 * @param type Picks the default [icon] and [colors].
 * @param title An optional bold first line.
 * @param icon The icon in the badge; `null` hides the badge.
 * @param actionLabel Text of the action button; `null` hides it.
 * @param onAction Called by the action button.
 * @param onClose Called by the close button; `null` hides it.
 * @param closeLabel What TalkBack says for the close button.
 */
@Composable
public fun ToastCard(
    message: String,
    modifier: Modifier = Modifier,
    type: ToastType = ToastType.Info,
    title: String? = null,
    icon: ImageVector? = ToastDefaults.icon(type),
    style: ToastStyle = ToastStyle.Tonal,
    colors: ToastColors = ToastDefaults.colors(type, style),
    shape: Shape = ToastDefaults.shape,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    closeLabel: String = "Dismiss",
) {
    val darkContainer = colors.container.luminance() < 0.5f
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        shadowElevation = if (darkContainer) 2.dp else 6.dp,
        border = if (darkContainer && style == ToastStyle.Tonal) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 14.dp, end = if (actionLabel != null || onClose != null) 6.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(colors.iconBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title.isNullOrBlank()) colors.content else colors.secondaryContent,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actionLabel != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.secondaryContent),
                ) {
                    Icon(ToastIcons.Close, contentDescription = closeLabel, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
