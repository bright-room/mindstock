# mindstock ドメイン層 設計ドキュメント

- 作成日: 2026-05-23
- 対象: Plan 3(ドメイン層: 集約 / Value Object / 例外 / Repository ポート)
- 親仕様: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)

このドキュメントは、Plan 3 で実装する内容のうち親仕様には収まらない実装詳細(クラス分割・命名・可視性・Repository 構造・テスト方針)を定める。親仕様の概念モデルとデータベース設計を前提に読む。

## 1. スコープ

Plan 3 で実装するもの:

1. `domain` モジュールを JVM-only から **KMP commonMain** に格上げ
2. `:shared:extensions` に **UUIDv7 生成ユーティリティ**を追加
3. `:shared:rpc` を `:domain` に依存させ、ID 型を `domain` に一本化
4. `domain` 配下の集約・Value Object・事実オブジェクト・例外を実装
5. 5 集約 × 2(参照系 + 登録系)= 10 個の Repository インターフェース(ポート)を定義
6. Kotest による単体テスト

Plan 3 で実装しないもの:

- UseCase(Plan 4)
- Repository の Exposed 実装(Plan 5)
- RPC サービス実装(Plan 6 以降)
- ドメインイベント発火 / ハンドラ(append-only 設計のため明示的イベント型は持たない方針)

## 2. モジュール構造とビルド

### 2.1 domain の KMP 化

| 項目 | Plan 2 まで | Plan 3 以降 |
|---|---|---|
| ターゲット | JVM only | JVM + wasmJs(KMP) |
| ソースルート | `src/main/kotlin/` | `src/commonMain/kotlin/`, `src/commonTest/kotlin/` |
| Convention plugin | `net.brightroom.mindstock.kotlin-jvm` | 新規 `net.brightroom.mindstock.kmp-domain`(または既存 `kmp-shared` を流用) |
| 既存ファイル | `domain/src/main/kotlin/.../HouseholdMemberRole.kt` | `domain/src/commonMain/kotlin/.../HouseholdMemberRole.kt` に移動(中身は不変) |

KMP convention の選定: `kmp-shared` は wasmJs 向け諸設定(js-joda 等)を含むので、純粋なロジック層には不要。Plan 3 では `kmp-shared` の構成を最小限に切り出した新 convention(`kmp-domain` 仮称)を作るほうが build スクリプトが薄くなる。実装時に既存の `kmp-shared` を inspect して、共通項を抜き出すか流用するかを最終判断する。

### 2.2 依存方向

```
domain                                       ──> shared:extensions       (UuidV7 のため、NEW)
shared:rpc                                   ──> domain                  (typed ID の出所統一、NEW)
backend:application:api                      ──> domain
backend:application:api                      ──> shared:rpc
backend:application:api                      ──> shared:extensions
backend:application:api                      ──> backend:infrastructure:schemas
backend:application:api                      ──> backend:infrastructure:migration:executor
backend:infrastructure:schemas               ──> domain                  (既存、JVM artifact 経由)
backend:infrastructure:migration:detector    ──> schemas + migration:annotation
backend:infrastructure:migration:generator   ──> schemas + migration:detector
backend:infrastructure:migration:executor    ──> schemas + migration:detector
```

`backend:infrastructure:schemas` は引き続き JVM only。KMP モジュールの JVM artifact を JVM モジュールが利用するのは Gradle の標準的なサポート範囲(commonMain は jvm() ターゲットの主要ソースに含まれる)。

### 2.3 追加ライブラリ

- **`kotlinx-uuid-core`**(KMP マルチプラットフォーム UUID v7 生成): `:shared:extensions` の依存に追加。Renovate 対象に含める。
- **Kotest**(`kotest-runner-junit5`, `kotest-assertions-core`, `kotest-framework-engine`): domain の `commonTest` で構成。Plan 1 で導入済みなら流用。

## 3. パッケージ構成

