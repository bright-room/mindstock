package net.brightroom.mindstock.domain.model.product

/**
 * 商品の集合。世帯の商品リスト等で使う。
 */
class Products(private val list: List<Product>) {
    /** archived = false の商品のみのコレクションを返す。 */
    fun activeOnly(): Products = Products(list.filter { !it.archived })

    fun asList(): List<Product> = list.toList()

    val size: Int get() = list.size
}
