package net.brightroom.mindstock.frontend.core.navigation

import kotlinx.serialization.Serializable

/** トップレベル目的地(型安全 route)。 */
sealed interface Route {
    @Serializable data object Stock : Route

    @Serializable data object Shop : Route

    @Serializable data object Activity : Route

    @Serializable data object Profile : Route
}
