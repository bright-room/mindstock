# inventory(在庫)コンテキスト

## コンテキスト概要

inventory は mindstock の中核コンテキストで、「世帯が何をどれだけ持っているか」を管理する。世帯が管理対象として選んだ品目を **商品(Product)**、その商品の在庫状況を **在庫(Stock)** として表す。在庫数量は直接更新される値ではなく、**在庫変動(StockMovement)** の追記(append-only)を畳み込んで導出する。変動には補充・消費・訂正の 3 種類があり、記録の取り消し・削除は行わず、訂正変動を追記することで過去の記録を実効的に上書きする。

商品は catalog(商品マスタ)から「採用」して作るか、マスタに無い品目を世帯独自に追加して作る。どちらの経路でも商品は世帯に属し、名前と JAN は catalog から値としてコピーされるだけで参照は持たない。商品ごとに単位と最低在庫を設定でき、現在数量と最低在庫の関係から在庫状態(在庫切れ/残りわずか/十分)が決まる。この在庫状態と、利用者が明示的に立てる手動希望フラグを合成した読みモデルが **買い物リスト(ShoppingList)** で、永続化された実体は持たない。同様に **消費予測(ConsumptionForecast)** も消費履歴から都度算出する導出値で、専用の RPC もテーブルも存在しない。

隣接コンテキストとの関係: catalog(採用元の商品マスタ)・barcode(JAN の表現)・household(世帯と認可)・resident(変動の実行者)は関連コンテキスト参照。

## 用語集

### 商品(Product)

- **定義**: 世帯が在庫管理の対象として登録した品目。名前・バーコード・在庫方針(単位と最低在庫)・画像・状態を持つ集約ルート。数量そのものは持たず、数量は在庫(Stock)側が変動履歴から導出する。
- **別名**: `Product`、品目
- **関連用語**: [商品 ID](#商品-idproductid) / [商品名](#商品名productname) / [在庫方針](#在庫方針stockingpolicy) / [商品状態](#商品状態productstatus) / [商品画像](#商品画像productimage) / [在庫](#在庫stock) / `Barcode`(barcode コンテキスト参照)
- **実装**: `Product`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Product.kt`)

### 商品 ID(ProductId)

- **定義**: 商品を一意に識別する ID。UUIDv7 で採番する。世帯をまたいで一意なため、商品 ID が分かれば世帯 ID を指定しなくても対象を特定できる。
- **別名**: `ProductId`
- **関連用語**: [商品](#商品product)
- **実装**: `ProductId`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductId.kt`)

### 商品名(ProductName)

- **定義**: 商品の表示名。前後の空白を取り除いた上で 1〜60 文字であることが保証される。採用時は catalog の商品名がそのままコピーされる。一度登録した商品名を変更する手段は提供されていない。
- **別名**: `ProductName`
- **関連用語**: [商品](#商品product) / `CatalogItemName`(catalog コンテキスト参照)
- **実装**: `ProductName`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductName.kt`)

### 商品一覧(Products)

- **定義**: 商品の集合を表すファーストクラスコレクション。該当なしは空のコレクションで表す。
- **別名**: `Products`
- **関連用語**: [商品](#商品product) / [在庫一覧](#在庫一覧stocks)
- **実装**: `Products`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Products.kt`)

### 商品状態(ProductStatus)

- **定義**: 商品が現在も在庫管理の対象かどうかを表す区分。`採用中` と `アーカイブ済` の 2 値。アーカイブ済の商品は在庫一覧に現れず、補充・消費もできない。
- **別名**: `ProductStatus`、採用中、アーカイブ済
- **関連用語**: [商品](#商品product) / [アーカイブ可否](#アーカイブ可否archivability)
- **実装**: `ProductStatus`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/ProductStatus.kt`)

### 在庫方針(StockingPolicy)

- **定義**: 商品ごとの在庫管理の設定。数え方の単位と、補充が必要と判断する最低在庫の組。
- **別名**: `StockingPolicy`、商品設定
- **関連用語**: [単位](#単位productunit) / [最低在庫](#最低在庫minimumstock) / [商品](#商品product)
- **実装**: `StockingPolicy`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/StockingPolicy.kt`)

### 単位(ProductUnit)

- **定義**: 商品の数え方を表す文字列(個・本・袋 など)。前後の空白を取り除いた上で 1〜10 文字であることが保証される。値の集合は固定されておらず、任意の文字列を設定できる。
- **別名**: `ProductUnit`
- **関連用語**: [在庫方針](#在庫方針stockingpolicy) / [数量](#数量quantity)
- **実装**: `ProductUnit`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/ProductUnit.kt`)

### 最低在庫(MinimumStock)

- **定義**: 「これを下回ったら補充したい」と利用者が定める数量の下限。0 以上の整数。現在数量が最低在庫**以下**なら閾値を下回っていると判定する(等しい場合も下回り扱い)。不足数は最低在庫と現在数量の差で、負にはならず 0 に丸める。
- **別名**: `MinimumStock`、しきい値、閾値
- **関連用語**: [在庫方針](#在庫方針stockingpolicy) / [在庫状態](#在庫状態stockstatus) / [正味数量](#正味数量netquantity)
- **実装**: `MinimumStock`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/setting/MinimumStock.kt`)

### 商品画像(ProductImage)

- **定義**: 商品に紐づく画像の有無と保存先を表す型。未設定(`None`)か、ストレージに保存済み(`Stored` + 参照キー)のいずれか。null では表現しない。
- **別名**: `ProductImage`
- **関連用語**: [画像参照キー](#画像参照キーimageref) / [画像 URL](#画像-urlimageurl) / [原画像アップロード](#原画像アップロードrawimageupload)
- **実装**: `ProductImage`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ProductImage.kt`)

### 画像参照キー(ImageRef)

- **定義**: ストレージ上で画像を特定する保存キー。空白のみは許されない。実際の値は処理後 JPEG の sha256 ハッシュ(16 進文字列)。
- **別名**: `ImageRef`、保存キー
- **関連用語**: [商品画像](#商品画像productimage) / [画像 URL](#画像-urlimageurl)
- **実装**: `ImageRef`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageRef.kt`)

