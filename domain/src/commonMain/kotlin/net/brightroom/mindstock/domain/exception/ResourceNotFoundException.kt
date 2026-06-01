package net.brightroom.mindstock.domain.exception

class ResourceNotFoundException(
    reason: String,
) : RuntimeException(reason)
