# catalog(カタログ)機能仕様書

## 機能概要

catalog は、世帯が在庫管理したい商品を追加するときの入力補助として、JAN(13 桁バーコード)と商品名の対応辞書を提供する機能。提供する操作は 2 つだけで、いずれも参照系である。

1. **名前でカタログ商品を検索する** — 入力文字列に部分一致するカタログ商品を最大 N 件返す
2. **JAN でカタログ商品を照会する** — 1 件のカタログ商品を返す。自前 DB に無ければ外部商品 API へフォールバックし、取得できたものは自前 DB にキャッシュする

カタログは世帯に紐づかないグローバルな辞書として実装されており、`catalog_items` テーブルに世帯 ID 列が無い。カタログ商品を世帯の商品として登録する「採用」自体は inventory コンテキスト(`ProductRegisterRpcService.adopt`)の責務で、catalog は採用時に商品情報を解決する役割だけを担う。

カタログへの書き込み経路は「外部商品 API の結果をキャッシュする」1 本のみで、利用者がカタログを直接編集する手段は提供されていない。

## ユースケース

### UC-CAT-1: 名前でカタログ商品を検索する

**アクター**: 登録済みの利用者(Resident)

**基本フロー**:
1. 利用者が商品追加画面の検索欄に文字列を入力する。
2. フロントエンドが入力を trim し、空でなければ `CatalogRpcService.search(name, limit)` を呼ぶ(`AddProductViewModel.search`。上限は固定で `SearchLimit(20)`)。
3. バックエンドが登録済み判定を行い(`requireRegistered`)、`CatalogService.search` を経由して `catalog_items` の `name` に対する部分一致検索を実行する。
4. 一致したカタログ商品を最大 `limit` 件、`CatalogItems` として返す。
5. 画面が結果一覧を表示し、利用者が 1 件選ぶと採用フォーム(`AddProductUiState.AdoptForm`)へ遷移する。

**代替・例外フロー**:
- 入力が trim 後に空 → フロントエンドが RPC を呼ばずに一覧を空にリセットする。
- 該当なし → 空の `CatalogItems` が返る(エラーにはならない)。画面は「カスタム追加」導線を提示する。
- 検索語が trim 後 61 文字以上 → `CatalogItemName` の生成時点で `IllegalArgumentException` → `BadRequest`。
- 未登録の利用者 → `Unauthorized`。フロントエンドは再認証フローへ遷移する。

### UC-CAT-2: JAN でカタログ商品を照会する

**アクター**: 登録済みの利用者(Resident)

**基本フロー**:
1. 利用者が検索欄に 13 桁の数字を入力する。フロントエンドは入力が `Jan` として解釈でき、かつ trim 後の入力と数字列が一致するときに JAN 照会ボタンを表示する(`AddProductScreen`)。
2. 利用者が JAN 照会ボタンを押すと `CatalogRpcService.lookupByJan(jan)` が呼ばれる。
3. バックエンドが登録済み判定を行い、`CatalogService.lookupByJan` が自前カタログを JAN で照合する。
4. ヒット → そのカタログ商品を返す。外部商品 API は呼ばれない。
5. 画面が採用フォーム(`AdoptForm`)へ遷移し、商品名は編集不可で表示される。

**代替・例外フロー**:
- 自前カタログに不在 → `ExternalProductRepository.findByJan` で外部商品 API を照会する。
  - 外部でヒット → 取得したカタログ商品を `catalog_items` に保存(キャッシュ)してから返す。
  - 外部でも不在(不在 / レート制限 / 障害 / パース失敗のいずれも同じ扱い) → `ResourceNotFoundException` が素通しで伝播し `NotFound` になる。
- `NotFound` を受けたフロントエンドは、その JAN を持つ手入力フォーム(`AddProductUiState.CustomForm`、名前は編集可)へ遷移する。以降はカスタム商品追加(inventory の `addCustom`)へ繋がる。
- JAN が 13 桁でない / 数字でない / チェックディジット不正 → `Jan` 生成時に `IllegalArgumentException` → `BadRequest`。フロントエンドは `runCatching { Jan(digits) }` で事前に弾くため、通常の画面操作では到達しない。
- **現状の制約**: `ExternalProductRepository` の実装は `UnconfiguredProductReceive` のみで常に不在を返すため、外部ヒットのフローは実際には発生しない。

