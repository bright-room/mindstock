# UseCase 層設計

- 対象: Plan 4(UseCase 層: Command Handler 風の薄いアプリケーション層)
- 親仕様: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)、[2026-05-23-domain-layer-design.md](./2026-05-23-domain-layer-design.md)
- 前提: Plan 3(domain layer)・domain-richness・stock-movements-unification が完了済み
- 後続: Plan 5(Repository の Exposed 実装)、Plan 6(kotlinx-rpc サービス実装)

## 1. ゴール

`:backend:application:api` 配下に、Repository ポートを呼び出してドメイン操作を実行する **薄いアプリケーション層** を実装する。リッチドメインモデルを前提に、Handler 自体は thin pass-through とし、ロジックは domain 集約に寄せる。

## 2. クラス構造: Command Handler 風

1 操作 1 クラス。命名は `<動詞><集約>Handler`。

```kotlin
package net.brightroom.mindstock.application.usecase.user

class RegisterUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        identity: AuthIdentity,
        defaultName: DisplayName,
    ): User = userRegisterRepository.register(identity, defaultName)
}
```

### 2.1 命名規約

- クラス: `<動詞><対象>Handler`(例: `RegisterUserHandler`、`ReplenishStockHandler`、`ListProductsOfHouseholdHandler`)
- メソッド: `handle(...)`(常に固定)
- パラメータ: domain の Value Object / 集約を直接受ける。プリミティブや RPC DTO は受けない
- 返り値: domain の集約 / 集合 / Unit

### 2.2 引数の渡し方

UseCase は **named arguments の多引数** で受ける。Request クラスを application 層には作らない。

RPC 境界では `shared:rpc` 側に Request DTO が定義され、RPC ハンドラが DTO を分解して Handler の `handle(...)` を呼ぶ。

```kotlin
// shared:rpc 側
@Serializable data class ReplenishStockRequest(...)

// :backend:application:api 側 RPC ハンドラ(Plan 6)
suspend fun replenish(request: ReplenishStockRequest) {
    val product = productRepository.findById(request.productId) ?: throw ...
    replenishStockHandler.handle(
        product = product,
        quantity = Quantity(request.quantity),
        occurredAt = OccurredAt(request.occurredAt),
        by = currentUser,
        note = Note(request.note),
    )
}
```

### 2.3 パッケージ構成

`:backend:application:api` の `src/main/kotlin/net/brightroom/mindstock/application/usecase/` 配下を、ドメイン集約ごとに切る。

```
application/
└── usecase/
    ├── user/
    │   ├── RegisterUserHandler.kt
    │   └── RenameUserHandler.kt
    ├── household/
    │   ├── CreateHouseholdHandler.kt
    │   ├── InviteMemberHandler.kt
    │   ├── RevokeMembershipHandler.kt
    │   └── FindHouseholdOfUserHandler.kt
    ├── catalog/
    │   ├── RegisterCatalogItemHandler.kt
    │   ├── ReviseCatalogItemHandler.kt
    │   ├── SearchCatalogItemsHandler.kt
    │   └── FindCatalogItemByIdHandler.kt
    ├── product/
    │   ├── AdoptProductHandler.kt
    │   ├── SetMinimumStockHandler.kt
    │   ├── ArchiveProductHandler.kt
    │   ├── ListProductsOfHouseholdHandler.kt
    │   └── FindProductHandler.kt
    └── stock/
        ├── ReplenishStockHandler.kt
        ├── ConsumeStockHandler.kt
        ├── GetStockHandler.kt
        ├── ListStocksHandler.kt
        └── GetMovementHistoryHandler.kt
```

domain 側の `domain.model.<aggregate>` / `domain.repository.<aggregate>` とパッケージ階層を対称にする。

## 3. スコープ: 実装する UseCase 一覧

Repository ポートの全メソッドと 1:1 対応。read / write をフルセットで実装する。