### 画像 URL(ImageUrl)

- **定義**: 商品画像を取得するための署名付き(presigned)GET URL。空白のみは許されない。有効期限は 1 時間。
- **別名**: `ImageUrl`、presigned URL
- **関連用語**: [画像参照キー](#画像参照キーimageref) / [商品画像](#商品画像productimage)
- **実装**: `ImageUrl`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/ImageUrl.kt`)

### 原画像アップロード(RawImageUpload)

- **定義**: 利用者が送信した加工前の画像バイト列。空のバイト列は許されない。可変参照が外部に漏れないよう、取り出し時は防御的コピーを返す。
- **別名**: `RawImageUpload`、原画像
- **関連用語**: [商品画像](#商品画像productimage) / [画像参照キー](#画像参照キーimageref)
- **実装**: `RawImageUpload`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/image/RawImageUpload.kt`)

### 在庫(Stock)

- **定義**: 1 つの商品と、その商品に対する在庫変動の全履歴を組にした集約。現在数量・在庫状態・消費予測はすべてこの集約が履歴から導出する。専用の識別子を持たず、商品によって同定される。
- **別名**: `Stock`
- **関連用語**: [商品](#商品product) / [在庫変動一覧](#在庫変動一覧stockmovements) / [正味数量](#正味数量netquantity) / [在庫状態](#在庫状態stockstatus)
- **実装**: `Stock`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stock.kt`)

### 在庫一覧(Stocks)

- **定義**: 在庫の集合を表すファーストクラスコレクション。世帯の在庫一覧画面や買い物リストの合成入力として使う。
- **別名**: `Stocks`
- **関連用語**: [在庫](#在庫stock) / [買い物リスト](#買い物リストshoppinglist)
- **実装**: `Stocks`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stocks.kt`)

### 在庫状態(StockStatus)

- **定義**: 現在数量と最低在庫の関係から決まる区分。現在数量が 0 以下なら `在庫切れ`、最低在庫以下なら `残りわずか`、それ以外は `十分`。
- **別名**: `StockStatus`、在庫切れ、残りわずか、十分
- **関連用語**: [最低在庫](#最低在庫minimumstock) / [正味数量](#正味数量netquantity) / [買い物要否](#買い物要否shoppingneed)
- **実装**: `StockStatus`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/StockStatus.kt`)

### アーカイブ可否(Archivability)

- **定義**: 現在数量からアーカイブしてよいかを判定する区分。現在数量がちょうど 0 のときだけ `可能`、それ以外は `在庫あり`(不可)。
- **別名**: `Archivability`
- **関連用語**: [商品状態](#商品状態productstatus) / [正味数量](#正味数量netquantity)
- **実装**: `Archivability`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Archivability.kt`)

### 在庫変動(StockMovement)

- **定義**: 在庫数量を動かした 1 件の出来事。補充・消費・訂正の 3 種類のいずれか。共通して識別子・数量・発生時刻・実行者・メモを持つ。追記のみで、更新も削除もされない。
- **別名**: `StockMovement`、変動、movement
- **関連用語**: [補充](#補充replenishment) / [消費](#消費consumption) / [訂正](#訂正correction) / [在庫変動一覧](#在庫変動一覧stockmovements) / [変動識別子](#変動識別子movementidentity)
- **実装**: `StockMovement`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovement.kt`)

### 補充(Replenishment)

- **定義**: 在庫を増やす変動。正味数量の集計では加算される。
- **別名**: `StockMovement.Replenishment`、`REPLENISHMENT`
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [消費](#消費consumption) / [手動希望](#手動希望wanted)
- **実装**: `StockMovement.Replenishment`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovement.kt`)

### 消費(Consumption)

- **定義**: 在庫を減らす変動。正味数量の集計では減算される。
- **別名**: `StockMovement.Consumption`、`CONSUMPTION`
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [補充](#補充replenishment) / [消費ペース](#消費ペースconsumptionrate)
- **実装**: `StockMovement.Consumption`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovement.kt`)

### 訂正(Correction)

- **定義**: 過去の補充または消費 1 件の数量を、正しい数量へ実効的に置き換える変動。対象変動の ID と訂正理由を持つ。訂正自体は増減を持たず、集計時に対象変動の数量を上書きする形で効く。
- **別名**: `StockMovement.Correction`、`CORRECTION`
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [変動 ID](#変動-idmovementid) / [訂正理由](#訂正理由reason)
- **実装**: `StockMovement.Correction`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovement.kt`)

### 在庫変動一覧(StockMovements)

- **定義**: 1 つの商品に対する在庫変動の集合を表すファーストクラスコレクション。正味数量の畳み込みと消費ペースの推定という 2 つの計算を担う。
- **別名**: `StockMovements`、変動履歴
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [正味数量](#正味数量netquantity) / [消費ペース](#消費ペースconsumptionrate)
- **実装**: `StockMovements`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/StockMovements.kt`)

### 変動識別子(MovementIdentity)

- **定義**: 変動が永続化済みかどうかを表す型。まだ保存されていない変動は `Pending`、保存済みの変動は `Persisted` + 変動 ID。訂正の対象にできるのは `Persisted` の変動だけ。
- **別名**: `MovementIdentity`、`Pending`、`Persisted`
- **関連用語**: [変動 ID](#変動-idmovementid) / [在庫変動](#在庫変動stockmovement)
- **実装**: `MovementIdentity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/MovementIdentity.kt`)

### 変動 ID(MovementId)

- **定義**: 永続化済みの在庫変動を一意に識別する ID。0 以上の整数(DB の連番)。
- **別名**: `MovementId`
- **関連用語**: [変動識別子](#変動識別子movementidentity) / [訂正](#訂正correction)
- **実装**: `MovementId`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/MovementId.kt`)

### 発生時刻(OccurredAt)

