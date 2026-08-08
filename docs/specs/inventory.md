# inventory(在庫)機能仕様書

## 機能概要

inventory は mindstock の中核機能で、世帯が「何をどれだけ持っているか」を管理する。提供する機能は次の 5 群である。

1. **商品の登録と管理** — カタログからの採用、カタログに無い品目のカスタム追加、単位・最低在庫・画像の変更、アーカイブと復元
2. **在庫の記録** — 補充・消費の記録と、過去の記録の訂正
3. **在庫の参照** — 世帯の在庫一覧、アーカイブ済商品一覧、商品単位の変動履歴、世帯全体の活動履歴
4. **買い物リスト** — 在庫状態と手動希望から合成される買い足し候補の一覧と、手動希望の設定・解除
5. **消費予測** — 消費履歴から推定した「あと約何日で在庫が尽きるか」の表示

在庫数量はフィールドとして保持されず、追記された在庫変動(補充・消費・訂正)を畳み込んで導出する。訂正は記録の削除ではなく訂正変動の追記として表現するため、履歴は失われない。商品の設定変更も同様に、変更後の全状態を新しいリビジョンとして追記する。

買い物リストと消費予測はどちらも永続化されない読みモデルで、参照のたびに再計算される。消費予測は共有モジュール `:domain` に実装されており、専用の RPC を持たずクライアント側で評価される。

在庫関連のバッチ処理(期限通知等)は実装されていない。`:backend:schedules` はプレースホルダのエントリポイントのみを持つ。

## ユースケース

### UC-INV-1: カタログから商品を採用する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が商品追加画面でカタログ商品を検索または JAN 照会し、1 件を選ぶ(catalog コンテキストの機能)。
2. 利用者が単位と最低在庫を入力して確定する。
3. `ProductRegisterRpcService.adopt(householdId, catalogItemId, unit, minimumStock)` が呼ばれる。
4. `AdoptProductScenario` が `CatalogService.findById` でカタログ商品を解決する。
5. `ProductRegisterService.adopt` が世帯メンバー判定を行い、同一 JAN の商品が世帯内に無いことを確認する。
6. `Product.adopt` が商品を生成する。名前とJAN はカタログ商品からコピーされ、状態は `採用中`、画像は未設定。
7. `products` / `product_revisions`(初回)/ `product_barcodes` / `product_catalog_links` が同一トランザクションで INSERT される。
8. 生成された商品が返り、画面が在庫一覧を再取得する。

**代替・例外フロー**:
- カタログ商品 ID が存在しない → `ResourceNotFoundException` → `NotFound`。
- 操作者が世帯メンバーでない → `MembershipRequiredException` → `Unauthorized`。
- 同一 JAN の商品が世帯内に既にある(アーカイブ済も含む) → `DuplicateJanException` → `Conflict`。
- 単位が空白のみ / 11 文字以上、最低在庫が負 → `IllegalArgumentException` → `BadRequest`。
- 未登録の利用者 → `Unauthorized`。

### UC-INV-2: カタログに無い商品を追加する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が商品追加画面で名前・単位・最低在庫を入力する。JAN 照会が NotFound だった場合はその JAN が引き継がれる。
2. `ProductRegisterRpcService.addCustom(householdId, request)` が呼ばれる(`request` は名前・単位・バーコード・最低在庫を束ねた `AddCustomProductRequest`)。
3. `ProductRegisterService.addCustom` が世帯メンバー判定を行う。
4. バーコードが JAN 有り(`Barcode.Linked`)の場合のみ、同一 JAN の重複を判定する。
5. `Product.custom` が商品を生成する。状態は `採用中`、画像は未設定。
6. `products` / `product_revisions`(初回)が INSERT される。JAN 有りなら `product_barcodes` も INSERT される。カタログへのリンクは作られない。

**代替・例外フロー**:
- 名前が空白のみ / 61 文字以上 → `BadRequest`。
- JAN が 13 桁でない / チェックディジット不正 → `BadRequest`(barcode コンテキストの検証)。
- 同一 JAN 重複 → `Conflict`。
- JAN 無しの場合は重複判定が行われないため、同名の商品を何個でも追加できる。
- 非メンバー → `Unauthorized`。

### UC-INV-3: 商品の設定を変更する(単位・最低在庫)

**アクター**: 世帯主(マスタ管理権限を持つメンバー)

**基本フロー**:
1. 利用者が商品設定シートで単位または最低在庫を変更し、保存する。
2. 変更のあった項目ごとに `ProductRegisterRpcService.changeUnit(productId, unit)` / `changeMinimum(productId, minimumStock)` が呼ばれる。
3. `ProductRegisterService` が商品の属する世帯を解決し、`requireCanManageMaster` で権限を検査する。
4. 現在の商品を読み出し、`Product.changeUnit` / `changeMinimum` で新しい状態を作る。
5. `product_revisions` に変更後の全状態を 1 行 INSERT する。

