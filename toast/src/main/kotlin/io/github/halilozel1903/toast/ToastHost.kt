package io.github.halilozel1903.toast

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.toast.core.RecommendedTimeout
import io.github.halilozel1903.toast.core.Toast
import io.github.halilozel1903.toast.core.ToastAnnouncement
import io.github.halilozel1903.toast.core.ToastDismissReason
import io.github.halilozel1903.toast.core.ToastPosition
import io.github.halilozel1903.toast.core.ToastSwipe
import io.github.halilozel1903.toast.core.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A toast as a custom [ToastHost] content lambda gets it.
 */
@Stable
public class ToastData internal constructor(
    /** The toast: text, type, action, duration, position. */
    public val toast: Toast,
    /** The icon passed to `show`, or `null` for the type's default. */
    public val icon: ImageVector?,
    private val state: ToastState,
) {
    public val id: Long get() = toast.id
    public val message: String get() = toast.message
    public val title: String? get() = toast.title
    public val type: ToastType get() = toast.type
    public val actionLabel: String? get() = toast.action?.label
    public val showCloseButton: Boolean get() = toast.request.showCloseButton

    /** Calls the toast's action and hides it. */
    public fun performAction() {
        state.performAction(toast.id)
    }

    /** Hides the toast as closed by the user. */
    public fun dismiss() {
        state.dismiss(toast.id, ToastDismissReason.Closed)
    }
}

/**
 * Words TalkBack uses for toasts. Pass translated texts for other languages.
 *
 * @property announcement Type names and the action hint that are read when a toast appears.
 * @property dismiss The close button and the "Dismiss" accessibility action.
 */
public data class ToastLabels(
    val announcement: ToastAnnouncement.Labels = ToastAnnouncement.Labels(),
    val dismiss: String = "Dismiss",
)

/**
 * Shows the toasts of [state] over the rest of the screen: at the top and bottom edges, stacked, with enter and
 * exit animations, swipe to dismiss, pause while touched, and TalkBack announcements. Place it last in a
 * full-screen `Box` (or over your `Scaffold`) so toasts are drawn above your content.
 *
 * ```kotlin
 * val toasts = rememberToastState()
 * Box(Modifier.fillMaxSize()) {
 *     MyScreen(onSave = { toasts.success("Saved") })
 *     ToastHost(toasts)
 * }
 * ```
 *
 * @param state The toasts to show.
 * @param modifier Applied to the full-screen container.
 * @param style Tonal (surface with colored icon) or Filled (colored background).
 * @param maxWidth Toasts are centered and never wider than this, which keeps them readable on tablets.
 * @param windowInsets Keeps toasts clear of the status bar, navigation bar, cutouts and the keyboard.
 * @param contentPadding Space between the screen edges and the toasts.
 * @param spacing Space between stacked toasts.
 * @param labels Words for TalkBack.
 * @param toast Draws one toast. The host adds animation, swipe, pause on touch and accessibility around it.
 */
