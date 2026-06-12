---
paths:
  - "domain/**/*.kt"
---

# Domain Guideline

mindstock のドメインモデルはリッチドメインモデル指向。テーブル定義の写し(貧血モデル)は避ける。集約は object graph を持ち、ロジックは domain メソッドとして表現する。

## Rule

### domain で許容する外部ライブラリ

以下のみ許容。これ以外は domain に持ち込まない:

- `kotlin` stdlib(`kotlin.time.Clock` 含む)
- `kotlinx-serialization`(`@Serializable`, `@SerialName`)
- `kotlinx-datetime`
- `:shared` モジュール(共通の日時/シリアライズ ext。例: `LocalDateTime.now()`。`:shared` は `:domain` に依存しないため循環しない)
- `org.kotlincrypto.random`(CSPRNG。`CryptoRand.Default.nextBytes(...)`。例: `InvitationCode.generate()` の招待コード乱数。`domain/build.gradle.kts` の `libs.kotlincrypto.crypto.rand`)

新規依存(サードパーティ)が必要に思えた時は「外して複雑化しないか / 取り込んでも品質を保てるか」で個別判断。ユーザに事前確認する。

### リッチドメイン 6 原則

1. **behavior-rich**: 集約に「なぜ存在するか / 何ができるか」を言語化できないなら貧血。メソッドや composition で責務を持たせるか、概念ごと削る
2. **集約は object graph**: 子を `XxxId` で持たず、子モデルそのものを保持。Repository が JOIN で hydrate する
3. **composition 優先**: 集約間で「ID 参照」を取る前に composition を検討。同一トランザクション/文脈なら composition、完全に別文脈なら ID 参照可
4. **`createdAt` は集約ルートから削除**(インフラメタ扱い)
5. **不変更新**: 操作メソッドは新インスタンスを返す。可変 setter は持たない
6. **fact クラスは domain から消える**: append-only な履歴行は Repository 内部の永続化単位として残し、ドメインでは「現在状態」を持つ集約ルートに集約。履歴閲覧は別 read model

### Value Object 規約

- `@Serializable @JvmInline value class` 形式
- バッキングフィールドは統一して `value`、可視性 `private`
- バリデーションは `init { require(...) }` で `IllegalArgumentException` を throw(stdlib)
- `operator fun invoke(): T = value`(public。別モジュール(infrastructure/永続層)が VO の生値を取り出すため。`internal` だと別 Gradle モジュールから呼べない)
- `override fun toString(): String = value.toString()`

```kotlin
@Serializable
@JvmInline
value class Quantity(private val value: Int) {
    init { require(value > 0) { "Quantity must be positive: $value" } }
    operator fun invoke(): Int = value
    override fun toString(): String = value.toString()
}
```

### 述語メソッドと区分(enum)の判定

