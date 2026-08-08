# session(セッション・認証)機能仕様書

## 機能概要

mindstock の認証は外部 IdP(Zitadel)に完全に委譲されている。バックエンドはパスワードや資格情報を一切保持せず、IdP が発行したアクセストークン(JWT)を検証することだけを行う。フロントエンド(Compose Multiplatform / Kotlin-Wasm)は OIDC の Authorization Code Flow + PKCE でトークンを取得し、WebSocket 接続時にそれを提示する。

本コンテキストが担うのは次の 4 点である。

1. **トークン検証**: WebSocket ハンドシェイク時に JWT の署名・発行者・受信者・有効期限を検証する(`MindstockAuthPlugin`)
2. **セッション組み立て**: 検証済みトークンの `sub` から認証アイデンティティを作り、対応する住人を引き当てて「登録済み」「未登録」いずれかのセッションを接続に紐づける(`MindstockSession`)
3. **認可ガード**: RPC メソッドごとに登録要件を宣言し、メッセージ単位で有効期限を再確認し、ドメイン例外を RPC エラー語彙へ翻訳する(`SessionGuard`)
4. **登録状態の通知**: クライアント起動時に登録済み/未登録を返し、ホームとオンボーディングの分岐材料を提供する(`SessionRpcService.whoami`)

全 RPC サービスは単一エンドポイント `/api/rpc`(WebSocket)に相乗りし、1 本の接続を多重化して使う。認証は接続確立時に 1 回だけ行われる。

## ユースケース

### UC-1: 初回サインイン(未認証 → 認可画面)

**アクター**: 利用者(有効なトークンを持たない)

**基本フロー**
1. 利用者がアプリを開く。フロントエンドの起動処理が走る。
2. 現在パスが `/auth/callback` ではなく、保管された有効なトークンも無いため、状態を「未認証」にする。
3. ウェルカム画面が表示される。
4. 利用者がサインインを押す。
5. PKCE 検証子(64 文字)と state 値(43 文字)を生成し、`sessionStorage` にキー `mindstock.oauth.verifier.v1` / `mindstock.oauth.state.v1` で保存する。
6. 認可 URL(`<issuer>/oauth/v2/authorize`)を組み立て、ブラウザを遷移させる。

**代替・例外フロー**
- 保管トークンがあっても、残り 30 秒以内に失効する場合は無効とみなし、同じく未認証扱いになる(`loadValidToken`)。

### UC-2: 認可コールバック(コード交換)

**アクター**: フロントエンド(利用者の IdP ログイン完了を契機に自動実行)

**基本フロー**
1. IdP が `/auth/callback?code=...&state=...` へリダイレクトする。
2. 起動処理がパスを見てコールバック処理に入る。
3. クエリに `error` が無いことを確認する。
4. `sessionStorage` の state 値とクエリの state 値が一致することを確認する。
5. `sessionStorage` の PKCE 検証子を取り出す。
6. トークンエンドポイント(`<issuer>/oauth/v2/token`)に `grant_type=authorization_code` で POST し、アクセストークン・リフレッシュトークン・ID トークン・有効期限を受け取る。
7. トークン一式を `sessionStorage` のキー `mindstock.tokens.v1` に保存する。
8. state 値と PKCE 検証子を削除する。
9. `/` へ置き換え遷移し、起動処理をやり直させる(UC-3 へ)。

**代替・例外フロー**
- クエリに `error` がある場合: `OidcException` を投げる。`access_denied`(利用者が同意を拒否)は再認証が必要と分類される。
- state 値が保存されていない、または一致しない場合: 中断する。
- `code` が無い、PKCE 検証子が保存されていない場合: 中断する。
- トークンエンドポイントが失敗を返した場合: レスポンス本文の `error` / `error_description` を読んで `OidcException` を投げる。`invalid_grant` は再認証が必要と分類される。JSON として解釈できない場合はエラーコード `http_<status>` になる。
- レスポンス本文が想定の形でない場合: エラーコード `parse_error` の例外。
- 上記いずれの失敗でも、起動状態は「失敗(ログイン失敗)」になり画面にメッセージが出る。

