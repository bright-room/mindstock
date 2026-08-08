# barcode(バーコード)コンテキスト

## コンテキスト概要

barcode コンテキストは、家庭の在庫に置かれる商品を「商品パッケージに印字された JAN コード」で識別するための最小のドメインである。`:domain` 上の実体は `Barcode`(バーコードの有無を表す sealed interface)と `Jan`(EAN-13 準拠の 13 桁コードの値オブジェクト)の 2 型のみで、集約は持たない。バーコード自体はライフサイクルを持つ集約ではなく、他コンテキスト(catalog / inventory)の中で識別子として使われる値である。

barcode が果たす役割は 2 つある。1 つは **カタログ品目(`CatalogItem`)の一意キー**で、JAN をキーに自前マスタ → 外部商品 API の順で商品情報を引き当てる(`CatalogService.lookupByJan`)。もう 1 つは **世帯内商品(`Product`)への紐付け**で、同一世帯に同じ JAN の商品を二重登録させないための重複判定キーになる(`ProductRepository.existsByJan`)。JAN を持たない商品も登録できるため、「JAN 有り/無し」を nullable ではなく `Barcode.Linked` / `Barcode.Unlinked` の sealed 型で表現している。なお、カメラによるバーコードスキャン機能は現時点で実装されておらず、JAN の入力経路は商品追加画面のテキスト入力のみである。

## 用語集

### バーコード
- **定義**: 商品が JAN コードと紐付いているかどうかを表す区分。「紐付いていない」状態と「特定の JAN に紐付いている」状態の 2 つだけを取る。
- **別名**: `Barcode`
- **関連用語**: JANコード、バーコード未連携、バーコード連携済み、世帯内商品
- **実装**: `Barcode`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Barcode.kt`)

### バーコード未連携
- **定義**: 商品に JAN コードが紐付いていない状態。マスタにも外部にも存在しない商品を手入力で追加した場合などに取る状態。
- **別名**: `Barcode.Unlinked`
- **関連用語**: バーコード、カスタム商品追加
- **実装**: `Barcode.Unlinked`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Barcode.kt`)

### バーコード連携済み
- **定義**: 商品が特定の JAN コード 1 件と紐付いている状態。保持する JAN は 1 件のみで、複数 JAN を持つ表現はない。
- **別名**: `Barcode.Linked`
- **関連用語**: バーコード、JANコード、JAN 重複
- **実装**: `Barcode.Linked`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Barcode.kt`)

### JANコード
- **定義**: 商品パッケージに印字された 13 桁の数字による商品識別コード。EAN-13 のチェックディジット規則を満たすものだけを有効な値として受理する。
- **別名**: `Jan`、EAN-13、JAN(標準タイプ)
- **関連用語**: チェックディジット、バーコード連携済み、カタログ品目
- **実装**: `Jan`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Jan.kt`)

### チェックディジット
- **定義**: JAN コードの 13 桁目に置かれる検査用の数字。先頭 12 桁から算出した値と一致しない入力は誤読・打ち間違いとみなして拒否する。
- **別名**: check digit
- **関連用語**: JANコード
- **実装**: `Jan.Companion.hasValidCheckDigit`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Jan.kt`、private)

### JAN 照会
- **定義**: JAN コードを手掛かりに商品情報(カタログ品目)を引き当てる操作。自前マスタを先に探し、無ければ外部商品 API に問い合わせ、そこで得た結果はマスタへ保存する。
- **別名**: `lookupByJan`
- **関連用語**: JANコード、カタログ品目、外部商品照会
- **実装**: `CatalogService.lookupByJan`(`backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogService.kt`)

### 外部商品照会
- **定義**: 自前マスタに存在しない JAN について、外部の商品 API から商品情報を受信する境界。プロバイダ(楽天 / Yahoo 等)は未確定で、現在は常に「不在」を返す既定実装のみが配線されている。
- **別名**: `ExternalProductRepository`、`UnconfiguredProductReceive`
- **関連用語**: JAN 照会、カタログ品目
- **実装**: `ExternalProductRepository`(`backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/ExternalProductRepository.kt`)、`UnconfiguredProductReceive`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/receive/catalog/UnconfiguredProductReceive.kt`)