**代替・例外フロー**:
- 商品が存在しない → `NotFound`。
- 操作者が世帯メンバーでない → `MembershipRequiredException` → `Unauthorized`。
- メンバーだがマスタ管理権限が無い(役割が `メンバー` または `閲覧者`) → `OwnerRequiredException` → `Unauthorized`。
- 値域外の単位 / 最低在庫 → `BadRequest`。
- 商品名の変更は RPC 自体が存在しないため実行できない。

### UC-INV-4: 商品画像をアップロードする / 取り外す

**アクター**: 世帯主(マスタ管理権限を持つメンバー)

**基本フロー(アップロード)**:
1. 利用者が画像を選ぶ。クライアントは原画像を base64 に変換して送る(クライアント側でのサイズ・形式検証は無い)。
2. `ProductRegisterRpcService.uploadImage(productId, UploadImageRequest(base64))` が呼ばれる。
3. Controller が base64 文字列の長さを検査し、上限(デコード後 8 MiB 相当)を超えていれば即座に `BadRequest` を返す。
4. Controller が base64 をデコードし、デコード後サイズが 8 MiB を超えていれば `BadRequest` を返す。
5. `ProductRegisterService.uploadImage` がマスタ管理権限を検査する。
6. `ProductImageTransfer.store` が画像を処理して保存する。処理は最大辺 512 ピクセルへの縮小 → 品質 0.85 の JPEG 再エンコード → sha256 算出。処理は CPU バウンドのため `Dispatchers.Default` で実行される。
7. 保存キー(sha256 の 16 進表現)で S3 互換ストレージ(Garage)に `image/jpeg` として PUT する。
8. 商品の画像を `ProductImage.Stored(ref)` に変えたリビジョンを追記する。

**基本フロー(取り外し)**:
1. `ProductRegisterRpcService.removeImage(productId)` が呼ばれる。
2. マスタ管理権限を検査し、商品の画像を `ProductImage.None` に変えたリビジョンを追記する。

**代替・例外フロー**:
- base64 が不正 → `BadRequest`(field = "base64")。
- デコード後が空のバイト列 → `RawImageUpload` の検証で `IllegalArgumentException` → `BadRequest`。
- 画像としてデコードできない形式 → `ImageProcessor` が `IllegalArgumentException("not a decodable image")` → `BadRequest`。
- 権限不足 → `Unauthorized`。
- ストレージ障害 → 翻訳対象外の例外として `Internal`。
- **制約**: 取り外しでもリビジョン差し替えでも、ストレージ上の実体オブジェクトは削除されない。

### UC-INV-5: 商品画像を表示する

**アクター**: 登録済みの利用者

**基本フロー**:
1. 画面が商品の画像を表示する際に `ProductRpcService.imageUrl(productId)` を呼ぶ。
2. `ProductService.imageUrl` が商品を読み出し、画像が `Stored` なら `ProductImageTransfer.presignedUrl` で有効期限 1 時間の署名付き GET URL を発行する。
3. クライアントは取得した URL で画像を読み込む(`ProductImageLoader` が世代付きキャッシュを持ち、画像の更新時に無効化する)。

**代替・例外フロー**:
- 商品が存在しない → `NotFound`。
- 画像が未設定(`ProductImage.None`) → `ResourceNotFoundException("product has no image: ...")` → `NotFound`。
- **制約**: この操作は世帯メンバー判定を行わない。登録済み利用者であれば、他世帯の商品 ID でも URL を取得できる。

### UC-INV-6: 在庫を補充する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が在庫詳細または買い物リストから補充シートを開き、数量・メモ・発生日を指定する。
2. `StockRegisterRpcService.replenish(productId, quantity, note, occurredAt)` が呼ばれる。
3. `StockRegisterService.replenish` が商品の属する世帯を解決し、世帯メンバー判定を行う。
4. セッションの居住者 ID から実行者(`Resident`)を解決する。
5. 在庫(商品 + 変動履歴)を読み出し、`Stock.replenish` を呼ぶ。ここで商品がアーカイブ済でないことが検査される。
6. 追記された補充変動 1 件を `stock_movements` に INSERT する。
7. 続けてその商品の手動希望を偽に設定する(`product_wanted_events` に追記)。
8. 画面が在庫一覧を再取得し、他タブへ更新を波及させる。

**代替・例外フロー**:
- 商品がアーカイブ済 → `ArchivedProductMovementException` → `Conflict`。
- 非メンバー → `Unauthorized`。
- 商品が存在しない → `NotFound`。
- 数量が 0 以下 → `Quantity` の検証で `BadRequest`。
- メモが 256 文字以上 → `BadRequest`。
- 発生日は過去日を指定できる(バックデート)。画面側は未来日を選択できないよう制限しているが、サーバ側に未来日の制約は無い。買い物リストからの補充では日付選択を出さず現在時刻を使う。

### UC-INV-7: 在庫を消費する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が在庫詳細から消費シートを開き、数量・メモ・発生日を指定する。
2. `StockRegisterRpcService.consume(productId, quantity, note, occurredAt)` が呼ばれる。
3. 世帯メンバー判定・実行者解決の後、`Stock.consume` を呼ぶ。アーカイブ済でないこと、現在数量が指定数量以上であることが検査される。
4. 消費変動 1 件を `stock_movements` に INSERT する。手動希望は解除されない。

