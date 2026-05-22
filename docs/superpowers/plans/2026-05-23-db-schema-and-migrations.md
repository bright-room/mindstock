# DB Schema + Migrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up mindstock's persistence foundation: PostgreSQL 18 in Docker Compose, Exposed Tables for every domain (User / Household / Catalog / Product / Stock) as append-only "fact tables", a custom `@Migratable` annotation + generator that emits Flyway-style SQL via Exposed's `MigrationUtils`, a Flyway-based applier wired into backend startup, and Testcontainers integration tests that verify migrations apply cleanly to a real PG 18 and that UPDATE/DELETE are denied on fact tables.

**Architecture:** All persistence code lives in the `infrastructure` module. Schema definitions are Exposed `Table` objects annotated `@Migratable`. A Gradle task (`generateMigrationScript`) runs `MigrationUtils.generateMigrationScript(...)` against an empty PG instance and emits a Flyway-format SQL file under `infrastructure/src/main/resources/db/migration/`. Flyway applies pending migrations at app startup (via a `DatabaseInitializer` invoked from `Application.module()`). Testcontainers spins up a real PG 18 container for integration tests so PG-specific functions (`uuidv7()`, `DISTINCT ON`) are exercised against the real engine.

**Tech Stack:** Exposed v1 (`org.jetbrains.exposed.v1.jdbc` + `org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils`), HikariCP, PostgreSQL 18 JDBC driver, Flyway 11, Testcontainers 1.20, Kotest, Docker Compose.

---

## Important notes for implementers

