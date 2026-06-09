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
- オブジェクトストレージ(S3/MinIO)導入。現行インフラ(Postgres + Zitadel のみ)に追加しない

## 2. 確定済みの方針(ユーザ判断・2026-06-09)

- **転送経路 = 既存 WS-RPC に相乗り**。新しい HTTP バイナリ面は作らない。配信は Compose/Skia 描画(DOM `<img>` 非使用)なので公開 URL は不要 → 認証済み WS チャネルでバイト列を運び、frontend で `ImageBitmap` に decode して `Image()` 描画。
  - JSON での `ByteArray` 肥大(int 配列 ~4倍)対策 = **サーバ側リサイズ + 転送は base64 文字列**。
- **スコープ = backend 先行 → ピッカー最終**。アップロード/配信/表示の backend を先に作り検証、最後に Wasm の `input type=file` ピッカーを配線。1 PR 内で段階実装。

## 3. ストレージ

新規テーブル `product_images`(Postgres bytea。`product_revisions` からは `image_ref` で参照のみ・実体は分離):

```
product_images
  ref          varchar(64)  PK      -- sha256(processed bytes) の hex
  content_type varchar(32)  NOT NULL -- "image/jpeg"
  bytes        bytea        NOT NULL -- リサイズ/再エンコード後の実体
  created_at   datetime     default now
```

- **content-addressed**: `ref = sha256(処理後バイト列)` の hex。同一画像は dedup され、ref は immutable。
- `ref` は 64 文字 hex → 既存 `image_ref varchar(512)` にそのまま収まる(**product_revisions のスキーマ変更不要**)。
- 削除は当面行わない(append-only / dedup 前提。orphan GC は将来課題として明記)。

## 4. 画像処理(infrastructure・JVM)

`javax.imageio`(JDK 内蔵・**新規依存なし**):
- decode: `ImageIO.read(bytes)`。decode 不能 → 不正画像として拒否(`IllegalArgumentException`)。
- リサイズ: 最大辺 **512px** を超えたらアスペクト比維持で縮小(`Image.getScaledInstance` / `Graphics2D` 描画)。
- 再エンコード: **JPEG 品質 ~0.85** で出力。出力 content_type は常に `image/jpeg`(透過は商品写真で稀・JPEG に統一)。
- 入力上限: base64 デコード後 **8MB** 超は拒否(`RpcError.BadRequest`)。

`product_images` の `bytes` は常に処理後 JPEG。

## 5. ドメイン

`domain/.../inventory/product/image/` に追加:
- `RawImageUpload`(`@JvmInline value class`、ByteArray 単一フィールド、`init { require(非空) }`)— クライアントがアップロードした原バイト列。
- `StoredImage`(`data class(bytes: ByteArray, mediaType: ImageMediaType)`)— 処理/保存済みの配信用コンテンツ。ByteArray を持つため equals/hashCode は本質比較しない旨を KDoc に明記(ドメインロジックで内容比較しない)。
- `ImageMediaType`(`enum { JPEG }`。将来 PNG 等拡張余地)。

`ProductImage` / `ImageRef` / `Product.changeImage` は既存のまま再利用。

## 6. application 層

新 Repository interface(`application/repository/product/`):
```kotlin
interface ProductImageStorageRepository {
    fun store(upload: RawImageUpload): ImageRef   // 処理 + sha256 + 保存、ref 返す
    fun load(ref: ImageRef): StoredImage          // 不在は ResourceNotFoundException
}
```

Service:
- **アップロード(write)** = `ProductRegisterService.uploadImage(productId, upload: RawImageUpload, actor)`:
  1. owner 認可(既存 `authorizeProduct` 再利用)
  2. `ref = storage.store(upload)`
  3. `product = productRepository.find...`
  4. `productRegisterRepository.appendRevision(product.changeImage(ProductImage.Stored(ref)))`
