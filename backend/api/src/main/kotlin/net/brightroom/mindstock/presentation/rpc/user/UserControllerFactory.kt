package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall

fun interface UserControllerFactory {
    fun create(call: ApplicationCall): UserController
}