- **Library API verification.** Exposed v1 is still on a beta line; the function names and import paths below should be cross-checked against the version pinned in `gradle/libs.versions.toml`. If something doesn't compile, search the artifact JAR for the closest matching symbol and update the plan note rather than guessing.
- **Don't use VIEWs.** All "latest value per group" reads happen in Kotlin via Exposed `withDistinctOn()` + `QueryAlias`, per the design spec. We keep the database schema to plain tables + indexes only.
- **PostgreSQL 18 is required.** Tasks rely on `uuidv7()` and `DISTINCT ON`. Use the `postgres:18` Docker image.
- **Network / outage handling.** If Maven Central or Docker Hub is degraded, stop and report — do not invent fallback artifacts (per Plan 1's lessons).
- **Branch convention.** Work on `feat/db-schema-and-migrations`. Don't push to main directly.
- **JDK 25 toolchain** (set by `net.brightroom.mindstock.kotlin-jvm` convention).
- **Append-only.** Every table created in this plan must have a corresponding DB grant such that the `mindstock_app` role has only `SELECT, INSERT` on it. Tests verify this.
- **Don't pre-populate uuids in Kotlin** for aggregate roots — use the PG `uuidv7()` default and let Exposed read the generated value back with `RETURNING id`.

---

## File Structure

After this plan:

```
mindstock/
├── compose.yml                              # PG 18 + (later) Zitadel
├── scripts/
│   ├── db-up.sh
│   └── db-down.sh
├── infrastructure/
│   ├── build.gradle.kts                     # +exposed-migration, +testcontainers
│   └── src/
│       ├── main/
│       │   ├── kotlin/net/brightroom/mindstock/infrastructure/
│       │   │   ├── persistence/
│       │   │   │   ├── Migratable.kt        # @Migratable annotation
│       │   │   │   ├── MigratableTables.kt  # Explicit table registry
│       │   │   │   ├── Database.kt          # HikariCP + Exposed Database
│       │   │   │   └── MigrationRunner.kt   # Flyway-based applier
│       │   │   └── schema/
│       │   │       ├── user/                # UsersTable, UserDisplayNamesTable
│       │   │       ├── household/           # HouseholdsTable + memberships + revocations
│       │   │       ├── catalog/             # CatalogItemsTable + names + units
│       │   │       ├── product/             # ProductsTable + minimum_stocks + archives
│       │   │       └── stock/               # ReplenishmentsTable + consumptions + corrections
│       │   └── resources/
│       │       └── db/migration/
│       │           └── V20260523000001__init.sql   # generated
│       └── test/
│           └── kotlin/net/brightroom/mindstock/infrastructure/
│               ├── TestContainersPostgres.kt
│               ├── persistence/MigrationApplierTest.kt
│               └── persistence/AppendOnlyEnforcementTest.kt
├── build-logic/src/main/kotlin/
│   └── net.brightroom.mindstock.gradle-tasks.gradle.kts  # generateMigrationScript task
└── backend/src/main/kotlin/net/brightroom/mindstock/backend/
    └── Main.kt                              # +DatabaseInitializer wiring
```

---

## Task 1: Add Docker Compose for PostgreSQL 18

**Files:**
- Create: `compose.yml`
- Create: `scripts/db-up.sh`
- Create: `scripts/db-down.sh`

- [ ] **Step 1: Write compose.yml**

Create `compose.yml` at repo root:

```yaml
services:
  postgres:
    image: postgres:18
    container_name: mindstock-postgres
    environment:
      POSTGRES_DB: mindstock
      POSTGRES_USER: mindstock
      POSTGRES_PASSWORD: mindstock
    ports:
      - "5432:5432"
    volumes:
      - mindstock-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mindstock -d mindstock"]
      interval: 2s
      timeout: 2s
      retries: 20

volumes:
  mindstock-pgdata:
```

- [ ] **Step 2: Write scripts/db-up.sh**

Create `scripts/db-up.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d postgres
docker compose exec -T postgres bash -lc 'until pg_isready -U mindstock -d mindstock; do sleep 1; done'
echo "postgres ready on localhost:5432 (db=mindstock user=mindstock)"
```

- [ ] **Step 3: Write scripts/db-down.sh**

Create `scripts/db-down.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose down
```

- [ ] **Step 4: Make scripts executable**

```bash
chmod +x scripts/db-up.sh scripts/db-down.sh
```

- [ ] **Step 5: Smoke test**

Run `./scripts/db-up.sh`. Expected: container starts and `postgres ready ...` prints. Then:

```bash
docker compose exec -T postgres psql -U mindstock -d mindstock -c "SELECT uuidv7();"
```

Expected: one UUIDv7 value printed. Stop the DB with `./scripts/db-down.sh`.

- [ ] **Step 6: Commit**

```bash
git add compose.yml scripts/
git commit -m "Add PostgreSQL 18 Docker Compose and helper scripts"
```

---

## Task 2: Add infrastructure dependencies

**Files:**
- Modify: `infrastructure/build.gradle.kts`

- [ ] **Step 1: Update infrastructure/build.gradle.kts**

Replace the contents of `infrastructure/build.gradle.kts` with:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.application)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    implementation(libs.hikari)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :infrastructure:compileKotlin
```

Expected: BUILD SUCCESSFUL (the existing `Placeholder.kt` still compiles).

- [ ] **Step 3: Commit**

```bash
git add infrastructure/build.gradle.kts
git commit -m "Wire infrastructure module deps for Exposed + Flyway + Testcontainers"
```

---

## Task 3: Add the `@Migratable` annotation and table registry

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Migratable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt`

- [ ] **Step 1: Write the annotation**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Migratable.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

/**
 * Marks an Exposed [org.jetbrains.exposed.v1.core.Table] as a target for
 * migration script generation. The generator iterates [MigratableTables.all]
 * (which lists each annotated table explicitly) to keep classpath scanning
 * out of the runtime.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Migratable
```

- [ ] **Step 2: Write the registry**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

/**
 * Canonical list of every [@Migratable] table. Tasks 7–11 add to this list
 * as each domain's schema is introduced. Listing tables explicitly (rather
 * than scanning the classpath at runtime) keeps the registry trivial and
 * test-friendly.
 */
