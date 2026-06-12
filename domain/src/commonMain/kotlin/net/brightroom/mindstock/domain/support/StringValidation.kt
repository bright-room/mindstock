package net.brightroom.mindstock.domain.support

/**
 * trim 済みで 1..[max] 文字であることを検証する(テキスト VO 共通)。
 * [label] は失敗メッセージに使う型名(例: "DisplayName")。
 */
internal fun String.requireTrimmedWithin(
    max: Int,
    label: String,
) {
    require(isNotEmpty() && length <= max && this == trim()) {
        "$label must be 1..$max chars after trim"
    }
}
