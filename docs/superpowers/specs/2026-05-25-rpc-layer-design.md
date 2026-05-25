# Plan 6: kotlinx-rpc 層の wiring — 設計

作成日: 2026-05-25
対象: backend の RPC 層導入(presentation 層実装、Ktor wiring、認証スタブ、集約の `@Serializable` 化)

結合テスト(testApplication + Testcontainers での RPC 越しエンドツーエンド)は Plan 7 で別途扱う。
本格的な認証 (JWT 等) も別 Plan。

## 1. 背景と目的

Plan 5 までで domain / Repository / Handler は揃った。一方で `shared/rpc` は placeholder のみ、
`RoutingConfiguration` の `routing {}` は空で、kotlinx-rpc のエンドポイントは未 wiring。

Plan 6 では「frontend が呼び出せる RPC API の枠組み」を整える。

**スコープに含む**:
- kotlinx-rpc の service interface と Request 型を `shared/rpc/commonMain` に定義
- 集約 (User / Household / Membership / CatalogItem / Product / Stock / Movement) と
  必要な内部型に `@Serializable` を追加(domain モジュール)
- backend 側に Service 実装(`presentation/rpc/` 配下)
- Ktor の Authentication Plugin をスタブ実装で導入
- Ktor の StatusPages で例外 → HTTP ステータスのマッピング
- Routing で `rpc(...) { registerService<...> { ... } }` を配線
- DI に Service 実装を追加
- 集約の serializer round-trip テスト
- Service Impl の最小単体テスト

**スコープ外**:
- 結合テスト (testApplication 越しの RPC 呼び出し) → Plan 7
- 本格的な認証 (JWT、OAuth、IdP 連携) → 別 Plan
- frontend からの呼び出しコード → frontend 着手 Plan

## 2. 全体構成

```
shared/rpc/commonMain/
  service/
    UserPublicRpcService        // 認証不要(register のみ)
    UserRpcService              // 認証必須(rename)
    HouseholdRpcService
    CatalogRpcService
    ProductRpcService
    StockRpcService
  request/                       // Request data class(VO 直接使用)

domain/                          // 集約・関連 enum・必要な内部 VO に @Serializable 追加

backend/application/api/
  presentation/rpc/
    user/UserPublicRpcServiceImpl, UserRpcServiceImpl
    household/HouseholdRpcServiceImpl
    catalog/CatalogRpcServiceImpl
    product/ProductRpcServiceImpl
    stock/StockRpcServiceImpl
  configuration/
    auth/
      AuthConfiguration.kt        // install(Authentication) { bearer("user") { ... } }
      MindstockPrincipal.kt       // data class MindstockPrincipal(val userId: UserId) : Principal
      StubAuthProvider.kt         // Bearer token = UserId 文字列を素通し
      ActorResolver.kt            // ApplicationCall.actor(userRepository): User
    error/
      ErrorConfiguration.kt       // StatusPages: 例外 → HTTP ステータスマッピング
      RpcExceptions.kt            // UnauthorizedException, NotFoundException
    routing/
      RoutingConfiguration.kt     // rpc(...) { registerService<...> { ... } } を 6 ルート分
    di/
      DependenciesConfiguration.kt  // Service 実装 6 個を provide 追加
```

mapper 専用層は作らない(集約・VO が直接 `@Serializable` になるため)。

## 3. RPC service 設計

5 領域 + 認証外の UserPublic を加えた 6 service。`Query`/`Command` の suffix は付けない。

### UserPublicRpcService (認証不要)
- `register(displayName: DisplayName, authIdentity: AuthIdentity): User`

### UserRpcService (認証必要)
- `rename(displayName: DisplayName): User`

### HouseholdRpcService (認証必要)
- `findOf(): Household?`
- `create(name: HouseholdName, ...): Household`
- `invite(householdId: HouseholdId, invitee: UserId): Membership`
- `revoke(householdId: HouseholdId, target: UserId)`