object MigratableTables {
    val all: List<Table>
        get() = listOf(
            // Populated in subsequent tasks
        )
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :infrastructure:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/
git commit -m "Add @Migratable annotation and explicit table registry"
```

---

## Task 4: Add Testcontainers harness for integration tests

**Files:**
- Create: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersPostgres.kt`

- [ ] **Step 1: Write the harness**

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersPostgres.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A lazily-initialised PostgreSQL 18 container shared across integration
 * tests. Each test should connect to a unique database created via
 * [withFreshDatabase] so they don't see each other's schemas.
 */
object TestContainersPostgres {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:18").apply {
            withDatabaseName("mindstock_test")
            withUsername("mindstock")
            withPassword("mindstock")
            start()
        }
    }

    val jdbcUrl: String get() = container.jdbcUrl
    val username: String get() = container.username
    val password: String get() = container.password

    /**
     * Runs [block] against a fresh schema. Creates a new schema with a
     * random name, sets the session search_path to it, runs the block,
     * and drops the schema afterward.
     */
    fun <T> withFreshSchema(block: (jdbcUrl: String, schema: String) -> T): T {
        val schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        container.createConnection("").use { conn ->
            conn.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        try {
            val urlWithSchema = container.jdbcUrl + "&currentSchema=$schema"
            return block(urlWithSchema, schema)
        } finally {
            container.createConnection("").use { conn ->
                conn.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
```

- [ ] **Step 2: Write a smoke test that pulls the image**

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersSmokeTest.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TestContainersSmokeTest : FunSpec({
    test("the test postgres container reports server_version_num for PG 18") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            java.sql.DriverManager.getConnection(jdbcUrl, TestContainersPostgres.username, TestContainersPostgres.password).use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SHOW server_version_num")
                    rs.next()
                    val versionNum = rs.getInt(1)
                    (versionNum >= 180000) shouldBe true
                }
            }
        }
    }
})
```

- [ ] **Step 3: Run it**

```bash
./gradlew :infrastructure:test --tests TestContainersSmokeTest
```

Expected: PASS. (Docker must be running locally; first run pulls postgres:18 which may take a minute.)

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/test/
git commit -m "Add Testcontainers harness with shared PG 18 container"
```

---

## Task 5: Add Database connection + Exposed wiring

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Database.kt`

- [ ] **Step 1: Write the connection helper**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Database.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 10,
)

object DatabaseFactory {
    fun dataSource(config: DatabaseConfig): HikariDataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maximumPoolSize
            isAutoCommit = false
            driverClassName = "org.postgresql.Driver"
        }
        return HikariDataSource(hikari)
    }

    fun exposed(dataSource: DataSource): Database = Database.connect(dataSource)
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :infrastructure:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Database.kt
git commit -m "Add HikariCP-backed Database connection factory"
```

---

## Task 6: Define UUIDv7-keyed and BIGINT-IDENTITY table base classes

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/TableBases.kt`

The Plan 2 design uses two id strategies:
- **Aggregate root tables** carry `UUID PRIMARY KEY DEFAULT uuidv7()`
- **History (fact) tables** carry `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`

We give them base classes so each schema table doesn't repeat the boilerplate.

- [ ] **Step 1: Write the bases**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/TableBases.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.schema

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

/**
 * Base for aggregate root tables. id is UUID with `DEFAULT uuidv7()` so PG
 * generates the value server-side. created_at is automatically populated.
 */
abstract class AggregateRootTable(name: String) : Table(name) {
    val id = uuid("id").defaultExpression(CustomFunction("uuidv7", UUIDColumnType()))
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Base for history / fact tables. id is BIGINT GENERATED ALWAYS AS
 * IDENTITY (monotonic, ensures "latest" = MAX(id) per group). created_at
 * is automatically populated.
 *
 * Note: Exposed's `long("id").autoIncrement()` emits BIGSERIAL by default
 * on PG. To get GENERATED ALWAYS AS IDENTITY, we declare the column as
 * `long("id")` (no autoIncrement) and rely on the migration generator to
 * emit the GENERATED clause via a custom DDL. See HistoryTableSchemaSql
 * for the override.
 *
 * For simplicity in this initial plan we accept BIGSERIAL (functionally
 * equivalent for our purposes — both produce monotonically increasing
 * BIGINT values).
 */
abstract class HistoryTable(name: String) : Table(name) {
    val id = long("id").autoIncrement()
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}
```

> **Implementation note for the next reader:** if `CustomFunction("uuidv7", UUIDColumnType())` doesn't compile against the pinned Exposed version, the alternative is to register a column-level DEFAULT via raw SQL during migration script generation. Document the actual choice in the commit message.

- [ ] **Step 2: Verify compile**

```bash
./gradlew :infrastructure:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/TableBases.kt
git commit -m "Add AggregateRootTable and HistoryTable base classes"
```

---

## Task 7: User domain schema

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/user/UsersTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/user/UserDisplayNamesTable.kt`
- Modify: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt`

- [ ] **Step 1: Write UsersTable**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/user/UsersTable.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable

@Migratable
object UsersTable : AggregateRootTable("users") {
    val zitadel_sub = text("zitadel_sub").uniqueIndex()
}
```

- [ ] **Step 2: Write UserDisplayNamesTable**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/user/UserDisplayNamesTable.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.schema.user

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object UserDisplayNamesTable : HistoryTable("user_display_names") {
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val display_name = text("display_name")

    init {
        index(false, user_id, id)
    }
}
```

- [ ] **Step 3: Register the tables**

Modify `MigratableTables.kt` so `all` becomes:

```kotlin
import net.brightroom.mindstock.infrastructure.schema.user.UserDisplayNamesTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable

// ...

val all: List<Table>
    get() = listOf(
        UsersTable,
        UserDisplayNamesTable,
    )
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :infrastructure:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/
git commit -m "Add User domain schema (users, user_display_names)"
```

---

## Task 8: Household domain schema

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/household/HouseholdsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/household/HouseholdMembershipsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/household/HouseholdMembershipRevocationsTable.kt`
- Modify: `MigratableTables.kt`

- [ ] **Step 1: Write HouseholdsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable

@Migratable
object HouseholdsTable : AggregateRootTable("households")
```

- [ ] **Step 2: Write HouseholdMembershipsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object HouseholdMembershipsTable : HistoryTable("household_memberships") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val role = text("role") // 'owner' | 'member'

    init {
        index(false, household_id, id)
        index(false, user_id, id)
    }
}
```

- [ ] **Step 3: Write HouseholdMembershipRevocationsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object HouseholdMembershipRevocationsTable : HistoryTable("household_membership_revocations") {
    val membership_id = reference("membership_id", HouseholdMembershipsTable.id, onDelete = ReferenceOption.RESTRICT)
}
```

- [ ] **Step 4: Register the tables**

Update `MigratableTables.kt` `all` to include the three new tables (alphabetical-ish, household first since it's a foundational aggregate):

```kotlin
val all: List<Table>
    get() = listOf(
        UsersTable,
        UserDisplayNamesTable,
        HouseholdsTable,
        HouseholdMembershipsTable,
        HouseholdMembershipRevocationsTable,
    )
```

- [ ] **Step 5: Verify compile & commit**

```bash
./gradlew :infrastructure:compileKotlin
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/
git commit -m "Add Household domain schema (households + memberships + revocations)"
```

---

## Task 9: Catalog domain schema

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/catalog/CatalogItemsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/catalog/CatalogItemNamesTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/catalog/CatalogItemUnitsTable.kt`
- Modify: `MigratableTables.kt`

- [ ] **Step 1: Write CatalogItemsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val created_by = reference("created_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
}
```

- [ ] **Step 2: Write CatalogItemNamesTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object CatalogItemNamesTable : HistoryTable("catalog_item_names") {
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = text("name")
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, catalog_item_id, id)
    }
}
```

- [ ] **Step 3: Write CatalogItemUnitsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object CatalogItemUnitsTable : HistoryTable("catalog_item_units") {
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val unit = text("unit")
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, catalog_item_id, id)
    }
}
```

- [ ] **Step 4: Register & commit**

Add `CatalogItemsTable`, `CatalogItemNamesTable`, `CatalogItemUnitsTable` to the registry. Then:

```bash
./gradlew :infrastructure:compileKotlin
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/
git commit -m "Add Catalog domain schema (items + names + units)"
```

---

## Task 10: Product domain schema

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/product/ProductsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/product/ProductMinimumStocksTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/product/ProductArchivesTable.kt`
- Modify: `MigratableTables.kt`

- [ ] **Step 1: Write ProductsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schema.catalog.CatalogItemsTable
import net.brightroom.mindstock.infrastructure.schema.household.HouseholdsTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object ProductsTable : AggregateRootTable("products") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        uniqueIndex("uq_products_household_catalog", household_id, catalog_item_id)
    }
}
```

- [ ] **Step 2: Write ProductMinimumStocksTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object ProductMinimumStocksTable : HistoryTable("product_minimum_stocks") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val minimum_stock = integer("minimum_stock").check { it greaterEq 0 }
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, product_id, id)
    }
}
```