**代替・例外フロー**:
- 現在数量を超える消費 → `InsufficientStockException` → `Conflict`。画面は消費後の数量が負になる場合に警告表示を出すが、送信自体はブロックしない。
- 商品がアーカイブ済 → `Conflict`。
- 非メンバー → `Unauthorized`。
- 数量が 0 以下 / メモ超過 → `BadRequest`。

### UC-INV-8: 在庫記録を訂正する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が変動履歴から 1 件を選び、正しい数量と訂正理由を入力する。
2. `StockRegisterRpcService.correct(target, correctedQuantity, reason)` が呼ばれる(商品 ID は渡さない)。
3. `StockRegisterService.correct` が変動 ID から在庫を丸ごと読み出す。
4. 在庫の商品が属する世帯について、世帯メンバー判定を行う。
5. `Stock.correct` が、対象が永続化済みの補充または消費であることを確認し、訂正変動を追記する。
6. 訂正後の正味数量が負にならないことを検査する。
7. 訂正変動 1 件を `stock_movements` に INSERT する(`target_movement_id` と `reason` が入る)。

**代替・例外フロー**:
- 対象の変動が存在しない → `ResourceNotFoundException("movement not found: ...")` → `NotFound`。
- 対象が訂正変動である → `hasBaseMovement` が偽になり同じく `NotFound`。
- 訂正の結果、正味数量が負になる → `InsufficientStockException` → `Conflict`。補充を下方訂正して負になる場合も同様。
- 理由が空白のみ / 256 文字以上 → `BadRequest`。
- 非メンバー → `Unauthorized`。
- アーカイブ済の商品でも訂正は実行できる(意図的にガードしていない)。
- **制約**: 訂正の発生時刻はサーバ側の現在時刻で生成される。訂正のバックデートはできない。

### UC-INV-9: 在庫一覧を見る

**アクター**: 世帯のメンバー

**基本フロー**:
1. 画面が `ProductRpcService.list(householdId)` を呼ぶ。
2. `ProductService.list` が世帯メンバー判定を行い、`StockRepository.listByHousehold` を呼ぶ。
3. 採用中の商品を最新リビジョン込みで取得し、それらの変動を `product_id IN (...)` の 1 クエリで一括取得して商品ごとにまとめる。実行者(`Resident`)も一括解決する。
4. 商品と変動履歴を組にした `Stocks` が返る。数量・在庫状態・消費予測はクライアント側でこの集合から算出する。

**代替・例外フロー**:
- 非メンバー → `Unauthorized`。
- 該当なし → 空の `Stocks`(エラーにはならない)。
- 変動の実行者の表示名が解決できない → `ResourceNotFoundException` → `NotFound`。
- **制約**: ページネーションは無く常に全件を返す。名前による絞り込みと表示形式(リスト/グリッド)の切替はクライアント側で行う。

### UC-INV-10: 商品の変動履歴を見る

**アクター**: 世帯のメンバー

**基本フロー**:
1. 画面が `StockRpcService.history(productId)` を呼ぶ。
2. `StockService.history` が商品の属する世帯を解決し、世帯メンバー判定を行う。
3. その商品の変動を追記順(ID 昇順)で全件返す。

**代替・例外フロー**:
- 商品が存在しない → `NotFound`。
- 非メンバー → `Unauthorized`。
- 変動が 1 件も無い → 空の `StockMovements`。

### UC-INV-11: 世帯の活動履歴を見る

**アクター**: 世帯のメンバー

**基本フロー**:
1. 画面が `StockRpcService.activity(householdId)` を呼ぶ。
2. `StockService.activity` が世帯メンバー判定を行い、世帯の在庫一覧を返す。
3. `StockController` が在庫を平坦化し、変動 1 件ごとに商品を添えた `ActivityEntry` を作る。
4. 発生時刻の降順に並べ替えた `ActivityFeed` を返す。
5. クライアントが日付ごとにグルーピングして表示する。

**代替・例外フロー**:
- 非メンバー → `Unauthorized`。
- 該当なし → 空の `ActivityFeed`。
- **制約**: アーカイブ済商品の変動は含まれない(在庫一覧が採用中の商品のみを返すため)。ページネーションは無い。

### UC-INV-12: 買い物リストを見る

**アクター**: 世帯のメンバー

**基本フロー**:
1. 画面が `ProductRpcService.shoppingList(householdId)` を呼ぶ。
2. `ProductService.shoppingList` が世帯メンバー判定を行う。
3. 世帯の在庫一覧と、現在手動希望が立っている商品の一覧をそれぞれ取得する。
4. `ShoppingList.from(stocks, wantedProductIds)` が、在庫ごとに手動希望フラグを付けたエントリの一覧を合成する。
5. 各エントリの要否(在庫不足 / 手動希望 / 不要)は在庫状態と手動希望から判定される。
6. クライアントが自動分(在庫不足)と手動分(手動希望)に分けて表示する。

