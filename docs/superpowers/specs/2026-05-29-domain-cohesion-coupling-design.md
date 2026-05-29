# domain モジュール 凝集度・結合度 診断書

- 日付: 2026-05-29
- 種別: 評価レポート（refactor は所見ごとに独立 Plan 化）
- 対象: `domain/` モジュール（`net.brightroom.mindstock.domain.*`）

## 0. 目的と前提

### 0.1 目的

「予防的見直し」として、`domain/` モジュールの凝集度・結合度が今後の機能追加に耐えうるかを評価する。
具体的な痛みや差し迫った機能要件は無いが、コードが育ってきた段階での棚卸し。

各所見には推奨アクションを付け、独立した Plan/PR として実施可能な粒度に分解する。

### 0.2 評価軸

3 軸で評価する:

1. パッケージ境界（BC グルーピング、ネスト、命名）
2. 集約境界と composition（aggregate の切り方、ID 参照 vs object graph）
3. 依存方向と外側との結合（domain が wire-format も兼ねる現状の含意）

### 0.3 評価対象のスナップショット

- 単一 KMP モジュール `domain/`、`commonMain` 中心
- パッケージ: `net.brightroom.mindstock.domain.{exception, model.<bc>.*}`
- BC: `catalog / household / product / shopping / stock / stock.movement / user / user.auth`
- VO は `@JvmInline value class` + `init` バリデーション + `invoke()` 取り出しが規約
- ID は `Uuid` v7 を `companion.create()` で発番
- Sealed `DomainException` 一本（11 ケース、flat）
- 集約は概ねリッチドメインモデル
  （`Stock.currentQuantity()/needsReplenishment()`、`ShoppingList.itemsToBuy()`、`HouseholdMembers.owner()/contains()` 等）

### 0.4 依存元

- `backend/core`, `backend/api`, `rpc`, `frontend` が `implementation(projects.domain)`
- **`rpc` / `frontend` が domain クラスをそのまま wire / UI モデルに転用**している
  → domain クラス自体が `@Serializable` で配線フォーマットを兼ねる

この前提（domain = wire-format）は Section 3 の所見全てに影響する。

---

## 1. パッケージ境界の所見

### 判断軸

サブパッケージで切るのは以下が成り立つ時のみ:

- 集約状態と派生 read-model のように **役割が違う**
- 集約内サブエンティティ群が **独立した塊** を成す（append-only fact 群など）
- 依存方向を遮断したい

ファイル数は判断材料にしない。「概念が混ざっていない」なら 10 件入っていても 1 パッケージで良い。

### 所見

| # | 所見 | 推奨 |
|---|---|---|
| 1.1 | `shopping/` が他 BC と並列なのは違和感（`ShoppingList` は `List<Stock>` の派生 read-model であり独立 BC ではない） | **直す**: `model/stock/shopping/` に移動 |
| 1.2 | `stock/` と `stock/movement/` の間に **循環参照** が存在: `Stock.kt` が `StockMovements` を import、一方 `Replenishment/Consumption` が `stock/Quantity, Note, OccurredAt` を import。原因は VO の所属先誤り（3 VO は movement 用 VO） | **直す**: `Quantity / Note / OccurredAt` を `stock/movement/` に移動。`stock/movement/` のサブパッケージ構造自体は維持（append-only fact 群として概念区別あり） |
| 1.3 | `user/auth/` は外部認証文脈の独立塊として妥当 | 現状維持 |
| 1.4 | `model/` 中間層 | 現状維持（Service/Event を別パッケージに切らない方針 = データとロジックは同居。将来トリガーで再評価） |
| 1.5 | `shopping` 命名（What 寄り、他は名詞 Concept） | 1.1 で解決 |

### 修正後の構造

