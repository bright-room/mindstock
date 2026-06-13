package net.brightroom.mindstock.frontend.core.image

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.coroutines.resume

// `input type=file` を生成して click し、FileReader.readAsDataURL で読む。
// data URL の payload 部分(カンマ以降)を base64 として返す。ファイル未選択/失敗は Cancelled。
// 注: DOM の file input には確実なキャンセルイベントが無いため、ダイアログを閉じただけでは
// continuation が resume されないことがある(Cancelled も来ない)。これは web の制約として許容する。
internal actual suspend fun pickImage(): ImagePickResult =
    suspendCancellableCoroutine { cont ->
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                cont.resume(ImagePickResult.Cancelled)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val dataUrl = (reader.result as JsString).toString()
                    cont.resume(ImagePickResult.Selected(dataUrl.substringAfter(",")))
                }
                reader.onerror = { cont.resume(ImagePickResult.Cancelled) }
                reader.readAsDataURL(file)
            }
        }
        input.click()
    }
