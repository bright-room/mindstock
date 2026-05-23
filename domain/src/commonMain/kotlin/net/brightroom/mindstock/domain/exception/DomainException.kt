package net.brightroom.mindstock.domain.exception

import kotlin.time.Instant

/**
 * Domain layer の不変条件違反を表す sealed 例外。
 *
 * Value Object のコンストラクタから throw される。
 * Application 層(UseCase)で catch して、必要に応じて RPC 層の
 * InventoryException に翻訳する。
 */
sealed class DomainException(
    message: String,
) : RuntimeException(message) {
    class InvalidQuantity(
        val value: Int,
    ) : DomainException("quantity must be > 0, got $value")

    class InvalidMinimumStock(
        val value: Int,
    ) : DomainException("minimum_stock must be >= 0, got $value")

    class OccurredAtInFuture(
        val value: Instant,
        val now: Instant,
    ) : DomainException("occurredAt $value must be <= now $now")

    class DisplayNameBlank : DomainException("display name must not be blank")

    class DisplayNameTooLong(
        val length: Int,
    ) : DomainException("display name length $length > 100")

    class CatalogItemNameBlank : DomainException("catalog item name must not be blank")

    class CatalogItemNameTooLong(
        val length: Int,
    ) : DomainException("catalog item name length $length > 200")

    class CatalogItemUnitBlank : DomainException("catalog item unit must not be blank")

    class CatalogItemUnitTooLong(
        val length: Int,
    ) : DomainException("catalog item unit length $length > 10")

    class AuthSubjectBlank : DomainException("auth subject must not be blank")
}
