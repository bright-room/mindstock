# Backend / shared module 構成見直し Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** backend を `:backend:{core,api,schedules}` に再編し、自作 migration を Exposed 1.3.0 Gradle plugin に置き換え、testcontainers JVM lib を捨てた外部 Postgres ベースの統合テスト構成に切り替える。

**Architecture:** `.tmp/library` (system-sekkei) 流の DDD パッケージ構成。`:backend:core` を library module、`:backend:api` を Ktor 起動 entrypoint、`:backend:schedules` を batch 用 placeholder。Service / Repository / DataSource は `<Ctx>` / `<Ctx>Register` の 1:1:1 対応で対称命名。

**Tech Stack:** Kotlin 2.3.21, Ktor 3.5.0, Exposed 1.3.0, kotlinx-rpc 0.10.2, Koin 4.2.1, Flyway 12.6.1, JUnit 5 / Kotest 6.1.11, Compose Multiplatform 1.11.0.

**Spec:** `docs/superpowers/specs/2026-05-29-backend-module-restructure-design.md`

---

## Phase 1: Exposed bump + migration:* 置き換え

構造は現状維持のまま、自作 migration サブシステムを Exposed 公式 plugin + Flyway に置き換える。Phase 1 完了時点で `./gradlew build` と `./gradlew :backend:application:api:run` が通る。

### Task 1: Exposed 1.0.0-beta-4 → 1.3.0 bump

**Files:**
- Modify: `gradle/libs.versions.toml:11`

- [ ] **Step 1: bump version**

Edit `gradle/libs.versions.toml`:

```diff
-exposed = "1.0.0-beta-4"
+exposed = "1.3.0"
```

- [ ] **Step 2: Verify dependency resolution**

Run: `./gradlew :backend:infrastructure:schemas:dependencies --configuration runtimeClasspath | grep exposed`

Expected: `org.jetbrains.exposed:exposed-core:1.3.0` (and others, all version `1.3.0`).

- [ ] **Step 3: Compile check**

Run: `./gradlew :backend:infrastructure:schemas:compileKotlin :backend:infrastructure:migration:executor:compileKotlin`

Expected: BUILD SUCCESSFUL. If API changes between beta-4 and 1.3.0 break compilation, fix them inline before proceeding.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build(deps): bump Exposed to 1.3.0"
```

---

### Task 2: Exposed Gradle plugin を :backend:infrastructure:schemas に適用

**Files:**
- Modify: `gradle/libs.versions.toml` ([plugins] section)
- Modify: `backend/infrastructure/schemas/build.gradle.kts`
- Modify: `settings.gradle.kts` (pluginManagement)

- [ ] **Step 1: Plugin coordinate を libs.versions.toml に追加**

Edit `gradle/libs.versions.toml` `[plugins]` セクションに以下を追記:

```toml
exposed-migration = { id = "org.jetbrains.exposed.v1.gradle.plugin", version.ref = "exposed" }
```

- [ ] **Step 2: schemas build.gradle.kts に plugin を適用**

Edit `backend/infrastructure/schemas/build.gradle.kts` に置き換え:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    alias(libs.plugins.exposed-migration)
}

dependencies {
    implementation(projects.domain)
    implementation(projects.backend.infrastructure.migration.annotation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.jdbc)
}

exposed {
    generateMigrations {
        tablesPackage.set("net.brightroom.mindstock.infrastructure.schemas")
        fileDirectory.set(layout.projectDirectory.dir("../migration/executor/src/main/resources/db/migration").asFile.absolutePath)
    }
}
```

- [ ] **Step 3: Plugin が解決できるか確認**

Run: `./gradlew :backend:infrastructure:schemas:tasks --group="exposed"`

Expected: `generateMigrations` タスクが一覧に含まれる。

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml backend/infrastructure/schemas/build.gradle.kts
git commit -m "build: apply Exposed Gradle plugin to schemas subproject"
```

---

### Task 3: 既存 init.sql 削除 + Exposed plugin で再生成 + role ファイル rename

**Files:**
- Rename: `backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523000001__append_only_role.sql` → `V00000000000000__append_only_role.sql`
- Delete: `backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql`
- Generate: `backend/infrastructure/migration/executor/src/main/resources/db/migration/V<timestamp>__init.sql`

- [ ] **Step 1: role ファイルを baseline 名にリネーム**

```bash
git mv \
  backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523000001__append_only_role.sql \
  backend/infrastructure/migration/executor/src/main/resources/db/migration/V00000000000000__append_only_role.sql
```

- [ ] **Step 2: 既存 init.sql 削除**

```bash
rm backend/infrastructure/migration/executor/src/main/resources/db/migration/V20260523071825__init.sql
```

- [ ] **Step 3: Docker daemon が起動しているか確認**

Run: `docker info > /dev/null 2>&1 && echo OK || echo "Docker not running"`

Expected: `OK`。生成は build-time に testcontainers で Postgres を立てるので Docker が必要。

- [ ] **Step 4: generateMigrations 実行**

Run: `./gradlew :backend:infrastructure:schemas:generateMigrations`

Expected: BUILD SUCCESSFUL。新しい `V<timestamp>__init.sql` が `backend/infrastructure/migration/executor/src/main/resources/db/migration/` に生成される。

- [ ] **Step 5: 生成 SQL の内容を確認**

Run: `ls backend/infrastructure/migration/executor/src/main/resources/db/migration/ && head -50 backend/infrastructure/migration/executor/src/main/resources/db/migration/V*__init.sql`

Expected: 2 ファイル(role + init)。init.sql に `CREATE TABLE` 文が並ぶ。

- [ ] **Step 6: Compose 起動 → migration 適用テスト**

```bash
docker compose up -d postgres
./gradlew :backend:application:api:run &
sleep 10
docker compose exec postgres psql -U mindstock -d mindstock -c "\dt"
```

Expected: `users`, `households`, `catalog_items`, `products`, `stocks`, `stock_movements` 等のテーブルが一覧表示される。

Stop the app: `pkill -f "EngineMain"` or `Ctrl-C`.

- [ ] **Step 7: Commit**

```bash
git add backend/infrastructure/migration/executor/src/main/resources/db/migration/
git commit -m "feat(migration): regenerate init.sql via Exposed plugin, rename role to baseline"
```

---

### Task 4: MigrationConfiguration を Flyway core 直叩きに置き換え

**Files:**
- Modify: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/migration/MigrationConfiguration.kt`
- Modify: `backend/application/api/build.gradle.kts`

