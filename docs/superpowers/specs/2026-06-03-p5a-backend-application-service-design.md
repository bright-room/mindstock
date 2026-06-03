# P5a backend application Service 層 設計

家庭在庫管理 SaaS「mindstock」フルリプレイス P5 のサブプロジェクト 1。`:backend:core` に **application 層(Service / Scenario)** と、`infrastructure` に **`ExternalProductGateway`** を新設し、P3 で確定した `@Rpc` 契約をバックエンドで満たせる状態にする。認証(P5b)・presentation Controller / 起動配線(P5c)は含めない。

- 上位ロードマップ: P5 = backend application(本書 P5a)+ 認証(P5b)+ presentation/配線(P5c)。`full-replace-2026-06` 参照。
- 前段の確定物: `:domain`(P1/P2)、`:rpc` 契約(P3)、`:backend:core` の Repository interface + Exposed DataSource(P4、main マージ済 PR #96)。
- 本書は ICONIX(`docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/02-iconix.md`)の control 層(application Service)を詳細化したもの。

## スコープ

### 含む

- `:backend:core` `application/service/<ctx>/` に Service(Register 系 / 参照系、RPC に対称)
- `:backend:core` `application/scenario/<ctx>/` に Scenario(複数 Service / コンテキストをまたぐユースケース)
- `:backend:core` `infrastructure/gateway/` に `ExternalProductGateway` interface + 1 実装(プロバイダは実装時に確定)
- 既存 Repository interface + DataSource への **読み取りメソッド追加**(`existsByJan` / `listWanted`)
- `Household` への **owner 権限チェック公開メソッド**追加(invitation 系 Scenario が呼ぶ)
- Service / Scenario の単体テスト(mockk、意味のある orchestration のみ)
- `:backend:core` の `build.gradle.kts` に Ktor client(gateway 用)を追加

### 含まない(別サブプロジェクト)

- 認証(Zitadel OIDC / JWT / `MindstockSession`)= **P5b**
- presentation Controller(`@Rpc` 実装)・認可(role 検査)・Routing / DI・`Database`/Hikari/Flyway 起動配線・例外 → `RpcError` 翻訳・`ActivityFeed`/`InvitationPreview` の組み立て = **P5c**
- frontend = P6

## 原則の踏襲

`.claude/rules/software-architecture.md` / `error-handling.md` / `rpc-and-transactions.md` に従う。要点:

- **層と依存方向**: presentation → application ← infrastructure / domain は横断。**application / infrastructure は presentation(`:rpc` の `RpcError`/`RpcResult`、`MindstockSession`)に依存禁止**。本書の Service / Scenario / Gateway は **`:rpc` を import しない**(戻り値は domain 型のみ)。
- **Service = 薄い orchestration**。ビジネス判定・状態遷移・計算は domain。Service は「集約 fetch → domain メソッド → 保存 / 複数 Repository 順序制御」のみ。
- **Scenario = 複数 Service をまたぐユースケース**(`application/scenario/<ctx>/<UseCase>Scenario.kt`)。Scenario 同士の呼び出し不可。1 Service で済むなら Scenario を作らず Controller が直接 Service を呼ぶ。
- **nullable 戻り値禁止**。不在は infrastructure が `ResourceNotFoundException`、Service / Scenario は **catch せず素通し**(null チェックも書かない)。
- **戻り値は VO / 集約 / ファーストクラスコレクション**。primitive / raw `List` を公開しない。一覧は空でも空 FCC。
- **トランザクション境界は DataSource 自前**(`transaction(database){}`)。Service / Scenario は tx を意識しない。
- **命名対称**: `<Ctx>Service` / `<Ctx>RegisterService` / `<Ctx>Scenario`。

## 設計判断(本書で確定)

P4 から P5a に送られた要解決事項と、ブレストで確定した判断:

1. **`correct()` は append-only / 単一書き込み**。`Stock.correct()` は対象 movement を打ち消す `Correction` movement を 1 件生成するだけで、qty は `StockMovements.netQuantity()` の畳み込みで導出される(別途 qty 更新 INSERT は無い)。よって `StockRegisterService.correct` の永続化は `StockRegisterRepository.appendMovement` 1 本。`replenish` / `consume` も同様に単一書き込み。→ クロス書き込みの原子性問題は発生しない。

2. **補充時に手動 wanted を触らない**。プロトタイプ / UC14 step3 の「補充時 wanted=false に戻す」は **意図的に見送る**。理由: `appendMovement`(stock context)+ `product_wanted_events`(product context)の 2 書き込みになり、DataSource 単独 tx 境界(P4 ルール)と両立しないため。手動希望は `setWanted` RPC でのみ操作する。`replenish` は movement 追記のみ。

3. **重複 JAN 判定は read 先行**。`ProductRepository.existsByJan(householdId, jan): Boolean` を新設。判定対象は **採用中 + アーカイブ済(非 tombstone)** の Product(アーカイブ品の復元で JAN 衝突しないため archived も含める)。append-only revision 構造では DB unique 制約が張りにくいため read ベースで判定し、家庭規模の低並行性で TOCTOU を許容する。`adopt`(catalog 由来は必ず JAN あり)と `addCustom` の `Barcode.Linked(jan)` 時に適用。`Barcode.Unlinked`(JAN 無し)はチェックしない(設計判断 #5)。衝突時は `DuplicateJanException` を throw。

4. **invitation 系の owner 認可は domain で判定**。`Invitation` 集約は世帯メンバーシップを知らないため、`createInvite` / `revokeInvite` の owner-only 認可は Scenario が `Household` を load し、**domain に追加する公開メソッド** `Household.requireCanManage(by: ResidentId)`(`RolePermissions(role, HouseholdCapability.世帯管理).isAllowed()` を内部で使い、不可なら `OwnerRequiredException`)で判定する。判定ロジックは domain に保ち、Scenario は呼ぶだけ。`rename` / `changeRole` / `removeMember` は既に集約メソッドが内部で同等チェックを行うため追加不要。

5. **`:rpc` DTO のマッピングは P5c Controller が両方向で担う(application は :rpc 非依存)**。`:backend:core` は `:rpc` を import しない。入出力とも :rpc DTO ↔ domain VO の変換は P5c Controller の責務(腐敗防止層):
   - **入力**: `AddCustomProductRequest`(:rpc)は Service 引数にせず、Controller が `name`/`barcode`/`unit`/`minimumStock` の domain VO に分解して `ProductRegisterService.addCustom(...)` を呼ぶ。
   - **出力**: `ActivityFeed`/`ActivityEntry`/`InvitationPreview`(:rpc)は Service が返さず、Controller が domain の戻り値から組み立てる。
   - **`previewInvite`**: `InvitationPreview` は (`householdName`, `grantedRole`) の射影。`Invitation` は `householdId` のみ保持し世帯名を持たないため、世帯名は `Household` を load して得る。**副作用の無い read 専用**のため、P5c Controller が `InvitationService.findByCode` + `HouseholdService.findById`(内部メソッド)を直接呼んで組み立てる(Scenario / domain read-model は作らない)。`HouseholdService.findById` は **RPC では公開しない内部メソッド**(任意 householdId での世帯詳細閲覧=非メンバーへの漏洩を防ぐため、`getHousehold(householdId)` RPC は作らない)。

6. **`activity` は既存 reader 再利用**。`StockRpcService.activity` の `ActivityFeed`(movement に Product を添えた射影)は P5c Controller が組み立てる。P5a の `StockService.activity(householdId)` は既存の `StockRepository.listByHousehold(householdId): Stocks`(Product + movements を内包)を返すだけ。Controller が各 Stock の movements を `ActivityEntry(product, movement)` に flatten する。

7. **`ShoppingList` 合成入力の wanted reader**。`ShoppingList` は read-model(`ShoppingEntry(stock, manuallyWanted)` の集合)。手動希望は Stock / Product 集約に持たない(P2 決定)ため、`ProductRepository.listWanted(householdId): Products`(現在手動希望中の Product)を新設し、`ProductService.shoppingList` が `Stocks` と突き合わせて合成する。

## モジュール / パッケージ構成

```text
:backend:core
  application/
    service/
      resident/   ResidentService, ResidentRegisterService
      household/  HouseholdService, HouseholdRegisterService
      invitation/ InvitationService, InvitationRegisterService
      catalog/    CatalogService
      product/    ProductService, ProductRegisterService
      stock/      StockService, StockRegisterService
    scenario/
      product/    AdoptProductScenario
      invitation/ CreateInvitationScenario, RevokeInvitationScenario
      household/   JoinHouseholdScenario
    repository/   (既存。読み取りメソッドを追加)
  infrastructure/
    gateway/      ExternalProductGateway(interface) + <Provider>ProductGateway(impl)
    datasource/   (既存。existsByJan / listWanted の DataSource 実装を追加)
:domain
  household/      Household に requireCanManage(by) を追加
```

## Service 詳細(RPC 契約への対応)

引数の `actor: ResidentId` は P5c Controller が `MindstockSession` から渡す。movement の `actor: Resident` や `Household.create(owner: Resident)` が必要な箇所は、Service が `ResidentRepository.findById(actor)` で Resident を load する。

### resident

```kotlin
class ResidentRegisterService(
    private val residentRepository: ResidentRepository,
    private val residentRegisterRepository: ResidentRegisterRepository,
) {
    // UC2 初回登録。authIdentity は session 由来。Resident をここで採番して返す
    fun register(authIdentity: AuthIdentity, displayName: DisplayName): Resident =
        residentRegisterRepository.registerResident(authIdentity, displayName)

    // 表示名変更(append-only。new display_name を 1 行追記)
    fun rename(actor: ResidentId, displayName: DisplayName) =
        residentRegisterRepository.appendDisplayName(actor, displayName)
}

class ResidentService(private val residentRepository: ResidentRepository) {
    fun me(actor: ResidentId): Resident = residentRepository.findById(actor)  // 不在は infra が例外
}
```

### household

```kotlin
class HouseholdRegisterService(
    private val residentRepository: ResidentRepository,
    private val householdRepository: HouseholdRepository,
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    // UC3 作成。作成者が owner(世帯主)
    fun create(name: HouseholdName, actor: ResidentId): Household {
        val owner = residentRepository.findById(actor)
        val household = Household.create(name, owner)   // domain factory
        householdRegisterRepository.registerHousehold(household)
        return household
    }

    fun rename(householdId: HouseholdId, name: HouseholdName, actor: ResidentId) {
        val household = householdRepository.findById(householdId)
        household.rename(name, actor)                   // domain が owner 認可 + 不変条件
        householdRegisterRepository.appendHouseholdName(householdId, name)
    }

    fun leave(householdId: HouseholdId, actor: ResidentId) {
        val household = householdRepository.findById(householdId)
        household.leave(actor)                           // domain が LastOwner 判定
        householdRegisterRepository.removeMember(householdId, actor)
    }

    fun changeRole(householdId: HouseholdId, target: ResidentId, role: HouseholdMemberRole, actor: ResidentId) {
        val household = householdRepository.findById(householdId)
        household.changeRole(target, role, actor)        // domain が owner / LastOwner 判定
        householdRegisterRepository.changeMemberRole(householdId, target, role)
    }

    fun removeMember(householdId: HouseholdId, target: ResidentId, actor: ResidentId) {
        val household = householdRepository.findById(householdId)
        household.removeMember(target, actor)            // domain が owner / LastOwner 判定
        householdRegisterRepository.removeMember(householdId, target)
    }
}

class HouseholdService(private val householdRepository: HouseholdRepository) {
    fun list(actor: ResidentId): Households = householdRepository.listByResident(actor)
    fun findById(householdId: HouseholdId): Household = householdRepository.findById(householdId)  // 内部用。RPC 非公開
}
```

> `rename`/`changeRole`/`removeMember` は集約メソッド呼び出し後に永続化する。集約メソッドが投げる `OwnerRequiredException` / `LastOwnerException` / `ResourceNotFoundException` は素通し(P5c で `RpcError` に翻訳)。集約が「現状のメンバー構成」を必要とするため `findById` で fetch 済みの `Household` に対して呼ぶ。

### invitation

```kotlin
class InvitationRegisterService(
    private val invitationRegisterRepository: InvitationRegisterRepository,
) {
    // 永続化のみ(owner 認可・household 整合は Scenario が担う)。code PK 衝突は repo がリトライ
    fun issue(invitation: Invitation): Invitation = invitationRegisterRepository.issue(invitation)
    fun revoke(code: InvitationCode) = invitationRegisterRepository.revoke(code)
}

class InvitationService(private val invitationRepository: InvitationRepository) {
    fun findByCode(code: InvitationCode): Invitation = invitationRepository.findByCode(code)  // 不在は infra 例外
}
```

### catalog

```kotlin
class CatalogService(
    private val catalogRepository: CatalogRepository,
    private val catalogRegisterRepository: CatalogRegisterRepository,
    private val externalProductGateway: ExternalProductGateway,
) {
    fun search(name: CatalogItemName, limit: Int): CatalogItems = catalogRepository.search(name, limit)

    // UC11,12: master 照合 → 未存在で外部 API → hit で cache 保存 → どちらも無ければ NotFound(素通し)
    fun lookupByJan(jan: Jan): CatalogItem =
        try {
            catalogRepository.findByJan(jan)                 // master ヒット
        } catch (e: ResourceNotFoundException) {
            val fetched = externalProductGateway.fetch(jan)  // 不在/失敗時は ResourceNotFoundException(素通し)
            catalogRegisterRepository.register(fetched)      // cache。同時 JAN は unique 衝突を repo 側で吸収
            fetched
        }
}
```

> `lookupByJan` は「master 不在 → 外部にフォールバック」という**分岐に意味がある**ため、ここでの `catch (ResourceNotFoundException)` は「素通し禁止」の例外ではなくフォールバック制御として許容する(error-handling ルールの「不在=例外」を分岐の起点に使う形)。外部 gateway 自体の不在/失敗は再び `ResourceNotFoundException` として上位に素通しし、フロントの手入力フォールバックに繋ぐ。

### product

```kotlin
class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productRegisterRepository: ProductRegisterRepository,
    private val stockRepository: StockRepository,  // archive/unarchive は Stock.archive() の在庫 0 ガードを使う
) {
    // adopt の product 側(catalogItem は AdoptProductScenario が解決して渡す)
    fun adopt(catalogItem: CatalogItem, householdId: HouseholdId, unit: ProductUnit, minimumStock: MinimumStock): Product {
        if (productRepository.existsByJan(householdId, catalogItem.jan)) throw DuplicateJanException("already adopted: ${catalogItem.jan}")
        val product = Product.adopt(catalogItem, unit, minimumStock)
        productRegisterRepository.registerAdopted(product, householdId, catalogItem.id)
        return product
    }

    // 引数は domain VO のみ(:rpc の AddCustomProductRequest は受けない=層依存回避)。
    // Request の分解は P5c Controller が行う(出力 DTO 組立と対称)
    fun addCustom(householdId: HouseholdId, name: ProductName, barcode: Barcode, unit: ProductUnit, minimumStock: MinimumStock): Product {
        // Barcode.Linked(jan) のときのみ重複チェック(Unlinked はスキップ)
        (barcode as? Barcode.Linked)?.let {
            if (productRepository.existsByJan(householdId, it.jan)) throw DuplicateJanException("already adopted: ${it.jan}")
        }
        val product = Product.custom(name, barcode, unit, minimumStock)
        productRegisterRepository.registerCustom(product, householdId)
        return product
    }

    // 変更系は immutable 再構築 → product_revisions に 1 行 append
    fun changeUnit(productId: ProductId, unit: ProductUnit) { /* findById → setting 差し替え → appendRevision */ }
    fun changeMinimum(productId: ProductId, minimumStock: MinimumStock) { /* 同上 */ }
    fun changeImage(productId: ProductId, image: ProductImage) { /* findById → image 差し替え → appendRevision */ }
    fun archive(productId: ProductId) {
        // Stock.archive() が在庫 0 を保証するため、Stock を load して archive する必要がある
        // → archive/unarchive は stock を介す(下記「archive の所在」参照)
    }
    fun unarchive(productId: ProductId) { /* 同上 */ }

    fun setWanted(productId: ProductId, wanted: Boolean) = productRegisterRepository.setWanted(productId, wanted)
}

class ProductService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
) {
    fun list(householdId: HouseholdId): Stocks = stockRepository.listByHousehold(householdId)      // 数量+status を見せるため Stock 集合
    fun listArchived(householdId: HouseholdId): Products = productRepository.listArchivedByHousehold(householdId)

    // read-model 合成: Stock 集合 × 手動希望 → ShoppingList
    fun shoppingList(householdId: HouseholdId): ShoppingList {
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds = productRepository.listWanted(householdId).list.map { it.id }.toSet()  // local set(公開 API ではない)
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
    }
}
```

**archive / unarchive の所在(要確定の実装詳細)**: `archive` は「在庫 0 のときのみ可」を `Stock.archive()` が保証する(`CannotArchiveWithStockException`)。`Product.archive()` は無条件。RPC は `ProductRegisterRpcService.archive(productId)` だが、在庫ガードを効かせるため **`Stock` を load して `stock.archive()` を呼び、変更後 Product を `appendRevision`** する必要がある。よって archive/unarchive は `ProductRegisterService` が `StockRepository`(read)も注入して `Stock` 経由で実行する。`changeUnit`/`changeMinimum`/`changeImage` は在庫に依存しないため Product 単独で再構築する。

### stock

```kotlin
class StockRegisterService(
    private val residentRepository: ResidentRepository,
    private val stockRepository: StockRepository,
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(productId: ProductId, quantity: Quantity, note: Note, actor: ResidentId) {
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val replenished = stock.replenish(quantity, OccurredAt.now(), resident, note)  // domain。occurredAt はサーバ確定
        stockRegisterRepository.appendMovement(productId, replenished.latestMovement())
    }

    fun consume(productId: ProductId, quantity: Quantity, note: Note, actor: ResidentId) {
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByProduct(productId)
        val consumed = stock.consume(quantity, OccurredAt.now(), resident, note)        // 不足は InsufficientStockException(素通し)
        stockRegisterRepository.appendMovement(productId, consumed.latestMovement())
    }

    // RPC correct は productId を受けず MovementId のみ。stock_movements は productId を持つため
    // findByMovement で対象を含む Stock を丸ごと load する
    fun correct(target: MovementId, correctedQuantity: Quantity, reason: Reason, actor: ResidentId) {
        val resident = residentRepository.findById(actor)
        val stock = stockRepository.findByMovement(target)
        val corrected = stock.correct(target, correctedQuantity, reason, resident, OccurredAt.now())
        stockRegisterRepository.appendMovement(stock.product.id, corrected.latestMovement())
    }
}

class StockService(private val stockRepository: StockRepository) {
    fun history(productId: ProductId): StockMovements = stockRepository.historyOf(productId)
    fun activity(householdId: HouseholdId): Stocks = stockRepository.listByHousehold(householdId)  // Controller が ActivityFeed に flatten
}
```

> **domain API 追加(確定)**: `Stock` の変更メソッド(`replenish`/`consume`/`correct`)は `MovementIdentity.Pending` の movement を末尾追加した新 `Stock` を返すが、追記対象を取り出すアクセサが無い。**`Stock.latestMovement(): StockMovement`**(`movements` の末尾=今追加した movement を返す)を domain に追加し、register service がこれを `appendMovement` に渡す。`StockRegisterRepository.appendMovement(productId, movement)` は採番済み `Persisted` movement を返すが、本フローでは戻り値不要。
>
> **`correct` の Stock load(確定)**: `StockRegisterRpcService.correct(target, correctedQuantity, reason)` は productId を受けない。`stock_movements` は productId(FK)を持つため、**`StockRepository.findByMovement(movementId): Stock`** を新設し、対象 movement を含む Stock を丸ごと load する(productId を別途解決するより素直)。不在は `ResourceNotFoundException`。

## Scenario 詳細

```kotlin
// adopt: catalog(item 解決)+ product(重複チェック+採用)
class AdoptProductScenario(
    private val catalogService: CatalogService,
    private val productRegisterService: ProductRegisterService,
) {
    fun run(householdId: HouseholdId, catalogItemId: CatalogItemId, unit: ProductUnit, minimumStock: MinimumStock): Product {
        val item = catalogService.findById(catalogItemId)   // CatalogService に findById を公開(内部用)
        return productRegisterService.adopt(item, householdId, unit, minimumStock)
    }
}

// createInvite: household(owner 認可)+ invitation(発行)
class CreateInvitationScenario(
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(householdId: HouseholdId, role: HouseholdMemberRole, actor: ResidentId): Invitation {
        val household = householdService.findById(householdId)
        household.requireCanManage(actor)                   // domain。不可なら OwnerRequiredException
        return invitationRegisterService.issue(Invitation.issue(householdId, role))
    }
}

// revokeInvite: invitation(解決)+ household(owner 認可)+ invitation(失効)
class RevokeInvitationScenario(
    private val invitationService: InvitationService,
    private val householdService: HouseholdService,
    private val invitationRegisterService: InvitationRegisterService,
) {
    fun run(code: InvitationCode, actor: ResidentId) {
        val invitation = invitationService.findByCode(code)
        householdService.findById(invitation.householdId).requireCanManage(actor)
        invitationRegisterService.revoke(code)
    }
}

// join: invitation(解決+有効性)+ resident(actor load)+ household(join)
class JoinHouseholdScenario(
    private val invitationService: InvitationService,
    private val residentService: ResidentService,
    private val householdRegisterService: HouseholdRegisterService,
) {
    fun run(code: InvitationCode, actor: ResidentId): Household {
        val invitation = invitationService.findByCode(code)
        if (!invitation.usable()) throw InvitationInvalidException("invitation not usable: $code")
        val resident = residentService.me(actor)
        return householdRegisterService.join(invitation.householdId, resident, invitation.grantedRole)
    }
}
```

> `HouseholdRegisterService` に `join(householdId, resident, grantedRole)` を追加(`findById` → `household.join(resident, role)` → `joinMember`)。`CatalogService` に `findById(catalogItemId)`(内部用、`catalogRepository.findById` 素通し)を追加。

## Repository / Domain への追加

| 種別 | 追加 | 用途 |
|---|---|---|
| `ProductRepository`(read) | `existsByJan(householdId: HouseholdId, jan: Jan): Boolean` | 重複 JAN 判定(採用中+アーカイブ済) |
| `ProductRepository`(read) | `listWanted(householdId: HouseholdId): Products` | ShoppingList 合成(現在の手動希望) |
| `StockRepository`(read) | `findByMovement(movementId: MovementId): Stock` | `correct` の対象 Stock を丸ごと load |
| `CatalogRepository`(read) | (既存 `findById` を Service から利用) | adopt の item 解決 |
| `Household`(domain) | `requireCanManage(by: ResidentId)` | invitation 系 owner 認可(`RolePermissions(role, 世帯管理)`、不可で `OwnerRequiredException`) |
| `Stock`(domain) | `latestMovement(): StockMovement` | 追記対象(末尾の Pending movement)の取得 |

DataSource 実装(`ProductDataSource` への `existsByJan` / `listWanted`、`StockDataSource` への movement→product reader)は `transaction(database){}` 自前境界・ROW_NUMBER Window(current 取得)・tombstone 除外という P4 規約に従う。`existsByJan` は採用中+アーカイブ済(非 tombstone)を対象に `product_barcodes` side-table を JAN で引く。

## ExternalProductGateway

```kotlin
// infrastructure/gateway/ExternalProductGateway.kt
interface ExternalProductGateway {
    /** JAN で外部商品 API を照会し CatalogItem(id は新規採番)を返す。不在/失敗は ResourceNotFoundException。 */
    fun fetch(jan: Jan): CatalogItem
}
```

- **interface 抽象化**(ブレスト決定)。具体プロバイダ(楽天市場商品検索 / Yahoo!ショッピング等)は実装時に確定。1 実装を `<Provider>ProductGateway` として用意し、DI で差し替え可能にする。
- 実装は Ktor client(cio)+ ContentNegotiation。base URL / application-id 等は config 駆動。timeout / レート制限 / パース失敗 / ヒット 0 件は一律 `ResourceNotFoundException` に倒す(理由の出し分けはしない=フロントは手入力フォールバックへ)。
- `:backend:core` `build.gradle.kts` に Ktor client 依存を追加(`ktorLib.client.cio`、`ktorLib.client.contentNegotiation`、`ktorLib.serialization.kotlinx.json`)。
- 外部呼び出しは I/O 境界。Service は `fetch` を呼ぶだけで HTTP / リトライ詳細を意識しない。

## エラー設計

- VO 値域違反 = `IllegalArgumentException`(domain VO の `init { require }`)。wire 経由でも保たれる。
- 単一値不在 = `ResourceNotFoundException`(infrastructure が throw、Service / Scenario 素通し)。
- 業務前提崩れ = 専用例外(`DuplicateJanException` / `CannotArchiveWithStockException` / `InsufficientStockException` / `LastOwnerException` / `OwnerRequiredException` / `InvitationInvalidException`)。
- これら例外 → `RpcError`(Unauthorized/NotFound/BadRequest/Conflict/Internal)の翻訳は **P5c の Controller** が行う(本書では翻訳しない)。

## テスト方針

`.claude/rules/testing.md` 準拠(テスト関数名は日本語、意味のあるテストのみ、機械的網羅は避ける)。

- **Service / Scenario**: mockk で repository / gateway をスタブし、**意味のある orchestration** のみ検証:
  - `CatalogService.lookupByJan` の master ヒット / 外部フォールバック / 外部も不在 の 3 分岐(`register` 呼び出し有無を含む)
  - `ProductRegisterService.adopt` / `addCustom(Linked)` の重複 JAN で `DuplicateJanException`、`addCustom(Unlinked)` で重複チェックしない
  - Scenario の Service 連携順序・owner 認可(`requireCanManage` 不可で `OwnerRequiredException` 素通し)・`join` の `usable()` 偽で `InvitationInvalidException`
  - `ShoppingList` 合成(wanted ID 突き合わせ)
  - 例外素通し(repo の `ResourceNotFoundException` を Service が catch せず伝播)
- 単純な 1:1 委譲(`me` / `history` / `list` 等)は mockk 検証する意味が薄いため最小限。
- DataSource 追加分(`existsByJan` / `listWanted` / movement→product reader)は P4 同様、本書では Repository/infra 単体テストを書かず、**P5c の Service 結合 or integrationTest** で吸収する。

## 受け入れ条件

- `:backend:core:build` 緑(新 Service / Scenario / Gateway interface + 追加 repo メソッド + domain 追加分)。
- 上記「意味のある」Service / Scenario テストが通る。
- application / infrastructure が `:rpc`(`RpcError`/`RpcResult`)・`MindstockSession` を import していない(層依存の検証)。
- 全 `@Rpc` 契約メソッドに対応する application 側の入口(Service or Scenario)が存在する(P5c が Controller から呼べる状態)。
- `ExternalProductGateway` interface + 1 実装が DI 可能な形で存在する。

## P5b / P5c へ送る前提

- **P5b(認証)**: `MindstockSession` は `AuthIdentity` を保持(未登録 Resident は `residentId == null`)。`registerDisplayName` / `me` は未登録ユーザの扱いが分岐する。`ResidentRepository.findByAuth` を認証配線が利用。
- **P5c(presentation/配線)**: Controller が ① 例外 → `RpcError` 翻訳、② :rpc DTO ↔ domain VO の双方向マッピング(`AddCustomProductRequest` 分解、`ActivityFeed`/`InvitationPreview` 組み立て)、③ role 認可(viewer 読取専用等、domain が enforce しない範囲)、④ `MindstockSession` から `actor` 抽出、⑤ `Database`/Hikari/Flyway/DI 起動配線 を担う。`correct` は productId を Controller から渡さず、Service が `StockRepository.findByMovement` で解決する。
