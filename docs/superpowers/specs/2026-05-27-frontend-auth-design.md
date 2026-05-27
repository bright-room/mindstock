# Frontend Plan 1: 認証(Zitadel OIDC PKCE クライアント) — 設計

作成日: 2026-05-27
対象: frontend の認証実装(Zitadel に対する OIDC PKCE フロー、token 保管、kotlinx-rpc 接続への注入、未登録ユーザーの register、ログアウト)

backend 側の JWT 検証は Plan 8(`2026-05-26-auth-design.md`)で完了済み。本 Plan ではそれに接続するクライアント側を実装する。

## 1. 背景と目的

frontend は現在 `App.kt` が `Text("mindstock")` のスタブのみ。backend は `Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<base64url(jwt)>` で JWT を受け取る前提で実装済み。MVP の UI(在庫一覧等)を作る前に、まず Zitadel ログイン → access_token 取得 → kotlinx-rpc 接続に token 注入 → 未登録なら `register` 呼び出し → ログアウトまでを一通り通す。

**スコープに含む**:
- Kotlin/Wasm でハンドロールする OIDC PKCE Authorization Code Flow
- access_token / refresh_token / id_token を `sessionStorage` に保持
- 起動時の token 復元 + 期限事前リフレッシュ
- kotlinx-rpc client への token 注入(Sec-WebSocket-Protocol 経由)
- 401 検知時の 1 度きり refresh + retry
- 起動時 ping で User 未登録を検知 → `UserPublicRpcService.register(displayName)` を実行する登録ダイアログ
- `oidc/v1/end_session` を叩くログアウト
- OIDC 設定値(issuer / client_id / redirect_uri / post_logout_redirect_uri / audience / project_id)を環境変数 → Gradle → Kotlin 定数で注入
- 起動時の `/auth/callback` 経路ハンドリング

**スコープ外**:
- 在庫 / 買い物リスト / 購入登録 / 消費登録 の UI(別 Plan)
- 世帯メンバーシップ管理 UI(別 Plan)
- i18n / a11y / ダークモード / ログイン画面のビジュアルデザイン
- PWA manifest / service worker
- マルチタブ間の token 同期(BroadcastChannel)
- silent renew(iframe 経由)
- 本番デプロイ用の Zitadel 設定 / TLS / Secret 管理

## 2. 全体構成

```
[Browser]
  Compose Multiplatform (Wasm)
    ├ AuthClient      ── ktor-client → Zitadel /oauth/v2/{authorize,token}, /oidc/v1/end_session
    ├ TokenStore      ── window.sessionStorage
    ├ AuthBootstrap   ── 起動時に token を復元 / 期限チェック / 必要なら refresh
    ├ RpcClientFactory── kotlinx-rpc client を「access_token を Sec-WebSocket-Protocol に乗せて」生成
    ├ AppViewModel    ── AuthState: LoggedOut / Authenticating / NeedRegister / Ready / Error
    └ UI 階層
        ├ LoginScreen         (未認証時)
        ├ RegisterDialog      (Zitadel ログイン済だが app User 未登録時)
        ├ AppShell (stub)     (Ready 時。中身はあとの Plan)
        └ AuthCallbackScreen  (OAuth redirect 戻り受け)
```

- `AuthClient` は kotlinx-rpc とは別の ktor-client インスタンスを持つ(OIDC エンドポイントは通常の HTTP)
- `RpcClientFactory` は AccessToken が変わるたびに新しい RPC クライアントを作る(WebSocket は接続時に token を埋め込むため再接続が必要)
- ルーティングは Compose 側で `window.location.pathname == "/auth/callback"` 判定のみ。専用ルーティングライブラリは導入しない

## 3. データフロー

### 3.1 初回ログイン(PKCE Authorization Code Flow)