```
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
├── model/
│   ├── user/
│   │   ├── User.kt                                  # aggregate root
│   │   ├── UserId.kt                                # value class(Uuid)
│   │   ├── UserDisplayName.kt                       # 事実
│   │   ├── UserDisplayNameId.kt                     # value class(Long)
│   │   ├── DisplayName.kt                           # VO(max 100)
│   │   └── ZitadelSub.kt                            # VO(空文字禁止)
│   ├── household/
│   │   ├── Household.kt                             # aggregate root
│   │   ├── HouseholdId.kt                           # value class(Uuid)
│   │   ├── HouseholdMembership.kt                   # 事実
│   │   ├── HouseholdMembershipId.kt                 # value class(Long)
│   │   ├── HouseholdMembershipRevocation.kt         # 事実
│   │   ├── HouseholdMembershipRevocationId.kt       # value class(Long)
│   │   └── HouseholdMemberRole.kt                   # 既存 enum を commonMain に移動
│   ├── catalog/
│   │   ├── CatalogItem.kt                           # aggregate root
│   │   ├── CatalogItemId.kt                         # value class(Uuid)
│   │   ├── CatalogItemRevision.kt                   # 事実(name + unit)
│   │   ├── CatalogItemRevisionId.kt                 # value class(Long)
│   │   ├── CatalogItemName.kt                       # VO(max 200)
│   │   └── CatalogItemUnit.kt                       # VO(max 10)
│   ├── product/
│   │   ├── Product.kt                               # aggregate root
│   │   ├── ProductId.kt                             # value class(Uuid)
│   │   ├── ProductMinimumStock.kt                   # 事実
│   │   ├── ProductMinimumStockId.kt                 # value class(Long)
│   │   ├── ProductArchive.kt                        # 事実
│   │   ├── ProductArchiveId.kt                      # value class(Long)
│   │   └── MinimumStock.kt                          # VO(>= 0)
│   └── stock/
│       ├── StockReplenishment.kt                    # 事実
│       ├── StockReplenishmentId.kt                  # value class(Long)
│       ├── StockConsumption.kt                      # 事実
│       ├── StockConsumptionId.kt                    # value class(Long)
│       ├── StockReplenishmentCorrection.kt          # 事実
│       ├── StockReplenishmentCorrectionId.kt        # value class(Long)
│       ├── StockConsumptionCorrection.kt            # 事実
│       ├── StockConsumptionCorrectionId.kt          # value class(Long)
│       ├── Quantity.kt                              # VO(> 0、補充/消費/訂正で共通)
│       ├── OccurredAt.kt                            # VO(Instant、`<= now`)
│       ├── Note.kt                                  # VO(空文字許容)
│       └── Reason.kt                                # VO(空文字許容)
├── repository/
│   ├── user/{UserRepository, UserRegisterRepository}
│   ├── household/{HouseholdRepository, HouseholdRegisterRepository}
│   ├── catalog/{CatalogItemRepository, CatalogItemRegisterRepository}
│   ├── product/{ProductRepository, ProductRegisterRepository}
│   └── stock/{StockRepository, StockRegisterRepository}
├── exception/
│   └── DomainException.kt                           # sealed class + サブクラス
└── policy/                                          # MVP 範囲では空。将来の独自ポリシー用
```

## 4. 可視性ポリシー

`domain.model` 配下のクラス・関数・プロパティは **デフォルト `internal`**、必要に応じて `public` に格上げ。

- **クラス**: `domain` の外(`backend:infrastructure:schemas`, `backend:application:api` 等)から型として参照するため `public`(これは Kotlin の制約: internal class を public 関数のシグネチャに出せない)
- **コンストラクタ**: 外部から構築する必要があれば `public`(Repository 実装が `Product(...)` を呼ぶため、初版から `public` でよい)
- **プロパティ**: デフォルト `internal`。Repository 実装や Application 層で参照が必要になったものを Plan 5 で都度 `public` 化(理由はコミットメッセージに残す)
- **メソッド**: デフォルト `internal`。外部呼び出しが必要なメソッド(`Product.ensureNotArchived` 等のガード)は `public`

