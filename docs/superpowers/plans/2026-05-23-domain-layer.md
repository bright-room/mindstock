# Plan 3: Domain Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** mindstock のドメイン層(集約・Value Object・例外・Repository ポート)を実装し、`:domain` モジュールを JVM-only から KMP commonMain に格上げする。

**Architecture:** 5 集約(User / Household / CatalogItem / Product / Stock)ごとに Repository ポート(参照系 + 登録系)を切り、Value Object と sealed `DomainException` で不変条件を表現。ID 型は `kotlin.uuid.Uuid` ベースで domain に集約し、`:shared:rpc` から参照する。UUIDv7 生成は `:shared:extensions` に置く。

**Tech Stack:** Kotlin 2.3.21(KMP commonMain + jvm + wasmJs)、kotlinx-serialization、kotlinx-uuid-core、Kotest 6.x、kotlin.uuid.Uuid。

**Reference:** [docs/superpowers/specs/2026-05-23-domain-layer-design.md](../specs/2026-05-23-domain-layer-design.md) と [docs/superpowers/specs/2026-05-23-mindstock-design.md](../specs/2026-05-23-mindstock-design.md)。

---

## File Structure

実装で作成/変更するファイルの全体像:

```
build.gradle / convention
├── gradle/libs.versions.toml                                                       # MODIFY: kotlinx-uuid 追加
├── shared/extensions/build.gradle.kts                                              # MODIFY: kotlinx-uuid 依存追加
├── domain/build.gradle.kts                                                         # REWRITE: JVM → KMP
└── shared/rpc/build.gradle.kts                                                     # MODIFY: domain 依存追加

shared/extensions
└── src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7.kt # CREATE
└── src/commonTest/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7Test.kt # CREATE

domain (源泉ソースを commonMain に移動 + 大量新規)
├── src/commonMain/kotlin/net/brightroom/mindstock/domain/
│   ├── exception/DomainException.kt                                                # CREATE
│   ├── model/
│   │   ├── user/{User, UserId, UserDisplayName, UserDisplayNameId, DisplayName, ZitadelSub}.kt
│   │   ├── household/{Household, HouseholdId, HouseholdMembership, HouseholdMembershipId,
│   │   │              HouseholdMembershipRevocation, HouseholdMembershipRevocationId,
│   │   │              HouseholdMemberRole}.kt                                      # MemberRole は MOVE
│   │   ├── catalog/{CatalogItem, CatalogItemId, CatalogItemRevision,
│   │   │             CatalogItemRevisionId, CatalogItemName, CatalogItemUnit}.kt
│   │   ├── product/{Product, ProductId, ProductMinimumStock, ProductMinimumStockId,
│   │   │             ProductArchive, ProductArchiveId, MinimumStock}.kt
│   │   └── stock/{StockReplenishment, StockReplenishmentId, StockConsumption,
│   │              StockConsumptionId, StockReplenishmentCorrection,
│   │              StockReplenishmentCorrectionId, StockConsumptionCorrection,
│   │              StockConsumptionCorrectionId, Quantity, OccurredAt,
│   │              Note, Reason}.kt
│   └── repository/
│       ├── user/{UserRepository, UserRegisterRepository}.kt
│       ├── household/{HouseholdRepository, HouseholdRegisterRepository}.kt
│       ├── catalog/{CatalogItemRepository, CatalogItemRegisterRepository}.kt
│       ├── product/{ProductRepository, ProductRegisterRepository}.kt
│       └── stock/{StockRepository, StockRegisterRepository}.kt
└── src/commonTest/kotlin/net/brightroom/mindstock/domain/
    ├── exception/DomainExceptionTest.kt
    └── model/<aggregate>/<VO>Test.kt + ProductGuardTest.kt 等

domain/src/main/kotlin/net/brightroom/mindstock/domain/Placeholder.kt                # DELETE
```

---

## Phase A: ビルド基盤(KMP 化)

### Task 1: kotlinx-uuid を Version Catalog に追加

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: `[versions]` セクションに行を追加**

`gradle/libs.versions.toml` の `[versions]` ブロック内、`kotlin = "2.3.21"` の直後あたりに以下を追加(アルファベット順は問わない):

```toml
kotlinx-uuid = "0.1.0"
```

- [ ] **Step 2: `[libraries]` セクションに行を追加**

`gradle/libs.versions.toml` の `[libraries]` ブロック内、`kotlinx-datetime` の行の近くに追加:

```toml
kotlinx-uuid-core = { module = "app.softwork:kotlinx-uuid-core", version.ref = "kotlinx-uuid" }
```

- [ ] **Step 3: `./gradlew :shared:extensions:dependencies` が通ることを確認**

Run: `./gradlew :shared:extensions:dependencies --no-daemon -q | head -5`
Expected: `--- Gradle ---` 等のエラーが出ない(まだ extensions の build には載せていない状態で OK)

- [ ] **Step 4: コミット**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add kotlinx-uuid-core to version catalog"
```

---

### Task 2: `:shared:extensions` に UuidV7 ユーティリティを追加

**Files:**
- Modify: `shared/extensions/build.gradle.kts`
- Create: `shared/extensions/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7.kt`
- Create: `shared/extensions/src/commonTest/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7Test.kt`

- [ ] **Step 1: `shared/extensions/build.gradle.kts` の commonMain に依存追加 + commonTest に Kotest 追加**

既存:

```kotlin
commonMain.dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
commonTest.dependencies {}
```

を以下に変更:

```kotlin
commonMain.dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.uuid.core)
}
commonTest.dependencies {
    implementation(libs.kotest.assertions.core)
}

jvmTest.dependencies {
    implementation(libs.kotest.runner.junit5)
}
```

加えて、`tasks.withType<Test>().configureEach { useJUnitPlatform() }` を `kotlin { ... }` ブロックの後に追加(`kotlin-jvm` convention にはあるが KMP の jvm() ターゲットでは個別に必要):

```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

- [ ] **Step 2: 失敗するテストを書く**

`shared/extensions/src/commonTest/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7Test.kt`:

```kotlin
package net.brightroom.mindstock.extensions.kotlin.uuid

import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UuidV7Test {
    @Test
    fun `newUuidV7 returns distinct ids on successive calls`() {
        val a = newUuidV7()
        val b = newUuidV7()
        a shouldNotBe b
    }

    @Test
    fun `newUuidV7 returns a version 7 uuid`() {
        val uuid = newUuidV7()
        val versionNibble = uuid.toString()[14]
        versionNibble.toString() shouldBe "7"
    }
}
```