```
1. User が LoginScreen の「ログイン」を押下
2. AuthClient:
   - code_verifier (43-128 chars, random) を生成
   - code_challenge = base64url(sha256(code_verifier))
   - state (CSRF nonce) を生成
   - sessionStorage に { code_verifier, state, return_to } を保存
   - window.location.assign(`${issuer}/oauth/v2/authorize?
       response_type=code
       &client_id=${client_id}
       &redirect_uri=${redirect_uri}
       &scope=openid profile offline_access urn:zitadel:iam:org:project:id:${project_id}:aud
       &state=${state}
       &code_challenge=${code_challenge}
       &code_challenge_method=S256`)
3. Zitadel で認証 → redirect_uri?code=...&state=... に戻る
4. AuthCallbackScreen が code/state を解釈:
   - state を sessionStorage の値と照合(不一致なら拒否)
   - POST ${issuer}/oauth/v2/token
       grant_type=authorization_code
       code=...
       redirect_uri=...
       client_id=...
       code_verifier=...
   - 戻りの { access_token, refresh_token, expires_in, id_token } を TokenStore へ
   - return_to に window.history.replaceState で戻る
```

`scope` の `urn:zitadel:iam:org:project:id:${project_id}:aud` は Zitadel 特有の指定で、これがないと access_token の `aud` が backend が期待する API Resource ID にならない。Plan 8 で `AUTH_AUDIENCE` に設定した値と一致させるため、`project_id` も env で注入する。

### 3.2 起動時のブートストラップ

```
App() 起動
  ↓
AuthBootstrap.start():
  if window.location.pathname == "/auth/callback":
      → AuthCallbackScreen を表示 (上記 3.1 step 4 を実行)
  else:
      tokens = TokenStore.load()
      if tokens == null:           → state = LoggedOut
      else if tokens.willExpireWithin(60s):
          tokens = AuthClient.refresh(tokens.refresh_token)
          TokenStore.save(tokens)
      → state = Authenticating

  Authenticating:
      rpc = RpcClientFactory.create(tokens.access_token)
      try rpc<HouseholdRpcService>.findOf()  // user レルムに ping
        → 成功:        state = Ready
        → 401:         state = NeedRegister
        → その他失敗: state = Error
```

ping に使う RPC は「副作用なし・必ず authenticate 必須・1 RPC で済む」もの。`HouseholdRpcService.findOf(): Household?` を採用(401 = 未登録、null = 世帯未作成だが User 登録済み、Household = Ready)。

### 3.3 401 リトライ

`RpcCallWrapper`(薄いラッパ)が:

```
try rpc.call()
catch UnauthorizedException:
    if 既に refresh 済み (この呼び出しサイクル内):
        → AuthState = LoggedOut
    else:
        new_tokens = AuthClient.refresh(refresh_token)
        TokenStore.save(new_tokens)
        RpcClientFactory.recreate(new_tokens.access_token)  // WS 再接続
        retry rpc.call()
```

kotlinx-rpc の WebSocket は接続確立後に token を差し替えられない(handshake 時にしか乗らない)ため、refresh 後は必ず RPC クライアントを作り直す。UI 上は短い再接続として現れる。

### 3.4 ログアウト

```
1. AuthClient.logout():
   id_token = TokenStore.id_token
   TokenStore.clear()
   RpcClientFactory.close()  // WS を閉じる
   window.location.assign(`${issuer}/oidc/v1/end_session?
       id_token_hint=${id_token}
       &post_logout_redirect_uri=${post_logout_redirect_uri}`)
2. Zitadel 側でセッション破棄 → post_logout_redirect_uri に戻る
3. アプリは LoggedOut 状態で起動(LoginScreen 表示)
```

`post_logout_redirect_uri` も Zitadel に事前登録必要。§4.1 の env 注入対象に含める。

## 4. コンポーネント構成

`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/` 配下:

```
auth/
  AuthConfig.kt          ── issuer / clientId / redirectUri / postLogoutRedirectUri / audience / projectId
                            (生成 Kotlin object)
  AuthClient.kt          ── PKCE 生成 / authorize URL 構築 / token endpoint / refresh / end_session
                            ktor-client (js engine) を内部に持つ pure な class。
  TokenStore.kt          ── sessionStorage への save/load/clear。expires_at を一緒に保存。
  Tokens.kt              ── data class Tokens(accessToken, refreshToken, idToken, expiresAt)
                            willExpireWithin(seconds) ヘルパ
  Pkce.kt                ── code_verifier / code_challenge 生成 (Web Crypto API を JS interop で呼ぶ)
  AuthState.kt           ── sealed: LoggedOut / Authenticating / NeedRegister / Ready(tokens) / Error(message)
  AuthBootstrap.kt       ── 起動時フローの司令塔。state を遷移させる。
  AuthCallbackHandler.kt ── /auth/callback の処理 (state 照合 + token 交換)

rpc/
  RpcClientFactory.kt    ── access_token を受け取って kotlinx-rpc client を作る/破棄する
                            Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<b64url>
  RpcCallWrapper.kt      ── 401 を 1 度だけ refresh + retry する suspend ヘルパ

ui/
  App.kt                 [改] AuthState で分岐するシェル
  login/LoginScreen.kt   [新] 「ログイン」ボタンのみ
  register/RegisterDialog.kt [新] displayName 入力 → UserPublicRpcService.register
  callback/AuthCallbackScreen.kt [新] ローディング表示のみ
  shell/AppShell.kt      [新] Ready 時の stub。"Hello, ${user.displayName}" と Logout ボタン。
```

### 4.1 設定値の注入

`frontend/build.gradle.kts` に Gradle task を追加:

```kotlin
val generateAuthConfig = tasks.register("generateAuthConfig") {
    val outDir = layout.buildDirectory.dir("generated/auth")
    outputs.dir(outDir)
    doLast {
        val issuer = System.getenv("AUTH_ISSUER") ?: "http://localhost:8081"
        val clientId = System.getenv("AUTH_CLIENT_ID") ?: error("AUTH_CLIENT_ID required")
        val redirectUri = System.getenv("AUTH_REDIRECT_URI") ?: "http://localhost:8080/auth/callback"
        val postLogoutRedirectUri = System.getenv("AUTH_POST_LOGOUT_REDIRECT_URI") ?: "http://localhost:8080/"
        val audience = System.getenv("AUTH_AUDIENCE") ?: error("AUTH_AUDIENCE required")
        val projectId = System.getenv("AUTH_PROJECT_ID") ?: error("AUTH_PROJECT_ID required")
        outDir.get().file("AuthConfig.kt").asFile.writeText(/* package + object 定義 */)
    }
}
kotlin.sourceSets.commonMain.kotlin.srcDir(generateAuthConfig)
```

- `AUTH_CLIENT_ID` / `AUTH_AUDIENCE` / `AUTH_PROJECT_ID` は Zitadel 初回セットアップ後にしか確定しないため、デフォルト値を持たせず欠けたらビルド失敗とする
- `mise.toml` の `[env]` セクションで開発デフォルトを管理(README に記載)

### 4.2 値の公開性に関する補足

`issuer` / `client_id` / `redirect_uri` / `audience` は OAuth 2.0 / OIDC 仕様上クライアント側に平文で持つことが前提のため、Wasm バイナリから読み取れることは問題ない(`mindstock-frontend` は public client = client_secret を持たない)。

漏れたらマズいのは access_token / refresh_token / backend 側の secret であり、それらは frontend には置かない。

## 5. エラーハンドリング

| 失敗箇所 | 検知 | ユーザー向け挙動 |
|---|---|---|
| Zitadel 不達 (authorize redirect 失敗) | ブラウザがそのままエラーページ | アプリ範囲外。LoginScreen で再試行ボタン |
| token endpoint 失敗 (4xx/5xx) | AuthClient が `OidcException` | AuthCallbackScreen で Error state → "ログインに失敗しました" + LoginScreen へ |
| state 不一致 | AuthCallbackHandler が拒否 | 同上(CSRF 疑い) |
| refresh_token 失効 (invalid_grant) | AuthClient が `OidcException(reauth=true)` | TokenStore.clear() → LoggedOut |
| RPC 401 (操作中) | UnauthorizedException | RpcCallWrapper が refresh 1 回 → 再 401 なら LoggedOut |
| RPC ping で 401 (起動時 NeedRegister 判定) | UnauthorizedException | NeedRegister state → RegisterDialog |
| RPC ping で他のエラー | 任意の Exception | Error state、"接続できませんでした"+再試行 |
| register で重複 | RpcException | NeedRegister のまま、ダイアログにエラー表示 |
| Web Crypto API 未対応 | Pkce 初期化失敗 | "対応していないブラウザです" 固定表示 |

