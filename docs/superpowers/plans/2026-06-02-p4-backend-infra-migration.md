# P4: backend infra + migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:backend:core` に Exposed テーブル定義(`schemas/`)・application 層 Repository interface・infrastructure DataSource 実装(append-only / Window 関数 hydration / 自前 `transaction(database){}`)を整備し、flyway versioned SQL を generateMigrations で生成する。

**Architecture:** 集約ルートは insert-once 識別子行、変化するものは append-only 履歴/イベントテーブル。current 値は `ROW_NUMBER() OVER (PARTITION BY <group> ORDER BY id DESC) = 1` のサブクエリで採る(N+1 回避)。DataSource は Repository を実装し、各メソッドで `transaction(database){}` を自前に張る(`tx()`/plugin 廃止)。VO ↔ column 変換は VO の public アクセサ(本 plan で internal を解除)+ `kotlin.time.Instant` 専用 `instantTz` transform column で行う。

**Tech Stack:** Kotlin/JVM、Exposed 1.3.0(`exposed-core` / `exposed-jdbc` / `exposed-kotlin-datetime`)、exposed gradle plugin(generateMigrations / testcontainers postgres:18)、flyway(適用は P5)、PostgreSQL。

**Source spec:** `docs/superpowers/specs/2026-06-01-p4-backend-infra-migration-design.md`(2026-06-02 改訂版)。

---

## 事前に確定した設計判断(本 plan の前提・レビューで覆せる)

これらは spec 改訂時にユーザ承認済み。**plan レビュー段階で異論があればここで止める。**

1. **VO アクセサ public 化(回答 A)**: P1/P2 で VO の生値アクセサが `internal operator fun invoke()` になり、別モジュール `:backend:core` から呼べない(コンパイルで確認: `Cannot access 'invoke': it is internal`)。本 plan の Task 1 で対象 VO の `invoke()` を public に広げる(`internal` を外すだけ)。wire-format 原則(deserialize でも primitive 往復前提)・既存 `toString()` 漏洩とも整合。
2. **Instant 列は `timestamptz`(`timestampWithTimeZone`)+ 変換層**: ドメインの時刻 VO は `kotlin.time.Instant`。TZ無し `timestamp()` は意味的に `LocalDateTime` 向きでミスマッチ(ユーザ指摘)。`kotlin.time.Instant` の意味を保つため timestamptz を使い、`Instant ↔ java.time.OffsetDateTime` を Exposed の `Column.transform` で吸収する `instantTz` 拡張を定義する(Task 1 で compile 検証済みの形あり)。
   - ただし**ドメインが値を持たない監査時刻**(`created_at` / `recorded_at` / `linked_at`)は write-only で hydrate しないため、変換不要の `timestampWithTimeZone(...).defaultExpression(CurrentTimestampWithTimeZone)` を使い DB デフォルトで打刻する。`instantTz`(read+write)が要るのは `stock_movements.occurred_at`(= `OccurredAt`)だけ。
3. **current 取得は ROW_NUMBER Window**(spec 準拠)。検証済み DSL(Task 1 参照)を全 hydration で使う。
4. **product register は 2 メソッド**: `registerAdopted(product, householdId, catalogItemId)` / `registerCustom(product, householdId)`(spec 改訂の revision decision)。
5. **P4 はテストを書かない**(spec のユーザ判断)。検証は ①`:backend:core:build` 緑 ②`generateMigrations` が DDL を生成 ③生成 SQL 目視レビュー。

---

## 検証済みファクト(recon 結果・コード貼り付け根拠)

- VO 生値アクセサは全て `internal operator fun invoke(): T`(ID は `Uuid`/`Long`、String VO は `String`、`Quantity`/`MinimumStock` は `Int`、`OccurredAt` は `kotlin.time.Instant`)。enum は `.name`。`AuthIdentity(provider: AuthProvider, subject: AuthSubject)`、`AuthProvider { ZITADEL }`。
- ID は `@JvmInline value class`、`Xxx(value: Uuid)` コンストラクタ + `create()`。`MovementId(value: Long)`(`init { require(value >= 0) }`)。
- 集約 shape: `Resident(id, profile: Profile(displayName))` / `Household(id, profile: Profile(name), members: Members(list))` / `HouseholdMember(resident, role)` / `Invitation(householdId, code, grantedRole, validity)` / `CatalogItem(id, jan, name)` / `Product(id, name, barcode, setting: StockingPolicy(unit, minimumStock), image, status)` / `Stock(product, movements: StockMovements(list))` / `StockMovement`(sealed: Replenishment/Consumption/Correction、共通 `identity/quantity/occurredAt/actor/note`、Correction は + `target: MovementId, reason: Reason`) / `MovementIdentity`(Pending / Persisted(id: MovementId))。FCC は全て `data class Xxx(val list: List<T>)`。
- Exposed 1.3.0 で検証済み(`:backend:core:compileKotlin` 緑):
  - `instantTz`(下記コード)。
  - `rowNumber().over().partitionBy(col).orderBy(col to SortOrder.DESC)` → `.alias("rn")` → 内側 select に含めて `.alias("latest")` → 外側で `where { sub[rnAlias] eq 1L }`。
- 旧 teardown 済みコードの基底: `AggregateRootTable`(uuid id PK)/`HistoryTable`(long autoIncrement id PK)。本 plan では集約ルート id は**ドメイン採番**(`autoGenerate` しない)。
- 現行 `backend/core/build.gradle.kts` の `exposed.migrations.tablesPackage` = `net.brightroom.mindstock.infrastructure.datasource`、`fileDirectory` = `src/main/resources/db/migration`、`testContainersImageName` = `postgres:18.0-alpine`。

---

## File Structure

新規/変更ファイル(`<root>` = `/Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock`):

**domain(VO アクセサ public 化 — Task 1)**
- Modify: `domain/.../model/**/<VO>.kt`(`internal operator fun invoke()` → `operator fun invoke()`)

**backend:core build(Task 2)**
- Modify: `backend/core/build.gradle.kts`(`tablesPackage` を `...datasource.schemas` へ)

**schemas(Exposed テーブル定義) — `net.brightroom.mindstock.infrastructure.datasource.schemas`(Task 3〜)**
- `schemas/TableBases.kt`(`AggregateRootTable` / `HistoryTable`)
- `schemas/Conversions.kt`(`Table.instantTz(name)`)
- `schemas/ResidentsTable.kt` / `ResidentAuthIdentitiesTable.kt` / `ResidentDisplayNamesTable.kt`
- `schemas/HouseholdsTable.kt` / `HouseholdNamesTable.kt` / `HouseholdMembershipEventsTable.kt`
- `schemas/InvitationsTable.kt` / `InvitationValidityEventsTable.kt`
- `schemas/CatalogItemsTable.kt`
- `schemas/ProductsTable.kt` / `ProductCatalogLinksTable.kt` / `ProductRevisionsTable.kt` / `ProductWantedEventsTable.kt`
- `schemas/StockMovementsTable.kt`

**application Repository interface — `net.brightroom.mindstock.application.repository.<ctx>`(各 context タスク)**
- `repository/resident/ResidentRepository.kt` / `ResidentRegisterRepository.kt`
- `repository/household/HouseholdRepository.kt` / `HouseholdRegisterRepository.kt`
- `repository/invitation/InvitationRepository.kt` / `InvitationRegisterRepository.kt`
- `repository/catalog/CatalogRepository.kt` / `CatalogRegisterRepository.kt`
- `repository/product/ProductRepository.kt` / `ProductRegisterRepository.kt`
- `repository/stock/StockRepository.kt` / `StockRegisterRepository.kt`

**infrastructure DataSource + Hydration — `net.brightroom.mindstock.infrastructure.datasource.<ctx>`(各 context タスク)**
- `datasource/Windows.kt`(共有 latest-per-partition ヘルパー)
- `datasource/resident/ResidentDataSource.kt` / `ResidentRegisterDataSource.kt` / `ResidentHydration.kt`
- `datasource/household/HouseholdDataSource.kt` / `HouseholdRegisterDataSource.kt` / `HouseholdHydration.kt`
- `datasource/invitation/InvitationDataSource.kt` / `InvitationRegisterDataSource.kt` / `InvitationHydration.kt`
- `datasource/catalog/CatalogDataSource.kt` / `CatalogRegisterDataSource.kt` / `CatalogItemHydration.kt`
- `datasource/product/ProductDataSource.kt` / `ProductRegisterDataSource.kt` / `ProductHydration.kt`
- `datasource/stock/StockDataSource.kt` / `StockRegisterDataSource.kt` / `StockHydration.kt`

**migration(Task 10)**
- Generate: `backend/core/src/main/resources/db/migration/V1__init.sql`

**rule / memory(Task 11)**
- Modify: `.claude/rules/software-architecture.md` / `.claude/rules/rpc-and-transactions.md`
- Modify: memory `krpc-ws-pipeline-gotchas` / `full-replace-2026-06`

---

## Task 1: domain VO アクセサを public 化

