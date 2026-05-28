package net.brightroom.mindstock.presentation.rpc.user

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockPrincipal
import net.brightroom.mindstock.configuration.error.UnauthorizedException
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class UserPublicRpcServiceImpl(
    private val userRegisterService: UserRegisterService,
    private val call: ApplicationCall,
    private val database: Database,
) : UserPublicRpcService {
    override suspend fun register(displayName: DisplayName): User {
        val principal =
            call.principal<MindstockPrincipal>()
                ?: throw UnauthorizedException("missing principal")
        return tx(database) { userRegisterService.register(principal.authIdentity, displayName) }
    }
}
