package net.brightroom.mindstock.frontend.auth

import kotlinx.serialization.json.Json

internal object TokenStore {
    private const val KEY = "mindstock.tokens.v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(tokens: Tokens) {
        SessionStorage.set(KEY, json.encodeToString(Tokens.serializer(), tokens))
    }

    fun load(): Tokens? = SessionStorage.get(KEY)?.let {
        runCatching { json.decodeFromString(Tokens.serializer(), it) }.getOrNull()
    }

    fun clear() { SessionStorage.remove(KEY) }
}
