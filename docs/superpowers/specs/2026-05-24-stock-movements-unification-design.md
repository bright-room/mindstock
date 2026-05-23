# Stock Movements 統一化 設計ドキュメント

- 作成日: 2026-05-24
- 対象: Stock 関連の DB スキーマ + ドメインを再設計し、4 テーブル → 1 テーブルに統一
- 親仕様: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)(§4.5 訂正の方針 / §5.5 Stock ドメイン を置き換え)
- 関連: [2026-05-24-domain-richness-design.md](./2026-05-24-domain-richness-design.md)(直前の設計)

## 1. なぜ再設計するか

PR #53(domain richness)実装中に、Stock 関連の現行設計に以下の違和感が表面化した。

### 1.1 「訂正で元イベントを置換」設計のフラジリティ

現行は `stock_replenishments` / `stock_consumptions` の事実テーブルと、それぞれの訂正テーブル `stock_replenishment_corrections` / `stock_consumption_corrections` を持ち、`Stock.effective()` が「対象イベントを特定して latest 訂正で置換」していた。

```kotlin
// 旧設計
private fun effective(event: Replenishment): EffectiveQuantity {
    val latest = replenishmentCorrections
        .filter { it.target == event }     // ← 値同値で特定。フラジル
        .maxByOrNull { it.correctedAt() }
    return EffectiveQuantity(event.quantity, latest?.correctedQuantity)
}
```

CodeRabbit から `it.target == event` の値同値が「異なる Replenishment が偶然同値だった場合に取り違える」可能性を指摘されており、ID 不在の現設計と相性が悪い。

### 1.2 「訂正」概念自体の不要性

mindstock は個人/家庭の在庫管理であり、「3 個補充したつもりが 2 個だった」というケースはユーザーが**単に消費 1 を追加登録すれば事実として整合する**。訂正という独立概念を導入する必要がない。

「補充」「消費」「補充の訂正」「消費の訂正」の 4 種を持つより、**「在庫を増やす事実(REPLENISHMENT)」「在庫を減らす事実(CONSUMPTION)」の 2 種だけ**で append-only に積めば十分。

### 1.3 受け入れる前提

- 「どの操作が誤りだったか」の監査情報は失われる(差分の積み重ねしか残らない)
- 「補充の訂正」という UI メニューは持たない(消費を 1 つ追加で代替)
- これらは個人/家庭向け在庫管理として許容範囲(ユーザー判断: 2026-05-24)

## 2. 設計の柱

| 柱 | 内容 |
|---|---|
| 1 テーブル統合 | Stock 関連の事実テーブルを `stock_movements` 1 本に統合 |
| 訂正概念の廃止 | 訂正は別概念ではなく「単なる増減 movement の追加」で表現 |
| 線形集計 | `currentQuantity` は全 movement の符号付き合計 |
| append-only 維持 | 親仕様の原則は維持。UPDATE/DELETE しない |

## 3. DB スキーマ

### 3.1 `stock_movements`(新設)

| カラム | 型 | 制約 / 備考 |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `product_id` | UUID | NOT NULL, FK → `products.id` |
| `type` | enum (`stock_movement_type`) | NOT NULL。`REPLENISHMENT` / `CONSUMPTION` |
| `quantity` | INT | NOT NULL, CHECK > 0(常に正の数量) |
| `occurred_at` | TIMESTAMPTZ | NOT NULL。発生日時 |
| `acted_by` | UUID | NOT NULL, FK → `users.id` |
| `note` | TEXT | NOT NULL DEFAULT ''。任意メモ(例: 「卵 10 個パックを 1 パック消費」) |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |

### 3.2 削除するテーブル

- `stock_replenishments`
- `stock_consumptions`
- `stock_replenishment_corrections`
- `stock_consumption_corrections`

### 3.3 type の表現

`type` は Kotlin enum (`StockMovementType`) を Exposed の `enumerationByName` で VARCHAR(20) にマッピングする。これは既存の `HouseholdMembershipsTable.role` (`HouseholdMemberRole` を `enumerationByName<HouseholdMemberRole>("role", 20)`)と同じパターンで、コードベースの慣習に合わせる。

```kotlin
val type = enumerationByName<StockMovementType>("type", 20)
```

PostgreSQL ネイティブ ENUM 型 (`CREATE TYPE ... AS ENUM`) は採用しない。理由: 現行の `MigrationGenerator`(Exposed `MigrationUtils.generateMigrationScript`)が `CREATE TYPE` を生成しないため、ネイティブ enum を選ぶと migration 生成パイプラインの改修が必要になり、本タスクのスコープを超える。

### 3.4 Migration 戦略

- `:backend:infrastructure:schemas` に新規 `StockMovementsTable` を追加
- 旧 4 テーブルの Table オブジェクトを削除
- Plan 2 の `executor/src/main/resources/db/migration/V20260523071825__init.sql` を **再生成** する(append-only な migration 履歴を守るための追加 migration は今回採用しない。理由: まだ本番デプロイされておらず init.sql 段階での書き換えが許容される)

## 4. ドメイン

