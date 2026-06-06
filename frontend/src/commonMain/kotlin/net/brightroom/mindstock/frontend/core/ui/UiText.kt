package net.brightroom.mindstock.frontend.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 非 Composable 層が持ち回るユーザ向け文言（リソースキー + 書式引数）。UI 層で resolve する。 */
data class UiText(
    val resource: StringResource,
    val args: List<String> = emptyList(),
)

@Composable
fun UiText.resolve(): String =
    if (args.isEmpty()) {
        stringResource(resource)
    } else {
        stringResource(resource, *args.toTypedArray())
    }