### JAN 重複
- **定義**: 世帯内に同一 JAN を持つ商品がすでに存在する状態。採用中・アーカイブ済のいずれも対象に含み、この状態での追加登録は拒否される。
- **別名**: `DuplicateJanException`
- **関連用語**: バーコード連携済み、世帯内商品
- **実装**: `DuplicateJanException`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/DuplicateJanException.kt`)、判定は `ProductRepository.existsByJan`(`backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`)

### カタログ品目
- **定義**: JAN と商品名からなる商品マスタの 1 件。barcode コンテキストから見ると「JAN を一意キーとする商品名の引き当て先」であり、詳細は catalog コンテキストが担う。
- **別名**: `CatalogItem`
- **関連用語**: JANコード、JAN 照会
- **実装**: `CatalogItem`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItem.kt`)

### 商品バーコード行
- **定義**: 世帯内商品と JAN の紐付けを永続化する行。行が存在すれば連携済み、存在しなければ未連携を意味する。
- **別名**: `product_barcodes`、`ProductBarcodesTable`
- **関連用語**: バーコード、バーコード未連携
- **実装**: `ProductBarcodesTable`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/ProductBarcodesTable.kt`)

## 業務イベント

### JAN コードが入力される
- **概要**: 利用者が商品追加画面の検索欄に 13 桁の数字を入力し、その値が有効な JAN として認識される。認識されると「この JAN で商品を探す」導線が表示される。
- **アクター**: 世帯メンバー(利用者)
- **対象**: 商品追加画面の検索入力
- **事前条件**: 商品追加画面が開いている。
- **事後条件**: 入力値が JAN として妥当なら JAN 照会導線が表示され、名前検索の RPC は送信されない。妥当でなければ通常の商品名検索として扱われる。
- **取消・失敗**: 入力を書き換えれば判定はその場でやり直される。桁数不足・非数字の混在・チェックディジット不一致は「JAN ではない」と判定されるだけで、エラー表示は出ない。
- **順序・タイミング**: JAN 照会の直前。判定は入力のたびに同期的に行われる(`AddProductScreen.BrowsingContent`)。

### JAN コードで商品情報が照会される
- **概要**: 入力された JAN でカタログ品目を引き当てる。自前マスタを先に探し、見つからなければ外部商品 API に問い合わせる。
- **アクター**: 世帯メンバー(利用者)
- **対象**: カタログ品目(`CatalogItem`)
- **事前条件**: 有効な JWT で接続済み、かつ Resident として登録済みであること(`requireRegistered`)。
- **事後条件**: 見つかれば商品名が確定した採用フォームへ遷移する。見つからなければ手入力フォームへフォールバックし、その JAN は入力済みの読み取り専用項目として引き継がれる。
- **取消・失敗**: マスタにも外部にも無い場合は `ResourceNotFoundException` → `RpcError.NotFound` となり、フロントは手入力フォームに切り替える(エラー表示はしない)。`NotFound` 以外の失敗はトーストで通知し、検索画面に戻る。
- **順序・タイミング**: 「JAN コードが入力される」の後、利用者が照会行をタップした時点。後続は「商品にバーコードが紐付けられる」または「JAN 無しで商品が追加される」。

### 外部カタログから商品情報が取り込まれる
- **概要**: 自前マスタに無い JAN について外部商品 API から商品情報を受信し、その結果を自前マスタへ保存(キャッシュ)する。
- **アクター**: システム(`CatalogService`)
- **対象**: カタログ品目(`catalog_items`)
- **事前条件**: 自前マスタでの JAN 照会が `ResourceNotFoundException` で終わっていること。
- **事後条件**: 受信したカタログ品目が `catalog_items` に 1 行追加され、以後は同じ JAN でマスタ側がヒットする。
- **取消・失敗**: 不在・レート制限・障害・パース失敗はすべて `ResourceNotFoundException` に倒し、理由は出し分けない。現行配線では外部プロバイダが未設定(`UnconfiguredProductReceive`)のため、このイベントは常に失敗する。
- **順序・タイミング**: 「JAN コードで商品情報が照会される」の内部。マスタ照合の直後。

### 商品にバーコードが紐付けられる
- **概要**: 世帯に商品を登録する際、その商品に JAN を 1 件紐付ける(`Barcode.Linked`)。カタログ品目からの採用時は品目の JAN がそのまま複写され、手入力追加時は照会に使った JAN が引き継がれる。
- **アクター**: 世帯メンバー(利用者)
- **対象**: 世帯内商品(`Product`)
- **事前条件**: 世帯のメンバーであること。世帯内に同じ JAN の商品が存在しないこと。
- **事後条件**: `products` の登録と同一トランザクションで `product_barcodes` に 1 行挿入される。
- **取消・失敗**: 同一 JAN の商品が世帯内にすでにある場合は `DuplicateJanException` → `RpcError.Conflict` で登録自体が行われない。
- **順序・タイミング**: 商品の採用(`adopt`)またはカスタム追加(`addCustom`)の内部。商品登録と不可分。

### JAN 無しで商品が追加される
- **概要**: JAN を伴わずに商品を世帯へ登録する(`Barcode.Unlinked`)。商品名で検索してマスタに無かった場合など、JAN を経由しない追加経路で発生する。
- **アクター**: 世帯メンバー(利用者)
- **対象**: 世帯内商品(`Product`)
- **事前条件**: 世帯のメンバーであること。
- **事後条件**: `products` に行が作られ、`product_barcodes` には行が作られない(行の不在が未連携を意味する)。
- **取消・失敗**: JAN 重複判定は行われない(JAN が無いため)。
- **順序・タイミング**: カスタム追加(`addCustom`)の内部。

### 同一 JAN の商品登録が拒否される
- **概要**: 世帯内にすでに同じ JAN の商品がある状態で、その JAN を持つ商品を追加しようとして拒否される。
- **アクター**: システム(`ProductRegisterService`)
- **対象**: 世帯内商品(`Product`)の登録要求
- **事前条件**: 登録しようとする商品が `Barcode.Linked` であること。
- **事後条件**: 商品は登録されない。`DuplicateJanException` が送出され、presentation 境界で `RpcError.Conflict` に翻訳される。
- **取消・失敗**: このイベント自体が失敗表明。アーカイブ済の商品も重複判定の対象に含まれるため、「アーカイブ済だから再登録できる」という抜け道はない。
- **順序・タイミング**: 「商品にバーコードが紐付けられる」の直前(登録前チェック)。

## 業務ルール

- JAN コードは **13 桁ちょうど** でなければならない。桁数が異なる値は `IllegalArgumentException`(根拠: `Jan`、`JanTest.桁数が13でなければ拒否する`)
- JAN コードは **全桁が数字** でなければならない。英字などが混じる値は `IllegalArgumentException`(根拠: `Jan`、`JanTest.数字以外の文字は拒否する`)
- JAN コードは **EAN-13 のチェックディジット** を満たさなければならない。計算は「先頭 12 桁を左から 0 起点で数え、偶数位置(0,2,4,…)を 1 倍・奇数位置(1,3,5,…)を 3 倍して合計 `sum` を求め、`(10 - sum % 10) % 10` が 13 桁目と一致すること」。不一致は `IllegalArgumentException`(根拠: `Jan.hasValidCheckDigit`、`JanTest.チェックデジットが誤っていれば拒否する`)
  - 受理される例: `4901234567894` / 拒否される例: `4901234567890`(根拠: `JanTest`)
- 対応する規格は **EAN-13(JAN 標準タイプ)のみ**。8 桁の JAN 短縮タイプ、UPC-A、ITF、CODE128 等を表す型は存在しない(根拠: `Jan.LENGTH = 13` のみ)
- 1 つの商品が持つ JAN は **0 件または 1 件**。複数 JAN の紐付けは表現できない(根拠: `Barcode` の variant は `Unlinked` / `Linked(jan)` の 2 つ、`ProductBarcodesTable` の主キーは `product_id` 単独)
- **JAN の有無は nullable ではなく sealed 型で表す**。`Barcode.Unlinked` = JAN 無し、`Barcode.Linked(jan)` = JAN 有り(根拠: `Barcode`、`AddCustomProductRequest` の KDoc)
- 商品と JAN の紐付けは **専用テーブル `product_barcodes` の行の有無** で表す。行あり = 連携済み、行なし = 未連携。`products` 側に nullable な jan 列は持たない(根拠: `ProductBarcodesTable` の KDoc、`ProductHydration`)
- カタログ品目の JAN は **マスタ全体で一意**(根拠: `V1__init.sql` の `catalog_items_jan_unique UNIQUE (jan)`)
- 永続化上の JAN 列は **`VARCHAR(13)`**(根拠: `V1__init.sql` の `catalog_items.jan` / `product_barcodes.jan`)
- 同一世帯内に **同じ JAN を持つ商品を 2 つ以上登録できない**。判定対象には採用中とアーカイブ済の両方を含む(根拠: `ProductRepository.existsByJan` の KDoc、`ProductRegisterService.adopt` / `addCustom`)
- カタログ品目から商品を採用するとき、**カタログ品目の JAN がそのまま商品へ複写** され、必ず `Barcode.Linked` になる(根拠: `Product.adopt`、`ProductAdoptTest`)
- JAN 照会は **自前マスタ → 外部商品 API の順** に行い、外部でヒットした結果は自前マスタへ保存する(根拠: `CatalogService.lookupByJan`)
- 外部商品照会の失敗は理由を出し分けず、不在・レート制限・障害・パース失敗をすべて `ResourceNotFoundException` に倒す(根拠: `ExternalProductRepository` の KDoc)
- フロントエンドが入力値を JAN とみなすのは、**「数字のみを抽出した結果が有効な `Jan` を構成し、かつそれが入力文字列(trim 後)と完全一致する」場合のみ**。JAN とみなした入力では商品名検索の RPC を送信しない(根拠: `AddProductScreen.BrowsingContent`)

## 設計判断

### JAN の任意性を nullable ではなく sealed interface で表す
- **判断**: 「JAN を持つ商品」と「持たない商品」を `Jan?` ではなく `Barcode` sealed interface(`Unlinked` / `Linked`)で表現する。
- **理由**: プロジェクト全体の「公開 API で nullable 戻り値を原則禁止し、不在は例外か sealed 型で表す」規約に従うため(`.claude/rules/error-handling.md`)。`AddCustomProductRequest` の KDoc にも「`barcode` で JAN 任意を表現」と明記されている。

### `Barcode` の variant を value class ではなく object / data class にする
- **判断**: `Barcode.Unlinked` は `data object`、`Barcode.Linked` は `data class` として定義し、`Linked` を value class にしない。
- **理由**: `@Serializable` な sealed interface の variant を `@JvmInline value class` にすると、wire 上で中身の生値へ unwrap され type discriminator を載せられず、デシリアライズ時に variant を復元できなくなる(`.claude/rules/domain-guideline.md` に「過去に遭遇」と記録あり)。

### 商品の JAN を side-table(`product_barcodes`)に分離する
- **判断**: `products` テーブルに nullable な `jan` 列を持たせず、`product_barcodes` を別テーブルとして切り出し、行の有無で連携状態を表す。
- **理由**: `ProductBarcodesTable` の KDoc に「products.jan の nullable を排した side-table」と明記されている。ドメイン側の `Barcode` sealed 表現とスキーマ表現を揃え、nullable 列を排除するため。

### 外部商品 API の失敗理由を出し分けない
- **判断**: 外部商品照会の不在・レート制限・障害・パース失敗をすべて `ResourceNotFoundException` に統一する。
- **理由**: `ExternalProductRepository` の KDoc に「理由は出し分けず、呼び出し側=CatalogService は NotFound としてフロントの手入力フォールバックへ繋ぐ」と記されている。利用者から見れば「商品情報が得られなかった → 手入力する」という単一の導線に収束するため。

### JAN 照会の不在(`ResourceNotFoundException`)を catch して外部照会へ切り替える
- **判断**: `CatalogService.lookupByJan` はマスタ照合の `ResourceNotFoundException` を catch し、外部照会へフォールバックしてから結果をキャッシュする。
- **理由**: プロジェクト規約では「Service は Repository の例外を catch しない」が原則だが、`.claude/rules/error-handling.md` がこのメソッドを名指しで許容例外として挙げている。「不在 → 別経路」は例外の握り潰しではなく業務フローそのものであるため。

### 外部商品プロバイダを未確定のまま既定実装で塞ぐ
- **判断**: 外部商品 API の実装として、常に不在を返す `UnconfiguredProductReceive` を配線しておく。
- **理由**: `UnconfiguredProductReceive` の KDoc に「provider 決定後に `<Provider>ProductReceive` を追加実装」「実プロバイダが用意できるまでの間、lookupByJan を master 照合のみで成立させる」と記されている。プロバイダ選定を待たずに JAN 照会のフローを完成させるための暫定措置。

### カメラによるバーコードスキャンを実装しない
- **判断**: バーコードスキャナ(カメラ連携)を実装せず、JAN はテキスト入力で受け付ける。
- **理由**: `docs/superpowers/fidelity/add-product.md` の項目 D-AP-cam に「カメラ/スキャナ未実装(P6-2 決定・Wasm getUserMedia 重い)。テキスト JAN 入力で代替」と記録されている。フロントエンドのコードベースにも `getUserMedia` / `BarcodeDetector` 等の呼び出しは存在しない。

### 商品登録後に JAN を変更する経路を設けない
- **判断**: 商品の JAN は登録時にのみ確定し、以後の変更操作を提供しない。
- **理由**: (要確認)`Product` に barcode を差し替えるメソッドは無く、変更履歴を積む `ProductRegisterDataSource.appendRevision` も `product_barcodes` を書き換えない。ただし「意図的に不変としている」ことを明示した記述はコード・ドキュメントのいずれにも見当たらない。
