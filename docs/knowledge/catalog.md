# catalog(カタログ)コンテキスト

## コンテキスト概要

catalog は「JAN(バーコード)から商品名を引くための商品マスタ」を扱うコンテキスト。世帯が在庫管理したい商品を追加するとき、名前の一部やバーコードから既知の商品情報を引き当て、入力の手間を減らすことを目的とする。集約は `CatalogItem`(id / jan / name)のみで、単位や最低在庫といった世帯ごとの設定は一切持たない。

カタログは世帯に紐づかないグローバルな辞書として実装されている(`catalog_items` テーブルに世帯 ID 列が無く、`jan` に UNIQUE 制約が張られている)。自前 DB に無い JAN は外部商品 API から補完し、取得できたものを自前 DB にキャッシュとして書き戻す設計になっている。ただし外部プロバイダの実装は現時点で未確定で、既定実装 `UnconfiguredProductReceive` が常に不在を返すため、実運用上の照会は自前 DB のヒットのみで成立している。カタログから世帯の商品(`Product`)を作る「採用」は inventory コンテキスト側の責務であり、`Product` は catalog を一切参照しない(名前と JAN を値としてコピーするだけ)。

## 用語集

### カタログ商品(CatalogItem)

- **定義**: JAN と商品名の対応を保持する、バーコード照会専用の集約ルート。世帯や在庫の情報は持たず、「このバーコードはこの名前の商品である」という事実だけを表す。
- **別名**: `CatalogItem`、マスタ、大元マスタ、商品マスタ
- **関連用語**: [JAN](#jan) / [カタログ商品名](#カタログ商品名catalogitemname) / [カタログ商品一覧](#カタログ商品一覧catalogitems) / [外部商品情報](#外部商品情報)
- **実装**: `CatalogItem`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItem.kt`)

### カタログ商品 ID(CatalogItemId)

- **定義**: カタログ商品を一意に識別する ID。UUIDv7 で採番する。
- **別名**: `CatalogItemId`
- **関連用語**: [カタログ商品](#カタログ商品catalogitem)
- **実装**: `CatalogItemId`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItemId.kt`)

### カタログ商品名(CatalogItemName)

- **定義**: カタログ商品の表示名。前後の空白を取り除いた上で 1〜60 文字であることが保証される。
- **別名**: `CatalogItemName`
- **関連用語**: [カタログ商品](#カタログ商品catalogitem) / 商品名(`ProductName`。inventory コンテキストの別概念)
- **実装**: `CatalogItemName`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/content/CatalogItemName.kt`)

### カタログ商品一覧(CatalogItems)

- **定義**: カタログ商品の集合を表すファーストクラスコレクション。検索結果の入れ物として使う。該当なしは空のコレクションで表す。
- **別名**: `CatalogItems`
- **関連用語**: [カタログ商品](#カタログ商品catalogitem) / [検索上限](#検索上限searchlimit)
- **実装**: `CatalogItems`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItems.kt`)

### 検索上限(SearchLimit)

- **定義**: 名前検索で一度に返す件数の上限。1 以上 100 以下でなければならない。
- **別名**: `SearchLimit`
- **関連用語**: [カタログ商品名](#カタログ商品名catalogitemname) / [カタログ商品一覧](#カタログ商品一覧catalogitems)
- **実装**: `SearchLimit`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/SearchLimit.kt`)

### JAN

- **定義**: 商品パッケージに印字された 13 桁の商品コード(EAN-13)。カタログ商品は必ず 1 つの JAN を持ち、カタログ全体で重複しない。
- **別名**: `Jan`、バーコード、EAN-13
- **関連用語**: [カタログ商品](#カタログ商品catalogitem) / `Barcode`(inventory の `Product` が持つバーコード表現。関連コンテキスト参照)
- **実装**: `Jan`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Jan.kt`。catalog 専用ではなく barcode コンテキストの共有 VO)

### 外部商品情報

