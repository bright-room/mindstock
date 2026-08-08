# barcode(バーコード)機能仕様書

## 機能概要

barcode は、商品パッケージの JAN コード(EAN-13)を扱うための最小のドメインである。`:domain` に含まれる型は次の 2 つだけで、集約やリポジトリは持たない。

| 型 | 役割 |
| --- | --- |
| `Jan` | 13 桁の JAN コードを表す値オブジェクト。桁数・数字のみ・チェックディジットを検証する |
| `Barcode` | 商品が JAN と紐付いているかを表す sealed interface(`Unlinked` / `Linked(jan)`) |

この 2 型は 2 つの用途で使われる。

1. **カタログ品目の一意キー**: `CatalogItem` が `jan` を保持し、JAN を手掛かりに自前マスタ → 外部商品 API の順で商品情報を引き当てる(`CatalogRpcService.lookupByJan`)。
2. **世帯内商品への紐付け**: `Product` が `barcode` を保持し、同一世帯における JAN の二重登録を防ぐ判定キーになる(`ProductRepository.existsByJan`)。

JAN の入力経路は **商品追加画面のテキスト入力のみ** である。カメラによるバーコードスキャンは実装されていない(`docs/superpowers/fidelity/add-product.md` の D-AP-cam に「P6-2 決定・Wasm getUserMedia 重い」として記録)。

## ユースケース

### UC-1: JAN コードで商品を探す

- **アクター**: 世帯メンバー(登録済み Resident)
- **基本フロー**
  1. 利用者が商品追加画面の検索欄に数字を入力する。
  2. フロントエンドが入力から数字のみを抽出し、`Jan` の生成を試みる。生成に成功し、かつ抽出結果が入力文字列(trim 後)と完全一致する場合のみ「JAN 入力」と判定する。JAN と判定された入力では商品名検索の RPC を送信しない。
  3. JAN と判定されると「JANコード『…』で商品を探す」の照会行が表示される。
  4. 利用者が照会行をタップすると `CatalogRpcService.lookupByJan(jan)` を呼ぶ。画面は全画面ローディング(`BrowsePhase.JanLookingUp`)になる。
  5. バックエンドが自前マスタ(`catalog_items`)を JAN で照合する。
  6. ヒットすれば `CatalogItem` を返し、フロントは商品名が確定した採用フォーム(`AdoptForm`)へ遷移する。
- **代替フロー(マスタ未ヒット → 外部照会)**
  - 5 でマスタに無い場合、`CatalogService.lookupByJan` が `ResourceNotFoundException` を catch し、`ExternalProductRepository.findByJan(jan)` で外部商品 API へ照会する。
  - 外部でヒットした場合は `CatalogRegisterRepository.register` で `catalog_items` に保存(キャッシュ)し、その `CatalogItem` を返す。
  - 現行配線では外部プロバイダが未設定(`UnconfiguredProductReceive`)のため、この経路は常に不在で終わる。
- **例外フロー(どこにも無い)**
  - マスタ・外部のいずれにも無ければ `ResourceNotFoundException` が presentation 境界で `RpcError.NotFound` に翻訳される。
  - フロントは `NotFound` をエラー扱いせず、手入力フォーム(`CustomForm`)へ遷移し、照会に使った JAN を読み取り専用項目として引き継ぐ。
- **例外フロー(その他の失敗)**
  - `NotFound` 以外の `RpcError` は `FailureHandler.onMutationFailure` でトースト表示し、検索画面(`Browsing`)へ戻る。`Unauthorized` の場合は再認証導線へ流れる。

### UC-2: JAN 付きで商品を世帯に登録する

- **アクター**: 世帯メンバー(登録済み Resident)
- **基本フロー(カタログからの採用)**
  1. 利用者が採用フォームで単位と最低在庫を決めて確定する。
  2. `ProductRegisterRpcService.adopt(householdId, catalogItemId, unit, minimumStock)` を呼ぶ。
  3. `AdoptProductScenario` が `CatalogService.findById` でカタログ品目を解決し、`ProductRegisterService.adopt` に渡す。
  4. `ProductRegisterService.adopt` が世帯メンバーであることを確認し、`existsByJan(householdId, catalogItem.jan)` で重複を確認する。
  5. `Product.adopt` がカタログ品目の名前と JAN を複写し、`Barcode.Linked(catalogItem.jan)` を持つ採用中商品を生成する。
  6. `ProductRegisterDataSource` が `products` / `product_barcodes` / `product_revisions` を同一トランザクションで挿入する。
- **基本フロー(手入力での追加)**
  1. 利用者が手入力フォームで商品名・単位・最低在庫を入力して確定する。JAN 照会からフォールバックしてきた場合は JAN が引き継がれている。
  2. フロントが `jan == null` なら `Barcode.Unlinked`、そうでなければ `Barcode.Linked(jan)` を組み立て、`AddCustomProductRequest` として `ProductRegisterRpcService.addCustom` を呼ぶ。
  3. `ProductRegisterService.addCustom` が世帯メンバーであることを確認する。`Barcode.Linked` の場合のみ `existsByJan` による重複確認を行う。
  4. `Product.custom` が商品を生成し、`registerCustom` で永続化する。`Barcode.Unlinked` の場合、`product_barcodes` には行が作られない。
