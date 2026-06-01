# P2: domain — catalog / inventory コンテキスト Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:domain` モジュールに catalog(大元商品マスタ)/ inventory(Product・Stock・StockMovement・ShoppingList)の集約・VO・区分・判定・例外を実装し、`./gradlew :domain:build` が緑になる状態を作る。

**Architecture:** 純粋ドメイン(KMP commonMain、外部依存は kotlin stdlib / kotlinx-serialization / kotlinx-datetime のみ)。ビジネスルールは集約に埋めず **区分(behavior 付き enum)/ 判定クラス / ファーストクラスコレクション** へ外出しし、集約は薄く委譲する([03 詳細ドメイン](../specs/2026-05-31-mindstock-full-replace-design/03-domain-detail.md)、[domain-guideline](../../../.claude/rules/domain-guideline.md))。`CatalogItem` は素性(全世帯共有)、`Product` は世帯の採用、`Stock` は数量・台帳のルート。**区分は型名=英語・値=日本語(ユビキタス言語)**。

**Tech Stack:** Kotlin 2.3.21 Multiplatform / kotlinx-serialization 1.11.0 / `kotlin.uuid.Uuid`(`generateV7()`)/ `kotlin.time.Clock`(stdlib)/ テストは `kotlin.test` + Kotest assertions。

---

## 設計判断: spec-03 の未確定事項の確定 + 乖離(本プランで確定)

spec-03 が「P2 で確定する」と明記した 2 点を、ユーザ合意のもと以下で確定した。**判断1 は spec-03 の未確定事項(id の出所)の確定、判断2 は spec-03 からの乖離**。**spec-03 のクラス図と一部異なるが本プランを正とする**。

1. **【未確定事項の確定】訂正(`correct`)はドメインに置く。新規 movement の id 出所を sealed `MovementIdentity` で確定する**。spec-03 は `StockMovement.id: MovementId` を載せたが「新規作成 movement がどう id を得るか」を定義していなかった。`Stock.correct()` / `MovementId` / `Correction` を domain に持つ点は spec-03 どおりで、**identity の表現方法のみ本プランで確定する**(ここが新規)。
   - **理由(ドメインに置く)**: replenish/consume/archive が全てドメイン内操作なのに `correct` だけ application へ外出しするのは非対称で違和感がある。一貫性を優先し、訂正もドメインの不変更新メソッドにする(当初は application 寄せ案だったがユーザ判断で撤回)。
   - **identity の壁と解法(新規確定部分)**: 新規 movement の `Long` id をドメインは採番できず nullable 戻り値も禁止。だが**訂正対象は常に永続化済み(hydrate された)movement** なので、identity を sealed `MovementIdentity { Pending / Persisted(MovementId) }` で表せば解決する。hydrate 済み movement は `Persisted(id)`、`replenish`/`consume`/`correct` が新規に作る movement は `Pending`。`correct(target: MovementId)` は Persisted な対象を参照して検証でき、nullable を使わない。
   - **帰結**:
     - domain の `StockMovement` は sealed `Replenishment | Consumption | Correction`。共通フィールドに `identity: MovementIdentity` を持つ。`Correction` は `target: MovementId` + `reason: Reason` を追加。
     - `StockMovements.netQuantity()` が**畳み込みを行う**: 同一 target への最新(`occurredAt` 最大)Correction が base movement の数量を上書き(符号は base の種別を継承)、Correction 自身は直接加算しない。
     - infrastructure は hydrate 時に**全 movement(base + 訂正)を Persisted id 付きで読み込む**だけ(畳み込みは domain が行う)。
     - `MovementId`(Long・非負)/ `Reason`(非空≤255)/ `MovementIdentity` VO を P2 で作る。`DuplicateJanException` のように application が throw する例外型は引き続き domain に置く(P1 前例)。
2. **`manualWanted` は `Stock` から分離する**(03 spec の `Stock(.., manualWanted: Boolean)` を採らない)。
   - **理由**: `Stock` は数量・台帳の集約で、「在庫十分でも買いたい」という手動希望は別関心。append-only 集約に mutable boolean を載せない。
   - **帰結**: `Stock` は `(product, movements)` のみ。手動希望は `ShoppingList` read-model の合成入力(`ShoppingEntry.manuallyWanted`)として外から与える。手動希望そのものの永続化は P4 の関心。domain は `ShoppingNeed.judge(status, manuallyWanted)` の合成ロジックのみ持つ。

---

## 前提・規約(全タスク共通)

- パッケージルート: `net.brightroom.mindstock.domain`。ソースは `domain/src/commonMain/kotlin/...`、テストは `domain/src/commonTest/kotlin/...`(同一パッケージ。`internal` メンバはテストから参照可)。
- **P1 で確立済みの型を再利用する**(再定義しない): `Resident`(`model/resident/Resident.kt`)、`ResidentId`、`DisplayName` / `Profile`、`ResourceNotFoundException`。`StockMovement.actor` は `Resident`(id + 表示)を composition で埋め込む。
- VO 規約([domain-guideline](../../../.claude/rules/domain-guideline.md)): `@Serializable @JvmInline value class`、バッキングは `private val value`、`internal operator fun invoke(): T`、`override fun toString()`、バリデーションは `init { require(...) }`(IAE)。**意味のないファクトリ(`Quantity.of` 等)は作らない**。
  - **重要(KMP)**: `@JvmInline value class` の各ファイルに `import kotlin.jvm.JvmInline` を**明示的に書く**。JVM 以外(JS/Wasm/metadata)コンパイラは `kotlin.jvm.*` を自動解決しない。`:domain:jvmTest` だけでは気付けず `:domain:build`(全ターゲット)で初めて落ちる。