- **定義**: 自前カタログに存在しない JAN を、外部の商品データベース(楽天 / Yahoo 等を想定)へ照会して得る商品情報。取得できた場合はカタログ商品としてそのまま自前 DB に保存される。
- **別名**: `ExternalProductRepository`、外部商品 API
- **関連用語**: [カタログ商品](#カタログ商品catalogitem) / [カタログのキャッシュ保存](#カタログのキャッシュ保存)
- **実装**: `ExternalProductRepository`(`backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/ExternalProductRepository.kt`)、既定実装 `UnconfiguredProductReceive`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/receive/catalog/UnconfiguredProductReceive.kt`)

### カタログのキャッシュ保存

- **定義**: 外部商品 API から受け取った商品情報を、次回以降は自前 DB のヒットだけで解決できるよう `catalog_items` に書き込むこと。カタログへの書き込み経路はこれのみ。
- **別名**: `CatalogRegisterRepository.register`、cache
- **関連用語**: [外部商品情報](#外部商品情報) / [カタログ商品](#カタログ商品catalogitem)
- **実装**: `CatalogRegisterRepository`(`backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/CatalogRegisterRepository.kt`)、`CatalogRegisterDataSource`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/catalog/CatalogRegisterDataSource.kt`)

### 商品採用リンク(product_catalog_links)