### UC-CAT-3: カタログ商品を世帯の商品として採用する(関連コンテキスト)

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が UC-CAT-1 / UC-CAT-2 で選んだカタログ商品に対し、単位と最低在庫を指定して確定する。
2. `ProductRegisterRpcService.adopt(householdId, catalogItemId, unit, minimumStock)` が呼ばれる。
3. `AdoptProductScenario` が `CatalogService.findById(catalogItemId)` でカタログ商品を解決する。
4. `ProductRegisterService.adopt` が世帯メンバー判定と JAN 重複判定を行い、`Product.adopt` で商品を生成する。
5. `product_catalog_links` に採用元のカタログ商品 ID が記録される。

**代替・例外フロー**:
- カタログ商品 ID が存在しない → `ResourceNotFoundException` → `NotFound`。
- 操作者が世帯メンバーでない → `MembershipRequiredException` → `Unauthorized`。
- 同一 JAN の商品が既に世帯にある → `DuplicateJanException` → `Conflict`。

このユースケースの詳細(単位・最低在庫・商品状態など)は inventory の仕様書を参照。

## RPC インターフェース

### CatalogRpcService

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/catalog/CatalogRpcService.kt`
実装: `CatalogController`(`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt`)

| メソッド名 | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `search` | `name: CatalogItemName`, `limit: SearchLimit` | `RpcResult<CatalogItems, RpcError>` | `Unauthorized`(未登録 Resident)/ `BadRequest`(`CatalogItemName` が trim 後 1〜60 文字でない、`SearchLimit` が 1〜100 でない)/ `Internal`(その他) |
| `lookupByJan` | `jan: Jan` | `RpcResult<CatalogItem, RpcError>` | `Unauthorized`(未登録 Resident)/ `NotFound`(自前カタログにも外部にも不在)/ `BadRequest`(`Jan` の桁数・数字・チェックディジット不正)/ `Internal`(その他) |

いずれも `requireRegistered(session)` で保護され、未登録 Resident は `Unauthorized` になる。世帯メンバーであることは要求されない。全 RPC service は単一エンドポイント `/api/rpc`(WebSocket)に相乗りする。

例外から `RpcError` への翻訳は `configuration/guard/SessionGuard.kt` の `runGuarded` が一括で行うため、Controller 自身は例外を扱わない。

### 関連する RPC(catalog 型を引数に取るもの)

| service | メソッド | catalog との関わり |
|---|---|---|
| `ProductRegisterRpcService` | `adopt(householdId, catalogItemId, unit, minimumStock)` | `CatalogItemId` を受け取り、`AdoptProductScenario` 経由で `CatalogService.findById` を呼ぶ。詳細は inventory の仕様書を参照 |

## データモデル

### 集約: CatalogItem

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItem.kt`

```kotlin
data class CatalogItem(
    val id: CatalogItemId,
    val jan: Jan,
    val name: CatalogItemName,
)
```

- 状態を変える操作メソッドを持たない(生成後に名前や JAN を変える手段が無い)。
- 世帯・単位・最低在庫・在庫数といった情報は一切持たない。
- `createdAt` は集約に持たず、永続化層のメタとして `catalog_items.created_at` にのみ存在する。

**不変条件**:
- `jan` は必須(nullable でない)。カタログ商品は必ず JAN を持つ。
- `name` は trim 済み 1〜60 文字(`CatalogItemName` が保証)。
- `jan` はカタログ全体で一意(DB の UNIQUE 制約が保証。ドメイン側の検査は無い)。

### 値オブジェクト

| VO | 実装 | 制約 |
|---|---|---|
| `CatalogItemId` | `domain/.../catalog/item/CatalogItemId.kt` | `Uuid` を保持。`create()` が UUIDv7 を採番。値域検証なし |
| `CatalogItemName` | `domain/.../catalog/content/CatalogItemName.kt` | `invoke(raw)` で `raw.trim()` してから検証。trim 後 1 文字以上 60 文字以下(`MAX_LENGTH = 60`)。空白のみは `IllegalArgumentException` |
| `SearchLimit` | `domain/.../catalog/SearchLimit.kt` | `Int`。1 以上 100 以下(`MAX = 100`)。範囲外は `IllegalArgumentException` |
| `Jan` | `domain/.../barcode/Jan.kt`(barcode コンテキスト) | `String`。ちょうど 13 桁(`LENGTH = 13`)かつ全て数字、かつ EAN-13 チェックディジットが一致。違反は `IllegalArgumentException` |

### ファーストクラスコレクション