- [ ] **Step 1: build.gradle.kts の executor 依存を flyway-core / flyway-database-postgresql に置換**

Edit `backend/application/api/build.gradle.kts`、`dependencies` ブロック内の以下を削除:

```kotlin
implementation(projects.backend.infrastructure.migration.executor)
```

代わりに追加:

```kotlin
implementation(libs.flyway.core)
implementation(libs.flyway.database.postgresql)
```

`testFixturesImplementation` セクションからも以下 2 行を削除:

```kotlin
testFixturesImplementation(projects.backend.infrastructure.migration.executor)
testFixturesImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
```

`testImplementation` から以下も削除:

```kotlin
testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
```

- [ ] **Step 2: MigrationConfiguration.kt を Flyway 直叩きに書き換え**

Replace the entire content of `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/migration/MigrationConfiguration.kt`:

```kotlin
package net.brightroom.mindstock.configuration.migration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import net.brightroom.mindstock.configuration.external.exposed.ExposedDataSourceProperties
import org.flywaydb.core.Flyway

fun Application.migrationConfigure(
    @Property("external.datasource.database") properties: ExposedDataSourceProperties,
) {
    val hikariConfig =
        HikariConfig().apply {
            driverClassName = properties.driverClassName
            jdbcUrl = properties.jdbcUrl
            username = properties.username
            password = properties.password
            maximumPoolSize = 2
            isAutoCommit = false
        }

    HikariDataSource(hikariConfig).use { dataSource ->
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
```

- [ ] **Step 3: migration ファイルが classpath に乗るよう resources を application/api に移す**

```bash
mkdir -p backend/application/api/src/main/resources/db/migration
git mv backend/infrastructure/migration/executor/src/main/resources/db/migration/*.sql \
       backend/application/api/src/main/resources/db/migration/
```

- [ ] **Step 4: schemas plugin の出力先を新しい場所に合わせる**

Edit `backend/infrastructure/schemas/build.gradle.kts` の `fileDirectory.set(...)` を以下に修正:

```kotlin
fileDirectory.set(rootProject.layout.projectDirectory.dir("backend/application/api/src/main/resources/db/migration").asFile.absolutePath)
```

- [ ] **Step 5: Build verification**

Run: `./gradlew :backend:application:api:build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 起動 + DB 状態確認**

```bash
docker compose down -v
docker compose up -d postgres
./gradlew :backend:application:api:run &
sleep 10
docker compose exec postgres psql -U mindstock -d mindstock -c "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank"
pkill -f EngineMain || true
```

Expected: `flyway_schema_history` に 2 行(baseline + init)、いずれも success。

- [ ] **Step 7: Commit**

```bash
git add backend/application/api/build.gradle.kts \
        backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/migration/MigrationConfiguration.kt \
        backend/application/api/src/main/resources/db/migration/ \
        backend/infrastructure/schemas/build.gradle.kts
git commit -m "refactor(migration): replace executor with Flyway core direct call"
```

---

### Task 5: 自作 migration サブシステム削除

**Files:**
- Delete: `backend/infrastructure/migration/{annotation,detector,generator,executor}/` 全体
- Modify: `settings.gradle.kts`
- Modify: `backend/infrastructure/schemas/build.gradle.kts`

- [ ] **Step 1: schemas の annotation 依存を削除**

Edit `backend/infrastructure/schemas/build.gradle.kts`、`dependencies` ブロックから以下を削除:

```kotlin
implementation(projects.backend.infrastructure.migration.annotation)
```

- [ ] **Step 2: schemas のソースで annotation を import しているなら削除**

Run: `grep -r "migration.annotation" backend/infrastructure/schemas/src/`

If hits found, remove those imports and any annotation usages (`@PrimaryKey` 等)。Exposed 標準のメタ表現で代替する(現状 schemas のテーブル定義はすでに Exposed 標準のみ使っている想定だが要確認)。

- [ ] **Step 3: settings.gradle.kts から migration subprojects を削除**

Edit `settings.gradle.kts`、以下 4 行を削除:

```kotlin
":backend:infrastructure:migration:annotation",
":backend:infrastructure:migration:detector",
":backend:infrastructure:migration:generator",
":backend:infrastructure:migration:executor",
```

- [ ] **Step 4: ディレクトリ削除**

```bash
git rm -r backend/infrastructure/migration/
```

- [ ] **Step 5: Build verification**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 起動確認**

```bash
docker compose down -v && docker compose up -d postgres
./gradlew :backend:application:api:run &
sleep 10
curl -sf http://localhost:8080/health || echo "health check fail"
pkill -f EngineMain || true
```

Expected: 起動成功、`/health` が 200。

- [ ] **Step 7: Commit (Phase 1 完了)**

```bash
git add -A
git commit -m "refactor(backend): replace home-grown migration with Exposed Gradle plugin

旧 :backend:infrastructure:migration:{annotation,detector,generator,executor}
の 4 module を削除し、Exposed 1.3.0 公式 Gradle plugin の generateMigrations
task と Flyway core 直叩きで置き換える。SQL ファイルは
backend/application/api/src/main/resources/db/migration に集約。"
```

---

## Phase 2: module 再編 + package 移動 + test infra 入れ替え

`:backend:application:api` を `:backend:{core,api,schedules}` に分割し、`:shared:{rpc,extensions}` を `:shared` + `:rpc` に整理し、命名規則を library 流に揃え、test infra を外部 Postgres ベースに切り替える。

### Task 6: domain.repository への参照点を grep + ベースライン記録

**Files:**
- Create: `.tmp/phase2-baseline.txt`(コミットしない)

- [ ] **Step 1: 全参照点を列挙**

Run: `rg -n "net\.brightroom\.mindstock\.domain\.repository" --type kt > .tmp/phase2-baseline.txt`

- [ ] **Step 2: モジュール別カウント**

Run: `awk -F/ '{print $1"/"$2}' .tmp/phase2-baseline.txt | sort -u`

Expected: `backend/application` 配下と `domain/src` 配下に出現。`:rpc` / `:frontend` には出ない想定(出たら spec で想定外、要相談)。

- [ ] **Step 3: テスト前のビルド状態を記録**

```bash
./gradlew check 2>&1 | tee .tmp/phase2-baseline-check.txt
```

Phase 2 完了時に同コマンドで結果比較できるよう保存。

---

### Task 7: :backend:core サブプロジェクトを骨組みだけ作成

**Files:**
- Create: `backend/core/build.gradle.kts`
- Create: `backend/core/src/main/kotlin/.gitkeep`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: settings.gradle.kts に :backend:core を追加**

Edit `settings.gradle.kts`、`include(":backend:application:api", ...)` を以下に置き換え:

```kotlin
include(
    ":backend:core",
    ":backend:application:api",
    ":backend:infrastructure:schemas",
)
```

(api / schemas は Task 13 まで残す)

- [ ] **Step 2: backend/core/build.gradle.kts を作成**

Create `backend/core/build.gradle.kts`:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    alias(libs.plugins.exposed.migration)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.domain)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hikari)
    implementation(libs.kotlin.logging.jvm)

    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.postgres.jdbc)
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.database.postgresql)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}

exposed {
    generateMigrations {
        tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource")
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration").asFile.absolutePath)
    }
}
```