- ID 採番: `companion object { fun create() = XxxId(Uuid.generateV7()) }`。`Uuid` は experimental なので **Uuid を使うファイルにだけ `@file:OptIn(ExperimentalUuidApi::class)` を明示**する(gradle 全体 opt-in はしない)。
- 時刻: `OccurredAt.now()` は `kotlin.time.Clock.System.now()`(stdlib・guideline 許容)を wrap し `kotlin.time.Instant` を保持する。**検証済み**: 本リポジトリ(Kotlin 2.3.21 / kotlinx-serialization 1.11.0)では `kotlin.time.Clock` / `Instant` は opt-in 不要・stable で、`@Serializable value class OccurredAt(val value: kotlin.time.Instant)` は組み込みシリアライザで `:domain:compileKotlinJvm` が通る(コンパイルプローブで確認済)。`kotlinx.datetime.Instant` ではなく `kotlin.time.Instant` を使う(既存 `shared/.../datetime/LocalDateTime.kt` も `kotlin.time.Clock` 採用)。
- 区分(enum): **型名は英語、entry は日本語**。`enum-entry-name-case` は P1 の Task 1 で root `.editorconfig` 無効化済み(本プランでは追加設定不要)。区分は 1 型 1 ファイル(P1 の landed 慣行に合わせる)。
- **不変更新は `.copy()` 禁止・コンストラクタ明示構築**([immutable-construction](../../../.claude/rules/immutable-construction.md)、`domain/**/*.kt` 適用): 集約/エンティティの状態更新メソッドは `.copy(field = ...)` ではなく `Stock(product, newMovements)` のように**全フィールドをコンストラクタで明示**して返す(フィールド追加時の設定漏れをコンパイルエラーで検出)。VO(単一フィールド value class)は対象外。本プランの `Product.archive/unarchive`・`Stock.replenish/consume/correct/archive/unarchive` が該当。
- sealed: `Barcode` / `ProductImage` / `StockMovement` / `MovementIdentity` は sealed interface + `@Serializable`。**variant に `@JvmInline value class` を使わない**(polymorphic deserialize 破壊の gotcha 回避)。variant は `data object` か `data class`。
- **テストは「意味のあるもの」だけ書く([.claude/rules/testing.md](../../../.claude/rules/testing.md))。** バリデーション・判定・計算・抽出・状態遷移・前提崩れの例外のみ。コンストラクタ/保持/単純なアクセサ/equals は書かない。
- テスト実行: `./gradlew :domain:jvmTest --tests "<FQCN>" --console=plain`(KMP commonTest は jvmTest で走る)。
- テスト不要タスクの検証: `./gradlew :domain:compileKotlinJvm --console=plain`(緑=実装 OK)。
- コミット前に必ず整形: `./gradlew :domain:spotlessApply`(各 Commit ステップに含む。import 順序は spotless が整える)。

## ファイル構成(このプランで作成するもの)

```text
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
  exception/InsufficientStockException.kt                       Task 1（テスト無し）
  exception/CannotArchiveWithStockException.kt                  Task 1（テスト無し）
  exception/DuplicateJanException.kt                            Task 1（テスト無し・application が throw）
  model/inventory/quantity/Quantity.kt                         Task 2（値域テスト）
  model/inventory/stock/movement/OccurredAt.kt                 Task 2（テスト無し）
  model/inventory/stock/movement/Note.kt                       Task 3（値域テスト）
  model/inventory/product/image/ImageRef.kt                    Task 3（値域テスト）
  model/catalog/item/CatalogItemId.kt                          Task 4（テスト無し）
  model/catalog/content/CatalogItemName.kt                     Task 4（値域テスト）
  model/catalog/content/CatalogItemUnit.kt                     Task 4（値域テスト）
  model/catalog/barcode/Jan.kt                                 Task 5（EAN-13 テスト）
  model/catalog/barcode/Barcode.kt                             Task 6（テスト無し）
  model/catalog/origin/CatalogOrigin.kt                        Task 6（テスト無し）
  model/catalog/content/CatalogContent.kt                      Task 7（テスト無し）
  model/catalog/item/CatalogItem.kt                            Task 7（テスト無し）
  model/inventory/product/ProductId.kt                         Task 8（テスト無し）
  model/inventory/product/setting/ProductUnit.kt               Task 8（値域テスト）
  model/inventory/product/setting/MinimumStock.kt             Task 9（判定テスト）
  model/inventory/product/setting/StockingPolicy.kt           Task 10（テスト無し）
  model/inventory/product/image/ProductImage.kt               Task 10（テスト無し）
  model/inventory/product/ProductStatus.kt                    Task 10（テスト無し）
  model/inventory/product/Product.kt                          Task 11（テスト無し）
  model/inventory/stock/StockStatus.kt                        Task 12（判定テスト）
  model/inventory/stock/Archivability.kt                      Task 13（判定テスト）
  model/inventory/stock/movement/MovementId.kt               Task 14（値域テスト）
  model/inventory/stock/movement/Reason.kt                   Task 14（値域テスト）
  model/inventory/stock/movement/MovementIdentity.kt         Task 14（テスト無し）
  model/inventory/stock/movement/StockMovement.kt             Task 15（テスト無し）
  model/inventory/stock/movement/StockMovements.kt            Task 15（畳み込みテスト）
  model/inventory/stock/Stock.kt                              Task 16（前提崩れ・訂正テスト）
  model/inventory/shopping/ShoppingNeed.kt                    Task 17（判定テスト）
  model/inventory/shopping/ShoppingList.kt                    Task 18（抽出テスト）
```

---

## Task 1: P2 ドメイン例外

inventory で使う前提崩れ系の専用例外を定義する。例外は「メッセージを保持するだけ」なので**テストは書かない**。`DuplicateJanException` は採用サービス(application/P5)が throw するが、P1 の `InvitationInvalidException` 前例に倣い型は domain に置く。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/InsufficientStockException.kt`
- Create: `.../exception/CannotArchiveWithStockException.kt`
- Create: `.../exception/DuplicateJanException.kt`

- [ ] **Step 1: 3 つの例外を実装**

`InsufficientStockException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class InsufficientStockException(reason: String) : RuntimeException(reason)
```
`CannotArchiveWithStockException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class CannotArchiveWithStockException(reason: String) : RuntimeException(reason)
```
`DuplicateJanException.kt`:
```kotlin
package net.brightroom.mindstock.domain.exception