### UC-3: 接続と登録状態の判定(起動分岐)

**アクター**: フロントエンド起動処理 + バックエンド認証プラグイン

**基本フロー**
1. 保管された有効なトークンを取り出す。
2. `/api/rpc` へ WebSocket 接続する。ヘッダ `Sec-WebSocket-Protocol` に `mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` の 2 エントリを別々に付ける。
3. バックエンドの認証プラグインがトークンを検証し、住人を引き当て、セッションを接続に紐づける(詳細は「認証フロー」節)。
4. サーバは `mindstock.v1` だけをレスポンスヘッダに echo する。
5. フロントエンドが `SessionRpcService.whoami()` を呼ぶ。
6. **登録済みの場合**: 住人をアプリセッションに反映し、`HouseholdRpcService.list()` で世帯一覧を読み込む。永続化済みのアクティブ世帯があればそれを、無ければ先頭を選び、状態を「利用可能」にする。
7. **未登録の場合**: 状態を「オンボーディング必要」にする(UC-4 へ)。

**代替・例外フロー**
- 世帯が 1 件も無い場合: 状態を「世帯必要」にする。
- 接続や `whoami` が失敗した場合(通信失敗など): 状態を「失敗(起動失敗)」にする。処理のキャンセルだけは握り潰さず再送出する。
- トークン未提示・検証失敗の場合: サーバが 401 を返し、WebSocket ハンドシェイクが成立しない。フロントエンドからは通信失敗として観測される。

### UC-4: 住人としての初回登録

**アクター**: 利用者(認証済み・未登録)

**基本フロー**
1. オンボーディング画面で表示名を入力する。
2. `ResidentRegisterRpcService.register(displayName)` を呼ぶ。このメソッドは未登録セッションでも呼べる。
3. サーバは未登録セッションであることを確認し、セッションが持つ認証アイデンティティと表示名で住人を登録する。同一トランザクションで住人・認証アイデンティティの紐づけ・表示名履歴の 3 レコードを作る。
4. 作成された住人を返す。
5. フロントエンドは住人をアプリセッションに反映し、WebSocket を閉じて開き直す(セッションを登録済みへ昇格させるため)。
6. 続けて世帯の作成または参加へ進む。

**代替・例外フロー**
- すでに登録済みのセッションから呼んだ場合: 理由「already registered」の `Conflict` が返る。
- 表示名が空・空白のみ、または 100 文字超の場合: `IllegalArgumentException` が `BadRequest` に翻訳されて返る(前後の空白は除去されてから検証される)。
- 再接続の直前にトークンを失った場合: フロントエンドは「token lost during registration」で失敗する。
- 世帯作成をスキップした場合: 状態は「世帯必要」になる。

### UC-5: セッション失効と再認証

**アクター**: 利用者 / フロントエンドのエラーハンドリング

**基本フロー**
1. 利用者が操作し、任意の RPC メソッドが呼ばれる。
2. サーバの登録ガードがセッションの有効期限と現在時刻を比較し、期限切れなら処理本体を実行せず理由「token expired」の `Unauthorized` を返す。
3. フロントエンドの ViewModel が `Unauthorized` を検知し、再認証シグナルを発する。
4. 受け口がトークン保管を空にし、WebSocket を閉じ、認可画面へ遷移する(UC-1 の手順 5 以降に合流)。

**代替・例外フロー**
- 未登録セッションから登録済み必須のメソッドを呼んだ場合も `Unauthorized`(理由「registration required」)となり、同じ再認証導線に乗る。
- 再認証シグナルは重複を捨てる(未処理の要求があれば追加分は破棄)。
- IdP 側でトークンが失効させられた(revocation)場合は検知しない。有効期限が切れるまでセッションは有効なままである。

### UC-6: ログアウト

**アクター**: 利用者

