package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface OnboardingControllerFactory {
    fun create(session: MindstockSession): OnboardingController
}