**Files:**
- Modify: 下記 VO ファイル群(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/...`)

対象(各ファイルの `internal operator fun invoke()` から `internal` を外す):

| VO | パス(`.../model/` 起点) |
|---|---|
| ResidentId | `resident/identity/ResidentId.kt` |
| HouseholdId | `household/HouseholdId.kt` |
| ProductId | `inventory/product/ProductId.kt` |
| CatalogItemId | `catalog/item/CatalogItemId.kt` |
| MovementId | `inventory/stock/movement/MovementId.kt` |
| DisplayName | `resident/profile/DisplayName.kt` |
| HouseholdName | `household/HouseholdName.kt` |
| ProductName | `inventory/product/ProductName.kt` |
| CatalogItemName | `catalog/content/CatalogItemName.kt` |
| ProductUnit | `inventory/product/setting/ProductUnit.kt` |
| Note | `inventory/stock/movement/Note.kt` |
| Reason | `inventory/stock/movement/Reason.kt` |
| Jan | `barcode/Jan.kt` |
| ImageRef | `inventory/product/image/ImageRef.kt` |
| AuthSubject | `resident/identity/auth/AuthIdentity.kt`(同ファイル内) |
| InvitationCode | `household/invitation/InvitationCode.kt` |
| Quantity | `inventory/quantity/Quantity.kt` |
| MinimumStock | `inventory/product/setting/MinimumStock.kt` |
| OccurredAt | `inventory/stock/movement/OccurredAt.kt` |

- [ ] **Step 1: 各 VO の `invoke()` から `internal` を外す**

例(`ResidentId.kt`):

```kotlin
// Before
internal operator fun invoke(): Uuid = value
// After
operator fun invoke(): Uuid = value
```

上表の全 VO に同じ変更を適用する(返り値の型はファイルごとに `Uuid` / `Long` / `String` / `Int` / `Instant` のまま)。`MinimumStock` の `isBelow`/`shortage` 等の既存 public メソッドはそのまま。`InvitationCode` / `Jan` 等の `companion`・`init` は触らない。

- [ ] **Step 2: domain がコンパイル緑か確認**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL(全 KMP ターゲット)。`invoke()` の可視性拡大は additive で既存呼び出しを壊さない。ktlint も影響なし。

- [ ] **Step 3: 別モジュールから読めることを確認(使い捨て probe)**

`backend/core/src/main/kotlin/net/brightroom/mindstock/_probe/Probe.kt` を作成:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package net.brightroom.mindstock._probe

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity

fun probe(id: HouseholdId, n: ProductName, q: Quantity) = Triple(id(), n(), q())
```

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL(Task 開始前は `Cannot access 'invoke': it is internal` で失敗していた)。確認後 `rm -rf backend/core/src/main/kotlin/net/brightroom/mindstock/_probe`。

- [ ] **Step 4: Commit**

```bash
git add domain
git commit -m "refactor(domain): VO の生値アクセサ invoke() を public 化(永続層の VO↔primitive 変換用)"
```

---

## Task 2: build.gradle の tablesPackage を schemas へ

**Files:**
- Modify: `backend/core/build.gradle.kts:33`

- [ ] **Step 1: tablesPackage を変更**

```kotlin
// Before
tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource")
// After
tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource.schemas")
```

`fileDirectory` / `testContainersImageName` は変更しない。

- [ ] **Step 2: 設定が読めるか確認**

Run: `./gradlew :backend:core:help -q`
Expected: BUILD SUCCESSFUL(設定評価でエラーが出ないこと。テーブルがまだ無くても OK)。

- [ ] **Step 3: Commit**

```bash
git add backend/core/build.gradle.kts
git commit -m "build(core): exposed tablesPackage を infrastructure.datasource.schemas に更新"
```

---

## Task 3: schemas の基盤(base tables / 変換層)

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/TableBases.kt`
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/Conversions.kt`

- [ ] **Step 1: 変換層 `instantTz` を作成**

`Conversions.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * `kotlin.time.Instant` を timestamptz 列にマッピングする。
 * Exposed の `timestampWithTimeZone` は `java.time.OffsetDateTime` を返すため、
 * UTC 固定で `Instant` と相互変換する(Instant は時点なので UTC offset で十分)。
 */
fun Table.instantTz(name: String): Column<Instant> =
    timestampWithTimeZone(name).transform(
        unwrap = { it.toJavaInstant().atOffset(ZoneOffset.UTC) },
        wrap = { it.toInstant().toKotlinInstant() },
    )
```

- [ ] **Step 2: base tables を作成**

`TableBases.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.Table

/** insert-once 識別子行。id はドメイン採番(autoGenerate しない)。 */
abstract class AggregateRootTable(name: String) : Table(name) {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)
}

/** append-only 履歴/イベント。id は単調増加 bigint(Window の ORDER キー)。 */
abstract class HistoryTable(name: String) : Table(name) {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)
}
```

> **重要(id 読み戻し)**: `HistoryTable` は `LongIdTable` ではなく素の `Table`(`id` は `Column<Long>`)。`LongIdTable` にすると `id` が `Column<EntityID<Long>>` になり全 `reference(...)` と全 `row[Table.id]` 読みが `EntityID` 化して波及するため**採用しない**。採番された id を読み戻すときは `insertAndGetId`(IdTable 専用)ではなく **`insert { } get StockMovementsTable.id`** を使う(Long が直接返る)。`row[HistoryTable.id]` は `Long` をそのまま返す。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas
git commit -m "feat(core): schemas 基盤(AggregateRootTable/HistoryTable・instantTz 変換層)"
```

---

## Task 4: 共有 Window ヘルパー

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/Windows.kt`

current = `ROW_NUMBER() OVER (PARTITION BY <group> ORDER BY id DESC) = 1` を組むヘルパー。各 hydration が「最新行サブクエリ」を作るのに使う。

- [ ] **Step 1: ヘルパーを作成**

`Windows.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

/**
 * append-only 履歴テーブルの「partition ごと最新行(id DESC で 1 番目)」だけを含む
 * サブクエリ alias を返す。order キーは単調増加 bigint id(同時刻衝突を避ける)。
 *
 * 使い方:
 *   val latest = HouseholdNamesTable.latestPerPartition(
 *       partitionBy = HouseholdNamesTable.householdId,
 *       columns = listOf(HouseholdNamesTable.householdId, HouseholdNamesTable.name),
 *   )
 *   // latest.alias を join し、latest.alias[HouseholdNamesTable.name] で読む
 */
class LatestPartition(
    val alias: QueryAlias,
    private val table: Table,
) {
    operator fun <T> get(column: Column<T>): Expression<T> = alias[column]
}

fun Table.latestPerPartition(
    partitionBy: Column<*>,
    columns: List<Column<*>>,
): LatestPartition {
    val idCol = (this as net.brightroom.mindstock.infrastructure.datasource.schemas.HistoryTable).id
    val rn =
        org.jetbrains.exposed.v1.core
            .rowNumber()
            .over()
            .partitionBy(partitionBy)
            .orderBy(idCol to SortOrder.DESC)
    val rnAlias = rn.alias("rn")
    val sub = this.select(columns + rnAlias).alias("latest_${tableName}")
    return LatestPartition(sub, this)
        .also { _ -> /* rn=1 フィルタは呼び出し側で sub[rnAlias] eq 1L */ }
}
```

> 注: Exposed の window 列を alias 越しに `where` する形は recon で compile 検証済み(`sub[rnAlias] eq 1L`)。本ヘルパーは「最新行だけのサブクエリ」を表現するが、`rnAlias` を呼び出し側に渡す必要があるため、**実装が複雑なら各 Hydration にインライン展開してよい**(下記 Task 5 の `ResidentHydration` は検証済みインライン形を示す)。ヘルパー抽象に固執せず、まず動く形を優先する。

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。型が合わない/`rnAlias` 受け渡しが煩雑な場合は、このヘルパーを削除し各 Hydration に下記検証済みインライン形(Task 5 Step 2)を直接書く方針へ切り替える。

- [ ] **Step 3: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/Windows.kt
git commit -m "feat(core): latest-per-partition Window ヘルパー(ROW_NUMBER)"
```

---

## Task 5: resident context(schema + repository + datasource + hydration)

**Files:**
- Create: `schemas/ResidentsTable.kt` / `ResidentAuthIdentitiesTable.kt` / `ResidentDisplayNamesTable.kt`
- Create: `application/repository/resident/ResidentRepository.kt` / `ResidentRegisterRepository.kt`
- Create: `infrastructure/datasource/resident/ResidentDataSource.kt` / `ResidentRegisterDataSource.kt` / `ResidentHydration.kt`

- [ ] **Step 1: テーブル定義**

`schemas/ResidentsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ResidentsTable : AggregateRootTable("residents") {
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
```

`schemas/ResidentAuthIdentitiesTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ResidentAuthIdentitiesTable : HistoryTable("resident_auth_identities") {
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val provider = enumerationByName("provider", 20, AuthProvider::class)
    val subject = varchar("subject", 255)
    val linkedAt = timestampWithTimeZone("linked_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(provider, subject)
        index(false, residentId)
    }
}
```

`schemas/ResidentDisplayNamesTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ResidentDisplayNamesTable : HistoryTable("resident_display_names") {
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val displayName = varchar("display_name", 100)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, residentId, id)
    }
}
```

- [ ] **Step 2: Hydration(検証済みインライン Window 形)**

`infrastructure/datasource/resident/ResidentHydration.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.rowNumber

