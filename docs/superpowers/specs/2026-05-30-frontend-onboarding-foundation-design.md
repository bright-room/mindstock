# frontend オンボーディング基盤 + アーキテクチャ確立 設計ドキュメント

- 作成日: 2026-05-30
- 対象: frontend MVP の第 1 サブプロジェクト(基盤)
- 親 spec: [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md)

## 0. このドキュメントの位置づけ

frontend は認証フローと空の `AppShell`(「Hello, {name}」表示のみ)しか実装されておらず、MVP の在庫管理画面が未着手。本ドキュメントは「frontend を進める」ための **第 1 サブプロジェクト** を設計する。

第 1 サブプロジェクトは単なる画面追加ではなく、**(1) 上流のアクセスモデル(誰がどうやってアプリを使える状態になるか)の確定**、**(2) それを成立させる backend オンボーディングの穴埋め**、**(3) 後続の全画面が乗る frontend アーキテクチャの確立** を行う。

### 0.1 親 spec とのドリフトについて(重要)

親 spec [2026-05-23-mindstock-design.md](./2026-05-23-mindstock-design.md) の **§6(API 契約)と §8(Frontend)は現在の実装と乖離している**。本サブプロジェクトでは **実コードの契約を正(ground truth)** とし、親 spec の §6/§8 は更新しない(ドリフトは本ドキュメントに注記する)。

実装が正である主な点:

- モジュール構成は `:rpc` / `:domain` / `:shared`(親 spec §3 の `:shared:rpc` ではない)
- RPC は例外伝播ではなく **`RpcResult<T, RpcError>`**(`:rpc/RpcResult.kt`, `:rpc/RpcError.kt`)を返す
- RPC service は context ごとに分離: `CatalogRpcService` / `ProductRpcService` / `StockRpcService` / `HouseholdRpcService` / `UserRpcService` / `UserPublicRpcService`
- 引数・戻り値は DTO ではなく **共有ドメインモデル**(`Stock` / `Stocks` / `Product` / `Products` / `CatalogItem` / `Household` 等)と VO(`Quantity` / `OccurredAt` / `Note` / `DisplayName` 等)を直接使う
- transport は WebSocket、認証は `Sec-WebSocket-Protocol` の subprotocol に Bearer を Base64URL で載せる方式(`mindstock.v1` + `mindstock.bearer.<b64>`)

## 1. サブプロジェクト分割とシーケンス

MVP の 9 画面は 1 spec には大きすぎるため、以下に分割する。各サブプロジェクトは独立した spec → plan → 実装サイクルを持つ。

| # | サブプロジェクト | 成果物 |
|---|---|---|
| **1** | **オンボーディング基盤 + アーキテクチャ確立**(本ドキュメント) | アクセスモデル確定、backend オンボーディング Scenario + session endpoint、frontend の AuthViewModel / セッション / RPC 統合 / navigation-compose 導入 |
| 2 | 在庫一覧(current stock) | 最初の実画面。`StockRpcService.list(householdId)` → `currentQuantity()` / `needsReplenishment()` 描画。画面パターン(route → ViewModel → gateway → domain 描画)の縦切り |
| 3 | 在庫の書き込み | 補充/消費ダイアログ、履歴(`movementHistory`)、訂正 |
| 4 | 商品 / カタログ | 商品登録(カタログ検索 → 採用 + 閾値)、商品設定(閾値変更 / archive) |
| 5 | 買い物リスト + プロフィール | 買い物リスト(共有 domain `ShoppingList` で `Stocks` からクライアント側導出、追加 RPC 不要)、表示名編集 |
| 6 | PWA / オフライン対応 | Service Worker、読み取りキャッシュ、インストール |

補足:

- **買い物リストは backend 不要**。`ShoppingList(stocks).itemsToBuy()` は共有 domain なので、フロントが取得済みの `Stocks` から導出できる。
- 在庫/一覧系の RPC は全て `householdId` を取る。これは認証後に 1 回だけ取得し、セッションホルダーに保持する(本サブプロジェクトで確立)。
- **`CatalogItem.unit` → `Product` 移動は別 Plan**。「単位は世帯固有の Product に属すべき」という整合性修正(StockMovement の数量は Product に対して記録される)だが、1 ユーザー 1 世帯の MVP では実利益がほぼ無く、settled/tested/migrated な domain に触る。frontend を進める本サブプロジェクト群とは独立した「ドメイン改善 Plan」として切り出す。

