# mindstock
Household consumables inventory manager — keep your home's stock out of your head.

## Zitadel (ローカル認証 IdP)

backend は OIDC access_token (JWT) を Zitadel で検証する。ローカル開発では compose 上に Zitadel を起動する。

### 起動

```sh
docker compose up -d zitadel-db zitadel
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
```

`AUTH_AUDIENCE` は初回セットアップで Zitadel が割り当てる Resource ID(数値文字列)なので、必ず実際の値で上書きすること(`application.yaml` のデフォルト `mindstock-backend` のままだと、Zitadel access_token の `aud` claim と一致せず検証失敗となる)。

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

### frontend の起動

```sh
docker compose up -d
./gradlew :backend:application:api:run    # ターミナル A
./gradlew :frontend:wasmJsBrowserRun       # ターミナル B (http://localhost:8080)
```

ブラウザで http://localhost:8080 を開く → Zitadel ログイン → 初回は表示名入力ダイアログ → `Hello, <name>` が出れば成功。
