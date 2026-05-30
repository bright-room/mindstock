# 認証機構の現状とその妥当性検証

> 対象: Zitadel OIDC を用いた SSO ログインと frontend ↔ backend 間認証
> 作成日: 2026-05-30
> 目的: 現状の認証機構で開発を進めて問題ないかの妥当性検証

---

## ざっくり理解(まず全体像)

> 用語を最小限にした入門。正確な詳細は §1 以降。

### 一言でいうと

**「Zitadel が発行した入場券(JWT)を、WebSocket をつなぐ瞬間にドアで1回チェックする方式」**。
かたい名前をつけるなら **「ハンドシェイク時の Bearer トークン認証」**。トークンの運び方が `Sec-WebSocket-Protocol` というだけ。

### たとえ話(ライブ会場)

1. **チケットを事前にもらう** … ユーザはチケット窓口(Zitadel)で **リストバンド(JWT)** をもらう。会場(バックエンド)は発券にノータッチ。
2. **入口で1回チェック** … 会場に入る(WebSocket をつなぐ)瞬間、係員がリストバンドを **しっかり確認**(本物か=署名 / 誰の券か=iss・aud / 期限切れてないか=exp)。OK なら入場。
3. **中では再スキャンしない** … 一度入れば、ドリンクを頼むたび(RPC を呼ぶたび)に **バーコードを読み直さない**。
4. **期限だけ毎回チラ見** … 注文のたびに、リストバンドに **印刷された終了時刻(exp)だけ** 見る。過ぎてたら断る。

→ つまり **「入口で1回ちゃんと確認。中では期限だけチラ見」**。「毎回フル認証」でも「入口だけで中は完全ノーチェック」でもない。

### 実際の流れ(用語を最小限に)

```
① ブラウザが Zitadel からトークン(JWT)をもらう      ← 事前準備(ログイン)
② WebSocket を開くとき、そのトークンを一緒に送る
③ サーバは「開く瞬間」にトークンを本格検証 → OK なら接続成立
④ 以降の各操作では「期限が切れてないか」だけ見る
⑤ 期限が切れたら、ブラウザがトークンを取り直して接続し直す
```

### 「登場人物が別々に動く2つのやり取り」

- **Zitadel ↔ ブラウザ**: チケットをもらうやり取り(ログイン)。バックエンドは無関係。
- **ブラウザ ↔ バックエンド**: もらったチケットを見せて中に入るやり取り(API アクセス)。Zitadel は無関係(公開鍵だけ事前に保持)。

「ログインする場所」と「チケットを使う場所」が別、というだけ。

### 1分でわかる用語

| 用語 | かみ砕くと |
|---|---|
| OIDC / SSO | 「ログインは Zitadel に任せる」仕組み |
| JWT | 中身と発行元が改ざんできない署名付きの入場券 |
| access token | 入場券そのもの(短命) |
| refresh token | 入場券を再発行してもらう引換券(やや長命) |
| JWKS | 券が本物か確かめるための「Zitadel の公開印鑑」 |
| PKCE | チケット受け取りの途中ですり替えられない仕掛け |
| ハンドシェイク | WebSocket をつなぎ始める最初の接続交渉 |

---

## 0. 要約(先に結論)

- **コードに実装された範囲は妥当**。OIDC Authorization Code + PKCE、JWKS による RS256 JWT 検証(iss/aud/exp 検証付き)、per-RPC の expiry 再チェック、token をレスポンスへ echo しない、など定石を踏んでいる。
- ただし **「進めて問題ないか」の最終判断は、このリポジトリのコードだけでは確定できない**。認証の健全性のうち数点は **Zitadel 側のアプリ設定に依存**しており、コードからは検証不能。これらを確認するまでは「条件付きで妥当」が正確な結論。
- **最大の構造的トレードオフ**: access / refresh / id token をすべてブラウザの `sessionStorage` に保管している。これは SPA + public client の標準形だが、**origin 上で XSS が 1 つでも起きれば(長命な refresh token を含む)全トークンが盗まれる**。本アプリの扱うデータ(家庭の在庫情報=中程度の機微性)を踏まえて、この姿勢を受容するかが本質的な意思決定。
- **本番では `wss`/TLS が必須**(後述。コードのデフォルトは `http`/`ws`)。

