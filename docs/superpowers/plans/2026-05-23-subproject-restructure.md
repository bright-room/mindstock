# Subproject Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-organize the Gradle module layout per `docs/superpowers/specs/2026-05-23-subproject-restructure-design.md`: introduce `:rpc`, move server-side modules under `:backend`, and split `:infrastructure` into `schemas` + `migration:{annotation,detector,generator,executor}` submodules.

**Architecture:** Pure structural refactor. No behavior change. Files move via `git mv` to preserve history; package names follow the new module layout. Tests stay green at every task boundary.

**Tech Stack:** Gradle (typesafe project accessors), Kotlin Multiplatform / JVM, Ktor, Koin, Exposed, Flyway, Testcontainers, Kotest, Spotless. Build conventions live in `build-logic/` as `net.brightroom.mindstock.*` plugins.

---

## Pre-flight

- Working on `main` (or a feature branch created from `main`). Spec already committed (`636beba`).
- Verify a clean baseline before starting:

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

If anything is red here, stop — fix it before continuing. The plan assumes a green starting point.

---

## File / Module Layout After This Plan

```
build-logic/src/main/kotlin/
  net.brightroom.mindstock.kotlin-jvm.gradle.kts            (existing)
  net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts   (NEW, Task 8)
  net.brightroom.mindstock.kmp-shared.gradle.kts            (existing)
  net.brightroom.mindstock.ktor-server.gradle.kts           (existing)
  net.brightroom.mindstock.compose-web.gradle.kts           (existing)
  net.brightroom.mindstock.spotless.gradle.kts              (existing)

domain/                            (unchanged)
shared/                            (build.gradle.kts: drop rpc.plugin in Task 9)
frontend/                          (build.gradle.kts: switch projects.shared → projects.rpc in Task 1)

rpc/                               (NEW, Task 1)
  build.gradle.kts
  src/commonMain/kotlin/net/brightroom/mindstock/rpc/Placeholder.kt

backend/
  api/                             (former :backend, moved Task 3)
    build.gradle.kts
    src/main/kotlin/net/brightroom/mindstock/backend/Main.kt
    src/main/kotlin/net/brightroom/mindstock/backend/Database.kt        (moved from infrastructure, Task 3)
    src/main/resources/{application.yaml, logback.xml}
  application/                     (former :application, moved Task 2)
    build.gradle.kts
    src/main/kotlin/net/brightroom/mindstock/application/Placeholder.kt
  infrastructure/
    schemas/                       (NEW, Task 4)
      build.gradle.kts
      src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/
        TableBases.kt
        catalog/{CatalogItemsTable.kt, CatalogItemRevisionsTable.kt}
        user/{UsersTable.kt, UserDisplayNamesTable.kt}
        household/{HouseholdsTable.kt, HouseholdMembershipsTable.kt, HouseholdMembershipRevocationsTable.kt}
        product/{ProductsTable.kt, ProductMinimumStocksTable.kt, ProductArchivesTable.kt}
        stock/{StockReplenishmentsTable.kt, StockConsumptionsTable.kt, StockReplenishmentCorrectionsTable.kt, StockConsumptionCorrectionsTable.kt}
    migration/
      annotation/                  (NEW, Task 5)
        build.gradle.kts
        src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/annotation/Migratable.kt
      detector/                    (NEW, Task 6)
        build.gradle.kts
        src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector/MigratableTables.kt
      generator/                   (NEW, Task 7)
        build.gradle.kts
        src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGenerator.kt
        src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/
          MigrationGeneratorTest.kt
          GenerateInitialMigrationManually.kt
      executor/                    (NEW, Task 8)
        build.gradle.kts
        src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/MigrationRunner.kt
        src/main/resources/db/migration/{V20260523000001__append_only_role.sql, V20260523071825__init.sql}
        src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestContainersPostgres.kt
        src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/
          MigrationRunnerTest.kt
          AppendOnlyEnforcementTest.kt
          TestContainersSmokeTest.kt
```

`Database.kt` (the Hikari + Exposed connection helpers in the current `infrastructure/persistence`) moves to `:backend:api` because Ktor's `Main.kt` is the only consumer and reads its config from `environment.config`. This is a small clarification over the design doc, which left runtime-connection helpers unspecified. If a future task adds repository implementations, they may want this lifted into a dedicated `:backend:infrastructure:persistence` module — out of scope here.

