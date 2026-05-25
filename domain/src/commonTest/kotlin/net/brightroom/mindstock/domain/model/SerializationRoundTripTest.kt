package net.brightroom.mindstock.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMember
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdMembers
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import kotlin.test.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SerializationRoundTripTest {

    private val json = Json { encodeDefaults = true }

    private fun <T> roundTrip(value: T, serializer: kotlinx.serialization.KSerializer<T>): T =
        json.decodeFromString(serializer, json.encodeToString(serializer, value))

    private val user = User(
        id = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001")),
        authIdentity = AuthIdentity(
            provider = AuthProvider.ZITADEL,
            subject = AuthSubject("subject-1"),
        ),
        displayName = DisplayName("Alice"),
    )

    private val catalogItem = CatalogItem(
        id = CatalogItemId(Uuid.parse("00000000-0000-0000-0000-000000000002")),
        name = CatalogItemName("Milk"),
        unit = CatalogItemUnit("L"),
    )

    private val product = Product(
        id = ProductId(Uuid.parse("00000000-0000-0000-0000-000000000003")),
        catalogItem = catalogItem,
        minimumStock = MinimumStock(2),
        archived = false,
    )

    private val replenishment: StockMovement = Replenishment(
        product = product,
        quantity = Quantity(5),
        occurredAt = OccurredAt(Instant.parse("2026-05-25T10:00:00Z")),
        actor = user,
        note = Note(""),
    )

    private val consumption: StockMovement = Consumption(
        product = product,
        quantity = Quantity(1),
        occurredAt = OccurredAt(Instant.parse("2026-05-25T11:00:00Z")),
        actor = user,
        note = Note("breakfast"),
    )

    @Test
    fun `User round-trip`() {
        roundTrip(user, User.serializer()) shouldBe user
    }

    @Test
    fun `CatalogItem round-trip`() {
        roundTrip(catalogItem, CatalogItem.serializer()) shouldBe catalogItem
    }

    @Test
    fun `Product round-trip`() {
        roundTrip(product, Product.serializer()) shouldBe product
    }

    @Test
    fun `Replenishment as StockMovement round-trip (polymorphic)`() {
        roundTrip(replenishment, StockMovement.serializer()) shouldBe replenishment
    }

    @Test
    fun `Consumption as StockMovement round-trip (polymorphic)`() {
        roundTrip(consumption, StockMovement.serializer()) shouldBe consumption
    }

    @Test
    fun `Stock round-trip`() {
        val stock = Stock(
            product = product,
            movements = StockMovements(listOf(replenishment, consumption)),
        )
        val restored = roundTrip(stock, Stock.serializer())
        restored.product shouldBe product
        restored.movements.list shouldBe listOf(replenishment, consumption)
        restored.currentQuantity() shouldBe 4
    }

    @Test
    fun `Household round-trip`() {
        val household = Household(
            id = HouseholdId(Uuid.parse("00000000-0000-0000-0000-000000000004")),
            members = HouseholdMembers(
                list = listOf(HouseholdMember(user, HouseholdMemberRole.OWNER)),
            ),
        )
        roundTrip(household, Household.serializer()) shouldBe household
    }
}
