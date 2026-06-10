# 商品画像(撮影/選択・表示)設計

P6-4b トラック B backend gap #2。`docs/superpowers/specs/2026-06-08-p6-4-frontend-fidelity-handoff.md` §4-2 を実装で確定する。

## 1. 目的とスコープ

商品に画像を設定し、一覧/詳細/設定で表示できるようにする E2E 経路を作る。

**既にあるもの**(再利用):
- ドメイン: `ProductImage`(`None` / `Stored(ImageRef)`)・`ImageRef` VO(非空 String)
- 永続化: `product_revisions.image_ref varchar(512) nullable`・hydration 双方向
- RPC: `ProductRegisterRpcService.changeImage(productId, ProductImage)`(owner のみ)

**今回作る**(欠けている E2E 経路):
1. バイト列アップロード → サーバ側で検証/リサイズ/再エンコード → 実体保存 → `ImageRef` 採番
2. `productId` → 画像バイト列の配信(表示)
3. frontend: Thumb の画像表示化 + 設定/マスタ編集の画像欄(ピッカー)

**スコープ外(意図的)**:
- カメラ撮影(既に見送り済。Wasm の `<input type=file accept="image/*">` は端末でカメラ起動を選べるが、専用 UI は作らない)

## 2. 確定済みの方針(ユーザ判断・2026-06-09)

- **保存先 = S3 互換オブジェクトストレージ。実装は Garage**(既存インフラで採用済み)。MinIO ではなく Garage を使う。DB(bytea)は不採用(DB 肥大/WAL を避け、SaaS のユーザアップロード画像の定石に寄せる)。
- **アップロード = backend 経由**(検証/リサイズ/認可のため)。frontend → base64 で WS-RPC → backend が検証/リサイズ/sha256/Garage put。
- **表示(配信)= presigned URL 直 fetch**(SaaS 定石)。backend は署名付き GET URL を RPC で発行するだけ → frontend(Ktor client)が Garage から直接生バイト fetch → `ImageBitmap` decode → `Image()` 描画(Compose/Skia なので DOM `<img>` 非使用)。backend 帯域を使わず並列/ブラウザキャッシュが効く。
  - **要件**: Garage をクライアントから到達可能にし、**バケットに CORS**(web オリジン + GET 許可)を設定。Garage は `PutBucketCors` / presigned URL を実装済み(確認済み)。
- **スコープ = backend 先行 → ピッカー最終**。アップロード/配信/表示の backend を先に作り検証、最後に Wasm の `input type=file` ピッカーを配線。1 PR 内で段階実装。

## 3. ストレージ(Garage / S3 互換)

実体は Garage バケットに置く。オブジェクトキー = `ref = sha256(処理後バイト列)` の hex(**content-addressed** → 同一画像は同一キーで dedup・immutable・キャッシュ安全)。

- `product_revisions.image_ref` には ref(64 文字 hex)を保存 → 既存 `varchar(512)` にそのまま収まる(**product_revisions のスキーマ変更不要**・DB に新テーブルは作らない)。
- バケット内オブジェクト: key=`<sha256hex>`、body=処理後 JPEG、Content-Type=`image/jpeg`。
- 削除は当面行わない(append-only / dedup 前提。orphan GC = 参照されなくなったオブジェクトの掃除は将来課題)。

### 3.1 クライアント / 配線

- S3 クライアント = **aws-sdk-kotlin**(`aws.sdk.kotlin:s3`。coroutine ネイティブで Ktor/suspend と相性良)。**新規依存**(`backend:core`。domain には持ち込まない)。
- Garage は path-style 必須 → `forcePathStyle = true`。`endpointUrl` / `region`(`garage`)/ 静的クレデンシャル(access key / secret)/ バケット名を **環境設定**(既存 `external.*` config 慣行に倣い `external.storage.*`)から注入。`S3Client` は起動配線(P5 相当)で生成し DataSource にコンストラクタ注入。

