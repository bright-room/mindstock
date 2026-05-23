package net.brightroom.mindstock.configuration

enum class Environment {
    LOCAL,
    DEV,
    STG,
    PROD,
    ;

    fun isDeployment(): Boolean = this != LOCAL

    fun isProduction(): Boolean = this == PROD
}