- [ ] **Step 3: ディレクトリ作成**

```bash
mkdir -p backend/core/src/main/kotlin backend/core/src/main/resources
touch backend/core/src/main/kotlin/.gitkeep
```

- [ ] **Step 4: 設定確認**

Run: `./gradlew :backend:core:tasks --group="build"`

Expected: BUILD SUCCESSFUL、`build` / `assemble` / `compileKotlin` 等がリスト。

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts backend/core/
git commit -m "build: add :backend:core subproject skeleton"
```

---

### Task 8: :backend:api サブプロジェクトを骨組みだけ作成

**Files:**
- Create: `backend/api/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: settings.gradle.kts に :backend:api を追加**

Edit `settings.gradle.kts` の include に追加:

```kotlin
include(
    ":backend:core",
    ":backend:api",
    ":backend:application:api",
    ":backend:infrastructure:schemas",
)
```

- [ ] **Step 2: backend/api/build.gradle.kts を作成**

Create `backend/api/build.gradle.kts`:

```kotlin
plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}

dependencies {
    implementation(projects.backend.core)
    implementation(projects.domain)
    implementation(projects.rpc)
    implementation(projects.shared)

    implementation(ktorLib.server.core)
    implementation(ktorLib.server.cio)
    implementation(ktorLib.server.di)
    implementation(ktorLib.server.config.yaml)
    implementation(ktorLib.server.websockets)
    implementation(ktorLib.server.auth)
    implementation(ktorLib.server.auth.jwt)
    implementation(ktorLib.server.contentNegotiation)
    implementation(ktorLib.serialization.kotlinx.json)
    implementation(ktorLib.server.doubleReceive)
    implementation(ktorLib.server.statusPages)
    implementation(ktorLib.server.callId)
    implementation(ktorLib.server.callLogging)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.kotlinx.rpc.serialization.json)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    testImplementation(testFixtures(projects.backend.core))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(ktorLib.server.testHost)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.rpc.client)
    testImplementation(libs.kotlinx.rpc.client.ktor)
    testImplementation(ktorLib.client.cio)
    testImplementation(ktorLib.client.websockets)
    testImplementation(ktorLib.client.contentNegotiation)
}
```

(`projects.rpc` / `projects.shared` は Task 18-20 で実体作成。settings に未 include 状態だと dependency 解決でエラーになるので、本 Task では `projects.shared.rpc` / `projects.shared.extensions` のままで作っておき、Task 20 で書き換える。下記に差し戻し版を採用)

修正版 — projects.rpc / projects.shared の代わりに旧名で書く:

```kotlin
    implementation(projects.shared.rpc)
    implementation(projects.shared.extensions)
```

(後で Task 20 / 21 で書き換える)

- [ ] **Step 3: ディレクトリ作成**

```bash
mkdir -p backend/api/src/main/kotlin backend/api/src/main/resources backend/api/src/test/kotlin
```

- [ ] **Step 4: 設定確認**

Run: `./gradlew :backend:api:tasks --group="application"`

Expected: `run` task が表示される。

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts backend/api/
git commit -m "build: add :backend:api subproject skeleton"
```

---

### Task 9: :backend:schedules サブプロジェクトをスケルトン作成

**Files:**
- Create: `backend/schedules/build.gradle.kts`
- Create: `backend/schedules/src/main/kotlin/net/brightroom/mindstock/Main.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: settings.gradle.kts に :backend:schedules を追加**

Edit `settings.gradle.kts` の include に `":backend:schedules",` を追加。

- [ ] **Step 2: backend/schedules/build.gradle.kts を作成**

Create `backend/schedules/build.gradle.kts`:

```kotlin
plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    application
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}

dependencies {
    implementation(projects.backend.core)
    implementation(projects.domain)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
}
```

- [ ] **Step 3: 最小 Main.kt を作成**

Create `backend/schedules/src/main/kotlin/net/brightroom/mindstock/Main.kt`:

```kotlin
package net.brightroom.mindstock

fun main() {
    println("schedules: placeholder entrypoint. No batch implemented yet.")
}
```

- [ ] **Step 4: Build + run 確認**

```bash
./gradlew :backend:schedules:build
./gradlew :backend:schedules:run
```

Expected: 2 行目で `schedules: placeholder entrypoint. ...` が出て即終了。

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts backend/schedules/
git commit -m "build: add :backend:schedules placeholder subproject"
```

---

### Task 10: Repository interface を :domain → :backend:core に移動

**Files:**
- Move: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/**/*.kt`
  - → `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/<ctx>/*.kt`
- Modify: 全 import 文(旧 `domain.repository.*` → 新 `application.repository.*`)

- [ ] **Step 1: Repository interface ファイルを移動**

```bash
mkdir -p backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository
for ctx in catalog household product stock user; do
  mkdir -p "backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/$ctx"
  git mv "domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/$ctx"/*.kt \
         "backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/$ctx/"
done
rmdir domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/{catalog,household,product,stock,user}
rmdir domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository
```

- [ ] **Step 2: 各 Repository interface ファイルの package 宣言を書き換え**

For each `<ctx>` in `catalog household product stock user`:

```bash
for ctx in catalog household product stock user; do
  for f in backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/$ctx/*.kt; do
    sed -i '' "s|^package net\.brightroom\.mindstock\.domain\.repository\.$ctx\$|package net.brightroom.mindstock.application.repository.$ctx|" "$f"
  done
done
```

- [ ] **Step 3: import 参照を一括更新**