- **例外フロー(JAN 重複)**
  - 4(採用)/ 3(手入力)で世帯内に同一 JAN の商品が存在する場合、`DuplicateJanException` を送出して登録を行わない。判定対象には採用中商品とアーカイブ済商品の両方を含む。
  - presentation 境界で `RpcError.Conflict` に翻訳される。フロントはフォーム状態を保持したままトーストを表示し、再試行できる状態を維持する。
- **例外フロー(権限・不正値)**
  - 世帯の非メンバーによる操作は `MembershipRequiredException` → `RpcError.Unauthorized`。
  - 不正な JAN 文字列を wire に載せた場合、`Jan` のデシリアライズ時に `IllegalArgumentException` が発生し `RpcError.BadRequest` に翻訳される。

## RPC インターフェース

barcode 専用の RPC service は存在しない。`Jan` / `Barcode` は catalog と product の RPC の引数・戻り値に現れる。

| RPC service | メソッド | barcode の関わり |
| --- | --- | --- |
| `CatalogRpcService` | `lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError>` | `Jan` を引数に取る唯一の RPC。戻り値の `CatalogItem` も `jan` を含む |
| `CatalogRpcService` | `search(name, limit): RpcResult<CatalogItems, RpcError>` | 戻り値の各 `CatalogItem` が `jan` を含む(引数には現れない) |
| `ProductRegisterRpcService` | `addCustom(householdId, request: AddCustomProductRequest)` | `AddCustomProductRequest.barcode: Barcode` で JAN の有無を受ける |
| `ProductRegisterRpcService` | `adopt(householdId, catalogItemId, unit, minimumStock)` | 戻り値 `Product` が `barcode` を含む。JAN はサーバ側でカタログ品目から複写される |
| `ProductRpcService` / `StockRpcService` 系 | `Product` / `Stock` を返す各メソッド | 戻り値に含まれる `Product.barcode` として伝搬する |

いずれも `requireRegistered` ガード配下(登録済み Resident のみ呼び出し可)で、全 RPC は単一エンドポイント `/api/rpc` に相乗りしている。

## データモデル

### `Jan`(値オブジェクト)

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Jan.kt`

- `@Serializable @JvmInline value class Jan(private val value: String)`
- `LENGTH = 13`(公開定数)
- バリデーション(すべて `init { require(...) }` → `IllegalArgumentException`)
  1. 文字列長がちょうど 13 であること
  2. 全文字が数字であること
  3. EAN-13 のチェックディジットが一致すること
- チェックディジット計算: 先頭 12 桁を左から 0 起点で数え、偶数位置(0,2,4,…,10)を 1 倍、奇数位置(1,3,5,…,11)を 3 倍して合計 `sum` を求め、`(10 - sum % 10) % 10` が 13 桁目と一致するかを見る。
- 受理例 `4901234567894` / 拒否例 `4901234567890`(チェックディジット不一致)、`490123456789`(12 桁)、`49012345678AB`(非数字)
- アクセサは `operator fun invoke(): String` と `toString()`

### `Barcode`(sealed interface)

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/barcode/Barcode.kt`

- `@Serializable sealed interface Barcode`
  - `data object Unlinked` — JAN 無し
  - `data class Linked(val jan: Jan)` — JAN 1 件と紐付け済み
- variant に value class を使わないのは、polymorphic serialization で type discriminator を載せられなくなるため(`.claude/rules/domain-guideline.md`)。

### 対応規格

対応するのは **EAN-13(JAN 標準タイプ)のみ**。JAN 短縮タイプ(8 桁)、UPC-A、ITF、CODE128 などを表す型は存在しない。

### 永続化

| テーブル | 列 | 内容 |
| --- | --- | --- |
| `catalog_items` | `jan VARCHAR(13) NOT NULL` | マスタ品目の JAN。`catalog_items_jan_unique UNIQUE (jan)` によりマスタ全体で一意 |
| `product_barcodes` | `product_id uuid PRIMARY KEY`, `jan VARCHAR(13) NOT NULL` | 世帯内商品と JAN の紐付け。行の有無が `Linked` / `Unlinked` を表す。`products(id)` への FK は `ON DELETE RESTRICT` |

- ハイドレーション: `ProductHydration` が `products LEFT JOIN product_barcodes` の結果から、`jan` が null なら `Barcode.Unlinked`、非 null なら `Barcode.Linked(Jan(...))` を組み立てる。
- 書き込み: `Barcode.toJanColumn()` が `Linked` → JAN 文字列、`Unlinked` → null に潰し、null でない場合のみ `product_barcodes` に挿入する(`ProductRegisterDataSource.insertProductAndRevision`)。
- 商品の変更履歴を積む `product_revisions` に JAN 列は無く、`appendRevision` は `product_barcodes` を更新しない。

