package net.brightroom.mindstock.domain.exception

class CannotArchiveWithStockException(
    reason: String,
) : RuntimeException(reason)