```
domain/
├── exception/
│   └── DomainException.kt                    (※ Section 3.4 で廃止予定)
└── model/
    ├── catalog/                              (5)
    │   ├── CatalogItem.kt
    │   ├── CatalogItemId.kt
    │   ├── CatalogItemName.kt
    │   ├── CatalogItemUnit.kt
    │   └── CatalogItems.kt
    ├── household/                            (5)
    │   ├── Household.kt
    │   ├── HouseholdId.kt
    │   ├── HouseholdMember.kt
    │   ├── HouseholdMemberRole.kt
    │   └── HouseholdMembers.kt
    ├── product/                              (4)
    │   ├── Product.kt
    │   ├── ProductId.kt
    │   ├── MinimumStock.kt
    │   └── Products.kt
    ├── stock/
    │   ├── Stock.kt                          (1) — 集約ルート
    │   ├── Stocks.kt                         (※ Section 2.2 で新設)
    │   ├── movement/                         (8) — StockMovement系 5 + Quantity, Note, OccurredAt
    │   └── shopping/                         (2) — 派生 read-model
    └── user/                                 (※ Section 2.4 で User クラス廃止)
        ├── UserId.kt
        ├── auth/   (3)                       (AuthIdentity, AuthProvider, AuthSubject)
        └── profile/                          (Profile, DisplayName)
```

### 依存方向の確認（修正後）

```
exception        (leaf)
user.auth        → exception
user.profile     → exception
catalog          → exception
product          → catalog, exception
household        → user.profile
stock.movement   → product, user.profile, exception
stock            → stock.movement, product
stock.shopping   → stock
```

- 循環参照なし
- `exception` が leaf（後に廃止予定。廃止後は IAE のみ）
- cross-BC composition は `household → user.profile`, `product → catalog`, `stock.movement → {product, user.profile}` の単方向のみ

---

## 2. 集約境界と composition の所見

| # | 所見 | 推奨 |
|---|---|---|
| 2.1 | `Stock.product` と `StockMovement.product` で Product が二重保持。「Stock 内の全 movement の product は Stock.product と一致」という不変条件が型で表現されていない | **直す**: `StockMovement` から `product` を外し、Stock 側に一元化 |
| 2.2 | `Stocks` 集合体が存在しない。`Products / CatalogItems / HouseholdMembers / StockMovements` には集合体がある中で非対称。`ShoppingList` が `List<Stock>` を直接受けている | **直す**: `Stocks` 集合体を新設し、`ShoppingList(stocks: Stocks)` に変更 |
| 2.3 | `Household` がメソッドを持たず、`owner()/contains()` 等は `members` 経由。貧血ボーダー | **様子見**: 招待/role 変更機能の追加時に `Household` へ動詞を集める |
| 2.4 + 2.5 + 3.3 | `User` がフル composition（`HouseholdMember.user`, `StockMovement.actor`）。結果として `authIdentity`（OIDC sub クレーム）が他メンバーや履歴 API に漏れるリスク | **直す**: `User` クラスを廃止。`UserId` を identity の主役にし、`Profile` / `AuthIdentity` を関連エンティティとして `UserId` で寄り添わせる。composition は `Profile` 経由に置換 |
| 2.6 | `HouseholdMember` の revoked 状態は Repository が読み込み時に除外する規約。「アクティブメンバー」と「全メンバー」が同じ型 | 現状維持（履歴閲覧機能の追加時に read-model 化） |

### 2.4 + 2.5 + 3.3 の詳細

User 集約を分解する:

- `UserId`: identity の主役
- `user/profile/Profile(userId: UserId, displayName: DisplayName)`: 表示文脈
- `user/auth/AuthIdentity`: 外部認証文脈、`UserId` と紐付く別エンティティ

composition の置換:

```kotlin
data class HouseholdMember(val profile: Profile, val role: HouseholdMemberRole)
sealed interface StockMovement { val actor: Profile; ... }
```

`User` クラス自体は廃止する。id しか持たないクラスは `UserId` と等価で、残しても貧血の温床になるだけ。

副次効果:

- メンバー一覧 / 履歴 API で `AuthIdentity` が一切露出しない
- 必要に応じて `rpc` 層に `Request/Response` クラスを置き、**腐敗防止層** として運用できる
- 同一 `UserId` に複数 `AuthIdentity`（Google + Zitadel 等）を紐付ける将来拡張が自然に書ける

---

## 3. 依存方向と外側との結合の所見

### 前提

`rpc` / `frontend` が domain クラスをそのまま wire / UI モデルとして使う設計（domain = wire-format）。

