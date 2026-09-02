package com.qmurzik.animetv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qmurzik.animetv.R
import com.qmurzik.animetv.ui.theme.TvBackgroundElevated
import com.qmurzik.animetv.ui.theme.TvFocusGlow
import com.qmurzik.animetv.ui.theme.TvPrimary

data class NavRailItem(val screen: Screen, val icon: ImageVector, val labelRes: Int)

val navRailItems = listOf(
    NavRailItem(Screen.Home, Icons.Filled.Home, R.string.nav_home),
    NavRailItem(Screen.Search, Icons.Filled.Search, R.string.nav_search),
    NavRailItem(Screen.Favorites, Icons.Filled.Favorite, R.string.nav_favorites),
    NavRailItem(Screen.History, Icons.Filled.History, R.string.nav_history),
    NavRailItem(Screen.Settings, Icons.Filled.Settings, R.string.nav_settings),
)

/** Persistent left-edge navigation used across every top-level screen (item 27). */
@Composable
fun NavRail(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(96.dp)
            .background(TvBackgroundElevated)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        navRailItems.forEach { item ->
            NavRailButton(
                item = item,
                selected = currentRoute == item.screen.route,
                onClick = { onNavigate(item.screen) },
            )
        }
    }
}

@Composable
private fun NavRailButton(item: NavRailItem, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val background = when {
        focused -> TvPrimary
        selected -> TvPrimary.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusGlow else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = if (focused || selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (focused || selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