/**
 * resident_display_names の「resident_id ごと最新行」だけを含むサブクエリ alias を作る。
 * 列: resident_id / display_name(rn=1 で絞る)。
 */
internal fun latestResidentDisplayNames(): Pair<QueryAlias, ResidentDisplayNamesLatestRefs> {
    val rn =
        rowNumber()
            .over()
            .partitionBy(ResidentDisplayNamesTable.residentId)
            .orderBy(ResidentDisplayNamesTable.id to SortOrder.DESC)
    val rnAlias = rn.alias("rn")
    val sub =
        ResidentDisplayNamesTable
            .select(
                ResidentDisplayNamesTable.residentId,
                ResidentDisplayNamesTable.displayName,
                rnAlias,
            ).alias("latest_display_names")
    return sub to ResidentDisplayNamesLatestRefs(rnAlias)
}

internal class ResidentDisplayNamesLatestRefs(
    val rn: org.jetbrains.exposed.v1.core.ExpressionAlias<Long>,
)

/** residents.id + display_name 行から Resident を組み立てる(両テーブルの ResultRow を渡す)。 */
internal fun ResultRow.toResident(displayNameAlias: QueryAlias): Resident =
    Resident(
        id = ResidentId(this[net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable.id]),
        profile = Profile(DisplayName(this[displayNameAlias[ResidentDisplayNamesTable.displayName]])),
    )
```

> Window サブクエリの「rn=1 フィルタ」は、これを join して使う DataSource 側の `.where { sub[rnAlias] eq 1L }` で行う(recon で compile 検証済み)。上の `select` には `rnAlias` を含めること。`import org.jetbrains.exposed.v1.jdbc.select` を DataSource 側で使う。

- [ ] **Step 3: Reader Repository interface**

`application/repository/resident/ResidentRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity

interface ResidentRepository {
    /** 認証境界 VO で resident を解決(初回ログイン=未登録 sub は ResourceNotFoundException)。 */
    fun findByAuth(authIdentity: AuthIdentity): Resident

    fun findById(id: ResidentId): Resident
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/resident/ResidentRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

interface ResidentRegisterRepository {
    /** residents + auth + 初回 display_name を INSERT して Resident を返す(id はここで採番)。 */
    fun registerResident(authIdentity: AuthIdentity, displayName: DisplayName): Resident

