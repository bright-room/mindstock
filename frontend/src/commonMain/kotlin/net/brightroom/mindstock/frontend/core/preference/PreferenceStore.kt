package net.brightroom.mindstock.frontend.core.preference

/** リロード・タブ復帰をまたいで保持する薄い key-value(localStorage backed)。 */
expect object PreferenceStore {
    fun get(key: String): String?

    fun set(
        key: String,
        value: String,
    )

    fun remove(key: String)
}
