# Plan C: パッケージ整理 + API 統一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** パッケージ整理と API 統一による軽量な構造改善。(1) `shopping/` を `model/stock/shopping/` に移動、(2) `Quantity / Note / OccurredAt` を `stock/movement/` に移動して循環参照を解消、(3) 集合型 API を `val list` 公開で統一、(4) `OccurredAt` の init 単一コンストラクタ化。

**Architecture:** 各変更は独立 PR として merge 可能。順序は依存関係に従い Phase 1 → 4。

**Tech Stack:** Kotlin Multiplatform / Exposed v1 / kotlinx-rpc 0.10.2 / kotlinx-serialization / Kotest

**Spec:** `docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md`（所見 1.1 / 1.2 / 3.2 / 4.1）

**前提:** Plan A + Plan B が完了済。本 Plan は基本的に import path 変更と機械的な API 置換で、ビジネスロジック変更なし。

---

## File Plan

### Phase 1: `shopping/` を `stock/shopping/` に移動（所見 1.1）

**移動:**
- `domain/.../model/shopping/ShoppingList.kt` → `domain/.../model/stock/shopping/ShoppingList.kt`
- `domain/.../model/shopping/ShoppingListItem.kt` → `domain/.../model/stock/shopping/ShoppingListItem.kt`
- `domain/src/commonTest/.../model/shopping/ShoppingListTest.kt` → `domain/src/commonTest/.../model/stock/shopping/ShoppingListTest.kt`

**修正（import 更新のみ）:**
- 上記 3 ファイルの `package` 宣言
- `ShoppingList` / `ShoppingListItem` を import している全箇所

### Phase 2: `Quantity / Note / OccurredAt` を `stock/movement/` に移動（所見 1.2）

**移動:**
- `domain/.../model/stock/Quantity.kt` → `domain/.../model/stock/movement/Quantity.kt`
- `domain/.../model/stock/Note.kt` → `domain/.../model/stock/movement/Note.kt`
- `domain/.../model/stock/OccurredAt.kt` → `domain/.../model/stock/movement/OccurredAt.kt`
- `domain/src/commonTest/.../model/stock/QuantityTest.kt` → `domain/src/commonTest/.../model/stock/movement/QuantityTest.kt`
- `domain/src/commonTest/.../model/stock/NoteTest.kt` → `domain/src/commonTest/.../model/stock/movement/NoteTest.kt`
- `domain/src/commonTest/.../model/stock/OccurredAtTest.kt` → `domain/src/commonTest/.../model/stock/movement/OccurredAtTest.kt`

**修正（import 更新のみ）:**
- 上記 6 ファイルの `package` 宣言
- これらを import している全箇所

確認コマンド:
```bash
grep -rln "domain.model.stock.\(Quantity\|Note\|OccurredAt\)" --include="*.kt" .
```

### Phase 3: 集合型 API 統一（所見 4.1）

**修正:**
- `domain/.../model/catalog/CatalogItems.kt` — `asList()` 削除、`size` 削除
- `domain/.../model/product/Products.kt` — `asList()` 削除、`size` 削除（`activeOnly()` は残す）
- `domain/.../model/household/HouseholdMembers.kt` — `asList()` 削除
- `domain/.../model/stock/movement/StockMovements.kt` — `asList()` 削除、`size` 削除（`netQuantity()` は残す）
- `domain/.../model/stock/Stocks.kt` — `asList()` 削除、`size` 削除（Plan B で作成済）
- すべての呼び出し側（`asList()`, `.size` 経由）を `.list` または `.list.size` に変更
- 内部実装で `stocks.asList().filter { ... }` のような箇所も `stocks.list.filter { ... }` に
- 各テスト

### Phase 4: `OccurredAt` init 単一コンストラクタ化（所見 3.2）

**修正:**
- `domain/.../model/stock/movement/OccurredAt.kt`（Phase 2 で移動済）の secondary constructor を削除し、init で `Clock.System.now()` を呼ぶ
- `domain/src/commonTest/.../model/stock/movement/OccurredAtTest.kt` — テスト方法を「現実時計依存」または `MockClock` 的な手段で

注: 既にローカル変更で着手済（`OccurredAt.kt` の secondary 残しつつ init を追加した状態）。本 Phase でローカル変更を吸収・整理する。

---

## 新規型の正準シグネチャ

### Phase 3 後の集合型（共通形）

