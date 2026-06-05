# mindstock
Household consumables inventory manager — keep your home's stock out of your head.

## Zitadel (ローカル認証 IdP)

backend は OIDC access_token (JWT) を Zitadel で検証する。ローカル開発では compose 上に PostgreSQL と Zitadel を起動する(`compose.yml` の `postgres` / `zitadel` 2 サービス。Zitadel も同じ `postgres` を使う)。

### 起動

```sh
docker compose up -d            # postgres(:5432) + zitadel(:8081)
```

http://localhost:8081 が応答するまで 30 秒ほど待つ。

### 初回セットアップ (UI 操作)

1. http://localhost:8081 にブラウザでアクセス
2. `admin@localhost` / `Password1!` でログイン
3. Project `mindstock` を作成
4. Application `mindstock-frontend` を「User Agent (PKCE)」で作成
5. API `mindstock-backend` を「JWT」で作成。作成後に表示される Resource ID(数値文字列)をコピーして `AUTH_AUDIENCE` に設定する

### backend 用の環境変数

```sh
AUTH_ISSUER="http://localhost:8081"
AUTH_AUDIENCE="<API mindstock-backend の Resource ID をここに>"
AUTH_JWKS_URL="http://localhost:8081/oauth/v2/keys"
# DB は application.yaml の既定(jdbc:postgresql://localhost:5432/mindstock, mindstock/mindstock)が
# compose の postgres と一致するため、通常は未設定で可。変えたい場合のみ DB_JDBC_URL / DB_USERNAME / DB_PASSWORD。
```

`AUTH_AUDIENCE` は初回セットアップで Zitadel が割り当てる Resource ID(数値文字列)なので、必ず実際の値で上書きすること(`application.yaml` のデフォルト `mindstock-backend` のままだと、Zitadel access_token の `aud` claim と一致せず検証失敗となる)。

backend はデフォルト :8080 で起動するが、frontend dev server も :8080 を使い `/api` を **:8090** にプロキシする(`frontend/webpack.config.d/proxy.js`)。そのため backend は **`PORT=8090`** で起動する(下記)。起動時に Flyway migration が `postgres` に対して走る。

### frontend 用の Application 設定

Application `mindstock-frontend` (User Agent / PKCE) に対して、Zitadel 管理 UI で以下も登録しておく:

- Redirect URIs: `http://localhost:8080/auth/callback`
- Post Logout Redirect URIs: `http://localhost:8080/`

控えるべき値:

- **Client ID** (Application 作成後に表示) → `AUTH_CLIENT_ID`
- **API mindstock-backend の Resource ID**(上記と同じ) → `AUTH_AUDIENCE`
- **Project mindstock の ID**(URL の `projects/<id>`) → `AUTH_PROJECT_ID`

### frontend 用の環境変数

```sh
AUTH_ISSUER="http://localhost:8081"
AUTH_CLIENT_ID="<Client ID>"
AUTH_REDIRECT_URI="http://localhost:8080/auth/callback"
AUTH_POST_LOGOUT_REDIRECT_URI="http://localhost:8080/"
AUTH_AUDIENCE="<API mindstock-backend の Resource ID>"
AUTH_PROJECT_ID="<Project mindstock の ID>"
```

`AUTH_CLIENT_ID` / `AUTH_AUDIENCE` / `AUTH_PROJECT_ID` が未設定だと `./gradlew :frontend:generateAuthConfig` がビルド失敗する(意図的)。`mise.toml` の `[env]` セクションや `.envrc` で管理するのが便利。

### 起動(backend + frontend)

`AUTH_*` 環境変数(上の backend 用 + frontend 用)は `mise.toml` の `[env]` か `.envrc` でまとめて入れておくと両ターミナルで共有できる。

```sh
docker compose up -d                                 # postgres + zitadel
PORT=8090 ./gradlew :backend:api:run                 # ターミナル A(:8090、frontend proxy 先)
./gradlew :frontend:wasmJsBrowserDevelopmentRun      # ターミナル B(http://localhost:8080)
```

ブラウザで http://localhost:8080 を開く。

### live 疎通で確認できること(P6-0 時点)

P6-0 は frontend の **土台**(認証 / RPC / セッション / テーマ / 外枠)までで、オンボーディング(表示名登録)と在庫一覧の実描画は後続フェーズ(P6-3 / P6-1)。よって live 疎通の確認ポイントは:

1. `http://localhost:8080` を開くと Zitadel の authorize へ redirect される(PKCE)。
2. Zitadel でログイン → `http://localhost:8080/auth/callback` に戻り、token 交換が成功する。
3. frontend が WebSocket で `/api/v1/resident`(`me()`)を叩く(backend ログに認証済み WS ハンドシェイクが出る)。
4. 画面が **`オンボーディング(P6-3)` のプレースホルダ**(= 未登録ユーザの `NeedOnboarding` 分岐)を表示する。

ここまで出れば「PKCE ログイン → token → 認証付き RPC → boot 分岐 → テーマ/shell 描画」の土台が live で疎通している。**登録済みユーザの `AppShell`(`在庫一覧(配線確認用プレースホルダ)`)を見るには Resident 登録が要るが、登録 UI は P6-3**。確認したい場合は backend に表示名登録の経路(`ResidentRegisterRpcService.registerDisplayName`)を一時的に叩くか、`residents` 系テーブルに該当ユーザ(JWT の provider+subject)の行を投入する。

> 注: `:frontend:wasmJsBrowserDevelopmentRun` は dev server(:8080)。本番配布ビルド(`wasmJsBrowserDistribution`)はローカルで OOM することがある。