(`shouldBe` の import: `import io.kotest.matchers.shouldBe`)

- [ ] **Step 3: テストを実行して失敗を確認**

Run: `./gradlew :shared:extensions:jvmTest --tests "*UuidV7Test*" --no-daemon`
Expected: コンパイル失敗(`newUuidV7` 未定義)

- [ ] **Step 4: 実装を書く**

`shared/extensions/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7.kt`:

```kotlin
package net.brightroom.mindstock.extensions.kotlin.uuid

import app.softwork.uuid.UuidV7
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UUID v7(時系列順に並ぶ128-bit identifier)を新規生成する。
 *
 * 集約ルートの ID 生成に使う。ミリ秒精度の time-based prefix を持つため、
 * 同じ集約ルートの ID は `ORDER BY id` で生成順に近い順序で並ぶ。
 */
@OptIn(ExperimentalUuidApi::class)
public fun newUuidV7(): Uuid = UuidV7.generate()
```

備考: `app.softwork:kotlinx-uuid-core` の API が `UuidV7.generate()` で `kotlin.uuid.Uuid` を返す前提。バージョン更新で API 名が変わっていたら(`UuidV7.next()` 等)、それに合わせて修正する。リリースノートで API 確認: <https://github.com/hfhbd/kotlinx-uuid>。

- [ ] **Step 5: テストを実行して成功を確認**

Run: `./gradlew :shared:extensions:jvmTest --tests "*UuidV7Test*" --no-daemon`
Expected: PASS(2 tests)

- [ ] **Step 6: コミット**

```bash
git add shared/extensions/build.gradle.kts shared/extensions/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7.kt shared/extensions/src/commonTest/kotlin/net/brightroom/mindstock/extensions/kotlin/uuid/UuidV7Test.kt
git commit -m "feat(shared:extensions): add newUuidV7() utility"
```

---

### Task 3: `:domain` モジュールを KMP に書き換え

**Files:**
- Rewrite: `domain/build.gradle.kts`
- Move: `domain/src/main/kotlin/.../HouseholdMemberRole.kt` → `domain/src/commonMain/kotlin/.../HouseholdMemberRole.kt`
- Delete: `domain/src/main/kotlin/net/brightroom/mindstock/domain/Placeholder.kt`

- [ ] **Step 1: 既存の domain/src/main/kotlin/ をすべて commonMain に移動**

```bash
mkdir -p domain/src/commonMain/kotlin
git mv domain/src/main/kotlin/net domain/src/commonMain/kotlin/net
rm -rf domain/src/main
```

- [ ] **Step 2: Placeholder.kt を削除**

```bash
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/Placeholder.kt
```

- [ ] **Step 3: `domain/build.gradle.kts` を KMP convention に書き換え**

`domain/build.gradle.kts` の中身を以下に置き換え:

```kotlin
plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.extensions)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

- [ ] **Step 4: コンパイル確認(common + jvm)**

Run: `./gradlew :domain:compileKotlinJvm :domain:compileKotlinWasmJs --no-daemon`
Expected: BUILD SUCCESSFUL

`HouseholdMemberRole` が common にあるので、JVM/wasmJs 両方で通る。

- [ ] **Step 5: 依存元モジュール(`:backend:infrastructure:schemas`)が壊れていないことを確認**

Run: `./gradlew :backend:infrastructure:schemas:compileKotlin --no-daemon`
Expected: BUILD SUCCESSFUL(JVM artifact 経由で `HouseholdMemberRole` を引ける)

- [ ] **Step 6: コミット**

```bash
git add domain/build.gradle.kts domain/src/
git commit -m "build(domain): migrate to Kotlin Multiplatform (commonMain + jvm + wasmJs)"
```

---

### Task 4: `:shared:rpc` に `:domain` 依存を追加

**Files:**
- Modify: `shared/rpc/build.gradle.kts`

- [ ] **Step 1: `commonMain.dependencies` に `projects.domain` 追加**

`shared/rpc/build.gradle.kts` の `commonMain { dependencies { ... } }` ブロックの先頭に追加:

```kotlin
implementation(projects.domain)
```

- [ ] **Step 2: 全モジュールのコンパイル確認**

Run: `./gradlew compileKotlinJvm compileKotlinWasmJs --no-daemon` 

(全 KMP モジュールの jvm + wasmJs コンパイルを通す)

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add shared/rpc/build.gradle.kts
git commit -m "build(shared:rpc): depend on :domain for typed ID source"
```

---

## Phase B: DomainException

### Task 5: DomainException sealed class の最小スタブ

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt`

このタスクでは VO の検証で使うサブクラスを最小限で導入する。後続タスクで VO ごとに必要なサブクラスを追加する。

- [ ] **Step 1: 失敗するテストを書く**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.exception

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class DomainExceptionTest {
    @Test
    fun `InvalidQuantity carries value in message`() {
        val ex = DomainException.InvalidQuantity(-3)
        ex.shouldBeInstanceOf<DomainException>()
        ex.value shouldBe -3
        ex.message!! shouldContain "-3"
    }
}
```

- [ ] **Step 2: テストを実行して失敗確認**

Run: `./gradlew :domain:jvmTest --tests "*DomainExceptionTest*" --no-daemon`
Expected: コンパイル失敗(クラス未定義)

- [ ] **Step 3: 実装**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt`:

```kotlin
package net.brightroom.mindstock.domain.exception

/**
 * Domain layer の不変条件違反を表す sealed 例外。
 *
 * Value Object のコンストラクタや Aggregate のガードメソッドから throw される。
 * Application 層(UseCase)で catch して、必要に応じて RPC 層の
 * InventoryException に翻訳する。
 */
public sealed class DomainException(message: String) : RuntimeException(message) {

    public class InvalidQuantity(public val value: Int) :
        DomainException("quantity must be > 0, got $value")
}
```

- [ ] **Step 4: テストを実行して成功確認**

Run: `./gradlew :domain:jvmTest --tests "*DomainExceptionTest*" --no-daemon`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/exception/DomainExceptionTest.kt
git commit -m "feat(domain): add sealed DomainException with InvalidQuantity"
```

---

## Phase C: Value Objects