    /** display_name を 1 行 append(registerDisplayName/rename 兼用)。最新 Resident を返す。 */
    fun appendDisplayName(residentId: ResidentId, displayName: DisplayName): Resident
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/resident/ResidentDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentAuthIdentitiesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ResidentDataSource(
    private val database: Database,
) : ResidentRepository {
    override fun findByAuth(authIdentity: AuthIdentity): Resident =
        transaction(database) {
            val residentId =
                ResidentAuthIdentitiesTable
                    .select(ResidentAuthIdentitiesTable.residentId)
                    .where {
                        (ResidentAuthIdentitiesTable.provider eq authIdentity.provider) and
                            (ResidentAuthIdentitiesTable.subject eq authIdentity.subject())
                    }.limit(1)
                    .firstOrNull()
                    ?.get(ResidentAuthIdentitiesTable.residentId)
                    ?: throw ResourceNotFoundException("resident not found for auth: ${authIdentity.provider}")
            hydrate(ResidentId(residentId))
        }

    override fun findById(id: ResidentId): Resident = transaction(database) { hydrate(id) }

    private fun hydrate(id: ResidentId): Resident {
        val (sub, refs) = latestResidentDisplayNames()
        val row =
            ResidentsTable
                .join(sub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = sub[net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable.residentId])
                .selectAll()
                .where { (ResidentsTable.id eq id()) and (sub[refs.rn] eq 1L) }
                .limit(1)
                .firstOrNull()
                ?: throw ResourceNotFoundException("resident not found: $id")
        return row.toResident(sub)
    }
}
```

> `firstOrNull` は `org.jetbrains.exposed.v1.jdbc` の Query 拡張。`provider eq authIdentity.provider` は enum 同士の比較(`enumerationByName` 列なので直接可)。

- [ ] **Step 6: Writer DataSource**

`infrastructure/datasource/resident/ResidentRegisterDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentAuthIdentitiesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ResidentRegisterDataSource(
    private val database: Database,
) : ResidentRegisterRepository {
    override fun registerResident(authIdentity: AuthIdentity, displayName: DisplayName): Resident =
        transaction(database) {
            val residentId = ResidentId.create()
            ResidentsTable.insert { it[id] = residentId() }
            ResidentAuthIdentitiesTable.insert {
                it[ResidentAuthIdentitiesTable.residentId] = residentId()
                it[provider] = authIdentity.provider
                it[subject] = authIdentity.subject()
            }
            ResidentDisplayNamesTable.insert {
                it[ResidentDisplayNamesTable.residentId] = residentId()
                it[ResidentDisplayNamesTable.displayName] = displayName()
            }
            Resident(residentId, Profile(displayName))
        }

    override fun appendDisplayName(residentId: ResidentId, displayName: DisplayName): Resident =
        transaction(database) {
            ResidentDisplayNamesTable.insert {
                it[ResidentDisplayNamesTable.residentId] = residentId()
                it[ResidentDisplayNamesTable.displayName] = displayName()
            }
            Resident(residentId, Profile(displayName))
        }
}
```

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。失敗時は Window join(`sub[refs.rn] eq 1L`)の型・import を中心に修正。`ExpressionAlias`/`QueryAlias` の import パス(`org.jetbrains.exposed.v1.core`)を確認。

- [ ] **Step 8: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/Resident*.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/resident \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/resident
git commit -m "feat(core): resident の schema/repository/datasource/hydration"
```

---

## Task 6: household context

**Files:**
- Create: `schemas/HouseholdsTable.kt` / `HouseholdNamesTable.kt` / `HouseholdMembershipEventsTable.kt`
- Create: `application/repository/household/HouseholdRepository.kt` / `HouseholdRegisterRepository.kt`
- Create: `infrastructure/datasource/household/HouseholdDataSource.kt` / `HouseholdRegisterDataSource.kt` / `HouseholdHydration.kt`

membership は `(household_id, resident_id)` partition の最新イベントで `status='所属'` のものだけが current メンバー。`status` はドメイン enum ではなく永続専用リテラル(`所属`/`除外`)。

- [ ] **Step 1: テーブル定義**

`schemas/HouseholdsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdsTable : AggregateRootTable("households") {
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
```

`schemas/HouseholdNamesTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdNamesTable : HistoryTable("household_names") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 30)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, householdId, id)
    }
}
```

`schemas/HouseholdMembershipEventsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdMembershipEventsTable : HistoryTable("household_membership_events") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val role = enumerationByName("role", 20, HouseholdMemberRole::class)
    val status = varchar("status", 10) // 永続専用: 所属 / 除外(tombstone)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, householdId, residentId, id)
    }

    const val STATUS_ACTIVE = "所属"
    const val STATUS_REMOVED = "除外"
}
```

- [ ] **Step 2: Hydration**

`infrastructure/datasource/household/HouseholdHydration.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident

/** Members を組み立てる(member ごとに hydrate 済み Resident を渡す)。 */
internal fun assembleHousehold(
    id: HouseholdId,
    name: HouseholdName,
    members: List<HouseholdMember>,
): Household = Household(id, Profile(name), Members(members))

internal fun member(resident: Resident, role: net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole): HouseholdMember =
    HouseholdMember(resident, role)
```

> household の hydration は ①最新 household_name ②current メンバー(membership window で rn=1 かつ status=所属)③各メンバーの Resident(residents × 最新 display_name のバッチロード)を組む。Resident バッチロードは Task 5 の `latestResidentDisplayNames()` を再利用。

- [ ] **Step 3: Reader Repository interface**

`application/repository/household/HouseholdRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

interface HouseholdRepository {
    fun findById(id: HouseholdId): Household

    /** resident が current メンバーである世帯一覧(空なら空 Households)。 */
    fun listByResident(residentId: ResidentId): Households
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/household/HouseholdRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

interface HouseholdRegisterRepository {
    /** households + 初回 household_name + owner の 所属 event を INSERT(Household.create 相当の永続化)。 */
    fun registerHousehold(household: Household): Household

    /** household_name を 1 行 append(rename)。 */
    fun appendHouseholdName(householdId: HouseholdId, name: HouseholdName)

    /** 所属 event を append(join)。 */
    fun joinMember(householdId: HouseholdId, resident: Resident, role: HouseholdMemberRole)

    /** 所属 + 新 role event を append(changeRole)。 */
    fun changeMemberRole(householdId: HouseholdId, residentId: ResidentId, role: HouseholdMemberRole)

    /** 除外 tombstone event を append(leave / removeMember。DELETE しない)。 */
    fun removeMember(householdId: HouseholdId, residentId: ResidentId)
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/household/HouseholdDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile as ResidentProfile
import net.brightroom.mindstock.infrastructure.datasource.resident.latestResidentDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HouseholdDataSource(
    private val database: Database,
) : HouseholdRepository {
    override fun findById(id: HouseholdId): Household = transaction(database) { hydrate(id) }

    override fun listByResident(residentId: ResidentId): Households =
        transaction(database) {
            // current メンバーである household_id を集める(membership window rn=1 & status=所属)
            val ids = currentHouseholdIds(residentId)
            Households(ids.map { hydrate(it) })
        }

    private fun currentHouseholdIds(residentId: ResidentId): List<HouseholdId> {
        val rn =
            rowNumber().over()
                .partitionBy(HouseholdMembershipEventsTable.householdId, HouseholdMembershipEventsTable.residentId)
                .orderBy(HouseholdMembershipEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdMembershipEventsTable
                .select(
                    HouseholdMembershipEventsTable.householdId,
                    HouseholdMembershipEventsTable.residentId,
                    HouseholdMembershipEventsTable.status,
                    rnAlias,
                ).where { HouseholdMembershipEventsTable.residentId eq residentId() }
                .alias("latest_membership")
        return sub
            .selectAll()
            .where {
                (sub[rnAlias] eq 1L) and
                    (sub[HouseholdMembershipEventsTable.status] eq HouseholdMembershipEventsTable.STATUS_ACTIVE)
            }.map { HouseholdId(it[sub[HouseholdMembershipEventsTable.householdId]]) }
    }

    private fun hydrate(id: HouseholdId): Household {
        val name =
            latestHouseholdName(id) ?: throw ResourceNotFoundException("household not found: $id")
        val members = currentMembers(id)
        return assembleHousehold(id, name, members)
    }

    private fun latestHouseholdName(id: HouseholdId): HouseholdName? {
        val rn =
            rowNumber().over()
                .partitionBy(HouseholdNamesTable.householdId)
                .orderBy(HouseholdNamesTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val sub =
            HouseholdNamesTable
                .select(HouseholdNamesTable.householdId, HouseholdNamesTable.name, rnAlias)
                .where { HouseholdNamesTable.householdId eq id() }
                .alias("latest_name")
        return sub
            .selectAll()
            .where { sub[rnAlias] eq 1L }
            .limit(1)
            .firstOrNull()
            ?.let { HouseholdName(it[sub[HouseholdNamesTable.name]]) }
    }

    private fun currentMembers(id: HouseholdId): List<HouseholdMember> {
        // current membership rows(window rn=1 & status=所属)
        val rn =
            rowNumber().over()
                .partitionBy(HouseholdMembershipEventsTable.householdId, HouseholdMembershipEventsTable.residentId)
                .orderBy(HouseholdMembershipEventsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val mSub =
            HouseholdMembershipEventsTable
                .select(
                    HouseholdMembershipEventsTable.householdId,
                    HouseholdMembershipEventsTable.residentId,
                    HouseholdMembershipEventsTable.role,
                    HouseholdMembershipEventsTable.status,
                    rnAlias,
                ).where { HouseholdMembershipEventsTable.householdId eq id() }
                .alias("latest_member")

        // resident display name のバッチロード join
        val (dnSub, dnRefs) = latestResidentDisplayNames()

        return mSub
            .join(ResidentsTable, JoinType.INNER, onColumn = mSub[HouseholdMembershipEventsTable.residentId], otherColumn = ResidentsTable.id)
            .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
            .selectAll()
            .where {
                (mSub[rnAlias] eq 1L) and
                    (mSub[HouseholdMembershipEventsTable.status] eq HouseholdMembershipEventsTable.STATUS_ACTIVE) and
                    (dnSub[dnRefs.rn] eq 1L)
            }.orderBy(mSub[HouseholdMembershipEventsTable.residentId] to SortOrder.ASC)
            .map { row ->
                val resident =
                    Resident(
                        ResidentId(row[mSub[HouseholdMembershipEventsTable.residentId]]),
                        ResidentProfile(DisplayName(row[dnSub[ResidentDisplayNamesTable.displayName]])),
                    )
                member(resident, row[mSub[HouseholdMembershipEventsTable.role]])
            }
    }
}
```

> 注: 複数 alias を跨ぐ join + window は Exposed で最も込み入る箇所。compile が通らない場合は、まず `currentMembers` を「membership current を 1 クエリ → resident_id を集めて display_name を別 1 クエリでバッチ取得 → メモリで突合」の **2 クエリ方式**に分解してよい(N+1 にはならない)。spec の「actor バッチロード」と同じ発想。動く形を優先。

- [ ] **Step 6: Writer DataSource**

`infrastructure/datasource/household/HouseholdRegisterDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.application.repository.household.HouseholdRegisterRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdMembershipEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.HouseholdsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HouseholdRegisterDataSource(
    private val database: Database,
) : HouseholdRegisterRepository {
    override fun registerHousehold(household: Household): Household =
        transaction(database) {
            HouseholdsTable.insert { it[id] = household.id() }
            HouseholdNamesTable.insert {
                it[householdId] = household.id()
                it[name] = household.profile.name()
            }
            household.members.list.forEach { m ->
                HouseholdMembershipEventsTable.insert {
                    it[householdId] = household.id()
                    it[residentId] = m.resident.id()
                    it[role] = m.role
                    it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
                }
            }
            household
        }

    override fun appendHouseholdName(householdId: HouseholdId, name: HouseholdName) {
        transaction(database) {
            HouseholdNamesTable.insert {
                it[HouseholdNamesTable.householdId] = householdId()
                it[HouseholdNamesTable.name] = name()
            }
        }
    }

    override fun joinMember(householdId: HouseholdId, resident: Resident, role: HouseholdMemberRole) {
        transaction(database) {
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[residentId] = resident.id()
                it[HouseholdMembershipEventsTable.role] = role
                it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
            }
        }
    }

    override fun changeMemberRole(householdId: HouseholdId, residentId: ResidentId, role: HouseholdMemberRole) {
        transaction(database) {
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[HouseholdMembershipEventsTable.residentId] = residentId()
                it[HouseholdMembershipEventsTable.role] = role
                it[status] = HouseholdMembershipEventsTable.STATUS_ACTIVE
            }
        }
    }

    override fun removeMember(householdId: HouseholdId, residentId: ResidentId) {
        transaction(database) {
            // role 列は NOT NULL。除外時点の role が不要でも何か入れる必要があるため、
            // current の role を読んで踏襲する(無ければ閲覧者を既定)。
            val currentRole = HouseholdMemberRole.閲覧者
            HouseholdMembershipEventsTable.insert {
                it[HouseholdMembershipEventsTable.householdId] = householdId()
                it[HouseholdMembershipEventsTable.residentId] = residentId()
                it[role] = currentRole
                it[status] = HouseholdMembershipEventsTable.STATUS_REMOVED
            }
        }
    }
}
```

> `removeMember` の role は tombstone なので意味を持たないが NOT NULL を満たす必要がある。厳密に「除外時点の role」を残したい場合は current role を window で引いて入れる(P5 で要否判断。P4 は既定値で可)。要解決として残す。

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/Household*.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household
git commit -m "feat(core): household の schema/repository/datasource/hydration(membership tombstone・Window)"
```

---

## Task 7: invitation context

**Files:**
- Create: `schemas/InvitationsTable.kt` / `InvitationValidityEventsTable.kt`
- Create: `application/repository/invitation/InvitationRepository.kt` / `InvitationRegisterRepository.kt`
- Create: `infrastructure/datasource/invitation/InvitationDataSource.kt` / `InvitationRegisterDataSource.kt` / `InvitationHydration.kt`

`code` はグローバル一意 PK。issue 時 PK 衝突は code 再生成で最大 3 回リトライ(last-write-wins の唯一の例外)。`Invitation.householdId` は `internal val`(Task 1 では非対象。invitation は code で解決するため hydration に householdId が必要 → **要確認**: `Invitation.householdId` が `internal` だと別モジュールから読めない。Step 1 で確認し、必要なら Task 1 対象に追加)。

- [ ] **Step 0: `Invitation.householdId` の可視性確認**

`domain/.../household/invitation/Invitation.kt` を開き、`householdId` が `internal val` なら `val`(public)に変更し `:domain:build` を通す(Task 1 と同種の理由)。`issue(householdId, grantedRole)` ファクトリと `code`/`grantedRole`/`validity` はそのまま。

- [ ] **Step 1: テーブル定義**

`schemas/InvitationsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object InvitationsTable : Table("invitations") {
    val code = varchar("code", 6)
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val grantedRole = enumerationByName("granted_role", 20, HouseholdMemberRole::class)
    override val primaryKey = PrimaryKey(code)

    init {
        index(false, householdId)
    }
}
```

`schemas/InvitationValidityEventsTable.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object InvitationValidityEventsTable : HistoryTable("invitation_validity_events") {
    val invitationCode = reference("invitation_code", InvitationsTable.code, onDelete = ReferenceOption.RESTRICT)
    val validity = enumerationByName("validity", 10, InvitationValidity::class)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, invitationCode, id)
    }
}
```

- [ ] **Step 2: Hydration**

`infrastructure/datasource/invitation/InvitationHydration.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