「進めてよいか」への答え → **コードは壊れていない。§5 の Zitadel 側 4 項目 + 本番 TLS を確認できれば妥当。それまでは条件付き。**

---

## 1. 全体像

```
┌──────────────┐   ① /oauth/v2/authorize (code + PKCE)    ┌─────────────┐
│              │ ───────────────────────────────────────► │             │
│  Frontend    │   ② code を受信 (/auth/callback)          │   Zitadel   │
│ (Wasm SPA)   │ ◄─────────────────────────────────────── │   (OIDC)    │
│              │   ③ /oauth/v2/token で code → tokens       │             │
│              │ ◄───────────────────────────────────────►│             │
└──────┬───────┘                                            └─────────────┘
       │ ④ WebSocket (kotlinx-rpc)
       │    Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<b64url(access_token)>
       ▼
┌──────────────┐   ⑤ JWKS で署名検証 (RS256, iss/aud/exp)  ┌─────────────┐
│  Backend     │ ───────────────────────────────────────► │ Zitadel     │
│ (Ktor + krpc)│   ⑥ sub → DB で userId 解決                │ /oauth/v2/  │
│              │      (未登録なら userId=null)              │   keys      │
└──────────────┘                                            └─────────────┘
```

- フロントは独自実装の軽量 OIDC クライアント(ライブラリ非依存)。トークン交換・更新はブラウザから Zitadel へ直接行う(public client)。
- バックエンドは **トークン発行に一切関与しない**。受け取った access token を JWT として検証するだけのリソースサーバ。
- frontend ↔ backend の通信はすべて kotlinx-rpc over WebSocket。認証は WebSocket ハンドシェイク時の bearer token で行う。

---

## 1.5 設計上の論点(経路・回数・発行元・移行容易性)

> 実装ロジックではなく「構造としてどうなっているか」の整理。

### 1.5.1 認証の経路

経路は **2 本** あり、別物として理解するのが要点。

**A. ユーザ認証(SSO)— ブラウザ ↔ Zitadel(バックエンドを通らない)**

```
ブラウザ ──(Authorization Code + PKCE)──► Zitadel
ブラウザ ◄──(access/refresh/id token)──── Zitadel
```

トークン交換はブラウザが Zitadel と **直接**行う。バックエンドはこの経路に登場しない。

**B. API 認証(リソースアクセス)— ブラウザ → バックエンド(Zitadel とは毎回話さない)**

```
ブラウザ ──(WS handshake, token を Sec-WebSocket-Protocol で同送)──► バックエンド
バックエンド ── 手元の JWKS 公開鍵で署名検証(オフライン)──┘
```

バックエンドは **リクエスト毎に Zitadel へ問い合わせない**。起動時に JWKS(公開鍵)を取得・キャッシュし、その鍵で JWT 署名をローカル検証する。
→ 「トークンは Zitadel が発行 → ブラウザが保持 → バックエンドへ提示 → バックエンドがオフライン暗号検証」という流れ。

### 1.5.2 認証の回数

**「ハンドシェイク時に 1 回だけ」でも「都度フル認証」でもなく、その中間。**

| タイミング | やること |
|---|---|
| WS ハンドシェイク時(1 回) | **フル検証**: 署名・`iss`・`aud`・`exp`。成功したら `MindstockSession`(その時のトークンの `exp` を埋め込む)を接続単位で保持 |
| 以後の各 RPC 呼び出し | **expiry 判定だけ**: `now > session.exp` を確認。署名再検証・JWKS 照会・iss/aud 再チェックは **しない** |

含意:

