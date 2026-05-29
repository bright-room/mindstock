package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface UserPublicControllerFactory {
    fun create(session: MindstockSession): UserPublicController
}
