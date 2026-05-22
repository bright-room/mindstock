package net.brightroom.mindstock.infrastructure.persistence

import org.flywaydb.core.Flyway
import javax.sql.DataSource

object MigrationRunner {
    /**
     * Applies every pending migration under classpath `db/migration/` to
     * the given [dataSource].
     */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
