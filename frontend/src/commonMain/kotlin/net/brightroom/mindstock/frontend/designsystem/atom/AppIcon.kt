package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** semantic アイコン名。feature は AppIconName でのみ参照(将来差し替え可能に)。 */
enum class AppIconName {
    Box,
    Cart,
    Plus,
    Minus,
    Clock,
    Home,
    User,
    Back,
    Bell,
    Search,
    ChevronRight,
    Trend,
    Close,
    Drop,
    Paper,
    Egg,
    Bottle,
    Salt,
    Bolt,
    Leaf,
    Settings,
    Barcode,
    Archive,
    Restore,
    Pencil,
    Check,
    Link,
    Users,
    Crown,
    Swap,
    Copy,
    Eye,
    Trash,
}

private fun AppIconName.vector(): ImageVector =
    when (this) {
        AppIconName.Box -> Icons.Filled.Inventory2
        AppIconName.Cart -> Icons.Filled.ShoppingCart
        AppIconName.Plus -> Icons.Filled.Add
        AppIconName.Minus -> Icons.Filled.Remove
        AppIconName.Clock -> Icons.Outlined.AccessTime
        AppIconName.Home -> Icons.Outlined.Home
        AppIconName.User -> Icons.Outlined.Person
        AppIconName.Back -> Icons.AutoMirrored.Filled.ArrowBack
        AppIconName.Bell -> Icons.Outlined.Notifications
        AppIconName.Search -> Icons.Outlined.Search
        AppIconName.ChevronRight -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
        AppIconName.Trend -> Icons.Outlined.TrendingUp
        AppIconName.Close -> Icons.Filled.Close
        AppIconName.Drop -> MindstockGlyphs.Drop
        AppIconName.Paper -> MindstockGlyphs.Paper
        AppIconName.Egg -> MindstockGlyphs.Egg
        AppIconName.Bottle -> MindstockGlyphs.Bottle
        AppIconName.Salt -> MindstockGlyphs.Salt
        AppIconName.Bolt -> MindstockGlyphs.Bolt
        AppIconName.Leaf -> MindstockGlyphs.Leaf
        AppIconName.Settings -> Icons.Outlined.Tune
        AppIconName.Barcode -> Icons.Outlined.QrCode
        AppIconName.Archive -> Icons.Outlined.Archive
        AppIconName.Restore -> Icons.Outlined.Unarchive
        AppIconName.Pencil -> Icons.Outlined.Edit
        AppIconName.Check -> Icons.Outlined.Check
        AppIconName.Link -> Icons.Outlined.Link
        AppIconName.Users -> Icons.Outlined.Group
        AppIconName.Crown -> Icons.Outlined.WorkspacePremium
        AppIconName.Swap -> Icons.Outlined.SwapHoriz
        AppIconName.Copy -> Icons.Outlined.ContentCopy
        AppIconName.Eye -> Icons.Outlined.Visibility
        AppIconName.Trash -> Icons.Outlined.DeleteOutline
    }

@Composable
fun AppIcon(
    name: AppIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = name.vector(),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
    )
}