internal fun assembleInvitation(
    householdId: HouseholdId,
    code: InvitationCode,
    grantedRole: HouseholdMemberRole,
    validity: InvitationValidity,
): Invitation = Invitation(householdId, code, grantedRole, validity)
```

> `Invitation` の primary constructor は `(householdId, code, grantedRole, validity)`。`householdId` が public 化済み(Step 0)であること。

- [ ] **Step 3: Reader Repository interface**

`application/repository/invitation/InvitationRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.invitation

import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

interface InvitationRepository {
    /** code でグローバルに解決(join 用)。不在は ResourceNotFoundException。 */
    fun findByCode(code: InvitationCode): Invitation
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/invitation/InvitationRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.invitation

import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode

interface InvitationRegisterRepository {
    /**
     * invitations(insert-once)+ 有効 event を INSERT。
     * code PK 衝突時は呼び出し側が code を再生成した Invitation で再試行する(最大 3 回)。
     * 衝突は unique violation を InvitationCodeCollisionException 等で表現せず、実装内でリトライ。
     */
    fun issue(invitation: Invitation): Invitation

    /** 無効 event を append(revoke)。 */
    fun revoke(code: InvitationCode)
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/invitation/InvitationDataSource.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationValidityEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class InvitationDataSource(
    private val database: Database,
) : InvitationRepository {
    override fun findByCode(code: InvitationCode): Invitation =
        transaction(database) {
            val base =
                InvitationsTable
                    .selectAll()
                    .where { InvitationsTable.code eq code() }
                    .limit(1)
                    .firstOrNull()
                    ?: throw ResourceNotFoundException("invitation not found: $code")

            val rn =
                rowNumber().over()
                    .partitionBy(InvitationValidityEventsTable.invitationCode)
                    .orderBy(InvitationValidityEventsTable.id to SortOrder.DESC)
            val rnAlias = rn.alias("rn")
            val vSub =
                InvitationValidityEventsTable
                    .select(InvitationValidityEventsTable.invitationCode, InvitationValidityEventsTable.validity, rnAlias)
                    .where { InvitationValidityEventsTable.invitationCode eq code() }
                    .alias("latest_validity")
            val validity =
                vSub
                    .selectAll()
                    .where { vSub[rnAlias] eq 1L }
                    .limit(1)
                    .first()[vSub[InvitationValidityEventsTable.validity]]

            assembleInvitation(
                householdId = HouseholdId(base[InvitationsTable.householdId]),
                code = code,
                grantedRole = base[InvitationsTable.grantedRole],
                validity = validity,
            )
        }
}
```

- [ ] **Step 6: Writer DataSource(衝突リトライ)**

`infrastructure/datasource/invitation/InvitationRegisterDataSource.kt`:

```kotlin
package net.brightroom.mindstock.infrastructure.datasource.invitation

import net.brightroom.mindstock.application.repository.invitation.InvitationRegisterRepository
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationValidityEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.InvitationsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.SQLException
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class InvitationRegisterDataSource(
    private val database: Database,
) : InvitationRegisterRepository {
    override fun issue(invitation: Invitation): Invitation =
        transaction(database) {
            // PK(code) 衝突は unique violation。発生したら code 再生成で最大 3 回リトライ。
            var current = invitation
            repeat(MAX_RETRY) { attempt ->
                try {
                    InvitationsTable.insert {
                        it[code] = current.code()
                        it[householdId] = current.householdId()
                        it[grantedRole] = current.grantedRole
                    }
                    InvitationValidityEventsTable.insert {
                        it[invitationCode] = current.code()
                        it[validity] = InvitationValidity.有効
                    }
                    return@transaction current
                } catch (e: SQLException) {
                    if (attempt == MAX_RETRY - 1) throw e
                    current = Invitation.issue(current.householdId(), current.grantedRole)
                }
            }
            error("unreachable")
        }

    override fun revoke(code: InvitationCode) {
        transaction(database) {
            InvitationValidityEventsTable.insert {
                it[invitationCode] = code()
                it[validity] = InvitationValidity.無効
            }
        }
    }

    private companion object {
        const val MAX_RETRY = 3
    }
}
```

> `Invitation.householdId()` は `internal val householdId` を public 化していれば直接 `.householdId` でも可。`current.householdId()` 表記は invoke が無いので `current.householdId`(プロパティ)に直す。**Step 0 で public 化したプロパティアクセス**として `current.householdId` を使う(invoke ではない)。`Invitation.issue(householdId, grantedRole)` で再生成。catch の `SQLException` は postgres unique violation(SQLState 23505)。厳密化したい場合は `(e as? java.sql.SQLException)?.sqlState == "23505"` で絞ってよい。

- [ ] **Step 7: コンパイル確認 → 修正(`current.householdId` プロパティ表記)**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。`householdId()` が「invoke が無い」で落ちたら `current.householdId`(プロパティ)へ修正。

- [ ] **Step 8: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/Invitation*.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/invitation \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/invitation \
        domain
git commit -m "feat(core): invitation の schema/repository/datasource(code 衝突リトライ・validity Window)"
```

---

## Task 8: catalog context

**Files:**
- Create: `schemas/CatalogItemsTable.kt`
- Create: `application/repository/catalog/CatalogRepository.kt` / `CatalogRegisterRepository.kt`
- Create: `infrastructure/datasource/catalog/CatalogDataSource.kt` / `CatalogRegisterDataSource.kt` / `CatalogItemHydration.kt`

catalog は insert-once のみ(履歴なし)。`jan` NOT NULL / UNIQUE。

- [ ] **Step 1: テーブル定義**

`schemas/CatalogItemsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val jan = varchar("jan", 13)
    val name = varchar("name", 60)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(jan)
    }
}
```

- [ ] **Step 2: Hydration**

`infrastructure/datasource/catalog/CatalogItemHydration.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toCatalogItem(): CatalogItem =
    CatalogItem(
        id = CatalogItemId(this[CatalogItemsTable.id]),
        jan = Jan(this[CatalogItemsTable.jan]),
        name = CatalogItemName(this[CatalogItemsTable.name]),
    )
```

- [ ] **Step 3: Reader Repository interface**

`application/repository/catalog/CatalogRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems

interface CatalogRepository {
    /** 名前 LIKE 検索(空なら空 CatalogItems)。 */
    fun search(query: String, limit: Int): CatalogItems

    /** JAN 照会(不在は ResourceNotFoundException → P5 で外部 API fallback)。 */
    fun findByJan(jan: Jan): CatalogItem

    fun findById(id: CatalogItemId): CatalogItem
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/catalog/CatalogRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem

interface CatalogRegisterRepository {
    /** 外部 API 取得品を catalog_items に保存(キャッシュ)。 */
    fun register(catalogItem: CatalogItem): CatalogItem
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/catalog/CatalogDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CatalogDataSource(
    private val database: Database,
) : CatalogRepository {
    override fun search(query: String, limit: Int): CatalogItems =
        transaction(database) {
            val items =
                CatalogItemsTable
                    .selectAll()
                    .where { CatalogItemsTable.name like "%$query%" }
                    .limit(limit)
                    .map { it.toCatalogItem() }
            CatalogItems(items)
        }

    override fun findByJan(jan: Jan): CatalogItem =
        transaction(database) {
            CatalogItemsTable
                .selectAll()
                .where { CatalogItemsTable.jan eq jan() }
                .limit(1)
                .firstOrNull()
                ?.toCatalogItem()
                ?: throw ResourceNotFoundException("catalog item not found for jan: $jan")
        }

    override fun findById(id: CatalogItemId): CatalogItem =
        transaction(database) {
            CatalogItemsTable
                .selectAll()
                .where { CatalogItemsTable.id eq id() }
                .limit(1)
                .firstOrNull()
                ?.toCatalogItem()
                ?: throw ResourceNotFoundException("catalog item not found: $id")
        }
}
```

> `like` は `org.jetbrains.exposed.v1.core.like`(`Column<String> like String`)。query は呼び出し側でサニタイズ前提(P5)。

- [ ] **Step 6: Writer DataSource**

`infrastructure/datasource/catalog/CatalogRegisterDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.infrastructure.datasource.schemas.CatalogItemsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CatalogRegisterDataSource(
    private val database: Database,
) : CatalogRegisterRepository {
    override fun register(catalogItem: CatalogItem): CatalogItem =
        transaction(database) {
            CatalogItemsTable.insert {
                it[id] = catalogItem.id()
                it[jan] = catalogItem.jan()
                it[name] = catalogItem.name()
            }
            catalogItem
        }
}
```

> `catalogItem.name()` は `CatalogItemName.invoke()`(public 化済み)。`catalogItem.jan()` は `Jan.invoke()`。

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/CatalogItemsTable.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/catalog
git commit -m "feat(core): catalog の schema/repository/datasource(jan UNIQUE キャッシュ)"
```

---

## Task 9: product context

**Files:**
- Create: `schemas/ProductsTable.kt` / `ProductCatalogLinksTable.kt` / `ProductRevisionsTable.kt` / `ProductWantedEventsTable.kt`
- Create: `application/repository/product/ProductRepository.kt` / `ProductRegisterRepository.kt`
- Create: `infrastructure/datasource/product/ProductDataSource.kt` / `ProductRegisterDataSource.kt` / `ProductHydration.kt`

`Product` = (id, name: ProductName, barcode: Barcode, setting: StockingPolicy(unit, minimumStock), image: ProductImage, status)。
- `products`: id / household_id / name / jan(nullable=Barcode) / created_at。
- `product_revisions`(可変設定スナップショット): unit / minimum_stock / image_ref(nullable) / status。
- `product_catalog_links`: 採用品のみ(origin 導出)。
- `product_wanted_events`: P5 read-model 用(Product 集約には hydrate しない)。

- [ ] **Step 1: テーブル定義**

`schemas/ProductsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductsTable : AggregateRootTable("products") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 60)
    val jan = varchar("jan", 13).nullable() // null = Barcode.Unlinked / 値 = Barcode.Linked
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, householdId)
    }
}
```

`schemas/ProductCatalogLinksTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ProductCatalogLinksTable : Table("product_catalog_links") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val catalogItemId = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    override val primaryKey = PrimaryKey(productId)

    init {
        index(false, catalogItemId)
    }
}
```

`schemas/ProductRevisionsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductRevisionsTable : HistoryTable("product_revisions") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val unit = varchar("unit", 10)
    val minimumStock = integer("minimum_stock")
    val imageRef = varchar("image_ref", 512).nullable() // null = ProductImage.None
    val status = enumerationByName("status", 20, ProductStatus::class)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, productId, id)
    }
}
```

`schemas/ProductWantedEventsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProductWantedEventsTable : HistoryTable("product_wanted_events") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val wanted = bool("wanted")
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, productId, id)
    }
}
```

- [ ] **Step 2: Hydration(products + 最新 revision → Product)**

`infrastructure/datasource/product/ProductHydration.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageRef
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy

/**
 * products 行 + 最新 product_revisions 行 から Product を組み立てる。
 * jan(nullable)→ Barcode、image_ref(nullable)→ ProductImage を導出。
 */
internal fun buildProduct(
    id: ProductId,
    name: String,
    jan: String?,
    unit: String,
    minimumStock: Int,
    imageRef: String?,
    status: ProductStatus,
): Product =
    Product(
        id = id,
        name = ProductName(name),
        barcode = jan?.let { Barcode.Linked(Jan(it)) } ?: Barcode.Unlinked,
        setting = StockingPolicy(ProductUnit(unit), MinimumStock(minimumStock)),
        image = imageRef?.let { ProductImage.Stored(ImageRef(it)) } ?: ProductImage.None,
        status = status,
    )

/** Product → jan 列値(Barcode を潰す)。 */
internal fun Barcode.toJanColumn(): String? =
    when (this) {
        is Barcode.Linked -> jan()
        Barcode.Unlinked -> null
    }

/** Product → image_ref 列値。 */
internal fun ProductImage.toImageRefColumn(): String? =
    when (this) {
        is ProductImage.Stored -> ref()
        ProductImage.None -> null
    }
```

> `jan()` は `Jan.invoke()`(public 化済み)。`ref()` は `ImageRef.invoke()`。`Barcode.Linked` の `jan` プロパティは `Jan` 型なので `jan()` で String に。

- [ ] **Step 3: Reader Repository interface**

`application/repository/product/ProductRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products

interface ProductRepository {
    fun findById(id: ProductId): Product

    /** 採用中の商品一覧(空なら空 Products)。 */
    fun listByHousehold(householdId: HouseholdId): Products

    /** アーカイブ済の商品一覧。 */
    fun listArchivedByHousehold(householdId: HouseholdId): Products
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/product/ProductRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId

interface ProductRegisterRepository {
    /** マスタ採用: products + 初回 revision + product_catalog_links を 1 tx で INSERT。 */
    fun registerAdopted(product: Product, householdId: HouseholdId, catalogItemId: CatalogItemId): Product

    /** 世帯独自: products + 初回 revision を INSERT(リンク無し)。 */
    fun registerCustom(product: Product, householdId: HouseholdId): Product

    /** 変更後の Product 全状態を product_revisions に 1 行 append(changeUnit/changeMinimum/changeImage/archive/unarchive)。 */
    fun appendRevision(product: Product): Product