## 2. アクセスモデル & オンボーディング設計

### 2.1 確定したアクセスモデル(MVP)

- アプリへのログインは **Zitadel アカウント**(OIDC 認可コード + PKCE)。
- アクセス取得は **セルフサインアップ**: Zitadel でサインイン → 自分の世帯を得て **OWNER** になる。
- **初回サインイン時に User + Household + OWNER membership を原子的に自動作成**する。世帯への入力項目は存在しないため、明示的な「世帯作成」画面は設けない。
- 結果として **「登録済みだが世帯なし」状態は存在しない**(登録済み User は必ず世帯を 1 つ持つ)。
- **1 User = 1 世帯**(MVP)。
- 招待 / 複数世帯所属 / 世帯間共有は **将来スコープ**(親 spec §1.3 と一致)。`invite()` / `revoke()` は backend に実装済みだが、その UI は将来。

### 2.2 世帯名と「一発作成」

世帯は **名前(`HouseholdName`)を持つ**(将来「1 世帯 = 複数ユーザー」で世帯を識別するためにも要る)。ただし MVP「1 ユーザー 1 世帯・実質個人利用」では初回に世帯名を入力させる UX 摩擦を避け、**表示名から導出したデフォルト名(例: 「〇〇の家」)で登録と一体に自動作成**し、後から世帯設定で変更可能とする。

- スキーマ: append-only の柱に従い `households` にカラムを足さず、**`household_names` 事実テーブル**(`user_display_names` と同パターン: `id BIGINT IDENTITY` / `household_id` / `name VARCHAR` / `created_at`、`index(household_id, id)`)で表す。最新行が現在の世帯名。
- ドメイン: `Household(id, name, members)`。`HouseholdName` VO(`DisplayName` に倣い「空白のみ禁止」、最大長は実装時に確定)。

初回は世帯名を入力させずデフォルト名で**原子的に一発作成**する(`RegisterFirstHouseholdScenario`)。これにより「登録済みだが世帯なし」の中間状態は発生しない(staged commit 不要)。世帯名の有無に関わらず、初回作成を 1 トランザクションに保つのが綺麗。

### 2.3 自動作成のタイミング

初回サインインで**黙って自動作成**する(「始める」のような明示タップは設けない)。フロー:

```
サインイン(Zitadel)
  ↓
[未登録?] → 表示名入力ダイアログ
  ↓  register(displayName)
  │   = User + Household(デフォルト名) + OWNER を原子的に作成
  │     (RegisterFirstHouseholdScenario)
  ↓
[登録済] → 在庫一覧(householdId 必ずあり)

※ 「世帯なし」状態は存在しない。世帯名は後から世帯設定で変更可能
```

### 2.4 将来「1 世帯 = 複数ユーザー」への拡張性

将来の世帯共有(1 世帯に複数 User)へ繋げられるよう、MVP でも **データモデルを将来形のまま保つ**:

- `household_memberships` は多対多のまま(`UNIQUE(user_id)` を**張らない**)。`role` / `revocations` も維持。
- 冪等性(2 回オンボーディングしても世帯が重複しない)は **Scenario レベル**で担保する。DB 制約で「1 User 1 世帯」を強制しない。
- `findOf()` の「最新の active membership を 1 つ返す」設計は、将来「現在の世帯」切替へそのまま発展できる。

この約束により、将来の世帯共有は原則 **招待 UI を足すだけ**で実現でき、ドメイン/スキーマ変更を要しない。

## 3. Backend スコープ

### 3.1 RegisterFirstHouseholdScenario

- 配置: `application/scenario/onboarding/RegisterFirstHouseholdScenario.kt`(`scenario` パッケージは現状空。本サブプロジェクトで初の Scenario)
- 責務: `UserRegisterService.register(identity, displayName)` + `HouseholdRegisterService.create(ownerId, householdName)` を **1 トランザクション**で実行。世帯名は表示名から導出したデフォルト名(例: 「〇〇の家」)を Scenario で組み立てて渡す
- **冪等**: 既に当該 identity の User が存在する場合は二重作成しない(2 回呼んでも世帯は 1 個)。判定は「事実の存在」=`userRepository.findProfileByAuthIdentity` の `ResourceNotFoundException` を握る既存慣行で行う
- 世帯名の追加に伴い `HouseholdRegisterService.create` / `HouseholdRegisterRepository.create` / `HouseholdRegisterDataSource.create` のシグネチャを `(ownerId)` → `(ownerId, householdName)` に変更し、`household_names` 事実テーブルへ INSERT する(§2.2)
- アーキテクチャルール(`software-architecture.md`)が「複数 Service をまたぐユースケース = Scenario」と定め、本 Scenario をその正規例として挙げている方針に沿う
- `register` を担う Controller をこの Scenario 経由に差し替える(現状は `UserRegisterService.register` のみ呼び、世帯を作らない)