各 VO タスクは「失敗テスト → 実装 → グリーン → コミット」の TDD ループを 1 周する。すべての VO は以下のテンプレートに従う:

- `@Serializable @JvmInline value class`
- バッキングフィールド `private val value`
- `init {}` で検証、違反時に `DomainException.<サブクラス>` を throw
- `override fun toString(): String = value.toString()`
- `internal operator fun invoke(): <T> = value`(必要に応じて)

### Task 6: Quantity VO(VO テンプレートの代表)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt`

このタスクで `@Serializable` + `private val` + `value class` + `init {}` のパターンが Kotest + kotlinx-serialization で動くことを確認する。

- [ ] **Step 1: 失敗するテストを書く**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class QuantityTest {
    @Test
    fun `accepts positive integer`() {
        Quantity(1).toString() shouldBe "1"
        Quantity(42).toString() shouldBe "42"
    }

    @Test
    fun `rejects zero`() {
        shouldThrow<DomainException.InvalidQuantity> { Quantity(0) }
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<DomainException.InvalidQuantity> { Quantity(-1) }
    }

    @Test
    fun `serializes to plain integer JSON`() {
        Json.encodeToString(Quantity.serializer(), Quantity(5)) shouldBe "5"
    }

    @Test
    fun `deserializes from plain integer JSON`() {
        Json.decodeFromString(Quantity.serializer(), "5") shouldBe Quantity(5)
    }
}
```

- [ ] **Step 2: テストを実行して失敗確認**

Run: `./gradlew :domain:jvmTest --tests "*QuantityTest*" --no-daemon`
Expected: コンパイル失敗(`Quantity` 未定義)

- [ ] **Step 3: 実装**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * 在庫イベント(補充・消費・訂正)の数量。常に正の整数。
 */
@Serializable
@JvmInline
public value class Quantity(private val value: Int) {
    init {
        if (value <= 0) throw DomainException.InvalidQuantity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Int = value
}
```

- [ ] **Step 4: テスト成功確認**

