package net.brightroom.mindstock.frontend.core.image

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.coroutines.resume

// `input type=file` を生成して click し、FileReader.readAsDataURL で読む。
// data URL の payload 部分(カンマ以降)を base64 として返す。ファイル未選択なら null。
// 注: DOM の file input には確実なキャンセルイベントが無いため、ダイアログを閉じただけでは
// continuation が resume されないことがある(null も来ない)。これは web の制約として許容する。
actual suspend fun pickImageAsBase64(): String? =
    suspendCancellableCoroutine { cont ->
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                cont.resume(null)
            } else {
                val reader = FileReader()
                reader.onload = {
                    val dataUrl = (reader.result as JsString).toString()
                    cont.resume(dataUrl.substringAfter(","))
                }
                reader.onerror = { cont.resume(null) }
                reader.readAsDataURL(file)
            }
        }
        input.click()
    }
