# Plan 8: 認証(Zitadel OIDC + JWT 検証) — 設計

作成日: 2026-05-26
対象: backend の認証実装(Stub 認証 → Zitadel JWT 検証への置き換え)

frontend の OIDC クライアント実装は Frontend Plan で別途扱う。
Household メンバーシップによる認可 (世帯スコープのチェック) も別 Plan。

## 1. 背景と目的

Plan 6 で導入した `StubAuthProvider`(Bearer token = UserId UUID 文字列を素通し)は開発・テスト用途であり、本番に出せない。Plan 8 ではメイン設計書 §7 に従い Zitadel を OIDC プロバイダとして、JWT (access_token) を JWKS で検証する本番認証を実装する。

**スコープに含む**:
- `ktor-server-auth-jwt` ベースの JWT 検証 (issuer / audience / clock skew / JWKS キャッシュ)
- 2 つの認証レルム (`"user"`: User 登録必須 / `"user-public"`: User 未登録でも通す)
- WebSocket ハンドシェイクで `Sec-WebSocket-Protocol` 経由の token 受け渡し
- `MindstockPrincipal` を `UserId` ベース → `AuthIdentity` ベースに変更
- `UserPublicRpcService.register` のシグネチャ変更(`authIdentity` 引数を廃止し Principal から取得)
- `compose.yml` への Zitadel + 専用 PostgreSQL 追加
- e2e テスト 34 本を本物の JWT フローで書き換え + 追加 5 ケース
- `StubAuthProvider` の削除

**スコープ外**:
- Household メンバーシップによる認可(世帯スコープのアクセスチェック)→ 別 Plan
- frontend の OIDC クライアント(PKCE フロー、redirect、token 保管)→ Frontend Plan
- token refresh / logout の仕組み → frontend 責務
- Zitadel の organization / role / group の取り込み → MVP では不要
- 本番 Zitadel デプロイ(SOPS / Secret Manager / TLS 等) → 別 Plan
- 認証関連の observability / metrics → 観測性 Plan(候補)

## 2. 全体構成

```text
backend/application/api/
  src/main/kotlin/net/brightroom/mindstock/configuration/auth/
    AuthConfiguration.kt          [改] install(Authentication) { jwt("user") + jwt("user-public") }
    JwtAuthConfiguration.kt       [新] issuer / audience / JWKS の設定読み込み + Verifier 構築
    WsBearerTokenExtractor.kt     [新] Sec-WebSocket-Protocol / Authorization header から token 取得
    MindstockPrincipal.kt         [改] AuthIdentity を保持(UserId は持たない)
    ActorResolver.kt              [改] Principal → AuthIdentity → UserRepository.findByAuthIdentity → User
    StubAuthProvider.kt           [削除]
    CurrentCall.kt                [改] Principal 形変更に追従
  src/main/kotlin/.../configuration/routing/
    RoutingConfiguration.kt       [改] register ルートを authenticate("user-public") で wrap
  src/main/resources/
    application.yaml              [改] external.auth: issuer / audience / jwks-url

shared/rpc/commonMain/.../service/
  UserPublicRpcService.kt         [改] register(displayName) シグネチャに変更
backend/application/api/src/main/.../presentation/rpc/user/
  UserPublicRpcServiceImpl.kt     [改] AuthIdentity は Principal から取得

compose.yml                       [改] zitadel-db + zitadel サービス追加
README.md                         [改] Zitadel セットアップ手順
```

テスト側:

```text
backend/application/api/src/test/kotlin/.../e2e/
  auth/
    TestKeyPair.kt        [新] テスト用 RSA 鍵ペア (suite 単位で 1 つ)
    TestJwks.kt           [新] 鍵から JWKS JSON を生成、testApplication 内で /test-jwks に host
    TestJwtIssuer.kt      [新] sub / aud / iss / exp 等を指定して JWT を発行
  E2eTestSupport.kt       [改] authenticatedRpcClient を JWT 発行 + WS サブプロトコル付与に変更
  user/UserRpcServiceE2eTest.kt 等 既存 34 本 [改] ヘルパー差し替えで通す
  auth/JwtAuthE2eTest.kt  [新] exp/issuer/audience/wrong-key/未登録 sub register OK の 5 ケース
```

## 3. JWT 検証の詳細

### 3.1 ライブラリと依存

- `ktor-server-auth-jwt:3.5.0`(classpath 済み)
- `jwks-rsa:0.24.0`(classpath 済み、Auth0 製)
- `java-jwt:4.5.2`(classpath 済み、Auth0 製)

追加依存はなし。Plan 6 着手時に speculatively 入れられていたものを実利用する。

### 3.2 application.yaml