```kotlin
@Serializable
data class Products(
    val list: List<Product>,
) {
    fun activeOnly(): Products = Products(list.filter { !it.archived })
}

@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
)

@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile
    fun activeMembers(): List<Profile> = list.map { it.profile }
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }
}

@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun netQuantity(): Int =
        list.sumOf { m ->
            when (m) {
                is Replenishment -> +m.quantity()
                is Consumption -> -m.quantity()
            }
        }
}

@Serializable
data class Stocks(
    val list: List<Stock>,
)
```

### Phase 4 後の `OccurredAt`

```kotlin
// domain/.../model/stock/movement/OccurredAt.kt
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
@JvmInline
value class OccurredAt(
    private val value: Instant,
) {
    init {
        require(value <= Clock.System.now()) { "occurredAt $value must be <= now" }
    }

    override fun toString(): String = value.toString()
    operator fun invoke(): Instant = value
}
```

注: 現状の `data class` から `value class` に変更。`@JvmInline value class` + `init` + `@Serializable` の組み合わせがデシリアライズ時に `init` を実行するか事前検証が必要（Phase 4 Step 4.1）。

---

## Phase 1: `shopping/` を `stock/shopping/` に移動

### Steps

- [ ] **Step 1.1: 3 ファイルを git mv**

```bash
mkdir -p domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/shopping
mkdir -p domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/shopping

git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingList.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/shopping/

git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingListItem.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/shopping/

git mv domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/shopping/ShoppingListTest.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/shopping/

rmdir domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/shopping
rmdir domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/shopping
```

- [ ] **Step 1.2: 移動した 3 ファイルの `package` 宣言を更新**

旧: `package net.brightroom.mindstock.domain.model.shopping`
新: `package net.brightroom.mindstock.domain.model.stock.shopping`

- [ ] **Step 1.3: 利用箇所の import を更新**

確認コマンド:
```bash
grep -rln "domain.model.shopping\." --include="*.kt" .
```

旧: `import net.brightroom.mindstock.domain.model.shopping.ShoppingList`
新: `import net.brightroom.mindstock.domain.model.stock.shopping.ShoppingList`

（`ShoppingListItem` も同様）

- [ ] **Step 1.4: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 1.5: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): move shopping/ under stock/ as derived read-model

ShoppingList は List<Stock> の派生 read-model であり独立 BC ではない
ため、stock/shopping/ サブパッケージに移動。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: `Quantity / Note / OccurredAt` を `stock/movement/` に移動

**目的:** これら 3 VO は実際には `Replenishment` / `Consumption` のフィールドであり、`Stock` 自身は使っていない（`Stock.currentQuantity()` は `Int` を返す）。`stock/` 直下に置いてあるため `stock → stock.movement` と `stock.movement → stock`（Quantity 等を参照）の循環が発生していた。`stock/movement/` に移すと単方向になる。

### Steps

- [ ] **Step 2.1: 3 ファイルを git mv**

```bash
git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Quantity.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/

git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Note.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/

git mv domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAt.kt \
       domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/

git mv domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/QuantityTest.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/

git mv domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/NoteTest.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/

git mv domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/OccurredAtTest.kt \
       domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/stock/movement/
```

- [ ] **Step 2.2: 移動した 6 ファイルの `package` 宣言を更新**

旧: `package net.brightroom.mindstock.domain.model.stock`
新: `package net.brightroom.mindstock.domain.model.stock.movement`

- [ ] **Step 2.3: 利用箇所の import を更新**

確認コマンド:
```bash
grep -rln "domain.model.stock.\(Quantity\|Note\|OccurredAt\)" --include="*.kt" .
```

各ファイルで:
- 旧: `import net.brightroom.mindstock.domain.model.stock.Quantity`
- 新: `import net.brightroom.mindstock.domain.model.stock.movement.Quantity`

`Note`, `OccurredAt` も同様。

対象ファイル（事前 grep 結果）:
- domain 内（Replenishment, Consumption, StockMovement, Stock, StockMovements 等）
- `rpc/.../StockRpcService.kt`
- `backend/core/.../infrastructure/datasource/stock/StockHydration.kt`
- `backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt`
- backend/api E2E test 群

- [ ] **Step 2.4: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 2.5: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): move Quantity/Note/OccurredAt into stock/movement/

これら 3 VO は StockMovement の構成要素であり、Stock 集約ルート自体は
使わない。stock/ 直下に置いてあったため stock <-> stock.movement の循環
参照になっていた。movement/ サブパッケージに移して単方向化。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: 集合型 API 統一

**目的:** `val list` 公開を統一フォーマットとし、`asList()` / `size` メソッドを削除する。`list.size` は `List<T>` から自明、`asList()` は `val list` 公開がある以上冗長。

