package net.brightroom.mindstock.presentation.rpc.user

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface UserControllerFactory {
    fun create(session: MindstockSession): UserController
}
