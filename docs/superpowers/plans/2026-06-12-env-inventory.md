# 環境変数インベントリ(2026-06-12 監査時点のスナップショット)

> リファクタリングマスタープラン(2026-06-12-refactoring-master-plan.md)の付録。D-8(README 環境変数リファレンス新設)の元ネタであり、フェーズ 0-1 / 0-12 / 2-8 / D-12 の実施で「問題」列の内容は解消される。各フェーズの spec 作成時は本表を正とせず、必ず現物を再確認すること。

## アプリケーション変数(開発者が意識するもの)

| 変数名 | 読む側 | 書く側 | デフォルト | 問題 / 対応タスク |
|--------|--------|--------|-----------|------|
| `KTOR_ENV` | application.yaml:2 | なし | `LOCAL` | **死変数**(Environment enum は参照ゼロ)→ 0-1 で削除 |
| `PORT` | application.yaml:4 | mise.toml:6 = `8090` | `8080` | デフォルトが frontend dev server(8080)と衝突 → D-12 で 8090 に変更 |
| `AUTH_ISSUER` | application.yaml:15、frontend/build.gradle.kts:54 | zitadel-init.sh:90 → `.env.zitadel` → mise(`_.file`) | backend: なし / frontend: `http://localhost:8081` | backend はデフォルトなし。未設定で深く死ぬ → 2-8 で fail-fast |
| `AUTH_AUDIENCE` | application.yaml:16、frontend/build.gradle.kts:58、ci.yml:73 | 同上 | なし(CI: `ci-placeholder`) | backend 同上 → 2-8。frontend 側 `AuthConfig.AUDIENCE` は**参照ゼロの死定数** → 0-12 で生成削除(`.env.zitadel` の出力は backend 用に維持) |
| `AUTH_JWKS_URL` | application.yaml:17 | 同上 | なし | デフォルトなし → 2-8 で fail-fast |
| `AUTH_PROJECT_ID` | frontend/build.gradle.kts:59、ci.yml:74 | 同上 | なし(CI: `ci-placeholder`) | frontend のみ。WebAuthDeps.kt:68 で使用。問題なし |
| `AUTH_CLIENT_ID` | frontend/build.gradle.kts:57、ci.yml:72 | 同上 | なし(CI: `ci-placeholder`) | frontend のみ。App.kt:108 / WebAuthDeps.kt:72 で使用。問題なし |
| `AUTH_REDIRECT_URI` | frontend/build.gradle.kts:55 | 同上 | `http://localhost:8080/auth/callback` | 問題なし |
| `AUTH_POST_LOGOUT_REDIRECT_URI` | frontend/build.gradle.kts:56 | 同上 | `http://localhost:8080/` | **参照ゼロの死定数**(`endSessionUrl()` 未呼出)→ 0-12 で生成削除、ログアウト実装時に再追加 |
| `DB_JDBC_URL` | application.yaml:21 | なし(任意上書き) | `jdbc:postgresql://localhost:5432/mindstock` | compose と一致。問題なし |
| `DB_USERNAME` / `DB_PASSWORD` | application.yaml:22-23 | なし(任意上書き) | `mindstock` / `mindstock` | 問題なし |
| `TEST_DB_URL` | backend/api/build.gradle.kts:81(タスクへ転送) | mise.toml:8、ci.yml:115 | `jdbc:postgresql://localhost:5432/mindstock_test` | mise.toml:7 のコメントが実態と乖離(postgres-test/5433 は存在しない)→ D-7。api 側は `@Tags("integration")` テスト 0 件で転送は現状 dead flow → 5-5 で convention 化+コメント明示 |
| `TEST_DB_USER` / `TEST_DB_PASSWORD` | 同上 | mise.toml:9-10、ci.yml:116-117 | `mindstock` / `mindstock` | 同上 |
| `STORAGE_ENDPOINT` | application.yaml:25、ProductImageTransferTest.kt:23 | なし(任意上書き) | `http://localhost:3900` | 問題なし |
| `STORAGE_REGION` | application.yaml:26 | なし | `garage` | テストは読まずハードコード(`region = "garage"`)→ 5-6 で参照に統一 |
| `STORAGE_BUCKET` | application.yaml:27、テスト | なし | `mindstock-images` | 問題なし |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | application.yaml:30-31、テスト | なし | 固定 dev キー(`GKdeadbeef...`) | 意図的設計(本番は env 上書き)。問題なし |
| `STORAGE_CORS_ORIGINS` | application.yaml:32 | なし | `http://localhost:8080` | カンマ区切り String → D 系(フェーズ 5-8 で HOCON List 化) |

## コンテナ内部変数(外部から設定不要)

| グループ | 書く側 | 備考 |
|---------|--------|------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_INITDB_ARGS` / `TZ` | compose.yml:6-10 | postgres-init.sh が `mindstock_test` DB も作成 |
| `ZITADEL_DATABASE_POSTGRES_*`(8)/ `ZITADEL_EXTERNAL*`(3)/ `ZITADEL_FIRSTINSTANCE_*`(6)/ `ZITADEL_DEFAULTINSTANCE_FEATURES_LOGINV2_REQUIRED` | compose.yml:30-55 | masterkey のみ D-6 で `${ZITADEL_MASTERKEY:-...}` に env 化予定 |

## フロー概要

- **AUTH_\***: `mise run up` → `docker/zitadel-init.sh` が `.env.zitadel` を生成 → mise の `_.file` で読み込み → backend(application.yaml)と frontend(`generateAuthConfig` タスクでビルド時定数化)が共有
- **DB_\* / STORAGE_\***: application.yaml のデフォルトが compose の dev 構成と一致しており、ローカルでは設定不要
- **TEST_DB_\***: mise / CI が設定し、Gradle の `integrationTest` タスクがテスト JVM へ転送