### 3.2 ローカル開発(compose)

`compose.yml` に Garage コンテナを追加(既存 postgres/zitadel と同じ宣言的パターン):

- `garage`: `dxflrs/garage` イメージ。`config.toml`(`replication_factor=1` 単一ノード・`s3_region="garage"`・`rpc_secret`・`admin_token`)を bind mount。ポート 3900(S3)/3901(RPC)/3903(Admin)。
- `garage-init`: 起動後に `garage layout assign -z dc1 -c 1G <node> && garage layout apply` → `garage bucket create <bucket>` → `garage key create` → `garage bucket allow --read --write` を冪等に実行し、生成クレデンシャルを `.env.garage`(repo ルート)へ書き出す(`zitadel-init` と同型)。
- 加えて **バケット CORS** を設定(`PutBucketCors` で web オリジンからの GET を許可)。presigned URL をブラウザから fetch するため必須。ローカルは frontend dev origin(例 `http://localhost:8080`)を許可。

## 4. 画像処理(infrastructure・JVM)

`javax.imageio`(JDK 内蔵・**新規依存なし**):
- decode: `ImageIO.read(bytes)`。decode 不能 → 不正画像として拒否(`IllegalArgumentException`)。
- リサイズ: 最大辺 **512px** を超えたらアスペクト比維持で縮小(`Image.getScaledInstance` / `Graphics2D` 描画)。
- 再エンコード: **JPEG 品質 ~0.85** で出力。出力 content_type は常に `image/jpeg`(透過は商品写真で稀・JPEG に統一)。
- 入力上限: base64 デコード後 **8MB** 超は拒否(`RpcError.BadRequest`)。
- 処理 + sha256 採番は infra(`ProductImageStorageDataSource`)で行い、Garage へ put する。

## 4. 画像処理(infrastructure・JVM)

`javax.imageio`(JDK 内蔵・**新規依存なし**):
- decode: `ImageIO.read(bytes)`。decode 不能 → 不正画像として拒否(`IllegalArgumentException`)。
- リサイズ: 最大辺 **512px** を超えたらアスペクト比維持で縮小(`Image.getScaledInstance` / `Graphics2D` 描画)。
- 再エンコード: **JPEG 品質 ~0.85** で出力。出力 content_type は常に `image/jpeg`(透過は商品写真で稀・JPEG に統一)。
- 入力上限: base64 デコード後 **8MB** 超は拒否(`RpcError.BadRequest`)。

`product_images` の `bytes` は常に処理後 JPEG。

## 5. ドメイン

`domain/.../inventory/product/image/` に追加:
- `RawImageUpload`(`@JvmInline value class`、ByteArray 単一フィールド、`init { require(非空) }`)— クライアントがアップロードした原バイト列。application 公開 API で primitive `ByteArray` を晒さないための VO。
- `ImageUrl`(`@JvmInline value class`、String、`init { require(非空) }`)— presigned GET URL。`imageUrl` RPC の戻り値 VO。

`ProductImage` / `ImageRef` / `Product.changeImage` は既存のまま再利用。

**`StoredImage` / `ImageMediaType` は不要**: presigned URL 配信では backend は処理後バイトを読まない(frontend が Garage から直 fetch)。処理後の JPEG バイトは infra 内部の `ByteArray` のまま put し、Content-Type は `image/jpeg` 固定で付与する。

## 6. application 層

