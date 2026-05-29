package net.brightroom.mindstock.presentation.rpc.household

import io.ktor.server.application.ApplicationCall

fun interface HouseholdControllerFactory {
    fun create(call: ApplicationCall): HouseholdController
}
