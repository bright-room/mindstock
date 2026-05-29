package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface HouseholdControllerFactory {
    fun create(session: MindstockSession): HouseholdController
}