**基本フロー**
1. 設定画面でログアウトを押す。
2. 再認証シグナルが発される(UC-5 の手順 4 と同じ処理)。
3. トークン保管が空になり、WebSocket が閉じ、認可画面へ遷移する。

**代替・例外フロー**
- (要確認: IdP のセッション終了エンドポイント(RP-initiated logout / `end_session`)は呼んでいない。Zitadel 側の設定には `postLogoutRedirectUris: ["http://localhost:8080/"]` があり `docker/zitadel-init.sh` は `AUTH_POST_LOGOUT_REDIRECT_URI` も書き出すが、フロントエンドのコードにこの値の参照が無い。そのため IdP 側のログインセッションは残り、再度サインインした際にログイン画面をスキップして即座に再認証される可能性がある)

## RPC インターフェース

### SessionRpcService

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionRpcService.kt` / 実装 `SessionController`

| メソッド名 | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `whoami()` | なし | `RpcResult<SessionStatus, RpcError>` — `SessionStatus.Registered(resident)` または `SessionStatus.Unregistered` | `Unauthorized`(有効期限切れ)/ `NotFound`(登録済みセッションだが住人再取得に失敗)/ `Internal`(想定外) |

- 登録ガードは `allowUnregistered`。認証済みであれば未登録でも呼べる。
- 登録済みの場合、`ResidentService.me(residentId)` で住人を取得して返す。

### ResidentRegisterRpcService(認証と接点のある部分のみ)

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRegisterRpcService.kt` / 実装 `ResidentRegisterController`

| メソッド名 | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `register(displayName: DisplayName)` | `DisplayName` | `RpcResult<Resident, RpcError>` | `Conflict`(理由「already registered」)/ `BadRequest`(表示名が不正)/ `Unauthorized`(有効期限切れ)/ `Internal` |
| `rename(displayName: DisplayName)` | `DisplayName` | `RpcResult<Unit, RpcError>` | `Unauthorized`(未登録セッション・有効期限切れ)/ `BadRequest`(表示名が不正)/ `Internal` |

- `register` は `allowUnregistered`。認証アイデンティティは引数ではなくセッションから取る(クライアントが他人になりすませない)。
- `rename` は `requireRegistered`(既定)。
- 住人そのものの詳細仕様は resident コンテキストの担当。ここでは認証・セッションとの接点のみ記載する。

### 登録ガードの一覧(session の観点)

| ガード | 対象メソッド | 未登録セッションでの挙動 |
|---|---|---|
| `allowUnregistered` | `SessionRpcService.whoami`、`ResidentRegisterRpcService.register` | 通過して処理本体を実行する |
| `requireRegistered`(既定) | 上記以外の全 RPC メソッド | `Unauthorized`(理由「registration required」)で短絡し、処理本体を実行しない |

## 認証フロー

### 全体シーケンス(初回サインイン〜アプリ利用)

```
利用者    フロントエンド(ブラウザ)         Zitadel                バックエンド
  |            |                              |                         |
  |-- 起動 --->|                              |                         |
  |            |-- 有効トークン確認(なし)     |                         |
  |<-- ウェルカム画面 --                       |                         |
  |-- サインイン ->|                           |                         |
  |            |-- verifier/state を sessionStorage に保存               |
  |            |------ GET /oauth/v2/authorize (code_challenge=S256) --->|
  |<-------------------- ログイン画面 ---------|                         |
  |-- 資格情報 -------------------------------->|                        |
  |            |<--- 302 /auth/callback?code=&state= --                  |
  |            |-- state 一致確認                                        |
  |            |------ POST /oauth/v2/token (code + code_verifier) ----->|
  |            |<----- access_token / refresh_token / id_token / expires_in
  |            |-- tokens を sessionStorage に保存, "/" へ replace       |
  |            |                                                        |
  |            |== WS ハンドシェイク /api/rpc =========================>|
  |            |   Sec-WebSocket-Protocol: mindstock.v1                 |
  |            |   Sec-WebSocket-Protocol: mindstock.bearer.<b64url(jwt)>|
  |            |                              |<-- GET JWKS(キャッシュ) -|
  |            |                              |--- 公開鍵 -------------->|
  |            |                                     [JWT 検証]         |
  |            |                                     [findByAuth]       |
  |            |                                     [session を接続に付与]
  |            |<== 101 Switching Protocols(Sec-WebSocket-Protocol: mindstock.v1)
  |            |                                                        |
  |            |-- whoami() ------------------------------------------->|
  |            |<-- Registered(resident) / Unregistered ----------------|
  |<-- ホーム or オンボーディング --             |                        |
```

