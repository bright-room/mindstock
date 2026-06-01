package net.brightroom.mindstock.rpc.stock

import kotlinx.serialization.Serializable

/**
 * 世帯全体の活動履歴(UC24)の射影。各 movement に商品を添えたエントリの集合。
 */
@Serializable
data class ActivityFeed(
    val list: List<ActivityEntry>,
) {
    fun size(): Int = list.size
}