| # | 所見 | 推奨 |
|---|---|---|
| 3.1 | domain = wire-format 前提の影響: 内部表現変更が即 API 破壊。シリアライズ都合がドメイン設計を歪める可能性 | 現状維持。問題が出てきたら都度直す。「DTO 層導入のトリガー条件」は Section 5 メモに残す |
| 3.2 | `OccurredAt` が二重コンストラクタ（primary 無検証、secondary でガード）。VO 内不変条件が規約依存 | **直す**: VO 内で `kotlin.time.Clock.System.now()` を呼んで init 単一化（要事前検証） |
| 3.3 | `User` フル wire → `AuthIdentity` 漏れ | 2.4 + 2.5 と統合解決 |
| 3.4 | `DomainException` の階層 | **直す**: 廃止。原則 `IllegalArgumentException`、stdlib では表現が歪になる場合のみ専用例外を定義 |
| 3.5 | （削除: 3.1 の派生事実で独立所見の価値なし） | — |
| 3.6 | `kotlinx.datetime`（`kotlin.time.Instant`）依存 | 現状維持。`Instant` はドメイン概念の正しい表現 |

### 3.2 の詳細

```kotlin
@Serializable
@JvmInline
value class OccurredAt(private val value: Instant) {
    init {
        require(value <= Clock.System.now()) { "occurredAt $value must be <= now" }
    }
}
```

事前検証必要:

1. `@Serializable` + `@JvmInline value class` + `init` の組み合わせで **デシリアライズ時に init が走るか**。走らなければ規約依存が残るため、カスタム `KSerializer` で揃える
2. 走る場合、デシリアライズで「過去データが now を超えて未来扱い」される事故が起きないか（基本起きないが、ユニットテスト時の時計操作と合わせて要確認）

### 3.4 の詳細

現状の `DomainException` 11 ケースは全て VO の値域違反 → `IllegalArgumentException` で表現可能。

帰結:

- `DomainException.kt` を削除
- 各 VO の `init` は `require(...)` で `IllegalArgumentException` を投げる
- Application / RPC 層は IAE を catch して RPC 例外（`InventoryException` 等）に翻訳

将来「ドメイン操作の前提が壊れている系」（例: 「アーカイブ済み商品の在庫変動」「OWNER が自分自身を revoke」）が必要になった時、その時に意味的に IAE では歪む箇所のみ専用例外を定義する。

### VO 内で扱ってよいライブラリ（方針メモ）

- `kotlin` stdlib
- `kotlinx-serialization`
- `kotlinx-datetime`（`kotlin.time.*` 統合済）

これら以外の依存を VO に持ち込む場合は、外して複雑化しないか / 取り込んでも一定の品質を保てるかを検討する。

---

## 4. 個別の細かい所見

| # | 所見 | 推奨 |
|---|---|---|
| 4.1 | 集合型 API の非対称（`val list` 公開と `asList()` 併用、`size` の有無バラバラ） | **直す**: `val list: List<T>` 公開で統一、`asList()` 削除、`size` は `list.size` 経由に統一 |
| 4.2 | `Note` が `String` の意味付きラッパのみ（バリデーション・振る舞いなし） | 現状維持（VO 厳格運用の一貫性として残す） |
| 4.3 | ID VO 4 種がほぼ同形だが抽象化されていない | 現状維持（抽象化すると `@JvmInline` / `@Serializable` と相性悪化） |
| 4.4 | `companion.create()` で ID 発番 | 現状維持（「ID 発番は ID 型の責務」方針は明快） |
| 4.5 | `StockMovementType` enum と `StockMovement.type` フィールドが冗長（sealed 判別で十分） | **直す**: 削除 |
| 4.6 | `Product.minimumStock: MinimumStock?` が null で「未設定」を表現。`Stock` 側に重複ロジックあり | **直す**: ポリモフィック化（`sealed { Set, NotSet }`）。`Stock` 側のロジックを `MinimumStock` に移譲、4.7 同時解消 |
| 4.7 | `Stock.needsReplenishment()` と `Stock.shortage()` で minimum 取り出しが重複 | 4.6 で自動解消 |

### 4.6 の詳細