### トークンの発行(フロントエンド側)

- **認可リクエスト**: `<issuer>/oauth/v2/authorize`
  - `response_type=code`
  - `client_id` = `AUTH_CLIENT_ID`(Zitadel の PKCE アプリ)
  - `redirect_uri` = `AUTH_REDIRECT_URI`(既定 `http://localhost:8080/auth/callback`)
  - `scope` = `openid profile offline_access urn:zitadel:iam:org:project:id:<AUTH_PROJECT_ID>:aud`
    - 末尾の Zitadel 固有スコープにより、発行されるアクセストークンの `aud` にプロジェクトの API アプリが載る
  - `state` = 43 文字のランダム値
  - `code_challenge` = PKCE 検証子の SHA-256 を base64url(パディングなし)にしたもの、`code_challenge_method=S256`
- **トークンリクエスト**: `<issuer>/oauth/v2/token`(`application/x-www-form-urlencoded`)
  - 認可コード交換: `grant_type=authorization_code` + `code` + `client_id` + `redirect_uri` + `code_verifier`
  - 更新: `grant_type=refresh_token` + `refresh_token` + `client_id`(実装はあるが呼び出し元が無い。後述)
- **有効期限の算出**: レスポンスの `expires_in`(秒)を受信時刻に加算して `expiresAt` を持つ。IdP のレスポンス値をそのまま信頼する。
- **PKCE の実装**: 検証子は 43〜128 文字を許容(既定 64)。文字集合は `A-Za-z0-9-._~`。乱数・SHA-256・base64url はプラットフォーム依存のため `expect/actual` で実装(`jsMain` / `wasmJsMain`)。

### トークンの検証(バックエンド側)

`MindstockAuthPlugin` はアプリケーションレベルで install され、`onCall` で **全リクエスト**に対して以下を順に行う。

1. **トークン抽出**(`WsBearerTokenExtractor`)
   - `Authorization: Bearer <token>` があればそれを使う(スキーム比較は大文字小文字を無視、値は trim)
   - 無ければ `Sec-WebSocket-Protocol` を見る。複数ヘッダ行をカンマで分割・trim し、`mindstock.bearer.` で始まるエントリを探す。**ちょうど 1 件**でなければ受理しない(0 件・複数件はいずれも不可)。base64url として復号できなければ受理しない
   - 取り出せなければ **401** を返して終了
2. **JWT 検証**(`com.auth0:java-jwt`)
   - アルゴリズム: RSA256 固定。公開鍵は `JwksKeyProvider` 経由で JWKS から取得(`kid` で引く)
   - `withIssuer(<AUTH_ISSUER>)` / `withAudience(<AUTH_AUDIENCE>)` を必須指定
   - `acceptLeeway(30)` 秒(`MindstockAuthConfig.leewaySeconds` の既定値)
   - 検証失敗は **401**
3. **クレーム確認**
   - `sub` が null または空白のみ → **401**
   - `exp` が無い → **401**
4. **セッション組み立て**
   - `AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))` を作る
   - 接続単位のトレース ID(`callId`)をランダム生成する
   - `ResidentRepository.findByAuth(identity)` を `Dispatchers.IO` 上で呼ぶ(内部は blocking JDBC トランザクション)
     - 見つかれば `MindstockSession.Registered(identity, residentId, exp, callId)`
     - `ResourceNotFoundException` なら `MindstockSession.Unregistered(identity, exp, callId)`
     - **それ以外の例外は吸収しない**。伝播させ、結果として 5xx になる(未登録に降格させない)
   - `call.attributes` に `MindstockSessionKey` で格納する