`OidcException` は AuthClient 内で `error` / `error_description` を握って投げる。UI はメッセージを翻訳せず raw を出して MVP 完成扱い(i18n は別 Plan)。

## 6. テスト戦略

| 対象 | 種別 | 内容 |
|---|---|---|
| `Pkce` | commonTest | code_verifier の文字種・長さ、code_challenge が SHA-256(verifier) の base64url と一致 |
| `AuthClient` | commonTest | ktor MockEngine で token endpoint / refresh / end_session を叩く request を検証 (URL, form params, headers) |
| `TokenStore` | webTest | 実 sessionStorage を使った save/load/clear と expires_at の round-trip |
| `RpcClientFactory` | commonTest | Sec-WebSocket-Protocol ヘッダに `mindstock.v1` と `mindstock.bearer.<b64url(token)>` が両方含まれることを MockEngine で検証 |
| `AuthBootstrap` | commonTest | tokens 無し→LoggedOut / 期限内→Authenticating / 期限切れ→refresh→Authenticating / refresh 失敗→LoggedOut の状態遷移 |
| `RpcCallWrapper` | commonTest | 401 → refresh → 200 / 401 → refresh 後また 401 → LoggedOut |

**手動検証 (README に手順追加)**:
1. `docker compose up -d`
2. Zitadel UI で Application `mindstock-frontend` を PKCE で作成 / redirect_uri を `http://localhost:8080/auth/callback` に登録
3. `AUTH_CLIENT_ID` 等を `mise.toml` (or `.envrc`) に設定
4. `./gradlew :backend:application:api:run` + `./gradlew :frontend:wasmJsBrowserRun`
5. http://localhost:8080 を開く → Zitadel ログイン → register ダイアログ → "Hello, …" 表示まで通る

Compose UI 自体のレンダリングテスト(LoginScreen / RegisterDialog 等)は今回入れない。骨組み段階で UI が薄すぎてコスパが悪く、後続 Plan で実画面が増えた時にまとめて入れる。

## 7. 実装スコープ(新規/変更ファイル一覧)

| 種別 | パス |
|---|---|
| 新規 | `frontend/src/commonMain/kotlin/.../auth/AuthClient.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/TokenStore.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/Tokens.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/Pkce.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/AuthState.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/AuthBootstrap.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../auth/AuthCallbackHandler.kt` |
| 生成 | `frontend/build/generated/auth/AuthConfig.kt` (build task で生成) |
| 新規 | `frontend/src/commonMain/kotlin/.../rpc/RpcClientFactory.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../rpc/RpcCallWrapper.kt` |
| 改 | `frontend/src/commonMain/kotlin/.../App.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../ui/login/LoginScreen.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../ui/register/RegisterDialog.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../ui/callback/AuthCallbackScreen.kt` |
| 新規 | `frontend/src/commonMain/kotlin/.../ui/shell/AppShell.kt` |
| 改 | `frontend/build.gradle.kts` (ktor-client / generateAuthConfig task / 依存追加) |
| 改 | `frontend/src/webMain/resources/index.html` (`/auth/callback` も同じ index を返す前提のコメント追記、必要なら) |
| 新規 | テストファイル群(§6 表参照) |
| 改 | `README.md` (Zitadel Application 作成手順 + AUTH_* env 設定手順) |

## 8. 完了条件

- `./gradlew check` 通過
- `./gradlew :frontend:wasmJsBrowserRun` で起動でき、ログイン → register → AppShell 表示まで手動で確認できる
- ログアウトで Zitadel セッションが切れ、再度 LoginScreen に戻る
- sessionStorage に access_token / refresh_token / id_token / expires_at が保存される
- access_token 期限の 60 秒前にバックグラウンドで refresh される
- RPC 401 で 1 度だけ refresh + retry し、再 401 で LoggedOut になる
- AUTH_CLIENT_ID / AUTH_AUDIENCE / AUTH_PROJECT_ID が未設定だとビルドが失敗する
- §6 のユニットテストが全て green