### Steps

- [ ] **Step 3.1: 5 つの集合型から `asList()` と `size` を削除**

`CatalogItems.kt`:

```kotlin
@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
)
```

`Products.kt`:

```kotlin
@Serializable
data class Products(
    val list: List<Product>,
) {
    fun activeOnly(): Products = Products(list.filter { !it.archived })
}
```

`HouseholdMembers.kt`:

```kotlin
@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile
    fun activeMembers(): List<Profile> = list.map { it.profile }
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }
}
```

`StockMovements.kt`:

```kotlin
@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun netQuantity(): Int =
        list.sumOf { m ->
            when (m) {
                is Replenishment -> +m.quantity()
                is Consumption -> -m.quantity()
            }
        }
}
```

`Stocks.kt`（Plan B 4.1 で作成済）:

```kotlin
@Serializable
data class Stocks(
    val list: List<Stock>,
)
```

- [ ] **Step 3.2: 呼び出し側の `.asList()` を `.list` に置換**

確認コマンド:
```bash
grep -rn "\.asList()" --include="*.kt" .
```

例:

```kotlin
// 旧
val items = products.asList()
// 新
val items = products.list
```

- [ ] **Step 3.3: 呼び出し側の `.size`（集合型経由）を `.list.size` に置換**

確認コマンド:
```bash
grep -rn "products.size\|catalogItems.size\|members.size\|movements.size\|stocks.size" --include="*.kt" .
```

各箇所で `.size` → `.list.size`。

注: 集合型変数ではない通常の `List<T>` の `.size` は対象外。

- [ ] **Step 3.4: ShoppingList などで使っている `stocks.asList().filter` 系を更新**

`ShoppingList.kt`（Plan B Phase 4 で `Stocks` 受け取りに変更済）:

```kotlin
class ShoppingList(
    private val stocks: Stocks,
) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks.list
            .filter { it.needsReplenishment() }
            .map { ShoppingListItem(it, shortage = it.shortage()) }
}
```

- [ ] **Step 3.5: テスト群を更新**

各 `*Test.kt` で `.asList()` / `.size` を使っている箇所を更新。

確認コマンド:
```bash
grep -rn "\.asList()\|\.size" domain/src/commonTest --include="*.kt"
```

- [ ] **Step 3.6: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 3.7: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): unify collection types on public val list

集合型(Products/CatalogItems/HouseholdMembers/StockMovements/Stocks)の
API を val list 公開で統一。冗長な asList() / size メソッドを削除し、
呼び出し側は .list / .list.size に置換。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: `OccurredAt` init 単一コンストラクタ化

**目的:** 現状の `data class` + secondary constructor 構造を `@JvmInline value class` + `init` に統一する。`init` 内で `Clock.System.now()` を呼ぶ。

### Steps

- [ ] **Step 4.1: 事前検証 — `@Serializable @JvmInline value class` + `init` のデシリアライズ挙動**

`domain/src/commonTest/.../Sandbox.kt`（一時ファイル）に:

```kotlin
@file:OptIn(ExperimentalSerializationApi::class)
package net.brightroom.mindstock.domain

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline
import kotlin.test.Test

@Serializable
@JvmInline
value class GuardedInt(private val value: Int) {
    init {
        require(value >= 0)
    }
    operator fun invoke(): Int = value
}

class SandboxTest {
    @Test
    fun `deserialize invalid value should fail with require`() {
        runCatching { Json.decodeFromString<GuardedInt>("-1") }
            .isFailure shouldBe true   // ← init が走るかどうかの確認
    }
}
```

```bash
./gradlew :domain:commonTest --tests "*Sandbox*"
```

- **expect FAIL**: デシリアライズで `init` が走らない場合、`shouldBe true` が外れる。その場合は **Phase 4 を中断**、`OccurredAt` 用にカスタム `KSerializer` を書く方針に切り替える（後述）。

- **expect PASS**: デシリアライズで `init` が走る場合、Phase 4 を予定通り進める。

Sandbox 削除:
```bash
rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/Sandbox.kt
```

- [ ] **Step 4.2: `OccurredAt.kt` を `value class` + 単一 init に書き換え**

```kotlin
// domain/.../model/stock/movement/OccurredAt.kt
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
@JvmInline
value class OccurredAt(
    private val value: Instant,
) {
    init {
        require(value <= Clock.System.now()) { "occurredAt $value must be <= now" }
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Instant = value
}
```

