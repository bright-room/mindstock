package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable

/**
 * 商品の集合。世帯の商品リスト等で使う。
 */
@Serializable
data class Products(
    val list: List<Product>,
) {
    /** archived = false の商品のみのコレクションを返す。 */
    fun activeOnly(): Products = Products(list.filter { !it.archived })
}