5. **サブプロトコルの echo**(`WsSubprotocolEchoPlugin`、別プラグイン)
   - `mindstock.v1` が提示されていれば、レスポンスヘッダに固定文字列 `mindstock.v1` だけを書く
   - トークンを含むエントリは決して echo しない(リクエスト由来の値を一切レスポンスに書かない)

**JWKS の取得設定**(`RoutingConfiguration`): `JwkProviderBuilder(<AUTH_JWKS_URL>).cached(10, 1, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES)`。キャッシュ 10 件・保持 1 時間、レート制限 1 分あたり 10 回。

**認証設定の読み込み**(`requireAuthSettings`): Ktor コンフィグの `external.auth.issuer` / `external.auth.audience` / `external.auth.jwks-url` を読む。環境変数はそれぞれ `AUTH_ISSUER` / `AUTH_AUDIENCE` / `AUTH_JWKS_URL`。**既定値は与えず**、未設定または空文字なら「`.env.zitadel` を生成しましたか?(`mise run up`)」という案内付きで起動時に停止する。

### セッションの検証(メッセージ単位)

WebSocket は張りっぱなしになるため、ハンドシェイク時の 1 回の検証では接続中の失効を取りこぼす。`SessionGuard` の `runGuarded` が全 RPC メソッドの入口で次を行う。

1. `Clock.System.now() > session.exp` なら、処理本体を実行せず `Unauthorized(reason = "token expired")` を返す
2. `requireRegistered` の場合、セッションが未登録なら `Unauthorized(reason = "registration required")` を返す(処理本体は実行しない)
3. `supervisorScope` の中で処理本体を実行し、例外を RPC エラー語彙へ翻訳する

JWT の署名検証はここでは行わない。接続時に保存した有効期限との比較だけである。IdP 側での即時失効(revocation)は検知対象外。

### セッションの失効・破棄

| 契機 | サーバ側の動き | クライアント側の動き |
|---|---|---|
| トークンの有効期限切れ | RPC メソッド入口で `Unauthorized` を返す | `Unauthorized` を検知 → 再認証シグナル → トークン破棄・WS 切断・認可画面へ |
| WebSocket 切断 | 接続に紐づくセッションが失われる(接続単位の生存) | 再接続時に改めてトークンを提示する |
| ログアウト操作 | (サーバへの通知なし) | 再認証シグナルと同じ処理 |
| 住人登録の直後 | (新しい接続で登録済みセッションを組み立て直す) | WS を close → connect し直す |
| タブを閉じる | — | `sessionStorage` の内容が破棄されるためトークンも消える |

### 永続化される認証情報

- **バックエンド**: `resident_auth_identities` テーブル(`ResidentAuthIdentitiesTable`)に `resident_id` / `provider` / `subject` / `linked_at` を保存する。`(provider, subject)` に一意インデックスがあり、同じ IdP アカウントを複数の住人に紐づけられない。`resident_id` は `ON DELETE RESTRICT`。トークンそのものは保存しない。
- **フロントエンド**: `sessionStorage` のみを使う(タブ寿命)。
  - `mindstock.tokens.v1` — アクセストークン / リフレッシュトークン / ID トークン / 失効時刻
  - `mindstock.oauth.state.v1` — state 値(コールバック処理後に削除)
  - `mindstock.oauth.verifier.v1` — PKCE 検証子(コールバック処理後に削除)
  - なお、アクティブ世帯 ID(`mindstock.active_household.v1`)は `localStorage` 側(`PreferenceStore`)に保存され、認証情報とは区別されている。

## エラー・例外

### バックエンド: HTTP 401 で拒否される条件(ハンドシェイク時)

`MindstockAuthPluginTest` で検証されている条件を含む。

