---
paths:
  - "**/*.kt"
---

# Error Handling

mindstock の例外設計と nullable 利用ルール。全層に適用。

## Rule

### nullable 戻り値原則禁止

- すべての公開 API(Repository / Service / Scenario / Controller / Domain / Infrastructure)で **戻り値・パラメータの `T?` は原則禁止**
- 「不在」が意味を持つ状況は **例外 or sealed 型** で表現する
- 空 List は別概念。「件数 0 が正常」なら例外不要、**空のファーストクラスコレクション** を返せばよい
- どうしても nullable を返したい場合は **必ず事前にユーザに「なぜ nullable が必要か」を提示して承認を得る**。勝手に `T?` を導入しない

### 単一値の不在

`domain/exception/ResourceNotFoundException(reason: String)` を throw。

- message には何が見つからなかったかを書く: `"household not found: $id"`

### Value Object の値域違反

stdlib `IllegalArgumentException`(`init { require(...) }` で throw)。Domain VO の値域 / 集約の不変条件違反は IAE で表現する。IAE では意味が歪む箇所(「アーカイブ済み商品の在庫変動」「OWNER が自分自身を revoke」等)のみ、その時に専用例外を定義する。

### Service / Scenario は素通し

Repository が throw した例外を **Service / Scenario が catch して再投げするような書き方をしない**。Repository の戻り値の null チェックも Service / Scenario で書かない(不在は infrastructure 側が例外で表現する)。例外はそのまま上の層に伝播させる。

### 素通し原則の許容例外

「Repository の例外を catch して詰め替えない」が原則だが、以下は **catch が設計上の意味を持つ** ため許容する:

- **不在を別戦略のトリガーにする catch**: 不在(`ResourceNotFoundException`)を「次の手段に切り替える合図」として使う場合。例: `CatalogService.lookupByJan`(`backend/core/.../application/service/catalog/CatalogService.kt`)は master 不在を catch して外部 API へフォールバックし、得た結果を cache する。これは「例外の握り潰し」ではなく「不在 → 別経路」という業務フローなので可。

  ```kotlin
  fun lookupByJan(jan: Jan): CatalogItem =
      try {
          catalogRepository.findByJan(jan)
      } catch (e: ResourceNotFoundException) {
          val received = externalProductRepository.findByJan(jan)  // 不在 → 外部から受信
          catalogRegisterRepository.register(received)             // cache
          received
      }
  ```

- **configuration 層(認証 plugin 等)での例外吸収**: `configuration/auth/MindstockAuthPlugin` のような Ktor plugin は presentation/application の外側の glue であり、例外を `RpcError` でなく HTTP ステータス / ハンドシェイク拒否 / ログで処理してよい。`runGuarded` の翻訳マップ(`backend-rpc-and-transactions.md` 参照)は RPC method 内の話で、configuration 層には適用されない。

- **`private fun` の nullable 戻り値(クラス内 sentinel 用途)**: 公開 API の `T?` は禁止だが、同一クラス内に閉じた `private fun` が「見つからなければ次へ」の内部制御に nullable を使うのは可(型シグネチャが外に漏れない)。公開境界(`internal`/`public`)に nullable を出す場合のみ事前承認が要る。

## Why

- Nullable は「呼び出し側で必ず分岐すべき情報」を型から消す。例外 or sealed 型なら、不在の意味と理由が型シグネチャに現れて無視できなくなる
- `Service.findById(id): T?` は Repository の null をそのまま転送しているだけで application 層としての契約を放棄している
- Service / Scenario で catch して再投げを繰り返すと、例外型が層ごとに増えて追跡しづらい。素通しで上層に流せば、例外は infrastructure / domain で発生したそのままの形で扱える

## How to apply

### ✅ 例外で不在を表現

```kotlin
// infrastructure (DataSource)
internal fun findById(id: StockId): Stock {
    return StockTable.selectAll()
        .where { StockTable.id eq id() }
        .singleOrNull()
        ?.toStock()
        ?: throw ResourceNotFoundException("stock not found: $id")
}

// Service は素通し(null チェックしない、catch しない)
class StockService(private val repo: StockRepository) {
    fun get(id: StockId): Stock = repo.findById(id)
}
```

### ✅ 空はファーストクラスコレクションで

```kotlin
fun findByHousehold(id: HouseholdId): Stocks =
    Stocks(StockTable.selectAll().where { /* ... */ }.map { it.toStock() })
// 空でも Stocks(emptyList()) を返す。throw しない、null を返さない
```

### ❌ nullable 戻り値

```kotlin
// アンチパターン
fun findById(id: StockId): Stock? = /* ... */
// → 呼び出し側で stock ?: throw NotFoundException(...) が散らかる
```

### ❌ Service / Scenario で素通しせず再 catch

```kotlin
// アンチパターン: Repository の null を Service で別の例外に詰め替えている
class StockService(private val repo: StockRepository) {
    fun get(id: StockId): Stock = repo.findById(id)
        ?: throw ResourceNotFoundException("stock not found: $id")  // ← infra で投げる
}
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [backend-software-architecture](backend-software-architecture.md) — 層責務との関係
- rule: [backend-rpc-and-transactions](backend-rpc-and-transactions.md) — トランザクション境界(DataSource 自前)の詳細
