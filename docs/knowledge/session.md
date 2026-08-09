# session(セッション・認証)コンテキスト

## コンテキスト概要

mindstock の利用者は外部 IdP(Zitadel)で認証され、その結果として発行されたアクセストークン(JWT)を使ってバックエンドに接続する。session コンテキストは「IdP が認証した人物(認証アイデンティティ)」と「mindstock 内部の住人(Resident)」を突き合わせ、1 本の WebSocket 接続に対して不変なセッションを組み立てる責務を持つ。バックエンドは自前でパスワードや資格情報を保持せず、認証は完全に IdP に委譲している。

このコンテキストの中心概念は「認証は済んでいるが、mindstock にはまだ住人として登録されていない」状態を一級市民として扱う点にある。セッションは `Registered` / `Unregistered` の 2 状態を持つ sealed 型で表現され、RPC メソッドごとに「登録済み必須(既定)」か「未登録でも可(初回登録・状態問い合わせのみ)」かを宣言する。全 RPC サービスは単一エンドポイント `/api/rpc` に相乗りし、WebSocket ハンドシェイク時に 1 度だけ JWT を検証したうえで、以後のメッセージごとに有効期限を再チェックする。

## 用語集

### セッション
- **定義**: 1 本の WebSocket 接続に紐づく認証結果。誰として接続しているか(認証アイデンティティ)、mindstock 内部の住人 ID(登録済みの場合)、トークンの有効期限、接続単位のトレース ID を持つ。接続確立時に組み立てられ、接続中は不変。
- **別名**: MindstockSession
- **関連用語**: 登録済みセッション、未登録セッション、認証アイデンティティ、トークン有効期限、呼び出し ID
- **実装**: `MindstockSession`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)

### 登録済みセッション
- **定義**: JWT が有効で、かつその認証サブジェクトに対応する住人が mindstock に登録済みであるセッション。住人 ID を保持し、全 RPC メソッドを呼べる。
- **別名**: MindstockSession.Registered
- **関連用語**: セッション、未登録セッション、住人 ID
- **実装**: `MindstockSession.Registered`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)

### 未登録セッション
- **定義**: JWT は有効だが、その認証サブジェクトに対応する住人がまだ mindstock に存在しないセッション。住人 ID を持たない。未登録でも呼べると宣言されたメソッド(住人登録・登録状態問い合わせ)だけを通過できる。
- **別名**: MindstockSession.Unregistered
- **関連用語**: セッション、登録済みセッション、登録ガード
- **実装**: `MindstockSession.Unregistered`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)

### 認証アイデンティティ
- **定義**: 「どの認証プロバイダの、どのサブジェクトか」の組。mindstock はこの組で外部 IdP 上の人物を一意に識別し、住人と紐づける。
- **別名**: AuthIdentity
- **関連用語**: 認証プロバイダ、認証サブジェクト、住人
- **実装**: `AuthIdentity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthIdentity.kt`)

### 認証プロバイダ
- **定義**: 認証を担う外部 IdP の種別。現在は Zitadel のみ。
- **別名**: AuthProvider、IdP
- **関連用語**: 認証アイデンティティ、Zitadel
- **実装**: `AuthProvider`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthProvider.kt`)

### 認証サブジェクト
- **定義**: 認証プロバイダが発行する利用者の一意識別子。JWT の `sub` クレームの値。空文字・空白のみは許容しない。
- **別名**: AuthSubject、sub
- **関連用語**: 認証アイデンティティ、アクセストークン
- **実装**: `AuthSubject`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthSubject.kt`)

### アクセストークン
- **定義**: IdP が発行する JWT。バックエンドへの接続時に提示され、署名・発行者・受信者・有効期限が検証される。Zitadel 側では「アクセストークン形式を JWT にする」設定(`accessTokenType: OIDC_TOKEN_TYPE_JWT`)で発行される。
- **別名**: access token、JWT、ベアラートークン
- **関連用語**: トークン有効期限、JWKS、ベアラーサブプロトコル、リフレッシュトークン
- **実装**: `Tokens`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Tokens.kt`)、検証は `MindstockAuthPlugin`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`)