```yaml
external:
  auth:
    issuer: "$AUTH_ISSUER:http://localhost:8081"
    audience: "$AUTH_AUDIENCE:mindstock-backend"
    jwks-url: "$AUTH_JWKS_URL:http://localhost:8081/oauth/v2/keys"
```

### 3.3 JwkProvider の設定

`com.auth0.jwk.JwkProviderBuilder` で:
- `cached(10, 1, TimeUnit.HOURS)`: 最大 10 鍵を 1 時間キャッシュ
- `rateLimited(10, 1, TimeUnit.MINUTES)`: 過剰アクセス抑制

### 3.4 JWTVerifier の設定

- 署名アルゴリズム: JWKS から取得した公開鍵で決定(RS256 想定だが固定はしない)
- `acceptLeeway(30)`: exp / nbf / iat の 30 秒スキュー許容
- `withIssuer(issuer)`: iss 固定
- `withAudience(audience)`: aud 固定

### 3.5 validate ブロック

```kotlin
jwt("user") {
    realm = "mindstock"
    authHeader { call -> WsBearerTokenExtractor.extract(call) }
    verifier(jwkProvider, issuer) { acceptLeeway(30); withAudience(audience) }
    validate { credential ->
        val sub = credential.payload.subject ?: return@validate null
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
        // User 未登録は 401
        userRepository.findByAuthIdentity(identity) ?: return@validate null
        MindstockPrincipal(identity)
    }
}

jwt("user-public") {
    realm = "mindstock"
    authHeader { call -> WsBearerTokenExtractor.extract(call) }
    verifier(jwkProvider, issuer) { acceptLeeway(30); withAudience(audience) }
    validate { credential ->
        val sub = credential.payload.subject ?: return@validate null
        MindstockPrincipal(AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub)))
    }
}
```

`"user-public"` レルムは「JWT 検証は通すが User の有無は問わない」状態を表す。`register` 専用。

### 3.6 access_token vs id_token

backend は **access_token のみ** を受け付ける。Zitadel の access_token は JWT 形式で発行されるためそのまま JWKS 検証できる(opaque ではない)。`id_token` は frontend が UI 表示用にのみ使い、backend には送らない。

## 4. WebSocket でのトークン受け渡し

### 4.1 問題

Browser の `WebSocket` API は `Authorization` ヘッダを設定できない。`Sec-WebSocket-Protocol` で代替するのが業界標準。

### 4.2 プロトコル設計

クライアントは handshake で:
```http
Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<base64url(jwt)>
```
を送る。

- `mindstock.v1`: アプリプロトコル識別子(将来の互換性維持用)
- `mindstock.bearer.<token>`: token を `base64url` でエンコードして運ぶ

サーバは応答で `mindstock.v1` だけ echo し、`mindstock.bearer.*` は echo しない(token がレスポンスヘッダ・中継ログ・proxy に漏れるのを防ぐ)。

### 4.3 抽出ロジック

`WsBearerTokenExtractor.extract(call): HttpAuthHeader?` が以下の順で token を探す:

1. `Authorization: Bearer <jwt>` が存在すればそれを使う(REST 互換・テストで使いやすい)
2. なければ `Sec-WebSocket-Protocol` を CSV パースし、`mindstock.bearer.` プレフィックス付きエントリから token を base64url デコードして抽出
3. どちらも無ければ `null`(→ Ktor が 401 を返す)

### 4.4 Ktor の組み立て順

```kotlin
authenticate("user") {
    rpc("/api") { registerService<UserRpcService> { ... } }
}
authenticate("user-public") {
    rpc("/api-public") { registerService<UserPublicRpcService> { ... } }
}
```

kotlinx-rpc の `rpc(...)` は内部で `webSocket(...)` を呼ぶ。`authenticate` で外側を wrap すれば handshake 時に validate が走る。

### 4.5 サブプロトコル応答の制御

Ktor の `WebSockets` プラグイン側で `protocol` を明示的に `mindstock.v1` のみ echo するように設定する。kotlinx-rpc 経由で WebSocket を組み立てている都合上、`Sec-WebSocket-Protocol` 応答ヘッダを直接制御する PreHandshake フックが必要なら導入する(実装段階で確認)。

## 5. compose.yml への Zitadel 追加

### 5.1 サービス定義(概略)

