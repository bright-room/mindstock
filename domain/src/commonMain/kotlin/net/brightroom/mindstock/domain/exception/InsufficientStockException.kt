package net.brightroom.mindstock.domain.exception

class InsufficientStockException(
    reason: String,
) : RuntimeException(reason)