### トークン有効期限
- **定義**: JWT の `exp` クレームが表す時点。セッションに保持され、RPC メッセージごとに現在時刻と比較して失効判定に使う。`exp` を持たない JWT は受理しない。
- **別名**: exp、expiresAt
- **関連用語**: アクセストークン、セッション、セッション失効
- **実装**: `MindstockSession.exp`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)、判定は `runGuarded`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`)

### 呼び出し ID
- **定義**: 接続単位で採番されるランダムなトレース ID。構造化ログに出力し、想定外エラーの発生元をたどるために使う。
- **別名**: callId
- **関連用語**: セッション
- **実装**: `MindstockSession.callId`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)

### JWKS
- **定義**: IdP が公開する署名検証用の公開鍵集合。バックエンドは JWKS から取得した RSA 公開鍵で JWT の署名を検証する。秘密鍵は保持しない(検証専用)。
- **別名**: JSON Web Key Set、jwks-url
- **関連用語**: アクセストークン、認証設定
- **実装**: `JwksKeyProvider`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwksKeyProvider.kt`)

### 認証設定
- **定義**: JWT 検証に必要な外部 IdP 設定(発行者・受信者・JWKS URL)の 3 点。いずれか 1 つでも未設定・空文字なら、アプリケーションは起動時に即エラーで停止する(既定値を与えない)。
- **別名**: AuthSettings
- **関連用語**: JWKS、発行者、受信者
- **実装**: `AuthSettings` / `requireAuthSettings`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthSettings.kt`)

### アプリサブプロトコル
- **定義**: WebSocket ハンドシェイクで提示するアプリケーション識別子 `mindstock.v1`。サーバはこれが提示されたときだけ、固定文字列としてレスポンスに echo する。
- **別名**: mindstock.v1、APP_PROTOCOL
- **関連用語**: ベアラーサブプロトコル
- **実装**: `WebSocketProtocols.APP_PROTOCOL`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocols.kt`)

### ベアラーサブプロトコル
- **定義**: WebSocket ハンドシェイクでアクセストークンを運ぶための `mindstock.bearer.<base64url(jwt)>` 形式のサブプロトコルエントリ。ブラウザの WebSocket API が `Authorization` ヘッダを付けられないための代替経路。ちょうど 1 件だけ提示された場合に受理し、複数件(曖昧)なら受理しない。トークンを含むため、レスポンスヘッダに echo してはならない。
- **別名**: mindstock.bearer.、BEARER_PREFIX
- **関連用語**: アプリサブプロトコル、アクセストークン
- **実装**: `WebSocketProtocols.bearerToken()`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocols.kt`)、`WsBearerTokenExtractor`(同ディレクトリ)

### 登録ガード
- **定義**: RPC メソッド単位で「登録済み住人であることを要求するか」を宣言する仕組み。既定は登録済み必須(フェイルクローズ)で、未登録でも呼べるメソッドだけが明示的にその旨を宣言する。有効期限チェックとドメイン例外の RPC エラーへの翻訳も同じ場所で行う。
- **別名**: requireRegistered / allowUnregistered、SessionGuard
- **関連用語**: 登録済みセッション、未登録セッション、セッション失効
- **実装**: `requireRegistered` / `allowUnregistered` / `runGuarded`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`)

### セッション状態
- **定義**: クライアントの起動時に「この接続が登録済みか未登録か」を返す通信用の型。登録済みなら住人そのものを含む。フロントエンドはこれを見てホーム画面とオンボーディングを分岐する。
- **別名**: SessionStatus
- **関連用語**: 登録済みセッション、未登録セッション、起動分岐
- **実装**: `SessionStatus`(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/session/SessionStatus.kt`)

### 起動分岐
- **定義**: フロントエンドの起動時に、コールバック処理・未認証・オンボーディング必要・世帯必要・利用可能・失敗のいずれかへ画面を振り分ける判断。
- **別名**: AuthState、boot
- **関連用語**: セッション状態、再認証
- **実装**: `AuthState`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/AuthState.kt`)、`AuthViewModel.boot()`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt`)

### PKCE 検証子
- **定義**: 認可コード横取り対策として、認可リクエストの前にクライアントが生成するランダム文字列。そのハッシュ(S256)を認可リクエストに載せ、コード交換時に元の文字列を提示する。
- **別名**: code_verifier、PKCE
- **関連用語**: 認可コード、state 値
- **実装**: `Pkce`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt`)

### state 値
- **定義**: 認可リクエストに載せ、コールバックで一致を確認する使い捨ての値。一致しない場合はコールバック処理を中断する。PKCE 検証子と同じ生成器で 43 文字として作られる。
- **別名**: state
- **関連用語**: PKCE 検証子、認可コード
- **実装**: `WebAuthDeps.redirectToAuthorize` / `handleCallback`(`frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/WebAuthDeps.kt`)

