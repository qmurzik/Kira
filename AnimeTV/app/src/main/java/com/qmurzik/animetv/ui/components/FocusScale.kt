package com.qmurzik.animetv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * The one focus-affordance every focusable TV element in this app shares: a gentle scale-up.
 * Combined with a border/glow drawn by the caller, this is what makes "where is the cursor"
 * unambiguous at all times (item 28 - TV UX) without leaning on a touch ripple, which reads
 * poorly from a couch.
 */
@Composable
fun rememberTvFocusScale(focused: Boolean, target: Float = 1.08f): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) target else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tv-focus-scale",
    )
    return scale
}