- セッションは **ハンドシェイク時に提示したトークンに固定**される。接続途中で Zitadel 側でそのトークンを失効させても、バックエンドは `exp` しか見ていないので **気づかない**(introspection が無いため)。失効が効くのは `exp` 到来 or 再接続のタイミング。
- `exp` を過ぎると以後の呼び出しは `Unauthorized`。フロントは refresh して WS を張り直す。
- → 長命接続中の **「期限切れ」は拾えるが、「期限内の明示的失効(ログアウト/revoke)」は拾えない**。これは §5.3-3(短い TTL を緩和策とする)と表裏一体。

### 1.5.3 トークンの発行元

**すべて Zitadel が発行元**(`/oauth/v2/token`)。

- access / refresh / id token はすべて Zitadel が署名・発行。
- **バックエンドは自前のトークンやセッションを一切発行しない**。純粋なリソースサーバ(relying party)で、署名秘密鍵を持たず、公開鍵(JWKS)で検証するだけ。
- → 責務分離としてはきれいな形(IdP がトークンを作り、アプリは検証して使うだけ)。

### 1.5.4 Zitadel 依存度と将来の移行容易性

**結論: ロックインは弱め。標準 OIDC に乗っているため移行コストは中〜小。ただし依存箇所は偏っている。**

**プロバイダ非依存(移行で触らなくてよい)**

- **バックエンドの検証はほぼ完全に標準 OIDC**。JWKS + RS256 + iss/aud/exp なので、Auth0 / Keycloak / Cognito / Entra ID 等どの OIDC 準拠 IdP でも `AUTH_ISSUER` / `AUTH_AUDIENCE` / `AUTH_JWKS_URL` の **設定差し替えだけ**で動く(コード変更ゼロ)。
- 認証フロー自体(Authorization Code + PKCE)も標準。
- ドメインに `AuthProvider` enum(現状 `ZITADEL` のみ。`domain/.../auth/AuthProvider.kt` のコメントに「将来 AUTH0 等を追加可能」)と `AuthIdentity(provider, subject)` があり、**複数 IdP 併存の seam が既にある**。

**Zitadel ハードコード(移行で書き換えが要る)** — ここに結合が集中

| 箇所 | ファイル | 内容 |
|---|---|---|
| エンドポイントパス直書き | `frontend/.../auth/AuthClient.kt` | `/oauth/v2/authorize`・`/oauth/v2/token`・`/oidc/v1/end_session` をリテラル。**OIDC discovery(`.well-known/openid-configuration`)は未使用** → 最大の結合点 |
| Zitadel 固有スコープ | `frontend/.../App.kt` | `urn:zitadel:iam:org:project:id:${PROJECT_ID}:aud`(access token の audience を制御する Zitadel 独自構文) |
| DB カラム名 | `backend/core/.../UsersTable.kt`, `V20260528172158__INIT.sql` | `users.zitadel_sub`(`UNIQUE`)。値は単なる OIDC `sub` なので機能的縛りではなく **命名の lock-in** |

**移行容易性の総評**

- バックエンド: **容易**(設定差し替え + カラム名くらい)。
- フロント: **そこそこ**。ただし対象は実質 `AuthClient.kt` 1 ファイル + スコープ定義 1 行に局所化されており量は小さい。
- **ロックインを最小化する一手**: フロントを **OIDC discovery 化**(`.well-known/openid-configuration` から authorize/token/end_session を動的取得)すれば、エンドポイント直書きが消え、IdP 切り替えが「issuer URL を変えるだけ」に近づく。`AuthProvider` enum と合わせれば複数 IdP 併存も視野に入る。

---

## 1.6 WebSocket 認証方式の位置づけ(分類・最適性・K8s との異同)

> 一般的な WebSocket 認証方式の中で現方式がどこに当たるか、最適か、そして「K8s と似ている」の実体。

### 1.6.1 方式の分類

WebSocket 認証は **「①トークンをどこから得るか(ベース戦略)」×「②それを WS にどう載せるか(運搬方式)」** の2層で整理できる。現方式は:

- ① ベース戦略 = **チケット(トークン)ベース認証**。外部権威(Zitadel)から先にトークンを取得し WS 接続時に提示。
- ② 運搬方式 = **Sec-WebSocket-Protocol 方式**(`mindstock.bearer.<base64url(jwt)>`)。