### 認可コード
- **定義**: IdP のログイン完了後にコールバック URL へ返される一時的な引換券。PKCE 検証子と併せて提示することでトークンに交換する。
- **別名**: code、authorization code
- **関連用語**: PKCE 検証子、state 値、アクセストークン
- **実装**: `AuthClient.exchangeCode`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt`)

### リフレッシュトークン
- **定義**: アクセストークンを再発行するための長期トークン。`offline_access` スコープにより IdP から受け取り、保存もされている。(要確認: 交換処理 `AuthClient.refresh` は実装されているが、リポジトリ内に呼び出し元が無く、実際の自動更新は未配線)
- **別名**: refresh_token
- **関連用語**: アクセストークン、再認証
- **実装**: `Tokens.refreshToken` / `AuthClient.refresh`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/`)

### 再認証
- **定義**: 保存済みトークンを破棄し、WebSocket を閉じ、IdP の認可画面へ遷移し直す一連の動き。認可エラーを受け取ったときと、利用者がログアウトを選んだときの両方が同じ導線に集約されている。
- **別名**: reauth、ReauthController
- **関連用語**: 起動分岐、セッション失効、ログアウト
- **実装**: `ReauthController`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/auth/ReauthController.kt`)、受け口は `App.kt`(`frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`)

### トークン保管
- **定義**: 取得したトークン一式のブラウザ側の保存場所。タブを閉じると破棄される `sessionStorage` に、キー `mindstock.tokens.v1` で保存する(永続化しない)。
- **別名**: TokenStore
- **関連用語**: アクセストークン、再認証
- **実装**: `TokenStore`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt`)、`SessionStorage`(同ディレクトリの expect/actual)

