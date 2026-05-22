package net.brightroom.mindstock.backend

import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.brightroom.mindstock.infrastructure.persistence.DatabaseConfig
import net.brightroom.mindstock.infrastructure.persistence.DatabaseFactory
import net.brightroom.mindstock.infrastructure.persistence.MigrationRunner
import javax.sql.DataSource

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = environment.config
    val dbConfig = DatabaseConfig(
        jdbcUrl = config.property("database.jdbcUrl").getString(),
        username = config.property("database.username").getString(),
        password = config.property("database.password").getString(),
    )
    val dataSource: DataSource = DatabaseFactory.dataSource(dbConfig)
    MigrationRunner.migrate(dataSource)
    DatabaseFactory.exposed(dataSource)

    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
