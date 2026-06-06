package net.brightroom.mindstock.frontend.feature.inventory

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import kotlin.test.Test

class ProductGlyphTest {
    @Test fun soap_maps_to_drop() {
        glyphForProductName("キレイキレイ 泡ハンドソープ") shouldBe AppIconName.Drop
    }

    @Test fun paper_maps_to_paper() {
        glyphForProductName("トイレットペーパー 12ロール") shouldBe AppIconName.Paper
    }

    @Test fun egg_maps_to_egg() {
        glyphForProductName("卵 10個入り") shouldBe AppIconName.Egg
    }

    @Test fun battery_maps_to_bolt() {
        glyphForProductName("単3 アルカリ乾電池") shouldBe AppIconName.Bolt
    }

    @Test fun unknown_maps_to_box() {
        glyphForProductName("謎の商品") shouldBe AppIconName.Box
    }
}
