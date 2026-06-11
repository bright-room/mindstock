package net.brightroom.mindstock.infrastructure.transfer.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageProcessorTest :
    FunSpec({
        fun pngBytes(
            w: Int,
            h: Int,
        ): ByteArray {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val out = ByteArrayOutputStream()
            ImageIO.write(img, "png", out)
            return out.toByteArray()
        }

        test("大きい画像は最大辺 512px 以内の JPEG に縮小される") {
            val processed = ImageProcessor.process(pngBytes(2000, 1000))
            val read = ImageIO.read(processed.bytes.inputStream())
            maxOf(read.width, read.height) shouldBeLessThanOrEqualTo 512
        }

        test("ref は sha256 hex(64桁)で処理後バイトに対応") {
            val processed = ImageProcessor.process(pngBytes(100, 100))
            processed.ref shouldHaveLength 64
        }

        test("同一入力は同一 ref(content-addressed)") {
            val a = ImageProcessor.process(pngBytes(100, 100))
            val b = ImageProcessor.process(pngBytes(100, 100))
            a.ref shouldBe b.ref
        }

        test("画像でないバイト列は IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { ImageProcessor.process(byteArrayOf(1, 2, 3, 4)) }
        }
    })