クエリパラメータ・Cookie・First Message・Basic・TLS 相互認証 の **いずれでもない**。

| 運搬方式 | 主な欠点 | 一般的な推奨度 |
|---|---|---|
| クエリパラメータ | URL/アクセスログに漏れる | 低 |
| **Sec-WebSocket-Protocol(現方式)** | 仕様本来の用途から逸脱 | 中(K8s/AppSync 実績) |
| First Message(接続後の最初のメッセージで認証) | 認証前にソケットが開く(接続枯渇)→ timeout 必須 | 高(自前プロトコル前提での最適解) |
| Cookie | CSWSH・別ドメイン不可 | 低 |

### 1.6.2 現スタックでの最適性

一般論の「最適解」は **First Message** に寄るが、その前提は **メッセージプロトコルを自前で握っていること**。本プロジェクトは **kotlinx-rpc がフレーミングを所有**するため、その前提が当てはまらない。したがって本スタックでは **Sec-WebSocket-Protocol 方式が最適/最善フィット**:

- **HTTP アップグレード層(Ktor plugin)で、RPC 機構が動く前に認証完結**できる(フレームワーク駆動 RPC と相性が良い)。
- First Message の弱点「認証前ソケットの窓(接続枯渇)」を **最初から持たない**。
- URL に載せないのでクエリパラメータの「ログ漏れ」に該当しない。
- 唯一 First Message に劣るのは **リクエストヘッダのロギング経路**。緩和は TLS 必須(§5.5)+ echo 抑止 + access token 短命(§5.3-3)、加えて自前ログ/プロキシで `Sec-WebSocket-Protocol` を出力しない運用。

クエリパラメータ/Cookie へ移すのは明確に劣化(前者=ログ漏れ、後者=CSWSH)。

### 1.6.3 Kubernetes 方式との異同

「K8s と同パターン」は **コアの仕組みが独立に一致している**という意味で正確。ただし **完全同一ではない**。

| 観点 | Kubernetes apiserver | mindstock(現方式) | 同じ? |
|---|---|---|---|
| トークンを `Sec-WebSocket-Protocol` で運ぶ | ✅ | ✅ | 同じ |
| エンコード(base64url パディング無) | `RawURLEncoding` | `trimEnd('=')` | 同じ |
| 応答からトークン entry を除去 | bearer を除去し残りを echo | `mindstock.v1` のみ echo、bearer は返さない | 同じ(結果は同一) |
| プレフィックス文字列 | `base64url.bearer.authorization.k8s.io.` | `mindstock.bearer.` | 違う(命名のみ) |
| トークンの中身/検証 | 任意の TokenAuthenticator チェーン | Zitadel RS256 JWT を JWKS 検証 | 役割は同じ、実体は別 |
| `Authorization: Bearer` も受け付ける | ❌(subprotocol 専用) | ❌(subprotocol 専用) | 同じ(§1.6.4-1 で削除済み) |
| bearer entry が複数なら拒否 | ✅ "multiple ... tokens specified" | ✅ 複数なら fail-closed で null | 同じ(§1.6.4-2 で実施済み) |
| 同伴 subprotocol を必須化 | ✅ "missing additional subprotocol" | ❌ `mindstock.v1` の有無は認証に無関係 | K8s が厳格 |

> 出典: K8s `staging/src/k8s.io/apiserver/pkg/authentication/request/websocket/protocol.go`(`bearerProtocolPrefix`)。

#### 「subprotocol(サブプロトコル)」とは

WS 接続時にクライアントが申告する **アプリ層の方言名**(`new WebSocket(url, ["chat.v1"])` =「chat v1 で話したい」)。サーバが1つ選んで返す(echo)ことで通信方式を合意する仕組み。現方式はここに2つ載せている: `mindstock.v1`(本物の subprotocol)と `mindstock.bearer.<token>`(トークンを方言名のフリで密輸したもの。ブラウザが付けられる唯一のヘッダだから間借り)。

### 1.6.4 `WsBearerTokenExtractor` の hardening(実施済み)