```bash
rg -l "net\.brightroom\.mindstock\.domain\.repository" --type kt -0 | xargs -0 sed -i '' \
  's|net\.brightroom\.mindstock\.domain\.repository\.|net.brightroom.mindstock.application.repository.|g'
```

- [ ] **Step 4: domain module を build**

Run: `./gradlew :domain:build`

Expected: BUILD SUCCESSFUL。Repository interface が抜けた純化 domain がコンパイル通る。

- [ ] **Step 5: backend:core を build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: backend:application:api を build(現状の参照が新パッケージを向くようになっているか)**

Run: `./gradlew :backend:application:api:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(domain): move Repository interfaces to :backend:core application.repository

Repository interface を :domain から :backend:core の application.repository.<ctx>
package に移動。:domain は model + 型のみの純化レイヤに。"
```

---

### Task 11: Exposed Table を :backend:infrastructure:schemas → :backend:core/infrastructure/datasource に移動

**Files:**
- Move: `backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/**/*.kt`
  - → `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/<ctx>/*.kt`

- [ ] **Step 1: schemas のファイル一覧を確認**

Run: `find backend/infrastructure/schemas/src/main -name '*.kt' | sort`

Expected: 5 ctx 分の `XxxTable.kt` 等が並ぶ。

- [ ] **Step 2: 各 Table ファイルを ctx ディレクトリに振り分け移動**

```bash
mkdir -p backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource
for ctx in catalog household product stock user; do
  mkdir -p "backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/$ctx"
  if [ -d "backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/$ctx" ]; then
    git mv "backend/infrastructure/schemas/src/main/kotlin/net/brightroom/mindstock/infrastructure/schemas/$ctx"/*.kt \
           "backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/$ctx/"
  fi
done
```

- [ ] **Step 3: package 宣言を書き換え**

```bash
for ctx in catalog household product stock user; do
  for f in backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/$ctx/*.kt; do
    sed -i '' "s|^package net\.brightroom\.mindstock\.infrastructure\.schemas\.$ctx\$|package net.brightroom.mindstock.infrastructure.datasource.$ctx|" "$f"
  done
done
```

- [ ] **Step 4: 参照を一括更新**

```bash
rg -l "net\.brightroom\.mindstock\.infrastructure\.schemas" --type kt -0 | xargs -0 sed -i '' \
  's|net\.brightroom\.mindstock\.infrastructure\.schemas\.|net.brightroom.mindstock.infrastructure.datasource.|g'
```

- [ ] **Step 5: Build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(schemas): move Exposed Table definitions to :backend:core infrastructure.datasource"
```

---

### Task 12: Repository 実装を :backend:application:api → :backend:core に移動、`*Impl` → `*DataSource` rename

**Files:**
- Move: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/<ctx>/*.kt`
  - → `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/<ctx>/*.kt`
- Rename: `XxxRepositoryImpl` → `XxxDataSource`, `XxxRegisterRepositoryImpl` → `XxxRegisterDataSource`

- [ ] **Step 1: ファイルを移動**

```bash
for ctx in catalog household product stock user; do
  if [ -d "backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/$ctx" ]; then
    git mv backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/$ctx/*.kt \
           backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/$ctx/
  fi
done
```

- [ ] **Step 2: package 宣言を書き換え**

```bash
for ctx in catalog household product stock user; do
  for f in backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/$ctx/*RepositoryImpl.kt; do
    [ -f "$f" ] || continue
    sed -i '' "s|^package net\.brightroom\.mindstock\.infrastructure\.datasource\.repository\.$ctx\$|package net.brightroom.mindstock.infrastructure.datasource.$ctx|" "$f"
  done
done
```

- [ ] **Step 3: ファイル名 rename**

```bash
for f in backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/*/*RepositoryImpl.kt; do
  [ -f "$f" ] || continue
  new="$(dirname "$f")/$(basename "$f" RepositoryImpl.kt)DataSource.kt"
  git mv "$f" "$new"
done
```

これで例えば `CatalogItemRegisterRepositoryImpl.kt` → `CatalogItemRegisterDataSource.kt`、`CatalogItemRepositoryImpl.kt` → `CatalogItemDataSource.kt`。

- [ ] **Step 4: クラス名 rename(ファイル内)**

```bash
rg -l "RepositoryImpl" --type kt -0 | xargs -0 sed -i '' \
  's|RepositoryImpl|DataSource|g'
```

- [ ] **Step 5: 旧パッケージディレクトリ削除**

```bash
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository/{catalog,household,product,stock,user} 2>/dev/null
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/repository 2>/dev/null
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource 2>/dev/null
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/infrastructure 2>/dev/null
```

- [ ] **Step 6: Build verification**

Run: `./gradlew :backend:core:compileKotlin :backend:application:api:compileKotlin`