- **定義**: 世帯の商品がどのカタログ商品から採用されたかを記録する中間テーブル。ドメインモデルには現れず、永続化層にのみ存在する。
- **別名**: `ProductCatalogLinksTable`、origin(マスタ由来 / 世帯独自 の導出元)
- **関連用語**: [カタログ商品](#カタログ商品catalogitem) / 商品採用(inventory の `Product.adopt`。関連コンテキスト参照)
- **実装**: `ProductCatalogLinksTable`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/ProductCatalogLinksTable.kt`)

## 業務イベント

### カタログ商品が名前で検索される

- **概要**: 商品を追加しようとする利用者が入力した文字列に対し、名前に部分一致するカタログ商品の一覧を返す。
- **アクター**: 登録済みの利用者(Resident)。世帯メンバーであるかの検査は行わない。
- **対象**: カタログ全体(全世帯共通の辞書)
- **事前条件**: 有効な JWT で接続し、Resident として登録済みであること(`requireRegistered`)。検索語は trim 後 1〜60 文字、上限件数は 1〜100 であること。
- **事後条件**: 名前に検索語を含むカタログ商品が最大 `limit` 件返る。該当なしのときは空の `CatalogItems` が返り、エラーにはならない。カタログの状態は変化しない。
- **取消・失敗**: 未登録の利用者は `Unauthorized`。検索語や上限が値域外なら `BadRequest`。取消の概念は無い(参照のみ)。
- **順序・タイミング**: 商品追加画面で利用者が検索語を入力したときに起きる。後続として「カタログ商品が世帯の商品として採用される」が続くことがある。

### カタログ商品が JAN で照会される

- **概要**: 13 桁の JAN を指定してカタログ商品を 1 件引き当てる。自前カタログに無い場合は外部商品 API へ問い合わせる。
- **アクター**: 登録済みの利用者(Resident)
- **対象**: 指定された JAN を持つカタログ商品
- **事前条件**: 有効な JWT で接続し、Resident として登録済みであること。JAN が 13 桁の数字かつ EAN-13 のチェックディジットを満たすこと。
- **事後条件**: カタログ商品が 1 件返る。自前カタログに無く外部から取得できた場合は、そのカタログ商品が `catalog_items` に保存された状態になる(次回以降は自前ヒットになる)。
- **取消・失敗**: 自前カタログにも外部にも存在しない場合は `ResourceNotFoundException` がそのまま伝播し `NotFound` になる。フロントエンドは `NotFound` を「商品情報なし」と解釈し、利用者が商品名を手入力するフォームへ遷移する(`AddProductViewModel.lookupByJan`)。JAN の形式不正は `BadRequest`。
- **順序・タイミング**: 商品追加画面で検索語が 13 桁の数字として解釈できるとき、利用者が JAN 照会ボタンを押すことで起きる。

### 外部商品情報がカタログに取り込まれる

- **概要**: JAN 照会で自前カタログが空振りしたとき、外部商品 API から得た商品情報をカタログ商品として保存する。
- **アクター**: システム(`CatalogService.lookupByJan` の内部処理)。利用者が直接起こす操作ではない。
- **対象**: 照会対象の JAN に対応するカタログ商品
- **事前条件**: 自前カタログに当該 JAN が存在しないこと。外部商品 API が商品情報を返すこと。
- **事後条件**: `catalog_items` に 1 行挿入され、以降その JAN は自前カタログでヒットする。挿入された ID は外部側から返された `CatalogItemId` をそのまま使う。
- **取消・失敗**: 外部側が不在・レート制限・障害・パース失敗のいずれであっても `ResourceNotFoundException` に倒され、保存は行われない(理由は呼び出し側に出し分けされない)。取消の手段は無い。
- **順序・タイミング**: 「カタログ商品が JAN で照会される」の途中、自前カタログの不在が判明した直後に起きる。現時点では既定実装 `UnconfiguredProductReceive` が常に不在を返すため、このイベントは実際には発生しない。

### カタログ商品が世帯の商品として採用される

- **概要**: 利用者がカタログ商品を選び、単位と最低在庫を指定して自分の世帯の商品(`Product`)として登録する。
- **アクター**: 世帯のメンバー(登録済み利用者かつ当該世帯のメンバー)
- **対象**: 世帯の商品(`Product`)。カタログ商品は読み取られるだけで変化しない。
- **事前条件**: 指定された `CatalogItemId` のカタログ商品が存在すること。操作者が当該世帯のメンバーであること。同一 JAN の商品がその世帯にまだ存在しないこと。
- **事後条件**: 世帯に `Product`(名前と JAN はカタログ商品からコピー、状態は採用中)が作られ、`product_catalog_links` に採用元のカタログ商品 ID が記録される。
- **取消・失敗**: カタログ商品が見つからなければ `NotFound`。非メンバーなら `Unauthorized`。同一 JAN が既に世帯にあれば `DuplicateJanException` → `Conflict`。
- **順序・タイミング**: 「カタログ商品が名前で検索される」または「カタログ商品が JAN で照会される」の後続。呼び出し口は catalog ではなく `ProductRegisterRpcService.adopt` で、`AdoptProductScenario` が catalog から商品を解決してから inventory の登録に渡す(詳細は inventory の product ドキュメントを参照)。

## 業務ルール

- カタログ商品は必ず 1 つの JAN を持つ。バーコードを持たない商品はカタログに存在しない(根拠: `CatalogItem` が `Jan` を非 nullable で直持ち。`Barcode.Unlinked` の概念は catalog に無い)。
- 同一 JAN のカタログ商品は 1 件だけ。`catalog_items.jan` に UNIQUE 制約が張られている(根拠: `CatalogItemsTable` の `uniqueIndex(jan)`、`V1__init.sql` の `catalog_items_jan_unique`)。
- カタログは世帯に属さないグローバルな辞書。誰がどの世帯から検索しても同じ結果が返る(根拠: `catalog_items` に世帯 ID 列が無く、`CatalogDataSource.search` / `findByJan` が世帯で絞り込まない)。
- カタログ商品名は前後の空白を取り除いた上で 1〜60 文字。空白のみの名前は拒否される(根拠: `CatalogItemName` の `requireTrimmedWithin(60, ...)`。生成時に `raw.trim()` で正規化してから検証する)。
- 名前検索の上限件数は 1〜100。0・負数・101 以上は拒否される(根拠: `SearchLimit` の `require(value in 1..MAX)`、`MAX = 100`)。
- JAN は 13 桁の数字で、EAN-13 のチェックディジットが正しくなければならない(根拠: `Jan` の `init` の 2 つの `require`)。
- 名前検索は部分一致(前方・後方を問わない)。検索語に含まれる `\` `%` `_` はエスケープされ、ワイルドカードとして解釈されない(根拠: `CatalogDataSource.search` が `LikePattern("%$escaped%", escapeChar = '\\')` を組み立てる)。
- カタログの照会・検索は「Resident として登録済み」であれば誰でも行える。世帯メンバーであることは要求されない(根拠: `CatalogController` の両メソッドが `requireRegistered` のみを使い、世帯の権限検査を行わない)。
- カタログへの書き込みは「外部商品 API から取得したものをキャッシュする」経路のみ。利用者がカタログ商品を直接登録・更新・削除する口は存在しない(根拠: `CatalogRegisterRepository` が `register` の 1 メソッドのみで、`CatalogRpcService` に書き込み系メソッドが無い)。
- 外部商品 API 側の失敗理由(不在 / レート制限 / 障害 / パース失敗)は呼び出し側に出し分けせず、すべて「見つからなかった」として扱う(根拠: `ExternalProductRepository` の KDoc、および `CatalogService.lookupByJan` が `ResourceNotFoundException` のみを catch する構造)。
- 自前カタログでヒットした場合は外部商品 API を呼ばない(根拠: `CatalogService.lookupByJan` の try 側が成功すれば catch に入らない。テスト「master にヒットしたら外部 API を呼ばない」で保証)。
- カタログ商品は作られた後に名前や JAN を変更する手段を持たない(根拠: `CatalogItem` に更新メソッドが無く、`CatalogRegisterRepository` に更新メソッドが無い)。

## 設計判断

### CatalogItem をバーコード照会専用の最小集約に縮小した

- **判断**: `CatalogItem` は `id` / `jan` / `name` の 3 つだけを持ち、推奨単位(`CatalogItemUnit`)・由来区分(`CatalogOrigin`)・名前を包む中間 VO(`CatalogContent`)を持たない。
- **理由**: 単位は世帯ごとに決める設定であってカタログの属性ではないため。由来(マスタ由来か世帯独自か)は `product_catalog_links` の有無から導出できるため、ドメインに持たせない。設計経緯は `docs/superpowers/specs/2026-06-02-p2-domain-reshape-product-catalog-design.md` に記録されている。

### Product はカタログを参照しない(値のコピーで切り離す)

- **判断**: 世帯の商品 `Product` は `CatalogItemId` を保持せず、採用時にカタログ商品の名前と JAN を値としてコピーする。採用元の対応関係は永続化層の `product_catalog_links` にだけ残す。
- **理由**: カタログはあくまで入力補助の辞書であり、世帯の商品がその後どう名前を変えても在庫管理は成立すべきだから。ドメインを切り離すことで、カタログ側の変更が世帯の商品に波及しない。

### 自前カタログの不在を外部照会への切り替え合図として使う

- **判断**: `CatalogService.lookupByJan` は自前カタログの `ResourceNotFoundException` を catch し、外部商品 API へフォールバックする。
- **理由**: 「不在 → 別経路」という業務フローそのものであり、例外の握り潰しではないため。プロジェクトの原則は「Service は例外を素通しする」だが、この箇所は明示的な許容例外としてルールに記載されている(`.claude/rules/error-handling.md` の「素通し原則の許容例外」)。

### 外部プロバイダ未確定の間は常に不在を返す既定実装で成立させる

- **判断**: `ExternalProductRepository` の実装として `UnconfiguredProductReceive` を DI に登録し、常に `ResourceNotFoundException` を投げる。
- **理由**: 外部 API プロバイダ(楽天 / Yahoo 等)の選定が終わっていない段階でも、JAN 照会を自前カタログのヒットのみで動かし、外れたときは手入力フォールバックへ繋げるため。プロバイダ確定後に `<Provider>ProductReceive` を追加実装して差し替える想定がクラスの KDoc に書かれている。

### 外部照会の失敗理由を呼び出し側に出し分けない

- **判断**: 不在・レート制限・障害・パース失敗をすべて `ResourceNotFoundException` に倒す。
- **理由**: 利用者から見た次の行動が「商品名を手入力する」で一つに収束するため。理由ごとに分岐しても画面の振る舞いが変わらない。

### 外部から取得した情報を無条件にキャッシュする

- **判断**: 外部照会でヒットしたら、その場で `catalog_items` に保存する。
- **理由**: 同じ JAN の再照会で外部 API を叩かないようにするため(レート制限・レイテンシの回避)。ただし外部から得た情報の正しさを検証する仕組みは無く、誤った名前もそのまま辞書に残る(要確認: 誤ったキャッシュを訂正する手段が現状無いことが許容されているのか)。

### 検索の並び順を指定していない

- **判断**: `CatalogDataSource.search` は `ORDER BY` を付けず、`LIMIT` のみを指定する。
- **理由**: 要確認。コードにもコメントにも意図の記載がなく、部分一致の件数が上限を超えたときにどの行が返るかが不定である。

### 名前検索を LIKE の部分一致で実装している

- **判断**: 全文検索や前方一致インデックスではなく、`%語%` の LIKE 検索を使う。
- **理由**: 要確認。カタログの想定規模や検索品質の要求がコードから読み取れない。`catalog_items.name` にインデックスは張られていない。

## 関連コンテキスト参照

- **inventory / product**: カタログ商品を世帯の商品として採用する処理(`Product.adopt` / `AdoptProductScenario` / `ProductRegisterService.adopt`)、同一 JAN の重複採用を防ぐ `DuplicateJanException`。詳細は inventory のドキュメントを参照。
- **barcode**: `Jan` / `Barcode` の値検証。catalog と inventory の双方から使われる中立な VO 群。詳細は barcode のドキュメントを参照。
