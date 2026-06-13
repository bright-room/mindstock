package net.brightroom.mindstock.frontend.app.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.app_name
import mindstock.frontend.generated.resources.welcome_chip_forget
import mindstock.frontend.generated.resources.welcome_chip_predict
import mindstock.frontend.generated.resources.welcome_chip_share
import mindstock.frontend.generated.resources.welcome_cta
import mindstock.frontend.generated.resources.welcome_cta_busy
import mindstock.frontend.generated.resources.welcome_description
import mindstock.frontend.generated.resources.welcome_footer
import mindstock.frontend.generated.resources.welcome_tagline
import net.brightroom.mindstock.frontend.app.shell.LocalIsWideShell
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/**
 * 未認証時に表示するウェルカム/サインイン splash。モック `app/screens-a.jsx` の `Login` 準拠。
 * ボタン押下で Zitadel authorize へ redirect する(busy 表示)。
 */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val wide = LocalIsWideShell.current
    var busy by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize().background(tokens.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    // Compact は縦並び全幅(420dp 上限)、Wide は desktop-login 準拠の中央寄せカード幅。
                    .widthIn(max = if (wide) 880.dp else 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(60.dp)
                            .rotate(-6f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconName.Box, contentDescription = null, tint = tokens.onAccent, size = 32.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppText(
                        stringResource(Res.string.app_name),
                        style = MindstockType.summaryTitle().copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
                        color = tokens.ink,
                    )
                    AppText(stringResource(Res.string.welcome_tagline), style = MindstockType.unitCaption(), color = tokens.faint)
                }
            }

            AppText(stringResource(Res.string.welcome_description), style = MindstockType.summarySub(), color = tokens.sub)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Res.string.welcome_chip_forget,
                    Res.string.welcome_chip_predict,
                    Res.string.welcome_chip_share,
                ).forEach { res ->
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .border(1.dp, tokens.line, RoundedCornerShape(99.dp))
                                .background(tokens.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        AppText(stringResource(res), style = MindstockType.statusLabel(), color = tokens.sub)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            AppButton(
                onClick = {
                    busy = true
                    onSignIn()
                },
                enabled = !busy,
                size = ButtonSize.Lg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(
                    stringResource(if (busy) Res.string.welcome_cta_busy else Res.string.welcome_cta),
                    color = tokens.onAccent,
                    style = MindstockType.button(),
                )
            }
            AppText(
                stringResource(Res.string.welcome_footer),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