```kotlin
@Serializable
sealed interface MinimumStock {
    fun isBelow(quantity: Int): Boolean
    fun shortage(quantity: Int): Int

    @Serializable
    data object NotSet : MinimumStock {
        override fun isBelow(quantity: Int) = false
        override fun shortage(quantity: Int) = 0
    }

    @Serializable
    @JvmInline
    value class Set(private val value: Int) : MinimumStock {
        init { require(value >= 0) }
        override fun isBelow(quantity: Int) = quantity < value
        override fun shortage(quantity: Int) = (value - quantity).coerceAtLeast(0)
        operator fun invoke(): Int = value
    }
}
```

帰結:

- `Product.minimumStock: MinimumStock`（**non-null**）
- `Stock` 側:
  ```kotlin
  fun needsReplenishment() = product.minimumStock.isBelow(currentQuantity())
  fun shortage() = product.minimumStock.shortage(currentQuantity())
  ```

事前検証必要:

1. `@JvmInline value class` が `sealed interface` を実装できるか（Kotlin 1.5+ で OK のはず、要確認）
2. `@Serializable` polymorphic serialization の wire 形式（type discriminator）が rpc / frontend で正しく動くか
3. wire 形式の破壊変更を許容（Section 3.1 現状維持下の意図的なリファクタとして許容）

---

## 5. 優先度付きアクションリスト

### P0: 構造的問題、独立 PR で先に直す

| # | 内容 | 影響範囲 |
|---|---|---|
| 2.4 + 2.5 + 3.3 | User クラス廃止 / Profile・AuthIdentity 分離 / composition は Profile 経由 | 大（HouseholdMember, StockMovement, repository, datasource, rpc, frontend） |
| 3.4 | `DomainException` 廃止、原則 IAE | 中（catch 箇所、application/rpc 層の翻訳ロジック） |

### P1: ドメイン整合性、独立 PR

| # | 内容 | 影響範囲 |
|---|---|---|
| 2.1 | `StockMovement` から `product` 削除（Stock 側に一元化） | 中（rpc, datasource） |
| 2.2 | `Stocks` 集合体新設、`ShoppingList(stocks: Stocks)` | 小〜中 |
| 4.5 | `StockMovementType` 削除、`type` フィールド削除 | 小（wire 形式変更） |
| 4.6 | `MinimumStock` ポリモフィック化（4.7 同時解消） | 中（wire 形式変更、Stock ロジック移動） |

### P2: API 統一・パッケージ整理、独立 PR

| # | 内容 | 影響範囲 |
|---|---|---|
| 1.1 | `shopping/` → `model/stock/shopping/` | 小（import 更新のみ） |
| 1.2 | `Quantity / Note / OccurredAt` を `stock/movement/` に移動（**循環解消**） | 小（import 更新のみ） |
| 4.1 | 集合型 API 統一（`val list` 公開、`asList()` 削除） | 中（呼び出し箇所更新） |
| 3.2 | `OccurredAt` を init 単一コンストラクタ化（`Clock.System.now()` を init で呼ぶ） | 小（要事前検証） |

### P3: 様子見

| # | 内容 | トリガー |
|---|---|---|
| 2.3 | `Household` に動詞を集める | 招待 / role 変更機能の追加時 |
| 2.6 | revoked メンバー履歴の read-model 化 | 履歴閲覧機能の追加時 |
| 1.4 | `model/` 中間層の要否再評価 | Service / Event 導入時 |
| 3.1 | DTO 層導入 | 「外部公開 API ができた時」「フロント・バックの所有チーム分離時」「破壊変更を渋るほどのコンシューマ数になった時」 |

### 現状維持（所見のみ）

| # | 内容 |
|---|---|
| 1.3 | `user/auth/` パッケージ |
| 4.2 | `Note` の薄い VO |
| 4.3 | ID VO 抽象化なし |
| 4.4 | `companion.create()` |
| 3.6 | `kotlinx.datetime` 依存 |

### Plan 化の推奨グルーピング

- **Plan A (P0)**: User 分離 + DomainException 廃止（**先行・最大の構造変更**）
- **Plan B (P1)**: Stock/Movement 周りの整合性 4 件
- **Plan C (P2)**: パッケージ整理 + API 統一（機械的変更が多い）

P0 → P1 → P2 の順で進める。各 Plan は独立で merge 可能。
