package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import kotlin.test.Test
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile

class HouseholdRenameTest {
    private fun resident(name: String) = Resident(ResidentId.create(), ResidentProfile(DisplayName(name)))

    @Test
    fun owner_can_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        household
            .rename(HouseholdName("新居"), owner.id)
            .profile.name
            .invoke() shouldBe "新居"
    }

    @Test
    fun stranger_cannot_rename() {
        val owner = resident("おや")
        val household = Household.create(HouseholdName("我が家"), owner)
        shouldThrow<ResourceNotFoundException> { household.rename(HouseholdName("新居"), ResidentId.create()) }
    }
}
