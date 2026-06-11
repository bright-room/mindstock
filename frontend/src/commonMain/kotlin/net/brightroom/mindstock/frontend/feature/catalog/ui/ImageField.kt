package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.image_field_add
import mindstock.frontend.generated.resources.image_field_change
import mindstock.frontend.generated.resources.image_field_hint
import mindstock.frontend.generated.resources.image_field_remove
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 画像欄(mock `screens-master.jsx:ImageField` 準拠)。左にサムネ、右にボタン行 + ヒント文。
 * [image] が null のときは [fallbackIcon] のサムネを表示し、ボタンは「画像を追加」のみ。
 * [image] があるときはボタン文言が「画像を変更」になり、ghost の「削除」が並ぶ。
 */
@Composable
fun ImageField(
    image: ImageBitmap?,
    fallbackIcon: AppIconName,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(icon = fallbackIcon, size = 64.dp, radius = 16.dp, image = image)
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    onClick = onPick,
                    variant = ButtonVariant.Soft,
                    size = ButtonSize.Sm,
                    icon = AppIconName.Plus,
                ) {
                    AppText(
                        stringResource(
                            if (image != null) Res.string.image_field_change else Res.string.image_field_add,
                        ),
                    )
                }
                if (image != null) {
                    AppButton(
                        onClick = onRemove,
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Sm,
                        icon = AppIconName.Trash,
                    ) {
                        AppText(stringResource(Res.string.image_field_remove))
                    }
                }
            }
            AppText(
                text = stringResource(Res.string.image_field_hint),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