- **配信(read)** = `ProductService.loadImage(productId): StoredImage`:
  - product の `image` が `Stored(ref)` → `storage.load(ref)` / `None` → `ResourceNotFoundException`(素通し)
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
suspend fun loadImage(productId: ProductId): RpcResult<ProductImageResponse, RpcError>
// ProductImageResponse(contentType: String, base64: String) — presentation/rpc/product/
```
- Controller: `uploadImage` で base64 decode → `RawImageUpload` → service。owner ガード。decode 失敗/上限超過 → `BadRequest`。
- `loadImage` は registered ガード。画像未設定(`ResourceNotFoundException`)→ `RpcError.NotFound` → frontend はアイコン fallback。
- 既存 `changeImage(Stored(ref))` のクライアント生成経路は使わない(ref をクライアントが作れないため)。`changeImage` は None(削除)用途で維持。

## 8. frontend

**Thumb 拡張**(`designsystem/atom/Thumb.kt`):
- 引数に `image: ImageBitmap? = null` を追加。非 null なら `Image(bitmap, contentScale = Crop, clip + border)`。null なら既存ハッチ+アイコン(現状維持)。

**画像ロード**(共有):
- `loadImage(productId)` RPC → base64 decode → `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()` で `ImageBitmap` 化(wasmJs/js とも skiko)。
- productId → ImageBitmap の簡易キャッシュ(再 render で再 fetch しない)。`Stored` を持つ商品のみ fetch、`None`/NotFound はアイコン fallback。
- 配置: inventory の既存 ViewModel / refresh 経路に乗せる(商品ロード時に画像参照有無で遅延 fetch)。

**ImageField composable**(ProductSettings / MasterItemSheet。mock `screens-master.jsx:ImageField` 忠実):
- Thumb 64px(radius 16)+ soft ボタン「画像を追加/変更」+ 画像有時 ghost「削除」+ 補助文「正方形がおすすめ。未設定ならアイコンを表示します。」
- **ピッカー(最終段)**: wasmJs で DOM `<input type=file accept="image/*">` を生成 → `click()` → `FileReader.readAsArrayBuffer` → ByteArray → base64 → `uploadImage` RPC。kotlinx-browser interop。
- 削除 → 既存 `changeImage(productId, ProductImage.None)`。

## 9. テスト

- domain: `RawImageUpload`(空拒否)・`StoredImage`。
- infrastructure(integrationTest): `ProductImageStorageDataSource` の store(リサイズ後 JPEG/sha256 ref/dedup)→ load 往復。不正バイト列拒否。
- application: `uploadImage` が owner 認可 → ref を product revision に反映 / `loadImage` が None で NotFound。
- presentation: base64 decode・上限超過 → BadRequest。
- frontend: ロード経路の VM 単体(Stored→fetch / None→skip)。ピッカーは描画 render-verify。

## 10. 実装順(1 PR 内・段階)

1. domain(`RawImageUpload` / `StoredImage` / `ImageMediaType`)+ テスト
2. infrastructure(`product_images` テーブル + migration + `ProductImageStorageDataSource` 処理/sha256/store/load)+ integrationTest
3. application(`ProductImageStorageRepository` + `ProductRegisterService.uploadImage` / `ProductService.loadImage`)+ test
4. presentation(RPC メソッド + Request/Response + Controller)+ test
5. **backend 検証**: seed か RPC 直叩きで画像を入れ、`loadImage` 往復確認
6. frontend Thumb 画像表示化 + ロード経路(seed 画像で Thumb 表示を render-verify)
7. frontend ImageField + ピッカー(最終)・dev server 実描画で mock 突き合わせ

## 11. 留意点 / 既知の割り切り

- JSON base64 転送: 512px JPEG ~30-50KB → base64 ~40-70KB。商品画像は hot path でないため許容。
- orphan GC(参照されなくなった `product_images` 行)は将来課題。今回は append-only + dedup。
- ByteArray を持つ `data class StoredImage` の equals は内容非保証(KDoc 明記)。
- 入力フォーマットは ImageIO が decode 可能なもの全般(JPEG/PNG/GIF/BMP)。出力は JPEG 統一。