### 認証アイデンティティの紐づけ
- **定義**: 住人と認証アイデンティティを結ぶ永続レコード。プロバイダとサブジェクトの組に一意制約があり、同じ IdP アカウントを複数の住人に紐づけることはできない。
- **別名**: resident_auth_identities
- **関連用語**: 認証アイデンティティ、住人
- **実装**: `ResidentAuthIdentitiesTable`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/ResidentAuthIdentitiesTable.kt`)

## 業務イベント

### ログインを開始する
- **概要**: 利用者がサインインを選ぶと、PKCE 検証子と state 値を生成してブラウザに保存し、IdP の認可エンドポイントへ遷移する。
- **アクター**: 利用者(未認証、またはトークンを失った状態)
- **対象**: 自分のブラウザセッション
- **事前条件**: 有効なアクセストークンが保管されていない(または再認証が要求された)
- **事後条件**: PKCE 検証子と state 値が `sessionStorage` に保存され、ブラウザが IdP の認可画面へ遷移している
- **取消・失敗**: 利用者は IdP 画面で同意を拒否できる。その場合コールバックに `error=access_denied` が返り、再認証が必要と判定される
- **順序・タイミング**: 起動時にトークンが無い場合はウェルカム画面のボタン押下で発火する。再認証要求(認可エラー・ログアウト)でも同じ処理が発火する

### 認可コードを交換する
- **概要**: IdP からコールバック URL に戻ってきた認可コードを、保存しておいた PKCE 検証子とともにトークンエンドポイントへ送り、アクセストークン一式を受け取る。
- **アクター**: フロントエンド(利用者の IdP ログイン完了を契機に自動実行)
- **対象**: 認可コード
- **事前条件**: 現在のパスが `/auth/callback` であること。保存済みの state 値と、コールバックで返された state 値が一致すること。PKCE 検証子が保存されていること
- **事後条件**: トークン一式が `sessionStorage` に保存され、state 値と PKCE 検証子が削除され、ブラウザが `/` に置き換え遷移する(その結果、起動処理がやり直される)
- **取消・失敗**: コールバックに `error` が付いていれば例外。state 不一致・コード欠落・検証子欠落でも中断。トークンエンドポイントがエラーを返した場合も例外(`invalid_grant` は再認証が必要と分類される)。いずれもログイン失敗として画面に表示される
- **順序・タイミング**: 「ログインを開始する」の後続。成功後は必ず起動処理からやり直される

### セッションが確立される
- **概要**: WebSocket ハンドシェイクの時点でアクセストークンを検証し、認証アイデンティティを組み立て、対応する住人が存在するかを引き当てて、登録済み/未登録いずれかのセッションを接続に紐づける。
- **アクター**: バックエンド(認証プラグイン)
- **対象**: 受信したリクエスト(WebSocket ハンドシェイク)
- **事前条件**: `Authorization: Bearer <jwt>` ヘッダ、またはベアラーサブプロトコルのいずれかでトークンが提示されていること
- **事後条件**: 接続に不変のセッションが紐づく。住人が引き当てられれば登録済みセッション、引き当てられなければ未登録セッション
- **取消・失敗**: トークン未提示・署名不正・発行者不一致・受信者不一致・有効期限切れ・`sub` が空・`exp` 欠落 のいずれでも 401 を返しセッションを作らない。住人検索がインフラ障害で失敗した場合は未登録に降格させず、例外をそのまま伝播させる(結果として 5xx)
- **順序・タイミング**: 1 接続につき 1 回。以後のメッセージでは JWT の再検証は行わず、有効期限だけを都度チェックする

### 登録状態を問い合わせる
- **概要**: クライアントが起動時に「この接続は登録済みか」を尋ね、登録済みなら住人そのものを受け取る。
- **アクター**: フロントエンド(起動処理)
- **対象**: 現在の接続
- **事前条件**: セッションが確立していること(未登録セッションでも呼べる)
- **事後条件**: 登録済みなら住人を含む状態、未登録ならその旨が返る。フロントエンドは前者で世帯一覧の読み込みへ、後者でオンボーディングへ進む
- **取消・失敗**: 有効期限切れなら認可エラー。住人の再取得に失敗した場合は「見つからない」エラーになりうる
- **順序・タイミング**: 「セッションが確立される」の直後、アプリ本体の描画前

### 住人として登録する
- **概要**: 未登録セッションの利用者が表示名を登録し、住人レコードと認証アイデンティティの紐づけが作られる。
- **アクター**: 利用者(認証済み・未登録)
- **対象**: 自分自身(住人)
- **事前条件**: セッションが未登録であること。表示名が空でなく 100 文字以内であること(前後の空白は除去される)
- **事後条件**: 住人・認証アイデンティティの紐づけ・表示名の履歴が同一トランザクションで作成され、住人が返る。フロントエンドはこの後 WebSocket を張り直し、セッションを登録済みへ昇格させる
- **取消・失敗**: すでに登録済みのセッションから呼ぶと競合エラー。表示名が不正なら入力エラー。取り消し(登録の撤回)は用意されていない
- **順序・タイミング**: 「登録状態を問い合わせる」で未登録と判明した後のオンボーディング内。登録成功後に接続の張り直しが続く

### セッションが失効する
- **概要**: RPC メッセージを処理する直前に、セッションが保持する有効期限を現在時刻と比較し、過ぎていれば処理を実行せず認可エラーを返す。
- **アクター**: バックエンド(登録ガード)
- **対象**: 現在の接続
- **事前条件**: セッションが確立していること
- **事後条件**: 業務処理は一切実行されず、理由「トークン期限切れ」の認可エラーが返る
- **取消・失敗**: 復旧手段は再認証のみ。IdP 側での即時失効(revocation)は検知対象外で、守るのは有効期限切れだけ
- **順序・タイミング**: 全 RPC メソッドの入口。WebSocket は張りっぱなしになるため、ハンドシェイク時の 1 回の検証では取りこぼす分をここで補う

### 再認証が要求される
- **概要**: RPC 呼び出しが認可エラーを返したとき、フロントエンドは再認証シグナルを発し、保管中のトークンを破棄して WebSocket を閉じ、認可画面へ遷移する。
- **アクター**: フロントエンド(各画面のエラーハンドリング)
- **対象**: 自分のブラウザセッション
- **事前条件**: RPC の結果が認可エラーであること
- **事後条件**: トークン保管が空になり、WebSocket が閉じ、ブラウザが IdP の認可画面へ遷移する
- **取消・失敗**: シグナルは重複要求を捨てる(未処理の要求があれば追加分は破棄)
- **順序・タイミング**: 任意の RPC 呼び出しの直後。「ログインを開始する」に合流する

### ログアウトする
- **概要**: 利用者が設定画面でログアウトを選ぶと、再認証と同じ導線(トークン破棄・接続切断・認可画面へ遷移)が実行される。
- **アクター**: 利用者
- **対象**: 自分のブラウザセッション
- **事前条件**: 利用可能状態でアプリを開いていること
- **事後条件**: ローカルのトークンが破棄され、認可画面へ遷移する
- **取消・失敗**: (要確認: IdP のセッション終了エンドポイント(RP-initiated logout)は呼んでいない。Zitadel 側には `postLogoutRedirectUris` が設定され `AUTH_POST_LOGOUT_REDIRECT_URI` も生成されるが、フロントエンドのコードからは参照されていないため、IdP 側のログインセッションは残る)
- **順序・タイミング**: 利用者の任意のタイミング。「ログインを開始する」に合流する

## 業務ルール

- アクセストークンの署名検証は自前実装せず、`com.auth0:java-jwt` の検証器を経由する(根拠: `MindstockAuthPlugin`)
- 署名アルゴリズムは RSA256 に固定し、公開鍵は JWKS から取得する。秘密鍵は保持しない(根拠: `MindstockAuthPlugin`、`JwksKeyProvider`)
- JWT の検証では発行者(`iss`)と受信者(`aud`)の一致を必ず要求する(根拠: `MindstockAuthPlugin`)
- 時刻ずれの許容は明示指定する。既定は 30 秒(根拠: `MindstockAuthConfig.leewaySeconds`)
- `sub` が空・空白のみのトークン、`exp` を持たないトークンは受理しない(根拠: `MindstockAuthPlugin`、`AuthSubject`)
- JWKS はキャッシュ(10 件・1 時間)とレート制限(1 分あたり 10 回)を掛けて取得する(根拠: `RoutingConfiguration` の `JwkProviderBuilder` 設定)
- 認証設定(発行者・受信者・JWKS URL)は既定値を与えず、未設定なら起動時に停止する(根拠: `requireAuthSettings`)
- トークンを含むベアラーサブプロトコルはレスポンスヘッダに echo しない。echo するのは固定のアプリ識別子 `mindstock.v1` のみで、リクエスト由来の値を一切レスポンスに書かない(根拠: `WsSubprotocolEchoPlugin`)
- トークンの提示経路は `Authorization` ヘッダを優先し、無い場合にベアラーサブプロトコルを見る(根拠: `WsBearerTokenExtractor`)
- ベアラーサブプロトコルのエントリがちょうど 1 件でない場合(0 件・複数件)は受理しない。base64url として復号できない場合も受理しない(根拠: `WebSocketProtocols.bearerToken`)
- 「JWT は有効だが住人が未登録」だけを未登録セッションとして扱う。住人検索がそれ以外の理由で失敗した場合は未登録に降格させず、例外を伝播させる(根拠: `MindstockAuthPlugin`)
- RPC メソッドの既定は登録済み必須(フェイルクローズ)。未登録で呼べるのは登録状態の問い合わせと住人登録のみ(根拠: `SessionGuard`、`SessionController`、`ResidentRegisterController`)
- 未登録セッションから登録済み必須のメソッドを呼ぶと、理由「registration required」の認可エラーになり、処理本体は実行されない(根拠: `requireRegistered`)
- すでに登録済みのセッションから住人登録を呼ぶと、理由「already registered」の競合エラーになる(根拠: `ResidentRegisterController.register`)
- 全 RPC メソッドの入口で有効期限を現在時刻と比較し、過ぎていれば処理を実行せず認可エラーを返す(根拠: `runGuarded`)
- IdP 側での即時失効(revocation)は検知しない。守るのは有効期限切れのみ(根拠: `SessionGuard` のドキュメンテーションコメント)
- 処理のキャンセルは握り潰さず再送出する。それ以外のドメイン例外は RPC エラー語彙へ翻訳し、未知の例外は内部エラーとして構造化ログ(呼び出し ID・認証サブジェクト・住人 ID)に記録する(根拠: `runGuarded`)
- 1 つの認証アイデンティティ(プロバイダ + サブジェクト)は 1 人の住人にしか紐づけられない(根拠: `ResidentAuthIdentitiesTable` の一意インデックス)
- 全 RPC サービスは単一エンドポイント `/api/rpc` に相乗りし、認証は接続確立時に 1 回だけ行う(根拠: `RoutingConfiguration`)
- ブラウザ側のトークンはタブ寿命の `sessionStorage` に置き、永続化しない(根拠: `TokenStore` と `SessionStorage`)
- 起動時、残り 30 秒以内に失効するトークンは無効とみなし、先回りして再認証へ倒す(根拠: `WebAuthDeps.loadValidToken`)
- 認可コードの交換は、保存済み state 値とコールバックの state 値が一致する場合にのみ行う(根拠: `WebAuthDeps.handleCallback`)
- 認可リクエストの PKCE 方式は S256 固定(根拠: `AuthClient.buildAuthorizeUrl`)

## 設計判断

### 未登録状態を null ではなく sealed 型の 2 状態で表す
- **判断**: 「認証済みだが住人未登録」をセッションの nullable フィールドではなく、`Registered` / `Unregistered` の 2 variant を持つ sealed interface で表現する。
- **理由**: プロジェクトの「nullable 戻り値原則禁止」ルールに従い、不在の意味を型に現して呼び出し側に分岐を強制するため。`MindstockSession` のコメントに「nullable で表さず sealed 2 状態で表現する(nullable 戻り値禁止原則。承認済)」と明記されている。

### 登録要件を経路(ルート)ではなくメソッド単位で宣言する
- **判断**: 「登録済みが必要か」を Ktor のルート分割で表さず、各 RPC メソッド内のガードヘルパーで宣言する。既定は登録済み必須で、例外だけが明示的に未登録許可を宣言する。
- **理由**: 全サービスが単一の WebSocket エンドポイントに相乗りするため、ルートでは登録要件を分けられない。既定を必須にすることで宣言漏れが「通ってしまう」方向ではなく「弾かれる」方向に倒れる(フェイルクローズ)。

### WebSocket でも接続後に有効期限を再チェックする
- **判断**: ハンドシェイク時に JWT を検証したうえで、さらに RPC メッセージごとに有効期限を確認する。
- **理由**: WebSocket は張りっぱなしになるため、ハンドシェイク時の 1 回きりの検証では接続中の失効を取りこぼす。ただし JWT の署名検証まで毎回やり直すのではなく、接続時に保存した有効期限との比較だけを行う。

### トークンを WebSocket サブプロトコルで運ぶ
- **判断**: ブラウザからの接続では、アクセストークンを `Sec-WebSocket-Protocol` の `mindstock.bearer.<base64url(jwt)>` エントリに載せる。ただし `Authorization` ヘッダがあればそちらを優先する。
- **理由**: ブラウザの WebSocket API は任意のリクエストヘッダ(`Authorization` を含む)を付けられない。ヘッダ優先にしているのは REST 互換性とテスト容易性のため。

### レスポンスにはリクエスト由来の値を一切書かない
- **判断**: サブプロトコルの echo では、受理したアプリ識別子を固定文字列としてのみ書き戻し、リクエストから読んだ値をそのまま返さない。
- **理由**: WebSocket 仕様上、クライアントがサブプロトコルを提示したらサーバは 1 つ echo しないとブラウザが接続を失敗させる。一方でトークンを含むエントリを echo するとレスポンスヘッダや中間プロキシのログにトークンが漏れる。

### 認証設定に既定値を与えず起動時に停止する
- **判断**: 発行者・受信者・JWKS URL のいずれかが未設定なら、案内メッセージ付きで起動時に即エラーにする。
- **理由**: コメントに「誤った既定値で起動する方が危険」と明記されている。認証の要となる値が黙って既定値で埋まると、意図しない発行者のトークンを受理しうる。

### インフラ障害を未登録に降格させない
- **判断**: 住人検索で「見つからない」例外だけを吸収して未登録セッションにし、それ以外の例外(DB 障害など)は握り潰さず伝播させる。
- **理由**: 障害時に未登録として通してしまうと、本来登録済みの利用者がオンボーディングに落ちたり、認可の前提が崩れたりする。テスト(`MindstockAuthPluginTest`)でも「2xx にならない」ことが検証されている。

### 登録直後は WebSocket を張り直す
- **判断**: 住人登録が成功したら、フロントエンドは接続を閉じて開き直す。
- **理由**: セッションは接続確立時に組み立てられ接続中は不変なので、登録前に張った接続は未登録セッションのままになる。張り直すことで登録済みセッションに昇格させる。

### ログアウトを再認証の導線に集約する
- **判断**: 設定画面のログアウトは、認可エラー起点の再認証と同じシグナルを発する。
- **理由**: (要確認: コード上は同一シグナルへの集約が事実だが、その選択理由はコード・規約からは読み取れない。ローカルのトークン破棄と認可画面への遷移という結果が同じであるため共用していると推測されるが、IdP のセッション終了を呼ばない点まで意図的かは不明)
