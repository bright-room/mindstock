---
paths:
  - "**/*Test.kt"
---

# Testing

mindstock のテスト方針。**意味のあるテストだけを書く**。テストの無いクラスがあってよい。

## Rule

### テストスタック

- KMP commonTest: `kotlin.test.@Test` + Kotest assertions(`io.kotest.matchers.*` / `io.kotest.assertions.throwables.shouldThrow`)。**Kotest の FunSpec は commonTest 不可**。
- backend JVM (jvmTest / src/test): Kotest FunSpec 可。

### 書くべきテスト(意味がある)

- **VO の値域バリデーション**: `init { require(...) }` の異常系・境界(空文字 → IAE、最大長超過 → IAE 等)
- **ビジネスロジック**:
  - 判定(区分の `judge` / `of`、`OwnerChangeability.on` 等)
  - 計算(`netQuantity` の畳み込み等)
  - 抽出/絞り込み(`owner()` / `roleOf()` / `needsReplenishment()` / `ShoppingList.autoItems()` 等)
  - 状態遷移の規則(`revoke()` 後に `usable()` が false 等)
  - 前提崩れの専用例外(最後の世帯主除外 → `LastOwnerException`、非 OWNER 操作 → `OwnerRequiredException` 等)
- **入力正規化**: trim 等が効くこと

### 書かないテスト(意味がない)

- コンストラクタ / data class が「値を保持するだけ」かの検証
- `equals` / `hashCode` / `toString` / `copy` など、コンパイラ/言語が生成・保証するボイラープレートの検証
- 単に値を返すだけのアクセサ(`fun id() = id`、`fun usable() = validity == 有効` 単体 等)
- 「ただ生成するだけ」のファクトリの戻り値検証(`create() = XxxId(Uuid.generateV7())` 等)

### 判断基準

**「この関数が壊れたらビジネス的に困るか?」** で判断する。困る(値域・判定・計算・抽出・遷移・前提崩れ)なら書く。困らない(保持・転送・自動生成)なら書かない。ロジックを持たない VO/集約はテスト無しでよく、コンパイルが通ることで足りる。

### 統合テスト(外部システム依存)

- DB / Garage(S3)等の外部依存を伴うテストは **`@Tags("integration")`**(Kotest)をクラスに付け、`io.kotest.core.spec.style.FunSpec` で書く(backend JVM のみ。commonTest 不可)。
- 実行: `./gradlew :backend:core:integrationTest`(Gradle の name-matching で `./gradlew integrationTest` でも各モジュールの同名タスクが起動する。`:backend:api:integrationTest` は現状 `@Tags("integration")` テスト 0 件で空実行)。通常の `./gradlew test` は `kotest.tags.exclude = "integration,manual"` で integration を除外するため、DB/Garage なしでも green。
- Garage 連携テストは `STORAGE_ENDPOINT` / `STORAGE_BUCKET` / `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` を環境変数から読む(`integrationTest` タスクが app と同じ env を test JVM へ転送)。既存例: `ProductImageTransferTest`(`@Tags("integration")` + `FunSpec`)。
- DataSource(DB)層の統合テストは現状ゼロ。フェーズ 3-1 で Hydration round-trip テストとして追加予定(その際に DB 接続用の env / testcontainers 構成も本節へ追記する)。

### Scenario / Service / Controller のテスト方針

- **Service / Scenario**: `FunSpec` + mockk で Repository をスタブし、(a) 主要ハッピーパス、(b) 非メンバー操作等の例外伝播を検証する。ビジネスロジック自体は domain テストが担うので、Service テストは orchestration(呼ぶ順・例外の素通し)に絞る。
- **Controller**: Controller にロジック(ガード分岐・例外翻訳)が**なければテスト不要**。薄い委譲だけの Controller にテストを書かない(委譲テストは保守コストだけ増やす)。ガードや分岐を足した時に、その分岐と同時にテストを書く。

## Why

- 意味のないテストは保守コストだけ増やし、実装変更のたびに機械的な追従を強いる(変更の二度手間)
- equals/hashCode/getter はコンパイラが保証する領域で、テストは二重検証にしかならない
- 「壊れて困るか」で絞ると、テストが仕様(ビジネスルール)のドキュメントとして機能する

## How to apply

### ✅ バリデーション

```kotlin
@Test
fun rejects_blank() {
    shouldThrow<IllegalArgumentException> { DisplayName("  ") }
}
```

### ✅ 判定・区分

```kotlin
@Test
fun member_cannot_manage_household() {
    RolePermissions(HouseholdMemberRole.メンバー, HouseholdCapability.世帯管理).isAllowed() shouldBe false
}
```

### ✅ 前提崩れ

```kotlin
@Test
fun last_owner_cannot_leave() {
    shouldThrow<LastOwnerException> { Household.create(HouseholdName("我が家"), owner).leave(owner.id) }
}
```

### ❌ 値を保持するだけ

```kotlin
@Test
fun holds_id_and_profile() {
    val r = Resident(id, Profile(DisplayName("たろう")))
    r.id shouldBe id // ← コンストラクタ検証、無意味
}
```

### ❌ 例外がメッセージを保持するだけ

```kotlin
@Test
fun carries_message() {
    ResourceNotFoundException("x").message shouldBe "x" // ← 無意味
}
```

## 関連

- rule: [domain-guideline](domain-guideline.md) — VO / 集約 / 区分の設計
- rule: [error-handling](error-handling.md) — IAE / 専用例外の方針
