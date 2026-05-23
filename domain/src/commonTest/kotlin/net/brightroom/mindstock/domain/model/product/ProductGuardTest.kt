package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.extensions.kotlin.uuid.newUuidV7
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ProductGuardTest {
    private val householdA = HouseholdId(newUuidV7())
    private val householdB = HouseholdId(newUuidV7())
    private val catalogItemId = CatalogItemId(newUuidV7())
    private val now = Instant.parse("2026-05-23T10:00:00Z")

    @Test
    fun `ensureNotArchived passes when not archived`() {
        val product =
            Product(
                id = ProductId(newUuidV7()),
                householdId = householdA,
                catalogItemId = catalogItemId,
                createdAt = now,
                latestMinimumStock = null,
                archivedAt = null,
            )
        shouldNotThrow<DomainException.ProductArchived> { product.ensureNotArchived() }
    }

    @Test
    fun `ensureNotArchived throws when archived`() {
        val product =
            Product(
                id = ProductId(newUuidV7()),
                householdId = householdA,
                catalogItemId = catalogItemId,
                createdAt = now,
                latestMinimumStock = null,
                archivedAt = now,
            )
        shouldThrow<DomainException.ProductArchived> { product.ensureNotArchived() }
    }

    @Test
    fun `ensureBelongsTo passes when household matches`() {
        val product =
            Product(
                id = ProductId(newUuidV7()),
                householdId = householdA,
                catalogItemId = catalogItemId,
                createdAt = now,
                latestMinimumStock = null,
                archivedAt = null,
            )
        shouldNotThrow<DomainException.ProductNotInHousehold> { product.ensureBelongsTo(householdA) }
    }

    @Test
    fun `ensureBelongsTo throws when household differs`() {
        val product =
            Product(
                id = ProductId(newUuidV7()),
                householdId = householdA,
                catalogItemId = catalogItemId,
                createdAt = now,
                latestMinimumStock = null,
                archivedAt = null,
            )
        shouldThrow<DomainException.ProductNotInHousehold> { product.ensureBelongsTo(householdB) }
    }
}
