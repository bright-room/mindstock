package net.brightroom.mindstock.domain.exception

/**
 * Domain layer の不変条件違反を表す sealed 例外。
 *
 * Value Object のコンストラクタや Aggregate のガードメソッドから throw される。
 * Application 層(UseCase)で catch して、必要に応じて RPC 層の
 * InventoryException に翻訳する。
 */
public sealed class DomainException(message: String) : RuntimeException(message) {

    public class InvalidQuantity(public val value: Int) :
        DomainException("quantity must be > 0, got $value")
}
