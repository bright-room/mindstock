# P2 ドメイン改修(Product / CatalogItem 分離)実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Product` を catalog 非依存・自己完結にし、`CatalogItem` を barcode lookup の軽量集約へ縮小する(P4 の前段ドメイン改修)。

**Architecture:** `:domain` で `Barcode`/`Jan` を中立パッケージへ移設 → `ProductName` 新設 → `CatalogItem`/`Product` を改修(不要 VO 削除)→ `:rpc` の DTO・round-trip テストを追従。各タスクで対象モジュールの build を緑に保つ。

**Tech Stack:** Kotlin Multiplatform(commonMain/commonTest)、kotlinx-serialization、Exposed は本改修では非関与。テストは `kotlin.test.@Test` + Kotest assertions(commonTest は FunSpec 不可)、テスト関数名は日本語。

**準拠ルール:** `domain-guideline` / `immutable-construction`(`copy()` 禁止・明示コンストラクタ)/ `one-class-per-file` / `error-handling` / `testing`(意味のあるテストのみ)。

**spec:** `docs/superpowers/specs/2026-06-02-p2-domain-reshape-product-catalog-design.md`

**ビルド注意(local-build-tips):** frontend WasmJs は OOM るので本改修の検証では触らない。検証は `:domain` / `:rpc` に限定。

---

## ファイル構成(改修後)

- 移設: `domain/.../model/catalog/barcode/{Barcode,Jan}.kt` → `domain/.../model/barcode/{Barcode,Jan}.kt`(package `net.brightroom.mindstock.domain.model.barcode`)
- 新設: `domain/.../model/inventory/product/ProductName.kt`
- 改修: `domain/.../model/catalog/item/CatalogItem.kt`、`domain/.../model/inventory/product/Product.kt`
- 削除: `domain/.../model/catalog/content/{CatalogContent,CatalogItemUnit}.kt`、`domain/.../model/catalog/origin/CatalogOrigin.kt`、`domain/.../commonTest/.../catalog/content/CatalogItemUnitTest.kt`
- 据え置き: `CatalogItemName.kt`(catalog/content パッケージのまま)、`StockingPolicy`/`ProductUnit`/`MinimumStock`/`ProductImage`/`ImageRef`/`ProductStatus`
- テスト改修: `StockTest.kt`、`ShoppingListTest.kt`、`JanTest.kt`(移設追従)、`PayloadSerializationTest.kt`、`ActivityFeedSerializationTest.kt`
- `:rpc` 改修: `AddCustomProductRequest.kt`、`CatalogRpcService.kt`(import 追従)

---

## Task 1: Barcode / Jan を中立パッケージへ移設

`Product`(barcode)と `CatalogItem`(jan)双方が参照するため、`catalog/barcode/` から catalog 非依存の `model/barcode/` へ移す。

**Files:**
- Move: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/barcode/Barcode.kt` → `.../model/barcode/Barcode.kt`
- Move: `.../model/catalog/barcode/Jan.kt` → `.../model/barcode/Jan.kt`
- Move: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/barcode/JanTest.kt` → `.../model/barcode/JanTest.kt`
- Modify(import 追従): `.../model/catalog/item/CatalogItem.kt`、`commonTest/.../inventory/stock/StockTest.kt`、`commonTest/.../inventory/shopping/ShoppingListTest.kt`、`rpc/.../catalog/CatalogRpcService.kt`、`rpc/.../product/AddCustomProductRequest.kt`、`rpc/.../commonTest/.../result/PayloadSerializationTest.kt`、`rpc/.../commonTest/.../stock/ActivityFeedSerializationTest.kt`

- [ ] **Step 1: ファイルを git mv で移設**

```bash
cd /Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock
D=domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model
T=domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model
mkdir -p $D/barcode $T/barcode
git mv $D/catalog/barcode/Barcode.kt $D/barcode/Barcode.kt
git mv $D/catalog/barcode/Jan.kt $D/barcode/Jan.kt
git mv $T/catalog/barcode/JanTest.kt $T/barcode/JanTest.kt
```

- [ ] **Step 2: 移設した3ファイルの package 宣言を変更**

`Barcode.kt` / `Jan.kt` / `JanTest.kt` の先頭を:

```kotlin
package net.brightroom.mindstock.domain.model.catalog.barcode
```

→

```kotlin
package net.brightroom.mindstock.domain.model.barcode
```

に変更(3ファイルとも)。