---

## Task 1: Introduce `:rpc` module

**Files:**
- Create: `rpc/build.gradle.kts`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/Placeholder.kt`
- Modify: `settings.gradle.kts`
- Modify: `frontend/build.gradle.kts`

- [ ] **Step 1.1: Create `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/Placeholder.kt`**

```kotlin
package net.brightroom.mindstock.rpc

internal const val PLACEHOLDER = "rpc"
```

- [ ] **Step 1.2: Create `rpc/build.gradle.kts`**

```kotlin
plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.rpc.core)
                implementation(libs.kotlinx.rpc.serialization.json)
            }
        }
    }
}
```

- [ ] **Step 1.3: Register `:rpc` in `settings.gradle.kts`**

Edit the `include(...)` block at the bottom to add `":rpc"` after `":shared"`:

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":application",
    ":infrastructure",
    ":backend",
    ":frontend",
)
```

- [ ] **Step 1.4: Point `:frontend` at `:rpc`**

Edit `frontend/build.gradle.kts` `commonMain.dependencies` block. Replace `implementation(projects.shared)` with `implementation(projects.rpc)`:

```kotlin
        commonMain.dependencies {
            implementation(projects.rpc)

            implementation(compose.runtime)
```

(rest of the block unchanged.)

- [ ] **Step 1.5: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. The new `:rpc` module compiles, `:frontend` resolves it.

- [ ] **Step 1.6: Commit**

```bash
git add rpc settings.gradle.kts frontend/build.gradle.kts
git commit -m "refactor: introduce :rpc module and switch :frontend to depend on it"
```

---

## Task 2: Move `:application` → `:backend:application`

**Files:**
- Move: `application/` → `backend/application/`
- Modify: `settings.gradle.kts`
- Modify: `backend/application/build.gradle.kts` (path-only changes are not required, but verify dependency notation)
- Modify: `backend/build.gradle.kts` (`projects.application` → `projects.backend.application`)

- [ ] **Step 2.1: Move the directory with `git mv` to preserve history**

```bash
mkdir -p backend
git mv application backend/application
```

Verify with `ls backend/` → should now show `application` alongside the existing `src/`, `build.gradle.kts`, etc.

- [ ] **Step 2.2: Update `settings.gradle.kts`**

Replace `":application",` with `":backend:application",` in the `include(...)` block:

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":infrastructure",
    ":backend",
    ":frontend",
)
```

- [ ] **Step 2.3: Update `backend/build.gradle.kts` consumer**

Open `backend/build.gradle.kts`. In the `dependencies { ... }` block, change:

```kotlin
    implementation(projects.application)
```

to:

```kotlin
    implementation(projects.backend.application)
```

- [ ] **Step 2.4: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. `:backend:application` now lives under `backend/application/`.

- [ ] **Step 2.5: Commit**

```bash
git add -A
git commit -m "refactor: move :application under :backend:application"
```

---

## Task 3: Rename `:backend` → `:backend:api` and move `Database.kt`

This task is bigger because the current `:backend` directory holds both the future parent-path `backend/` and source. We move its source into `backend/api/`, and inline the `Database.kt` consumer relocation while we're touching the file.

**Files:**
- Move: `backend/src/` → `backend/api/src/`
- Move: `backend/build.gradle.kts` → `backend/api/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Database.kt` → `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Database.kt`
- Modify: `settings.gradle.kts`
- Modify: `backend/api/build.gradle.kts` (mainClass already correct; verify it points to `net.brightroom.mindstock.backend.MainKt`)
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt` (imports change from `infrastructure.persistence.*` to local package for `Database.kt` symbols)
- Modify: `Makefile`, `compose.yml`, `backend/api/src/main/resources/application.yaml` only if they reference module path — they don't currently (they use the main class name).

- [ ] **Step 3.1: Move backend source into `backend/api/`**

```bash
mkdir -p backend/api
git mv backend/src backend/api/src
git mv backend/build.gradle.kts backend/api/build.gradle.kts
```

- [ ] **Step 3.2: Move `Database.kt` from infrastructure to `backend/api`**