新 Repository interface(`application/repository/product/`):
```kotlin
interface ProductImageStorageRepository {
    fun store(upload: RawImageUpload): ImageRef     // 処理 + sha256 + Garage put、ref 返す
    fun presignedUrl(ref: ImageRef): ImageUrl       // ref に対する署名付き GET URL を発行
}
```
- 実装 `ProductImageStorageDataSource`(infrastructure)は注入された `S3Client` で Garage と通信。store = put object。`presignedUrl` = aws-sdk-kotlin の presign(`presignGetObject`、有効期限付き)。
- **配信は presigned URL 方式なので backend は画像バイトを読まない**(store のみが put、読みは frontend が直 fetch)。`load(ref): StoredImage` は不要。
- aws-sdk-kotlin は suspend。Repository interface を `suspend` にするか同期に揃えるかは既存 DataSource(JDBC blocking)との対称性で実装時に決める。
- `ImageUrl` = presigned URL を運ぶ VO(`@JvmInline value class`)。配置(domain image pkg か application か)は plan で確定。

Service:
- **アップロード(write)** = `ProductRegisterService.uploadImage(productId, upload: RawImageUpload, actor)`:
  1. owner 認可(既存 `authorizeProduct` 再利用)
  2. `ref = storage.store(upload)`
  3. `product = productRepository.find...`
  4. `productRegisterRepository.appendRevision(product.changeImage(ProductImage.Stored(ref)))`
- **配信(read)** = `ProductService.imageUrl(productId): ImageUrl`:
  - product の `image` が `Stored(ref)` → `storage.presignedUrl(ref)` / `None` → `ResourceNotFoundException`(素通し)
- **削除** = 既存 `changeImage(productId, ProductImage.None)` をそのまま使う(新規不要)。

## 7. presentation 層(RPC)

base64 ↔ ByteArray 変換は Controller(腐敗防止層)で行う。

`ProductRegisterRpcService` に追加:
```kotlin
suspend fun uploadImage(productId: ProductId, request: UploadImageRequest): RpcResult<Unit, RpcError>
// UploadImageRequest(base64: String) — presentation/rpc/product/
```
`ProductRpcService` に追加:
```kotlin
suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError>
// ImageUrl = presigned GET URL の VO。型一致なら Request/Response を作らず VO 直返し。
```
- Controller: `uploadImage` で base64 decode → `RawImageUpload` → service。owner ガード。decode 失敗/上限超過 → `BadRequest`。
- `imageUrl` は registered ガード。画像未設定(`ResourceNotFoundException`)→ `RpcError.NotFound` → frontend はアイコン fallback。
- 既存 `changeImage(Stored(ref))` のクライアント生成経路は使わない(ref をクライアントが作れないため)。`changeImage` は None(削除)用途で維持。

## 8. frontend

**Thumb 拡張**(`designsystem/atom/Thumb.kt`):
- 引数に `image: ImageBitmap? = null` を追加。非 null なら `Image(bitmap, contentScale = Crop, clip + border)`。null なら既存ハッチ+アイコン(現状維持)。

**画像ロード**(共有):
- `imageUrl(productId)` RPC で presigned URL 取得 → **Ktor HttpClient で Garage から直接 GET**(生バイト)→ `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()` で `ImageBitmap` 化(wasmJs/js とも skiko)。
- frontend に Ktor `HttpClient`(js engine)を1つ用意(presigned URL は query に署名を含むので追加ヘッダ不要)。
- productId → ImageBitmap の簡易キャッシュ(再 render で再 fetch しない)。`Stored` を持つ商品のみ fetch、`None`/NotFound はアイコン fallback。presigned URL は有効期限付きなので、期限切れ時は再取得。
- 配置: inventory の既存 ViewModel / refresh 経路に乗せる(商品ロード時に画像参照有無で遅延 fetch)。

