package net.brightroom.mindstock.domain.exception

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId

/**
 * Domain layer の不変条件違反を表す sealed 例外。
 *
 * Value Object のコンストラクタや Aggregate のガードメソッドから throw される。
 * Application 層(UseCase)で catch して、必要に応じて RPC 層の
 * InventoryException に翻訳する。
 */
public sealed class DomainException(
    message: String,
) : RuntimeException(message) {
    public class InvalidQuantity(
        public val value: Int,
    ) : DomainException("quantity must be > 0, got $value")

    public class InvalidMinimumStock(
        public val value: Int,
    ) : DomainException("minimum_stock must be >= 0, got $value")

    public class OccurredAtInFuture(
        public val value: Instant,
        public val now: Instant,
    ) : DomainException("occurredAt $value must be <= now $now")

    public class DisplayNameBlank : DomainException("display name must not be blank")

    public class DisplayNameTooLong(
        public val length: Int,
    ) : DomainException("display name length $length > 100")

    public class ZitadelSubBlank : DomainException("zitadel sub must not be blank")

    public class CatalogItemNameBlank : DomainException("catalog item name must not be blank")

    public class CatalogItemNameTooLong(
        public val length: Int,
    ) : DomainException("catalog item name length $length > 200")

    public class CatalogItemUnitBlank : DomainException("catalog item unit must not be blank")

    public class CatalogItemUnitTooLong(
        public val length: Int,
    ) : DomainException("catalog item unit length $length > 10")

    public class InvalidIdentity(
        public val value: Long,
    ) : DomainException("identity must be >= 0, got $value")

    public class ProductArchived(
        public val productId: ProductId,
    ) : DomainException("product $productId is archived")

    public class ProductNotInHousehold(
        public val productId: ProductId,
        public val householdId: HouseholdId,
    ) : DomainException("product $productId does not belong to household $householdId")
}