> 契約注記: `register(displayName)` の戻り値は `RpcResult<Profile, RpcError>` のまま据え置き。世帯情報(householdId/householdName)は直後の `bootstrap()`(§3.2)で取得する。

### 3.2 初期化 route と bootstrap RPC

> 旧案(薄い HTTP `GET /api/v1/auth/session` + `Authorization: Bearer`)は **破棄した**。理由: 認証は WS subprotocol 一本に統一済みで、ブラウザの `fetch` は `Sec-` 始まりのヘッダを送れず、`Authorization` の再導入は本ブランチで閉じたばかりの動線を開け直すことになる(`docs/authentication-current-state.md`)。

WebSocket handshake の 401 がブラウザ JS に見えない問題は、**WS 経路の中で**解決する。現状 `/api/v1/user/public`(`RequireRegisteredUserPlugin` の外 = JWT 有効なら未登録でも通る唯一の route)を「**初期化 route**」と再定義し、`UserPublicRpcService` を初期化 service(改名: `OnboardingRpcService`)として `register` + `bootstrap` を持たせる。route は 1 本のまま(動線を増やさない)。

```kotlin
@Rpc
interface OnboardingRpcService {                                   // 旧 UserPublicRpcService を改名
    suspend fun register(displayName: DisplayName): RpcResult<Profile, RpcError>
    suspend fun bootstrap(): RpcResult<SessionBootstrap, RpcError>
}

// RpcResult は KrpcJson(POLYMORPHIC discriminator)なので sealed が使える = nullable 不要
@Serializable
sealed interface SessionBootstrap {
    @Serializable data object Unregistered : SessionBootstrap
    @Serializable data class Registered(
        val displayName: DisplayName,
        val householdId: HouseholdId,
        val householdName: HouseholdName,
    ) : SessionBootstrap
}
```

3 状態の区別(現状の ping「全 Throwable を Unauthorized」より正確):

- **トークン無効/期限切れ** → WS handshake 自体が失敗(ブラウザは接続例外)→ 要 refresh / 再ログイン
- **JWT 有効・未登録** → 接続成功 + `bootstrap()` が `Unregistered`(登録判定は §2「事実の存在から導出」=`MindstockAuthPlugin` が解決した `session.userId` が null)
- **登録済み** → 接続成功 + `bootstrap()` が `Registered`(displayName / householdId / householdName)

効果:

- WS 一本のまま 3 状態を区別でき、HTTP endpoint も `Authorization` も増えない
- `me()` 相当の profile 取得の穴を埋める(再訪時に displayName 復元。現状は `"user"` 固定)
- 登録判定に専用マーカーを持たず、user 行 + OWNER membership の存在から導出(§2)。sealed なので nullable ゼロ

> 改名の波及: `UserPublicRpcService` → `OnboardingRpcService` は `:rpc` モジュール + backend の Controller/Factory/DI/route に及ぶ。frontend(`App.kt` の `open("user/public")` と `register` 呼び出し)への波及は Plan 1b で吸収する。route パス(`/api/v1/user/public`)を併せて改名するかは Plan 1a の実装時に決める(機能には影響しない)。

## 4. Frontend スコープ

現状の `App.kt`(160 行超)は AuthState 機械・コルーチン launch・OIDC redirect・callback handling・register 呼び出しまで全部入り。これを設計された土台に解体する。

### 4.1 AuthViewModel

- `lifecycle-viewmodel`(導入済み)の ViewModel
- `App.kt` から AuthState 機械と OIDC orchestration(login 開始 / callback 処理 / register)を移設
- `StateFlow<AuthState>` を公開し、UI は観測するだけ(`@Composable` から状態遷移ロジックを排除)

### 4.2 AppSession(認証後コンテキスト)

