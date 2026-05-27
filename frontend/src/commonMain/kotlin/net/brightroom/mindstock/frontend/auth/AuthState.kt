package net.brightroom.mindstock.frontend.auth

sealed interface AuthState {
    data object LoggedOut : AuthState

    data object Authenticating : AuthState

    data object NeedRegister : AuthState

    data class Ready(val tokens: Tokens) : AuthState

    data class Error(val message: String) : AuthState
}