### 世帯内の重複判定

`ProductDataSource.existsByJan` は `products INNER JOIN product_barcodes` に対して `products.household_id = ? AND product_barcodes.jan = ?` で存在確認する。商品ステータス(採用中 / アーカイブ済)による絞り込みは行わないため、アーカイブ済商品も重複判定の対象になる。

## エラー・例外

| 発生源 | 例外 | RpcError | 契機 |
| --- | --- | --- | --- |
| `Jan.init` | `IllegalArgumentException` | `BadRequest` | 桁数不一致 / 非数字混在 / チェックディジット不一致 |
| `CatalogDataSource.findByJan` | `ResourceNotFoundException` | `NotFound`(ただし `CatalogService` が catch して外部照会へ) | マスタに当該 JAN が無い |
| `UnconfiguredProductReceive.findByJan` | `ResourceNotFoundException` | `NotFound` | 外部プロバイダ未設定(現行は常にこれ)。実プロバイダ導入後も、不在・レート制限・障害・パース失敗はすべてこの例外に倒す契約 |
| `ProductRegisterService.adopt` / `addCustom` | `DuplicateJanException` | `Conflict` | 世帯内に同一 JAN の商品が既存 |

例外から `RpcError` への翻訳は `configuration/guard/SessionGuard.kt` の `runGuarded` が一括で行う。フロントエンド側では `lookupByJan` の `NotFound` のみ特別扱いされ、手入力フォームへのフォールバックとして解釈される(エラー表示はしない)。それ以外は `errorText(error)` でトースト文言に変換される。

## 制約事項・要確認

### 制約事項

- **カメラによるバーコードスキャンは未実装**。JAN は商品追加画面のテキスト入力でのみ受け付ける(`docs/superpowers/fidelity/add-product.md` D-AP-cam)。フロントエンドに `getUserMedia` / `BarcodeDetector` 等の呼び出しは存在しない。
- **外部商品 API のプロバイダが未確定**。`UnconfiguredProductReceive` が常に不在を返すため、マスタに無い JAN の照会は必ず手入力フォールバックになる。実装追加時は `<Provider>ProductReceive` を `infrastructure/receive/catalog/` に置く方針(`UnconfiguredProductReceive` の KDoc)。
- **`catalog_items` への初期データ投入経路がリポジトリ内に存在しない**。マイグレーション `V1__init.sql` はテーブル定義のみで、seed データは含まれない。したがって現状、マスタに行が入るのは外部照会がヒットした場合のキャッシュ経由に限られるが、その外部照会が未設定である。
- **1 商品につき JAN は最大 1 件**。複数バーコード(型番違い・リニューアル後の JAN など)の紐付けは表現できない。
- **EAN-13 以外の規格は非対応**。8 桁の JAN 短縮タイプを入力しても `Jan` の生成に失敗し、単なる商品名検索として扱われる。

### 要確認

- **JAN の変更経路が無いのは意図的か**: `Product` に barcode を差し替えるメソッドは無く、`appendRevision` も `product_barcodes` を書き換えない。「登録時に確定して以後不変」という設計判断がどこにも明示されていない。
- **`CustomForm.nameLocked` が常に false**: `AddProductViewModel` は `CustomForm` を生成する 2 箇所(JAN 照会の `NotFound` 時、商品名からのカスタム追加時)のいずれでも `nameLocked = false` を渡している。一方 `AddProductScreen.CustomFormContent` には `nameLocked = true` のときに商品名を読み取り専用で表示する分岐と「JANコードから取得した商品名を使用します。」という文言が実装されており、現状この分岐には到達しない。設計意図(将来の外部照会ヒット時に使う想定か、デッドコードか)を確認したい。
- **重複 JAN 用の文言が未使用**: `strings.xml` の `add_product_already_jan`(「このJANコードの商品はすでに在庫にあります。」)と `add_product_jan_hint` はコードから参照されていない。`AddProductViewModel.submit` は `conflictText` を渡さずに `failure.onMutationFailure(outcome.error)` を呼ぶため、`DuplicateJanException` → `Conflict` は汎用のエラー文言でトースト表示される。専用文言を出す意図があったのか確認したい。
- **`ProductRepository.existsByJan` の Boolean 戻り値**: `Boolean` を返すのは述語メソッドとして規約上許容されているが(`.claude/rules/domain-guideline.md`)、この判定は application 層の Repository interface にあり、domain の述語ではない。設計上の位置付けとして意図どおりか確認したい。
- **`Jan` の妥当性検査がクライアント側にもあることの扱い**: フロントエンドは `runCatching { Jan(digits) }.getOrNull()` で JAN 判定を行っており、無効な JAN はそもそもサーバへ送られない。サーバ側の `IllegalArgumentException` → `BadRequest` 経路が実際に到達するのは、フロント以外のクライアントや不正なペイロードの場合に限られる。
