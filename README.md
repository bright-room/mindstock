# mindstock
Household consumables inventory manager — keep your home's stock out of your head.

## ローカル開発(認証込み)

backend は OIDC access_token (JWT) を Zitadel で検証する。**Zitadel のセットアップ(Project / API / PKCE アプリ)は `docker compose up` 時に自動で行われる**(画面操作不要)。

`compose.yml` の 3 サービス:

| サービス | 役割 |
|---|---|
| `postgres`(:5432) | アプリ DB + Zitadel DB |
| `zitadel`(:8081) | OIDC IdP。Login UI は v1 を使用(v4 既定の v2 は別コンテナ要のため無効化)。初回 init で IAM 管理用サービスアカウントの PAT を `docker/machinekey/pat.txt` に発行 |
| `zitadel-init` | 上記 PAT で Management API を叩き、Project `mindstock` / API `mindstock-backend`(JWT)/ PKCE アプリ `mindstock-frontend`(**Dev Mode + Auth Token Type=JWT** + redirect URI)を**冪等に**作成し、生成された `AUTH_*` を repo ルートの **`.env.zitadel`** に書き出す |

### 1. 起動 + 自動セットアップ

```sh
docker compose up -d
```

`zitadel-init` が完走すると `.env.zitadel` が生成される(数十秒)。進捗は `docker compose logs -f zitadel-init`。中身:

```sh
AUTH_ISSUER=http://localhost:8081
AUTH_JWKS_URL=http://localhost:8081/oauth/v2/keys
AUTH_PROJECT_ID=<自動採番>
AUTH_AUDIENCE=<自動採番: API mindstock-backend の clientId>
AUTH_CLIENT_ID=<自動採番: PKCE アプリの clientId>
AUTH_REDIRECT_URI=http://localhost:8080/auth/callback
AUTH_POST_LOGOUT_REDIRECT_URI=http://localhost:8080/
```

### 2. 環境変数の読み込み

backend / frontend の両方が `AUTH_*` を要求する(未設定だと `:frontend:generateAuthConfig` がビルド失敗=意図的)。`.env.zitadel` を読み込む:

```sh
set -a; . ./.env.zitadel; set +a      # 各ターミナルで
```

`mise` 利用なら `mise.toml` に `[env]` → `_.file = ".env.zitadel"`、direnv なら `.envrc` に `dotenv ./.env.zitadel` でも可。

> DB は `application.yaml` の既定(`jdbc:postgresql://localhost:5432/mindstock`, `mindstock`/`mindstock`)が compose の `postgres` と一致するため通常未設定で可。変えたい場合のみ `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD`。

### 3. backend + frontend 起動

backend は既定 :8080 だが、frontend dev server も :8080 を使い `/api` を **:8090** にプロキシする(`frontend/webpack.config.d/proxy.js`)。そのため backend は **`PORT=8090`** で起動する。

```sh
PORT=8090 ./gradlew :backend:api:run                 # ターミナル A(:8090、frontend proxy 先。Flyway migration が走る)
./gradlew :frontend:wasmJsBrowserDevelopmentRun      # ターミナル B(http://localhost:8080)
```

ブラウザで http://localhost:8080 を開く → Zitadel ログイン(**`admin@localhost` / `Password1!`**)。

### 再セットアップ / 注意

- Zitadel の設定を作り直したいときは `docker compose down -v && docker compose up -d`(DB ボリュームが消え、`.env.zitadel` も再生成される。生成された ID が変わるので env を読み直す)。
- `.env.zitadel` と `docker/machinekey/` は生成物(gitignore 済み)。手で編集しない。
- 自動化前に手動でハマりやすかった 2 点(http redirect 用の **Dev Mode**、opaque ではなく **JWT アクセストークン**)は `zitadel-init` が自動設定するので、コンソールでの手作業は不要。

### live 疎通で確認できること(P6-0 時点)

P6-0 は frontend の **土台**(認証 / RPC / セッション / テーマ / 外枠)までで、オンボーディング(表示名登録)と在庫一覧の実描画は後続フェーズ(P6-3 / P6-1)。よって live 疎通の確認ポイントは:

1. `http://localhost:8080` を開くと Zitadel の authorize へ redirect される(PKCE)。
2. Zitadel でログイン → `http://localhost:8080/auth/callback` に戻り、token 交換が成功する。
3. frontend が WebSocket で `/api/v1/resident`(`me()`)を叩く(backend ログに認証済み WS ハンドシェイクが出る)。
4. 画面が **`オンボーディング(P6-3)` のプレースホルダ**(= 未登録ユーザの `NeedOnboarding` 分岐)を表示する。

ここまで出れば「PKCE ログイン → token → 認証付き RPC → boot 分岐 → テーマ/shell 描画」の土台が live で疎通している。**登録済みユーザの `AppShell`(`在庫一覧(配線確認用プレースホルダ)`)を見るには Resident 登録が要るが、登録 UI は P6-3**。確認したい場合は backend に表示名登録の経路(`ResidentRegisterRpcService.registerDisplayName`)を一時的に叩くか、`residents` 系テーブルに該当ユーザ(JWT の provider+subject)の行を投入する。

> 注: `:frontend:wasmJsBrowserDevelopmentRun` は dev server(:8080)。本番配布ビルド(`wasmJsBrowserDistribution`)はローカルで OOM することがある。
