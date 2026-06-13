# mindstock
Household consumables inventory manager — keep your home's stock out of your head.

## ローカル開発(認証込み)

backend は OIDC access_token (JWT) を Zitadel で検証する。**Zitadel のセットアップ(Project / API / PKCE アプリ)は `docker compose up` 時に自動で行われる**(画面操作不要)。

`compose.yml` のサービス:

| サービス | 役割 |
|---|---|
| `postgres`(:5432) | アプリ DB(`mindstock`)+ テスト DB(`mindstock_test`)+ Zitadel DB。`docker/postgres-init.sh` が test/zitadel DB を作成 |
| `zitadel`(:8081) | OIDC IdP。Login UI は v1 を使用(v4 既定の v2 は別コンテナ要のため無効化)。初回 init で IAM 管理用サービスアカウントの PAT を `docker/machinekey/pat.txt` に発行 |
| `zitadel-init` | 上記 PAT で Management API を叩き、Project `mindstock` / API `mindstock-backend`(JWT)/ PKCE アプリ `mindstock-frontend`(**Dev Mode + Auth Token Type=JWT** + redirect URI)を**冪等に**作成し、生成された `AUTH_*` を repo ルートの **`.env.zitadel`** に書き出す |
| `garage`(:3900) | S3 互換オブジェクトストレージ(商品画像の保管先) |
| `garage-init` | garage の layout / bucket `mindstock-images` / 固定 dev アクセスキーを冪等にセットアップ(資格情報は `application.yaml` の `external.storage` デフォルトと一致) |

### 1. 起動 + 自動セットアップ(推奨: mise)

```sh
mise run up
```

`mise run up` は (1) `docker compose up -d --wait postgres zitadel garage` で依存を起動し、(2) `zitadel-init` / `garage-init` を foreground で完走させる。完走すると repo ルートに `.env.zitadel`(`AUTH_*`)が生成される。進捗は `docker compose logs -f zitadel-init`。

> `mise` を使わない場合は `docker compose up -d --wait postgres zitadel garage` の後に `docker compose run --rm zitadel-init` と `docker compose run --rm garage-init` を順に実行する(単に `docker compose up -d` するだけでは init の完走を待たない)。

生成される `.env.zitadel` の中身:

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

`mise` 利用なら `mise.toml` の `_.file = ".env.zitadel"` で自動読み込みされる。使わない場合は各ターミナルで:

```sh
set -a; . ./.env.zitadel; set +a
```

backend / frontend の両方が `AUTH_*` を要求する(未設定だと `:frontend:generateAuthConfig` がビルド失敗=意図的。backend も未設定だと JWT 検証に失敗する)。DB / Storage は `application.yaml` の既定が compose と一致するため通常未設定で可(変えたい場合のみ `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD`)。

### 3. backend + frontend 起動

```sh
mise run backend     # ターミナル A(:8090。Flyway migration が走る)
mise run frontend    # ターミナル B(http://localhost:8080。--continuous 付き)
```

`mise` を使わない場合(`./gradlew` 直叩き):

```sh
./gradlew :backend:api:run                                    # :8090(application.yaml の PORT デフォルトが 8090)
./gradlew :frontend:wasmJsBrowserDevelopmentRun --continuous  # http://localhost:8080(/api を :8090 へプロキシ)
```

frontend dev server(:8080)は `/api` を backend(:8090)へプロキシする(`frontend/webpack.config.d/proxy.js`)。ブラウザで http://localhost:8080 を開く → Zitadel ログイン(**`admin@localhost` / `Password1!`**)。

### 環境変数リファレンス

ローカル開発では基本的に `mise run up` が生成・注入するため手動設定は不要。全体像は以下。詳細インベントリは `docs/superpowers/plans/2026-06-12-env-inventory.md`。