| 集約 | Handler | 呼び出す Repository メソッド |
|---|---|---|
| User | `RegisterUserHandler` | `UserRegisterRepository.register` |
| User | `RenameUserHandler` | `UserRegisterRepository.rename` |
| Household | `CreateHouseholdHandler` | `HouseholdRegisterRepository.create` |
| Household | `InviteMemberHandler` | `HouseholdRegisterRepository.invite` |
| Household | `RevokeMembershipHandler` | `HouseholdRegisterRepository.revoke` |
| Household | `FindHouseholdOfUserHandler` | `HouseholdRepository.findOf` |
| CatalogItem | `RegisterCatalogItemHandler` | `CatalogItemRegisterRepository.register` |
| CatalogItem | `ReviseCatalogItemHandler` | `CatalogItemRegisterRepository.revise` |
| CatalogItem | `SearchCatalogItemsHandler` | `CatalogItemRepository.search` |
| CatalogItem | `FindCatalogItemByIdHandler` | `CatalogItemRepository.findById` |
| Product | `AdoptProductHandler` | `ProductRegisterRepository.adopt` |
| Product | `SetMinimumStockHandler` | `ProductRegisterRepository.setMinimumStock` |
| Product | `ArchiveProductHandler` | `ProductRegisterRepository.archive` |
| Product | `ListProductsOfHouseholdHandler` | `ProductRepository.listOf` |
| Product | `FindProductHandler` | `ProductRepository.find` |
| Stock | `ReplenishStockHandler` | `StockRegisterRepository.replenish` |
| Stock | `ConsumeStockHandler` | `StockRegisterRepository.consume` |
| Stock | `GetStockHandler` | `StockRepository.stockOf` |
| Stock | `ListStocksHandler` | `StockRepository.stocksOf` |
| Stock | `GetMovementHistoryHandler` | `StockRepository.movementHistory` |

合計 **20 Handler**。Repository ポートが増えた時点で同期して新規 Handler を追加する。

## 4. トランザクション境界

`transaction { }` は **アプリ層に書かない**。Ktor の plugin として境界に張る。

### 4.1 方針

`:backend:application:api` の Ktor configuration 側で plugin を 1 つ追加し、kotlinx-rpc 呼び出しを `transaction(db) { ... }` で囲む。Handler / Repository 実装は `transaction {}` を一切書かない。Repository 実装は内側で `TransactionManager.currentOrNull()` を経由して plugin の transaction を拾う。

### 4.2 実装スケッチ

```kotlin
// configuration/transaction/ExposedTransactionPlugin.kt
val ExposedTransactionPlugin = createApplicationPlugin("ExposedTransaction") {
    val db = application.get<Database>()  // Koin から取得
    onCall { call ->
        // kotlinx-rpc 呼び出しを transaction で囲む
        // 実装詳細(intercept のフック点)は Plan 4 実装時に確定
    }
}
```

実装時に確定すること:
- kotlinx-rpc の interceptor フックがあるか確認(無ければ Ktor の `intercept(ApplicationCallPipeline.Call)` で代用)
- WebSocket 経由の RPC で「1 frame = 1 transaction」が成立するか確認

### 4.3 Read / Write の区別

区別しない。read 系も write 系も同じ通常 transaction を使う。理由:

- Postgres の read-only transaction の節約効果は微小(WAL 書き込みなし、snapshot 取得のみ)
- 真のボトルネックは Connection pool。read-only 化しても pool 圧迫は変わらない
- mindstock の想定ワークロード(家庭単位、世帯員数人)で問題化しない
- 将来計測してから `transaction(readOnly = true)` を選択的に入れる

### 4.4 idle-in-transaction の懸念

1 RPC = 1 transaction なので、ハンドラが外部 API 待ち等で長引くと idle-in-transaction が積もる。MVP の RPC はすべて自社 DB 内で完結する想定なので問題なし。将来「外部 API を叩く UseCase」が出てきた時に plugin の対象外にする仕組みを後付けする。

## 5. エラー処理

`DomainException` は Handler を **透過** させ、境界 plugin で一括して RPC error にマップする。

### 5.1 方針

- Handler は `try/catch` を書かない
- 親仕様(`domain-layer-design.md` §例外翻訳)の「UseCase 層で catch して InventoryException 等に翻訳」という記述は **本仕様で上書き**。MVP では境界翻訳に統一
- アプリ層の例外型(`ApplicationException` / `InventoryException`)は **新規には作らない**

### 5.2 境界での翻訳

Ktor の `StatusPages` plugin(または kotlinx-rpc の error interceptor)で `DomainException` を捕捉し、RPC error に変換する。

```kotlin
install(StatusPages) {
    exception<DomainException> { call, cause ->
        // DomainException のサブクラスごとに RPC error にマップ
    }
}
```