`backend/api/.../auth/WsBearerTokenExtractor.kt` の調査で挙げた 3 候補のうち、価値のある 2 つを実施した。

1. **Authorization(Bearer ヘッダ)パスを削除 → 実施済み(価値:高)**
   - Health エンドポイントは **存在せず**(コメントの「REST 互換」に実在の利用者なし)、本番フロント(`RpcClientFactory`)・ブラウザとも WS に Authorization を付けられないため実利用は無かった。
   - 旧実装では **e2e テストの大半が Authorization パスを使い**、本番(subprotocol)と違う経路で認証する忠実性ギャップがあった。テストヘルパー(`authenticatedRpcClientWithToken`)を subprotocol 方式へ寄せ、全 e2e が本番経路を踏むようにしたうえで Authorization パスを extractor ごと削除した。「本番コード削減」+「テストが本番経路を踏む」の二得(redundant な `authenticatedRpcClientViaWsProtocol` / `authorize()` ヘルパーも撤去)。

2. **bearer entry の複数指定を拒否 → 実施済み(価値:低、1 と同じ作業でついで)**
   - 「曖昧な認証情報は fail-closed」は健全な作法。bearer entry が 2 つ以上あれば `null`(→ 401)を返す(旧実装は先頭採用で黙って無視していた)。

3. **同伴 subprotocol 必須化 → 見送り(不要)**
   - K8s がこのチェックで守るのは「echo すべき本物が無く、トークンを echo してしまう事故」。本方式は echo を `mindstock.v1` 固定にし bearer を絶対返さない(`WsSubprotocolEchoPlugin`)ため、**等価の防御を別経路で既に達成**。追加の安全はほぼ無い。

---

## 2. SSO ログインフロー(フロントエンド)

### 2.1 ログイン開始 — Authorization Code + PKCE

`frontend/src/webMain/.../App.kt` `startLogin()` / `frontend/src/commonMain/.../auth/AuthClient.kt`

1. `Pkce.newVerifier()` で code_verifier(64 文字)と `state`(43 文字, CSRF nonce)を生成。
2. `state` / `verifier` / `returnTo` を `sessionStorage` に保存。
3. `code_challenge = base64url(sha256(verifier))`(`code_challenge_method=S256`)。
4. `${ISSUER}/oauth/v2/authorize` へリダイレクト。
   - `scope = openid profile offline_access urn:zitadel:iam:org:project:id:${PROJECT_ID}:aud`
   - `offline_access` で refresh token を要求。Zitadel 固有スコープで access token の audience に project を含める。

### 2.2 コールバック処理

`App.kt` `handleCallback()`(`pathname == "/auth/callback"` で起動)/ `auth/AuthCallbackHandler.kt`

1. クエリから `code` / `state` を取得。
2. `state` を保存値と照合(不一致なら `OidcException("state_mismatch")`)→ **CSRF/リプレイ対策**。
3. `${ISSUER}/oauth/v2/token` へ `grant_type=authorization_code` で POST(`code_verifier` 同送)。
4. レスポンス(`access_token` / `refresh_token` / `id_token` / `expires_in`)を `Tokens` に詰めて保存。

> PKCE の SHA-256 / 乱数 / base64url は `wasmJsMain/.../PkceWasmJs.kt` が `globalThis.crypto.subtle` / `crypto.getRandomValues` を使う(ブラウザ標準 API)。

### 2.3 トークン保管

`auth/TokenStore.kt`(key=`mindstock.tokens.v1`)+ `wasmJsMain/.../SessionStorage.kt`

- `window.sessionStorage` に JSON 直列化して保存(`localStorage` ではない=タブを閉じると消える)。
- `Tokens { accessToken, refreshToken, idToken, expiresAt }`。

### 2.4 トークン更新

`auth/AuthBootstrap.kt` / `AuthClient.refresh()`

- アプリ起動時、`expiresAt` まで残り 60 秒以内なら `grant_type=refresh_token` で更新。失敗時はトークンを破棄してログアウト扱い。
- `invalid_grant` は `reauthRequired=true` として再ログインへ。

