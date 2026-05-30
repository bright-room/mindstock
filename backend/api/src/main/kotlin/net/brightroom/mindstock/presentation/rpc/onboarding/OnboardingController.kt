package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.application.scenario.onboarding.RegisterFirstHouseholdScenario
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile
import net.brightroom.mindstock.rpc.OnboardingRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.SessionBootstrap
import org.jetbrains.exposed.v1.jdbc.Database

class OnboardingController(
    private val registerFirstHouseholdScenario: RegisterFirstHouseholdScenario,
    private val userService: UserService,
    private val householdService: HouseholdService,
    private val session: MindstockSession,
    private val database: Database,
) : OnboardingRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError> =
        tx(database, session) {
            RpcResult.Ok(registerFirstHouseholdScenario.run(session.identity, displayName))
        }

    override suspend fun bootstrap(): RpcResult<SessionBootstrap, RpcError> =
        tx(database, session) {
            RpcResult.Ok(resolveSessionBootstrap(session, userService, householdService))
        }
}