```bash
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Database.kt \
       backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Database.kt
```

- [ ] **Step 3.3: Update package declaration of `Database.kt`**

Open `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Database.kt`. Change the first line:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence
```

to:

```kotlin
package net.brightroom.mindstock.backend
```

- [ ] **Step 3.4: Update imports in `Main.kt`**

Open `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt`. Remove these imports:

```kotlin
import net.brightroom.mindstock.infrastructure.persistence.ExposedDataSourceProperties
import net.brightroom.mindstock.infrastructure.persistence.buildHikariDataSource
import net.brightroom.mindstock.infrastructure.persistence.connectExposed
```

`Database.kt` now sits in the same `net.brightroom.mindstock.backend` package as `Main.kt`, so no replacement imports are needed.

Keep the `MigrationRunner` import for now (it still resolves to `net.brightroom.mindstock.infrastructure.persistence.MigrationRunner` from `:infrastructure`). Task 8 will update it.

- [ ] **Step 3.5: Rewire `settings.gradle.kts`**

Replace `":backend",` with `":backend:api",` in the `include(...)` block:

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 3.6: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. The Ktor entrypoint is now at `:backend:api` and `Database.kt` belongs to that module.

- [ ] **Step 3.7: Smoke-test the app actually starts**

(Requires docker compose running locally; skip if Postgres is unavailable.)

```bash
make up
./gradlew :backend:api:run
```

Hit `http://localhost:8080/health` in another terminal: should return `OK`. Stop with Ctrl-C, then `make down`.

- [ ] **Step 3.8: Commit**

```bash
git add -A
git commit -m "refactor: rename :backend → :backend:api and move Database.kt into it"
```

---

## Task 4: Introduce `:backend:infrastructure:schemas`

**Files:**
- Create: `backend/infrastructure/schemas/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/**` → `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/**`
- Modify: package declarations in moved files (`infrastructure.schema*` → `infrastructure.schemas*`)
- Modify: `settings.gradle.kts`
- Modify: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt` (import updates — temporary, until Task 6 moves the file out of `:infrastructure`)

- [ ] **Step 4.1: Move schema sources**

```bash
mkdir -p backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/TableBases.kt \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/TableBases.kt
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/catalog \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/catalog
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/user \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/user
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/household \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/household
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/product \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/product
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/schema/stock \
       backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/stock
```

- [ ] **Step 4.2: Rewrite package declarations in moved files**

For every moved `.kt` file, change `package net.brightroom.mindstock.infrastructure.schema` (and subpackages like `.catalog`, `.user`, etc.) to `infrastructure.schemas` (and `.schemas.catalog`, `.schemas.user`, etc.).

Run this `sed` over the moved directory tree:

```bash
find backend/infrastructure/schemas/src/main/kotlin -name '*.kt' -print0 | \
  xargs -0 sed -i '' -E 's|^package net\.brightroom\.mindstock\.infrastructure\.schema(\b\|\.)|package net.brightroom.mindstock.infrastructure.schemas\1|'
```

Open one file (`TableBases.kt`) and one subpackage file (`catalog/CatalogItemsTable.kt`) to confirm the package line now reads `package net.brightroom.mindstock.infrastructure.schemas` and `package net.brightroom.mindstock.infrastructure.schemas.catalog` respectively.

- [ ] **Step 4.3: Create `backend/infrastructure/schemas/build.gradle.kts`**

The annotation module doesn't exist yet (Task 5 creates it), so don't declare a dependency on it here. Task 6 (after annotation + detector exist) will revisit if any schema file actually applies `@Migratable`. Currently `Migratable` annotates Table objects, but the annotation will be added during Task 6 (the existing code only declares the annotation in `infrastructure.persistence` — none of the Table classes use it). So `schemas` only needs Exposed.

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)

    implementation(libs.exposed.core)
    implementation(libs.exposed.kotlin.datetime)
    api(libs.exposed.jdbc)
}
```

- [ ] **Step 4.4: Register `:backend:infrastructure:schemas` in `settings.gradle.kts`**

Add the new include:

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 4.5: Update `MigratableTables.kt` imports (still inside `:infrastructure` for now)**