### CatalogRpcService (認証必要)
- `search(query: String, limit: Int): List<CatalogItem>`
- `findById(id: CatalogItemId): CatalogItem?`
- `register(name: CatalogItemName, unit: CatalogItemUnit): CatalogItem`
- `revise(id: CatalogItemId, name: CatalogItemName?, unit: CatalogItemUnit?): CatalogItem`

### ProductRpcService (認証必要)
- `listOfHousehold(householdId: HouseholdId): List<Product>`
- `find(id: ProductId): Product?`
- `adopt(householdId: HouseholdId, catalogItemId: CatalogItemId): Product`
- `setMinimumStock(id: ProductId, minimumStock: MinimumStock?): Product`
- `archive(id: ProductId)`

### StockRpcService (認証必要)
- `get(productId: ProductId): Stock`
- `list(householdId: HouseholdId): List<Stock>`
- `movementHistory(productId: ProductId, limit: Int): List<Movement>`
- `replenish(productId: ProductId, qty: Quantity, occurredAt: OccurredAt, note: Note): Replenishment`
- `consume(productId: ProductId, qty: Quantity, occurredAt: OccurredAt, note: Note): Consumption`

各メソッドの戻り値・引数とも、domain 型 (VO / 集約 / enum) を直接使う。中間 DTO は作らない。

## 4. 集約の `@Serializable` 追加方針

### 対象

- 集約: `User`, `Household`, `Membership`, `CatalogItem`, `Product`, `Stock`, `Movement` (`Replenishment` / `Consumption` 等)
- enum: `HouseholdMemberRole`, `MovementKind`(存在すれば), 他
- 内部 VO で未 `@Serializable` のもの(`HouseholdName` 等あれば棚卸し)

### 方針

- factory(`User.register()` 等)で守る不変条件はそのまま維持
- シリアライゼーション経由の構築は「Repository が DB から復元する」のと同じ低レベル経路扱い
- Request 側で集約を丸ごと受け取るメソッドは作らない(VO/ID のみ)ことで、外部入力が
  factory を迂回して invariant を破壊するルートを遮断
- 過剰露出が気になるフィールドは `@Transient` で除外。Plan 6 着手時に棚卸し:
  - `User.authIdentity` は外に出さない(`@Transient`)
  - 他は実コード見て個別判断
- まず全部出してみて、frontend が動き始めた段階で問題が見つかれば View 型に切り出す
  (Plan 7 以降の判断)

## 5. 認証 Plugin

### 構成

```
configuration/auth/
  AuthConfiguration.kt        Application.authConfigure()
  MindstockPrincipal.kt       data class MindstockPrincipal(val userId: UserId) : Principal
  StubAuthProvider.kt         Bearer token → UserId 解決(開発/テスト専用)
  ActorResolver.kt            ApplicationCall.actor(UserRepository): User
```

### スタブ仕様

- `Authorization: Bearer <uuid>` の `<uuid>` を `UserId` として扱う
- DB に存在しない UserId なら `UnauthorizedException`
- 本物の JWT 検証は別 Plan(コードコメントで明示)

### ActorResolver

継承を使わず、free function として提供:

```kotlin
suspend fun ApplicationCall.actor(userRepository: UserRepository): User {
    val principal = principal<MindstockPrincipal>() ?: throw UnauthorizedException()
    return userRepository.findById(principal.userId) ?: throw UnauthorizedException()
}
```

各 Service Impl はコンストラクタで `UserRepository` を受け、必要なメソッド先頭で
`currentCall().actor(userRepository)` を呼んで `User` を取得する。
(`currentCall()` は kotlinx-rpc が coroutine context 経由で提供するアクセサ。
正確な API は Plan 6 着手時に kotlinx-rpc 0.10.2 のドキュメントで確認。)

### 認証境界