具体的なマッピング(`DomainException` → RPC error の対応)は Plan 6 で kRPC サービス実装と同時に定義する。Plan 4 では「Handler は throw を素通り」とだけ守れば良い。

### 5.3 想定外の例外

`DomainException` 以外(`IllegalStateException` 等)は捕まえず、Ktor のデフォルト 500 ハンドリングに任せる。

## 6. テスト戦略

### 6.1 Plan 4 範囲のテスト

**ロジックがある Handler だけ選択的に MockK で単体テスト**。thin pass-through な Handler は単体テストを書かない。

判定基準:
- Repository を 1 つだけ呼んで結果を返す → テスト不要(タウトロジー)
- 複数 Repository をオーケストレーションする / 条件分岐がある → テスト対象

現状の Handler は大半が thin pass-through。実装時に「テストを書く Handler」を明示的にリストアップする。

テストは `:backend:application:api` の `src/test/kotlin` に Kotest + MockK で書く。

### 6.2 Plan 4 範囲外のテスト

**結合テスト**(実 Repository + 実 PostgreSQL on Testcontainers)は **Plan 5 (Repository 実装) で必ず実施**。Plan 4 ではやらない。これは結合テストを放棄したという意味ではない。

**RPC 統合テスト**(kotlinx-rpc in-memory transport)は **Plan 6 (RPC 実装) で実施**。

## 7. DI 配線

Koin で Handler 群を登録する。

```kotlin
// configuration/di/UseCaseModule.kt
val useCaseModule = module {
    singleOf(::RegisterUserHandler)
    singleOf(::RenameUserHandler)
    singleOf(::CreateHouseholdHandler)
    // ...
}
```

Repository 実装は Plan 5 で別 module として配線される。Plan 4 段階では Repository ポートは **未配線**(実装が存在しないため、Koin module への登録は Plan 5 で追加)。Plan 4 ではコンパイルが通り、単体テスト(モック)が走ることだけを保証する。

`:backend:application:api` の `MainKt` で `useCaseModule` を `modules { useCaseModule }` で登録する。

## 8. Repository ポートの位置・ID 引き

- **Repository ポートは `domain/repository/` のまま据え置く**。Application 層には移さない
- `findById(id)` 系メソッドの扱いは **Plan 4 では触らない**。現状の Repository ポートにあるもの(`CatalogItemRepository.findById` 等)はそのまま使う。再設計は Plan 6 で RPC 側と合わせて検討

## 9. 親仕様(domain-richness-design / domain-layer-design)の更新

本仕様の決定に合わせて、親仕様の以下を更新する(Plan 4 着手前に別 PR で実施):

- `domain-richness-design.md` の以下「未解決(Plan 4-5 で設計)」記述を削除:
  - L33(Request クラスの位置): 「`:backend:application:api` 配下に置くかどうかを再判断」 → 削除(本仕様で「shared:rpc 側、application には作らない」と確定)
  - L59 / L266 / L367 / L458(Stock 訂正の domain↔DB 対応付け): stock-movements-unification で訂正概念ごと廃止により自動解消。古い記述を削除
  - L123 / L544(Repository ポートの位置): 「Plan 4 で再考」 → 削除(本仕様で「domain 据え置き」と確定)
  - L408(`findById` の取扱い): 「Plan 4 で再考」 → 「Plan 6 で再考」に変更
  - L546(Application 層の構造): 「Plan 4」 → 本仕様で確定したので削除
- `domain-layer-design.md` L288(UseCase 層で DomainException を翻訳): 「Plan 4 では境界 plugin で翻訳。詳細は `2026-05-24-usecase-design.md` §5 を参照」と差し替え
- `domain-layer-design.md` L381(UseCase 層がトランザクション開閉): 「Ktor plugin で境界トランザクション。Handler は `transaction {}` を書かない。詳細は同上 §4」と差し替え

## 10. 対象外(明示的に Plan 5 / 6 で扱う)

- Repository の Exposed 実装(Plan 5)
- Testcontainers による Handler + Repository + 実 DB の結合テスト(Plan 5)
- kotlinx-rpc サービス IF と Handler 配線(Plan 6)
- RPC error mapping の具体(Plan 6)
- 認証(JWT 検証 / current user 取得)の Ktor 側実装(Plan 6 or 別途)
- WebSocket 接続単位の transaction 戦略の確定検証(Plan 6 で kRPC 配線時)
