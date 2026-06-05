package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** semantic アイコン名。feature は AppIconName でのみ参照(将来差し替え可能に)。 */
enum class AppIconName { Box, Cart, Plus, Minus, Clock, Home, User }

private fun AppIconName.vector(): ImageVector =
    when (this) {
        AppIconName.Box -> Icons.Filled.Inventory2
        AppIconName.Cart -> Icons.Filled.ShoppingCart
        AppIconName.Plus -> Icons.Filled.Add
        AppIconName.Minus -> Icons.Filled.Remove
        AppIconName.Clock -> Icons.Outlined.AccessTime
        AppIconName.Home -> Icons.Outlined.Home
        AppIconName.User -> Icons.Outlined.Person
    }

@Composable
fun AppIcon(
    name: AppIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(imageVector = name.vector(), contentDescription = contentDescription, modifier = modifier)
}