@Composable
public fun ToastHost(
    state: ToastState,
    modifier: Modifier = Modifier,
    style: ToastStyle = ToastStyle.Tonal,
    maxWidth: Dp = ToastDefaults.MaxWidth,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    spacing: Dp = ToastDefaults.Spacing,
    labels: ToastLabels = ToastLabels(),
    toast: @Composable (ToastData) -> Unit = { data ->
        ToastCard(
            message = data.message,
            type = data.type,
            title = data.title,
            icon = data.icon ?: ToastDefaults.icon(data.type),
            style = style,
            actionLabel = data.actionLabel,
            onAction = data::performAction,
            onClose = if (data.showCloseButton) data::dismiss else null,
            closeLabel = labels.dismiss,
        )
    },
) {
    AccessibilityTimeouts(state)
    LaunchedEffect(state) { state.runTimers() }
    // Time only runs while the toasts can be seen.
    DisposableEffect(state) {
        state.resume()
        onDispose { state.pause() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .padding(contentPadding),
    ) {
        for (position in ToastPosition.entries) {
            val items = state.items.filter { it.toast.position == position }
            if (items.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(if (position == ToastPosition.Top) Alignment.TopCenter else Alignment.BottomCenter)
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                ) {
                    // The newest toast sits closest to its edge.
                    val ordered = if (position == ToastPosition.Top) items.asReversed() else items
                    for (item in ordered) {
                        key(item.id) {
                            ToastSlot(item, position, state, spacing, labels, toast)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToastSlot(
    item: ToastItemState,
    position: ToastPosition,
    state: ToastState,
    spacing: Dp,
    labels: ToastLabels,
    content: @Composable (ToastData) -> Unit,
) {
    val visibility = item.visibility
    if (visibility.isIdle && !visibility.currentState && !visibility.targetState) {
        LaunchedEffect(item) { state.remove(item) }
    }
    val fromTop = position == ToastPosition.Top
    AnimatedVisibility(
        visibleState = visibility,
        enter = enterTransition(fromTop),
        exit = exitTransition(fromTop, swiped = item.dismissReason == ToastDismissReason.Swiped),
    ) {
        val current = item.toast
        val data = remember(current, item.icon) { ToastData(current, item.icon, state) }
        val announcement = remember(current, labels) { ToastAnnouncement.text(current.request, labels.announcement) }
        SwipeToDismiss(
            enabled = current.request.swipeToDismiss,
            onTouch = { touched -> state.touch(item.id, touched) },
            onDismiss = { state.dismiss(item.id, ToastDismissReason.Swiped) },
            modifier = Modifier
                .padding(vertical = spacing / 2)
                .semantics(mergeDescendants = true) {
                    liveRegion = if (ToastAnnouncement.isAssertive(current.type)) LiveRegionMode.Assertive else LiveRegionMode.Polite
                    contentDescription = announcement
                    dismiss(label = labels.dismiss) {
                        state.dismiss(item.id, ToastDismissReason.Closed)
                        true
                    }
                    val action = current.action
                    if (action != null) {
                        customActions = listOf(
                            CustomAccessibilityAction(action.label) {
                                state.performAction(item.id)
                                true
                            },
                        )
                    }
                },
        ) {
            content(data)
        }
    }
}

private fun enterTransition(fromTop: Boolean): EnterTransition =
    slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { height -> if (fromTop) -height else height } +
        expandVertically(expandFrom = if (fromTop) Alignment.Top else Alignment.Bottom) +
        fadeIn(tween(180)) +
        scaleIn(initialScale = 0.92f)

private fun exitTransition(fromTop: Boolean, swiped: Boolean): ExitTransition =
    if (swiped) {
        shrinkVertically(tween(200)) + fadeOut(tween(120))
    } else {
        fadeOut(tween(160)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(200)) +
            shrinkVertically(tween(220), shrinkTowards = if (fromTop) Alignment.Top else Alignment.Bottom)
    }

/**
 * Horizontal swipe to dismiss plus pause while touched. The pointer handler sits outside the moving layer,
 * so the drag is measured in steady coordinates.
 */
@Composable
private fun SwipeToDismiss(
    enabled: Boolean,
    onTouch: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    val velocityThreshold = with(LocalDensity.current) { 600.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var settle by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnTouch(true)
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragging = false
                    var travelled = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!currentEnabled) continue
                            val dx = change.positionChange().x
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging) {
                                travelled += dx
                                if (abs(travelled) > viewConfiguration.touchSlop && !change.isConsumed) {
                                    dragging = true
                                    settle?.cancel()
                                }
                            }
                            if (dragging) {
                                change.consume()
                                offset += dx
                            }
                        }
                    } finally {
                        currentOnTouch(false)
                    }
                    if (dragging) {
                        val velocity = tracker.calculateVelocity().x
                        val full = width.toFloat()
                        val dismiss = ToastSwipe.shouldDismiss(offset, full, velocity, velocityThreshold)
                        val target = ToastSwipe.settleTarget(offset, full, dismiss)
                        settle = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = target,
                                initialVelocity = velocity,
                                animationSpec = if (dismiss) tween<Float>(180) else spring<Float>(dampingRatio = 0.7f, stiffness = 600f),
                            ) { value, _ -> offset = value }
                            if (dismiss) currentOnDismiss()
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = offset
                alpha = ToastSwipe.alpha(offset, width.toFloat())
            },
        ) {
            content()
        }
    }
}

/** Tells the queue about TalkBack and the "Time to take action" setting, and keeps it updated. */
@Composable
private fun AccessibilityTimeouts(state: ToastState) {
    val composeManager = LocalAccessibilityManager.current
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val recommended = remember(composeManager) {
        composeManager?.let { compose ->
            RecommendedTimeout { original, hasAction ->
                compose.calculateRecommendedTimeoutMillis(
                    original,
                    containsIcons = true,
                    containsText = true,
                    containsControls = hasAction,
                )
            }
        }
    }
    DisposableEffect(state, manager, recommended) {
        val update = { state.setAccessibility(manager?.isTouchExplorationEnabled == true, recommended) }
        update()
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { update() }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
}