初版は「全部 internal で書いて、コンパイラに怒られたら public 化」のスタンスで進める。

## 5. Value Object のコーディング規約

ファクトリ関数(`Quantity.of(...)`)は持たない。**コンストラクタと `init {}` ブロック**で完結する。

```kotlin
@Serializable
@JvmInline
value class Quantity(private val value: Int) {
    init {
        if (value <= 0) throw DomainException.InvalidQuantity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Int = value
}
```

規約:

| 項目 | ルール |
|---|---|
| バッキングフィールド名 | `value`(統一) |
| バッキングフィールド可視性 | `private` |
| バリデーション | `init {}` で実施、違反時は `DomainException` のサブクラスを `throw` |
| `toString()` | 必須。`value.toString()` を返す |
| `invoke()` | 必要になったら定義。デフォルト `internal`、外部参照が要る場合 `public` |
| `@Serializable` | 全 VO・ID に付与(`shared:rpc` の DTO から参照されるため) |

`@Serializable` + `private val` + `value class` の組み合わせは kotlinx-serialization が公式にサポートする。最初に実装する VO(`Quantity` を予定)で `Json.encodeToString` / `decodeFromString` の動作確認テストを書き、動作を担保した上で他の VO に展開する。

### 5.1 ID 型

UUID 系 ID(集約ルート):

```kotlin
@Serializable
@JvmInline
value class ProductId(private val value: Uuid) {
    override fun toString(): String = value.toString()
    internal operator fun invoke(): Uuid = value
}
```

Long 系 ID(履歴テーブル):

```kotlin
@Serializable
@JvmInline
value class StockReplenishmentId(private val value: Long) {
    init { if (value < 0) throw DomainException.InvalidIdentity(value) }
    override fun toString(): String = value.toString()
    internal operator fun invoke(): Long = value
}
```

ID 生成は呼び出し側の責任:

```kotlin
// Application 層
val newId = ProductId(newUuidV7())   // newUuidV7 は :shared:extensions が提供
val product = Product(id = newId, householdId = ..., ...)
```

## 6. 集約と事実オブジェクト

### 6.1 共通規約

- `class` で宣言、`val` プロパティのみ(immutable)
- `data class` は使わない(等価性は ID で判断する)
- ファクトリ用 companion(`create` / `reconstitute`)は持たない。**コンストラクタで直接構築**
- ガードメソッド(不変条件チェック)は `public`(UseCase から呼ぶため)、ヘルパーは `internal`

### 6.2 各集約のアウトライン

サンプル(`Product` のみ詳細、他は同パターン):

```kotlin
class Product(
    val id: ProductId,
    val householdId: HouseholdId,
    val catalogItemId: CatalogItemId,
    val createdAt: Instant,
    val latestMinimumStock: MinimumStock?,   // 未設定なら null
    val archivedAt: Instant?,                // アーカイブ済みなら値あり
) {
    val isArchived: Boolean get() = archivedAt != null

    fun ensureNotArchived() {
        if (isArchived) throw DomainException.ProductArchived(id)
    }

    fun ensureBelongsTo(householdId: HouseholdId) {
        if (this.householdId != householdId) {
            throw DomainException.ProductNotInHousehold(id, householdId)
        }
    }
}
```

事実オブジェクト(`StockReplenishment` 等):

```kotlin
class StockReplenishment(
    val id: StockReplenishmentId,
    val productId: ProductId,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actedBy: UserId,
    val note: Note,
    val createdAt: Instant,
)
```

### 6.3 不変条件の住み分け