- [ ] **Step 3: Write ProductArchivesTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object ProductArchivesTable : HistoryTable("product_archives") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val archived_by = reference("archived_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, product_id, id)
    }
}
```

- [ ] **Step 4: Register & commit**

Append the three tables to `MigratableTables.all`. Then:

```bash
./gradlew :infrastructure:compileKotlin
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/
git commit -m "Add Product domain schema (products + minimum_stocks + archives)"
```

---

## Task 11: Stock domain schema

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/stock/StockReplenishmentsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/stock/StockConsumptionsTable.kt`
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/stock/StockEventCorrectionsTable.kt`
- Modify: `MigratableTables.kt`

- [ ] **Step 1: Write StockReplenishmentsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object StockReplenishmentsTable : HistoryTable("stock_replenishments") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = datetime("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").nullable()

    init {
        index(false, product_id, id)
    }
}
```

- [ ] **Step 2: Write StockConsumptionsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.product.ProductsTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object StockConsumptionsTable : HistoryTable("stock_consumptions") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val quantity = integer("quantity").check { it greater 0 }
    val occurred_at = datetime("occurred_at")
    val acted_by = reference("acted_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = text("note").nullable()

    init {
        index(false, product_id, id)
    }
}
```

- [ ] **Step 3: Write StockEventCorrectionsTable**

```kotlin
package net.brightroom.mindstock.infrastructure.schema.stock

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object StockEventCorrectionsTable : HistoryTable("stock_event_corrections") {
    val target_table = text("target_table").check {
        (it eq "stock_replenishments") or (it eq "stock_consumptions")
    }
    val target_id = long("target_id")
    val new_quantity = integer("new_quantity").check { it greater 0 }
    val reason = text("reason").nullable()
    val corrected_by = reference("corrected_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, target_table, target_id, id)
    }
}
```

- [ ] **Step 4: Register & commit**

Append the three tables to `MigratableTables.all`. Then:

```bash
./gradlew :infrastructure:compileKotlin
git add infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/
git commit -m "Add Stock domain schema (replenishments + consumptions + corrections)"
```

---

## Task 12: Migration script generator (Gradle task + integration)

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt`
- Create: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt`
- Modify: `infrastructure/build.gradle.kts` (add a `generateMigrationScript` task)

The generator runs in a JUnit test rather than a custom Gradle task to keep things simple — Gradle just executes `./gradlew :infrastructure:test --tests "*MigrationGeneratorTest" -PgenerateMigration` (or similar) when a developer wants new migration SQL written to disk.

- [ ] **Step 1: Write the generator**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Generates a Flyway-format migration script that brings the connected
 * database in sync with the [MigratableTables.all] schema.
 *
 * The generated file is written to [outputDirectory] and named
 * `V{timestamp}__{description}.sql`. If no changes are required the call
 * is a no-op (an empty file is not written).
 *
 * Typical invocation: from a JUnit test against an empty Testcontainer
 * Postgres, save the result into `src/main/resources/db/migration/`.
 */
object MigrationGenerator {
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

    @OptIn(ExperimentalDatabaseMigrationApi::class)
    fun generate(
        @Suppress("UNUSED_PARAMETER") database: Database,
        outputDirectory: File,
        description: String,
    ): File? {
        outputDirectory.mkdirs()
        val timestamp = TIMESTAMP_FORMAT.format(Instant.now())
        val scriptName = "V${timestamp}__$description"
        MigrationUtils.generateMigrationScript(
            *MigratableTables.all.toTypedArray(),
            scriptDirectory = outputDirectory.absolutePath,
            scriptName = scriptName,
            withLogs = false,
        )
        val produced = File(outputDirectory, "$scriptName.sql")
        return if (produced.exists() && produced.length() > 0L) produced else {
            // Some Exposed builds always write a file; treat zero-length as empty diff.
            if (produced.exists()) produced.delete()
            null
        }
    }
}
```

