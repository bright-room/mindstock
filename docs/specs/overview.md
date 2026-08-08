# mindstock 全体仕様書(overview)

家庭の在庫管理 SaaS。世帯(household)単位で家庭内の消耗品・食料品などの在庫を管理し、補充・消費の記録、買い物リストの自動生成、バーコードスキャンによる商品登録を提供する。

本書はシステム全体の構成と機能マップを示す。ドメイン別の詳細は各仕様書を参照:

| ドメイン | 仕様書 | 業務整理(koto 向け) |
|---|---|---|
| 居住者 | [resident.md](resident.md) | [../knowledge/resident.md](../knowledge/resident.md) |
| 世帯 | [household.md](household.md) | [../knowledge/household.md](../knowledge/household.md) |
| カタログ | [catalog.md](catalog.md) | [../knowledge/catalog.md](../knowledge/catalog.md) |
| 在庫 | [inventory.md](inventory.md) | [../knowledge/inventory.md](../knowledge/inventory.md) |
| バーコード | [barcode.md](barcode.md) | [../knowledge/barcode.md](../knowledge/barcode.md) |
| セッション・認証 | [session.md](session.md) | [../knowledge/session.md](../knowledge/session.md) |

## 技術スタック

- Kotlin Multiplatform(JVM バックエンド + Kotlin/Wasm フロントエンド)
- Ktor(HTTP サーバ)/ kotlinx-rpc(フロント・バック間の型付き RPC)
- Exposed + PostgreSQL(永続化)
- Compose Multiplatform(UI)
- Zitadel OIDC(認証)
- S3 互換ストレージ(Garage。商品画像の保存)

バージョンは `gradle/libs.versions.toml` を参照。

## モジュール構成

```
:domain             純粋なドメインモデル(集約・VO・例外)。KMP common
:rpc                RPC interface 定義(@Rpc service、RpcError、RpcResult)。KMP common
:shared             frontend / backend 共通の薄いロジック(KrpcJson 等)。KMP common + wasmJs
:backend:core       application 層 interface(Repository / Service / Scenario)
                    + infrastructure 実装(DataSource[DB] / Transfer[送信] / Receive[受信])。JVM
:backend:api        Ktor 起動モジュール(configuration/、presentation/rpc/)。JVM
:backend:schedules  スケジュール処理。JVM(現状 placeholder エントリポイントのみ。バッチ未実装)
:frontend           Compose Multiplatform / Kotlin/Wasm の UI
```

## アーキテクチャ(層構造と依存方向)

```
presentation (rpc Controller, RpcError, MindstockSession)
      │
      ▼
application (Scenario, Service, Repository interface)
      ▲
      │
infrastructure (Repository 実装 = DataSource[DB] / Transfer[送信] / Receive[受信])

domain(model, value object, exception)← 全層が依存可能
configuration(Ktor plugin / DI / routing)← 片方向の glue
```

- Scenario = 複数 Service を跨ぐユースケース単位のオーケストレーション。Service = 単一集約の薄いオーケストレーション。ビジネスロジックは domain に置く(リッチドメイン)
- 公開 API の nullable 戻り値は原則禁止。「不在」は `ResourceNotFoundException` 等の例外で表現する
- DataSource は各メソッドで自前にトランザクション境界を張る。外部システム連携は通信方向で Transfer(送信)/ Receive(受信)に分類

## RPC サービスマップ

フロントエンドとバックエンドは kotlinx-rpc の型付きインターフェースで通信する。全 9 サービス・34 メソッド(`rpc/src/commonMain` 定義)。

| ドメイン | サービス | メソッド |
|---|---|---|
| resident | `ResidentRegisterRpcService` | register / rename |
| household | `HouseholdRpcService` | list / previewInvite |
| household | `HouseholdRegisterRpcService` | create / rename / leave / changeRole / removeMember / createInvite / revokeInvite / join |
| catalog | `CatalogRpcService` | search / lookupByJan |
| inventory(商品) | `ProductRpcService` | list / listArchived / shoppingList / imageUrl |
| inventory(商品) | `ProductRegisterRpcService` | adopt / addCustom / uploadImage / changeUnit / removeImage / changeMinimum / archive / unarchive / setWanted |
| inventory(在庫) | `StockRpcService` | history / activity |
| inventory(在庫) | `StockRegisterRpcService` | replenish / consume / correct |
| session | `SessionRpcService` | whoami |

戻り値はすべて `RpcResult<T, RpcError>`。エラー表現の詳細は各ドメイン仕様書を参照。

## フロントエンド機能マップ

`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/` 配下:

- `app/` — shell(アプリ枠)・welcome・settings
- `auth/`, `core/auth`, `core/session` — 認証・セッション管理
- `feature/inventory` — 在庫一覧・補充・消費
- `feature/shopping` — 買い物リスト
- `feature/catalog` — カタログ検索・商品登録
- `feature/household` — 世帯管理・招待
- `feature/resident` — 居住者プロフィール
- `feature/activity` — 活動フィード
- `feature/notification` — 通知
- `feature/onboarding` — 初回セットアップ
- `designsystem/` — デザインシステム(atom・theme)

## 開発コマンド

- Backend 起動: `./gradlew :backend:api:run`
- Frontend dev server: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
- 単体テスト: `./gradlew test`
- 統合テスト: `./gradlew :backend:core:integrationTest`(要 `mise run up` / `STORAGE_*` 環境変数)