Run: `./gradlew :domain:jvmTest --tests "*QuantityTest*" --no-daemon`
Expected: PASS(5 tests)

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt
git commit -m "feat(domain): add Quantity value object"
```

---

### Task 7: MinimumStock VO

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt`(InvalidMinimumStock サブクラス追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStock.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStockTest.kt`

- [ ] **Step 1: テストを書く**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStockTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun `accepts zero`() {
        MinimumStock(0).toString() shouldBe "0"
    }

    @Test
    fun `accepts positive`() {
        MinimumStock(10).toString() shouldBe "10"
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<DomainException.InvalidMinimumStock> { MinimumStock(-1) }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt` の `InvalidQuantity` 行の直後に:

```kotlin
    public class InvalidMinimumStock(public val value: Int) :
        DomainException("minimum_stock must be >= 0, got $value")
```

- [ ] **Step 3: 実装**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStock.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * 商品の最低在庫(これを下回ると買い物リストに載る)。非負整数。
 */
@Serializable
@JvmInline
public value class MinimumStock(private val value: Int) {
    init {
        if (value < 0) throw DomainException.InvalidMinimumStock(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Int = value
}
```

- [ ] **Step 4: テスト成功確認 + コミット**

Run: `./gradlew :domain:jvmTest --tests "*MinimumStockTest*" --no-daemon`
Expected: PASS

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStock.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/MinimumStockTest.kt
git commit -m "feat(domain): add MinimumStock value object"
```

---

### Task 8: OccurredAt VO

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt`(OccurredAtInFuture 追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAt.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAtTest.kt`

`OccurredAt` は他 VO と違い、**second コンストラクタ引数 `now: Instant` を受け取って検証**する。これは VO の純粋性を保つため(Clock を外から渡す)。

- [ ] **Step 1: テストを書く**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAtTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class OccurredAtTest {
    private val now = Instant.parse("2026-05-23T10:00:00Z")

    @Test
    fun `accepts past`() {
        val past = Instant.parse("2026-05-22T10:00:00Z")
        OccurredAt(past, now).toString() shouldBe past.toString()
    }

    @Test
    fun `accepts now exactly`() {
        OccurredAt(now, now).toString() shouldBe now.toString()
    }

    @Test
    fun `rejects future`() {
        val future = Instant.parse("2026-05-24T10:00:00Z")
        shouldThrow<DomainException.OccurredAtInFuture> { OccurredAt(future, now) }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

```kotlin
    public class OccurredAtInFuture(
        public val value: kotlinx.datetime.Instant,
        public val now: kotlinx.datetime.Instant,
    ) : DomainException("occurredAt $value must be <= now $now")
```

(import を整理するなら top に `import kotlinx.datetime.Instant` を追加して FQN を `Instant` 短縮可)

- [ ] **Step 3: 実装**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAt.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * 在庫イベント発生時刻。未来日は許容しない(now を比較対象として渡す)。
 *
 * `value > now` の場合 [DomainException.OccurredAtInFuture] を throw。
 * `value <= now` のときに有効。
 */
@Serializable
public class OccurredAt(private val value: Instant) {
    public constructor(value: Instant, now: Instant) : this(value) {
        if (value > now) throw DomainException.OccurredAtInFuture(value, now)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Instant = value
}
```

**注意**: `@JvmInline value class` は `init {}` で外部依存(`now`)を参照できないため、`OccurredAt` だけは通常の `class` にし、`(value, now)` の secondary constructor で検証する。`(value)` 単独コンストラクタはシリアライズ復元用(検証なし)に残す。VO のシリアライズ要件と検証要件のトレードオフ。

- [ ] **Step 4: テスト成功確認 + コミット**

Run: `./gradlew :domain:jvmTest --tests "*OccurredAtTest*" --no-daemon`
Expected: PASS

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAt.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAtTest.kt
git commit -m "feat(domain): add OccurredAt value object with now-based validation"
```

---

### Task 9: DisplayName VO

**Files:**
- Modify: `DomainException.kt`(DisplayNameTooLong / DisplayNameBlank 追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/DisplayName.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/DisplayNameTest.kt`

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class DisplayNameTest {
    @Test
    fun `accepts non-blank within 100 chars`() {
        DisplayName("Alice").toString() shouldBe "Alice"
        DisplayName("x".repeat(100)).toString().length shouldBe 100
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.DisplayNameBlank> { DisplayName("") }
        shouldThrow<DomainException.DisplayNameBlank> { DisplayName("   ") }
    }

    @Test
    fun `rejects over 100 chars`() {
        shouldThrow<DomainException.DisplayNameTooLong> { DisplayName("x".repeat(101)) }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

```kotlin
    public class DisplayNameBlank : DomainException("display name must not be blank")
    public class DisplayNameTooLong(public val length: Int) :
        DomainException("display name length $length > 100")
```

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * ユーザーの表示名。空文字禁止、最大 100 文字。
 */
@Serializable
@JvmInline
public value class DisplayName(private val value: String) {
    init {
        if (value.isBlank()) throw DomainException.DisplayNameBlank()
        if (value.length > 100) throw DomainException.DisplayNameTooLong(value.length)
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 4: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*DisplayNameTest*" --no-daemon
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/DisplayName.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/DisplayNameTest.kt
git commit -m "feat(domain): add DisplayName value object (blank-reject, max 100)"
```

---

### Task 10: ZitadelSub VO

**Files:**
- Modify: `DomainException.kt`(ZitadelSubBlank 追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSub.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSubTest.kt`

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class ZitadelSubTest {
    @Test
    fun `accepts non-blank`() {
        ZitadelSub("abc-123").toString() shouldBe "abc-123"
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.ZitadelSubBlank> { ZitadelSub("") }
        shouldThrow<DomainException.ZitadelSubBlank> { ZitadelSub("   ") }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

```kotlin
    public class ZitadelSubBlank : DomainException("zitadel sub must not be blank")
```

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * Zitadel が発行するユーザーのサブジェクト識別子。空文字禁止。
 */
@Serializable
@JvmInline
public value class ZitadelSub(private val value: String) {
    init {
        if (value.isBlank()) throw DomainException.ZitadelSubBlank()
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 4: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*ZitadelSubTest*" --no-daemon
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DomainException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSub.kt \
        domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/user/ZitadelSubTest.kt
git commit -m "feat(domain): add ZitadelSub value object"
```

---

### Task 11: CatalogItemName VO

**Files:**
- Modify: `DomainException.kt`(CatalogItemNameTooLong / CatalogItemNameBlank 追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemName.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemNameTest.kt`

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun `accepts non-blank within 200 chars`() {
        CatalogItemName("キレイキレイ").toString() shouldBe "キレイキレイ"
        CatalogItemName("x".repeat(200)).toString().length shouldBe 200
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.CatalogItemNameBlank> { CatalogItemName("") }
        shouldThrow<DomainException.CatalogItemNameBlank> { CatalogItemName("  ") }
    }

    @Test
    fun `rejects over 200 chars`() {
        shouldThrow<DomainException.CatalogItemNameTooLong> { CatalogItemName("x".repeat(201)) }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

```kotlin
    public class CatalogItemNameBlank : DomainException("catalog item name must not be blank")
    public class CatalogItemNameTooLong(public val length: Int) :
        DomainException("catalog item name length $length > 200")
```

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * カタログ商品の名前。空文字禁止、最大 200 文字。
 */
@Serializable
@JvmInline
public value class CatalogItemName(private val value: String) {
    init {
        if (value.isBlank()) throw DomainException.CatalogItemNameBlank()
        if (value.length > 200) throw DomainException.CatalogItemNameTooLong(value.length)
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 4: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*CatalogItemNameTest*" --no-daemon
git add ... # 関連 3 ファイル
git commit -m "feat(domain): add CatalogItemName value object"
```

---

### Task 12: CatalogItemUnit VO

**Files:**
- Modify: `DomainException.kt`(CatalogItemUnitTooLong / CatalogItemUnitBlank 追加)
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemUnit.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemUnitTest.kt`

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class CatalogItemUnitTest {
    @Test
    fun `accepts non-blank within 10 chars`() {
        CatalogItemUnit("個").toString() shouldBe "個"
        CatalogItemUnit("ml").toString() shouldBe "ml"
        CatalogItemUnit("x".repeat(10)).toString().length shouldBe 10
    }

    @Test
    fun `rejects blank`() {
        shouldThrow<DomainException.CatalogItemUnitBlank> { CatalogItemUnit("") }
    }

    @Test
    fun `rejects over 10 chars`() {
        shouldThrow<DomainException.CatalogItemUnitTooLong> { CatalogItemUnit("x".repeat(11)) }
    }
}
```

- [ ] **Step 2: DomainException にサブクラス追加**

```kotlin
    public class CatalogItemUnitBlank : DomainException("catalog item unit must not be blank")
    public class CatalogItemUnitTooLong(public val length: Int) :
        DomainException("catalog item unit length $length > 10")
```

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * カタログ商品の単位。空文字禁止、最大 10 文字。
 */
@Serializable
@JvmInline
public value class CatalogItemUnit(private val value: String) {
    init {
        if (value.isBlank()) throw DomainException.CatalogItemUnitBlank()
        if (value.length > 10) throw DomainException.CatalogItemUnitTooLong(value.length)
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 4: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*CatalogItemUnitTest*" --no-daemon
git commit -m "feat(domain): add CatalogItemUnit value object"
```

---

### Task 13: Note VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Note.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/NoteTest.kt`

`Note` は空文字許容(検証なし)。`DomainException` のサブクラス追加は不要。

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteTest {
    @Test
    fun `accepts empty`() {
        Note("").toString() shouldBe ""
    }

    @Test
    fun `accepts arbitrary text`() {
        Note("Costco で 3 個入り").toString() shouldBe "Costco で 3 個入り"
    }
}
```

- [ ] **Step 2: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

/**
 * 在庫イベントに付与する自由記述。空文字許容。
 */
@Serializable
@JvmInline
public value class Note(private val value: String) {
    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 3: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*NoteTest*" --no-daemon
git commit -m "feat(domain): add Note value object"
```

---

### Task 14: Reason VO

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Reason.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/ReasonTest.kt`

Note と同じ構造(空文字許容)。

- [ ] **Step 1: テスト**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReasonTest {
    @Test
    fun `accepts empty`() {
        Reason("").toString() shouldBe ""
    }

    @Test
    fun `accepts arbitrary text`() {
        Reason("数え間違い").toString() shouldBe "数え間違い"
    }
}
```

- [ ] **Step 2: 実装**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

/**
 * 訂正イベントに付与する理由文。空文字許容(必須化は UseCase/UI で運用)。
 */
@Serializable
@JvmInline
public value class Reason(private val value: String) {
    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
```

- [ ] **Step 3: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*ReasonTest*" --no-daemon
git commit -m "feat(domain): add Reason value object"
```

---

## Phase D: ID Types

ID 型は VO と同じパターンだが、検証は最小限。UUID 系は検証なし、Long 系は負値拒否。

### Task 15: UUID 系 ID(4 種)

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/ProductId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserId.kt`
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemIdTest.kt`(代表テスト 1 種のみ — 全 ID で同パターンのため)

- [ ] **Step 1: テスト(代表として CatalogItemId のみ)**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemIdTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogItemIdTest {
    @Test
    fun `wraps a Uuid and stringifies to canonical form`() {
        val uuid = Uuid.parse("0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f")
        val id = CatalogItemId(uuid)
        id.toString() shouldBe "0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f"
    }

    @Test
    fun `equality follows underlying Uuid`() {
        val uuid = Uuid.parse("0190a8e9-7f3c-7c1e-b3e0-1a2b3c4d5e6f")
        CatalogItemId(uuid) shouldBe CatalogItemId(uuid)
        CatalogItemId(uuid) shouldNotBe CatalogItemId(Uuid.parse("0190a8ea-0000-7000-8000-000000000000"))
    }
}
```

- [ ] **Step 2: 実装(4 ファイル同形)**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItemId.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class CatalogItemId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
```

`ProductId.kt`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/`):

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class ProductId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
```

`HouseholdId.kt`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/`):

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class HouseholdId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
```

`UserId.kt`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/`):

```kotlin
package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class UserId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
```

- [ ] **Step 3: テスト成功 + コミット**

```bash
./gradlew :domain:jvmTest --tests "*CatalogItemIdTest*" --no-daemon
git commit -m "feat(domain): add UUID-rooted id types (CatalogItemId, ProductId, HouseholdId, UserId)"
```

---

### Task 16: Long 系 ID(10 種)+ InvalidIdentity 例外

**Files:**
- Modify: `DomainException.kt`(InvalidIdentity 追加)
- Create: 10 個の Long 系 ID ファイル
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentIdTest.kt`(代表テスト 1 種)

対象 ID:

| ファイル | パッケージ |
|---|---|
| `UserDisplayNameId.kt` | `model/user/` |
| `HouseholdMembershipId.kt` | `model/household/` |
| `HouseholdMembershipRevocationId.kt` | `model/household/` |
| `CatalogItemRevisionId.kt` | `model/catalog/` |
| `ProductMinimumStockId.kt` | `model/product/` |
| `ProductArchiveId.kt` | `model/product/` |
| `StockReplenishmentId.kt` | `model/stock/` |
| `StockConsumptionId.kt` | `model/stock/` |
| `StockReplenishmentCorrectionId.kt` | `model/stock/` |
| `StockConsumptionCorrectionId.kt` | `model/stock/` |

- [ ] **Step 1: DomainException に追加**

```kotlin
    public class InvalidIdentity(public val value: Long) :
        DomainException("identity must be >= 0, got $value")
```

- [ ] **Step 2: 代表テスト**

`domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/StockReplenishmentIdTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.test.Test

class StockReplenishmentIdTest {
    @Test
    fun `accepts non-negative`() {
        StockReplenishmentId(0).toString() shouldBe "0"
        StockReplenishmentId(42).toString() shouldBe "42"
    }

    @Test
    fun `rejects negative`() {
        shouldThrow<DomainException.InvalidIdentity> { StockReplenishmentId(-1) }
    }
}
```

- [ ] **Step 3: 実装(10 ファイル、テンプレートに従う)**

各ファイルの中身は以下のテンプレート(`StockReplenishmentId` を見本に、クラス名・パッケージのみ差し替え):

```kotlin
package net.brightroom.mindstock.domain.model.stock  // ←各 ID のパッケージに

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException

@Serializable
@JvmInline
public value class StockReplenishmentId(private val value: Long) {  // ←クラス名
    init {
        if (value < 0) throw DomainException.InvalidIdentity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Long = value
}
```

10 個分繰り返し:

| クラス名 | パッケージ |
|---|---|
| `UserDisplayNameId` | `net.brightroom.mindstock.domain.model.user` |
| `HouseholdMembershipId` | `net.brightroom.mindstock.domain.model.household` |
| `HouseholdMembershipRevocationId` | `net.brightroom.mindstock.domain.model.household` |
| `CatalogItemRevisionId` | `net.brightroom.mindstock.domain.model.catalog` |
| `ProductMinimumStockId` | `net.brightroom.mindstock.domain.model.product` |
| `ProductArchiveId` | `net.brightroom.mindstock.domain.model.product` |
| `StockReplenishmentId` | `net.brightroom.mindstock.domain.model.stock` |
| `StockConsumptionId` | `net.brightroom.mindstock.domain.model.stock` |
| `StockReplenishmentCorrectionId` | `net.brightroom.mindstock.domain.model.stock` |
| `StockConsumptionCorrectionId` | `net.brightroom.mindstock.domain.model.stock` |

- [ ] **Step 4: テスト + コミット**

```bash
./gradlew :domain:jvmTest --tests "*StockReplenishmentIdTest*" --no-daemon
git add ...
git commit -m "feat(domain): add Long-rooted id types and InvalidIdentity exception"
```

---

## Phase E: 集約と事実オブジェクト

### Task 17: User 集約 + UserDisplayName 事実

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayName.kt`

集約・事実クラスは検証なしの単純なデータ保持クラス(検証は VO 側で完結)。テストはコンストラクタが値を保持することの sanity check のみ。今フェーズはテスト省略で良い(集約のロジック追加は Phase F)。

- [ ] **Step 1: User 実装**

```kotlin
package net.brightroom.mindstock.domain.model.user

import kotlinx.datetime.Instant

/**
 * アプリ内ユーザー集約。`zitadelSub` で外部認証(Zitadel)と紐づく。
 */
public class User(
    public val id: UserId,
    internal val zitadelSub: ZitadelSub,
    internal val createdAt: Instant,
)
```

- [ ] **Step 2: UserDisplayName 実装**

```kotlin
package net.brightroom.mindstock.domain.model.user

import kotlinx.datetime.Instant

/**
 * User の表示名変更履歴の 1 行。
 */
public class UserDisplayName(
    public val id: UserDisplayNameId,
    internal val userId: UserId,
    internal val displayName: DisplayName,
    internal val createdAt: Instant,
)
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/User.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/user/UserDisplayName.kt
git commit -m "feat(domain): add User aggregate and UserDisplayName fact"
```

---

### Task 18: Household 集約 + Membership + Revocation 事実

**Files:**
- Create: `Household.kt`, `HouseholdMembership.kt`, `HouseholdMembershipRevocation.kt`(`model/household/`)

- [ ] **Step 1: 実装**

`Household.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

public class Household(
    public val id: HouseholdId,
    internal val createdAt: Instant,
)
```

`HouseholdMembership.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class HouseholdMembership(
    public val id: HouseholdMembershipId,
    internal val householdId: HouseholdId,
    internal val userId: UserId,
    internal val role: HouseholdMemberRole,
    internal val createdAt: Instant,
)
```

`HouseholdMembershipRevocation.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.datetime.Instant

public class HouseholdMembershipRevocation(
    public val id: HouseholdMembershipRevocationId,
    internal val membershipId: HouseholdMembershipId,
    internal val createdAt: Instant,
)
```

- [ ] **Step 2: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add Household aggregate with membership facts"
```

---

### Task 19: CatalogItem 集約 + Revision 事実

**Files:**
- Create: `CatalogItem.kt`, `CatalogItemRevision.kt`(`model/catalog/`)

- [ ] **Step 1: CatalogItem 実装**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class CatalogItem(
    public val id: CatalogItemId,
    internal val createdBy: UserId,
    internal val createdAt: Instant,
    /** 最新リビジョンの名前と単位。新規登録直後は最初のリビジョンの値が入る。 */
    internal val latestName: CatalogItemName,
    internal val latestUnit: CatalogItemUnit,
)
```

- [ ] **Step 2: CatalogItemRevision 実装**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class CatalogItemRevision(
    public val id: CatalogItemRevisionId,
    internal val catalogItemId: CatalogItemId,
    internal val name: CatalogItemName,
    internal val unit: CatalogItemUnit,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add CatalogItem aggregate with revision fact"
```

---

### Task 20: Product 集約 + ガード + ProductGuardTest

**Files:**
- Modify: `DomainException.kt`(ProductArchived / ProductNotInHousehold 追加)
- Create: `Product.kt`(`model/product/`)
- Create: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/product/ProductGuardTest.kt`

- [ ] **Step 1: DomainException にサブクラス追加**

`DomainException.kt` の末尾(}の直前)に追加:

```kotlin
    public class ProductArchived(public val productId: net.brightroom.mindstock.domain.model.product.ProductId) :
        DomainException("product $productId is archived")

    public class ProductNotInHousehold(
        public val productId: net.brightroom.mindstock.domain.model.product.ProductId,
        public val householdId: net.brightroom.mindstock.domain.model.household.HouseholdId,
    ) : DomainException("product $productId does not belong to household $householdId")
```

(FQN を避けたい場合は import を整理してよい)

- [ ] **Step 2: ProductGuardTest を書く(失敗)**

```kotlin
package net.brightroom.mindstock.domain.model.product

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.extensions.kotlin.uuid.newUuidV7
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ProductGuardTest {
    private val householdA = HouseholdId(newUuidV7())
    private val householdB = HouseholdId(newUuidV7())
    private val catalogItemId =
        net.brightroom.mindstock.domain.model.catalog.CatalogItemId(newUuidV7())
    private val now = Instant.parse("2026-05-23T10:00:00Z")

    @Test
    fun `ensureNotArchived passes when not archived`() {
        val product = Product(
            id = ProductId(newUuidV7()),
            householdId = householdA,
            catalogItemId = catalogItemId,
            createdAt = now,
            latestMinimumStock = null,
            archivedAt = null,
        )
        shouldNotThrow<DomainException.ProductArchived> { product.ensureNotArchived() }
    }

    @Test
    fun `ensureNotArchived throws when archived`() {
        val product = Product(
            id = ProductId(newUuidV7()),
            householdId = householdA,
            catalogItemId = catalogItemId,
            createdAt = now,
            latestMinimumStock = null,
            archivedAt = now,
        )
        shouldThrow<DomainException.ProductArchived> { product.ensureNotArchived() }
    }

    @Test
    fun `ensureBelongsTo passes when household matches`() {
        val product = Product(
            id = ProductId(newUuidV7()),
            householdId = householdA,
            catalogItemId = catalogItemId,
            createdAt = now,
            latestMinimumStock = null,
            archivedAt = null,
        )
        shouldNotThrow<DomainException.ProductNotInHousehold> { product.ensureBelongsTo(householdA) }
    }

    @Test
    fun `ensureBelongsTo throws when household differs`() {
        val product = Product(
            id = ProductId(newUuidV7()),
            householdId = householdA,
            catalogItemId = catalogItemId,
            createdAt = now,
            latestMinimumStock = null,
            archivedAt = null,
        )
        shouldThrow<DomainException.ProductNotInHousehold> { product.ensureBelongsTo(householdB) }
    }
}
```

- [ ] **Step 3: Product 実装**

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.exception.DomainException
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId

/**
 * 世帯固有の商品インスタンス。CatalogItem を世帯で「採用」したもの。
 *
 * 最低在庫値(`latestMinimumStock`)とアーカイブ状態(`archivedAt`)を集約スナップショットとして持つ。
 */
public class Product(
    public val id: ProductId,
    internal val householdId: HouseholdId,
    internal val catalogItemId: CatalogItemId,
    internal val createdAt: Instant,
    internal val latestMinimumStock: MinimumStock?,
    internal val archivedAt: Instant?,
) {
    internal val isArchived: Boolean get() = archivedAt != null

    public fun ensureNotArchived() {
        if (isArchived) throw DomainException.ProductArchived(id)
    }

    public fun ensureBelongsTo(householdId: HouseholdId) {
        if (this.householdId != householdId) {
            throw DomainException.ProductNotInHousehold(id, householdId)
        }
    }
}
```

- [ ] **Step 4: テスト成功 + コミット**

```bash
./gradlew :domain:jvmTest --tests "*ProductGuardTest*" --no-daemon
git add ...
git commit -m "feat(domain): add Product aggregate with ensureNotArchived/ensureBelongsTo guards"
```

---

### Task 21: ProductMinimumStock + ProductArchive 事実

**Files:**
- Create: `ProductMinimumStock.kt`, `ProductArchive.kt`(`model/product/`)

- [ ] **Step 1: 実装**

`ProductMinimumStock.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class ProductMinimumStock(
    public val id: ProductMinimumStockId,
    internal val productId: ProductId,
    internal val minimumStock: MinimumStock,
    internal val editedBy: UserId,
    internal val createdAt: Instant,
)
```

`ProductArchive.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class ProductArchive(
    public val id: ProductArchiveId,
    internal val productId: ProductId,
    internal val archivedBy: UserId,
    internal val createdAt: Instant,
)
```

- [ ] **Step 2: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add ProductMinimumStock and ProductArchive facts"
```

---

### Task 22: Stock 事実(Replenishment / Consumption / 訂正)

**Files:**
- Create: `StockReplenishment.kt`, `StockConsumption.kt`, `StockReplenishmentCorrection.kt`, `StockConsumptionCorrection.kt`(`model/stock/`)

- [ ] **Step 1: 実装**

`StockReplenishment.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId

public class StockReplenishment(
    public val id: StockReplenishmentId,
    internal val productId: ProductId,
    internal val quantity: Quantity,
    internal val occurredAt: OccurredAt,
    internal val actedBy: UserId,
    internal val note: Note,
    internal val createdAt: Instant,
)
```

`StockConsumption.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId

public class StockConsumption(
    public val id: StockConsumptionId,
    internal val productId: ProductId,
    internal val quantity: Quantity,
    internal val occurredAt: OccurredAt,
    internal val actedBy: UserId,
    internal val note: Note,
    internal val createdAt: Instant,
)
```

`StockReplenishmentCorrection.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class StockReplenishmentCorrection(
    public val id: StockReplenishmentCorrectionId,
    internal val stockReplenishmentId: StockReplenishmentId,
    internal val correctedQuantity: Quantity,
    internal val reason: Reason,
    internal val correctedBy: UserId,
    internal val createdAt: Instant,
)
```

`StockConsumptionCorrection.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId

public class StockConsumptionCorrection(
    public val id: StockConsumptionCorrectionId,
    internal val stockConsumptionId: StockConsumptionId,
    internal val correctedQuantity: Quantity,
    internal val reason: Reason,
    internal val correctedBy: UserId,
    internal val createdAt: Instant,
)
```

- [ ] **Step 2: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add stock event facts (replenishment, consumption, corrections)"
```

---

## Phase F: Repository ポート

### Task 23: User 集約 Repository ポート

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/user/UserRepository.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/repository/user/UserRegisterRepository.kt`

- [ ] **Step 1: UserRepository(参照系)**

```kotlin
package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserDisplayName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.ZitadelSub

public interface UserRepository {
    public fun findById(id: UserId): User?
    public fun findByZitadelSub(sub: ZitadelSub): User?

    /** ユーザーの最新表示名を取得する。display_name 履歴がない場合は null。 */
    public fun findDisplayNameOf(userId: UserId): UserDisplayName?
}
```

- [ ] **Step 2: UserRegisterRepository(登録系)**

```kotlin
package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.ZitadelSub

public interface UserRegisterRepository {
    /** users 行を新規 INSERT(id は呼び出し側が UUIDv7 を生成して引数化)。 */
    public fun register(id: UserId, zitadelSub: ZitadelSub)

    /** user_display_names 行を新規 INSERT。最新表示名のロールフォワード扱い。 */
    public fun rename(userId: UserId, displayName: DisplayName)
}
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add User repository ports"
```

---

### Task 24: Household 集約 Repository ポート

**Files:**
- Create: `HouseholdRepository.kt`, `HouseholdRegisterRepository.kt`(`repository/household/`)

- [ ] **Step 1: HouseholdRepository(参照系)**

```kotlin
package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMembership
import net.brightroom.mindstock.domain.model.user.UserId

public interface HouseholdRepository {
    public fun findById(id: HouseholdId): Household?

    /** ユーザーが現在所属する世帯のメンバーシップ(有効な最新)。未所属または revoke 済みなら null。 */
    public fun findMembershipOf(userId: UserId): HouseholdMembership?

    /** 世帯の有効なメンバー一覧。 */
    public fun listMembersOf(householdId: HouseholdId): List<HouseholdMembership>
}
```

- [ ] **Step 2: HouseholdRegisterRepository(登録系)**

```kotlin
package net.brightroom.mindstock.domain.repository.household

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.HouseholdMembershipId
import net.brightroom.mindstock.domain.model.user.UserId

public interface HouseholdRegisterRepository {
    public fun create(id: HouseholdId)
    public fun join(householdId: HouseholdId, userId: UserId, role: HouseholdMemberRole)
    public fun revoke(membershipId: HouseholdMembershipId)
}
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add Household repository ports"
```

---

### Task 25: CatalogItem 集約 Repository ポート

**Files:**
- Create: `CatalogItemRepository.kt`, `CatalogItemRegisterRepository.kt`(`repository/catalog/`)

- [ ] **Step 1: CatalogItemRepository(参照系)**

```kotlin
package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId

public interface CatalogItemRepository {
    /** catalog_items + 最新 catalog_item_revisions を joins した CatalogItem。 */
    public fun findById(id: CatalogItemId): CatalogItem?

    /** 名前部分一致検索(MVP は単純な LIKE で OK)。 */
    public fun search(query: String, limit: Int = 50): List<CatalogItem>
}
```

- [ ] **Step 2: CatalogItemRegisterRepository(登録系)**

```kotlin
package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.UserId

public interface CatalogItemRegisterRepository {
    /**
     * catalog_items + catalog_item_revisions(初回)を 1 トランザクションで INSERT。
     */
    public fun register(
        id: CatalogItemId,
        createdBy: UserId,
        name: CatalogItemName,
        unit: CatalogItemUnit,
    )

    /**
     * 新リビジョンを INSERT。
     * 名前のみ・単位のみの変更でも、両方の値を持ち回す責任は呼び出し側(UseCase)。
     */
    public fun revise(
        catalogItemId: CatalogItemId,
        name: CatalogItemName,
        unit: CatalogItemUnit,
        editedBy: UserId,
    )
}
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add CatalogItem repository ports"
```

---

### Task 26: Product 集約 Repository ポート

**Files:**
- Create: `ProductRepository.kt`, `ProductRegisterRepository.kt`(`repository/product/`)

- [ ] **Step 1: ProductRepository(参照系)**

```kotlin
package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId

public interface ProductRepository {
    /** products + 最新 minimum_stock + 最新 archive を joins。 */
    public fun findById(id: ProductId): Product?

    /** 同一世帯で同一カタログ商品を採用済みか引く(`UNIQUE` 検出用)。 */
    public fun findByHouseholdAndCatalog(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): Product?

    /** 世帯のすべての商品(アーカイブ含む。フィルタは Application で)。 */
    public fun listByHousehold(householdId: HouseholdId): List<Product>
}
```

- [ ] **Step 2: ProductRegisterRepository(登録系)**

```kotlin
package net.brightroom.mindstock.domain.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.user.UserId

public interface ProductRegisterRepository {
    public fun adopt(id: ProductId, householdId: HouseholdId, catalogItemId: CatalogItemId)
    public fun setMinimumStock(productId: ProductId, value: MinimumStock, editedBy: UserId)
    public fun archive(productId: ProductId, archivedBy: UserId)
}
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add Product repository ports"
```

---

### Task 27: Stock 集約 Repository ポート

**Files:**
- Create: `StockRepository.kt`, `StockRegisterRepository.kt`(`repository/stock/`)

- [ ] **Step 1: StockRepository(参照系)**

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.StockConsumption
import net.brightroom.mindstock.domain.model.stock.StockConsumptionCorrection
import net.brightroom.mindstock.domain.model.stock.StockConsumptionId
import net.brightroom.mindstock.domain.model.stock.StockReplenishment
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentCorrection
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentId

public interface StockRepository {
    public fun findReplenishmentById(id: StockReplenishmentId): StockReplenishment?
    public fun findConsumptionById(id: StockConsumptionId): StockConsumption?

    public fun listReplenishmentsOf(productId: ProductId, limit: Int = 50): List<StockReplenishment>
    public fun listConsumptionsOf(productId: ProductId, limit: Int = 50): List<StockConsumption>

    public fun listCorrectionsOf(replenishmentId: StockReplenishmentId): List<StockReplenishmentCorrection>
    public fun listCorrectionsOf(consumptionId: StockConsumptionId): List<StockConsumptionCorrection>
}
```

- [ ] **Step 2: StockRegisterRepository(登録系)**

```kotlin
package net.brightroom.mindstock.domain.repository.stock

import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Reason
import net.brightroom.mindstock.domain.model.stock.StockConsumptionId
import net.brightroom.mindstock.domain.model.stock.StockReplenishmentId
import net.brightroom.mindstock.domain.model.user.UserId

public interface StockRegisterRepository {
    public fun replenish(
        productId: ProductId,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actedBy: UserId,
        note: Note,
    ): StockReplenishmentId

    public fun consume(
        productId: ProductId,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actedBy: UserId,
        note: Note,
    ): StockConsumptionId

    public fun correct(
        replenishmentId: StockReplenishmentId,
        correctedQuantity: Quantity,
        reason: Reason,
        correctedBy: UserId,
    )

    public fun correct(
        consumptionId: StockConsumptionId,
        correctedQuantity: Quantity,
        reason: Reason,
        correctedBy: UserId,
    )
}
```

- [ ] **Step 3: コンパイル + コミット**

```bash
./gradlew :domain:compileKotlinJvm --no-daemon
git commit -m "feat(domain): add Stock repository ports"
```

---

## Phase G: 最終検証

### Task 28: ビルド全体の緑化と最終チェック

- [ ] **Step 1: domain の全テスト実行**

Run: `./gradlew :domain:check --no-daemon`
Expected: BUILD SUCCESSFUL(全 VO テスト + ID テスト + ガードテスト + 例外テスト)

- [ ] **Step 2: 影響モジュールの再ビルド**

Run: `./gradlew :shared:extensions:check :shared:rpc:build :backend:infrastructure:schemas:build --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: トップレベル check(全モジュール)**

Run: `./gradlew check --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Spotless チェック**

Run: `./gradlew spotlessCheck --no-daemon`
Expected: BUILD SUCCESSFUL(必要なら `spotlessApply` してから再実行 + 修正コミット)

- [ ] **Step 5: 最終コミット(差分があれば)**

```bash
git status
# 不要なら何もしない。Spotless で fix が入った場合は:
git add -u
git commit -m "style: spotless apply"
```

- [ ] **Step 6: ブランチ push と PR 作成**

```bash
git push -u origin feat/domain-layer
gh pr create --title "feat: Plan 3 — domain layer (aggregates, VOs, repository ports)" --body "Implements Plan 3 per docs/superpowers/plans/2026-05-23-domain-layer.md and design spec docs/superpowers/specs/2026-05-23-domain-layer-design.md."
```

---

## 補足: コミット粒度の方針

- 1 タスク = 1 コミット(原則)
- VO 1 個追加 = テスト + 実装 + DomainException 追加(あれば)を 1 コミットに
- ID 群はカテゴリ単位(UUID 系まとめて 1 コミット、Long 系まとめて 1 コミット)で OK
- 集約と事実は 1 集約 = 1 コミット
- Repository ポートは 1 集約 = 1 コミット

## 注意事項

- `@OptIn(ExperimentalUuidApi::class)` が必要なのは `kotlin.uuid.Uuid` を使うファイル全体。Kotlin 2.3 でも experimental 扱い。
- `OccurredAt` だけは `@JvmInline value class` ではなく通常の `class`(secondary constructor で外部依存 `now` を受けるため)。シリアライズ復元用の primary constructor は検証なし。
- Long 系 ID は `value < 0` で `InvalidIdentity` を throw する。0 は許容(DB は IDENTITY 1 始まりだが、0 をテストで使うことがある)。
- `Note` / `Reason` は空文字許容、検証ロジック・例外サブクラスなし。
- 全 VO/ID は `@Serializable` 必須(`:shared:rpc` の DTO から参照されるため)。
- Kotest API は `io.kotest.matchers.shouldBe` 等の中置記法。`shouldThrow` は `io.kotest.assertions.throwables.shouldThrow`。
- 各タスクの「テストを実行して失敗確認」ステップは TDD の Red 相当。コンパイル失敗も「失敗」として OK。
- 各タスクの「コミット」ステップでは `git add -A` ではなく**個別ファイル指定**で(他の作業ファイルが混入しないように)。