```yaml
services:
  postgres:               # 既存 (mindstock app DB)
    ...
  zitadel-db:             # Zitadel 専用 PG
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: zitadel
      POSTGRES_USER: zitadel
      POSTGRES_PASSWORD: zitadel
    volumes: ["zitadel-pgdata:/var/lib/postgresql"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U zitadel -d zitadel"]
      interval: "20s"
      timeout: "5s"
      retries: 3
  zitadel:
    image: ghcr.io/zitadel/zitadel:v2.71.0   # バージョン pin(実装時に最新 stable に合わせる)
    command: 'start-from-init --masterkey "MasterkeyNeedsToHave32Characters" --tlsMode disabled'
    environment:
      ZITADEL_DATABASE_POSTGRES_HOST: zitadel-db
      ZITADEL_DATABASE_POSTGRES_DATABASE: zitadel
      ZITADEL_DATABASE_POSTGRES_USER_USERNAME: zitadel
      ZITADEL_DATABASE_POSTGRES_USER_PASSWORD: zitadel
      ZITADEL_DATABASE_POSTGRES_ADMIN_USERNAME: zitadel
      ZITADEL_DATABASE_POSTGRES_ADMIN_PASSWORD: zitadel
      ZITADEL_EXTERNALSECURE: "false"
      ZITADEL_EXTERNALDOMAIN: localhost
      ZITADEL_EXTERNALPORT: "8081"
      ZITADEL_FIRSTINSTANCE_ORG_HUMAN_EMAIL_ADDRESS: admin@localhost
      ZITADEL_FIRSTINSTANCE_ORG_HUMAN_PASSWORD: Password1!
    ports: ["8081:8080"]
    depends_on:
      zitadel-db: { condition: service_healthy }
volumes:
  mindstock-pgdata: {}
  zitadel-pgdata: {}
```

### 5.2 初回セットアップ(README 追記内容)

1. `docker compose up -d zitadel-db zitadel` で Zitadel 起動
2. http://localhost:8081 にアクセス、`admin@localhost` / `Password1!` でログイン
3. Zitadel の管理 UI で:
   - Project `mindstock` を作成
   - Application `mindstock-frontend` を PKCE で作成(redirect URI 等は Frontend Plan で設定)
   - API `mindstock-backend` を作成 → audience を `mindstock-backend` に設定
4. issuer / jwks-url / audience を必要なら `.env.local` に上書き

### 5.3 注意点

- masterkey はローカル開発用の固定値。本番では別管理(別 Plan)
- ports 衝突回避のため Zitadel はホスト側 8081 に出す(コンテナ内は 8080)
- バージョン pin は実装時の最新 stable に合わせる
- zitadel-db は PG 17 系を使う(Zitadel v2.71.0 は PG 18 の partitioned unlogged table 制約と非互換)

## 6. ドメイン・リポジトリ層への影響

### 6.1 変更不要

Plan 3–5 で整備済みのため、以下は本 Plan では変更しない:
- `domain/model/user/auth/AuthIdentity.kt`
- `domain/model/user/auth/AuthProvider.kt`(`ZITADEL` のみ)
- `domain/model/user/auth/AuthSubject.kt`
- `domain/model/user/User.kt` の `authIdentity` プロパティ
- `domain/repository/user/UserRepository.findByAuthIdentity`
- `domain/repository/user/UserRegisterRepository.register(displayName, authIdentity)`

### 6.2 `UserPublicRpcService.register` のシグネチャ変更

```kotlin
// Before
suspend fun register(displayName: DisplayName, authIdentity: AuthIdentity): User

// After
suspend fun register(displayName: DisplayName): User
```

`authIdentity` は Principal から取得する。クライアントが任意の `sub` を詐称して登録できる穴を塞ぐ。これは `shared/rpc/` への破壊的変更だが frontend は未着手なので影響は backend のみ。

### 6.3 例外マッピング

- 既存の `UnauthorizedException` → 401 のマッピングはそのまま流用
- JWT 検証失敗時の `WWW-Authenticate: Bearer realm="mindstock", error="invalid_token"` ヘッダは `ktor-server-auth-jwt` が自動付与
- `validate` ブロックが `null` を返した場合の 401 挙動も同様

## 7. テスト戦略

### 7.1 e2e ヘルパーの設計

Zitadel コンテナはテストでは使わない。テストプロセス内で RSA 鍵ペアを生成し、JWKS endpoint は test suite 単位の singleton として `embeddedServer(CIO, port=0)` で起動した実 HTTP サーバで host する (`JwkProvider` は実 HTTP 接続を要求するため、testApplication 内 routing では満たせない)。動的ポートを `external.auth.jwks-url` に注入する:

1. テスト suite 起動時に静的 RSA 鍵ペアを 1 つ生成(`TestKeyPair`)
2. suite 単位の singleton `SharedJwksServer` (`embeddedServer(CIO, port=0)`) で `/test-jwks` を host し、起動後に割り当てられた動的ポートを取得
3. testApplication の `environment { config = MapApplicationConfig(...) }` で `auth.issuer`, `auth.jwks-url`, `auth.audience` を上書き(`test-issuer`, `http://localhost:<dynamic-port>/test-jwks`, `mindstock-backend-test`)
4. `authenticatedRpcClient(asUser = user)` が:
   - `user.authIdentity.subject` を sub に入れて `TestJwtIssuer` で署名
   - WS 接続時に `Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<token>` を付与