### 4.1 クラス構成

```kotlin
package net.brightroom.mindstock.domain.model.stock

sealed interface StockMovement {
    val product: Product
    val quantity: Quantity        // 常に正
    val occurredAt: OccurredAt
    val actor: Actor
    val note: Note
}

data class Replenishment(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Actor,
    override val note: Note,
) : StockMovement

data class Consumption(
    override val product: Product,
    override val quantity: Quantity,
    override val occurredAt: OccurredAt,
    override val actor: Actor,
    override val note: Note,
) : StockMovement
```

### 4.2 Collection オブジェクト

```kotlin
class StockMovements(private val list: List<StockMovement>) {
    fun asList(): List<StockMovement> = list

    /** 補充を正、消費を負として全 movement を集計した正味数量 */
    fun netQuantity(): Int = list.sumOf {
        when (it) {
            is Replenishment -> +it.quantity()
            is Consumption -> -it.quantity()
        }
    }
}
```

`currentQuantity` の計算ロジックは collection に持たせる(リッチドメインモデル指向: 集合に対する集計は集合自身の責務)。

### 4.3 `Stock` の再設計

```kotlin
class Stock(
    val product: Product,
    val movements: StockMovements,
) {
    fun currentQuantity(): Int = movements.netQuantity()

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.let { it() } ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.let { it() } ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }
}
```

`Stock.effective()` は不要(差分計上型なので「補正対象の特定」が発生しない)。CodeRabbit 指摘の値同値問題も自然消滅。

### 4.4 削除するクラス

| クラス | 削除理由 |
|---|---|
| `Replenishments` | `StockMovements` に統合 |
| `Consumptions` | `StockMovements` に統合 |
| `ReplenishmentCorrection` | 訂正概念ごと削除 |
| `ConsumptionCorrection` | 同上 |
| `ReplenishmentId` | ドメイン上で id を参照する箇所がない(append-only、訂正対象の特定もしない)。DB の BIGSERIAL は持つが domain には持ち上げない |
| `ConsumptionId` | 同上 |
| `EffectiveQuantity` | 訂正廃止により不要 |

### 4.5 id を持たせない判断

`StockMovement` には id プロパティを持たせない。理由:

- movement は append-only で UPDATE/DELETE しない
- 訂正概念を廃止したため「特定の movement を参照する」ドメインユースケースが存在しない
- DB の BIGSERIAL は Repository / Infrastructure 層の関心事として扱う

これは親仕様 [domain-richness-design §2.5](./2026-05-24-domain-richness-design.md) の「id / 日時 はドメインに持ち上げない」原則と整合。

## 5. Repository

### 5.1 `StockRegisterRepository`(書き込み)

```kotlin
interface StockRegisterRepository {
    suspend fun replenish(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Actor,
        note: Note,
    )

    suspend fun consume(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        actor: Actor,
        note: Note,
    )
}
```

- 訂正系メソッド(`correctReplenishment` / `correctConsumption`)は削除
- 各メソッドは `stock_movements` への 1 行 INSERT を行う

### 5.2 `StockRepository`(読み取り)

I/F は維持:

```kotlin
interface StockRepository {
    suspend fun stockOf(product: Product): Stock
    suspend fun stocksOf(household: Household): List<Stock>
}
```

中身は `stock_movements` から該当 product の全 movement を取得して `StockMovements` を組み立て、`Stock` に渡す。

## 6. API への影響

- `replenishStock(...)` / `consumeStock(...)` の RPC エンドポイントは維持
- `correctReplenishment(...)` / `correctConsumption(...)` の RPC エンドポイント(があれば)削除
- UI 側は「補充の訂正」操作を「消費を追加」に置き換える(UI は本仕様の対象外、別途検討)

## 7. 影響範囲

| モジュール | 変更内容 |
|---|---|
| `:backend:infrastructure:schemas` | 旧 4 Table 削除、`StockMovementsTable` 新設 |
| `:backend:infrastructure:migration` | `V20260523071825__init.sql` を再生成 |
| `:domain` | 旧クラス群削除、`StockMovement` / `Replenishment` / `Consumption` / `StockMovements` 新設、`Stock` 書き換え |
| `:domain` repository | `StockRegisterRepository` / `StockRepository` の I/F 書き換え |
| `:backend:application:api` | RPC ハンドラから訂正系を削除、replenish/consume の入出力調整 |
| テスト | Stock 関連のテスト全面書き直し |

## 8. 親仕様への波及

[2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md) の以下を本仕様で置き換え:

- §4.3 ID 戦略 — Stock 関連 ID(`stock_replenishment_id` 等)を削除し `stock_movement_id`(BIGSERIAL)を追加
- §4.5 訂正の方針 — 「訂正テーブルを別途持つ」方針を廃止し「訂正は単なる movement 追加」に変更
- §5.5 Stock ドメイン — 4 テーブル構成を 1 テーブル(`stock_movements`)構成に変更
- §6.1 サービスインターフェース — 訂正系 RPC を削除

これらの親仕様側の差分反映は、本仕様の実装 PR 内で同時に行う。
