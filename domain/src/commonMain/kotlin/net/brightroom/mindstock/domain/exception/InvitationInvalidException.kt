package net.brightroom.mindstock.domain.exception

class InvitationInvalidException(
    reason: String,
) : RuntimeException(reason)