| 変数 | 用途 | 既定 / 供給元 | 手動設定 |
|---|---|---|---|
| `AUTH_ISSUER` / `AUTH_JWKS_URL` / `AUTH_AUDIENCE` | backend の JWT 検証 | `.env.zitadel`(`mise run up` が生成) | 不要(未設定だと JWT 検証に失敗) |
| `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` / `AUTH_REDIRECT_URI` | frontend の PKCE ログイン(ビルド時定数) | `.env.zitadel` | 不要 |
| `PORT` | backend の待受ポート | 既定 `8090`(`application.yaml`)。`mise` も 8090 を注入 | 不要 |
| `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` | backend の DB 接続 | `application.yaml` 既定が compose と一致 | 任意上書きのみ |
| `STORAGE_ENDPOINT` / `STORAGE_REGION` / `STORAGE_BUCKET` / `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` / `STORAGE_CORS_ORIGINS` | 商品画像ストレージ(garage) | `application.yaml` 既定が garage-init の固定 dev キーと一致 | 本番のみ上書き |
| `TEST_DB_URL` / `TEST_DB_USER` / `TEST_DB_PASSWORD` | 統合テストの DB | `mise.toml` / CI が供給(`mindstock_test`) | 不要 |
| `ZITADEL_MASTERKEY` | Zitadel の masterkey | 既定 `MasterkeyNeedsToHave32Characters`(`compose.yml`) | 本番のみ上書き |

> `STORAGE_CORS_ORIGINS` は `application.yaml` の `external.storage.cors-allowed-origins`(`List<String>`)の **第 1 要素を上書きする env**(`- "$STORAGE_CORS_ORIGINS:http://localhost:8080"`)。**env 値はカンマ区切りで複数オリジンには展開されない**(分割実装は無く 1 要素の文字列になる)。複数オリジンを許可したい場合は `application.yaml` の `cors-allowed-origins` リストに `-` で要素を追記する。

### 再セットアップ / 注意

- Zitadel の設定を作り直したいときは `docker compose down -v && mise run up`(DB ボリュームが消え、`.env.zitadel` も再生成される。生成された ID が変わるので env を読み直す)。
- `.env.zitadel` と `docker/machinekey/` は生成物(gitignore 済み)。手で編集しない。
- 自動化前に手動でハマりやすかった 2 点(http redirect 用の **Dev Mode**、opaque ではなく **JWT アクセストークン**)は `zitadel-init` が自動設定するので、コンソールでの手作業は不要。

### トラブルシューティング

- **`external.auth.* (env AUTH_*) が未設定です` で backend 起動が失敗する**: `AUTH_ISSUER` / `AUTH_AUDIENCE` / `AUTH_JWKS_URL` が未注入(`AuthSettings` が起動時に fail-fast する)。`.env.zitadel` を `mise run up` で生成し、env を読み込む(`mise` 利用なら自動。直叩きなら `set -a; . ./.env.zitadel; set +a`)→ backend を再起動する。`docker compose down -v && mise run up` で作り直した場合は生成 ID が変わるため env を読み直す。
- **frontend ビルドが `:frontend:generateAuthConfig` で失敗する**: 同じく `AUTH_*`(frontend は `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` / `AUTH_REDIRECT_URI`)が未設定。上記と同様に `.env.zitadel` を生成・読込してから再ビルドする。

### live 疎通で確認できること(P6-0 時点)

P6-0 は frontend の **土台**(認証 / RPC / セッション / テーマ / 外枠)までで、オンボーディング(表示名登録)と在庫一覧の実描画は後続フェーズ(P6-3 / P6-1)。よって live 疎通の確認ポイントは:

1. `http://localhost:8080` を開くと Zitadel の authorize へ redirect される(PKCE)。
2. Zitadel でログイン → `http://localhost:8080/auth/callback` に戻り、token 交換が成功する。
3. frontend が WebSocket で `/api/v1/resident`(`me()`)を叩く(backend ログに認証済み WS ハンドシェイクが出る)。
4. 画面が **`オンボーディング(P6-3)` のプレースホルダ**(= 未登録ユーザの `NeedOnboarding` 分岐)を表示する。

ここまで出れば「PKCE ログイン → token → 認証付き RPC → boot 分岐 → テーマ/shell 描画」の土台が live で疎通している。**登録済みユーザの `AppShell`(`在庫一覧(配線確認用プレースホルダ)`)を見るには Resident 登録が要るが、登録 UI は P6-3**。確認したい場合は backend に表示名登録の経路(`ResidentRegisterRpcService.registerDisplayName`)を一時的に叩くか、`residents` 系テーブルに該当ユーザ(JWT の provider+subject)の行を投入する。

> 注: `:frontend:wasmJsBrowserDevelopmentRun` は dev server(:8080)。本番配布ビルド(`wasmJsBrowserDistribution`)はローカルで OOM することがある。