class DuplicateJanException(reason: String) : RuntimeException(reason)
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): inventory の前提崩れ系ドメイン例外 3 種を定義"
```

---

## Task 2: Quantity + OccurredAt(共通 VO)

`Quantity` の値域(`> 0`)だけ意味があるのでテストする。`OccurredAt` は `Clock` を wrap する生成のみ(テスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/quantity/Quantity.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/OccurredAt.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/quantity/QuantityTest.kt`

- [ ] **Step 1: 失敗するテストを書く(Quantity の値域のみ)**

`QuantityTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.quantity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class QuantityTest {
    @Test
    fun accepts_positive() {
        Quantity(1).invoke() shouldBe 1
    }

    @Test
    fun rejects_zero() {
        shouldThrow<IllegalArgumentException> { Quantity(0) }
    }

    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { Quantity(-1) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.quantity.QuantityTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Quantity.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.quantity

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Quantity(private val value: Int) {
    init {
        require(value > 0) { "Quantity must be positive: $value" }
    }

    internal operator fun invoke(): Int = value

    override fun toString(): String = value.toString()
}
```
`OccurredAt.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class OccurredAt(private val value: Instant) {
    internal operator fun invoke(): Instant = value

    override fun toString(): String = value.toString()

    companion object {
        fun now(): OccurredAt = OccurredAt(Clock.System.now())
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.quantity.QuantityTest" --console=plain`
Expected: PASS
(`OccurredAt` で experimental エラーが出たら `OccurredAt.kt` 先頭に `@file:OptIn(kotlin.time.ExperimentalTime::class)` を足し `import kotlin.time.ExperimentalTime` を追加)

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Quantity（>0）/ OccurredAt（Clock.now）VO"
```

---

## Task 3: Note + ImageRef(共通 VO)

`Note`(trim 後 最大 255・空許容)と `ImageRef`(非空)の値域をテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/Note.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageRef.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/NoteTest.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageRefTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`NoteTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteTest {
    @Test
    fun allows_empty_note() {
        Note("").invoke() shouldBe ""
    }

    @Test
    fun trims_surrounding_whitespace() {
        Note("  まとめ買い  ").invoke() shouldBe "まとめ買い"
    }

    @Test
    fun rejects_over_255_chars() {
        shouldThrow<IllegalArgumentException> { Note("あ".repeat(256)) }
    }
}
```
`ImageRefTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test