### 2.5 ログアウト

`App.kt` `onLogout` / `AuthClient.endSessionUrl()`

- `sessionStorage` をクリア → 全 WS を close → `${ISSUER}/oidc/v1/end_session?id_token_hint=...&post_logout_redirect_uri=...` へリダイレクト(RP-initiated logout)。

### 2.6 認証状態

`auth/AuthState.kt`: `LoggedOut` / `Authenticating` / `NeedRegister` / `Ready(tokens)` / `Error`。
起動時の `ping`(`household.findOf()` を 1 回叩く)結果で `Ready` / `NeedRegister` を判定。

---

## 3. frontend ↔ backend 認証(WebSocket / kotlinx-rpc)

### 3.1 トークンの送信方法

`frontend/.../rpc/RpcClientFactory.kt`

ブラウザの WebSocket API は任意ヘッダ(`Authorization`)を付けられないため、`Sec-WebSocket-Protocol` を運搬路に流用する:

```
Sec-WebSocket-Protocol: mindstock.v1
Sec-WebSocket-Protocol: mindstock.bearer.<base64url(access_token), パディング無>
```

### 3.2 バックエンドでのトークン抽出

`backend/api/.../configuration/auth/WsBearerTokenExtractor.kt`

1. `Sec-WebSocket-Protocol` の `mindstock.bearer.` プレフィックスを base64url デコード(運搬路はこの 1 本のみ)。
2. `mindstock.bearer.*` entry が複数あれば曖昧として fail-closed(`null` → 401)。
3. 無ければ `null` → 401。

### 3.3 トークンを応答に echo しない

`backend/api/.../configuration/auth/WsSubprotocolEcho.kt`(`WsSubprotocolEchoPlugin`)

- サーバは応答の `Sec-WebSocket-Protocol` に `mindstock.v1` のみ返し、**`mindstock.bearer.*` は返さない**。
- → ログ・プロキシ・ブラウザ devtools にトークンが漏れるのを防ぐ。

### 3.4 JWT 検証 + セッション組み立て

`backend/api/.../configuration/auth/MindstockAuthPlugin.kt`(`/api/v1` 配下に route-scoped で install)

- `JWT.require(Algorithm.RSA256(JwksKeyProvider(jwkProvider))).withIssuer(issuer).withAudience(audience).acceptLeeway(30).build()`
- crypto は自前実装せず `com.auth0:java-jwt`。アルゴリズムは **RS256 固定**(`alg=none` / HS256 すり替え攻撃を構造的に排除)。
- JWKS は `JwkProviderBuilder(...).cached(10, 1h).rateLimited(10, 1min)`(`RoutingConfiguration.kt`)。
- `sub` 取得 → `AuthIdentity(ZITADEL, AuthSubject(sub))` → DB(`users.zitadel_sub`)で `userId` 解決。未登録なら `userId=null`(`ResourceNotFoundException` を握って null 化)。
- `exp` を `MindstockSession.exp` に格納し `call.attributes` へ。

### 3.5 認可レベル(routing)

`backend/api/.../configuration/routing/RoutingConfiguration.kt`

| route | ガード | 用途 |
|---|---|---|
| `/api/v1/user/public` | JWT 有効のみ(未登録 OK) | `register` 用 |
| `/api/v1/{user,household,catalog,product,stock}` | `RequireRegisteredUserPlugin`(`userId != null`) | 登録済みユーザ専用 |

### 3.6 RPC 呼び出し時の expiry 再チェック

`backend/api/.../configuration/transaction/Transaction.kt` `tx()`

- WebSocket は長命のため、ハンドシェイク時のみの検証では途中で失効したトークンを検出できない。
- 各 RPC 呼び出しで `Clock.System.now() > session.exp` を確認し、失効していれば `RpcError.Unauthorized`。**二段構え(ハンドシェイク時 + 呼び出し毎)** になっている点は良い設計。

---

## 4. 設定(環境変数)

