# mindstock

家庭の在庫管理 SaaS。Kotlin Multiplatform プロジェクトとして、JVM バックエンドと Compose Multiplatform(Kotlin/Wasm)フロントエンドを `:shared` / `:rpc` / `:domain` の共有モジュール経由で繋ぐ。

## 技術スタック

- Kotlin Multiplatform
- Ktor
- kotlinx-rpc
- Exposed
- Compose Multiplatform(Kotlin/Wasm)
- PostgreSQL
- Zitadel OIDC

(バージョンは `gradle/libs.versions.toml` を参照。本ファイルには記載しない)

## モジュール構成

- `:domain` — 純粋なドメインモデル(集約・VO・例外)。KMP common。外部依存は kotlin stdlib / kotlinx-serialization / kotlinx-datetime + `:shared`(共通の日時/シリアライズ ext。`:shared` は `:domain` に依存しないので循環しない)
- `:rpc` — RPC interface 定義(`@Rpc` service interface、`RpcError`、`RpcResult`)。KMP common
- `:shared` — frontend と backend 双方で使う薄い共通ロジック(`KrpcJson` / `CustomJson` 等)。KMP common + wasmJs
- `:backend:core` — application 層 interface(Repository / Service interface)+ infrastructure DataSource 実装(Exposed)。JVM
- `:backend:api` — Ktor 起動モジュール(`configuration/`, `presentation/rpc/`)。JVM
- `:backend:schedules` — スケジュール処理(バッチ・cron 等)。JVM
- `:frontend` — Compose Multiplatform / Kotlin/Wasm の UI

## 主要コマンド

- Backend 起動: `./gradlew :backend:api:run`
- Frontend dev server: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
- 単体テスト: `./gradlew test`
- 統合テスト: `./gradlew integrationTest`
- フル build: `./gradlew build`

## 絶対に守る原則

詳細は `.claude/rules/*.md` に置かれ、編集対象に応じて自動ロードされる。ここでは絶対に外せない 4 原則だけ:

1. **層責務と依存方向**: presentation → application ← infrastructure / domain は横断 / 逆方向の依存禁止
2. **nullable 戻り値原則禁止**: 公開 API の `T?` は原則禁止。検索で単一値が見つからない等の「不在」は例外で表現する。導入が必要なら事前にユーザ承認を得る
3. **リッチドメイン**: ビジネスロジックは domain に。Service は薄い orchestration。集約は object graph(子を ID で持たない)
4. **`@Rpc` annotation 必須**: RPC service interface に `@kotlinx.rpc.annotations.Rpc` を必ず付ける。`RemoteService` 継承は使わない
