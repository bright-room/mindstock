package net.brightroom.mindstock.domain.model.user.profile

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ProfileTest {
    @Test
    fun `equals works on userId and displayName`() {
        val id = UserId.create()
        val a = Profile(id, DisplayName("Alice"))
        val b = Profile(id, DisplayName("Alice"))
        a shouldBe b
    }

    @Test
    fun `different displayName yields different Profile`() {
        val id = UserId.create()
        val a = Profile(id, DisplayName("Alice"))
        val b = Profile(id, DisplayName("Bob"))
        a shouldNotBe b
    }

    @Test
    fun `different userId yields different Profile`() {
        val a = Profile(UserId.create(), DisplayName("Alice"))
        val b = Profile(UserId.create(), DisplayName("Alice"))
        a shouldNotBe b
    }
}