| 型 | 実装 | 備考 |
|---|---|---|
| `CatalogItems` | `domain/.../catalog/item/CatalogItems.kt` | `list: List<CatalogItem>` と `size()` のみ。該当なしは `CatalogItems(emptyList())` で表し、例外にも null にもしない |

### DB テーブル

#### catalog_items

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/CatalogItemsTable.kt`
DDL: `backend/core/src/main/resources/db/migration/V1__init.sql:11-12`

| 列 | 型 | 制約 |
|---|---|---|
| `id` | `uuid` | PRIMARY KEY |
| `jan` | `varchar(13)` | NOT NULL、UNIQUE(`catalog_items_jan_unique`) |
| `name` | `varchar(60)` | NOT NULL |
| `created_at` | `timestamp` | NOT NULL、既定値 `CURRENT_TIMESTAMP` |

世帯 ID 列は存在しない(グローバル辞書)。`name` にインデックスは張られていない。

#### product_catalog_links

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/ProductCatalogLinksTable.kt`
DDL: `backend/core/src/main/resources/db/migration/V1__init.sql:18-19`

| 列 | 型 | 制約 |
|---|---|---|
| `product_id` | `uuid` | PRIMARY KEY、`products.id` への FK(ON DELETE RESTRICT) |
| `catalog_item_id` | `uuid` | NOT NULL、`catalog_items.id` への FK(ON DELETE RESTRICT)、非ユニークインデックスあり |

商品 1 件につき採用元カタログ商品は最大 1 件(`product_id` が主キー)。この対応関係はドメインモデルに現れず、「マスタ由来 / 世帯独自」の区別を導出するためだけに存在する。書き込みは inventory 側(`ProductRegisterDataSource`)が行う。

### 層ごとの構成