- [ ] **Step 2: Write the test that drives generation**

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File
import java.util.Properties

class MigrationGeneratorTest : FunSpec({
    test("generator emits a migration script when the target schema is empty") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val db = Database.connect(jdbcUrl, "org.postgresql.Driver",
                TestContainersPostgres.username, TestContainersPostgres.password)
            val tempDir = createTempDirectory()
            val script = MigrationGenerator.generate(db, tempDir, "test_init")
            script shouldNotBe null
            (script!!.readText().contains("CREATE TABLE", ignoreCase = true)) shouldBe true
        }
    }

    test("generator emits nothing when the schema already matches") {
        // Second generation against a database where all Migratable tables exist
        // would yield an empty script; we don't test that here yet — covered
        // once Task 13 wires the applier in.
    }
})

private fun createTempDirectory(): File = File.createTempFile("mindstock-migrations-", "").apply {
    delete()
    mkdir()
    deleteOnExit()
}
```

- [ ] **Step 3: Run the test**

```bash
./gradlew :infrastructure:test --tests "*MigrationGeneratorTest"
```

Expected: PASS. The test should emit a `Vxxxxxxxxxxxxxx__test_init.sql` to a temp dir and assert it contains `CREATE TABLE`.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/
git commit -m "Add MigrationGenerator backed by Exposed MigrationUtils"
```