| 不変条件 | 守られる場所 |
|---|---|
| `quantity > 0` | `Quantity` VO の `init {}` |
| `minimum_stock >= 0` | `MinimumStock` VO の `init {}` |
| `occurred_at <= now` | `OccurredAt` VO の `init {}`(`now` を引数で受け取る) |
| `corrected_quantity > 0` | `Quantity` を再利用(同じ正整数の意味) |
| 文字列長制約(VARCHAR(N))| `DisplayName` / `CatalogItemName` / `CatalogItemUnit` の `init {}` |
| 空文字禁止(`ZitadelSub`)| `ZitadelSub.init {}` |
| 空文字許容(`Note`, `Reason`)| 検証なし(VO はあるが内容制限なし) |
| アーカイブ済み Product への新規 stock event 禁止 | `Product.ensureNotArchived()` |
| 訂正対象が当該世帯の Product であること | UseCase 側で `Product.ensureBelongsTo(householdId)` を呼ぶ |
| `(household_id, catalog_item_id)` 一意性 | DB UNIQUE。Repository 実装が SQLException を捕まえて `DuplicateAdoption` に翻訳 |

## 7. DomainException

```kotlin
sealed class DomainException(message: String) : RuntimeException(message) {

    // VO レベル
    class InvalidQuantity(val value: Int) : DomainException("quantity must be > 0, got $value")
    class InvalidMinimumStock(val value: Int) : DomainException("minimum_stock must be >= 0, got $value")
    class InvalidIdentity(val value: Long) : DomainException("identity must be >= 0, got $value")
    class OccurredAtInFuture(val value: Instant, val now: Instant) : DomainException("occurredAt $value > now $now")
    class DisplayNameTooLong(val length: Int) : DomainException("display name length $length > 100")
    class DisplayNameBlank : DomainException("display name must not be blank")
    class CatalogItemNameTooLong(val length: Int) : DomainException("catalog item name length $length > 200")
    class CatalogItemNameBlank : DomainException("catalog item name must not be blank")
    class CatalogItemUnitTooLong(val length: Int) : DomainException("catalog item unit length $length > 10")
    class CatalogItemUnitBlank : DomainException("catalog item unit must not be blank")
    class ZitadelSubBlank : DomainException("zitadel sub must not be blank")

    // Aggregate レベル
    class ProductArchived(val productId: ProductId) :
        DomainException("product $productId is archived")
    class ProductNotInHousehold(val productId: ProductId, val householdId: HouseholdId) :
        DomainException("product $productId does not belong to household $householdId")
}
```

Plan 4 では Handler は `DomainException` を catch せず透過させ、Ktor の境界 plugin(`StatusPages` or kotlinx-rpc error interceptor)で一括して RPC error に翻訳する。詳細は `2026-05-24-usecase-design.md` §5 を参照。`DomainException` は domain の純粋な語彙で、ワイヤー越境はしない(境界翻訳の責務は plugin)。

## 8. Repository ポート

5 集約 × 2(参照 + 登録)= 10 interface。**メソッド名はドメイン上の行為**(action verb)を使う。