| 層 | 型 | 実装 |
|---|---|---|
| presentation | `CatalogController` | `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt` |
| application(service) | `CatalogService` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogService.kt` |
| application(scenario) | `AdoptProductScenario` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/product/AdoptProductScenario.kt`(product 側に配置) |
| application(repository) | `CatalogRepository` / `CatalogRegisterRepository` / `ExternalProductRepository` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/` |
| infrastructure(DB) | `CatalogDataSource` / `CatalogRegisterDataSource` / `CatalogItemHydration` | `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/catalog/` |
| infrastructure(外部受信) | `UnconfiguredProductReceive` | `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/receive/catalog/UnconfiguredProductReceive.kt` |
| frontend(data) | `CatalogRepository` | `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/data/CatalogRepository.kt` |
| frontend(画面) | `AddProductViewModel` / `AddProductScreen` ほか | `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/` |

`CatalogService` は 3 つのメソッドを持つ。`search` と `findById` は Repository への素通し、`lookupByJan` のみ「自前不在 → 外部照会 → キャッシュ保存」のフォールバックを行う。`findById` は RPC には露出せず、`AdoptProductScenario` からの内部利用専用。

`backend/schedules` に catalog 関連のバッチ処理は存在しない。

## エラー・例外

| 状況 | 例外 | RpcError | 発生箇所 |
|---|---|---|---|
| カタログ商品名が trim 後 1〜60 文字でない | `IllegalArgumentException` | `BadRequest` | `CatalogItemName.init`(`requireTrimmedWithin`) |
| 検索上限が 1〜100 の範囲外 | `IllegalArgumentException` | `BadRequest` | `SearchLimit.init` |
| JAN が 13 桁の数字でない / チェックディジット不正 | `IllegalArgumentException` | `BadRequest` | `Jan.init` |
| 指定 JAN のカタログ商品が自前 DB に無い | `ResourceNotFoundException` | (`lookupByJan` 内で catch され外部照会へ) | `CatalogDataSource.findByJan` |
| 自前 DB にも外部にも無い | `ResourceNotFoundException` | `NotFound` | `UnconfiguredProductReceive.findByJan` ほか外部実装 |
| 指定 ID のカタログ商品が無い | `ResourceNotFoundException` | `NotFound` | `CatalogDataSource.findById`(採用フローで顕在化) |
| 未登録 Resident が RPC を呼ぶ | — | `Unauthorized` | `requireRegistered`(`SessionGuard.kt`) |
| 同一 JAN の商品を同一世帯で二重採用 | `DuplicateJanException` | `Conflict` | `ProductRegisterService.adopt`(inventory 側) |
| 上記以外 | 任意の `Throwable` | `Internal`(構造化ログに記録) | `runGuarded` |

エラーは domain / infrastructure で例外として投げられ、presentation 境界の `runGuarded` が一括で `RpcError` に翻訳する。`CatalogService` は例外を素通しし、唯一の例外が `lookupByJan` の `ResourceNotFoundException` catch(不在を外部照会への切り替え合図として使う)。この扱いは `.claude/rules/error-handling.md` に許容例外として明記されている。

フロントエンド側では `RpcOutcome.Failure` を `FailureHandler` が処理し、`Unauthorized` は再認証、それ以外はトースト表示になる。ただし `lookupByJan` の `NotFound` だけは例外的に手入力フォームへの遷移として扱われる(`AddProductViewModel.lookupByJan`)。

## 制約事項・要確認

### 実装済みだが未完の箇所

- **外部商品 API 連携は未実装**。`ExternalProductRepository` の実装は `UnconfiguredProductReceive` のみで、常に `ResourceNotFoundException` を返す。プロバイダ(楽天 / Yahoo 等を想定)が決まり次第 `<Provider>ProductReceive` を追加実装する設計になっている(`docs/2026-06-13-release-audit.md` にも「JAN コードから Yahoo/楽天 API 経由での商品検索は未実装」として記録されている)。
- **初期カタログデータの投入手段が無い**。`catalog_items` を埋める経路は外部 API キャッシュのみで、それが動かない現状では自前カタログは空のまま。テンプレ商品の投入は別 PR 扱いとして繰り越されている(`docs/2026-06-13-release-audit.md`)。結果として、現時点で `search` は常に空、`lookupByJan` は常に `NotFound` になる。
- **バーコードスキャン UI が未実装**。JAN 照会は検索欄への 13 桁手入力のみで起動する。カメラ / スキャナ連携は省略されている。

### 要確認項目

- (要確認: 検索結果の並び順)`CatalogDataSource.search` は `ORDER BY` を指定せず `LIMIT` のみを付けるため、一致件数が上限を超えたときにどの行が返るかが不定。意図した仕様か、指定漏れかがコードから読み取れない。
- (要確認: キャッシュ保存時の JAN 重複)`CatalogRegisterDataSource.register` は `insert` のみで、UPSERT や事前の存在確認をしない。同一 JAN が並行して外部照会されると `catalog_items.jan` の UNIQUE 制約違反になり、`runGuarded` で `Internal` に落ちる。この競合が許容されているのか、対処が必要なのかは不明。
- (要確認: キャッシュの訂正手段)外部 API から取得した名前が誤っていた場合に、カタログ商品を修正・削除する手段が存在しない。カタログの更新経路を意図的に持たない設計なのか、単に未実装なのかがコードから読み取れない。
- (要確認: カタログの検索性能)`catalog_items.name` にインデックスが無く、検索は `%語%` の LIKE 部分一致。想定するカタログ件数と許容レイテンシがドキュメントにもコードにも記載されていない。
- (要確認: 権限設計)カタログの検索・照会は「登録済み Resident」であれば世帯に属していなくても実行できる。カタログがグローバル辞書である以上の意図(例えば未所属ユーザに検索させる要件)があるのかは不明。
- (要確認: 外部由来 ID の採番)キャッシュ保存時、`CatalogItemId` は外部実装が返した値をそのまま使う(`CatalogService.lookupByJan` が `received` をそのまま `register` に渡す)。外部実装側で `CatalogItemId.create()` を呼ぶ規約なのか、外部システムの ID を写すのかが定義されていない。

### テストで確認されている仕様

- `CatalogItemNameTest`(`domain/src/commonTest/.../catalog/content/CatalogItemNameTest.kt`): 前後の空白をトリムして受理する / 空白のみは拒否 / 61 文字は拒否。
- `SearchLimitTest`(`domain/src/commonTest/.../catalog/SearchLimitTest.kt`): 1 と 100 は許容 / 0 以下は IAE / 101 以上は IAE。
- `CatalogServiceTest`(`backend/core/src/test/.../application/service/catalog/CatalogServiceTest.kt`): 自前ヒット時は外部 API を呼ばない / 自前不在なら外部取得してキャッシュ保存して返す / 双方不在なら `ResourceNotFoundException` を素通しし保存もしない。
- `CatalogControllerTest`(`backend/api/src/test/.../presentation/rpc/catalog/CatalogControllerTest.kt`): `search` が `CatalogService.search` の結果を `Ok` で包む。
- `CatalogDataSource` / `CatalogRegisterDataSource` の DB 統合テストは存在しない(LIKE エスケープや UNIQUE 制約の挙動は未検証)。