Expected: BUILD SUCCESSFUL。DI が古い `*RepositoryImpl` 名を参照しているため後続 Task で修正するが、core 自体はビルドが通る。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(datasource): rename *RepositoryImpl to *DataSource and move to :backend:core"
```

---

### Task 13: Handlers を `<Ctx>Service` / `<Ctx>RegisterService` に集約 — catalog

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogItemService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogItemRegisterService.kt`
- Delete: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/*.kt`

- [ ] **Step 1: CatalogItemService.kt を作成(参照系集約)**

Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogItemService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

class CatalogItemService(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun findById(id: CatalogItemId): CatalogItem? = catalogItemRepository.findById(id)

    fun search(query: String, limit: Int): CatalogItems = catalogItemRepository.search(query, limit)
}
```

(引数名/型は現 `FindCatalogItemByIdHandler.kt` / `SearchCatalogItemsHandler.kt` を参照して合わせる)

- [ ] **Step 2: CatalogItemRegisterService.kt を作成(更新系集約)**

Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogItemRegisterService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User

class CatalogItemRegisterService(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)

    fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        catalogItemRegisterRepository.revise(catalogItem, newName, newUnit, editedBy)
    }
}
```

引数名/型は現 `RegisterCatalogItemHandler.kt` / `ReviseCatalogItemHandler.kt` から正確に取る。差異があれば既存に合わせる。

- [ ] **Step 3: 既存 catalog Handler 4 ファイルを削除**

```bash
rm backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/*.kt
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog
```

- [ ] **Step 4: build (Handler 削除直後は DI が壊れるので core だけ確認)**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(catalog): consolidate Handlers into CatalogItemService / CatalogItemRegisterService"
```

---

### Task 14: Handlers 集約 — household

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdService.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterService.kt`
- Delete: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/*.kt`

- [ ] **Step 1: HouseholdService.kt(参照系: FindHouseholdOfUserHandler)**

Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun findOf(user: User): Household? = householdRepository.findOf(user)
}
```

- [ ] **Step 2: HouseholdRegisterService.kt(更新系: Create / Invite / Revoke)**

Read `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/InviteMemberHandler.kt` and `RevokeMembershipHandler.kt` to confirm signatures. Then create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdRegisterService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdRole
import net.brightroom.mindstock.domain.model.user.User

class HouseholdRegisterService(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun create(owner: User): Household = householdRegisterRepository.create(owner)

    fun invite(household: Household, user: User, role: HouseholdRole) {
        householdRegisterRepository.invite(household, user, role)
    }

    fun revoke(household: Household, user: User) {
        householdRegisterRepository.revoke(household, user)
    }
}
```

(`HouseholdRole` 型名は実際の Handler 引数から確認。違ったら正しい型に修正)

- [ ] **Step 3: 既存 Handler 削除**

```bash
rm backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/*.kt
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household
```

- [ ] **Step 4: Build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(household): consolidate Handlers into HouseholdService / HouseholdRegisterService"
```

---

### Task 15: Handlers 集約 — product

- [ ] **Step 1: ProductService.kt(参照系: FindProduct / ListProductsOfHousehold)**

Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.Products

class ProductService(
    private val productRepository: ProductRepository,
) {
    fun find(household: Household, catalogItem: CatalogItem): Product? = productRepository.find(household, catalogItem)

    fun listOf(household: Household): Products = productRepository.listOf(household)
}
```

- [ ] **Step 2: ProductRegisterService.kt(更新系: Adopt / Archive / SetMinimumStock)**

Read `AdoptProductHandler.kt`, `ArchiveProductHandler.kt`, `SetMinimumStockHandler.kt` for exact signatures. Create:

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User

class ProductRegisterService(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun adopt(household: Household, catalogItem: CatalogItem): Product =
        productRegisterRepository.adopt(household, catalogItem)

    fun archive(product: Product, by: User) {
        productRegisterRepository.archive(product, by)
    }

    fun setMinimumStock(product: Product, value: MinimumStock, editedBy: User) {
        productRegisterRepository.setMinimumStock(product, value, editedBy)
    }
}
```

`MinimumStock` の型名は実際の handler から確認。

- [ ] **Step 3: 既存 Handler 削除**

```bash
rm backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/*.kt
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product
```

- [ ] **Step 4: Build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(product): consolidate Handlers into ProductService / ProductRegisterService"
```

---

### Task 16: Handlers 集約 — stock

- [ ] **Step 1: StockService.kt(参照系: GetStock / ListStocks / GetMovementHistory)**

Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

class StockService(
    private val stockRepository: StockRepository,
) {
    fun get(product: Product): Stock = stockRepository.stockOf(product)

    fun list(household: Household): List<Stock> = stockRepository.stocksOf(household)

    fun getMovementHistory(product: Product, limit: Int): StockMovements =
        stockRepository.movementHistory(product, limit)
}
```

`limit` の型(Int / 別 VO)は元 Handler を確認。

- [ ] **Step 2: StockRegisterService.kt(更新系: Replenish / Consume)**

Read `ReplenishStockHandler.kt` / `ConsumeStockHandler.kt` for exact signatures. Create:

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Consumption
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Replenishment
import net.brightroom.mindstock.domain.model.user.User

class StockRegisterService(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment = stockRegisterRepository.replenish(product, quantity, occurredAt, by, note)

    fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption = stockRegisterRepository.consume(product, quantity, occurredAt, by, note)
}
```

- [ ] **Step 3: 既存 Handler 削除**

```bash
rm backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/*.kt
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock
```

- [ ] **Step 4: Build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(stock): consolidate Handlers into StockService / StockRegisterService"
```

---

### Task 17: Handlers 集約 — user(RegisterService のみ)

- [ ] **Step 1: UserRegisterService.kt(更新系: Register / Rename)**

Read `RegisterUserHandler.kt` / `RenameUserHandler.kt` for signatures. Create `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/user/UserRegisterService.kt`:

```kotlin
package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject

class UserRegisterService(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun register(identity: AuthSubject, defaultDisplayName: DisplayName): User =
        userRegisterRepository.register(identity, defaultDisplayName)

    fun rename(user: User, newName: DisplayName) {
        userRegisterRepository.rename(user, newName)
    }
}
```

`AuthSubject` / `DisplayName` 型名は実 Handler から確認。

- [ ] **Step 2: 既存 Handler 削除**

```bash
rm backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/*.kt
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase 2>/dev/null
rmdir backend/application/api/src/main/kotlin/net/brightroom/mindstock/application 2>/dev/null
```

- [ ] **Step 3: Build**

Run: `./gradlew :backend:core:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(user): consolidate Handlers into UserRegisterService"
```

---

### Task 18: RPC server 実装を :backend:application:api → :backend:api、`*RpcServer*` → `*Controller` rename

**Files:**
- Move: `backend/application/api/src/main/kotlin/.../rpc/<ctx>/*.kt`
  - → `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/<ctx>/<Ctx>Controller.kt`

- [ ] **Step 1: 現状の RPC 実装ファイル一覧確認**

Run: `find backend/application/api/src/main/kotlin -path '*/rpc/*' -name '*.kt'`

Expected: 5〜6 ファイル(catalog, household, product, stock, user, userPublic に対応)。

- [ ] **Step 2: ファイル名と package を新しいものに移動 + rename**

For each existing file, do this (mapping table per spec §4):

| 旧 | 新 |
|---|---|
| `CatalogRpcServer.kt`(or similar) | `presentation/rpc/catalog/CatalogController.kt` |
| `HouseholdRpcServer.kt` | `presentation/rpc/household/HouseholdController.kt` |
| `ProductRpcServer.kt` | `presentation/rpc/product/ProductController.kt` |
| `StockRpcServer.kt` | `presentation/rpc/stock/StockController.kt` |
| `UserRpcServer.kt` | `presentation/rpc/user/UserController.kt` |
| `UserPublicRpcServer.kt` | `presentation/rpc/user/UserPublicController.kt` |

実際の旧ファイル名は git ls で確認:

```bash
ls backend/application/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/ 2>/dev/null || \
  find backend/application/api/src/main/kotlin -path '*/rpc/*' -name '*.kt'
```

そのうえで:

```bash
mkdir -p backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc
for ctx in catalog household product stock user; do
  mkdir -p "backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/$ctx"
done
```

- [ ] **Step 3: 各ファイル移動 + クラス名 rename**

実ファイル名を確認した上で、1 つずつ `git mv` してから内容を編集する。例(`CatalogRpcServer.kt` の場合):

```bash
git mv backend/application/api/src/main/kotlin/.../rpc/CatalogRpcServer.kt \
       backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt
```

ファイル内の編集:
- `package` を `net.brightroom.mindstock.presentation.rpc.catalog` に変更
- クラス名 `CatalogRpcServer` → `CatalogController`
- コンストラクタ引数の Handler 型を新 Service 型に差し替え(例: `RegisterCatalogItemHandler` → `CatalogItemRegisterService`)
- メソッド本体の Handler 呼び出しを Service 呼び出しに置換(例: `registerCatalogItemHandler.handle(...)` → `catalogItemRegisterService.register(...)`)

各 ctx で同様。

- [ ] **Step 4: import / 参照を update**

```bash
rg -l "application\.usecase\." --type kt -0 backend/api 2>/dev/null | xargs -0 -I{} echo "Manually update: {}"
```

ヒットしたファイルを 1 つずつ手で更新(Handler → Service への import / 呼び出し変更)。

- [ ] **Step 5: Build (api のみ)**

Run: `./gradlew :backend:api:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(rpc): move RPC server impls to :backend:api as <Ctx>Controller"
```

---

### Task 19: Main + configuration を :backend:application:api → :backend:api に移動

**Files:**
- Move: 全 `backend/application/api/src/main/kotlin/net/brightroom/mindstock/{Main.kt,configuration/**}` → `backend/api/src/main/kotlin/net/brightroom/mindstock/`
- Move: `backend/application/api/src/main/resources/**` → `backend/api/src/main/resources/`

- [ ] **Step 1: Main.kt 移動**

```bash
git mv backend/application/api/src/main/kotlin/net/brightroom/mindstock/Main.kt \
       backend/api/src/main/kotlin/net/brightroom/mindstock/Main.kt
```

- [ ] **Step 2: configuration/ ツリーごと移動**

```bash
git mv backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration \
       backend/api/src/main/kotlin/net/brightroom/mindstock/configuration
```

- [ ] **Step 3: resources 移動(application.yaml, logback.xml, db/migration 等)**

```bash
for f in backend/application/api/src/main/resources/*; do
  git mv "$f" backend/api/src/main/resources/
done
```

- [ ] **Step 4: DI を Service ベースに書き換え**

Edit `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt` を以下に全面置換:

```kotlin
package net.brightroom.mindstock.configuration.di

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.configuration.Environment
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemDataSource
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockDataSource
import net.brightroom.mindstock.infrastructure.datasource.stock.StockRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UserDataSource
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

fun Application.dependenciesConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    dependencies {
        // Repository implementations (DataSource)
        provide<UserRepository> { UserDataSource() }
        provide<UserRegisterRepository> { UserRegisterDataSource() }

        provide<HouseholdRepository> { HouseholdDataSource() }
        provide<HouseholdRegisterRepository> { HouseholdRegisterDataSource() }

        provide<CatalogItemRepository> { CatalogItemDataSource() }
        provide<CatalogItemRegisterRepository> { CatalogItemRegisterDataSource() }

        provide<ProductRepository> { ProductDataSource() }
        provide<ProductRegisterRepository> { ProductRegisterDataSource() }

        provide<StockRepository> { StockDataSource(resolve()) }
        provide<StockRegisterRepository> { StockRegisterDataSource() }

        // Application Services
        provide<CatalogItemService> { CatalogItemService(resolve()) }
        provide<CatalogItemRegisterService> { CatalogItemRegisterService(resolve()) }

        provide<HouseholdService> { HouseholdService(resolve()) }
        provide<HouseholdRegisterService> { HouseholdRegisterService(resolve()) }

        provide<ProductService> { ProductService(resolve()) }
        provide<ProductRegisterService> { ProductRegisterService(resolve()) }

        provide<StockService> { StockService(resolve()) }
        provide<StockRegisterService> { StockRegisterService(resolve()) }

        provide<UserRegisterService> { UserRegisterService(resolve()) }
    }
}
```

注: `StockDataSource` の `resolve()` 引数は現 `StockRepositoryImpl(resolve())` に対応(他の repo に依存している)。元の依存関係を維持する。

- [ ] **Step 5: 旧 backend/application/api 配下の残骸ディレクトリ確認**

Run: `find backend/application -type f`

If `build.gradle.kts` 以外のソースが残っていなければ Task 21 で削除。残ってたら手動で確認 + 移動。

- [ ] **Step 6: Build**

Run: `./gradlew :backend:api:build`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(api): move Main + configuration to :backend:api, wire DI to Services"
```

---

### Task 20: `:shared:extensions` を `:shared` にリネーム

**Files:**
- Move: `shared/extensions/` → `shared/`
- Modify: `settings.gradle.kts`
- Modify: `frontend/build.gradle.kts`(参照を更新)
- Modify: `backend/api/build.gradle.kts`

- [ ] **Step 1: settings.gradle.kts 更新**

Edit `settings.gradle.kts`、

```kotlin
include(
    ":shared:rpc",
    ":shared:extensions",
)
```

を:

```kotlin
include(":shared")
include(":rpc")
```

に置き換え(`:rpc` の実体作成は Task 21)。

- [ ] **Step 2: ディレクトリ移動**

```bash
git mv shared/extensions/build.gradle.kts shared/build.gradle.kts
mkdir -p shared/src
git mv shared/extensions/src/* shared/src/
rmdir shared/extensions/src shared/extensions
```

- [ ] **Step 3: 参照を更新**

```bash
rg -l "projects\.shared\.extensions" --type kts -0 | xargs -0 sed -i '' \
  's|projects\.shared\.extensions|projects.shared|g'
```

- [ ] **Step 4: Build**

Run: `./gradlew :shared:build`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(shared): rename :shared:extensions to :shared"
```

---

### Task 21: `:shared:rpc` を `:rpc` にリネーム + package を `presentation.rpc` → `rpc` に rename

**Files:**
- Move: `shared/rpc/` → `rpc/`
- Modify: 全 RPC service interface の package
- Modify: 参照側(`:frontend`, `:backend:api`)

- [ ] **Step 1: ディレクトリ移動**

```bash
git mv shared/rpc rpc
rmdir shared 2>/dev/null || true
```

(`shared/` が空になっているか確認。空ならば後段でクリーンアップ)

- [ ] **Step 2: package 書き換え**

```bash
rg -l "package net\.brightroom\.mindstock\.presentation\.rpc" --type kt -0 rpc/ | xargs -0 sed -i '' \
  's|^package net\.brightroom\.mindstock\.presentation\.rpc|package net.brightroom.mindstock.rpc|'
```

- [ ] **Step 3: ファイル移動(presentation/rpc → rpc)**

```bash
mkdir -p rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc
git mv rpc/src/commonMain/kotlin/net/brightroom/mindstock/presentation/rpc/*.kt \
       rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/
rmdir rpc/src/commonMain/kotlin/net/brightroom/mindstock/presentation/rpc
rmdir rpc/src/commonMain/kotlin/net/brightroom/mindstock/presentation
```

- [ ] **Step 4: 全プロジェクトの import を一括更新(`presentation.rpc.*RpcService` → `rpc.*RpcService`)**

```bash
rg -l "net\.brightroom\.mindstock\.presentation\.rpc\." --type kt -0 | xargs -0 sed -i '' \
  's|net\.brightroom\.mindstock\.presentation\.rpc\.|net.brightroom.mindstock.rpc.|g'
```

(注: backend/api 側の `presentation.rpc.<ctx>.<Ctx>Controller` パッケージは別物。これは `presentation.rpc.<ctx>` で残す。誤マッチを起こさないよう、上記の sed は **`presentation.rpc.` の直後にクラス名が来るパターン**しか書き換えないが、`<ctx>.` で続くパターンも書き換えてしまう恐れあり。手動で確認:)

```bash
rg "net\.brightroom\.mindstock\.rpc\.(catalog|household|product|stock|user)" --type kt
```

ヒットしたら `presentation.rpc.<ctx>` に戻す。

- [ ] **Step 5: build.gradle.kts 参照を更新**

```bash
rg -l "projects\.shared\.rpc" --type kts -0 | xargs -0 sed -i '' \
  's|projects\.shared\.rpc|projects.rpc|g'
```

- [ ] **Step 6: rpc subproject の build.gradle.kts を見直す**

Edit `rpc/build.gradle.kts` を確認、変更不要(plugin / dependency は変わらない)。

- [ ] **Step 7: Build**

Run: `./gradlew :rpc:build :frontend:compileKotlinWasmJs :backend:api:compileKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(rpc): rename :shared:rpc to :rpc, package -> net.brightroom.mindstock.rpc"
```

---

### Task 22: 旧 `:backend:application:api` / `:backend:infrastructure:schemas` を削除

**Files:**
- Delete: `backend/application/`
- Delete: `backend/infrastructure/`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: settings から削除**

Edit `settings.gradle.kts`、以下 2 行を削除:

```kotlin
":backend:application:api",
":backend:infrastructure:schemas",
```

- [ ] **Step 2: ディレクトリ削除**

```bash
git rm -r backend/application backend/infrastructure
```

- [ ] **Step 3: Full build**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 起動確認**

```bash
docker compose down -v
docker compose up -d postgres
./gradlew :backend:api:run &
sleep 15
curl -sf http://localhost:8080/health || echo "FAIL"
pkill -f EngineMain
docker compose down
```

Expected: `OK` (or 200 status).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(backend): remove obsolete :backend:application:api and :backend:infrastructure:schemas"
```

---

### Task 23: Test infra — testcontainers JVM lib 削除、外部 Postgres へ切り替え

**Files:**
- Modify: `backend/core/build.gradle.kts`(testFixtures から testcontainers 削除)
- Modify: `backend/api/build.gradle.kts`(test から testcontainers 削除)
- Create: `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt`
- Modify: `backend/api/build.gradle.kts`(integrationTest task)
- Modify: `backend/core/build.gradle.kts`(integrationTest task)

- [ ] **Step 1: testcontainers 依存を build.gradle.kts から削除**

Edit `backend/core/build.gradle.kts` の `testFixturesImplementation` から `libs.testcontainers.*` の行を削除(現状は元から無いが念のため確認)。

Edit `backend/api/build.gradle.kts` の `testImplementation` から以下を削除:

```kotlin
testImplementation(libs.testcontainers.junit)
testImplementation(libs.testcontainers.postgres)
```

- [ ] **Step 2: TestDataSource.kt(testFixtures)を作成**

Create `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt`:

```kotlin
package net.brightroom.mindstock.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object TestDataSource {
    /**
     * テスト用 DataSource を返す。
     *
     * 接続先: 環境変数 `TEST_DB_URL` (default: `jdbc:postgresql://localhost:5432/mindstock_test`)
     * 認証: `TEST_DB_USER` / `TEST_DB_PASSWORD` (default: `mindstock` / `mindstock`)
     *
     * 接続失敗時は例外を投げる(skip しない)。
     */
    fun create(): HikariDataSource {
        val url = System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/mindstock_test"
        val user = System.getenv("TEST_DB_USER") ?: "mindstock"
        val pass = System.getenv("TEST_DB_PASSWORD") ?: "mindstock"
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = user
            password = pass
            maximumPoolSize = 4
            isAutoCommit = false
        }
        val ds = HikariDataSource(config)
        // verify connectivity early
        ds.connection.use { it.isValid(2) }
        return ds
    }

    /**
     * テスト DB に migration を適用する。各テストクラスの前で 1 度呼ぶ。
     */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()
            .also { it.clean() }
            .migrate()
    }
}
```

- [ ] **Step 3: integrationTest task を register**

Edit `backend/api/build.gradle.kts`、末尾に追加:

```kotlin
val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tag(\"integration\") tests against TEST_DB_URL."
    useJUnitPlatform { includeTags("integration") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    systemProperty("TEST_DB_URL", System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/mindstock_test")
}

tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

tasks.check {
    dependsOn(integrationTest)
}
```

- [ ] **Step 4: 既存テストに @Tag を付与**

Run: `rg -l "@Test|class .*Test" --type kt backend/api/src/test backend/core/src/test 2>/dev/null`

ヒットしたテストクラスのうち DB / migration / Repository 実装 / Ktor server を起動するものに以下を追加:

```kotlin
import org.junit.jupiter.api.Tag

@Tag("integration")
class XxxIntegrationTest : ... { ... }
```

(unit test は変更不要)

- [ ] **Step 5: Build (unit only)**

Run: `./gradlew :backend:api:test`

Expected: BUILD SUCCESSFUL、testcontainers が起動しないことを確認。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test(backend): replace testcontainers JVM lib with external Postgres + integration tag"
```

---

### Task 24: compose.yml に postgres-test service を追加

**Files:**
- Modify: `compose.yml`

- [ ] **Step 1: compose.yml に service 追加**

Edit `compose.yml`、`services:` ブロックに以下を追加(既存 `postgres` の隣):

```yaml
  postgres-test:
    image: postgres:17-alpine
    environment:
      POSTGRES_USER: mindstock
      POSTGRES_PASSWORD: mindstock
      POSTGRES_DB: mindstock_test
    ports:
      - "5433:5432"
    volumes:
      - postgres-test-data:/var/lib/postgresql/data
```

そして同ファイル末尾の `volumes:` ブロックに追加:

```yaml
  postgres-test-data:
```

- [ ] **Step 2: mise.toml に TEST_DB_URL を追加**

Edit `mise.toml` の `[env]` セクションに:

```toml
TEST_DB_URL = "jdbc:postgresql://localhost:5433/mindstock_test"
```

- [ ] **Step 3: integrationTest 動作確認**

```bash
docker compose up -d postgres-test
./gradlew :backend:api:integrationTest
docker compose down
```

Expected: integrationTest が PASS(統合テストが書かれていれば。無ければ "no tests" でも OK)。

- [ ] **Step 4: Commit**

```bash
git add compose.yml mise.toml
git commit -m "test(compose): add postgres-test service on port 5433 for integration tests"
```

---

### Task 25: GHA workflow に Postgres service container を追加

**Files:**
- Modify: `.github/workflows/<workflow>.yml`(CI 設定)

- [ ] **Step 1: 既存 workflow を確認**

Run: `ls .github/workflows/`

Read the main CI workflow file (e.g. `ci.yml`).

- [ ] **Step 2: jobs.<job>.services に postgres を追加**

For the job that runs `./gradlew check`, add `services` section:

```yaml
    services:
      postgres:
        image: postgres:17-alpine
        env:
          POSTGRES_USER: mindstock
          POSTGRES_PASSWORD: mindstock
          POSTGRES_DB: mindstock_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U mindstock"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    env:
      TEST_DB_URL: jdbc:postgresql://localhost:5432/mindstock_test
      TEST_DB_USER: mindstock
      TEST_DB_PASSWORD: mindstock
```

- [ ] **Step 3: 既存の AUTH_* placeholder と整合確認**

`d185377` で追加された `AUTH_*` env と並存することを確認。

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/
git commit -m "ci: add postgres service container for integration tests"
```

---

### Task 26: `.DS_Store` 全削除 + `.gitignore` 追加

**Files:**
- Delete: 全 `.DS_Store`
- Modify: `.gitignore`

- [ ] **Step 1: 削除**

```bash
find . -name '.DS_Store' -not -path './.git/*' -print -delete
```

- [ ] **Step 2: git からも除去**

```bash
git rm --cached $(git ls-files | grep '\.DS_Store$') 2>/dev/null || true
```

- [ ] **Step 3: `.gitignore` に追加**

Edit `.gitignore`、末尾に追加(既に行があるか確認、なければ):

```
.DS_Store
**/.DS_Store
```

- [ ] **Step 4: Verify**

Run: `git ls-files | grep DS_Store`

Expected: 何も出ない。

- [ ] **Step 5: Commit**

```bash
git add .gitignore
git commit -m "chore: untrack .DS_Store files and add to gitignore"
```

---

### Task 27: Final build verification + spec の影響範囲を更新

**Files:**
- Modify: `docs/superpowers/specs/2026-05-29-backend-module-restructure-design.md`(必要ならば §10 に grep 結果を追記)

- [ ] **Step 1: Full clean build**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 起動確認**

```bash
docker compose down -v
docker compose up -d postgres postgres-test
./gradlew :backend:api:run &
sleep 15
curl -sf http://localhost:8080/health
pkill -f EngineMain
```

Expected: 200 OK.

- [ ] **Step 3: Integration test**

```bash
./gradlew :backend:api:integrationTest
docker compose down
```

Expected: PASS.

- [ ] **Step 4: Module ツリー確認**

Run: `./gradlew projects`

Expected:

```
Root project 'mindstock'
+--- Project ':backend'
|    +--- Project ':backend:api'
|    +--- Project ':backend:core'
|    \--- Project ':backend:schedules'
+--- Project ':domain'
+--- Project ':frontend'
+--- Project ':rpc'
\--- Project ':shared'
```

- [ ] **Step 5: ベースラインとの比較**

Run: `./gradlew check 2>&1 | tee .tmp/phase2-final-check.txt; diff .tmp/phase2-baseline-check.txt .tmp/phase2-final-check.txt || true`

Test 数の増減・成功/失敗の差分を確認、想定外の regression が無いか目視。

- [ ] **Step 6: 一時ファイル削除**

```bash
rm -rf .tmp/phase2-baseline.txt .tmp/phase2-baseline-check.txt .tmp/phase2-final-check.txt
```

- [ ] **Step 7: Final commit**

```bash
git status  # verify clean tree
git log --oneline -25  # review Phase 1 + Phase 2 history
```

If everything is good, no additional commit needed. Open PR.

---

## Self-Review Checklist

実装着手前に作成者が確認すること:

- [ ] Phase 1 / Phase 2 の境界が明確で、Phase 1 完了時点でビルドが通る
- [ ] Service / Repository / DataSource の `<Ctx>` / `<Ctx>Register` 1:1:1 対応が全 ctx で揃う
- [ ] Repository interface 2 系統(`<Ctx>Repository` / `<Ctx>RegisterRepository`)が維持される
- [ ] DI 設定で旧 Handler 型を一切参照していない
- [ ] testcontainers JVM lib への参照が build.gradle.kts / kotlin source の双方から消える
- [ ] `:backend:api` と `:backend:schedules` がそれぞれ独立に build + run できる
- [ ] migration plugin の出力先が `:backend:core` の resources に固定される
- [ ] GHA workflow で integration test が走る

実装中に追加判断が必要な場合は spec(`docs/superpowers/specs/2026-05-29-backend-module-restructure-design.md`)を参照。