Open `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt`. Replace every `infrastructure.schema.` import prefix with `infrastructure.schemas.`. There are imports for `catalog`, `household`, `product`, `stock`, and `user` subpackages — update all of them.

- [ ] **Step 4.6: Update `:infrastructure` to depend on `:backend:infrastructure:schemas`**

Open `infrastructure/build.gradle.kts`. In the `dependencies { ... }` block, add:

```kotlin
    implementation(projects.backend.infrastructure.schemas)
```

(Keep the existing dependencies.)

- [ ] **Step 4.7: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. `:backend:infrastructure:schemas` compiles; `:infrastructure`'s tests still pass.

- [ ] **Step 4.8: Commit**

```bash
git add -A
git commit -m "refactor: extract :backend:infrastructure:schemas from :infrastructure"
```

---

## Task 5: Introduce `:backend:infrastructure:migration:annotation`

**Files:**
- Create: `backend/infrastructure/migration/annotation/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Migratable.kt` → `backend/infrastructure/migration/annotation/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/annotation/Migratable.kt`
- Modify: package declaration of the moved file
- Modify: `settings.gradle.kts`
- Modify: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt` and `MigrationGenerator.kt` if they reference `Migratable` (they don't currently — verify with grep)

- [ ] **Step 5.1: Move the annotation file**

```bash
mkdir -p backend/infrastructure/migration/annotation/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/annotation
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/Migratable.kt \
       backend/infrastructure/migration/annotation/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/annotation/Migratable.kt
```

- [ ] **Step 5.2: Update the package declaration**

Open the moved file. Change:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence
```

to:

```kotlin
package net.brightroom.mindstock.infrastructure.migration.annotation
```

- [ ] **Step 5.3: Create `backend/infrastructure/migration/annotation/build.gradle.kts`**

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}
```

(No dependencies. Pure JVM annotation library.)

- [ ] **Step 5.4: Register in `settings.gradle.kts`**

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 5.5: Verify no consumers exist yet**

```bash
grep -rn 'net.brightroom.mindstock.infrastructure.persistence.Migratable\b' .
grep -rn '@Migratable\b' .
```

Both should output nothing (or only the file we just moved). If grep finds usages, update those imports to `net.brightroom.mindstock.infrastructure.migration.annotation.Migratable`.

- [ ] **Step 5.6: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.7: Commit**

```bash
git add -A
git commit -m "refactor: extract :backend:infrastructure:migration:annotation"
```

---

## Task 6: Introduce `:backend:infrastructure:migration:detector` (manual registry)

**Files:**
- Create: `backend/infrastructure/migration/detector/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt` → `backend/infrastructure/migration/detector/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector/MigratableTables.kt`
- Modify: package declaration; imports already pointing at `schemas.*` after Task 4
- Modify: `settings.gradle.kts`
- Modify: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt` import for `MigratableTables`

- [ ] **Step 6.1: Move the registry file**

```bash
mkdir -p backend/infrastructure/migration/detector/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigratableTables.kt \
       backend/infrastructure/migration/detector/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/detector/MigratableTables.kt
```

- [ ] **Step 6.2: Rewrite the package declaration**

Open the moved file. Change:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence
```

to:

```kotlin
package net.brightroom.mindstock.infrastructure.migration.detector
```

(Imports of `schemas.*` Tables — set up in Task 4 — are already correct.)

- [ ] **Step 6.3: Create `backend/infrastructure/migration/detector/build.gradle.kts`**

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.annotation)

    implementation(libs.exposed.core)
}
```

- [ ] **Step 6.4: Register in `settings.gradle.kts`**

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":backend:infrastructure:migration:detector",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 6.5: Update `MigrationGenerator.kt` to import from new location**

Open `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt`. Add the import:

```kotlin
import net.brightroom.mindstock.infrastructure.migration.detector.MigratableTables
```

(No old import existed because `MigratableTables` was in the same package; making it explicit since the package changed.)

- [ ] **Step 6.6: Update `:infrastructure` to depend on `:backend:infrastructure:migration:detector`**

Open `infrastructure/build.gradle.kts`. Add to the `dependencies { ... }` block:

```kotlin
    implementation(projects.backend.infrastructure.migration.detector)
```

- [ ] **Step 6.7: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. MigrationGenerator now imports `MigratableTables` from the detector module.