- **定義**: 在庫変動が実際に起きた時刻。補充・消費では利用者が指定でき、過去日を指定するバックデート記録が可能。値域の制約は持たない。
- **別名**: `OccurredAt`
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [評価基準時刻](#評価基準時刻evaluatedtime)
- **実装**: `OccurredAt`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/OccurredAt.kt`)

### 評価基準時刻(EvaluatedTime)

- **定義**: 消費予測を「いつ時点で評価したか」を表す時刻。発生時刻とは別概念で、予測の残日数と「直近」の判定の起点になる。
- **別名**: `EvaluatedTime`、asOf
- **関連用語**: [消費予測](#消費予測consumptionforecast) / [消費ペース](#消費ペースconsumptionrate) / [発生時刻](#発生時刻occurredat)
- **実装**: `EvaluatedTime`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/EvaluatedTime.kt`)

### メモ(Note)

- **定義**: 在庫変動に添える自由記述。前後の空白を取り除いた上で 0〜255 文字。空文字を許容する(メモ無しは空文字で表す)。
- **別名**: `Note`
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [訂正理由](#訂正理由reason)
- **実装**: `Note`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/Note.kt`)

### 訂正理由(Reason)

- **定義**: なぜ訂正するのかの説明。前後の空白を取り除いた上で 1〜255 文字で、空にはできない(メモと異なり必須)。
- **別名**: `Reason`
- **関連用語**: [訂正](#訂正correction) / [メモ](#メモnote)
- **実装**: `Reason`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/Reason.kt`)

### 消費ペース(ConsumptionRate)

- **定義**: 消費履歴から推定した 1 日あたりの消費数量(単位/日)。非負の小数で、0.0 は「消費実績が無く予測できない」ことを表す。
- **別名**: `ConsumptionRate`
- **関連用語**: [消費予測](#消費予測consumptionforecast) / [消費](#消費consumption) / [評価基準時刻](#評価基準時刻evaluatedtime)
- **実装**: `ConsumptionRate`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/movement/ConsumptionRate.kt`)

### 消費予測(ConsumptionForecast)

- **定義**: 現在のペースであと何日で在庫が尽きるかの見込み。残日数(`DaysRemaining`、0 以上)か、予測不可(`Unknown`)のいずれか。永続化も通信もしない算出専用の読みモデル。
- **別名**: `ConsumptionForecast`、残日数、あと約N日
- **関連用語**: [消費ペース](#消費ペースconsumptionrate) / [評価基準時刻](#評価基準時刻evaluatedtime) / [在庫](#在庫stock)
- **実装**: `ConsumptionForecast`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/ConsumptionForecast.kt`)

### 活動履歴(ActivityFeed / ActivityEntry)