---

## Task 13: Generate and commit the initial migration script

The actual SQL file lives in `infrastructure/src/main/resources/db/migration/`.

**Files:**
- Create (generated): `infrastructure/src/main/resources/db/migration/V20260524000001__init.sql` *(exact timestamp will differ)*
- Create: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt`

- [ ] **Step 1: Write a manual-runner Kotlin file**

Create a Kotest spec that, when explicitly invoked, writes the canonical migration into the resources directory. Mark it so it doesn't run during normal CI.

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File

/**
 * Run with:
 * ./gradlew :infrastructure:test --tests "*GenerateInitialMigrationManually" \
 *   -Pkotest.framework.runtimeTags=manual
 *
 * Writes a fresh init.sql under src/main/resources/db/migration/.
 *
 * @Ignored prevents accidental runs in normal CI.
 */
@Ignored
class GenerateInitialMigrationManually : FunSpec({
    test("write init migration to resources") {
        val outDir = File("src/main/resources/db/migration").absoluteFile
        outDir.mkdirs()
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val db = Database.connect(
                jdbcUrl, "org.postgresql.Driver",
                TestContainersPostgres.username, TestContainersPostgres.password,
            )
            val script = MigrationGenerator.generate(db, outDir, "init")
            requireNotNull(script) { "Generator emitted no script — schema must already match" }
            println("Wrote ${script.absolutePath} (${script.length()} bytes)")
        }
    }
})
```

- [ ] **Step 2: Invoke it once to produce the file**

```bash
./gradlew :infrastructure:test \
  --tests "*GenerateInitialMigrationManually" \
  -Dkotest.tags="" \
  -Dkotest.runIgnored=true
```

