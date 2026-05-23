package net.brightroom.mindstock.extensions.kotlin.uuid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UuidV7Test {
    @Test
    fun `newUuidV7 returns distinct ids on successive calls`() {
        val a = newUuidV7()
        val b = newUuidV7()
        a shouldNotBe b
    }

    @Test
    fun `newUuidV7 returns a version 7 uuid`() {
        val uuid = newUuidV7()
        val versionNibble = uuid.toString()[14]
        versionNibble.toString() shouldBe "7"
    }
}