- **定義**: 世帯全体の在庫変動を時系列で並べた読みモデル。在庫変動は商品への参照を持たないため、1 件ごとに商品を添えて「誰が・何の商品を・いくつ動かしたか」を表せるようにしている。
- **別名**: `ActivityFeed`、`ActivityEntry`、アクティビティ
- **関連用語**: [在庫変動](#在庫変動stockmovement) / [商品](#商品product)
- **実装**: `ActivityFeed`(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/ActivityFeed.kt`)、`ActivityEntry`(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/ActivityEntry.kt`)

### 買い物リスト(ShoppingList)

- **定義**: 「買い足すべき商品」を表す読みモデル。世帯の在庫一覧と手動希望フラグの集合から都度合成され、永続化された実体は持たない。在庫不足由来(自動)の項目と手動希望由来の項目を区別して取り出せる。
- **別名**: `ShoppingList`、買い物メモ
- **関連用語**: [買い物リスト項目](#買い物リスト項目shoppingentry) / [買い物要否](#買い物要否shoppingneed) / [手動希望](#手動希望wanted) / [在庫一覧](#在庫一覧stocks)
- **実装**: `ShoppingList`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingList.kt`)

### 買い物リスト項目(ShoppingEntry)

- **定義**: 買い物リストの 1 行。在庫と、その商品に手動希望が立っているかどうかの組。要否はこの 2 つから判定される。
- **別名**: `ShoppingEntry`
- **関連用語**: [買い物リスト](#買い物リストshoppinglist) / [買い物要否](#買い物要否shoppingneed)
- **実装**: `ShoppingEntry`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingEntry.kt`)

### 買い物要否(ShoppingNeed)

- **定義**: 買い物リストに載せるべきかどうかと、その理由を表す区分。在庫状態が `十分` でなければ `在庫不足`、`十分` でも手動希望が立っていれば `手動希望`、どちらでもなければ `不要`。`在庫不足` と `手動希望` はリストに載る。
- **別名**: `ShoppingNeed`、在庫不足、手動希望、不要
- **関連用語**: [在庫状態](#在庫状態stockstatus) / [手動希望](#手動希望wanted) / [買い物リスト](#買い物リストshoppinglist)
- **実装**: `ShoppingNeed`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingNeed.kt`)

### 手動希望(Wanted)

- **定義**: 在庫が足りていても「買っておきたい」と利用者が明示的に立てるフラグ。真偽値。設定と解除の履歴が追記形式で記録され、最新の値が現在の状態になる。
- **別名**: `Wanted`、買い物希望
- **関連用語**: [買い物要否](#買い物要否shoppingneed) / [買い物リスト](#買い物リストshoppinglist) / [補充](#補充replenishment)
- **実装**: `Wanted`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/Wanted.kt`)

### 数量(Quantity)

- **定義**: 1 件の在庫変動が動かす数量。必ず 1 以上の正の整数で、0 や負は許されない。
- **別名**: `Quantity`
- **関連用語**: [正味数量](#正味数量netquantity) / [在庫変動](#在庫変動stockmovement) / [単位](#単位productunit)
- **実装**: `Quantity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/quantity/Quantity.kt`)

### 正味数量(NetQuantity)

- **定義**: 全変動を畳み込んだ現在の在庫数量。補充の加算・消費の減算・訂正による上書きの結果で、0 にも(訂正途中の不整合では)負にもなり得るため、正の値しか取れない数量とは別の型で表す。
- **別名**: `NetQuantity`、現在数量、現在庫
- **関連用語**: [数量](#数量quantity) / [在庫状態](#在庫状態stockstatus) / [在庫変動一覧](#在庫変動一覧stockmovements)
- **実装**: `NetQuantity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/quantity/NetQuantity.kt`)

## 業務イベント

### 商品がマスタから採用される

- **概要**: カタログ商品を選び、単位と最低在庫を指定して、世帯の管理対象商品として登録する。
- **アクター**: 世帯のメンバー(役割の制約なし。世帯メンバーであることのみを検査する)
- **対象**: 世帯 / カタログ商品
- **事前条件**: 操作者が登録済み利用者であること。指定したカタログ商品が存在すること。操作者がその世帯のメンバーであること。同じ JAN を持つ商品が世帯内に(採用中・アーカイブ済のいずれでも)存在しないこと。
- **事後条件**: 商品が `採用中` 状態・画像未設定・バーコードは採用元カタログ商品の JAN にリンクした状態で生成される。商品名は採用元の名前がコピーされる。商品本体・初回リビジョン・バーコード行・採用元カタログへのリンク行が同一トランザクションで記録される。在庫変動は 1 件も無いので現在数量は 0(在庫切れ)。
- **取消・失敗**: カタログ商品が存在しなければ `ResourceNotFoundException`(NotFound)。非メンバーなら `MembershipRequiredException`(Unauthorized)。JAN が重複していれば `DuplicateJanException`(Conflict)。単位や最低在庫が値域外なら `IllegalArgumentException`(BadRequest)。採用の取消に相当する操作はアーカイブ。
- **順序・タイミング**: catalog の検索または JAN 照会でカタログ商品を特定した後に起きる。後続として補充・消費が可能になる。

### 世帯独自の商品が追加される

- **概要**: カタログに無い品目を、名前・バーコード・単位・最低在庫を指定してその場で追加する。
- **アクター**: 世帯のメンバー
- **対象**: 世帯
- **事前条件**: 操作者が登録済みかつ世帯メンバーであること。JAN 付きで追加する場合、その JAN を持つ商品が世帯内に存在しないこと。
- **事後条件**: 商品が `採用中` 状態・画像未設定で生成される。バーコードは JAN 有り(`Barcode.Linked`)か JAN 無し(`Barcode.Unlinked`)のいずれか。JAN 無しの場合はバーコード行が作られない。カタログへのリンク行は作られない。
- **取消・失敗**: 非メンバーなら Unauthorized。JAN 重複なら Conflict。名前・単位・最低在庫・JAN が値域外なら BadRequest。JAN 無しで追加する場合は重複判定そのものが行われない。
- **順序・タイミング**: catalog の JAN 照会が NotFound だったときの後続導線として起きることが多い。

### 商品の単位が変更される

- **概要**: 商品の数え方の単位を変更する。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品
- **事前条件**: 操作者が登録済みであること。操作者が商品の属する世帯のメンバーであり、かつマスタ管理権限を持つこと。
- **事後条件**: 変更後の商品全状態が新しいリビジョンとして 1 行追記される。過去のリビジョンは残る。
- **取消・失敗**: 商品が存在しなければ NotFound。非メンバーなら Unauthorized(`MembershipRequiredException`)。メンバーだがマスタ管理権限が無ければ Unauthorized(`OwnerRequiredException`)。単位が値域外なら BadRequest。取消は再度の変更で行う。
- **順序・タイミング**: 採用・追加の後、任意のタイミング。過去の在庫変動の数量は単位変更で換算されない。

### 商品の最低在庫が変更される

- **概要**: 補充が必要と判断する数量の下限を変更する。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品
- **事前条件**: 単位変更と同じ(登録済み・世帯メンバー・マスタ管理権限)。最低在庫は 0 以上。
- **事後条件**: 変更後の商品全状態が新しいリビジョンとして追記される。以後の在庫状態判定と買い物リストの内容が新しい閾値で評価される。
- **取消・失敗**: 単位変更と同じ。負の値なら BadRequest。
- **順序・タイミング**: 採用・追加の後、任意のタイミング。変更は既存の在庫変動には影響せず、判定結果だけが変わる。

### 商品画像がアップロードされる

- **概要**: 商品の写真を送信し、サーバが縮小・再エンコードしてストレージへ保存する。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品
- **事前条件**: 登録済み・世帯メンバー・マスタ管理権限。送信データが base64 文字列で、デコード後 8 MiB 以下であること。デコード可能な画像形式であること。
- **事後条件**: 画像が最大辺 512 ピクセルに縮小され、品質 0.85 の JPEG に再エンコードされてストレージへ保存される。保存キーは処理後バイト列の sha256。商品の画像が `Stored`(保存キー付き)に変わり、新しいリビジョンとして追記される。
- **取消・失敗**: base64 が不正、または上限超過なら BadRequest(RPC 実装側で判定)。画像としてデコードできなければ `IllegalArgumentException` → BadRequest。空のバイト列なら BadRequest。権限不足なら Unauthorized。取消は画像の取り外し。
- **順序・タイミング**: 商品登録後、任意のタイミング。ストレージ保存が先に行われ、その後にリビジョン追記が行われる。

### 商品画像が取り外される

- **概要**: 商品の画像を未設定の状態に戻す。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品
- **事前条件**: 登録済み・世帯メンバー・マスタ管理権限。
- **事後条件**: 商品の画像が `None` になった新しいリビジョンが追記される。
- **取消・失敗**: 権限不足なら Unauthorized。商品不在なら NotFound。取消は再アップロード。(要確認: ストレージ上の実体オブジェクトを削除する処理は無く、参照だけが外れる。孤児オブジェクトの掃除方針が未定)
- **順序・タイミング**: 画像アップロードの後。画像を設定する手段はアップロード経由のみで、保存キーを直接指定して設定することはできない。

### 商品がアーカイブされる

- **概要**: 使わなくなった商品を在庫一覧から外す。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品 / 在庫
- **事前条件**: 登録済み・世帯メンバー・マスタ管理権限。**現在数量がちょうど 0 であること**。
- **事後条件**: 商品状態が `アーカイブ済` になった新しいリビジョンが追記される。以後、在庫一覧・買い物リストに現れず、補充・消費もできなくなる。在庫変動の履歴は消えない。
- **取消・失敗**: 現在数量が 0 でなければ `CannotArchiveWithStockException`(Conflict)。現在数量が負の場合もアーカイブ不可として扱われる。権限不足なら Unauthorized。取消は復元。
- **順序・タイミング**: 在庫を使い切った後に行う。商品行そのものは削除されない(削除経路は存在しない)。

### 商品が復元される

- **概要**: アーカイブ済の商品を再び管理対象に戻す。
- **アクター**: 世帯主(マスタ管理権限を持つメンバー)
- **対象**: 商品 / 在庫
- **事前条件**: 登録済み・世帯メンバー・マスタ管理権限。
- **事後条件**: 商品状態が `採用中` になった新しいリビジョンが追記され、在庫一覧に再び現れる。数量は履歴どおり(アーカイブ時に 0 だったので通常 0)。
- **取消・失敗**: 権限不足なら Unauthorized。商品不在なら NotFound。現在数量に関する事前条件は無い。
- **順序・タイミング**: アーカイブの後。

### 在庫が補充される

- **概要**: 買ってきた・作ったなどで在庫が増えたことを記録する。
- **アクター**: 世帯のメンバー(役割の制約なし)
- **対象**: 在庫(商品)
- **事前条件**: 操作者が登録済みかつ商品の属する世帯のメンバーであること。**商品が `アーカイブ済` でないこと**。数量が 1 以上。
- **事後条件**: 補充変動が 1 件追記され、現在数量がその分だけ増える。発生時刻は利用者指定の値がそのまま記録される(過去日の指定が可能)。実行者はセッションから解決した居住者。**さらに、その商品の手動希望フラグが解除される**。
- **取消・失敗**: アーカイブ済の商品なら `ArchivedProductMovementException`(Conflict)。非メンバーなら Unauthorized。数量が 0 以下、メモが 256 文字以上なら BadRequest。記録の取消は削除ではなく訂正で行う。
- **順序・タイミング**: 商品登録の後。買い物リストからの補充では発生時刻は現在時刻に固定される。手動希望の解除は補充の後続処理として同じユースケース内で起きる。

### 在庫が消費される

- **概要**: 使った・食べたなどで在庫が減ったことを記録する。
- **アクター**: 世帯のメンバー(役割の制約なし)
- **対象**: 在庫(商品)
- **事前条件**: 登録済み・世帯メンバー。**商品が `アーカイブ済` でないこと**。数量が 1 以上で、**現在数量以下であること**。
- **事後条件**: 消費変動が 1 件追記され、現在数量がその分だけ減る。発生時刻は利用者指定の値がそのまま記録される。手動希望は解除されない。
- **取消・失敗**: 現在数量を超える消費は `InsufficientStockException`(Conflict)。アーカイブ済なら `ArchivedProductMovementException`(Conflict)。非メンバーなら Unauthorized。取消は訂正で行う。
- **順序・タイミング**: 補充の後(在庫が 0 のままでは消費できない)。消費の結果、在庫状態が `残りわずか` や `在庫切れ` に変われば買い物リストに自動的に載る。

### 在庫記録が訂正される

- **概要**: 過去に記録した補充または消費 1 件の数量が誤っていたとき、正しい数量へ実効的に置き換える。
- **アクター**: 世帯のメンバー(役割の制約なし)
- **対象**: 在庫変動 1 件
- **事前条件**: 登録済みであること。対象の変動が存在すること。操作者が、対象変動が属する商品の世帯のメンバーであること。**対象が永続化済みの補充または消費であること**(訂正変動そのものは訂正できない)。訂正後の正味数量が負にならないこと。訂正理由が 1 文字以上。
- **事後条件**: 訂正変動が 1 件追記される。集計時、対象変動の数量は訂正値で上書きされ、符号(補充なら加算・消費なら減算)は対象変動のものを引き継ぐ。元の変動行は書き換わらない。
- **取消・失敗**: 対象が存在しない、または補充・消費でなければ `ResourceNotFoundException`(NotFound)。訂正後に正味数量が負になるなら `InsufficientStockException`(Conflict)。理由が空なら BadRequest。取消は同じ対象に再度訂正を行う(同一対象への訂正は発生時刻が最も新しいものが採用される)。
- **順序・タイミング**: 対象変動が永続化された後。訂正はアーカイブ済の商品に対しても実行できる(履歴修正のため意図的にガードしていない)。(要確認: 訂正の発生時刻はサーバ側の現在時刻で生成され、利用者は指定できない。補充・消費と非対称で、暫定仕様とされている)

### 手動希望が設定・解除される

- **概要**: 在庫が足りていても買っておきたい商品に印を付ける、または印を外す。
- **アクター**: 世帯のメンバー(役割の制約なし)
- **対象**: 商品
- **事前条件**: 登録済みかつ商品の属する世帯のメンバーであること。
- **事後条件**: 希望フラグの値が履歴として 1 行追記され、最新値が現在の手動希望状態になる。真なら、在庫状態が `十分` でも買い物リストに `手動希望` として載る。
- **取消・失敗**: 非メンバーなら Unauthorized。商品不在なら NotFound。取消は逆の値を設定すること。
- **順序・タイミング**: 任意のタイミング。補充が記録されると自動的に偽へ解除される(消費では解除されない)。

### 買い物リストが合成される

- **概要**: 世帯の在庫一覧と手動希望の集合から、買い足すべき商品の一覧を都度組み立てる。
- **アクター**: 世帯のメンバー(参照操作)
- **対象**: 世帯
- **事前条件**: 登録済みかつ世帯メンバーであること。
- **事後条件**: 採用中の商品それぞれについて在庫状態と手動希望から要否が判定された一覧が返る。要否が `在庫不足` か `手動希望` の項目がリストに載り、`不要` の項目も一覧には含まれる(呼び出し側が絞り込む)。永続化は起きない。
- **取消・失敗**: 非メンバーなら Unauthorized。該当なしは空のリストで表され、エラーにはならない。
- **順序・タイミング**: 参照のたびに再計算される。補充・消費・最低在庫の変更・手動希望の設定のいずれかがあれば、次の参照から内容が変わる。

### 消費予測が算出される

- **概要**: 消費履歴から 1 日あたりの消費ペースを推定し、あと何日で在庫が尽きるかを見積もる。
- **アクター**: 画面(利用者の明示操作ではなく表示のたびに評価される)
- **対象**: 在庫
- **事前条件**: 評価基準時刻が与えられること。
- **事後条件**: 残日数(0 以上)か「予測不可」のいずれかが得られる。現在数量が 0 以下、または消費実績が無い場合は予測不可。
- **取消・失敗**: 失敗という概念は無く、予測できない場合は必ず「予測不可」を返す。
- **順序・タイミング**: 在庫の参照時にクライアント側で評価される。サーバ側では算出も保存もされない。

### 商品画像の閲覧 URL が発行される

- **概要**: 商品画像を表示するための署名付き URL を発行する。
- **アクター**: 登録済みの利用者
- **対象**: 商品
- **事前条件**: 操作者が登録済みであること。対象商品が存在し、画像が設定されていること。
- **事後条件**: 有効期限 1 時間の署名付き GET URL が返る。
- **取消・失敗**: 商品が存在しない、または画像が未設定なら NotFound。
- **順序・タイミング**: 画像アップロードの後。(要確認: この操作では世帯メンバー判定を行わない。実装コメントには「商品 ID は世帯一意・表示はメンバー全員可・URL は短命」を理由として意図的に省いたと記されているが、結果として他世帯の登録済み利用者でも商品 ID を知っていれば画像 URL を取得できる)

## 業務ルール

### 商品

- 商品名は前後の空白を除いた 1〜60 文字でなければならない(根拠: `ProductName`)。
- 商品名を後から変更する手段は提供されていない(根拠: `Product` に名前変更メソッドが無く、`ProductRegisterRpcService` にも該当メソッドが無い。名前は `products` テーブルにのみ保持されリビジョン管理の対象外)。
- 単位は前後の空白を除いた 1〜10 文字でなければならない(根拠: `ProductUnit`)。
- 最低在庫は 0 以上の整数でなければならない(根拠: `MinimumStock`)。
- 同一世帯内で同じ JAN を持つ商品を 2 つ登録することはできない。判定対象にはアーカイブ済の商品も含む(根拠: `ProductRegisterService.adopt` / `addCustom`、`ProductDataSource.existsByJan`)。
- JAN を持たない商品(`Barcode.Unlinked`)には重複判定が適用されない。同名の商品を何個でも追加できる(根拠: `ProductRegisterService.addCustom` が `Barcode.Linked` のときだけ判定する)。
- 商品の状態遷移は `採用中` ⇄ `アーカイブ済` の 2 状態のみで、削除は存在しない(根拠: `ProductStatus`、`Product.archive` / `unarchive`。DB の外部キーはすべて `RESTRICT`)。
- 商品の状態変更・単位変更・最低在庫変更・画像変更は、いずれも変更後の全状態を新しいリビジョンとして追記することで表現する。既存行の更新は行わない(根拠: `ProductRegisterRepository.appendRevision`、`ProductRevisionsTable`)。
- 商品の現在の設定は、その商品のリビジョンのうち ID が最大のものである(根拠: `ProductDataSource.productRows` が `row_number()` で最新 1 件に絞る)。

### 画像

- 画像参照キー・画像 URL は空白のみであってはならない(根拠: `ImageRef` / `ImageUrl`)。
- アップロードする原画像は空のバイト列であってはならない(根拠: `RawImageUpload`)。
- アップロード上限はデコード後 8 MiB。base64 文字列の長さで事前に上限超過を弾き、デコード後に厳密判定する(根拠: `ProductRegisterController.MAX_UPLOAD_BYTES` / `MAX_BASE64_CHARS`)。
- 保存される画像は必ず最大辺 512 ピクセル・品質 0.85 の JPEG に変換される。原画像はそのまま保存されない(根拠: `ImageProcessor`)。
- 透過を含む画像は白背景の RGB に落とされる(根拠: `ImageProcessor.toRgb`。JPEG が透過非対応のため)。
- 保存キーは処理後バイト列の sha256 なので、同一内容の画像は同一キーに収束する(根拠: `ImageProcessor.sha256Hex`)。
- 画像を設定する経路はアップロードのみで、保存キーを指定して設定することはできない。取り外しは未設定に戻す専用操作のみ(根拠: `ProductRegisterRpcService.uploadImage` / `removeImage` の設計コメント)。
- 発行される閲覧 URL の有効期限は 1 時間(根拠: `ProductImageTransfer.presignedUrl`)。

### 数量と在庫変動

- 1 件の在庫変動の数量は必ず 1 以上でなければならない。0 や負の数量の変動は作れない(根拠: `Quantity`)。
- 現在数量は変動を畳み込んだ結果であり、0 や負になり得る。そのため正の値しか取れない数量とは別の型で表す(根拠: `NetQuantity`、`StockMovements.netQuantity`)。
- 補充は加算、消費は減算、訂正自体は増減 0 として集計する(根拠: `StockMovements.netQuantity`)。
- 訂正は対象変動の数量を上書きする形で効き、符号は対象変動のものを引き継ぐ(根拠: `StockMovements.effectiveQuantity`。テスト `StockMovementsTest` の「補充への訂正はプラス符号を引き継ぐ」「消費への訂正はマイナス符号を引き継ぐ」)。
- 同一の対象に複数の訂正がある場合、発生時刻が最も新しい訂正が採用される(根拠: `StockMovements.latestCorrectionByTarget`)。
- メモは前後の空白を除いて 0〜255 文字。空文字を許容する(根拠: `Note`)。
- 訂正理由は前後の空白を除いて 1〜255 文字で、空にできない(根拠: `Reason` が `requireTrimmedWithin` を使う)。
- 変動 ID は 0 以上でなければならない(根拠: `MovementId`)。
- 訂正の対象にできるのは永続化済み(`Persisted`)の補充または消費のみ。訂正変動を訂正の対象にはできない(根拠: `StockMovements.hasBaseMovement`)。

### 在庫の操作

- アーカイブ済の商品に対して補充・消費を行うことはできない(根拠: `Stock.replenish` / `consume` が `ArchivedProductMovementException` を投げる)。
- 訂正はアーカイブ済の商品に対しても実行できる。履歴修正のため意図的にガードしていない(根拠: `Stock.correct` にアーカイブ判定が無い。`docs/known-issues.md` の項目 1 に「`correct`(訂正)は履歴修正なのでガードしない」と明記)。
- 現在数量を超える消費はできない(根拠: `Stock.consume` が `InsufficientStockException` を投げる)。
- 訂正の結果、正味数量が負になる場合はその訂正を受け付けない(根拠: `Stock.correct`)。
- アーカイブできるのは現在数量がちょうど 0 のときだけ。数量が残っていても、負であってもアーカイブできない(根拠: `Archivability.of` が `== 0` を条件とする、`Stock.archive`)。
- 復元には数量に関する事前条件が無い(根拠: `Stock.unarchive`)。
- 補充を記録したら、その商品の手動希望を必ず解除する。消費では解除しない(根拠: `StockRegisterService.replenish` の末尾で `setWanted(productId, Wanted(false))` を呼ぶ。`docs/known-issues.md` の項目 2)。
- 補充・消費の発生時刻は利用者が指定でき、過去日のバックデート記録ができる(根拠: `StockRegisterRpcService.replenish` / `consume` が `OccurredAt` を引数に取る)。画面側は未来日を選択できないよう制限している(根拠: `frontend` の `DatePick`)。
- 訂正の発生時刻はサーバ側の現在時刻で生成され、利用者は指定できない(根拠: `StockRegisterService.correct` が `OccurredAt.now()` を渡す。`.claude/rules/backend-rpc-and-transactions.md` に暫定仕様と明記)。

### 在庫状態と買い物リスト

- 在庫状態は、現在数量が 0 以下なら `在庫切れ`、最低在庫以下なら `残りわずか`、それ以外は `十分`(根拠: `StockStatus.of`)。最低在庫と等しい数量は `残りわずか` に含まれる(根拠: `MinimumStock.isBelow` が `<=` で判定。テスト `StockStatusTest`)。
- 買い物要否は、在庫状態が `十分` でなければ `在庫不足`、`十分` かつ手動希望が真なら `手動希望`、それ以外は `不要`(根拠: `ShoppingNeed.judge`)。在庫不足と手動希望は同時に成立せず、在庫不足が優先される。
- 買い物リストは採用中の在庫すべてから合成され、`不要` の項目も含んだ形で返る。自動分・手動分の絞り込みは呼び出し側が行う(根拠: `ShoppingList.from` / `autoItems` / `manualItems`、`ProductService.shoppingList`)。
- 手動希望の現在値は、その商品の希望イベントのうち ID が最大のものの値である(根拠: `ProductDataSource.latestWantedProductIds`)。

### 消費予測

- 予測は現在数量を 1 日あたりの消費ペースで割り、四捨五入した残日数として表す(根拠: `Stock.forecast`)。
- 現在数量が 0 以下、または消費ペースが 0.0 以下の場合は予測不可(根拠: `Stock.forecast`)。
- 消費ペースは消費変動(訂正反映後の実効数量)のみから推定し、補充は数えない(根拠: `StockMovements.consumptionRatePerDay`)。
- 観測期間が 60 日以上あり、かつ直近 60 日に消費があれば「直近 60 日の消費合計 ÷ 60」を採る。そうでなければ「全期間の消費合計 ÷ 観測日数」にフォールバックする(根拠: `StockMovements.consumptionRatePerDay`、`FORECAST_WINDOW_DAYS = 60`。テスト `StockForecastTest`)。
- 観測日数は「最初の補充または消費の日から評価基準日まで」で、最小 1 日にクランプして 0 除算を避ける(根拠: 同上。テスト「span1日クランプで0除算しない」)。
- ちょうど 60 日前の消費は直近窓に含める(根拠: テスト「窓境界_ちょうど60日前の消費はトレーリングに含む」)。

### 認可

- inventory のすべての RPC は登録済み利用者であることを要求する。未登録なら Unauthorized で短絡する(根拠: 各 Controller が `requireRegistered` を使う)。
- 参照系(在庫一覧・アーカイブ一覧・買い物リスト・変動履歴・活動履歴)は世帯メンバーであることを要求する(根拠: `ProductService` / `StockService` が `requireMember` を呼ぶ)。
- 在庫の書き込み(補充・消費・訂正)と商品の追加(採用・カスタム追加)、手動希望の設定は世帯メンバーであることのみを要求する(根拠: `StockRegisterService` / `ProductRegisterService.adopt` / `addCustom` / `setWanted` が `requireMember` を呼ぶ)。
- 商品マスタの編集(単位・最低在庫・画像・アーカイブ・復元)はマスタ管理権限を要求する。この権限を持つのは世帯主のみ(根拠: `ProductRegisterService.authorizeMaster` が `requireCanManageMaster` を呼ぶ、`RolePermissions.TABLE`)。
- 非メンバーの操作は `MembershipRequiredException`、メンバーだが権限不足の操作は `OwnerRequiredException` で区別する。どちらも RPC 上は Unauthorized になる(根拠: `Household.requireCanManageMaster`、`SessionGuard`)。
- 商品画像の閲覧 URL 発行だけは世帯メンバー判定を行わない(根拠: `ProductService.imageUrl` の実装コメント)。
- (要確認: `HouseholdCapability.在庫編集` という権限が定義され、役割 `閲覧者` はこれを持たないと定められているが、inventory の書き込み処理はどこでもこの権限を検査せず `requireMember` のみを使っている。結果として `閲覧者` の役割でも補充・消費・訂正・手動希望の設定・商品追加ができる。意図的か漏れかを確認したい)

## 設計判断

### 在庫数量を値として持たず、変動の追記から導出する

- **判断**: `Stock` は現在数量のフィールドを持たず、`StockMovements` を畳み込んで `NetQuantity` を算出する。在庫変動は追記のみで、更新・削除を行わない。
- **理由**: 「誰がいつ何をいくつ動かしたか」を失わずに残すため。訂正も削除ではなく訂正変動の追記として表現するので、監査可能性が保たれる。実装コメント(`StockMovements.netQuantity`)にも訂正の畳み込み規則が明記されている。

### 在庫に専用の識別子を持たせず、商品で同定する

- **判断**: `Stock` は `StockId` を持たず、`Stock(product, movements)` の組で表す。RPC も在庫の操作をすべて `ProductId` で受ける。
- **理由**: 1 商品につき在庫は 1 つで、商品と在庫が 1 対 1 に対応するため。実装コメント(`StockRpcService.history`)にも「商品は productId で既知」と書かれている。永続化上も在庫テーブルは存在せず、`stock_movements` が `product_id` を持つだけ。

### 商品の状態変更をリビジョンの追記として表す

- **判断**: 単位・最低在庫・画像・状態のいずれを変えても、変更後の全状態を `product_revisions` に 1 行追記する。既存行を UPDATE しない。現在値は ID 最大のリビジョン。
- **理由**: 設定変更の経緯を残すため。(要確認: 過去のリビジョンを利用者に見せる機能は現状無く、常に最新 1 件しか読まれない。履歴を残す目的が監査なのか将来の機能なのかはコードからは読み取れない)

### 手動希望も追記イベントとして持つ

- **判断**: 手動希望フラグは商品の属性としてではなく、`product_wanted_events` への真偽値の追記として持ち、最新値を現在状態とする。
- **理由**: (要確認: コード上の明示的な理由は無い。商品リビジョンと同じ追記スタイルで揃えたものと読めるが、`ShoppingList` の合成入力であることと、`Stock` 集約を不変に保ちたい意図が `StockRegisterService.replenish` のコメントに示唆されている)

### 買い物リストと消費予測を永続化しない読みモデルとする

- **判断**: `ShoppingList` は在庫一覧と手動希望集合から都度合成し、`ConsumptionForecast` は在庫から都度算出する。どちらもテーブルを持たない。
- **理由**: どちらも既存データから決定的に導けるため、保存すると同期ずれの原因になる。`ConsumptionForecast` には「算出のみの read-model で永続化・通信はしない」と実装コメントがある。

### 消費予測をサーバではなくクライアントで評価する

- **判断**: 消費予測の算出ロジックは `:domain`(Kotlin Multiplatform 共通)に置き、フロントエンドが `Stock.forecast(EvaluatedTime.now())` を直接呼ぶ。予測専用の RPC は存在しない。
- **理由**: 在庫一覧の RPC が変動履歴込みの `Stocks` を返すため、予測に必要な情報がクライアント側に揃っている。ドメインロジックを共有モジュールに置いたことで、サーバを介さずに同じ規則で評価できる。

### 予測不可を null ではなく型で表す

- **判断**: 消費予測は `ConsumptionForecast.Unknown` と `DaysRemaining` の 2 択の sealed 型で表し、null を返さない。
- **理由**: プロジェクト規約「nullable 戻り値原則禁止」に従い、「不在」を型シグネチャに現すため(実装コメントに明記)。同じ理由で商品画像も `ProductImage.None` / `Stored` の sealed 型、バーコードも `Barcode.Unlinked` / `Linked` の sealed 型で表している。

### 補充時に手動希望を自動解除する

- **判断**: 補充を記録したら、条件を付けずにその商品の手動希望を解除する。消費では解除しない。この処理はドメインではなく application 層(`StockRegisterService.replenish`)に置く。
- **理由**: 十分な量まで補充した商品が手動希望のまま買い物リストに残り続ける不具合(`docs/known-issues.md` の項目 2)への対応。手動希望は買い物リストの合成入力であり `Stock` 集約の状態ではないため、解除は集約のメソッドではなく orchestration として表現している。

### 数量を表す型を 2 つに分ける

- **判断**: 1 件の変動の数量は正の値のみ取れる `Quantity`、畳み込み結果は 0 や負も取れる `NetQuantity` と、別の型にする。
- **理由**: 「0 個補充した」のような無意味な記録を型で禁じつつ、訂正途中の不整合で負になり得る集計結果を表現できるようにするため(実装コメントに明記)。

### バーコードの有無を別テーブルで表す

- **判断**: 商品の JAN は `products` の nullable 列ではなく、`product_barcodes` の行の有無で表す。行があれば JAN 有り、無ければ JAN 無し。
- **理由**: nullable 列を排するため(実装コメントに「products.jan の nullable を排した side-table」と明記)。ドメイン側の `Barcode.Unlinked` / `Linked` の sealed 表現と対応している。

### 訂正で「打ち消し」ではなく「上書き」を採る

- **判断**: 訂正変動は対象変動を打ち消す逆符号の変動ではなく、対象変動の数量を置き換える指示として集計に効く。訂正変動自体の増減は 0。
- **理由**: (要確認: `StockRegisterRpcService.correct` の説明には「対象 movement を打ち消す訂正 movement を追記」と書かれているが、実装(`StockMovements.netQuantity` / `effectiveQuantity`)は打ち消しではなく上書きになっている。ドキュメントコメントと実装が食い違っており、どちらが意図した仕様か確認が必要)

### 画像の保存キーを内容ハッシュにする

- **判断**: ストレージ上の保存キーを、縮小・再エンコード後の JPEG バイト列の sha256 とする。
- **理由**: (要確認: コード上の明示的な理由は無い。同一内容の画像が同一キーに収束するため重複保存を避けられるが、逆に「別の商品に同じ写真を設定して片方を外すと、もう片方の実体も参照ごと共有される」性質を持つ。取り外し時に実体を削除しない現在の実装とは整合しているが、削除処理を足すときには衝突を考慮する必要がある)

### 活動履歴の並べ替えを presentation 層で行う

- **判断**: 世帯全体の活動履歴は、在庫一覧を取得したうえで Controller が変動を平坦化し、発生時刻の降順に並べ替えて返す。
- **理由**: 変動が商品への参照を持たないため、商品を添えた読みモデル(`ActivityEntry`)への組み立てが必要で、その組み立てが presentation の腐敗防止層の責務にあたるため。一方、商品単位の変動履歴(`history`)は追記順(ID 昇順)で返され、並び順の規則が 2 つの API で異なる。
