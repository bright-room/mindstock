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

## 2. アクセスモデル & オンボーディング設計

### 2.1 確定したアクセスモデル(MVP)

- アプリへのログインは **Zitadel アカウント**(OIDC 認可コード + PKCE)。
- アクセス取得は **セルフサインアップ**: Zitadel でサインイン → 自分の世帯を得て **OWNER** になる。
- **初回サインイン時に User + Household + OWNER membership を原子的に自動作成**する。世帯への入力項目は存在しないため、明示的な「世帯作成」画面は設けない。
- 結果として **「登録済みだが世帯なし」状態は存在しない**(登録済み User は必ず世帯を 1 つ持つ)。
- **1 User = 1 世帯**(MVP)。
- 招待 / 複数世帯所属 / 世帯間共有は **将来スコープ**(親 spec §1.3 と一致)。`invite()` / `revoke()` は backend に実装済みだが、その UI は将来。

### 2.2 キーストーンとなる事実: 世帯には属性が無い

- スキーマ: `households(id, created_at)` のみ。
- ドメイン: `Household(id, members)` のみ。名前も設定も持たない。

世帯作成時にユーザーへ尋ねることが何も無いため、明示的な作成ステップは空のステップになる。よって **登録と一体で自動作成** するのが唯一綺麗な形であり、`RegisterFirstHouseholdScenario` で原子的に作る根拠もここにある。

### 2.3 自動作成のタイミング

初回サインインで**黙って自動作成**する(「始める」のような明示タップは設けない)。フロー:

```
サインイン(Zitadel)
  ↓
[未登録?] → 表示名入力ダイアログ
  ↓  register(displayName)
  │   = User + Household + OWNER を原子的に作成(RegisterFirstHouseholdScenario)
  ↓
[登録済] → 在庫一覧(householdId 必ずあり)

※ 「世帯なし」状態は存在しない
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
- 責務: `UserRegisterService.register(identity, displayName)` + `HouseholdRegisterService.create(ownerId)` を **1 トランザクション**で実行
- **冪等**: 既に当該 identity の User / owner 世帯が存在する場合は二重作成しない(2 回呼んでも世帯は 1 個)
- アーキテクチャルール(`software-architecture.md`)が「複数 Service をまたぐユースケース = Scenario」と定め、本 Scenario をその正規例として挙げている方針に沿う
- `UserPublicController.register` をこの Scenario 経由に差し替える(現状は `UserRegisterService.register` のみ呼び、世帯を作らない)

> 契約変更の注記: `UserPublicRpcService.register(displayName)` の戻り値は現状 `RpcResult<Profile, RpcError>`。世帯を同時に作るようになるが、戻り値の形は据え置きでよい(frontend は直後に session endpoint で householdId を取得する)。

### 3.2 Session bootstrap endpoint(薄い HTTP, RPC ではない)

WebSocket の handshake では 401 ステータスがブラウザ JS に公開されないため、現状 frontend は「ping 失敗 = すべて NeedRegister」に倒している。これを HTTP の薄いエンドポイントで解決する。

```
GET /api/v1/auth/session
  - 配置: MindstockAuthPlugin の内側 / RequireRegisteredUserPlugin の外側
  - 200 { registered: false }
        … JWT 有効・User 未登録 → NeedRegister
  - 200 { registered: true, displayName, householdId }
        … Ready(2.1 より登録済みなら householdId は必ず存在 = nullable 不要)
  - 401
        … トークン無効/期限切れ → 要 refresh / 再ログイン
```

効果:

- WS の 401 不可視問題を解決し、3 状態(reauth / NeedRegister / Ready)を HTTP ステータス + body で明確に区別できる
- `me()` 相当の profile 取得 RPC が存在しない穴を埋める(再訪ユーザーが起動時に displayName を復元できる。現状は `"user"` 固定)
- registered=true なら householdId を必ず含むため、frontend セッションモデルを nullable-free に保てる

> 補足: これは RPC ではなく素の HTTP JSON エンドポイント。レスポンス形は backend の腐敗防止として `presentation` 配下に専用 Response 型を置く。`registered=false` 時は displayName/householdId を含めない(sealed 的な 2 形)。

## 4. Frontend スコープ

現状の `App.kt`(160 行超)は AuthState 機械・コルーチン launch・OIDC redirect・callback handling・register 呼び出しまで全部入り。これを設計された土台に解体する。

### 4.1 AuthViewModel

- `lifecycle-viewmodel`(導入済み)の ViewModel
- `App.kt` から AuthState 機械と OIDC orchestration(login 開始 / callback 処理 / register)を移設
- `StateFlow<AuthState>` を公開し、UI は観測するだけ(`@Composable` から状態遷移ロジックを排除)

### 4.2 AppSession(認証後コンテキスト)

- 認証後の文脈(`tokens` + `householdId` + `displayName`)を保持
- session endpoint(§3.2)のレスポンスから構築
- 「`Ready` なら `householdId` は必ずある」を型で保証(nullable を持ち込まない)
- 後続サブプロジェクトの画面 ViewModel はここから `householdId` を得る

### 4.3 RPC アクセス統合

- `RpcClientFactory` の `open` / `openRaw` 二重実装と Base64URL エンコード重複を解消(`openRaw` はテスト helper のためだけに internal を露出している)
- `RpcCallWrapper`(401 → refresh → retry。実装済みだが App.kt に未配線)を session endpoint ベースで配線する
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
- 再訪(有効トークンあり)→ session endpoint で displayName / householdId 復元 → メインシェル
- トークン期限切れ → refresh → 復帰、または refresh 失敗 → ログイン画面
- 既存の auth テスト群(`AuthBootstrapTest` / `AuthCallbackHandlerTest` / `PkceTest` / `TokenStoreTest` / `TokensTest` / `AuthClientTest` / `RpcClientFactoryTest` / `RpcCallWrapperTest`)は維持(リファクタに追従して更新可)
- backend: `RegisterFirstHouseholdScenario` の単体テスト(冪等性含む) + session endpoint の TestApplication テスト

メインシェルの中身(在庫一覧)は #2 のスコープ。本サブプロジェクトのメインシェルは現状の placeholder を維持してよい(土台が動くことの検証が目的)。

## 6. 未決事項

1. **navigation-compose のバージョン**: Compose Multiplatform 1.11.0 に整合する `org.jetbrains.androidx.navigation:navigation-compose` のバージョンを実装フェーズで確定する。
2. **session endpoint のパス名**: `GET /api/v1/auth/session` を仮置き。`/api/v1/me` 等の別案も実装フェーズで最終決定してよい(意味は §3.2 のとおり固定)。
3. **expect/actual 統合(§4.5)**: compose-web convention の制約で実際に統合できるかは着手時に確認。できなければ現状維持で可。

## 7. 用語(本ドキュメント固有)

| 用語 | 意味 |
|---|---|
| アクセスモデル | 認証済みアイデンティティが「アプリを使える状態」になるまでのライフサイクル(本 MVP ではセルフサインアップ + 世帯自動作成) |
| オンボーディング | 初回サインインで User + Household + OWNER membership を原子的に揃える一連の処理 |
| session endpoint | 起動時ブートストラップ用の薄い HTTP エンドポイント(§3.2)。RPC ではない |
| gateway | frontend 側で RPC service をラップし `RpcResult` を結果型へマッピングする薄い層(本サブプロジェクトでは土台のみ) |