| 変数 | backend(`application.yaml`) | frontend(`build.gradle.kts` codegen) |
|---|---|---|
| issuer | `AUTH_ISSUER` (def `http://localhost:8081`) | `AUTH_ISSUER` 同左 |
| audience | `AUTH_AUDIENCE` (def `mindstock-backend`) | `AUTH_AUDIENCE` |
| jwks | `AUTH_JWKS_URL` (def `.../oauth/v2/keys`) | — |
| client_id | — | `AUTH_CLIENT_ID`(必須) |
| redirect_uri | — | `AUTH_REDIRECT_URI` (def `http://localhost:8080/auth/callback`) |
| post_logout | — | `AUTH_POST_LOGOUT_REDIRECT_URI` (def `http://localhost:8080/`) |
| project_id | — | `AUTH_PROJECT_ID` |

- フロントの `AuthConfig` は **ビルド時に環境変数から `.kt` を生成**(`generateAuthConfig` task)。client secret は持たない(public client)。
- `redirect_uri` のデフォルトが backend と同じ `:8080` である点から、本番ではフロントとバックを **同一 origin** で配信する想定とみられる。

---

## 5. 妥当性検証

### 5.1 コードで検証済み(= 主張してよい強み)

| 項目 | 状態 |
|---|---|
| Authorization Code + PKCE(S256) | ✅ public client の正しい選択 |
| `state` 照合 | ✅ CSRF/リプレイ対策あり |
| JWT 署名検証(RS256 固定 + JWKS) | ✅ `alg` すり替え不可。crypto 自前実装なし |
| `iss` / `aud` / `exp`(30s leeway)検証 | ✅ いずれも明示 |
| JWKS の cache + rate-limit | ✅ |
| トークンの応答 echo 抑止 | ✅ 漏洩面を 1 つ潰している |
| RPC 呼び出し毎の expiry 再チェック | ✅ 長命 WS への対処 |
| 未登録ユーザの分離(`RequireRegisteredUserPlugin`) | ✅ |

### 5.2 CORS / CSRF が「無くて問題ない」理由(欠落ではない)

- backend に CORS 設定は **無い**。だが API 認証は **ヘッダ上の bearer token** で行い、**Cookie(ambient credential)を一切使わない**。
- → 他 origin のページが WebSocket を開くことはできても、**被害者のトークンを取得できない**ため、なりすまし送信(CSRF)が成立しない。よって CORS 不在は API にとって問題ない。これは「設定漏れ」ではなく設計上の帰結。

### 5.3 Zitadel 側設定に依存し、コードからは確認できない項目(要確認)

> **ここが「進めてよいか」の判断を左右する。コードがいくら正しくても、以下が外れていると機能しない/弱くなる。**

1. **【最重要】access token が JWT であること。**
   Zitadel の access token は **デフォルトで opaque(非 JWT)**。本バックエンドは access token を RS256 JWT として JWKS 検証している。アプリ設定が「JWT」になっていないと **バックエンド検証が成立しない**。→ Zitadel のアプリ設定で access token type = JWT を確認。
2. **refresh token のローテーション有無と寿命。**
   refresh token は `sessionStorage` に置かれる(§5.4)。**使い捨てローテーション**が有効なら盗難時の被害を限定できる。コードからは不明 → Zitadel 設定で確認。
3. **access token の TTL(寿命)。**
   本構成にトークン introspection / 失効確認は無く、**`exp` まではトークンが有効(=失効後も使える)**。これが許容できるのは **TTL が短い場合のみ**。TTL を短く保つことが唯一の緩和策。
4. **token endpoint の CORS(Zitadel 側)。**
   ブラウザが `/oauth/v2/token` へ直接 POST するため、Zitadel 側で SPA の origin を許可している必要がある。

### 5.4 本質的トレードオフ — トークンを `sessionStorage` に置くこと

