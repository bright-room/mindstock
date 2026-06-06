package net.brightroom.mindstock.frontend.core.ui

import io.kotest.matchers.shouldBe
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_replenished
import kotlin.test.Test

class ToastControllerTest {
    @Test
    fun show_publishes_message_and_dismiss_clears() {
        val controller = ToastController()
        controller.current.value shouldBe null
        controller.show(UiText(Res.string.toast_replenished))
        controller.current.value
            ?.text
            ?.resource shouldBe Res.string.toast_replenished
        controller.dismiss()
        controller.current.value shouldBe null
    }
}