注: `data class` → `value class` に変わるので `equals/hashCode/copy/componentN` の生成セマンティクスが変わる。`equals` は値同等のままで OK だが、`copy` を使っている呼び出し箇所があれば検出が必要:

```bash
grep -rn "OccurredAt(.*\.copy\(" --include="*.kt" .
```

- [ ] **Step 4.3: `OccurredAtTest.kt` を新インタフェースに合わせる**

旧テストは secondary constructor で `now` を渡す形:

```kotlin
shouldThrow<IllegalArgumentException> {
    OccurredAt(futureInstant, now = currentInstant)
}
```

新形式（now は internal で取られる）:

```kotlin
shouldThrow<IllegalArgumentException> {
    // テスト実行時刻の数秒先を渡す
    OccurredAt(Clock.System.now().plus(60.seconds))
}
```

テスト時計差し替えが必要なら、テスト用に `OccurredAt.of(value, now)` factory を後で追加する選択肢もあるが、本 Plan の範囲外。

- [ ] **Step 4.4: 呼び出し側の `OccurredAt(value, now)` を `OccurredAt(value)` に**

確認コマンド:
```bash
grep -rn "OccurredAt(.*,.*now" --include="*.kt" .
```

各箇所で `, now)` を削除し、`OccurredAt(value)` のみに。

- [ ] **Step 4.5: ビルドと全テスト**

```bash
./gradlew clean build
```

- [ ] **Step 4.6: コミット**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): unify OccurredAt constructor with init-time Clock check

二重コンストラクタ(primary 無検証 + secondary でガード)を廃止し、
@JvmInline value class + init で Clock.System.now() を呼んで検証。
VO で stdlib/datetime を呼ぶ方針(memory: domain-refactor-policy-2026-05)
に揃える。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Phase 4 の代替パス（事前検証で FAIL した場合）

Step 4.1 が FAIL したら、デシリアライズで `init` が走らないことが確認されたことになる。その場合:

- [ ] **代替 Step A: カスタム `KSerializer<OccurredAt>` を書く**

```kotlin
@Serializable(with = OccurredAtSerializer::class)
@JvmInline
value class OccurredAt(private val value: Instant) {
    init {
        require(value <= Clock.System.now()) { "occurredAt must be <= now" }
    }
    operator fun invoke(): Instant = value
}

object OccurredAtSerializer : KSerializer<OccurredAt> {
    private val delegate = Instant.serializer()
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: OccurredAt) {
        delegate.serialize(encoder, value())
    }
    override fun deserialize(decoder: Decoder): OccurredAt =
        OccurredAt(delegate.deserialize(decoder))
}
```

これで `deserialize` 経由でも `OccurredAt(...)` 呼び出しが走り、`init` のガードが効く。

---

## 検証チェックリスト

Plan C 完了の判定条件:

- [ ] `domain/.../model/shopping/` ディレクトリが存在しない
- [ ] `domain/.../model/stock/shopping/ShoppingList.kt` が存在
- [ ] `domain/.../model/stock/Quantity.kt` / `Note.kt` / `OccurredAt.kt` が**存在しない**（movement/ に移動済）
- [ ] `domain/.../model/stock/movement/OccurredAt.kt` が存在し、`@JvmInline value class` + 単一 init
- [ ] `grep "\.asList()" --include="*.kt" -r .` の結果が 0 件（集合型での `asList()` 呼び出しが消えている）
- [ ] `domain/.../model/{Products,CatalogItems,HouseholdMembers,StockMovements,Stocks}.kt` のいずれも `asList()` メソッドを持たない
- [ ] `domain/.../model/{Products,CatalogItems,HouseholdMembers,StockMovements,Stocks}.kt` のいずれも `val size` プロパティを持たない
- [ ] パッケージ依存グラフに循環なし（stock → stock.movement の単方向）
- [ ] `./gradlew clean build` 成功
- [ ] `./gradlew test` 全 pass

---

## 想定リスク

| リスク | 対策 |
|---|---|
| `data class` → `value class` への変更で `.copy` 利用箇所が壊れる | Step 4.2 の grep で検出。`OccurredAt(newValue)` 直接構築に置換 |
| `Clock.System.now()` を VO 内で呼ぶことでテスト時の時計差し替えが困難 | 本 Plan ではテスト時は実時計に依存。問題が出たら `OccurredAt.of(value, now)` factory を別途追加 |
| デシリアライズで `init` が走らない可能性 | Step 4.1 の事前検証で早期検出。FAIL なら代替パス(カスタム KSerializer) |
| 集合型 API 統一で呼び出し漏れ | grep ベースの全件確認 + ビルドエラーで検出 |
