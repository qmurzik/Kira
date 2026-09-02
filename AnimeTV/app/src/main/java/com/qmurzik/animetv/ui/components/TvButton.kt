package com.qmurzik.animetv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.qmurzik.animetv.ui.theme.TvFocusGlow
import com.qmurzik.animetv.ui.theme.TvPrimary

/**
 * Standard action button. [primary] renders a solid filled CTA (e.g. "Watch"); the outlined
 * variant is for secondary actions (e.g. "Details", "Add to Favorites"). Both grow slightly
 * and gain a glow border on D-pad focus, matching [PosterCard]'s focus language.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = rememberTvFocusScale(focused, target = 1.05f)
    val focusBorder = Modifier.border(
        width = if (focused) 2.dp else 0.dp,
        color = if (focused) TvFocusGlow else Color.Transparent,
        shape = RoundedCornerShape(50),
    )

    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text)
        }
    }

    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
            shape = RoundedCornerShape(50),
            modifier = modifier.scale(scale).then(focusBorder),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(50),
            modifier = modifier.scale(scale).then(focusBorder),
        ) { content() }
    }
}