- 認証後の文脈(`tokens` + `householdId` + `householdName` + `displayName`)を保持
- `bootstrap()`(§3.2)の `Registered` レスポンスから構築
- 「`Ready` なら `householdId` は必ずある」を型で保証(nullable を持ち込まない)
- 後続サブプロジェクトの画面 ViewModel はここから `householdId` を得る

### 4.3 RPC アクセス統合

- `RpcClientFactory` の `open` / `openRaw` 二重実装と Base64URL エンコード重複を解消(`openRaw` はテスト helper のためだけに internal を露出している)
- `RpcCallWrapper`(401 → refresh → retry。実装済みだが App.kt に未配線)を配線する
- context 別の薄い gateway(`RpcResult<T, RpcError>` を frontend 都合の結果型へマッピングする層)の**土台のみ**用意する。各 context の本格的な gateway 実装は #2 以降で行う

### 4.4 navigation-compose 導入

- `gradle/libs.versions.toml` に `org.jetbrains.androidx.navigation:navigation-compose` を追加(バージョンは §6 の未決事項)
- `NavHost` + ルート: login / auth callback / register / main shell
- `AuthViewModel` の `StateFlow<AuthState>` を観測してナビゲートする
- 現状 `material3-adaptive-navigation-suite` は導入済みだが、主たる画面遷移は navigation-compose で type-safe に行う(navigation suite は #2 以降の adaptive な bottom bar / rail で活用)

### 4.5 expect/actual 整理(余力で)

- `Pkce` の rejection sampling / `base64UrlNoPad` の expect/actual 分割、`SessionStorage` の wasmJs / jsIR 二重 actual を見直す
- 現状 compose-web convention の制約で webMain と commonMain が分かれている点を、`dependsOn(commonMain)` で統合できるか検討
- 本項は必須ではなく、コストが見合えば実施

## 5. 検証ライン

- 新規サインイン → 表示名入力 → (裏で世帯自動作成) → メインシェル表示、が新アーキテクチャでグリーン
- 再訪(有効トークンあり)→ `bootstrap()` で displayName / householdId / householdName 復元 → メインシェル
- トークン期限切れ → refresh → 復帰、または refresh 失敗 → ログイン画面
- 既存の auth テスト群(`AuthBootstrapTest` / `AuthCallbackHandlerTest` / `PkceTest` / `TokenStoreTest` / `TokensTest` / `AuthClientTest` / `RpcClientFactoryTest` / `RpcCallWrapperTest`)は維持(リファクタに追従して更新可)
- backend: `RegisterFirstHouseholdScenario` の単体テスト(冪等性含む)+ `bootstrap()` Controller の単体テスト + `household_names` を含む Household repository の統合テスト

メインシェルの中身(在庫一覧)は #2 のスコープ。本サブプロジェクトのメインシェルは現状の placeholder を維持してよい(土台が動くことの検証が目的)。

## 6. 未決事項

1. **navigation-compose のバージョン**: Compose Multiplatform 1.11.0 に整合する `org.jetbrains.androidx.navigation:navigation-compose` のバージョンを実装フェーズで確定する。
2. **`OnboardingRpcService` の route パス**: 改名後も route パスは `/api/v1/user/public` 据え置きか、`/api/v1/onboarding` 等へ改名するかを Plan 1a 実装時に決める(機能には影響しない。frontend 波及は 1b)。
3. **`HouseholdName` の最大長**: `DisplayName`(100)に倣うか別値か。デフォルト世帯名の導出規則(「〇〇の家」等)と併せて Plan 1a 実装時に確定。
4. **expect/actual 統合(§4.5)**: compose-web convention の制約で実際に統合できるかは着手時に確認。できなければ現状維持で可。

## 7. 用語(本ドキュメント固有)

| 用語 | 意味 |
|---|---|
| アクセスモデル | 認証済みアイデンティティが「アプリを使える状態」になるまでのライフサイクル(本 MVP ではセルフサインアップ + 世帯自動作成) |
| オンボーディング | 初回サインインで User + Household + OWNER membership を原子的に揃える一連の処理 |
| session endpoint | 起動時ブートストラップ用の薄い HTTP エンドポイント(§3.2)。RPC ではない |
| gateway | frontend 側で RPC service をラップし `RpcResult` を結果型へマッピングする薄い層(本サブプロジェクトでは土台のみ) |