```kotlin
// User
interface UserRepository {
    fun findById(id: UserId): User?
    fun findByZitadelSub(sub: ZitadelSub): User?
    fun findDisplayNameOf(userId: UserId): UserDisplayName?         // 最新
}
interface UserRegisterRepository {
    fun register(zitadelSub: ZitadelSub)                            // users 行 INSERT(id は呼び出し側が生成して引数化、もしくは内部生成)
    fun rename(userId: UserId, displayName: DisplayName)            // user_display_names 行 INSERT
}

// Household
interface HouseholdRepository {
    fun findById(id: HouseholdId): Household?
    fun findMembershipOf(userId: UserId): HouseholdMembership?      // 有効な最新
    fun listMembersOf(householdId: HouseholdId): List<HouseholdMembership>
}
interface HouseholdRegisterRepository {
    fun create(id: HouseholdId)
    fun join(householdId: HouseholdId, userId: UserId, role: HouseholdMemberRole)
    fun revoke(membershipId: HouseholdMembershipId)
}

// CatalogItem
interface CatalogItemRepository {
    fun findById(id: CatalogItemId): CatalogItem?                   // 最新リビジョン込み
    fun search(query: String, limit: Int = 50): List<CatalogItem>
}
interface CatalogItemRegisterRepository {
    fun register(id: CatalogItemId, createdBy: UserId, name: CatalogItemName, unit: CatalogItemUnit)
    // ↑ catalog_items + catalog_item_revisions の 2 行を 1 トランザクションで INSERT
    fun revise(catalogItemId: CatalogItemId, name: CatalogItemName, unit: CatalogItemUnit, editedBy: UserId)
}

// Product
interface ProductRepository {
    fun findById(id: ProductId): Product?                           // 最新の minimum + archive 状態込み
    fun findByHouseholdAndCatalog(householdId: HouseholdId, catalogItemId: CatalogItemId): Product?
    fun listByHousehold(householdId: HouseholdId): List<Product>
}
interface ProductRegisterRepository {
    fun adopt(id: ProductId, householdId: HouseholdId, catalogItemId: CatalogItemId)
    fun setMinimumStock(productId: ProductId, value: MinimumStock, editedBy: UserId)
    fun archive(productId: ProductId, archivedBy: UserId)
}

// Stock
interface StockRepository {
    fun findReplenishmentById(id: StockReplenishmentId): StockReplenishment?
    fun findConsumptionById(id: StockConsumptionId): StockConsumption?
    fun listReplenishmentsOf(productId: ProductId, limit: Int = 50): List<StockReplenishment>
    fun listConsumptionsOf(productId: ProductId, limit: Int = 50): List<StockConsumption>
    fun listCorrectionsOf(replenishmentId: StockReplenishmentId): List<StockReplenishmentCorrection>
    fun listCorrectionsOf(consumptionId: StockConsumptionId): List<StockConsumptionCorrection>
}
interface StockRegisterRepository {
    fun replenish(
        productId: ProductId, quantity: Quantity, occurredAt: OccurredAt,
        actedBy: UserId, note: Note,
    ): StockReplenishmentId
    fun consume(
        productId: ProductId, quantity: Quantity, occurredAt: OccurredAt,
        actedBy: UserId, note: Note,
    ): StockConsumptionId
    fun correct(
        replenishmentId: StockReplenishmentId,
        correctedQuantity: Quantity, reason: Reason, correctedBy: UserId,
    )
    fun correct(
        consumptionId: StockConsumptionId,
        correctedQuantity: Quantity, reason: Reason, correctedBy: UserId,
    )
}
```

### 8.1 戻り値の規約

- **UUID 集約の生成系**(`register` / `create` / `adopt`): `Unit`。id は UseCase 側で生成して引数として渡す。
- **UUID 集約の変更系**(`rename` / `setMinimumStock` / `archive` / `join` / `revoke` / `revise`): `Unit`。
- **Long 採番事実の生成系**(`replenish` / `consume`): **新規 id を返す**。理由: 親仕様 §6.1 の RPC 契約(`replenishStock(...): StockEventId`)で id をクライアントに返す必要があり、autoincrement のため id は INSERT 後にしか確定しないため。
- **Long 採番事実の変更系**(`correct`): `Unit`。

非対称(`replenish` / `consume` のみ戻り値あり)だが、これは Plan 2 で確定した「履歴テーブルの ID = BIGINT autoincrement」の自然な帰結。対称性のためにスキーマを UUIDv7 化する選択肢もあるが、Plan 2 のやり直しコストに見合わないため採用しない。

### 8.2 トランザクション境界

Repository インターフェースはトランザクション境界を表現しない。トランザクションは **Ktor plugin で境界に張る**(1 RPC 呼び出し = 1 transaction)。UseCase Handler は `transaction {}` を書かない。詳細は `2026-05-24-usecase-design.md` §4 を参照。

例外: `CatalogItemRegisterRepository.register(...)` は **`catalog_items` + `catalog_item_revisions` の 2 行を 1 つのトランザクションで INSERT する**ことを実装側が保証する(同等の制約として `Product` の `adopt` + 初期 `setMinimumStock` を呼び出し側で 1 トランザクションにする、等。Plan 4 の Ktor plugin transaction が 1 RPC = 1 transaction を保証するため、これは自動的に担保される)。