**代替・例外フロー**:
- 非メンバー → `Unauthorized`。
- 該当なし → 空の `ShoppingList`。
- **制約**: 返る一覧には要否が `不要` のエントリも含まれる。絞り込みは呼び出し側の責務。

### UC-INV-13: 手動希望を設定・解除する

**アクター**: 世帯のメンバー

**基本フロー**:
1. 利用者が買い物リストまたは商品詳細で希望の印を切り替える。
2. `ProductRegisterRpcService.setWanted(productId, wanted)` が呼ばれる。世帯 ID は渡さない(商品 ID がグローバルに一意なため)。
3. `ProductRegisterService.setWanted` が商品の属する世帯を解決し、世帯メンバー判定を行う。
4. `product_wanted_events` に真偽値を 1 行追記する。最新行の値が現在状態になる。

**代替・例外フロー**:
- 商品が存在しない → `NotFound`。
- 非メンバー → `Unauthorized`。
- 補充を記録すると、この操作を経ずに希望が偽へ解除される。

### UC-INV-14: 商品をアーカイブする / 復元する

**アクター**: 世帯主(マスタ管理権限を持つメンバー)

**基本フロー(アーカイブ)**:
1. 利用者が商品設定シートでアーカイブを実行する。画面は現在数量が 1 以上ならボタンを無効化している。
2. `ProductRegisterRpcService.archive(productId)` が呼ばれる。
3. `ProductRegisterService.archive` がマスタ管理権限を検査し、在庫を読み出して `Stock.archive` を呼ぶ。
4. 現在数量がちょうど 0 であることが検査され、状態を `アーカイブ済` にしたリビジョンが追記される。

**基本フロー(復元)**:
1. 利用者がアーカイブ済一覧(`ProductRpcService.listArchived`)から商品を選び復元する。
2. `ProductRegisterRpcService.unarchive(productId)` が呼ばれ、状態を `採用中` にしたリビジョンが追記される。数量に関する事前条件は無い。

**代替・例外フロー**:
- 現在数量が 0 でない → `CannotArchiveWithStockException` → `Conflict`。
- 現在数量が負(訂正の不整合等)の場合もアーカイブ不可。
- 権限不足 → `Unauthorized`。
- **制約**: アーカイブに確認ダイアログは無い。商品行そのものを削除する経路は存在しない。

### UC-INV-15: 消費予測を表示する

**アクター**: 画面(利用者の明示操作ではない)

**基本フロー**:
1. 在庫一覧・商品詳細・買い物リストの描画時に、クライアントが `Stock.forecast(EvaluatedTime.now())` を評価する。
2. 現在数量を 1 日あたりの消費ペースで割り、四捨五入した残日数を得る。
3. 残日数が得られた在庫について「あと約N日」を表示する。在庫ホームのバナーは残日数が最小の 1 件だけを出す。

**代替・例外フロー**:
- 現在数量が 0 以下、または消費実績が無い → `ConsumptionForecast.Unknown` となり表示しない。
- **制約**: サーバ側では算出も保存もされず、専用の RPC も無い。お知らせ機能は「残り 5 日以内」をクライアント固有の閾値として使い、最大 6 件まで表示する。

## RPC インターフェース

全メソッドの戻り値は `RpcResult<T, RpcError>` で、`T` は non-null。全メソッドが登録済み利用者を要求する(`requireRegistered`)ため、下表の「発生しうるエラー」には共通で `Unauthorized`(未登録 / トークン失効)が加わる。