- **述語メソッドの Boolean 戻り値は VO 原則の対象外**。公開 API の引数・戻り値は VO で表すのが原則だが、`usable()` / `isBelow(other)` / `existsByJan(jan)` / `isAllowed()` のような「判定結果」は **データ値ではないため `Boolean` のまま返してよい**。VO で包まない。
- **区分(enum)の状態判定は区分内の述語メソッドで表現し、呼び出し側で `==` 比較しない**(tell-don't-ask)。`status == ProductStatus.アーカイブ済` のような外側での `==` 比較を書かず、`status.isアーカイブ済()` のように区分自身に述語を持たせる。判定ロジックの所在を区分に集約することで、状態追加時の影響範囲を区分内に閉じる。
- **区分の意図を表す述語メソッドは、参照ゼロでも死コード扱いで削除しない**。`InvitationValidity.is有効()` 等は「区分が表現すべき意味」であり、現時点で呼び出し元が無くても残す。

### ファクトリ関数の方針

- **意味のあるファクトリは持ってよい**。クロック呼び出し・UUID 生成のような **副作用を伴う / コンストラクタだけでは作れない** 生成ロジックを wrap する目的のものが該当
  - 例: `ResidentId.create()` が `Uuid.generateV7()` を呼んで生成
  - 例: `OccurredAt.now()` が `:shared` の `LocalDateTime.now()` を呼ぶ
- **意味のないファクトリは NG**。`Quantity.of(123)` のような、コンストラクタ `Quantity(123)` と等価なものを別名で持たない(API の二重化になる)

### ID 型規約

- UUID 系(集約ルート): `value class ProductId(private val value: Uuid)`、バリデーション無し
- Long 系(履歴テーブル): `value class StockReplenishmentId(private val value: Long)`、`init` で非負強制
- ID 発番は呼び出し側責任。`ResidentId.create()` で UUIDv7 生成

### ファーストクラスコレクション(First-class Collection)

`Products` / `Stocks` / `HouseholdMembers` / `StockMovements` / `CatalogItems` 等。

```kotlin
@Serializable
data class Stocks(val list: List<Stock>) {
    fun size(): Int = list.size
    fun needsReplenishment(): Stocks = Stocks(list.filter { it.belowThreshold() })
}
```

- `val list: List<T>` を public で持つ。**ループ・map / filter など List の操作には `.list` を使う**
- **件数を取りたい時は `fun size()`** を呼ぶ。`.list.size` のような内部 list 経由のサイズ取得は **NG**(件数の意味がファーストクラスコレクションの責務として表現されるべきため)
- `asList()` メソッドは **持たない**(`val list` 公開で代替できる)
- `@Serializable` を付ける
- ドメイン固有操作のみメソッド化(`owner()`, `activeOnly()`, `netQuantity()`, `Stocks.needsReplenishment()` 等)

### 時間

- 「現在時刻」の生成は **`:shared` の `LocalDateTime.now()`(既定 `TimeZone.JST`)を使う**。`Clock.System.now().toLocalDateTime(...)` を VO 内で手書きしない(タイムゾーン/wasmJs の IANA 対応を `:shared` に一元化)
- 値域に「未来日不許容」等の制約があれば `init { require(...) }` で検証する

### sealed interface でポリモフィズム

- 列挙的な variant を扱う型は sealed interface + subclass で表現する。`type` フィールドや null を使った状態判別はしない
- **`@Serializable` で polymorphic serialize する sealed interface の variant に `@JvmInline value class` を使ってはならない**。value class は wire 上「中身の生値」に unwrap され type discriminator を載せられず、deserialize 時に variant を復元できず静かに壊れる(過去に遭遇)。variant は `data class` か `object` で表現する。`@Serializable` を付けない(永続化/通信に乗せない)sealed なら value class variant でも可
- polymorphic serialization は kotlinx-serialization 標準(type discriminator + FQN)。カスタム discriminator は導入しない

## Why

- 貧血モデルは「テーブル定義の写し」になり、ロジックが Service に流出する
- `createdAt` を集約に持たせると、永続化前の「createdAt = null」状態を扱わないといけなくなる
- VO の意味のないファクトリ関数(`Quantity.of(...)`)は、呼び出し側が `Quantity(123)` も使えてしまい二重化する。`init` で完結させればコンストラクタ一本道。一方、UUID 生成のように「副作用を含む生成」はコンストラクタには書けないので、ファクトリとして名前と責務を明示すべき
- ファーストクラスコレクションの「件数」は集合自体の振る舞いであり、内部 List の `size` プロパティをそのまま使うのは「ファーストクラスコレクションとして集合の意味を持たせた」ことに反する。`fun size()` を集合の API として明示する
- 列挙状態を `String type` や `null` で表現すると、網羅判別が型システムで保証されない。sealed interface なら `when` で網羅性をコンパイラがチェックする

## How to apply

### ✅ リッチドメイン

```kotlin
data class Stock(
    val id: StockId,
    val product: Product,
    val movements: StockMovements,
) {
    fun replenish(quantity: Quantity, note: Note, actor: ResidentId): Stock {
        require(!product.archived) { "cannot replenish archived product: ${product.name}" }
        return copy(movements = movements + StockMovement.Replenishment(quantity, note, actor, OccurredAt.now()))
    }

    fun currentQuantity(): Quantity = movements.netQuantity()
}
```

### ✅ 意味のあるファクトリ

```kotlin
@Serializable @JvmInline
value class ResidentId(private val value: Uuid) {
    operator fun invoke(): Uuid = value
    override fun toString(): String = value.toString()

    companion object {
        fun create(): ResidentId = ResidentId(Uuid.generateV7())  // 副作用ありの生成は factory として明示
    }
}
```

### ❌ 意味のないファクトリ

```kotlin
@Serializable @JvmInline
value class Quantity(private val value: Int) {
    init { require(value > 0) }
    companion object {
        fun of(value: Int): Quantity = Quantity(value)  // ← Quantity(value) と等価、二重化なので NG
    }
}
```

### ❌ 貧血モデル

```kotlin
// アンチパターン
data class Stock(
    val id: StockId,
    val productId: ProductId,     // ← ID 参照のみ、composition していない
    val quantity: Int,            // ← VO ではなく primitive
    val createdAt: Instant,       // ← インフラメタが集約に
)
// → Service 側で if (stock.quantity < threshold) ... と判定するハメに
```

### ✅ ファーストクラスコレクションの利用

```kotlin
val stocks: Stocks = stockRepository.findByHousehold(id)

// 件数: fun size() を使う
val count = stocks.size()  // ✅

// イテレーション: .list を使う
for (stock in stocks.list) {  // ✅
    println(stock)
}

// 件数を内部 list 経由で取るのは NG
val badCount = stocks.list.size  // ❌
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [backend-software-architecture](backend-software-architecture.md) — 層全体の依存方向
- rule: [error-handling](error-handling.md) — IAE / ResourceNotFoundException の扱い