### 7.2 既存テストの書き換え

| 既存テスト | 書き換え方針 |
|---|---|
| happy path 系 | ヘルパー差し替えのみで通る想定(`seedUser` は既に `AuthIdentity` を持つ) |
| 「Bearer なし」系 | サブプロトコルなしで接続 → 401 を期待(挙動同一) |
| 「unknown UserId」系 | 「unknown sub」テストに置換(JWT 発行はするが対応 User が DB に居ない → 401) |

### 7.3 追加するテスト(`JwtAuthE2eTest`)

1. exp が過去の JWT → 401(`acceptLeeway(30)` の外)
2. 違う issuer の JWT → 401
3. 違う audience の JWT → 401
4. 違う鍵で署名された JWT → 401(JWKS で公開鍵が見つからない)
5. 未登録 sub で `register` → 200(`user-public` レルムの境界確認)+ 重複 register → 期待される 4xx

### 7.4 単体テスト

- `WsBearerTokenExtractor`:
  - Authorization header のみ
  - `Sec-WebSocket-Protocol` のみ
  - 両方あり(Authorization 優先)
  - 両方なし
  - `mindstock.bearer.` が複数あるとき(エラー or 最初を採用 — 実装時に決定)
- `JwtAuthConfiguration` の validate ブロック:
  - sub → AuthIdentity 構築
  - `"user"` レルムで User 未登録時 null
  - `"user-public"` レルムで User 未登録でも Principal を返す

### 7.5 手動検証

README に手順を記載:
1. `docker compose up -d` で Zitadel 含む全サービス起動
2. Zitadel 管理 UI で Application 作成 → access_token 取得(`curl` / OIDC playground 等)
3. backend に `Authorization: Bearer <token>` で RPC 呼び出し → 200 確認

### 7.6 Zitadel コンテナを e2e に使わない理由

- 起動 30–60s で重く、e2e 1 ファイルあたり数百ms の現状コストから大幅悪化
- Zitadel の初回 init は compose 前提で testcontainers 対応が複雑
- 検証ロジックの正しさは JWKS スタブで十分(本番との差は鍵が Zitadel 由来かテスト由来かのみ)

## 8. 実装スコープ(変更ファイル一覧)

| 種別 | パス |
|---|---|
| 新規 | `backend/application/api/src/main/.../configuration/auth/JwtAuthConfiguration.kt` |
| 新規 | `backend/application/api/src/main/.../configuration/auth/WsBearerTokenExtractor.kt` |
| 改 | `backend/application/api/src/main/.../configuration/auth/AuthConfiguration.kt` |
| 改 | `backend/application/api/src/main/.../configuration/auth/MindstockPrincipal.kt` |
| 改 | `backend/application/api/src/main/.../configuration/auth/ActorResolver.kt` |
| 改 | `backend/application/api/src/main/.../configuration/auth/CurrentCall.kt` |
| 削除 | `backend/application/api/src/main/.../configuration/auth/StubAuthProvider.kt` |
| 改 | `backend/application/api/src/main/.../configuration/routing/RoutingConfiguration.kt` |
| 改 | `shared/rpc/src/commonMain/.../service/UserPublicRpcService.kt` |
| 改 | `backend/application/api/src/main/.../presentation/rpc/user/UserPublicRpcServiceImpl.kt` |
| 改 | `backend/application/api/src/main/resources/application.yaml` |
| 改 | `compose.yml` |
| 改 | `README.md` |
| 新規 | `backend/application/api/src/test/.../e2e/auth/TestKeyPair.kt` |
| 新規 | `backend/application/api/src/test/.../e2e/auth/TestJwks.kt` |
| 新規 | `backend/application/api/src/test/.../e2e/auth/TestJwtIssuer.kt` |
| 改 | `backend/application/api/src/test/.../e2e/E2eTestSupport.kt` |
| 改 | 既存 e2e テスト 34 本(ヘルパー切り替えのみ) |
| 新規 | `backend/application/api/src/test/.../e2e/auth/JwtAuthE2eTest.kt` |

## 9. 完了条件

- `./gradlew check` 通過
- `./gradlew :backend:application:api:test` で e2e 全テスト green(書き換え後 34 本 + 追加 5 ケース)
- `StubAuthProvider` が削除されている
- `docker compose up -d` で Zitadel が起動し、README 手順で手動の access_token 取得から 200 応答までが確認できる
- `Sec-WebSocket-Protocol` 経由・`Authorization` ヘッダ経由の両方で認証が通ることが test で確認されている
- `UserPublicRpcService.register` から `authIdentity` 引数が消えている