    /** 手動希望フラグを product_wanted_events に append。 */
    fun setWanted(productId: ProductId, wanted: Boolean)
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/product/ProductDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ProductDataSource(
    private val database: Database,
) : ProductRepository {
    override fun findById(id: ProductId): Product =
        transaction(database) {
            productRows { _ -> (ProductsTable.id eq id()) }
                .firstOrNull()
                ?: throw ResourceNotFoundException("product not found: $id")
        }

    override fun listByHousehold(householdId: HouseholdId): Products =
        transaction(database) {
            Products(
                productRows { rev ->
                    (ProductsTable.householdId eq householdId()) and
                        (rev[ProductRevisionsTable.status] eq ProductStatus.採用中)
                },
            )
        }

    override fun listArchivedByHousehold(householdId: HouseholdId): Products =
        transaction(database) {
            Products(
                productRows { rev ->
                    (ProductsTable.householdId eq householdId()) and
                        (rev[ProductRevisionsTable.status] eq ProductStatus.アーカイブ済)
                },
            )
        }

    /**
     * products × 最新 product_revisions を join し、where で絞って Product 群を返す。
     * 最新 revision の alias を predicate に渡す(alias 列で status 等を比較するため)。
     */
    private fun productRows(
        where: (QueryAlias) -> org.jetbrains.exposed.v1.core.Op<Boolean>,
    ): List<Product> {
        val rn =
            rowNumber().over()
                .partitionBy(ProductRevisionsTable.productId)
                .orderBy(ProductRevisionsTable.id to SortOrder.DESC)
        val rnAlias = rn.alias("rn")
        val revSub =
            ProductRevisionsTable
                .select(
                    ProductRevisionsTable.productId,
                    ProductRevisionsTable.unit,
                    ProductRevisionsTable.minimumStock,
                    ProductRevisionsTable.imageRef,
                    ProductRevisionsTable.status,
                    rnAlias,
                ).alias("latest_revision")

        return ProductsTable
            .join(revSub, JoinType.INNER, onColumn = ProductsTable.id, otherColumn = revSub[ProductRevisionsTable.productId])
            .selectAll()
            .where { (revSub[rnAlias] eq 1L) and where(revSub) }
            .map { row ->
                buildProduct(
                    id = ProductId(row[ProductsTable.id]),
                    name = row[ProductsTable.name],
                    jan = row[ProductsTable.jan],
                    unit = row[revSub[ProductRevisionsTable.unit]],
                    minimumStock = row[revSub[ProductRevisionsTable.minimumStock]],
                    imageRef = row[revSub[ProductRevisionsTable.imageRef]],
                    status = row[revSub[ProductRevisionsTable.status]],
                )
            }
    }
}
```

> predicate は最新 revision の alias(`revSub`)を受け取り、status など revision 由来の列は `rev[ProductRevisionsTable.status]` のように alias 越しに比較する(`revSub` は `productRows` 内で生成されるため、呼び出し側ラムダから素の `ProductRevisionsTable.status` を参照できない)。`ProductsTable` 由来の列(`id`/`householdId`)はそのまま参照してよい。

- [ ] **Step 6: Writer DataSource**

`infrastructure/datasource/product/ProductRegisterDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.product

import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductCatalogLinksTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductWantedEventsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ProductsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ProductRegisterDataSource(
    private val database: Database,
) : ProductRegisterRepository {
    override fun registerAdopted(product: Product, householdId: HouseholdId, catalogItemId: CatalogItemId): Product =
        transaction(database) {
            insertProductAndRevision(product, householdId)
            ProductCatalogLinksTable.insert {
                it[productId] = product.id()
                it[ProductCatalogLinksTable.catalogItemId] = catalogItemId()
            }
            product
        }

    override fun registerCustom(product: Product, householdId: HouseholdId): Product =
        transaction(database) {
            insertProductAndRevision(product, householdId)
            product
        }

    override fun appendRevision(product: Product): Product =
        transaction(database) {
            ProductRevisionsTable.insert {
                it[productId] = product.id()
                it[unit] = product.setting.unit()
                it[minimumStock] = product.setting.minimumStock()
                it[imageRef] = product.image.toImageRefColumn()
                it[status] = product.status
            }
            product
        }

    override fun setWanted(productId: ProductId, wanted: Boolean) {
        transaction(database) {
            ProductWantedEventsTable.insert {
                it[ProductWantedEventsTable.productId] = productId()
                it[ProductWantedEventsTable.wanted] = wanted
            }
        }
    }

    private fun insertProductAndRevision(product: Product, householdId: HouseholdId) {
        ProductsTable.insert {
            it[id] = product.id()
            it[ProductsTable.householdId] = householdId()
            it[name] = product.name()
            it[jan] = product.barcode.toJanColumn()
        }
        ProductRevisionsTable.insert {
            it[productId] = product.id()
            it[unit] = product.setting.unit()
            it[minimumStock] = product.setting.minimumStock()
            it[imageRef] = product.image.toImageRefColumn()
            it[status] = product.status
        }
    }
}
```

> `product.name()` = `ProductName.invoke()`、`product.setting.unit()` = `ProductUnit.invoke()`、`product.setting.minimumStock()` = `MinimumStock.invoke()`(public 化済み)。

- [ ] **Step 7: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。`productRows` の status alias 参照を中心に修正。

- [ ] **Step 8: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/Product*.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product
git commit -m "feat(core): product の schema/repository/datasource(revision Window・catalog link・barcode/image 導出)"
```

---

## Task 10: stock context

**Files:**
- Create: `schemas/StockMovementsTable.kt`
- Create: `application/repository/stock/StockRepository.kt` / `StockRegisterRepository.kt`
- Create: `infrastructure/datasource/stock/StockDataSource.kt` / `StockRegisterDataSource.kt` / `StockHydration.kt`

`Stock = Product + StockMovements`。movements は全件返す(`netQuantity` は domain が畳み込む)。`actor` は full `Resident`(actor_resident_id + 最新 display_name のバッチロードで N+1 回避)。`occurred_at` は `instantTz`。`kind` は sealed 判別リテラル。

- [ ] **Step 1: テーブル定義**

`schemas/StockMovementsTable.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption

object StockMovementsTable : HistoryTable("stock_movements") {
    val productId = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val kind = varchar("kind", 20) // REPLENISHMENT / CONSUMPTION / CORRECTION
    val quantity = integer("quantity")
    val occurredAt = instantTz("occurred_at")
    val actorResidentId = reference("actor_resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val note = varchar("note", 255)
    val targetMovementId = long("target_movement_id").references(id, onDelete = ReferenceOption.RESTRICT).nullable() // Correction のみ
    val reason = varchar("reason", 255).nullable() // Correction のみ

    init {
        index(false, productId, id)
    }

    const val KIND_REPLENISHMENT = "REPLENISHMENT"
    const val KIND_CONSUMPTION = "CONSUMPTION"
    const val KIND_CORRECTION = "CORRECTION"
}
```

> self-FK `target_movement_id` → `id`。`references(id, ...)` は同テーブルの `id` 列を参照(`long(...).references(...)`)。compile/migration で self-FK の生成順序を確認(Task 11)。

- [ ] **Step 2: Hydration(movements + actor バッチロード)**

`infrastructure/datasource/stock/StockHydration.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.core.ResultRow

/** stock_movements 行 + 解決済み actor から StockMovement(sealed)を組み立てる。 */
internal fun ResultRow.toStockMovement(actor: Resident): StockMovement {
    val identity = MovementIdentity.Persisted(MovementId(this[StockMovementsTable.id]))
    val quantity = Quantity(this[StockMovementsTable.quantity])
    val occurredAt = OccurredAt(this[StockMovementsTable.occurredAt])
    val note = Note(this[StockMovementsTable.note])
    return when (this[StockMovementsTable.kind]) {
        StockMovementsTable.KIND_REPLENISHMENT ->
            StockMovement.Replenishment(identity, quantity, occurredAt, actor, note)
        StockMovementsTable.KIND_CONSUMPTION ->
            StockMovement.Consumption(identity, quantity, occurredAt, actor, note)
        StockMovementsTable.KIND_CORRECTION ->
            StockMovement.Correction(
                identity, quantity, occurredAt, actor, note,
                target = MovementId(this[StockMovementsTable.targetMovementId]!!),
                reason = Reason(this[StockMovementsTable.reason]!!),
            )
        else -> error("unknown movement kind: ${this[StockMovementsTable.kind]}")
    }
}

/** StockMovement → kind リテラル。 */
internal fun StockMovement.kindColumn(): String =
    when (this) {
        is StockMovement.Replenishment -> StockMovementsTable.KIND_REPLENISHMENT
        is StockMovement.Consumption -> StockMovementsTable.KIND_CONSUMPTION
        is StockMovement.Correction -> StockMovementsTable.KIND_CORRECTION
    }
```

- [ ] **Step 3: Reader Repository interface**

`application/repository/stock/StockRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements

interface StockRepository {
    fun findByProduct(productId: ProductId): Stock

    /** 世帯の在庫一覧(採用中商品。activity 組み立ては P5)。 */
    fun listByHousehold(householdId: HouseholdId): Stocks

    /** 1 商品の movement 全件(history)。 */
    fun historyOf(productId: ProductId): StockMovements
}
```

- [ ] **Step 4: Writer Repository interface**

`application/repository/stock/StockRegisterRepository.kt`:

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

interface StockRegisterRepository {
    /** stock_movements に 1 行 INSERT し、採番された id で Persisted な StockMovement を返す。 */
    fun appendMovement(productId: ProductId, movement: StockMovement): StockMovement
}
```

- [ ] **Step 5: Reader DataSource**

`infrastructure/datasource/stock/StockDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.latestResidentDisplayNames
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentDisplayNamesTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.ResidentsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class StockDataSource(
    private val database: Database,
    private val productDataSource: ProductDataSource,
) : StockRepository {
    override fun findByProduct(productId: ProductId): Stock =
        transaction(database) {
            val product = productDataSource.findById(productId) // 採用中/アーカイブ問わず取得できる前提
            Stock(product, loadMovements(productId))
        }

    override fun listByHousehold(householdId: HouseholdId): Stocks =
        transaction(database) {
            val products = productDataSource.listByHousehold(householdId).list
            Stocks(products.map { p -> Stock(p, loadMovements(p.id)) })
        }

    override fun historyOf(productId: ProductId): StockMovements =
        transaction(database) { loadMovements(productId) }

    /** product の movement 全件を occurred 順(= id 昇順)で返す。actor はバッチ解決。 */
    private fun loadMovements(productId: ProductId): StockMovements {
        val rows =
            StockMovementsTable
                .selectAll()
                .where { StockMovementsTable.productId eq productId() }
                .orderBy(StockMovementsTable.id to SortOrder.ASC)
                .toList()
        if (rows.isEmpty()) return StockMovements(emptyList())

        // actor をバッチ解決(actor_resident_id IN (...) × 最新 display_name)
        val actorIds = rows.map { it[StockMovementsTable.actorResidentId] }.toSet()
        val (dnSub, dnRefs) = latestResidentDisplayNames()
        val actors: Map<Uuid, Resident> =
            ResidentsTable
                .join(dnSub, JoinType.INNER, onColumn = ResidentsTable.id, otherColumn = dnSub[ResidentDisplayNamesTable.residentId])
                .selectAll()
                .where { (ResidentsTable.id inList actorIds) and (dnSub[dnRefs.rn] eq 1L) }
                .associate { row ->
                    val rid = row[ResidentsTable.id]
                    rid to Resident(ResidentId(rid), Profile(DisplayName(row[dnSub[ResidentDisplayNamesTable.displayName]])))
                }

        val movements: List<StockMovement> =
            rows.map { row ->
                val actor = actors.getValue(row[StockMovementsTable.actorResidentId])
                row.toStockMovement(actor)
            }
        return StockMovements(movements)
    }
}
```

> `inList` は `org.jetbrains.exposed.v1.core.inList`。`Set<Uuid>` を渡す。`productDataSource.findById` は採用中のみ取れる実装(Task 9 の `findById` は status フィルタ無しなので OK — 確認)。Stock の product はアーカイブ済も取りたいので `findById`(フィルタ無し)を使う点が正しい。

- [ ] **Step 6: Writer DataSource**

`infrastructure/datasource/stock/StockRegisterDataSource.kt`:

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.stock

import net.brightroom.mindstock.application.repository.stock.StockRegisterRepository
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.infrastructure.datasource.schemas.StockMovementsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class StockRegisterDataSource(
    private val database: Database,
) : StockRegisterRepository {
    override fun appendMovement(productId: ProductId, movement: StockMovement): StockMovement =
        transaction(database) {
            // HistoryTable は素の Table(IdTable ではない)。insertAndGetId は使えないため
            // `insert { } get id` で採番された Long を読み戻す。
            val newId: Long =
                StockMovementsTable.insert {
                    it[StockMovementsTable.productId] = productId()
                    it[kind] = movement.kindColumn()
                    it[quantity] = movement.quantity()
                    it[occurredAt] = movement.occurredAt()
                    it[actorResidentId] = movement.actor.id()
                    it[note] = movement.note()
                    if (movement is StockMovement.Correction) {
                        it[targetMovementId] = movement.target()
                        it[reason] = movement.reason()
                    }
                } get StockMovementsTable.id
            // 採番された id で Persisted に詰め直して返す
            rebindIdentity(movement, MovementIdentity.Persisted(MovementId(newId)))
        }

    /** movement の identity だけ Persisted に差し替えた新インスタンスを返す。 */
    private fun rebindIdentity(movement: StockMovement, identity: MovementIdentity.Persisted): StockMovement =
        when (movement) {
            is StockMovement.Replenishment -> movement.copy(identity = identity)
            is StockMovement.Consumption -> movement.copy(identity = identity)
            is StockMovement.Correction -> movement.copy(identity = identity)
        }
}
```

> **要確認(immutable-construction ルール)**: domain は `copy()` 禁止規約がある([[domain-refactor-policy-2026-05]])。`StockMovement` の sealed variant に `copy` を使うのは infra 側だが、ルール抵触の可能性。回避策: (a) domain に `withIdentity(MovementIdentity)` を生やす(domain 変更・要承認)、または (b) infra で全フィールドを明示してコンストラクタ再生成。**まず (b)** で書く(`StockMovement.Replenishment(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note)` の形)。`copy` は使わない。Step 6 のコードは (b) に直してから commit すること。
> `movement.quantity()` 等は public 化済みアクセサ。`movement.target()` は `MovementId.invoke(): Long`、`movement.reason()` は `Reason.invoke(): String`。`insertAndGetId` は HistoryTable の `id`(Long)を返す(`EntityID<Long>` → `.value`)。

- [ ] **Step 7: Step 6 を copy 不使用形へ修正**

`rebindIdentity` を以下に置き換える(`copy` 禁止規約準拠):

```kotlin
    private fun rebindIdentity(movement: StockMovement, identity: MovementIdentity.Persisted): StockMovement =
        when (movement) {
            is StockMovement.Replenishment ->
                StockMovement.Replenishment(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note)
            is StockMovement.Consumption ->
                StockMovement.Consumption(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note)
            is StockMovement.Correction ->
                StockMovement.Correction(identity, movement.quantity, movement.occurredAt, movement.actor, movement.note, movement.target, movement.reason)
        }
```

- [ ] **Step 8: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 9: Commit**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/StockMovementsTable.kt \
        backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock \
        backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock
git commit -m "feat(core): stock の schema/repository/datasource(movement append・actor バッチロード・occurred_at instantTz)"
```

---

## Task 11: マイグレーション生成 + 目視レビュー

**Files:**
- Generate: `backend/core/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: 全体ビルドで緑を確認**

Run: `./gradlew :backend:core:build`
Expected: BUILD SUCCESSFUL(テストは無いのでコンパイル + 既存 testFixtures のみ)。

- [ ] **Step 2: マイグレーション生成**

Run: `./gradlew :backend:core:generateMigrations`
Expected: postgres:18 testcontainer が起動し、現行スキーマと diff して `backend/core/src/main/resources/db/migration/V1__init.sql` を生成。BUILD SUCCESSFUL。

> 注意: Docker が必要(testcontainers)。失敗時は Docker 起動を確認。`tablesPackage` が `...datasource.schemas` を指していること(Task 2)で、`schemas/` 配下の `object XxxTable` だけが拾われる。

- [ ] **Step 3: 生成 SQL を目視レビュー**

`backend/core/src/main/resources/db/migration/V1__init.sql` を開き、以下を確認:

- 全 13 テーブルが生成されている(residents / resident_auth_identities / resident_display_names / households / household_names / household_membership_events / invitations / invitation_validity_events / catalog_items / products / product_catalog_links / product_revisions / product_wanted_events / stock_movements)。
- FK 生成順序: 参照先が先に作られる(`stock_movements` の self-FK `target_movement_id`、`product_catalog_links` の `products`/`catalog_items` 参照、`invitations` の `households` 参照)。Exposed が ALTER TABLE で FK を後付けするか、CREATE 順を解決しているか確認。
- 型: uuid PK / bigint auto-increment / `timestamp with time zone` / varchar 長 / `integer` / `boolean`。
- UNIQUE: `resident_auth_identities(provider, subject)` / `catalog_items(jan)`。
- INDEX: 各履歴テーブルの `(group_key, id)`。
- nullable: `products.jan` / `product_revisions.image_ref` / `stock_movements.target_movement_id` / `stock_movements.reason`。

不整合(順序エラー・想定外の型)があれば該当 Table 定義を直し、生成 SQL を削除して Step 2 から再生成。

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/main/resources/db/migration/V1__init.sql
git commit -m "feat(core): 初回スキーマ V1__init.sql を生成"
```

---

## Task 12: ルール / メモリの transaction 方針更新

**Files:**
- Modify: `.claude/rules/software-architecture.md`
- Modify: `.claude/rules/rpc-and-transactions.md`
- Modify: memory `krpc-ws-pipeline-gotchas` / `full-replace-2026-06`

spec で本フェーズの方針(DataSource 内 `transaction(database){}` 自前境界・`tx()` ヘルパー / `ExposedTransactionPlugin` 廃止)に矛盾するルール記述を更新する。

- [ ] **Step 1: software-architecture.md の DataSource 節を更新**

該当箇所(現行):

```markdown
### DataSource(infrastructure)

- 実装内では `transaction {}` を書かない(Ktor plugin または `tx()` で境界管理)
```

を次へ:

```markdown
### DataSource(infrastructure)

- 各メソッドは `transaction(database) { }` で自前にトランザクション境界を張る(`tx()` ヘルパー / `ExposedTransactionPlugin` は廃止。取り回しの煩雑さ回避)。`Database` はコンストラクタ注入する
```

- [ ] **Step 2: rpc-and-transactions.md の `tx()` 節を更新**

`### tx() ヘルパー` 節(場所・役割・「DB を触る RPC method は tx() で包む」・supervisorScope の説明)を、次の方針に書き換える:

```markdown
### トランザクション境界(DataSource 自前)

- **トランザクションは DataSource 実装内で `transaction(database) { }` を張る**(`backend/core` の各 DataSource メソッド)。`tx()` ヘルパーと `ExposedTransactionPlugin` は廃止した
- Controller / Service は transaction を意識しない(DataSource が境界を持つ)
- `Database` は起動配線(P5)で生成し DataSource にコンストラクタ注入する
- 旧 `tx()` / plugin 方式は P4 で撤廃(理由: WS upgrade 時 1 回しか張れない plugin の制約と、RPC method ごとに張り直す煩雑さを、DataSource 自前境界で解消)
```

`## How to apply` の `✅ RPC method を tx() で包む` サンプルは、DataSource 自前 transaction を示すサンプルへ差し替える(Controller は `RpcResult` を返すだけ、transaction は DataSource 内)。`## Why` の `tx()` 関連 2 項目も「DataSource 自前境界」に合わせて更新。

- [ ] **Step 3: メモリ更新**

`krpc-ws-pipeline-gotchas`(`tx()`/supervisorScope の落とし穴を記録している箇所)に「P4 で DataSource 自前 `transaction(database){}` に移行し `tx()`/plugin は廃止」を追記。`full-replace-2026-06` の「P4 は spec 改訂済・plan/実装は未着手」を「plan 化済(`docs/superpowers/plans/2026-06-02-p4-backend-infra-migration.md`)」へ更新。

- [ ] **Step 4: 整合確認**

`.claude/rules/` 内に `tx(` / `ExposedTransactionPlugin` の残存が無いか grep:

Run: `grep -rn "tx(\|ExposedTransactionPlugin" .claude/rules/`
Expected: 廃止を説明する文脈以外でヒットしない。

- [ ] **Step 5: Commit**

```bash
git add .claude/rules/software-architecture.md .claude/rules/rpc-and-transactions.md
git commit -m "docs(rules): transaction 境界を DataSource 自前 transaction(database){} に更新(tx()/plugin 廃止)"
```

---

## 要解決(実装中に詰める / P5 送り)

- `household_membership_events.removeMember` の `role` 値(tombstone なので意味なし。current role を踏襲するか既定値か。P5 判断、P4 は既定 `閲覧者`)。
- 採用済み判定(同一世帯同一 JAN → `DuplicateJanException`)用 reader の要否・形(P5 着手時。`catalog_items × product_catalog_links × 自世帯 products` で表現可)。
- `generateMigrations` の self-FK / 中間テーブル FK 順序(Task 11 Step 3 で実地確認)。
- Window join + 複数 alias の Exposed DSL が想定形で compile しない場合の 2 クエリ分解(Task 6 注記。N+1 にしない範囲で)。
- `:backend:core` が `Database` をどう受け取るか(コンストラクタ注入の配線は P5。P4 では DataSource が `private val database: Database` を持つ前提でコンパイルのみ確認)。
- **DataSource 間のネストした transaction**: `StockDataSource.findByProduct`/`listByHousehold` は自分の `transaction(database)` 内で `productDataSource.findById`(別 `transaction(database)`)を呼ぶ。Exposed は同一スレッドで接続を再利用するため動作するが、spec の「各メソッドが自前 transaction」モデルは DataSource 合成を想定していない。P5 配線で「合成時は外側 tx を再利用 / または tx 無しの内部クエリ経路を分ける」かを決める(P4 はコンパイル + 動作前提で進める)。

## 関連

- spec: `docs/superpowers/specs/2026-06-01-p4-backend-infra-migration-design.md`(2026-06-02 改訂)
- 起点設計: `docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/`(00-03)
- rule: `software-architecture.md` / `error-handling.md` / `one-class-per-file.md` / `immutable-construction.md` / `testing.md` / `rpc-and-transactions.md`
- 前段プラン: `docs/superpowers/plans/2026-06-01-p3-rpc-service-and-dto.md`
- 参考: soudai「履歴テーブルから最新レコードを取得する」(Window 関数推奨)