## 9. テスト

### 9.1 配置

```
domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/
├── model/
│   ├── user/         (DisplayNameTest, ZitadelSubTest)
│   ├── catalog/      (CatalogItemNameTest, CatalogItemUnitTest)
│   ├── product/      (MinimumStockTest, ProductGuardTest)
│   └── stock/        (QuantityTest, OccurredAtTest, NoteTest, ReasonTest)
└── exception/        (DomainExceptionTest — message 整形の sanity)
```

### 9.2 範囲

- **対象**: VO の `init {}` 検証(境界値テスト)、`toString()` 出力、`invoke()` 戻り値、Aggregate のガードメソッド、Serialization round-trip(最初に実装する VO で 1 度確認)
- **対象外**: Repository interface(interface のみで動作なし、実装は Plan 5)、`policy/`(MVP では空)

### 9.3 TDD

superpowers の `test-driven-development` スキルに従い、各 VO / Aggregate ガードは red → green → refactor の順で書く。

### 9.4 ライブラリ

`kotest-runner-junit5` / `kotest-assertions-core` / `kotest-framework-engine` を Version Catalog に追加。`domain` の `commonTest` で構成。

## 10. 実装順序(Plan 3 タスクの粒度)

1. **kotlinx-uuid 依存追加** + Version Catalog 更新
2. **`:shared:extensions` に `UuidV7.kt` 追加**(`fun newUuidV7(): Uuid`)
3. **KMP convention plugin 整備**(build-logic、`net.brightroom.mindstock.kmp-domain` 仮称)
4. **`domain/build.gradle.kts` を KMP に書き換え**、既存 `main` ソースを `commonMain` に移動、`HouseholdMemberRole.kt` のパッケージ調整
5. **`:shared:rpc` に `:domain` 依存追加**
6. **`DomainException.kt`(sealed class、サブクラスは VO テスト追加時に都度足す)**
7. **`Quantity.kt` 実装 + Kotest(`@Serializable` 動作確認含む)**
8. **残り VO(`MinimumStock`, `OccurredAt`, `DisplayName`, `ZitadelSub`, `CatalogItemName`, `CatalogItemUnit`, `Note`, `Reason`)+ テスト**
9. **ID 型 14 種**(検証ロジックは Long ID のみ `>= 0`、UUID は検証なし)
10. **集約と事実クラス(User → Household → CatalogItem → Product → Stock 順)**
11. **Aggregate ガードメソッド + テスト**
12. **Repository interface 10 個**
13. **`./gradlew :domain:check`(と影響範囲: `:shared:extensions:check`, `:shared:rpc:check`)が緑**

詳細な task 分解は writing-plans スキルで生成する Plan 3 実装ドキュメントに記載する。

## 11. 親仕様への反映が必要な変更

このドキュメント確定後、親仕様 `2026-05-23-mindstock-design.md` に以下の更新を入れる(本 PR で同時実施):

- §3.2 ディレクトリ図: `domain` を JVM-only から KMP に
- §3.2 依存方向: `shared:rpc → domain` を追加、`domain → shared:extensions` を追加
- §6.2 typed ID: 出所を `domain commonMain` に統一する旨を追記

## 12. 非ゴール / 持ち越し

- ドメインイベントの明示的な型(`sealed interface DomainEvent`): 採用しない。append-only 設計では事実テーブルの行がイベントそのものなので、追加の型は冗長
- 履歴テーブルの UUIDv7 化(Long autoincrement の置き換え): 採用しない。`replenish` / `consume` の戻り値非対称は許容
- `policy/` パッケージの中身: MVP では空。`Product.ensureNotArchived` のような単純なガードは集約に同居させる。集約をまたぐポリシーが出てきた時点で追加
- Repository 実装(Exposed): Plan 5
- UseCase(コマンドハンドラ): Plan 4