| 条件 | 応答 |
|---|---|
| トークンが提示されていない | 401、セッション未付与 |
| 署名が不正(別鍵で署名) | 401 |
| 発行者(`iss`)が不一致 | 401 |
| 受信者(`aud`)が不一致 | 401 |
| 有効期限切れ(leeway 超過) | 401 |
| `sub` が空 | 401 |
| `exp` が欠落 | 401 |
| ベアラーサブプロトコルが 0 件または複数件 | トークン抽出に失敗し 401 |
| ベアラーサブプロトコルが base64url として不正 | トークン抽出に失敗し 401 |
| 住人検索がインフラ障害で失敗 | 5xx。**未登録セッションを作らず、成功応答も返さない** |

### バックエンド: RPC エラーへの翻訳(`runGuarded`)

| 例外 / 条件 | `RpcError` |
|---|---|
| 有効期限切れ(ガードでの短絡) | `Unauthorized(reason = "token expired")` |
| 未登録セッションで `requireRegistered` | `Unauthorized(reason = "registration required")` |
| `IllegalArgumentException`(値オブジェクトの値域・不変条件) | `BadRequest(field = "request", reason = …)` |
| `ResourceNotFoundException` | `NotFound(message = …)` |
| `OwnerRequiredException` | `Unauthorized` |
| `MembershipRequiredException` | `Unauthorized` |
| `LastOwnerException` | `Conflict` |
| `DuplicateJanException` | `Conflict` |
| `CannotArchiveWithStockException` | `Conflict` |
| `InsufficientStockException` | `Conflict` |
| `ArchivedProductMovementException` | `Conflict` |
| `InvitationInvalidException` | `Conflict` |
| `CancellationException` | **翻訳しない**。握り潰さず再送出する(構造化並行性のため) |
| その他の `Throwable` | `Internal(reason = "unexpected server error")`。呼び出し ID・認証サブジェクト・住人 ID とともに構造化ログへ記録 |

翻訳漏れは `Internal` に落ちる。新しいドメイン例外を追加した際は `SessionGuard.kt` の翻訳マップに追加する必要がある。

### バックエンド: 起動時エラー

- `external.auth.issuer` / `audience` / `jwks-url` のいずれかが未設定・空文字 → `IllegalStateException`(案内メッセージ付き)で起動失敗
- 認証プラグインの設定(`jwkProvider` / `issuer` / `audience` / `residentRepository`)のいずれかが未指定 → プラグイン install 時に `IllegalArgumentException`

### フロントエンド: OIDC 例外(`OidcException`)

エラーコード・説明・「再認証が必要か」のフラグを持つ。

| 発生元 | エラーコード | 再認証が必要 |
|---|---|---|
| コールバックの `error` パラメータ | IdP が返した値(例 `access_denied`) | `access_denied` のときのみ true |
| トークンエンドポイントのエラー応答 | レスポンスの `error`、解釈できなければ `http_<status>` | `invalid_grant` のときのみ true |
| トークン応答の解釈失敗 | `parse_error` | false |

その他、state 不一致は `IllegalArgumentException`、`code` / PKCE 検証子の欠落は `IllegalStateException` として中断する。

### フロントエンド: RPC エラーの扱い

- `RpcError` の全 variant を `errorText(error)`(`core/rpc/RpcErrors.kt`)で網羅的に文言化する(`else` なしの `when` なので、variant 追加時にコンパイルエラーで気づける)
- `RpcError.requiresReauth()` は `Unauthorized` のときのみ true
- ViewModel は `FailureHandler` を通し、`Unauthorized` なら再認証シグナル、それ以外は用途に応じてトーストまたはエラー状態へ倒す
- 起動処理での失敗は `AuthState.Failed` に倒す。ログイン失敗(コールバック処理の失敗)と起動失敗(接続・`whoami` の失敗)でメッセージを分けている

## 制約事項・要確認

### 明示的な非対象

