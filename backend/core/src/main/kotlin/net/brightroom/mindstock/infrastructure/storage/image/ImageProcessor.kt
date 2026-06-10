package net.brightroom.mindstock.infrastructure.storage.image

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** 処理後の JPEG バイトと、その sha256 hex(= 保存キー)。 */
class ProcessedImage(
    val bytes: ByteArray,
    val ref: String,
)

/** 原画像バイト列を decode→最大辺 512px に縮小→JPEG(品質 0.85)再エンコード→sha256。S3 非依存の純処理。 */
object ImageProcessor {
    private const val MAX_EDGE = 512
    private const val JPEG_QUALITY = 0.85f

    fun process(raw: ByteArray): ProcessedImage {
        val src =
            ImageIO.read(raw.inputStream())
                ?: throw IllegalArgumentException("not a decodable image")
        val scaled = resize(src)
        val jpeg = encodeJpeg(scaled)
        return ProcessedImage(jpeg, sha256Hex(jpeg))
    }

    private fun resize(src: BufferedImage): BufferedImage {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= MAX_EDGE) return toRgb(src)
        val scale = MAX_EDGE.toDouble() / longEdge
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    // JPEG は透過非対応。alpha を含む入力を白背景の RGB に落とす。
    private fun toRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB) return src
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.drawImage(src, 0, 0, java.awt.Color.WHITE, null)
        g.dispose()
        return dst
    }

    private fun encodeJpeg(img: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        // write が例外を投げてもネイティブエンコーダを解放するため finally で dispose する。
        return try {
            val out = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JPEG_QUALITY
                    }
                writer.write(null, IIOImage(img, null, null), param)
            }
            out.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
