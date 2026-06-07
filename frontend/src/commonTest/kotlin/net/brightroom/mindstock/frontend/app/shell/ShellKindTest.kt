package net.brightroom.mindstock.frontend.app.shell

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ShellKindTest {
    @Test
    fun below_840_is_compact() {
        shellKindFor(839) shouldBe ShellKind.Compact
    }

    @Test
    fun exactly_840_is_wide() {
        shellKindFor(840) shouldBe ShellKind.Wide
    }

    @Test
    fun large_width_is_wide() {
        shellKindFor(1280) shouldBe ShellKind.Wide
    }

    @Test
    fun tablet_portrait_stays_compact() {
        shellKindFor(700) shouldBe ShellKind.Compact
    }
}
