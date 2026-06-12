package net.brightroom.mindstock.infrastructure.datasource

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import net.brightroom.mindstock.testfixtures.TestDatabase
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class TestDatabaseSmokeTest :
    FunSpec({
        beforeTest { TestDatabase.clean() }

        test("migrate + clean が通り residents が空で読める") {
            transaction(TestDatabase.database) {
                ResidentsTable.selectAll().count()
            } shouldBe 0L
        }
    })