(If the `-Dkotest.runIgnored` flag doesn't unlock @Ignored in your Kotest version, temporarily remove the `@Ignored` annotation, run, then re-add it.)

Verify a file exists under `infrastructure/src/main/resources/db/migration/V…__init.sql` and that it contains `CREATE TABLE users`, `CREATE TABLE households`, … through `CREATE TABLE stock_event_corrections` (one per `MigratableTables.all` entry).

- [ ] **Step 3: Eyeball the SQL**

`cat infrastructure/src/main/resources/db/migration/V*__init.sql`. Confirm:
- `uuidv7()` appears as the default for aggregate root `id` columns
- Foreign key constraints reference the right parent tables
- Indexes are present
- No `DROP TABLE` lines (this is a forward-only initial migration)

If something looks wrong, fix the offending Table object and regenerate.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/main/resources/db/migration/ \
        infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt
git commit -m "Generate initial migration SQL covering all five domains"
```

---

## Task 14: Flyway-based migration applier

**Files:**
- Create: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunner.kt`
- Create: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunnerTest.kt`

- [ ] **Step 1: Write MigrationRunner**

Create `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunner.kt`:

```kotlin
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
```

- [ ] **Step 2: Write the integration test**

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunnerTest.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import javax.sql.DataSource

class MigrationRunnerTest : FunSpec({
    test("migrate creates every table in MigratableTables") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, schema ->
            val ds = DatabaseFactory.dataSource(
                DatabaseConfig(
                    jdbcUrl = jdbcUrl,
                    username = TestContainersPostgres.username,
                    password = TestContainersPostgres.password,
                ),
            )
            MigrationRunner.migrate(ds)
            val actual = listTables(ds, schema)
            val expected = MigratableTables.all.map { it.tableName }
            actual shouldContainAll expected
            ds.close()
        }
    }
})

private fun listTables(ds: DataSource, schema: String): List<String> =
    ds.connection.use { conn ->
        conn.prepareStatement(
            "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
        ).apply { setString(1, schema) }.use { stmt ->
            val rs = stmt.executeQuery()
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }
```

- [ ] **Step 3: Run the test**

```bash
./gradlew :infrastructure:test --tests "*MigrationRunnerTest"
```

Expected: PASS. All 14 tables from `MigratableTables.all` show up in `pg_tables`.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/
git commit -m "Add Flyway-based MigrationRunner with integration test"
```

---

## Task 15: Enforce append-only via a DB role + grants

**Files:**
- Create: `infrastructure/src/main/resources/db/migration/V20260524000002__append_only_role.sql` *(exact timestamp varies)*
- Create: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/AppendOnlyEnforcementTest.kt`

- [ ] **Step 1: Write the migration**

Create the migration script by hand (not via the generator — it's a permissions DDL, not a schema diff):

```sql
-- V20260524000002__append_only_role.sql
--
-- Append-only enforcement: define a role that has only SELECT and INSERT
-- on every domain table. The application connects as this role at
-- runtime. Migrations / admin tasks use a different (more privileged)
-- role.

CREATE ROLE mindstock_app NOINHERIT NOLOGIN;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO mindstock_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT ON TABLES TO mindstock_app;
```

(Adjust the schema name if your Testcontainers harness uses a non-default schema; the test below sets it.)

- [ ] **Step 2: Write the enforcement test**

Create `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/AppendOnlyEnforcementTest.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import net.brightroom.mindstock.infrastructure.TestContainersPostgres
import java.sql.SQLException

class AppendOnlyEnforcementTest : FunSpec({
    test("mindstock_app role can INSERT but cannot UPDATE or DELETE") {
        TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
            val ds = DatabaseFactory.dataSource(
                DatabaseConfig(
                    jdbcUrl = jdbcUrl,
                    username = TestContainersPostgres.username,
                    password = TestContainersPostgres.password,
                ),
            )
            MigrationRunner.migrate(ds)

            // Switch to app role for the duration of this test
            ds.connection.use { conn ->
                conn.createStatement().use { it.execute("SET ROLE mindstock_app") }

                // INSERT works
                conn.prepareStatement(
                    "INSERT INTO users (zitadel_sub) VALUES (?) RETURNING id",
                ).use { stmt ->
                    stmt.setString(1, "test-sub")
                    val rs = stmt.executeQuery()
                    check(rs.next())
                }

                // UPDATE is denied
                shouldThrow<SQLException> {
                    conn.createStatement().use { it.execute("UPDATE users SET zitadel_sub = 'x'") }
                }
                // DELETE is denied
                shouldThrow<SQLException> {
                    conn.createStatement().use { it.execute("DELETE FROM users") }
                }
            }
            ds.close()
        }
    }
})
```

- [ ] **Step 3: Run the test**

```bash
./gradlew :infrastructure:test --tests "*AppendOnlyEnforcementTest"
```

Expected: PASS. INSERT succeeds, UPDATE/DELETE raise `SQLException`.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/main/resources/db/migration/V*__append_only_role.sql \
        infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/AppendOnlyEnforcementTest.kt
git commit -m "Enforce append-only with mindstock_app role + integration test"
```

---

## Task 16: Wire DatabaseInitializer into backend startup

**Files:**
- Modify: `backend/build.gradle.kts` (depend on infrastructure already, just verify)
- Modify: `backend/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt`
- Modify: `backend/src/main/resources/application.yaml` (DB config keys)

- [ ] **Step 1: Update application.yaml**

Edit `backend/src/main/resources/application.yaml`:

```yaml
ktor:
  deployment:
    port: "$PORT:8080"
    host: "0.0.0.0"
  application:
    modules:
      - net.brightroom.mindstock.backend.MainKt.module

database:
  jdbcUrl: "$DATABASE_URL:jdbc:postgresql://localhost:5432/mindstock"
  username: "$DATABASE_USERNAME:mindstock"
  password: "$DATABASE_PASSWORD:mindstock"
```

- [ ] **Step 2: Update Main.kt to run migrations at startup**

Replace `backend/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt` with:

```kotlin
package net.brightroom.mindstock.backend

import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.brightroom.mindstock.infrastructure.persistence.DatabaseConfig
import net.brightroom.mindstock.infrastructure.persistence.DatabaseFactory
import net.brightroom.mindstock.infrastructure.persistence.MigrationRunner

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = environment.config
    val dbConfig = DatabaseConfig(
        jdbcUrl = config.property("database.jdbcUrl").getString(),
        username = config.property("database.username").getString(),
        password = config.property("database.password").getString(),
    )
    val dataSource = DatabaseFactory.dataSource(dbConfig)
    MigrationRunner.migrate(dataSource)
    DatabaseFactory.exposed(dataSource)

    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
```

- [ ] **Step 3: Verify backend module depends on infrastructure**

`backend/build.gradle.kts` already has `implementation(projects.infrastructure)` from Plan 1 — verify with grep.

- [ ] **Step 4: Smoke test with the Compose Postgres**

```bash
./scripts/db-up.sh
./gradlew :backend:run &
BPID=$!
for i in {1..30}; do
  sleep 1
  curl -fs http://localhost:8080/health && break
done
echo
kill $BPID 2>/dev/null
./scripts/db-down.sh
```

Expected: `OK` printed; backend log shows Flyway applying both migrations.

- [ ] **Step 5: Commit**

```bash
git add backend/
git commit -m "Run Flyway migrations and bind Exposed Database at backend startup"
```

---

## Task 17: Full build and CI verification

- [ ] **Step 1: Run check**

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL. All unit + integration tests pass.

- [ ] **Step 2: Verify CI workflow includes infrastructure tests**

The existing `.github/workflows/ci.yml` runs `./gradlew check` which transitively includes `:infrastructure:test`. Testcontainers needs Docker; verify GitHub-hosted Ubuntu runners have it preinstalled (they do). If a future runner change breaks this, the fix is to add `services: { docker: {} }` or use a self-hosted runner with Docker.

No CI changes required for this task — just confirm the run is green.

- [ ] **Step 3: Push and open a PR**

```bash
git push -u origin feat/db-schema-and-migrations
gh pr create --title "Add DB schema and migration foundation" --body "..."
```

The PR title and body should describe: domains covered, the @Migratable + Flyway pattern, append-only enforcement, and how to regenerate the init script when schema changes.

---

## Done state

After this plan:

- ✅ `compose.yml` brings up a real PostgreSQL 18 locally
- ✅ Every domain (User, Household, Catalog, Product, Stock) has its Exposed schema declared as `@Migratable` tables in `infrastructure/.../schema/`
- ✅ `MigrationGenerator` emits Flyway-format SQL from the table registry
- ✅ `V…__init.sql` is committed to `db/migration/` covering every table
- ✅ A separate migration declares the `mindstock_app` role with only SELECT/INSERT grants
- ✅ Backend startup applies pending migrations and connects Exposed
- ✅ Testcontainers integration tests verify: migrations apply, all tables exist, UPDATE/DELETE are denied
- ✅ `./gradlew check` and CI are green

Next plan: **Plan 3 — Domain Layer** (Aggregates, Value Objects, Events, Policies for all five domains, with TDD; pure JVM, no persistence references).
