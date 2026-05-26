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
5. API `mindstock-backend` を「JWT」で作成し、Audience を `mindstock-backend` に設定

### backend 用の環境変数 (デフォルト値で動く)

```sh
AUTH_ISSUER="http://localhost:8081"
AUTH_AUDIENCE="mindstock-backend"
AUTH_JWKS_URL="http://localhost:8081/oauth/v2/keys"
```

これらは `application.yaml` のデフォルトで埋まっているため、ローカル開発では設定不要。