- [ ] **Step 6.8: Commit**

```bash
git add -A
git commit -m "refactor: extract :backend:infrastructure:migration:detector"
```

---

## Task 7: Introduce `:backend:infrastructure:migration:generator`

**Files:**
- Create: `backend/infrastructure/migration/generator/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt` → `backend/infrastructure/migration/generator/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGenerator.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt` → `backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGeneratorTest.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt` → `backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/GenerateInitialMigrationManually.kt`
- Modify: package declarations on all moved files
- Modify: imports inside the moved files (`infrastructure.persistence.MigrationGenerator` → `infrastructure.migration.generator.MigrationGenerator`; `infrastructure.TestContainersPostgres` will be addressed in Task 8 — for now both tests still import it from `:infrastructure` test classpath, but they'll lose that classpath once we delete `:infrastructure` in Task 10. We pre-fix by declaring a `testImplementation(testFixtures(...))` dep on `:executor` in this module's build file at Step 7.3, but executor's testFixtures don't exist yet — so we keep imports unchanged here and finalize wiring in Task 8 once executor exists)

> **Ordering note:** Because the generator tests depend on `TestContainersPostgres` (which Task 8 lifts into the executor's test-fixtures source set), we cannot fully wire the generator tests until after Task 8. To keep this plan TDD-friendly:
> 1. In this task we move generator's *main source* (MigrationGenerator.kt) only, plus the build file.
> 2. Test files stay in `:infrastructure/src/test/` for one more task.
> 3. In Task 8, after the executor + testFixtures source set exist, we relocate the generator's tests and wire `testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))` into the generator's build.

So the moves below are *main only*.

- [ ] **Step 7.1: Move main source**

```bash
mkdir -p backend/infrastructure/migration/generator/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/generator
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGenerator.kt \
       backend/infrastructure/migration/generator/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGenerator.kt
```

- [ ] **Step 7.2: Update package declaration and internal imports in `MigrationGenerator.kt`**

Open the moved file. Change:

```kotlin
package net.brightroom.mindstock.infrastructure.persistence
```

to:

```kotlin
package net.brightroom.mindstock.infrastructure.migration.generator
```

Imports of `MigratableTables` (added in Task 6.5) already point at the detector package — no change needed.

- [ ] **Step 7.3: Create `backend/infrastructure/migration/generator/build.gradle.kts`**

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
}
```

(Tests will be added in Task 8 along with the testFixtures dependency.)

- [ ] **Step 7.4: Register in `settings.gradle.kts`**

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":backend:infrastructure:migration:detector",
    ":backend:infrastructure:migration:generator",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 7.5: Update the still-in-`:infrastructure` tests to import the moved class**

Open `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt` and `GenerateInitialMigrationManually.kt`. Wherever they import `net.brightroom.mindstock.infrastructure.persistence.MigrationGenerator`, change to `net.brightroom.mindstock.infrastructure.migration.generator.MigrationGenerator`.

- [ ] **Step 7.6: Make `:infrastructure` depend on `:backend:infrastructure:migration:generator` (so its tests still compile)**

Open `infrastructure/build.gradle.kts`. Add:

```kotlin
    implementation(projects.backend.infrastructure.migration.generator)