- access / refresh / id token がすべて `sessionStorage`。**origin 上で XSS が 1 つでも起きると、長命な refresh token を含む全トークンが流出**する。これが本構成の最弱点。
- これは SPA + public client の **標準的な姿勢**であり「間違い」ではない。より高保証なのは **BFF(Backend for Frontend)/ HttpOnly Cookie** パターン(トークンをブラウザ JS から触れない場所に置く)。
- 採否は **扱うデータの機微性**次第。家庭の在庫情報は中程度。「現状の sessionStorage 方式で進める」か「BFF へ寄せる」かが、ユーザが実際に問うている設計判断の核心。

### 5.5 本番デプロイの必須条件

- コードのデフォルトは `http`/`ws`(localhost)。bearer JWT は `Sec-WebSocket-Protocol` リクエストヘッダで流れる。
- → **本番は `wss`/TLS 必須**。平文だと経路上でトークンが露出する。これは付帯事項ではなく **ハードな前提条件**。

### 5.6 堅牢性の軽微な課題(ブロッカーではない / 既知)

- `AuthBootstrap.ping` が **全 Throwable を `Unauthorized` 扱い** → 一時的なネットワーク断で `NeedRegister` に誤遷移しうる(メモリ「frontend auth は動くが素朴」と整合)。
- 並列 RPC が同時に refresh を走らせ、ローテーション有効時に片方が他方を無効化 → 不意のログアウトの可能性。`RpcCallWrapper`(1 回 refresh + retry)は存在するが bootstrap/register 経路には未配線。

---

## 6. 結論

- **コードに壊れている箇所は無い。** 実装された認証フローは OIDC / JWT 検証の定石を踏んでおり、設計判断(二段 expiry チェック、token echo 抑止、RS256 固定)も妥当。
- **妥当性は条件付きで成立する。** 次を満たせば「現状の機構で進めて問題ない」と言える:
  1. Zitadel で access token type = **JWT**(§5.3-1)
  2. refresh token **ローテーション**有効(§5.3-2)
  3. access token **TTL が短い**(§5.3-3)
  4. token endpoint の **CORS** が SPA origin を許可(§5.3-4)
  5. 本番は **`wss`/TLS**(§5.5)
- そのうえで「トークンを `sessionStorage` に置く(XSS で全トークン流出のリスク)」姿勢を受容するか、BFF へ寄せるかは、別途の意思決定として明示的に選ぶべき(§5.4)。

---

## 付録: 主要ファイル

**Backend**
- `backend/api/.../configuration/auth/MindstockAuthPlugin.kt` — JWT 検証 + session 組み立て
- `backend/api/.../configuration/auth/WsBearerTokenExtractor.kt` — トークン抽出
- `backend/api/.../configuration/auth/WsSubprotocolEcho.kt` — token echo 抑止
- `backend/api/.../configuration/auth/RequireRegisteredUserPlugin.kt` — 登録済み判定
- `backend/api/.../configuration/auth/MindstockSession.kt` — セッション値
- `backend/api/.../configuration/routing/RoutingConfiguration.kt` — route / JWKS / 認可
- `backend/api/.../configuration/transaction/Transaction.kt` — `tx()`(expiry 再チェック)
- `backend/api/src/main/resources/application.yaml` — auth 設定

**Frontend**
- `frontend/src/webMain/.../App.kt` — フロー統括(login / callback / logout)
- `frontend/.../auth/AuthClient.kt` — authorize URL / token 交換 / refresh / end_session
- `frontend/.../auth/AuthBootstrap.kt` — 起動時復元 + refresh
- `frontend/.../auth/AuthCallbackHandler.kt` — state 照合
- `frontend/.../auth/{Tokens,TokenStore,AuthState,Pkce}.kt`
- `frontend/.../rpc/RpcClientFactory.kt` — WS subprotocol へトークン注入
- `frontend/build.gradle.kts` — `generateAuthConfig`(`AuthConfig` codegen)

**Domain**
- `domain/.../model/user/auth/{AuthIdentity,AuthSubject,AuthProvider}.kt`

**Tests(挙動の根拠)**
- `backend/api/src/test/.../e2e/auth/JwtAuthE2eTest.kt` — expired / wrong iss / wrong aud / 未知鍵 → 401、subprotocol 経由成功、未登録 sub の扱い