class ImageRefTest {
    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { ImageRef("  ") }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.NoteTest" --tests "net.brightroom.mindstock.domain.model.inventory.product.image.ImageRefTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Note.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Note(private val value: String) {
    init {
        require(value.trim().length <= MAX_LENGTH) {
            "Note must be at most $MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 255
    }
}
```
`ImageRef.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ImageRef(private val value: String) {
    init {
        require(value.isNotBlank()) { "ImageRef must not be blank" }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.NoteTest" --tests "net.brightroom.mindstock.domain.model.inventory.product.image.ImageRefTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Note（trim 後 0..255）/ ImageRef（非空）VO"
```

---

## Task 4: CatalogItemId + CatalogItemName + CatalogItemUnit

`CatalogItemName`(≤60)・`CatalogItemUnit`(≤10)の値域をテストする。`CatalogItemId` は生成のみ(テスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItemId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemName.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemUnit.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemNameTest.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemUnitTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`CatalogItemNameTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemNameTest {
    @Test
    fun trims_and_accepts() {
        CatalogItemName("  トイレットペーパー  ").invoke() shouldBe "トイレットペーパー"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("  ") }
    }

    @Test
    fun rejects_over_60_chars() {
        shouldThrow<IllegalArgumentException> { CatalogItemName("あ".repeat(61)) }
    }
}
```
`CatalogItemUnitTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatalogItemUnitTest {
    @Test
    fun trims_and_accepts() {
        CatalogItemUnit("  ロール  ").invoke() shouldBe "ロール"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("  ") }
    }

    @Test
    fun rejects_over_10_chars() {
        shouldThrow<IllegalArgumentException> { CatalogItemUnit("あ".repeat(11)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.catalog.content.CatalogItemNameTest" --tests "net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnitTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`CatalogItemId.kt`:
```kotlin
@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.catalog.item

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CatalogItemId(private val value: Uuid) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): CatalogItemId = CatalogItemId(Uuid.generateV7())
    }
}
```
`CatalogItemName.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.content

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CatalogItemName(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "CatalogItemName must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 60
    }
}
```
`CatalogItemUnit.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.content

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class CatalogItemUnit(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "CatalogItemUnit must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 10
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.catalog.content.CatalogItemNameTest" --tests "net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnitTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): CatalogItemId / CatalogItemName（≤60）/ CatalogItemUnit（≤10）"
```

---

## Task 5: Jan VO(EAN-13 チェックディジット)

13 桁数字 + EAN-13 チェックディジット検証。計算ロジックなのでテストする。**有効な JAN は実際にチェックディジットを計算した値**を使う(`4901234567894` は妥当、末尾を変えた `4901234567890` は不正)。

EAN-13 アルゴリズム: 左から 1..12 桁目に重み(奇数位置=1・偶数位置=3)を掛けて合計し、`check = (10 - sum % 10) % 10`。これが 13 桁目と一致すれば妥当。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/barcode/Jan.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/barcode/JanTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`JanTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.barcode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class JanTest {
    @Test
    fun accepts_valid_ean13() {
        Jan("4901234567894").invoke() shouldBe "4901234567894"
    }

    @Test
    fun rejects_wrong_check_digit() {
        shouldThrow<IllegalArgumentException> { Jan("4901234567890") }
    }

    @Test
    fun rejects_non_13_length() {
        shouldThrow<IllegalArgumentException> { Jan("490123456789") }
    }

    @Test
    fun rejects_non_digits() {
        shouldThrow<IllegalArgumentException> { Jan("49012345678AB") }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.catalog.barcode.JanTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Jan.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.barcode

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Jan(private val value: String) {
    init {
        require(value.length == LENGTH && value.all { it.isDigit() }) {
            "Jan must be $LENGTH digits: '$value'"
        }
        require(hasValidCheckDigit(value)) {
            "Jan has invalid EAN-13 check digit: '$value'"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val LENGTH = 13

        private fun hasValidCheckDigit(value: String): Boolean {
            val digits = value.map { it - '0' }
            val sum =
                digits.take(LENGTH - 1).mapIndexed { i, d ->
                    if (i % 2 == 0) d else d * 3
                }.sum()
            val check = (10 - sum % 10) % 10
            return check == digits[LENGTH - 1]
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.catalog.barcode.JanTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Jan VO（13 桁・EAN-13 チェックディジット検証）"
```

---

## Task 6: Barcode(sealed)+ CatalogOrigin(区分)

`Barcode` は JAN 任意(nullable を使わず sealed で `Unlinked` / `Linked`)。`CatalogOrigin` は素性の出所区分。どちらも保持/列挙のみなので**テスト無し**。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/barcode/Barcode.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/origin/CatalogOrigin.kt`

- [ ] **Step 1: 実装を書く**

`Barcode.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.barcode

import kotlinx.serialization.Serializable

@Serializable
sealed interface Barcode {
    @Serializable
    data object Unlinked : Barcode

    @Serializable
    data class Linked(val jan: Jan) : Barcode
}
```
`CatalogOrigin.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.origin

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogOrigin {
    大元マスタ,
    外部取得,
    世帯独自,
}
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Barcode（Unlinked/Linked）/ CatalogOrigin 区分"
```

---

## Task 7: CatalogContent + CatalogItem(素性の集約)

名前 + 推奨単位の塊 `CatalogContent` と、素性集約 `CatalogItem`。保持のみなので**テスト無し**。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogContent.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItem.kt`

- [ ] **Step 1: 実装を書く**

`CatalogContent.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.content

import kotlinx.serialization.Serializable

@Serializable
data class CatalogContent(val name: CatalogItemName, val defaultUnit: CatalogItemUnit)
```
`CatalogItem.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin

@Serializable
data class CatalogItem(
    val id: CatalogItemId,
    val content: CatalogContent,
    val barcode: Barcode,
    val origin: CatalogOrigin,
)
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): CatalogContent / CatalogItem（素性集約）"
```

---

## Task 8: ProductId + ProductUnit

`ProductUnit`(世帯固有の数える単位・≤10)の値域をテストする。`ProductId` は生成のみ(テスト無し)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/ProductUnit.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/ProductUnitTest.kt`

- [ ] **Step 1: 失敗するテストを書く(ProductUnit の値域のみ)**

`ProductUnitTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProductUnitTest {
    @Test
    fun trims_and_accepts() {
        ProductUnit("  個  ").invoke() shouldBe "個"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { ProductUnit("  ") }
    }

    @Test
    fun rejects_over_10_chars() {
        shouldThrow<IllegalArgumentException> { ProductUnit("あ".repeat(11)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnitTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`ProductId.kt`:
```kotlin
@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.inventory.product

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ProductId(private val value: Uuid) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): ProductId = ProductId(Uuid.generateV7())
    }
}
```
`ProductUnit.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ProductUnit(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "ProductUnit must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 10
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnitTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): ProductId / ProductUnit（≤10）"
```

---

## Task 9: MinimumStock(判定 VO)

`isBelow`(在庫が最低在庫以下か)・`shortage`(不足数)の判定をテストする。spec 規則: `StockStatus.of` は `current <= 0 → 在庫切れ` / `minimum.isBelow(current) → 残りわずか`(= `current <= minimum`)/ それ以外 → 十分。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/MinimumStock.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/MinimumStockTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`MinimumStockTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.setting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MinimumStockTest {
    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { MinimumStock(-1) }
    }

    @Test
    fun isBelow_is_true_when_current_at_or_under_minimum() {
        MinimumStock(3).isBelow(3) shouldBe true
        MinimumStock(3).isBelow(2) shouldBe true
    }

    @Test
    fun isBelow_is_false_when_current_above_minimum() {
        MinimumStock(3).isBelow(4) shouldBe false
    }

    @Test
    fun shortage_is_gap_to_minimum_clamped_at_zero() {
        MinimumStock(3).shortage(1) shouldBe 2
        MinimumStock(3).shortage(5) shouldBe 0
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStockTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`MinimumStock.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MinimumStock(private val value: Int) {
    init {
        require(value >= 0) { "MinimumStock must be >= 0: $value" }
    }

    fun isBelow(current: Int): Boolean = current <= value

    fun shortage(current: Int): Int = (value - current).coerceAtLeast(0)

    internal operator fun invoke(): Int = value

    override fun toString(): String = value.toString()
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStockTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): MinimumStock（isBelow/shortage 判定）"
```

---

## Task 10: StockingPolicy + ProductImage + ProductStatus

世帯固有設定 `StockingPolicy`(単位 + 最低在庫)、画像 `ProductImage`(sealed)、状態区分 `ProductStatus`。保持/列挙のみなので**テスト無し**。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/StockingPolicy.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ProductImage.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductStatus.kt`

- [ ] **Step 1: 実装を書く**

`StockingPolicy.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlinx.serialization.Serializable

@Serializable
data class StockingPolicy(val unit: ProductUnit, val minimumStock: MinimumStock)
```
`ProductImage.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product.image

import kotlinx.serialization.Serializable

@Serializable
sealed interface ProductImage {
    @Serializable
    data object None : ProductImage

    @Serializable
    data class Stored(val ref: ImageRef) : ProductImage
}
```
`ProductStatus.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
enum class ProductStatus {
    採用中,
    アーカイブ済,
    ;

    fun isアーカイブ済(): Boolean = this == アーカイブ済
}
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): StockingPolicy / ProductImage / ProductStatus"
```

---

## Task 11: Product(世帯の採用集約)

素性 + 世帯固有設定 + 画像 + 状態。`archive()` / `unarchive()` は状態を変える不変更新(単純な状態差し替えなので**テスト無し**。アーカイブ可否の前提崩れ検証は在庫数量を持つ `Stock` 側の責務で Task 16 でテストする)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Product.kt`

- [ ] **Step 1: 実装を書く**

`Product.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy

@Serializable
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val setting: StockingPolicy,
    val image: ProductImage,
    val status: ProductStatus,
) {
    fun archive(): Product = Product(id, catalogItem, setting, image, ProductStatus.アーカイブ済)

    fun unarchive(): Product = Product(id, catalogItem, setting, image, ProductStatus.採用中)
}
```

- [ ] **Step 2: コンパイルが通ることを確認**

Run: `./gradlew :domain:compileKotlinJvm --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Product（採用集約・archive/unarchive）"
```

---

## Task 12: StockStatus(区分)

在庫状況を `current`(現在数量)と `minimum`(最低在庫)から算出する区分。判定なのでテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockStatus.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockStatusTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`StockStatusTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import kotlin.test.Test

class StockStatusTest {
    @Test
    fun zero_or_less_is_out_of_stock() {
        StockStatus.of(0, MinimumStock(3)) shouldBe StockStatus.在庫切れ
    }

    @Test
    fun at_or_under_minimum_is_low() {
        StockStatus.of(3, MinimumStock(3)) shouldBe StockStatus.残りわずか
        StockStatus.of(1, MinimumStock(3)) shouldBe StockStatus.残りわずか
    }

    @Test
    fun above_minimum_is_enough() {
        StockStatus.of(4, MinimumStock(3)) shouldBe StockStatus.十分
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.StockStatusTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`StockStatus.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock

enum class StockStatus {
    在庫切れ,
    残りわずか,
    十分,
    ;

    companion object {
        fun of(
            current: Int,
            minimum: MinimumStock,
        ): StockStatus =
            when {
                current <= 0 -> 在庫切れ
                minimum.isBelow(current) -> 残りわずか
                else -> 十分
            }
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.StockStatusTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): StockStatus 区分（在庫切れ/残りわずか/十分）"
```

---

## Task 13: Archivability(区分)

在庫 0 のときだけアーカイブ可能、という判定区分。テストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Archivability.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/ArchivabilityTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ArchivabilityTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ArchivabilityTest {
    @Test
    fun zero_stock_is_archivable() {
        Archivability.of(0).archivable shouldBe true
    }

    @Test
    fun nonzero_stock_is_not_archivable() {
        Archivability.of(1).archivable shouldBe false
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.ArchivabilityTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Archivability.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

enum class Archivability(
    val archivable: Boolean,
) {
    可能(true),
    在庫あり(false),
    ;

    companion object {
        fun of(currentQuantity: Int): Archivability = if (currentQuantity == 0) 可能 else 在庫あり
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.ArchivabilityTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Archivability 区分（在庫 0 のみ可能）"
```

---

## Task 14: MovementId + Reason + MovementIdentity(在庫変動の identity VO)

`MovementId`(永続化済み movement の Long identity・非負)と `Reason`(訂正理由・非空≤255)の値域をテストする。`MovementIdentity`(sealed・未採番 `Pending` / 採番済 `Persisted`)は保持のみ(テスト無し)。設計判断1のとおり、訂正対象は常に `Persisted`、新規 movement は `Pending`。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/MovementId.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/Reason.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/MovementIdentity.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/MovementIdTest.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/ReasonTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`MovementIdTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MovementIdTest {
    @Test
    fun accepts_zero_and_positive() {
        MovementId(0).invoke() shouldBe 0L
        MovementId(42).invoke() shouldBe 42L
    }

    @Test
    fun rejects_negative() {
        shouldThrow<IllegalArgumentException> { MovementId(-1) }
    }
}
```
`ReasonTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReasonTest {
    @Test
    fun trims_and_accepts() {
        Reason("  数え直し  ").invoke() shouldBe "数え直し"
    }

    @Test
    fun rejects_blank() {
        shouldThrow<IllegalArgumentException> { Reason("  ") }
    }

    @Test
    fun rejects_over_255_chars() {
        shouldThrow<IllegalArgumentException> { Reason("あ".repeat(256)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdTest" --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.ReasonTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`MovementId.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MovementId(private val value: Long) {
    init {
        require(value >= 0) { "MovementId must be >= 0: $value" }
    }

    internal operator fun invoke(): Long = value

    override fun toString(): String = value.toString()
}
```
`Reason.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Reason(private val value: String) {
    init {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) {
            "Reason must be 1..$MAX_LENGTH chars after trim: '$value'"
        }
    }

    internal operator fun invoke(): String = value.trim()

    override fun toString(): String = value.trim()

    companion object {
        const val MAX_LENGTH = 255
    }
}
```
`MovementIdentity.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable

@Serializable
sealed interface MovementIdentity {
    @Serializable
    data object Pending : MovementIdentity

    @Serializable
    data class Persisted(val id: MovementId) : MovementIdentity
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdTest" --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.ReasonTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): MovementId（≥0）/ Reason（≤255）/ MovementIdentity（Pending/Persisted）"
```

---

## Task 15: StockMovement(sealed)+ StockMovements(訂正畳み込み)

`StockMovement` は append-only な在庫変動の事実。共通フィールドに `identity: MovementIdentity` を持つ sealed `Replenishment | Consumption | Correction`。`actor` は P1 の `Resident` を埋め込む。`Correction` は `target: MovementId` + `reason: Reason` を追加。`StockMovements.netQuantity()` は**畳み込み**を行う(設計判断1の規則): 同一 target への最新 `Correction`(`occurredAt` 最大)が base movement の数量を上書き(符号は base の種別を継承)、`Correction` 自身は直接加算しない。計算ロジックなので畳み込みをテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovement.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovements.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovementsTest.kt`

- [ ] **Step 1: 失敗するテストを書く(netQuantity の畳み込み)**

`StockMovementsTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test
import kotlin.time.Instant

class StockMovementsTest {
    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun at(epochSeconds: Long) = OccurredAt(Instant.fromEpochSeconds(epochSeconds))

    private fun persisted(id: Long) = MovementIdentity.Persisted(MovementId(id))

    private fun replenish(id: Long, n: Int) = Replenishment(persisted(id), Quantity(n), at(0), actor(), Note(""))

    private fun consume(id: Long, n: Int) = Consumption(persisted(id), Quantity(n), at(0), actor(), Note(""))

    private fun correction(target: Long, n: Int, atSeconds: Long) =
        Correction(MovementIdentity.Pending, Quantity(n), at(atSeconds), actor(), Note(""), MovementId(target), Reason("数え直し"))

    @Test
    fun empty_is_zero() {
        StockMovements(emptyList()).netQuantity() shouldBe 0
    }

    @Test
    fun sums_base_without_corrections() {
        // +2 +3 -1 = 4
        StockMovements(listOf(replenish(1, 2), replenish(2, 3), consume(3, 1))).netQuantity() shouldBe 4
    }

    @Test
    fun correction_overwrites_consumption_keeping_minus_sign() {
        // 補充2 +(消費1 → 訂正で2) = +2 - 2 = 0
        val movements = StockMovements(listOf(replenish(1, 2), consume(2, 1), correction(target = 2, n = 2, atSeconds = 100)))
        movements.netQuantity() shouldBe 0
    }

    @Test
    fun correction_overwrites_replenishment_keeping_plus_sign() {
        // (補充5 → 訂正で2)+ 消費1 = +2 - 1 = 1（base が Replenishment の + 符号を継承）
        val movements = StockMovements(listOf(replenish(1, 5), consume(2, 1), correction(target = 1, n = 2, atSeconds = 100)))
        movements.netQuantity() shouldBe 1
    }

    @Test
    fun latest_correction_wins() {
        // 補充10、消費1(id=2)を 3→5 と二度訂正 → 最新 5 を採用 → 10 - 5 = 5
        val movements =
            StockMovements(
                listOf(
                    replenish(1, 10),
                    consume(2, 1),
                    correction(target = 2, n = 3, atSeconds = 100),
                    correction(target = 2, n = 5, atSeconds = 200),
                ),
            )
        movements.netQuantity() shouldBe 5
    }

    @Test
    fun size_counts_all_movements() {
        StockMovements(listOf(replenish(1, 2), consume(2, 1))).size() shouldBe 2
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovementsTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`StockMovement.kt`(variant は**トップレベル**。テストは `Replenishment` / `Consumption` / `Correction` をトップレベルで import する):
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.resident.Resident

@Serializable
sealed interface StockMovement {
    val identity: MovementIdentity
    val quantity: Quantity
    val occurredAt: OccurredAt
    val actor: Resident
    val note: Note
}

@Serializable
data class Replenishment(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
) : StockMovement

@Serializable
data class Consumption(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
) : StockMovement

@Serializable
data class Correction(
    override val identity: MovementIdentity,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Resident,
    override val note: Note,
    val target: MovementId,
    val reason: Reason,
) : StockMovement
```
`StockMovements.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock.movement

import kotlinx.serialization.Serializable

@Serializable
data class StockMovements(val list: List<StockMovement>) {
    fun size(): Int = list.size

    fun add(movement: StockMovement): StockMovements = StockMovements(list + movement)

    fun netQuantity(): Int {
        val latestCorrection: Map<MovementId, Correction> =
            list.filterIsInstance<Correction>()
                .groupBy { it.target }
                .mapValues { (_, corrections) -> corrections.maxBy { it.occurredAt() } }
        return list.sumOf { movement ->
            when (movement) {
                is Replenishment -> effectiveQuantity(movement, latestCorrection)
                is Consumption -> -effectiveQuantity(movement, latestCorrection)
                is Correction -> 0
            }
        }
    }

    private fun effectiveQuantity(
        base: StockMovement,
        latestCorrection: Map<MovementId, Correction>,
    ): Int {
        val id = (base.identity as? MovementIdentity.Persisted)?.id
        val correction = id?.let { latestCorrection[it] }
        return correction?.quantity?.invoke() ?: base.quantity()
    }
}
```
> 注: `it.occurredAt()` / `base.quantity()` は `OccurredAt.invoke()` / `Quantity.invoke()`(同一モジュール `internal`)。`when` は sealed `StockMovement` の 3 variant を網羅。訂正対象は必ず `Persisted`、新規/Pending base は訂正対象にならないので own quantity になる。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovementsTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): StockMovement（補充/消費/訂正）/ StockMovements（訂正畳み込み netQuantity）"
```

---

## Task 16: Stock 集約(replenish / consume / correct / archive / unarchive)

数量・台帳のルート集約。`consume` の在庫不足(`InsufficientStockException`)、`correct` の訂正(対象不在は `ResourceNotFoundException`・総量が負は `InsufficientStockException`)、`archive` の在庫あり拒否(`CannotArchiveWithStockException`)がビジネスロジックなのでテストする。`currentQuantity` / `status` は委譲。手動希望(`want`/`unwant`)は domain に持たない(設計判断2)。`replenish`/`consume`/`correct` が作る新規 movement は `MovementIdentity.Pending`(設計判断1)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`StockTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class StockTest {
    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun product(minimum: Int = 1) =
        Product(
            id = ProductId.create(),
            catalogItem =
                CatalogItem(
                    id = CatalogItemId.create(),
                    content = CatalogContent(CatalogItemName("米"), CatalogItemUnit("袋")),
                    barcode = Barcode.Unlinked,
                    origin = CatalogOrigin.世帯独自,
                ),
            setting = StockingPolicy(ProductUnit("袋"), MinimumStock(minimum)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )

    private fun emptyStock(minimum: Int = 1) = Stock(product(minimum), StockMovements(emptyList()))

    // 訂正テスト用に「永続化済み(Persisted)movement」を直接組んだ Stock を作る
    private fun persistedReplenishment(id: Long, n: Int) =
        Replenishment(MovementIdentity.Persisted(MovementId(id)), Quantity(n), OccurredAt.now(), actor(), Note(""))

    private fun persistedConsumption(id: Long, n: Int) =
        Consumption(MovementIdentity.Persisted(MovementId(id)), Quantity(n), OccurredAt.now(), actor(), Note(""))

    private fun stockWith(
        vararg movements: StockMovement,
        minimum: Int = 1,
    ) = Stock(product(minimum), StockMovements(movements.toList()))

    @Test
    fun replenish_then_consume_tracks_quantity() {
        val stock =
            emptyStock()
                .replenish(Quantity(5), OccurredAt.now(), actor(), Note(""))
                .consume(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.currentQuantity() shouldBe 3
    }

    @Test
    fun consume_beyond_stock_is_rejected() {
        val stock = emptyStock().replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        shouldThrow<InsufficientStockException> {
            stock.consume(Quantity(3), OccurredAt.now(), actor(), Note(""))
        }
    }

    @Test
    fun status_reflects_current_quantity() {
        val stock = emptyStock(minimum = 3).replenish(Quantity(2), OccurredAt.now(), actor(), Note(""))
        stock.status() shouldBe StockStatus.残りわずか
    }

    @Test
    fun archive_is_rejected_when_stock_remains() {
        val stock = emptyStock().replenish(Quantity(1), OccurredAt.now(), actor(), Note(""))
        shouldThrow<CannotArchiveWithStockException> { stock.archive() }
    }

    @Test
    fun archive_succeeds_when_empty() {
        emptyStock().archive().product.status shouldBe ProductStatus.アーカイブ済
    }

    @Test
    fun correct_overwrites_target_quantity() {
        // 補充5、消費3(id=10)→ 現在2。消費を1に訂正 → +5 - 1 = 4
        val stock = stockWith(persistedReplenishment(1, 5), persistedConsumption(10, 3))
        stock.currentQuantity() shouldBe 2
        val corrected = stock.correct(MovementId(10), Quantity(1), Reason("数え直し"), actor(), OccurredAt.now())
        corrected.currentQuantity() shouldBe 4
    }

    @Test
    fun correct_to_negative_total_is_rejected() {
        // 補充2、消費2(id=10)→ 現在0。消費を5に訂正 → +2 - 5 = -3 で拒否
        val stock = stockWith(persistedReplenishment(1, 2), persistedConsumption(10, 2))
        shouldThrow<InsufficientStockException> {
            stock.correct(MovementId(10), Quantity(5), Reason("入れ過ぎ"), actor(), OccurredAt.now())
        }
    }

    @Test
    fun correct_unknown_target_is_not_found() {
        val stock = stockWith(persistedReplenishment(1, 2))
        shouldThrow<ResourceNotFoundException> {
            stock.correct(MovementId(999), Quantity(1), Reason("対象なし"), actor(), OccurredAt.now())
        }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.StockTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`Stock.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Correction
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident

@Serializable
data class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun status(): StockStatus = StockStatus.of(currentQuantity(), product.setting.minimumStock)

    fun replenish(
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Resident,
        note: Note,
    ): Stock = Stock(product, movements.add(Replenishment(MovementIdentity.Pending, quantity, occurredAt, actor, note)))

    fun consume(
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Resident,
        note: Note,
    ): Stock {
        if (currentQuantity() < quantity()) {
            throw InsufficientStockException("cannot consume $quantity from stock of ${currentQuantity()}")
        }
        return Stock(product, movements.add(Consumption(MovementIdentity.Pending, quantity, occurredAt, actor, note)))
    }

    fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
        actor: Resident,
        occurredAt: OccurredAt,
    ): Stock {
        val targetExists =
            movements.list.any {
                (it is Replenishment || it is Consumption) && it.identity == MovementIdentity.Persisted(target)
            }
        if (!targetExists) {
            throw ResourceNotFoundException("movement not found: $target")
        }
        val corrected =
            movements.add(
                Correction(
                    identity = MovementIdentity.Pending,
                    quantity = correctedQuantity,
                    occurredAt = occurredAt,
                    actor = actor,
                    note = Note(""),
                    target = target,
                    reason = reason,
                ),
            )
        if (corrected.netQuantity() < 0) {
            throw InsufficientStockException("correction would make stock negative: ${corrected.netQuantity()}")
        }
        return Stock(product, corrected)
    }

    fun archive(): Stock {
        if (!Archivability.of(currentQuantity()).archivable) {
            throw CannotArchiveWithStockException("cannot archive with stock: ${currentQuantity()}")
        }
        return Stock(product.archive(), movements)
    }

    fun unarchive(): Stock = Stock(product.unarchive(), movements)
}
```
> 注: `quantity()` は `Quantity.invoke()`(同一モジュール `internal`)で Int を取り出す。**集約の不変更新は `.copy()` ではなくコンストラクタ明示構築**([immutable-construction](../../../.claude/rules/immutable-construction.md))。`correct` は対象が `Persisted(target)` の base movement として存在しなければ `ResourceNotFoundException`、訂正後の総量が負なら `InsufficientStockException`。`Correction.note` は訂正コマンドに note 引数が無いため空(理由は `reason` が持つ)。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.stock.StockTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): Stock 集約（replenish/consume/correct/archive/unarchive・訂正畳み込み）"
```

---

## Task 17: ShoppingNeed(区分)

買い物リストの必要性を `status`(在庫状況)と `manuallyWanted`(手動希望・Stock 外から合成)から判定する区分。判定ロジックなのでテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingNeed.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingNeedTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ShoppingNeedTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus
import kotlin.test.Test

class ShoppingNeedTest {
    @Test
    fun insufficient_stock_needs_shopping() {
        ShoppingNeed.judge(StockStatus.在庫切れ, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
        ShoppingNeed.judge(StockStatus.残りわずか, manuallyWanted = false) shouldBe ShoppingNeed.在庫不足
    }

    @Test
    fun enough_but_manually_wanted() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = true) shouldBe ShoppingNeed.手動希望
    }

    @Test
    fun enough_and_not_wanted_is_unnecessary() {
        ShoppingNeed.judge(StockStatus.十分, manuallyWanted = false) shouldBe ShoppingNeed.不要
    }

    @Test
    fun onShoppingList_flag() {
        ShoppingNeed.在庫不足.onShoppingList shouldBe true
        ShoppingNeed.手動希望.onShoppingList shouldBe true
        ShoppingNeed.不要.onShoppingList shouldBe false
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingNeedTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`ShoppingNeed.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.shopping

import net.brightroom.mindstock.domain.model.inventory.stock.StockStatus

enum class ShoppingNeed(
    val onShoppingList: Boolean,
) {
    在庫不足(true),
    手動希望(true),
    不要(false),
    ;

    companion object {
        fun judge(
            status: StockStatus,
            manuallyWanted: Boolean,
        ): ShoppingNeed =
            when {
                status != StockStatus.十分 -> 在庫不足
                manuallyWanted -> 手動希望
                else -> 不要
            }
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingNeedTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): ShoppingNeed 区分（在庫不足/手動希望/不要）"
```

---

## Task 18: ShoppingEntry + ShoppingList(派生 read-model)

買い物リストは永続集約を持たない派生ビュー。各エントリは `Stock` + `manuallyWanted`(手動希望は Stock 外から与える=設計判断2)の合成。`autoItems` / `manualItems` の抽出をテストする。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingList.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingListTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ShoppingListTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.shopping

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

class ShoppingListTest {
    private fun actor() = Resident(ResidentId.create(), Profile(DisplayName("おや")))

    private fun stock(
        name: String,
        minimum: Int,
        quantity: Int,
    ): Stock {
        val product =
            Product(
                id = ProductId.create(),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId.create(),
                        content = CatalogContent(CatalogItemName(name), CatalogItemUnit("個")),
                        barcode = Barcode.Unlinked,
                        origin = CatalogOrigin.世帯独自,
                    ),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(minimum)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val base = Stock(product, StockMovements(emptyList()))
        return if (quantity > 0) base.replenish(Quantity(quantity), OccurredAt.now(), actor(), Note("")) else base
    }

    @Test
    fun partitions_auto_and_manual_items() {
        val shortage = ShoppingEntry(stock("米", minimum = 3, quantity = 1), manuallyWanted = false) // 在庫不足
        val manual = ShoppingEntry(stock("醤油", minimum = 1, quantity = 5), manuallyWanted = true) // 十分だが手動
        val list = ShoppingList(listOf(shortage, manual))

        list.autoItems().list shouldBe listOf(shortage)
        list.manualItems().list shouldBe listOf(manual)
        list.size() shouldBe 2
    }

    @Test
    fun entry_need_and_onList() {
        val entry = ShoppingEntry(stock("米", minimum = 3, quantity = 1), manuallyWanted = false)
        entry.need() shouldBe ShoppingNeed.在庫不足
        entry.onList() shouldBe true
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingListTest" --console=plain`
Expected: FAIL

- [ ] **Step 3: 実装を書く**

`ShoppingList.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.stock.Stock

@Serializable
data class ShoppingEntry(
    val stock: Stock,
    val manuallyWanted: Boolean,
) {
    fun need(): ShoppingNeed = ShoppingNeed.judge(stock.status(), manuallyWanted)

    fun onList(): Boolean = need().onShoppingList
}

@Serializable
data class ShoppingList(val list: List<ShoppingEntry>) {
    fun size(): Int = list.size

    fun autoItems(): ShoppingList = ShoppingList(list.filter { it.need() == ShoppingNeed.在庫不足 })

    fun manualItems(): ShoppingList = ShoppingList(list.filter { it.need() == ShoppingNeed.手動希望 })
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingListTest" --console=plain`
Expected: PASS

- [ ] **Step 5: 整形してコミット**

```bash
./gradlew :domain:spotlessApply
git add domain/src
git commit -m "feat(domain): ShoppingEntry / ShoppingList（派生 read-model）"
```

---

## Task 19: モジュール全体の緑を確認

- [ ] **Step 1: 全テスト + lint を含むフルビルド**

Run: `./gradlew :domain:build --console=plain`
Expected: `BUILD SUCCESSFUL`(全 commonTest が JVM/JS/Wasm で緑、spotless 緑)
- もし JS/Wasm で `JvmInline` 未解決エラーが出たら、当該 value class ファイルに `import kotlin.jvm.JvmInline` が抜けていないか確認(KMP gotcha)。`OccurredAt` の `kotlin.time.Instant` 直列化は JVM compile で確認済(opt-in 不要)。

- [ ] **Step 2: 差分が無ければスキップ、あればコミット**

```bash
git status --short
# 差分があれば:
git add -A
git commit -m "chore(domain): P2（catalog/inventory）ドメインのビルド緑化を確認"
```

---

## 完了条件(Definition of Done)

- catalog(`CatalogItem` / `CatalogContent` / `CatalogItemId` / `CatalogItemName` / `CatalogItemUnit` / `Jan` / `Barcode` / `CatalogOrigin`)実装。`Jan` は EAN-13 検証。
- inventory/product(`Product` / `ProductId` / `StockingPolicy` / `ProductUnit` / `MinimumStock` / `ProductImage` / `ImageRef` / `ProductStatus`)実装。アーカイブ可否は `Stock` 側で判定。
- inventory/stock(`Stock` / `StockMovement`(`Replenishment`/`Consumption`/`Correction`)/ `StockMovements` / `MovementId` / `Reason` / `MovementIdentity` / `Quantity` / `OccurredAt` / `Note` / `StockStatus` / `Archivability`)実装。`consume` 在庫不足と `correct` の負総量は `InsufficientStockException`、`correct` の対象不在は `ResourceNotFoundException`、`archive` 在庫ありは `CannotArchiveWithStockException`。`netQuantity` は訂正畳み込み(最新訂正が base 数量を上書き)。
- inventory/shopping(`ShoppingNeed` / `ShoppingEntry` / `ShoppingList`)実装。手動希望は read-model の合成入力。
- 例外 3 種(`InsufficientStockException` / `CannotArchiveWithStockException` / `DuplicateJanException`)定義済み。
- **設計判断どおり**: `correct()` / `MovementId` / `Reason` / `Correction` はドメインにある(identity は `MovementIdentity` の sealed で表現)。`Stock.manualWanted` は **存在しない**(手動希望は read-model 合成入力、永続化は P4)。
- **テストは意味のあるもの(値域・判定・計算・抽出・前提崩れ)のみ**。保持/アクセサ/equals のテストは無い。
- `Uuid` 使用ファイルに `@file:OptIn(ExperimentalUuidApi::class)`、`@JvmInline` 各ファイルに `import kotlin.jvm.JvmInline`。
- `./gradlew :domain:build` が緑。
