package net.brightroom.mindstock.frontend.auth

internal expect object SessionStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}