- [ ] **Step 3: import 参照を一括更新**

下記7ファイルの import 文 `net.brightroom.mindstock.domain.model.catalog.barcode.Barcode` / `....catalog.barcode.Jan` を `net.brightroom.mindstock.domain.model.barcode.Barcode` / `....model.barcode.Jan` に置換する。

```bash
grep -rl "domain.model.catalog.barcode" domain/src rpc/src --include="*.kt" \
  | xargs sed -i '' 's/domain\.model\.catalog\.barcode/domain.model.barcode/g'
```

対象(確認用): `CatalogItem.kt` / `StockTest.kt` / `ShoppingListTest.kt` / `CatalogRpcService.kt` / `AddCustomProductRequest.kt` / `PayloadSerializationTest.kt` / `ActivityFeedSerializationTest.kt`。

- [ ] **Step 4: ビルドで移設の妥当性を確認**

Run: `./gradlew :domain:build :rpc:build`
Expected: PASS(移設のみで挙動不変、既存テスト緑)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(domain): Barcode/Jan を catalog 非依存の model/barcode へ移設"
```

---

## Task 2: ProductName VO を新設

`Product` 自前の名前 VO。catalog 非依存なので `CatalogItemName` は使わず別 VO として定義(値域は同一: 非空・最大60・trim 保持)。

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductName.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductNameTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

`ProductNameTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ProductNameTest {
    @Test
    fun 前後の空白をトリムする() {
        ProductName("  牛乳  ").invoke() shouldBe "牛乳"
    }

    @Test
    fun 空文字は拒否する() {
        shouldThrow<IllegalArgumentException> { ProductName("   ") }
    }

    @Test
    fun 最大長を超えると拒否する() {
        shouldThrow<IllegalArgumentException> { ProductName("あ".repeat(ProductName.MAX_LENGTH + 1)) }
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:compileTestKotlinJvm`
Expected: FAIL(`ProductName` 未定義でコンパイルエラー)

- [ ] **Step 3: ProductName を実装**

`ProductName.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProductName private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "ProductName must be 1..$MAX_LENGTH chars after trim"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 60

        operator fun invoke(raw: String): ProductName = ProductName(raw.trim())
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): Product 自前の名前 VO ProductName を新設"
```

---

## Task 3: CatalogItem 縮小 + Product 自己完結化 + 不要 VO 削除 + domain テスト更新

`CatalogItem` を `(id, jan, name)` に縮小、`Product` から `catalogItem` を外し `name`/`barcode` + `adopt`/`custom` ファクトリを持たせる。両者は domain テストで同時に構築されるため 1 タスクで反映し `:domain:build` を緑に戻す。`CatalogItemUnit` は `:rpc` がまだ参照するため Task 4 で削除する。

**Files:**
- Modify: `.../model/catalog/item/CatalogItem.kt`
- Modify: `.../model/inventory/product/Product.kt`
- Delete: `.../model/catalog/content/CatalogContent.kt`、`.../model/catalog/origin/CatalogOrigin.kt`
- Modify(テスト): `commonTest/.../inventory/stock/StockTest.kt`、`commonTest/.../inventory/shopping/ShoppingListTest.kt`
- Test(新規): `commonTest/.../inventory/product/ProductAdoptTest.kt`

- [ ] **Step 1: 失敗するテスト(adopt のコピー挙動)を書く**

`ProductAdoptTest.kt`:

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import kotlin.test.Test

class ProductAdoptTest {
    @Test
    fun catalog の名前とJANをコピーしLinkedバーコードの採用中商品を生成する() {
        val catalogItem =
            CatalogItem(
                id = CatalogItemId.create(),
                jan = Jan("4901234567894"),
                name = CatalogItemName("明治おいしい牛乳"),
            )

        val product = Product.adopt(catalogItem, ProductUnit("本"), MinimumStock(2))

        product.name.invoke() shouldBe "明治おいしい牛乳"
        product.barcode shouldBe Barcode.Linked(Jan("4901234567894"))
        product.status shouldBe ProductStatus.採用中
        product.image shouldBe ProductImage.None
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:compileTestKotlinJvm`
Expected: FAIL(新 `CatalogItem(id, jan, name)` / `Product.adopt` 未対応でコンパイルエラー)

- [ ] **Step 3: CatalogItem を縮小**

`CatalogItem.kt` を全置換:

```kotlin
package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName

@Serializable
data class CatalogItem(
    val id: CatalogItemId,
    val jan: Jan,
    val name: CatalogItemName,
)
```

- [ ] **Step 4: 不要 VO を削除**

```bash
cd /Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock
D=domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model
git rm $D/catalog/content/CatalogContent.kt $D/catalog/origin/CatalogOrigin.kt
```

（`CatalogItemName.kt` は `catalog/content/` に残す。`CatalogItemUnit.kt` は Task 4 まで残す。）

- [ ] **Step 5: Product を自己完結化**

`Product.kt` を全置換:

```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy

@Serializable
data class Product(
    val id: ProductId,
    val name: ProductName,
    val barcode: Barcode,
    val setting: StockingPolicy,
    val image: ProductImage,
    val status: ProductStatus,
) {
    fun archive(): Product = Product(id, name, barcode, setting, image, ProductStatus.アーカイブ済)

    fun unarchive(): Product = Product(id, name, barcode, setting, image, ProductStatus.採用中)

    companion object {
        fun adopt(
            catalogItem: CatalogItem,
            unit: ProductUnit,
            minimumStock: MinimumStock,
        ): Product =
            Product(
                id = ProductId.create(),
                name = ProductName(catalogItem.name()),
                barcode = Barcode.Linked(catalogItem.jan),
                setting = StockingPolicy(unit, minimumStock),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )

        fun custom(
            name: ProductName,
            barcode: Barcode,
            unit: ProductUnit,
            minimumStock: MinimumStock,
        ): Product =
            Product(
                id = ProductId.create(),
                name = name,
                barcode = barcode,
                setting = StockingPolicy(unit, minimumStock),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
    }
}
```

注: `catalogItem.name()` は `CatalogItemName.invoke()`(internal、同一モジュール)で raw String を取り `ProductName` を構築する。

- [ ] **Step 6: StockTest の product ビルダーを新構造へ更新**

`StockTest.kt` の import 群(`catalog.content.CatalogContent` / `catalog.content.CatalogItemName` / `catalog.content.CatalogItemUnit` / `catalog.item.CatalogItem` / `catalog.item.CatalogItemId` / `catalog.origin.CatalogOrigin`)を削除し、`import net.brightroom.mindstock.domain.model.inventory.product.ProductName` を追加。`Barcode` import は Task 1 で `model.barcode.Barcode` 済み。`product(...)` ビルダーを:

```kotlin
    private fun product(minimum: Int = 1) =
        Product(
            id = ProductId.create(),
            name = ProductName("米"),
            barcode = Barcode.Unlinked,
            setting = StockingPolicy(ProductUnit("袋"), MinimumStock(minimum)),
            image = ProductImage.None,
            status = ProductStatus.採用中,
        )
```

に置換。

- [ ] **Step 7: ShoppingListTest の stock ビルダーを新構造へ更新**

`ShoppingListTest.kt` の同様の catalog 系 import を削除し `ProductName` import を追加。`stock(...)` 内の `product` を:

```kotlin
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName(name),
                barcode = Barcode.Unlinked,
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(minimum)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
```

に置換(`name: String` 引数はそのまま `ProductName(name)` に渡す)。

- [ ] **Step 8: `:domain:build` を緑に**

Run: `./gradlew :domain:build`
Expected: PASS(`ProductAdoptTest` / `ProductNameTest` / 既存テスト緑)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(domain): Product を catalog 非依存に・CatalogItem を縮小(CatalogContent/CatalogOrigin 削除)"
```

---

## Task 4: :rpc 追従(DTO 型変更・CatalogItemUnit 削除・round-trip テスト更新)

`:domain` の新構造に `:rpc` を合わせ、未使用になる `CatalogItemUnit` を削除する。

**Files:**
- Modify: `rpc/.../product/AddCustomProductRequest.kt`
- Delete: `domain/.../model/catalog/content/CatalogItemUnit.kt`、`domain/.../commonTest/.../catalog/content/CatalogItemUnitTest.kt`
- Modify(テスト): `rpc/.../commonTest/.../result/PayloadSerializationTest.kt`、`rpc/.../commonTest/.../stock/ActivityFeedSerializationTest.kt`
- 確認のみ: `rpc/.../catalog/CatalogRpcService.kt`(Task 1 で Jan import 済み、変更不要)

- [ ] **Step 1: AddCustomProductRequest の型を変更**

`AddCustomProductRequest.kt` を全置換:

```kotlin
package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

/**
 * マスタに無い商品をその場で追加(UC13)。複合パラメータを 1 つにまとめた Request。
 * `barcode` で JAN 任意を表現(`Barcode.Unlinked` = JAN 無し / `Barcode.Linked(jan)` = JAN 有り)。
 */
@Serializable
data class AddCustomProductRequest(
    val name: ProductName,
    val unit: ProductUnit,
    val barcode: Barcode,
    val minimumStock: MinimumStock,
)
```

- [ ] **Step 2: CatalogItemUnit とそのテストを削除**

```bash
cd /Users/nonaka.koki/dev/ghq/github.com/bright-room/mindstock
git rm domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemUnit.kt
git rm domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemUnitTest.kt
```

- [ ] **Step 3: PayloadSerializationTest を新構造へ更新**

`PayloadSerializationTest.kt` の catalog 系 import を整理(`model.barcode.Barcode`/`model.barcode.Jan` は維持、`catalog.content.CatalogContent`/`CatalogItemUnit`/`catalog.origin.CatalogOrigin` を削除、`inventory.product.ProductName` を追加、`catalog.content.CatalogItemName`・`catalog.item.*` は不要なら削除)。`product` 生成を:

```kotlin
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("洗剤"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(1)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
```

に置換。

- [ ] **Step 4: ActivityFeedSerializationTest を新構造へ更新**

`ActivityFeedSerializationTest.kt` の 2 つの `product` 生成(`シャンプー`・`牛乳`)を新構造へ置換し、import を同様に整理。例(1 つ目):

```kotlin
        val product =
            Product(
                id = ProductId.create(),
                name = ProductName("シャンプー"),
                barcode = Barcode.Linked(Jan("4901234567894")),
                setting = StockingPolicy(ProductUnit("本"), MinimumStock(2)),
                image = ProductImage.Stored(ImageRef("https://example.com/images/shampoo.jpg")),
                status = ProductStatus.採用中,
            )
```

2 つ目(`牛乳`)も同様に `name = ProductName("牛乳")` / `barcode = Barcode.Linked(Jan("4901234567894"))` / `setting = StockingPolicy(ProductUnit("本"), MinimumStock(1))` / `image = ProductImage.None` で置換。`ActivityEntry(product = product, movement = movement)` 構造は不変。

- [ ] **Step 5: `:rpc:build` を緑に(:domain も推移的にビルド)**

Run: `./gradlew :rpc:build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(rpc): Product/CatalogItem 改修に追従(DTO 型変更・CatalogItemUnit 削除・round-trip 更新)"
```

---

## Task 5: 全体検証

- [ ] **Step 1: domain と rpc を通しでビルド**

Run: `./gradlew :domain:build :rpc:build`
Expected: PASS(全 KMP ターゲット緑)

- [ ] **Step 2: 残存参照が無いことを確認**

Run:
```bash
grep -rn "CatalogContent\|CatalogItemUnit\|CatalogOrigin\|model.catalog.barcode" domain/src rpc/src --include="*.kt"
```
Expected: 出力なし(全て削除/移設済み)

- [ ] **Step 3(問題なければ): 完了**

追加コミット不要(各タスクでコミット済み)。

---

## Self-Review(作成者チェック結果)

- **spec カバレッジ**: CatalogItem 縮小=Task3 / Product 自己完結+adopt+custom=Task3 / ProductName 新設=Task2 / Barcode・Jan 移設=Task1 / CatalogContent・CatalogItemUnit・CatalogOrigin 廃止=Task3,4 / :rpc DTO 変更=Task4 / round-trip 更新=Task4。origin は domain から除外(read-model 導出は P5、本プラン対象外)で spec と一致。
- **プレースホルダ**: なし(全コード明示)。
- **型整合**: `ProductName`(MAX_LENGTH=60)/ `CatalogItem(id, jan, name)` / `Product(id, name, barcode, setting, image, status)` / `Product.adopt(catalogItem, unit, minimumStock)` / `Product.custom(name, barcode, unit, minimumStock)` は全タスクで一貫。`catalogItem.name()`=`CatalogItemName.invoke()` の internal 利用も同一モジュール内で整合。
- **据え置き確認**: `CatalogItemName` は `catalog/content/` パッケージに残置(`CatalogItem` が import)。`StockingPolicy`/`ProductUnit`/`MinimumStock`/`ProductImage`/`ProductStatus` 不変。