- **IdP 側の即時失効(revocation)を検知しない**。`SessionGuard` のドキュメンテーションコメントに「IdP 側の即時失効(revocation)は対象外。守るのは JWT の有効期限切れのみ」と明記されている。IdP でトークンを失効させても、有効期限が切れるまで既存の WebSocket 接続は使え続ける。
- **バックエンドは認証セッションを永続化しない**。セッションは WebSocket 接続の生存期間だけ存在する接続単位のインメモリ状態であり、Cookie もサーバサイドセッションストアも使っていない(したがって Cookie 属性という概念自体が存在しない)。

### 要確認事項

- **リフレッシュトークンによる自動更新が未配線**: `AuthClient.refresh(refreshToken)` は実装され、`offline_access` スコープでリフレッシュトークンも取得・保存されているが、リポジトリ内に `refresh` の呼び出し元が存在しない。結果として、アクセストークンが失効すると(残り 30 秒を切った時点で)利用者は認可画面へ飛ばされ、IdP のセッションが生きていれば黙って再認証されることになる。意図的な未実装か、配線漏れかはコードからは判断できない。
- **アクセストークンの有効期限がコードから読み取れない**: バックエンドは JWT の `exp` をそのまま使い、フロントエンドはトークン応答の `expires_in` をそのまま使う。実際の秒数は Zitadel 側の設定に依存し、リポジトリ内(`docker/zitadel-init.sh` を含む)に明示的な設定は無い。
- **ログアウト時に IdP のセッションを終了していない**: Zitadel アプリには `postLogoutRedirectUris` が設定され、`docker/zitadel-init.sh` は `AUTH_POST_LOGOUT_REDIRECT_URI` を `.env.zitadel` に書き出すが、フロントエンドのコードからこの値への参照が無く、`end_session` エンドポイントも呼んでいない。ログアウトはローカルのトークン破棄にとどまる。
- **ID トークンを検証していない**: トークン応答の `id_token` は保存されるが、フロントエンド・バックエンドのどちらでも署名検証やクレーム参照をしている箇所が見当たらない。何のために保持しているかはコードから読み取れない。
- **認証プラグインが全リクエストに適用される**: `MindstockAuthPlugin` はアプリケーションレベルで install され、`onCall` で全リクエストを対象にする。現在のルーティングは `/api/rpc` の 1 つだけなので実害は無いが、ヘルスチェックや静的配信のエンドポイントを追加すると、それらも 401 になる。意図的な設計か、ルーティングが 1 つしかない現状に依存しているだけかは判断できない。
- **`leewaySeconds` を外部設定から変更できない**: 既定 30 秒は `MindstockAuthConfig` のプロパティだが、`RoutingConfiguration` の install ブロックで設定していないため、環境変数やコンフィグからは変更できない。
- **`AuthProvider` が ZITADEL のみ**: 列挙型に他の値が無く、認証アイデンティティを作る箇所もハードコードされている。複数 IdP を想定した拡張ポイントとして残されているのか、単に Zitadel 専用なのかは読み取れない。
- **開発環境の設定値**: `docker/zitadel-init.sh` が生成する `.env.zitadel` は `AUTH_ISSUER=http://localhost:8081` / `AUTH_JWKS_URL=http://localhost:8081/oauth/v2/keys` / `AUTH_REDIRECT_URI=http://localhost:8080/auth/callback` を含む。`AUTH_AUDIENCE` は Zitadel の API アプリのクライアント ID、`AUTH_CLIENT_ID` は PKCE アプリのクライアント ID が入る。これらはローカル開発用であり、本番の値・運用手順はリポジトリ内に記載が無い。
- **フロントエンドの認証設定はビルド時に埋め込まれる**: `frontend/build.gradle.kts` の `generateAuthConfig` タスクが環境変数 `AUTH_ISSUER` / `AUTH_REDIRECT_URI` / `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` から `AuthConfig.kt` を生成する。`AUTH_CLIENT_ID` と `AUTH_PROJECT_ID` には既定値が無いため、未設定だとビルドが失敗する。実行時の切り替えはできない。
