package net.brightroom.mindstock.domain.exception

class OwnerRequiredException(
    reason: String,
) : RuntimeException(reason)
