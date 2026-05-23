# mindstock ドメインモデル リッチ化 設計ドキュメント

- 作成日: 2026-05-24
- 対象: Plan 3(マージ済 PR #50)で実装したドメイン層をリッチドメインモデルに再設計
- 親仕様: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)
- 旧設計: [2026-05-23-domain-layer-design.md](./2026-05-23-domain-layer-design.md)(置き換え)

## 1. なぜリッチ化するか

旧設計はテーブル定義をそのまま Kotlin クラスに写したような構造になり、以下の問題があった:

- `Household` が `id` と `createdAt` だけしか持たず、ドメインとして「何のためにあるか」が不明
- 集約間が ID 参照(`householdId`, `userId`)で繋がっており、object graph が表現できていない
- `id` / `createdAt` が全集約ルートに付いており、永続化(インフラ)の関心事がドメインに漏れている
- `UserDisplayName` / `HouseholdMembership` 等の "fact" クラスが集約ルートと並んで存在し、ドメインクラスとテーブル行が 1 対 1 になっている

これらを参考リポジトリ([library サンプル](https://github.com/system-sekkei/library))の構造を踏まえて作り直す。

## 2. 設計の柱

### 2.1 composition over reference

集約は他の集約の ID ではなく **モデル本体を保持** する。`Loan.member: Member`(参考)と同じく、`Household.members: HouseholdMembers` で User オブジェクトを直接保持。Repository は読み取り時に object graph を hydrate する責任を持つ。

### 2.2 read-rich, write via Repository(多引数)

集約には `rename()` のような状態遷移メソッドを置かない。**書き込みは Repository のメソッドに必要な値オブジェクト・集約を渡して行う**。集約の動作メソッドは「計算する」「問い合わせる」役割のみ:

- `household.owner(): User?`(計算)
- `household.isMember(user: User): Boolean`(問い合わせ)
- `stock.currentQuantity(): Int`(計算)

Request クラスは domain には置かない。Request は外部入力(RPC DTO)と domain の間に位置する**腐敗防止層(ACL)**の概念で、Plan 4 で `:backend:application:api` 配下に置くかどうかを再判断する。Plan 3 範囲では Repository は多引数で受ける。

### 2.3 ファーストクラスドメイン概念

集約とは別に「ドメインで意味のある計算結果」もクラスにする:

- `Stock`(在庫: 商品 + 補充消費履歴 → 現在数量)
- `ShoppingList`(買い物リスト: 在庫が閾値以下の商品群)
- `EffectiveQuantity`(実効数量: 補充/消費イベントに訂正適用後の値)

参考の `LoanStatus` / `Loanability` 等に相当するが、`rule/` のような共通サブパッケージを作らず、それぞれ独立したパッケージに置く。

### 2.4 Collection オブジェクト

`List<Xxx>` を `Xxxs` クラスでラップしてドメイン語彙を持たせる(`Products`, `HouseholdMembers`, `Replenishments` 等)。参考の `Loans.冊数()` と同じ思想。

### 2.5 id / createdAt の扱い

| 種別 | id | createdAt |
|---|---|---|
| 集約ルート(User / Household / CatalogItem / Product)| **public**(`val id`) | **削除**(インフラメタ) |
| Stock イベント(Replenishment / Consumption)| **public** | **削除**(`occurredAt` のみ残す) |
| Stock 訂正(ReplenishmentCorrection 等)| **public** | **保持**(訂正日時として domain 概念) |

ポイント:
- `id` は public だが、**domain ロジック内で `a.id == b.id` のような比較は書かない慣習**で運用(`data class` の `equals` を使う)。Repository 実装はモジュール外から id を読んで SQL に使う必要があるため public 必須
- `occurredAt` は「ユーザー入力の出来事時刻」(Replenishment / Consumption が持つ)。ドメイン概念
- 訂正の `createdAt` は「いつ訂正されたか」で domain 概念として例外的に残す

### 2.6 immutable

全 domain クラスは immutable。`val` のみ。再構築は新インスタンス生成。

### 2.7 DB スキーマは変更しない

append-only な履歴テーブル群(`user_display_names`, `household_memberships`, `catalog_item_revisions`, `product_minimum_stocks`, `stock_replenishments` 等)はそのまま維持。**Repository が DB 履歴を読んでドメインオブジェクトの "現在状態" を組み立てる**。

## 3. パッケージ構成

```
domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/
├── exception/
│   └── DomainException.kt
├── model/
│   ├── user/
│   │   ├── User.kt                     # 集約ルート(displayName 必須)
│   │   ├── UserId.kt                   # value class(private 利用)
│   │   ├── AuthIdentity.kt         # data class(provider + value)、Zitadel 固有名を排除
│   │   ├── AuthProvider.kt         # enum(ZITADEL、将来拡張)
│   │   └── DisplayName.kt
│   ├── household/
│   │   ├── Household.kt                # 集約ルート(members を持つ)
│   │   ├── HouseholdId.kt
│   │   ├── HouseholdMember.kt          # data class(user, role)
│   │   ├── HouseholdMembers.kt         # コレクション(owner(), isMember() 等)
│   │   └── HouseholdMemberRole.kt      # enum
│   ├── catalog/
│   │   ├── CatalogItem.kt              # 集約ルート(name, unit)
│   │   ├── CatalogItemId.kt
│   │   ├── CatalogItemName.kt
│   │   ├── CatalogItemUnit.kt
│   │   └── CatalogItems.kt             # コレクション(検索結果用)
│   ├── product/
│   │   ├── Product.kt                  # 集約ルート(catalogItem, minimumStock, archived)
│   │   ├── ProductId.kt
│   │   ├── MinimumStock.kt
│   │   └── Products.kt                 # コレクション(activeOnly() 等)
│   ├── stock/
│   │   ├── Stock.kt                    # 在庫状態(Product + Replenishments + Consumptions)
│   │   ├── EffectiveQuantity.kt        # 訂正適用後の数量(計算オブジェクト)
│   │   ├── Quantity.kt
│   │   ├── OccurredAt.kt
│   │   ├── Note.kt
│   │   ├── Reason.kt
│   │   ├── replenishment/
│   │   │   ├── Replenishment.kt        # public id 持ち
│   │   │   ├── ReplenishmentId.kt
│   │   │   ├── Replenishments.kt       # コレクション
│   │   │   ├── ReplenishmentCorrection.kt    # target: Replenishment(composition)
│   │   │   ├── ReplenishmentCorrectionId.kt
│   │   │   └── ReplenishmentCorrections.kt   # 単一 Replenishment への訂正群
│   │   └── consumption/
│   │       ├── Consumption.kt
│   │       ├── ConsumptionId.kt
│   │       ├── Consumptions.kt
│   │       ├── ConsumptionCorrection.kt
│   │       ├── ConsumptionCorrectionId.kt
│   │       └── ConsumptionCorrections.kt
│   └── shopping/
│       ├── ShoppingList.kt             # List<Stock> → 買うべきアイテム
│       └── ShoppingListItem.kt         # 「買うべき」状態の項目(Stock + shortage 数量)
└── repository/                          # 現状の位置を維持(Repository 移動は Plan 4 検討)
    ├── user/
    │   ├── UserRepository.kt           # 参照系
    │   └── UserRegisterRepository.kt   # 登録系(register, rename)
    ├── household/
    │   ├── HouseholdRepository.kt
    │   └── HouseholdRegisterRepository.kt
    ├── catalog/
    │   ├── CatalogItemRepository.kt
    │   └── CatalogItemRegisterRepository.kt
    ├── product/
    │   ├── ProductRepository.kt
    │   └── ProductRegisterRepository.kt
    └── stock/
        ├── StockRepository.kt
        └── StockRegisterRepository.kt
```

## 4. 集約と関連クラスの詳細

### 4.1 User

```kotlin
data class User(
    val id: UserId,
    val authIdentity: AuthIdentity,
    val displayName: DisplayName,
)

/**
 * 認証プロバイダの識別情報。OIDC の sub クレーム相当だが
 * Zitadel 固有名を domain に出さないため抽象化している。
 */
data class AuthIdentity(val provider: AuthProvider, val value: String) {
    init {
        if (value.isBlank()) throw DomainException.AuthIdentityValueBlank()
    }
}

enum class AuthProvider { ZITADEL }  // 将来追加: AUTH0 等
```

ポイント:
- `displayName` は **NOT NULL**。初回登録時に認証プロバイダから取得した名前を必ず渡して構築する
- `id` は public(`data class` で自動生成された equals / hashCode / copy / toString に含まれる)。domain ロジックで `user.id == other.id` のような比較は書かない慣習で運用
- `AuthIdentity` は Zitadel 固有名(`ZitadelSub`)を排除した抽象。MVP では `AuthProvider` enum に ZITADEL のみだが、将来 Auth0 等を追加する時に拡張可能

### 4.2 Household

```kotlin
data class Household(
    val id: HouseholdId,
    val members: HouseholdMembers,
)

data class HouseholdMember(val user: User, val role: HouseholdMemberRole)
// 注: 「revoked = true のメンバー」は HouseholdMembers から除外して読み込む(Repository 側で revocation を考慮)
// 履歴監査が必要になれば別途 read model を作る

class HouseholdMembers(private val list: List<HouseholdMember>) {
    fun owner(): User? = list.firstOrNull { it.role == OWNER }?.user
    fun activeMembers(): List<User> = list.map { it.user }
    fun contains(user: User): Boolean = list.any { it.user == user }
    fun asList(): List<HouseholdMember> = list.toList()
}
```

ポイント:
- `Household.members` は **アクティブなメンバーのみ**を持つ。revoked は domain には出さない
- メンバー関連の計算は `HouseholdMembers` に集約(`members.owner()`, `members.contains(user)`)。Household に同名メソッドを置かない
- revocation 操作は (household, user) のペアで識別(MVP では一意)

### 4.3 CatalogItem

```kotlin
data class CatalogItem(
    val id: CatalogItemId,
    val name: CatalogItemName,
    val unit: CatalogItemUnit,
)

class CatalogItems(private val list: List<CatalogItem>) {
    fun asList(): List<CatalogItem> = list.toList()
    val size: Int get() = list.size
}
```

ポイント:
- `name` / `unit` は現在値。リビジョン履歴は Repository 内部に隠す(DB の `catalog_item_revisions` テーブルは継続使用)
- `revise` は Repository メソッドで name と unit 両方を受ける。名前だけ変えたい時も呼び出し側(UseCase)が前回 unit を引き継いだ値を渡す

### 4.4 Product

```kotlin
data class Product(
    val id: ProductId,
    val catalogItem: CatalogItem,
    val minimumStock: MinimumStock?,
    val archived: Boolean,
)

class Products(private val list: List<Product>) {
    fun activeOnly(): Products = Products(list.filter { !it.archived })
    fun asList(): List<Product> = list.toList()
    val size: Int get() = list.size
}
```

ポイント:
- `catalogItem` は composition、`CatalogItem` 本体を持つ(`catalogItemId` ではない)。`name` / `unit` には `product.catalogItem.name` / `product.catalogItem.unit` で直接アクセス(forwarding プロパティは置かない)
- `minimumStock` は最新値、null なら未設定
- `archived` は最新状態、`true` なら archive 済
- `householdId` は domain には出さない。Product は **Household 経由でアクセス**することを前提(Plan 4 で UseCase が `productRepository.listOf(household)` で取得する)

### 4.5 Stock 関連

#### Replenishment / Consumption(events)

```kotlin
data class Replenishment(
    val id: ReplenishmentId,
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)

data class Consumption(
    val id: ConsumptionId,
    val product: Product,
    val quantity: Quantity,
    val occurredAt: OccurredAt,
    val actor: User,
    val note: Note,
)
```

#### Corrections

```kotlin
data class ReplenishmentCorrection(
    val id: ReplenishmentCorrectionId,
    val target: Replenishment,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val createdAt: Instant,   // 訂正日時(domain 概念として残す)
)

data class ConsumptionCorrection(
    val id: ConsumptionCorrectionId,
    val target: Consumption,
    val correctedQuantity: Quantity,
    val reason: Reason,
    val corrector: User,
    val createdAt: Instant,
)
```

訂正の `createdAt` は他の集約と違って domain で意味を持つ(「いつ訂正されたか」)。DB の `created_at` カラムをそのまま使う。

#### Collections

```kotlin
class Replenishments(private val list: List<Replenishment>) {
    fun asList(): List<Replenishment> = list.toList()
    // 訂正適用後の合計数量は、EffectiveQuantity を使って計算する(下記)
}

class Consumptions(private val list: List<Consumption>) {
    fun asList(): List<Consumption> = list.toList()
}

class ReplenishmentCorrections(private val list: List<ReplenishmentCorrection>) {
    /** 最新の訂正(id 降順で先頭)。なければ null。 */
    fun latest(): ReplenishmentCorrection? = list.maxByOrNull { it.id }
    fun asList(): List<ReplenishmentCorrection> = list.toList()
}

class ConsumptionCorrections(private val list: List<ConsumptionCorrection>) {
    fun latest(): ConsumptionCorrection? = list.maxByOrNull { it.id }
    fun asList(): List<ConsumptionCorrection> = list.toList()
}
```

#### EffectiveQuantity(計算)

```kotlin
class EffectiveQuantity(
    private val originalQuantity: Quantity,
    private val corrections: List<Quantity>,  // 訂正された数量(時系列、最新が末尾)
) {
    fun value(): Int = corrections.lastOrNull()?.value ?: originalQuantity.value
}
```

#### Stock(計算: 在庫状態)

```kotlin
class Stock(
    val product: Product,
    val replenishments: Replenishments,
    val consumptions: Consumptions,
    // 各 event 毎に訂正があれば適用するロジックを持つ
    private val replenishmentCorrectionsByEventId: Map<ReplenishmentId, ReplenishmentCorrections>,
    private val consumptionCorrectionsByEventId: Map<ConsumptionId, ConsumptionCorrections>,
) {
    fun currentQuantity(): Int {
        val replenished = replenishments.asList().sumOf { effective(it).value }
        val consumed = consumptions.asList().sumOf { effective(it).value }
        return replenished - consumed
    }

    fun needsReplenishment(): Boolean {
        val minimum = product.minimumStock?.value ?: return false
        return currentQuantity() < minimum
    }

    fun shortage(): Int {
        val minimum = product.minimumStock?.value ?: 0
        return (minimum - currentQuantity()).coerceAtLeast(0)
    }

    private fun effective(event: Replenishment): Quantity =
        replenishmentCorrectionsByEventId[event.id]?.latest()?.correctedQuantity ?: event.quantity

    private fun effective(event: Consumption): Quantity =
        consumptionCorrectionsByEventId[event.id]?.latest()?.correctedQuantity ?: event.quantity
}
```

### 4.6 ShoppingList(買い物リスト)

```kotlin
class ShoppingList(private val stocks: List<Stock>) {
    fun itemsToBuy(): List<ShoppingListItem> =
        stocks.filter { it.needsReplenishment() }
              .map { ShoppingListItem(it, shortage = it.shortage()) }
}

data class ShoppingListItem(val stock: Stock, val shortage: Int)
```

## 5. Repository ポート

書き込みは多引数で受ける(Request クラスは置かない)。読み取りは集約 / 集計値を返す。

```kotlin
// User
interface UserRepository {
    fun findByAuthIdentity(identity: AuthIdentity): User?
}
interface UserRegisterRepository {
    fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): User
    fun rename(user: User, newName: DisplayName)
}

// Household
interface HouseholdRepository {
    fun findOf(user: User): Household?     // ユーザーが所属する世帯(MVP は 1 ユーザー 1 世帯)
}
interface HouseholdRegisterRepository {
    fun create(owner: User): Household
    fun invite(household: Household, user: User, role: HouseholdMemberRole)
    fun revoke(household: Household, user: User)
}

// CatalogItem
interface CatalogItemRepository {
    fun search(query: String, limit: Int = 50): CatalogItems
    fun findById(id: CatalogItemId): CatalogItem?  // RPC 経由の id 引き(Plan 4 で取り扱い再考)
}
interface CatalogItemRegisterRepository {
    fun register(name: CatalogItemName, unit: CatalogItemUnit, createdBy: User): CatalogItem
    fun revise(catalogItem: CatalogItem, newName: CatalogItemName, newUnit: CatalogItemUnit, editedBy: User)
}

// Product
interface ProductRepository {
    fun listOf(household: Household): Products
    fun find(household: Household, catalogItem: CatalogItem): Product?
}
interface ProductRegisterRepository {
    fun adopt(household: Household, catalogItem: CatalogItem): Product
    fun setMinimumStock(product: Product, value: MinimumStock, editedBy: User)
    fun archive(product: Product, by: User)
}

// Stock
interface StockRepository {
    fun stockOf(product: Product): Stock                    // 1 商品の在庫状態
    fun stocksOf(household: Household): List<Stock>         // 世帯全商品の在庫状態(ShoppingList 用)
    fun replenishmentHistory(product: Product, limit: Int = 50): Replenishments
    fun consumptionHistory(product: Product, limit: Int = 50): Consumptions
}
interface StockRegisterRepository {
    fun replenish(
        product: Product, quantity: Quantity, occurredAt: OccurredAt,
        by: User, note: Note,
    ): Replenishment

    fun consume(
        product: Product, quantity: Quantity, occurredAt: OccurredAt,
        by: User, note: Note,
    ): Consumption

    fun correct(
        replenishment: Replenishment, correctedQuantity: Quantity,
        reason: Reason, by: User,
    ): ReplenishmentCorrection

    fun correct(
        consumption: Consumption, correctedQuantity: Quantity,
        reason: Reason, by: User,
    ): ConsumptionCorrection
}
```

メソッド引数の数が多くなる(`replenish` は 5 引数)が、Kotlin の named arguments で呼び出し側の可読性は維持できる。Request クラス化したい場合は Plan 4 で `:backend:application:api` に置く(ACL として)。

## 6. Value Object 規約(変更なし)

旧設計通り:
- `@Serializable @JvmInline value class XXX(private val value: T)`
- `init {}` で検証、`DomainException.<サブクラス>` を throw
- `toString()` 必須
- `internal operator fun invoke()` 必要に応じて

ただし **Aggregate root の ID は backing が `Uuid`**(`UUIDv7` factory は今までどおり companion object に置く):

```kotlin
@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class UserId(private val value: Uuid) {
    override fun toString(): String = value.toString()
    internal operator fun invoke(): Uuid = value
    companion object {
        fun create(): UserId = UserId(Uuid.generateV7())
    }
}
```

## 7. DomainException(簡素化)

旧設計の例外サブクラスのうち、Aggregate のガード関連(`ProductArchived`, `ProductNotInHousehold` 等)は **不要**(Aggregate の boolean property で表現できる、UseCase でチェック)。

VO 系の検証例外(`InvalidQuantity`, `InvalidMinimumStock`, `OccurredAtInFuture`, `DisplayNameTooLong`, ...)は残す。

新規追加:
- `AuthIdentityValueBlank`: `AuthIdentity(provider, value)` の value 空文字検証用
- `HouseholdHasNoOwner`(必要なら): `Household.invite` で OWNER role が既にいるかチェック等で使う場合(MVP では未使用)

削除:
- `ZitadelSubBlank` → `AuthIdentityValueBlank` に置き換え
- `ProductArchived`, `ProductNotInHousehold` などの Aggregate ガード関連

## 8. 削除されるクラス

| 旧クラス | 状態 | 代替 |
|---|---|---|
| `UserDisplayName`(fact)| 削除 | `User.displayName` に統合(Repository が DB 履歴を読んで最新を hydrate) |
| `UserDisplayNameId` | 削除 | 不要 |
| `HouseholdMembership`(fact)| 削除 | `HouseholdMember`(data class)に統合 |
| `HouseholdMembershipId` | 削除 | (household, user) で識別 |
| `HouseholdMembershipRevocation` | 削除 | revoke 操作の引数で済む |
| `HouseholdMembershipRevocationId` | 削除 | 不要 |
| `CatalogItemRevision`(fact)| 削除 | `CatalogItem.name` / `unit` に統合 |
| `CatalogItemRevisionId` | 削除 | 不要 |
| `ProductMinimumStock`(fact)| 削除 | `Product.minimumStock` に統合 |
| `ProductMinimumStockId` | 削除 | 不要 |
| `ProductArchive`(fact)| 削除 | `Product.archived` に統合 |
| `ProductArchiveId` | 削除 | 不要 |
| `StockReplenishment` → `Replenishment` | リネーム | `Replenishment`(prefix 削除、Stock の package に居るので冗長) |
| `StockReplenishmentId` → `ReplenishmentId` | リネーム | |
| `StockConsumption` → `Consumption` | リネーム | |
| `StockConsumptionId` → `ConsumptionId` | リネーム | |
| `StockReplenishmentCorrection` → `ReplenishmentCorrection` | リネーム | |
| `StockReplenishmentCorrectionId` → `ReplenishmentCorrectionId` | リネーム | |
| `StockConsumptionCorrection` → `ConsumptionCorrection` | リネーム | |
| `StockConsumptionCorrectionId` → `ConsumptionCorrectionId` | リネーム | |
| `ZitadelSub` → `AuthIdentity` + `AuthProvider` | リネーム/抽象化 | data class(`provider`, `value`)で外部認証プロバイダを domain から切り離す |

## 9. テスト方針

- VO テスト: 変更なし(Quantity, OccurredAt, etc. の境界値)
- Aggregate テスト: 新規追加。data class equality の sanity check と、`AuthIdentity` の空文字検証
- Collection テスト: `Products.activeOnly()`, `HouseholdMembers.owner()` / `contains()` 等
- Stock テスト: `Stock.currentQuantity()` の補充 - 消費計算、訂正適用、`needsReplenishment()` 判定
- ShoppingList テスト: 閾値以下の Product だけ拾えるか

旧 `ProductGuardTest` 相当は削除(ガードメソッドが Aggregate からなくなるため)。

## 10. 実装順序の概略(詳細は Plan ドキュメントに)

1. **削除フェーズ**: 旧 fact クラス + その ID を削除(11 + 10 = 21 ファイル)、テストも削除
2. **リネームフェーズ**: Stock 系 4 クラス + ID 4 個 を `Stock` prefix なしに
3. **Aggregate 再構築**: User / Household / CatalogItem / Product を新しい composition 形に書き換え
4. **新クラス追加**: HouseholdMember, Collection 群(`Products`, `HouseholdMembers`, `Replenishments` 等), Stock(計算), ShoppingList, EffectiveQuantity
5. **Repository 書き換え**: 全 register repo を多引数に
6. **テスト**: 新 spec に合わせて書き直し

## 11. 非ゴール / 持ち越し

- Repository インターフェースの位置(domain vs application): 現状 domain に置いたまま。リファレンスでは application 側だが、Plan 4 で UseCase を書く際に再考
- DB スキーマ変更: なし(履歴テーブル群はそのまま)
- Application 層(Scenario / Service クラス): Plan 4
- Read model の発展(audit view 等): MVP では不要、必要になれば追加