```kotlin
routing {
    // 認証不要
    rpc("/api/v1/user/public") {
        registerService<UserPublicRpcService> { resolve() }
    }
    // 認証必要
    authenticate("user") {
        rpc("/api/v1/user")      { registerService<UserRpcService>      { resolve() } }
        rpc("/api/v1/household") { registerService<HouseholdRpcService> { resolve() } }
        rpc("/api/v1/catalog")   { registerService<CatalogRpcService>   { resolve() } }
        rpc("/api/v1/product")   { registerService<ProductRpcService>   { resolve() } }
        rpc("/api/v1/stock")     { registerService<StockRpcService>     { resolve() } }
    }
}
```

`UserRpcService.register` が公開ルート、それ以外が認証必須という分離のため、
register のみ別 interface (`UserPublicRpcService`) として切り出す。

## 6. エラーマッピング

`presentation/rpc/` に最小限の例外型を定義:

- `UnauthorizedException`: 認証失敗 / Principal なし / User 見つからず
- `NotFoundException`: 集約 resolve 失敗(`productRepository.findById(...)` が null 等)

`StatusPages` で HTTP ステータスにマッピング:

| 例外 | ステータス |
|---|---|
| `UnauthorizedException` | 401 |
| `NotFoundException` | 404 |
| `IllegalArgumentException` (domain VO 構築失敗) | 400 |
| その他 | 500 |

kotlinx-rpc の例外シリアライゼーション機構は標準のままにし、Plan 6 では細工しない。

## 7. DI

`DependenciesConfiguration` に 6 service 実装を追加。既存の Repository / Handler 登録は変更なし。

```kotlin
provide<UserPublicRpcService> { UserPublicRpcServiceImpl(resolve(), resolve()) }
provide<UserRpcService>       { UserRpcServiceImpl(resolve(), resolve()) }
provide<HouseholdRpcService>  { HouseholdRpcServiceImpl(resolve(), resolve(), resolve(), resolve(), resolve()) }
provide<CatalogRpcService>    { CatalogRpcServiceImpl(...) }
provide<ProductRpcService>    { ProductRpcServiceImpl(...) }
provide<StockRpcService>      { StockRpcServiceImpl(...) }
```

各 Service Impl は以下を依存にとる:
- 自領域の Handler 群
- 集約 resolve 用の Repository(自領域 + 必要に応じ他領域、例: Stock は Product 取得が必要)
- `UserRepository`(Principal → User 解決のため)

## 8. テスト戦略

Plan 6 のテストは以下のみ。本格カバーは Plan 7。

1. **集約の serializer round-trip テスト**(`domain/src/commonTest/`)
   - 各集約(7 種前後)について `Json.encodeToString` → `decodeFromString` で同値
   - 約 7 本

2. **Service Impl の最小単体テスト**(`backend/application/api/src/test/`)
   - 各 Service Impl の 1 メソッド、依存を mock した最小ケース
   - 目的: 「Principal → User 解決 → Handler 呼び出し」の wiring 確認
   - 約 5 本

3. **Smoke build**
   - `:backend:application:api:build` が通る
   - `:shared:rpc:build` が通る(wasm/jvm 含む)
   - `:frontend:composeApp:build` が壊れない

## 9. ビルド設定

既存:
- `shared/rpc/build.gradle.kts` は kotlinx-rpc プラグイン・serialization 済み
- `backend/application/api/build.gradle.kts` は kotlinx-rpc server, server-auth, server-auth-jwt,
  status-pages 全て含む

Plan 6 で **build.gradle.kts への新規追加は基本ない**。コードだけ書く。

## 10. 未確定 / 後続 Plan に持ち越し

- 本格認証 (JWT / OAuth / IdP) 設計 → 別 Plan
- testApplication ベースの結合テスト → Plan 7
- 集約の過剰露出フィールドがあった場合の View 型分離 → frontend 着手後の判断
- backend モジュール構造の見直し(Repository 実装を infrastructure 層へ等)→
  memory `structure-review-pending` 参照、別途