### ProductRpcService(参照系)

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRpcService.kt`
実装: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `list` | `householdId: HouseholdId` | `Stocks` | `Unauthorized`(非メンバー)/ `NotFound`(世帯不在・実行者の表示名不在)/ `Internal` |
| `listArchived` | `householdId: HouseholdId` | `Products` | `Unauthorized`(非メンバー)/ `NotFound`(世帯不在)/ `Internal` |
| `shoppingList` | `householdId: HouseholdId` | `ShoppingList` | `Unauthorized`(非メンバー)/ `NotFound`(世帯不在)/ `Internal` |
| `imageUrl` | `productId: ProductId` | `ImageUrl` | `NotFound`(商品不在・画像未設定)/ `Internal`(ストレージ障害) |

### ProductRegisterRpcService(更新系)

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRegisterRpcService.kt`
実装: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductRegisterController.kt`

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `adopt` | `householdId: HouseholdId`, `catalogItemId: CatalogItemId`, `unit: ProductUnit`, `minimumStock: MinimumStock` | `Product` | `NotFound`(カタログ商品・世帯不在)/ `Unauthorized`(非メンバー)/ `Conflict`(JAN 重複)/ `BadRequest`(値域違反)/ `Internal` |
| `addCustom` | `householdId: HouseholdId`, `request: AddCustomProductRequest` | `Product` | `NotFound`(世帯不在)/ `Unauthorized`(非メンバー)/ `Conflict`(JAN 重複)/ `BadRequest`(名前・単位・JAN・最低在庫の値域違反)/ `Internal` |
| `uploadImage` | `productId: ProductId`, `request: UploadImageRequest` | `Unit` | `BadRequest`(base64 不正・8 MiB 超過・デコード不能・空バイト列)/ `NotFound`(商品不在)/ `Unauthorized`(非メンバー・マスタ管理権限不足)/ `Internal`(ストレージ障害) |
| `changeUnit` | `productId: ProductId`, `unit: ProductUnit` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー・権限不足)/ `BadRequest`(単位の値域違反)/ `Internal` |
| `removeImage` | `productId: ProductId` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー・権限不足)/ `Internal` |
| `changeMinimum` | `productId: ProductId`, `minimumStock: MinimumStock` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー・権限不足)/ `BadRequest`(負の最低在庫)/ `Internal` |
| `archive` | `productId: ProductId` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー・権限不足)/ `Conflict`(在庫が 0 でない)/ `Internal` |
| `unarchive` | `productId: ProductId` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー・権限不足)/ `Internal` |
| `setWanted` | `productId: ProductId`, `wanted: Wanted` | `Unit` | `NotFound`(商品不在)/ `Unauthorized`(非メンバー)/ `Internal` |

`AddCustomProductRequest`(`name: ProductName`, `unit: ProductUnit`, `barcode: Barcode`, `minimumStock: MinimumStock`)は複合パラメータを 1 つにまとめるための Request 型。`UploadImageRequest`(`base64: String`)は原画像を JSON 文字列で安全に運ぶための wire 型。

### StockRpcService(参照系)

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRpcService.kt`
実装: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt`

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `history` | `productId: ProductId` | `StockMovements` | `NotFound`(商品不在・実行者の表示名不在)/ `Unauthorized`(非メンバー)/ `Internal` |
| `activity` | `householdId: HouseholdId` | `ActivityFeed` | `NotFound`(世帯不在・実行者の表示名不在)/ `Unauthorized`(非メンバー)/ `Internal` |

### StockRegisterRpcService(更新系)

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt`
実装: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockRegisterController.kt`

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `replenish` | `productId: ProductId`, `quantity: Quantity`, `note: Note`, `occurredAt: OccurredAt` | `Unit` | `NotFound`(商品不在・実行者不在)/ `Unauthorized`(非メンバー)/ `Conflict`(アーカイブ済商品)/ `BadRequest`(数量 0 以下・メモ超過)/ `Internal` |
| `consume` | `productId: ProductId`, `quantity: Quantity`, `note: Note`, `occurredAt: OccurredAt` | `Unit` | `NotFound`(商品不在・実行者不在)/ `Unauthorized`(非メンバー)/ `Conflict`(アーカイブ済商品・在庫不足)/ `BadRequest`(数量 0 以下・メモ超過)/ `Internal` |
| `correct` | `target: MovementId`, `correctedQuantity: Quantity`, `reason: Reason` | `Unit` | `NotFound`(対象変動不在・対象が補充/消費でない)/ `Unauthorized`(非メンバー)/ `Conflict`(訂正後に正味数量が負)/ `BadRequest`(数量 0 以下・理由が空 / 超過)/ `Internal` |

## データモデル

### 集約

#### Product(商品)

`domain/.../inventory/product/Product.kt`

| フィールド | 型 | 説明 |
|---|---|---|
| `id` | `ProductId` | UUIDv7 |
| `name` | `ProductName` | trim 後 1〜60 文字。変更手段なし |
| `barcode` | `Barcode` | `Unlinked` / `Linked(jan)`(barcode コンテキスト) |
| `setting` | `StockingPolicy` | 単位と最低在庫 |
| `image` | `ProductImage` | `None` / `Stored(ref)` |
| `status` | `ProductStatus` | `採用中` / `アーカイブ済` |

操作: `archive()` / `unarchive()` / `changeUnit(unit)` / `changeMinimum(minimumStock)` / `changeImage(image)`。いずれも新しいインスタンスをコンストラクタで明示構築して返す。
生成: `Product.adopt(catalogItem, unit, minimumStock)`(名前と JAN をカタログからコピー、画像は `None`、状態は `採用中`)、`Product.custom(name, barcode, unit, minimumStock)`。

#### Stock(在庫)

`domain/.../inventory/stock/Stock.kt`

| フィールド | 型 | 説明 |
|---|---|---|
| `product` | `Product` | 商品そのものを保持(ID 参照ではない) |
| `movements` | `StockMovements` | 変動の全履歴 |

専用の識別子を持たない。導出値: `currentQuantity()` → `NetQuantity`、`status()` → `StockStatus`、`forecast(asOf)` → `ConsumptionForecast`、`latestMovement()`(変動が無ければ `ResourceNotFoundException`)。

不変条件:
- `replenish` / `consume`: 商品が `アーカイブ済` なら `ArchivedProductMovementException`
- `consume`: 現在数量 < 指定数量なら `InsufficientStockException`
- `correct`: 対象が永続化済みの補充または消費でなければ `ResourceNotFoundException`、訂正後の正味数量が負なら `InsufficientStockException`
- `archive`: 現在数量が 0 でなければ `CannotArchiveWithStockException`

### 値オブジェクト

| 型 | 内部表現 | 制約 |
|---|---|---|
| `ProductId` | `Uuid` | 制約なし。`create()` が UUIDv7 を採番 |
| `ProductName` | `String` | trim 後 1〜60 文字。生成時に自動 trim |
| `ProductUnit` | `String` | trim 後 1〜10 文字。生成時に自動 trim |
| `MinimumStock` | `Int` | 0 以上。`isBelow(current)` は `current <= value`、`shortage(current)` は `max(0, value - current)` |
| `ImageRef` | `String` | 空白のみ不可 |
| `ImageUrl` | `String` | 空白のみ不可 |
| `RawImageUpload` | `ByteArray` | 空バイト列不可。取り出し時に防御的コピー |
| `Quantity` | `Int` | 1 以上(0 と負は不可) |
| `NetQuantity` | `Int` | 制約なし(0 と負を許容) |
| `MovementId` | `Long` | 0 以上 |
| `Note` | `String` | trim 後 0〜255 文字(空文字を許容) |
| `Reason` | `String` | trim 後 1〜255 文字(空文字は不可) |
| `OccurredAt` | `LocalDateTime` | 制約なし。`now()` は JST 既定 |
| `EvaluatedTime` | `LocalDateTime` | 制約なし。`now()` は JST 既定 |
| `ConsumptionRate` | `Double` | 0 以上。0.0 は「予測不可」 |
| `Wanted` | `Boolean` | 制約なし。wire 上は素の Boolean |

### 区分(enum)と sealed 型

- `ProductStatus`: `採用中` / `アーカイブ済`
- `StockStatus`: `在庫切れ`(現在数量 ≤ 0)/ `残りわずか`(最低在庫以下)/ `十分`
- `Archivability`: `可能`(現在数量 == 0)/ `在庫あり`
- `ShoppingNeed`: `在庫不足`(リスト掲載)/ `手動希望`(リスト掲載)/ `不要`
- `ProductImage`: `None` / `Stored(ref: ImageRef)`
- `StockMovement`: `Replenishment` / `Consumption` / `Correction(+ target: MovementId, reason: Reason)`。共通フィールドは `identity` / `quantity` / `occurredAt` / `actor: Resident` / `note`
- `MovementIdentity`: `Pending`(未永続化)/ `Persisted(id: MovementId)`
- `ConsumptionForecast`: `Unknown` / `DaysRemaining(days: Int ≥ 0)`。永続化・通信しないため `@Serializable` を付けていない

### ファーストクラスコレクションと読みモデル

- `Products` / `Stocks`: `size()` のみを持つ単純な集合
- `StockMovements`: `size()` / `add(movement)` / `hasBaseMovement(id)` / `netQuantity()` / `consumptionRatePerDay(asOf)`。`FORECAST_WINDOW_DAYS = 60`
- `ShoppingList`: `size()` / `autoItems()` / `manualItems()` / `from(stocks, wantedProductIds)`
- `ShoppingEntry`: `stock` と `manuallyWanted` の組。`need()` / `onList()`
- `ActivityFeed` / `ActivityEntry`(`:rpc` に定義): 変動に商品を添えた世帯全体の履歴

### 正味数量の算出規則

`StockMovements.netQuantity()`:
1. 訂正のうち、対象ごとに発生時刻が最大のものを採る。
2. 各変動について、その変動 ID に対する最新の訂正があれば訂正の数量を、無ければ自身の数量を実効数量とする。
3. 補充は実効数量を加算、消費は実効数量を減算、訂正自体は 0 として合計する。

同一対象・同一発生時刻の訂正が複数ある場合はリスト出現順で最初の最大値を採る(実運用では同時刻の衝突は起きない前提)。

### 消費ペースの算出規則

`StockMovements.consumptionRatePerDay(asOf)`:
1. 変動が 1 件も無ければ 0.0。
2. 消費の実効数量の合計が 0 なら 0.0。
3. 観測開始日 = 補充または消費のうち最古の発生日。観測日数 = `max(1, 観測開始日 → asOf の日数)`。
4. 直近窓 = `asOf の日付 - 60 日` 以降。窓内の消費合計を求める。
5. 観測日数が 60 日以上 かつ 窓内の消費 > 0 なら `窓内消費 ÷ 60`、そうでなければ `全消費 ÷ 観測日数`。

### DB テーブル(Exposed 定義)

すべての外部キーは `onDelete = RESTRICT`。定義は `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/` 配下。

#### products

| 列 | 型 | 備考 |
|---|---|---|
| `id` | uuid | PK |
| `household_id` | uuid | FK → `households.id`。非一意インデックスあり |
| `name` | varchar(60) | 商品名。リビジョン管理の対象外(不変) |
| `created_at` | datetime | 既定値は現在時刻 |

#### product_revisions(追記のみ)

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / 自動採番。**最大値が最新リビジョン** |
| `product_id` | uuid | FK → `products.id`。`(product_id, id)` にインデックス |
| `unit` | varchar(10) | 単位 |
| `minimum_stock` | integer | 最低在庫 |
| `image_ref` | varchar(512) nullable | null = 画像未設定(`ProductImage.None`) |
| `status` | varchar(20) | enum 名(`採用中` / `アーカイブ済`) |
| `recorded_at` | datetime | 既定値は現在時刻 |

#### product_barcodes

| 列 | 型 | 備考 |
|---|---|---|
| `product_id` | uuid | PK / FK → `products.id` |
| `jan` | varchar(13) | 行の有無で `Barcode.Linked` / `Unlinked` を表す |

#### product_catalog_links

| 列 | 型 | 備考 |
|---|---|---|
| `product_id` | uuid | PK / FK → `products.id` |
| `catalog_item_id` | uuid | FK → `catalog_items.id`。非一意インデックスあり |

採用(`adopt`)でのみ作られ、カスタム追加では作られない。

#### product_wanted_events(追記のみ)

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / 自動採番。**最大値が現在値** |
| `product_id` | uuid | FK → `products.id`。`(product_id, id)` にインデックス |
| `wanted` | boolean | 手動希望の値 |
| `recorded_at` | datetime | 既定値は現在時刻 |

#### stock_movements(追記のみ)

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / 自動採番。`MovementId` に対応 |
| `product_id` | uuid | FK → `products.id`。`(product_id, id)` にインデックス |
| `kind` | varchar(20) | `REPLENISHMENT` / `CONSUMPTION` / `CORRECTION` |
| `quantity` | integer | 1 以上 |
| `occurred_at` | datetime | 発生時刻(既定値なし) |
| `actor_resident_id` | uuid | FK → `residents.id` |
| `note` | varchar(255) | メモ(空文字可) |
| `target_movement_id` | long nullable | 自己参照 FK。訂正のみ |
| `reason` | varchar(255) nullable | 訂正理由。訂正のみ |

在庫そのもののテーブルは存在しない。`kind` は永続化専用の判別子(`MovementKind`)で、ドメインの型ではない。訂正行で `target_movement_id` または `reason` が null の場合、読み出し時に `error(...)`(データ破損)として扱われる。

### 読み出しの組み立て

- 商品: `products` × 最新 `product_revisions`(`row_number()` で `product_id` ごとに `id` 降順の 1 件)を INNER JOIN し、`product_barcodes` を LEFT JOIN する。未マッチの JAN は `Barcode.Unlinked` に、null の `image_ref` は `ProductImage.None` に写す。
- 変動: `product_id` で絞り、`id` 昇順(追記順)で取得する。実行者は表示名の最新リビジョン込みで一括解決する。
- 在庫一覧: 商品一覧を取ったうえで、`product_id IN (...)` の 1 クエリで全変動を取得し、商品ごとにグルーピングする(N+1 を避ける)。
- 手動希望: `product_wanted_events` を `product_id` ごとに `id` 降順で `row_number()` し、最新が真の商品 ID 集合を得る。

トランザクション境界は各 DataSource メソッド内の `transaction(database) { }` で張る。

### 外部ストレージ

商品画像は S3 互換ストレージ(Garage)に保存する。実装は `ProductImageTransfer`(`infrastructure/transfer/product/`)で、インターフェースは application 層の `ProductImageStorageRepository`。

- 保存: 原画像 → 最大辺 512 px へ縮小 → 品質 0.85 の JPEG → sha256(16 進)を保存キーとして `image/jpeg` で PUT。処理は `Dispatchers.Default` で実行。
- 参照: 有効期限 1 時間の署名付き GET URL を発行。
- 削除: **実装されていない**。

## エラー・例外

ドメインと infrastructure は例外を投げるだけで、`RpcError` への翻訳は presentation 境界(`configuration/guard/SessionGuard.kt` の `runGuarded`)が行う。

| 例外 | 発生源 | RpcError | inventory での主な発生条件 |
|---|---|---|---|
| `IllegalArgumentException` | 値オブジェクトの `init { require(...) }` | `BadRequest` | 数量 0 以下、名前 / 単位 / メモ / 理由の値域違反、負の最低在庫、空の画像バイト列、デコードできない画像 |
| `ResourceNotFoundException` | infrastructure / ドメイン | `NotFound` | 商品不在、変動不在、訂正対象が補充・消費でない、画像未設定、変動が 1 件も無い状態での `latestMovement()`、実行者の表示名不在 |
| `MembershipRequiredException` | `Household.requireMember` | `Unauthorized` | 操作者が世帯メンバーでない |
| `OwnerRequiredException` | `Household.requireCapability` | `Unauthorized` | メンバーだがマスタ管理権限が無い |
| `DuplicateJanException` | `ProductRegisterService` | `Conflict` | 同一 JAN の商品が世帯内に既にある |
| `CannotArchiveWithStockException` | `Stock.archive` | `Conflict` | 現在数量が 0 でない状態でのアーカイブ |
| `InsufficientStockException` | `Stock.consume` / `correct` | `Conflict` | 現在数量を超える消費、訂正後に正味数量が負 |
| `ArchivedProductMovementException` | `Stock.replenish` / `consume` | `Conflict` | アーカイブ済商品への補充・消費 |
| その他の `Throwable` | 任意 | `Internal` | ストレージ障害、DB 障害など。構造化ログに記録し、詳細はクライアントに返さない |

これに加え、未登録利用者の呼び出しは `RpcError.Unauthorized("registration required")`、JWT 失効時は `RpcError.Unauthorized("token expired")` で短絡する。Controller 層で直接生成されるエラーは、画像アップロードの base64 検証(`BadRequest(field = "base64")`)のみ。

## 制約事項・要確認

### 認可に関するもの

1. **(要確認)`在庫編集` 権限が使われていない**: `HouseholdCapability.在庫編集` が定義され、役割 `閲覧者` はこれを持たないと `RolePermissions` に定められている。しかし inventory の書き込み処理(補充・消費・訂正・手動希望の設定・商品の採用とカスタム追加)はどれもこの権限を検査せず `requireMember` のみを使っている。結果として `閲覧者` の役割でもこれらの操作が可能。意図的か検査漏れかの確認が必要。
2. **(要確認)画像 URL 発行に世帯判定が無い**: `ProductService.imageUrl` は世帯メンバー判定を行わない。実装コメントは「商品 ID は世帯一意・表示はメンバー全員可・URL は短命」を理由に挙げているが、登録済みであれば他世帯の商品 ID からも画像 URL を取得できる状態。許容範囲かの確認が必要。

### 仕様の食い違い・未確定

3. **(要確認)訂正の説明と実装が食い違う**: `StockRegisterRpcService.correct` の説明は「対象 movement を打ち消す訂正 movement を追記」だが、実装(`StockMovements.netQuantity` / `effectiveQuantity`)は打ち消しではなく対象の数量を上書きする方式。どちらが意図した仕様か確認が必要。
4. **訂正の発生時刻がサーバ生成**: 補充・消費は利用者が発生時刻を指定できるが、訂正は `OccurredAt.now()` でサーバ側が生成する。`.claude/rules/backend-rpc-and-transactions.md` に「本来クライアント入力であるべきで、トラック B で見直す予定。それまではサーバ生成が現仕様」と記載がある。
5. **(要確認)リビジョン履歴の閲覧手段が無い**: `product_revisions` は追記で履歴を蓄えるが、読み出しは常に最新 1 件のみで、過去のリビジョンを見る API も画面も無い。履歴を残す目的(監査 / 将来機能)がコードからは読み取れない。

### 実装上の制約

6. **画像の実体が削除されない**: 画像の取り外しや差し替えを行っても、ストレージ上のオブジェクトは残る。保存キーが内容ハッシュのため、同一画像を複数商品で共有している可能性があり、削除処理を足す際は参照の有無を考慮する必要がある。
7. **ページネーションが無い**: 在庫一覧・アーカイブ済一覧・変動履歴・活動履歴・買い物リストのいずれも全件を返す。商品数や変動件数が増えたときの挙動は未検討。
8. **並び順の規則が API 間で異なる**: 商品単位の変動履歴(`history`)は追記順(ID 昇順)、世帯全体の活動履歴(`activity`)は発生時刻の降順。バックデート記録があると両者の順序は一致しない。
9. **活動履歴にアーカイブ済商品の変動が含まれない**: `activity` は採用中の商品の在庫一覧を基に組み立てるため、アーカイブした商品の過去の変動は履歴から消える。
10. **現在数量が負のときアーカイブできない**: `Archivability.of` はちょうど 0 のときだけ「可能」を返すため、訂正の不整合等で数量が負になった商品はアーカイブできない。
11. **在庫関連のバッチ処理が無い**: `:backend:schedules` はプレースホルダのみで、期限通知等のバッチは実装されていない。消費予測に基づくお知らせはクライアント側の描画時に評価される。
12. **消費予測の閾値がクライアント固有**: 「残り 5 日以内なら警告」「最大 6 件表示」はフロントエンドのお知らせ機能が持つ値で、ドメインの規則ではない。
13. **JAN 無しの商品に重複チェックが無い**: `Barcode.Unlinked` の商品は名前が同じでも何個でも追加できる。
14. **商品の削除経路が無い**: 商品行を削除する API は無く、DB の外部キーもすべて `RESTRICT`。不要になった商品はアーカイブで扱う。
15. **DataSource 層の統合テストが無い**: 現状 DB を伴う DataSource のテストはゼロ(`.claude/rules/testing.md` に「フェーズ 3-1 で追加予定」と記載)。ここに書いた読み出しの組み立て規則は実装コードのみを根拠としている。