```

- [ ] **Step 7.7: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. MigrationGenerator now lives in its own module; its tests still run from `:infrastructure` for one more task.

- [ ] **Step 7.8: Commit**

```bash
git add -A
git commit -m "refactor: extract :backend:infrastructure:migration:generator (main source)"
```

---

## Task 8: Introduce `:backend:infrastructure:migration:executor` (incl. testcontainers convention plugin and test moves)

This is the biggest task. It pulls in: the Flyway runner, the Flyway SQL resources, all the testcontainers-using tests across both `executor` and `generator`, a new testFixtures source set in `executor`, and a build-logic convention plugin that owns the Docker socket / Ryuk / kotest manual-tag configuration.

**Files:**
- Create: `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts`
- Create: `backend/infrastructure/migration/executor/build.gradle.kts`
- Move: `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunner.kt` → `backend/infrastructure/migration/executor/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/MigrationRunner.kt`
- Move: `infrastructure/src/main/resources/db/migration/*.sql` → `backend/infrastructure/migration/executor/src/main/resources/db/migration/`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersPostgres.kt` → `backend/infrastructure/migration/executor/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestContainersPostgres.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersSmokeTest.kt` → `backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestContainersSmokeTest.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunnerTest.kt` → `backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/MigrationRunnerTest.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/AppendOnlyEnforcementTest.kt` → `backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/AppendOnlyEnforcementTest.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt` → `backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGeneratorTest.kt`
- Move: `infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt` → `backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/GenerateInitialMigrationManually.kt`
- Modify: `backend/infrastructure/migration/generator/build.gradle.kts` (add test deps + testFixtures dep on executor + convention plugin)
- Modify: package declarations of all moved Kotlin files
- Modify: import references inside moved files (e.g., `net.brightroom.mindstock.infrastructure.TestContainersPostgres` → `net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres`)
- Modify: `backend/api/build.gradle.kts` (`projects.infrastructure` → `projects.backend.infrastructure.migration.executor` + `projects.backend.infrastructure.schemas`)
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt` import of `MigrationRunner`
- Modify: `settings.gradle.kts`

- [ ] **Step 8.1: Create the testcontainers convention plugin**

Create `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts`:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

tasks.withType<Test>().configureEach {
    // On macOS with a non-default Docker context, Testcontainers cannot
    // auto-detect the socket. The socket is at /var/run/docker.sock on both
    // macOS Docker Desktop (via symlink) and GitHub Actions Ubuntu runners.
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    jvmArgs(
        "-Dtc.host=unix:///var/run/docker.sock",
        "-Dtestcontainers.dockerhost=unix:///var/run/docker.sock",
        "-Dapi.version=1.41",
    )
    // Ryuk fails on Docker Desktop when the socket path isn't resolved before
    // strategy selection. Containers are still stopped via GenericContainer's
    // JVM shutdown hook.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // Exclude tests tagged "manual" by default. Override on the command line
    // with -Dkotest.tags.exclude= (empty) to run GenerateInitialMigrationManually
    // and similar maintenance specs.
    val kotestTagsExclude = providers.systemProperty("kotest.tags.exclude").orElse("manual")
    systemProperty("kotest.tags.exclude", kotestTagsExclude.get())
}
```

- [ ] **Step 8.2: Move executor main source**

```bash
mkdir -p backend/infrastructure/migration/executor/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/executor
git mv infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunner.kt \
       backend/infrastructure/migration/executor/src/main/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/MigrationRunner.kt
```

Open the moved `MigrationRunner.kt` and change its package from `net.brightroom.mindstock.infrastructure.persistence` to `net.brightroom.mindstock.infrastructure.migration.executor`.

- [ ] **Step 8.3: Move Flyway SQL resources**

```bash
mkdir -p backend/infrastructure/migration/executor/src/main/resources/db/migration
git mv infrastructure/src/main/resources/db/migration/*.sql \
       backend/infrastructure/migration/executor/src/main/resources/db/migration/
```

- [ ] **Step 8.4: Move `TestContainersPostgres.kt` into executor's testFixtures**

```bash
mkdir -p backend/infrastructure/migration/executor/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/migration/executor
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersPostgres.kt \
       backend/infrastructure/migration/executor/src/testFixtures/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestContainersPostgres.kt
```

Open the moved file and update the package:

```kotlin
package net.brightroom.mindstock.infrastructure.migration.executor
```

- [ ] **Step 8.5: Move executor tests**

```bash
mkdir -p backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/TestContainersSmokeTest.kt \
       backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/TestContainersSmokeTest.kt
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationRunnerTest.kt \
       backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/MigrationRunnerTest.kt
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/AppendOnlyEnforcementTest.kt \
       backend/infrastructure/migration/executor/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/executor/AppendOnlyEnforcementTest.kt
```

For each moved test file:
- Change `package net.brightroom.mindstock.infrastructure` (smoke test) or `package net.brightroom.mindstock.infrastructure.persistence` (the other two) to `package net.brightroom.mindstock.infrastructure.migration.executor`.
- Update any import of `net.brightroom.mindstock.infrastructure.TestContainersPostgres` to `net.brightroom.mindstock.infrastructure.migration.executor.TestContainersPostgres`.
- Update any import of `net.brightroom.mindstock.infrastructure.persistence.MigrationRunner` to `net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner`.

- [ ] **Step 8.6: Move generator tests**

```bash
mkdir -p backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/MigrationGeneratorTest.kt \
       backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/MigrationGeneratorTest.kt
git mv infrastructure/src/test/kotlin/net/brightroom/mindstock/infrastructure/persistence/GenerateInitialMigrationManually.kt \
       backend/infrastructure/migration/generator/src/test/kotlin/net/brightroom/mindstock/infrastructure/migration/generator/GenerateInitialMigrationManually.kt
```

For each:
- Change `package net.brightroom.mindstock.infrastructure.persistence` to `package net.brightroom.mindstock.infrastructure.migration.generator`.
- Update imports of `MigrationGenerator`, `MigratableTables`, and `TestContainersPostgres` to their new packages (`migration.generator`, `migration.detector`, `migration.executor` respectively).

- [ ] **Step 8.7: Create `backend/infrastructure/migration/executor/build.gradle.kts`**

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    api(libs.hikari)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.logging.jvm)

    testFixturesImplementation(libs.testcontainers.postgres)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
```

- [ ] **Step 8.8: Update `backend/infrastructure/migration/generator/build.gradle.kts` to add tests**

Open it and replace its contents with:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    implementation(libs.postgres.jdbc)
    implementation(libs.hikari)

    testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
```

- [ ] **Step 8.9: Register executor in `settings.gradle.kts`**

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":backend:infrastructure:migration:detector",
    ":backend:infrastructure:migration:generator",
    ":backend:infrastructure:migration:executor",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 8.10: Rewire `:backend:api` away from `:infrastructure`**

Open `backend/api/build.gradle.kts`. Replace:

```kotlin
    implementation(projects.infrastructure)
```

with:

```kotlin
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.executor)
```

- [ ] **Step 8.11: Update `Main.kt` import for `MigrationRunner`**

Open `backend/api/src/main/kotlin/net/brightroom/mindstock/backend/Main.kt`. Change:

```kotlin
import net.brightroom.mindstock.infrastructure.persistence.MigrationRunner
```

to:

```kotlin
import net.brightroom.mindstock.infrastructure.migration.executor.MigrationRunner
```

- [ ] **Step 8.12: Build and verify everything still passes**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. All testcontainers tests run against the new modules.

Spot-check the manual generator test is still wired:

```bash
./gradlew :backend:infrastructure:migration:generator:test --tests '*MigrationGeneratorTest*'
```

Expected: PASS.

- [ ] **Step 8.13: Smoke-test the app still boots**

```bash
make up
./gradlew :backend:api:run
```

Hit `http://localhost:8080/health` → `OK`. Stop with Ctrl-C, `make down`.

- [ ] **Step 8.14: Commit**

```bash
git add -A
git commit -m "refactor: extract :backend:infrastructure:migration:executor and move tests; add testcontainers convention plugin"
```

---

## Task 9: Drop kotlinx-rpc plugin from `:shared`

`:shared` no longer hosts kRPC contracts (Task 1 moved them — well, the placeholder — to `:rpc`). Clean its plugin set so it doesn't pull rpc tooling unnecessarily.

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 9.1: Edit `shared/build.gradle.kts`**

Remove the `id("org.jetbrains.kotlinx.rpc.plugin")` line from the `plugins { ... }` block, and the `implementation(libs.kotlinx.rpc.core)` + `implementation(libs.kotlinx.rpc.serialization.json)` lines from the `commonMain.dependencies` block. After the edit it should read:

```kotlin
plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}
```

- [ ] **Step 9.2: Build and verify**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.3: Commit**

```bash
git add shared/build.gradle.kts
git commit -m "refactor: drop kotlinx-rpc plugin from :shared (now lives in :rpc)"
```

---

## Task 10: Delete the legacy `:infrastructure` module and clean up settings

By now `:infrastructure` should hold only its `Placeholder.kt` and `build.gradle.kts`. Verify, then remove.

**Files:**
- Delete: `infrastructure/` directory
- Modify: `settings.gradle.kts` (remove `:infrastructure`)
- Modify: `README.md` if it references the old layout (check)
- Modify: `compose.yml` / `Makefile` / `application.yaml` if they reference old module paths (they don't currently — confirm)

- [ ] **Step 10.1: Verify `:infrastructure` is empty apart from the placeholder**

```bash
find infrastructure/src -type f
```

Expected output: only `infrastructure/src/main/kotlin/net/brightroom/mindstock/infrastructure/Placeholder.kt`. If anything else appears, route it to the appropriate new module before deleting.

- [ ] **Step 10.2: Remove the directory**

```bash
git rm -r infrastructure
```

- [ ] **Step 10.3: Remove `:infrastructure` from `settings.gradle.kts`**

The final `include(...)` block should be:

```kotlin
include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":backend:infrastructure:migration:detector",
    ":backend:infrastructure:migration:generator",
    ":backend:infrastructure:migration:executor",
    ":backend:api",
    ":frontend",
)
```

- [ ] **Step 10.4: Sanity check no stale references remain**

```bash
grep -rn 'projects.infrastructure\b\|projects\.application\b\|projects\.backend\b' --include='*.kts'
grep -rn 'net\.brightroom\.mindstock\.infrastructure\.persistence\b' --include='*.kt' --include='*.kts'
grep -rn 'net\.brightroom\.mindstock\.infrastructure\.schema\b' --include='*.kt' --include='*.kts'
```

`projects.backend` lines should only appear as `projects.backend.api`, `projects.backend.application`, `projects.backend.infrastructure.*`. `infrastructure.persistence` and bare `infrastructure.schema` (without trailing `s`) must produce no hits. Fix anything that does.

- [ ] **Step 10.5: Update documentation**

Open `docs/superpowers/specs/2026-05-23-mindstock-design.md`. Search for any mention of the module layout (e.g., a "プロジェクト構成" section listing `:application`, `:infrastructure`, `:backend`). Update those references to reflect the new layout.

If `README.md` mentions module paths, update those too.

- [ ] **Step 10.6: Full build, run tests, smoke-test the app**

```bash
./gradlew clean build
make up
./gradlew :backend:api:run
```

Confirm `/health` responds, then Ctrl-C and `make down`.

- [ ] **Step 10.7: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy :infrastructure module and update docs"
```

---

## Final Verification

- [ ] **Final Step 1: Full clean build from scratch**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. All testcontainers tests pass.

- [ ] **Final Step 2: Confirm directory layout**

```bash
ls -1 .
ls -1 backend
ls -1 backend/infrastructure
ls -1 backend/infrastructure/migration
```

Top-level: `domain`, `shared`, `rpc`, `frontend`, `backend`, plus the usual `build-logic`, `docs`, `gradle`, etc. — no `application/`, no `infrastructure/`.
`backend/`: `api`, `application`, `infrastructure`.
`backend/infrastructure/`: `schemas`, `migration`.
`backend/infrastructure/migration/`: `annotation`, `detector`, `generator`, `executor`.

- [ ] **Final Step 3: Final settings.gradle.kts review**

`cat settings.gradle.kts` — confirm exact include list matches Task 10.3 above.

- [ ] **Final Step 4: Push the branch / open the PR**

(Optional — only if the user wants to ship this immediately.)

---

## Rollback / Risk Notes

- Every task ends on a green build and its own commit. If anything goes sideways mid-task, `git reset --hard HEAD` returns to the last good state.
- Testcontainers tests fall over fast when Docker isn't running locally — `make up` (or Docker Desktop running) is a prerequisite for steps 3.7, 8.12, 8.13, 10.6. Skip and revisit when Docker is back.
- If `git mv` complains about an in-progress merge or untracked file, fall back to `mkdir -p <dest> && mv <src> <dest> && git add <dest> && git rm <src>` to keep history.
- The `java-test-fixtures` plugin is applied implicitly to `:backend:infrastructure:migration:executor` in Task 8.7. If a future Gradle upgrade deprecates it, the helper `TestContainersPostgres` can be promoted to a small `:backend:infrastructure:testsupport` module without changing test semantics.
- `Database.kt` lives in `:backend:api` per the plan, not in a separate `persistence` module. If Plan 3 introduces repositories that need shared access to the Exposed `Database`, that will be a follow-up refactor — not part of this plan.
