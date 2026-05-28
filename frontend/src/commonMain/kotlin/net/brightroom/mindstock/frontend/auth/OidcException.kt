package net.brightroom.mindstock.frontend.auth

class OidcException(
    val errorCode: String,
    val errorDescription: String?,
    val reauthRequired: Boolean = false,
) : RuntimeException("$errorCode: ${errorDescription ?: "(no description)"}")