**ImageField composable**(ProductSettings / MasterItemSheet。mock `screens-master.jsx:ImageField` 忠実):
- Thumb 64px(radius 16)+ soft ボタン「画像を追加/変更」+ 画像有時 ghost「削除」+ 補助文「正方形がおすすめ。未設定ならアイコンを表示します。」
- **ピッカー(最終段)**: wasmJs で DOM `<input type=file accept="image/*">` を生成 → `click()` → `FileReader.readAsArrayBuffer` → ByteArray → base64 → `uploadImage` RPC。kotlinx-browser interop。
- **アップロードは単純同期**(ユーザ判断 2026-06-09): 選択→送信→完了までインジケータ(不定スピナー)で待つ。即プレビュー(楽観 UI)もクライアント側縮小も**入れない**。原本(最大8MB)を base64 で送るので大きい写真は数秒待ちが出うるが、低頻度・実装最小を優先。改善したくなったら A=即プレビュー+裏送信 / B=クライアント 1280px 縮小 を後付け(サーバ検証/512px/sha256 は不変なので追加は frontend 局所)。
- 削除 → 既存 `changeImage(productId, ProductImage.None)`。

## 9. テスト

- domain: `RawImageUpload`(空拒否)・`StoredImage`。画像処理(リサイズ/JPEG 再エンコード/sha256)は infra の純関数として単体テスト可(Garage 不要)。
- infrastructure(integrationTest・既存同様 `@Tags("integration")` で compose の live Garage に `TEST_STORAGE_*` 経由で当てる): `ProductImageStorageDataSource` の store(リサイズ後 JPEG/sha256 ref/dedup)→ presignedUrl 発行 → その URL を直 GET して同一バイトが返る往復。不正バイト列拒否。
- application: `uploadImage` が owner 認可 → ref を product revision に反映 / `imageUrl` が None で NotFound(storage はモック)。
- presentation: base64 decode・上限超過 → BadRequest。
- frontend: ロード経路の VM 単体(Stored→fetch / None→skip)。ピッカーは描画 render-verify。

## 10. 実装順(1 PR 内・段階)

1. **infra 準備**: `compose.yml` に Garage + `garage-init` 追加・config.toml・`.env.garage` 生成。aws-sdk-kotlin 依存追加・`external.storage.*` config・`S3Client` 起動配線。手動で put/get 疎通確認。
2. domain(`RawImageUpload` / `StoredImage` / `ImageMediaType`)+ 画像処理純関数 + テスト
3. infrastructure(`ProductImageStorageDataSource` 処理/sha256/Garage put・get/dedup)+ integrationTest
4. application(`ProductImageStorageRepository` + `ProductRegisterService.uploadImage` / `ProductService.loadImage`)+ test
5. presentation(RPC メソッド + Request/Response + Controller)+ test
6. **backend 検証**: seed か RPC 直叩きで画像を入れ、`imageUrl` 発行 → その URL を直 GET して往復確認(Garage に object が入ること・revision に ref が乗ること・presigned URL がブラウザ/CLI から引けること)
7. frontend Thumb 画像表示化 + ロード経路(seed 画像で Thumb 表示を render-verify)
8. frontend ImageField + ピッカー(最終)・dev server 実描画で mock 突き合わせ

## 11. 留意点 / 既知の割り切り

- base64 はアップロード(client→backend)のみ。512px に縮小済みを上げるわけではなく原本(最大 8MB)を base64 で送るので、上限内なら許容。**表示は presigned URL 直 fetch で生バイト**(base64 肥大なし)。
- presigned URL 有効期限(例 15分〜1時間)とバケット CORS は plan で具体値確定。期限切れは frontend が再取得。
- Garage をクライアントから到達可能にする必要(ローカルは `localhost:3900`、本番は公開 endpoint)。
- orphan GC(参照されなくなった Garage オブジェクト)は将来課題。今回は append-only + dedup。
- ByteArray を持つ `data class StoredImage` の equals は内容非保証(KDoc 明記)。
- 入力フォーマットは ImageIO が decode 可能なもの全般(JPEG/PNG/GIF/BMP)。出力は JPEG 統一。
- 本番は S3 互換 endpoint(既存 Garage インフラ)に `external.storage.*` を向ける。CI で integrationTest を回すには Garage(または S3 互換)を CI に立てる必要あり → CI 構成は実装時に確認(立てられなければ storage integ は手元/manual タグ運用にフォールバック)。
