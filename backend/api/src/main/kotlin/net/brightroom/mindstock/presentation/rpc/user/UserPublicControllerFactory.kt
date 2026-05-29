package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall

fun interface UserPublicControllerFactory {
    fun create(call: ApplicationCall): UserPublicController
}
