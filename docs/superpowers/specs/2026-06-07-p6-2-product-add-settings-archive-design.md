# P6-2 商品追加 / 設定 / アーカイブ 設計

フルリプレイス最終フェーズ **P6(`:frontend` / Compose Multiplatform Wasm)** の 3 番目のサブプロジェクト。在庫スパイン(P6-1a)・買い物/活動タブ(P6-1b)に続き、**商品をカタログから追加/カスタム追加し、単位・最低在庫を設定し、アーカイブ/復元する**フローを配線する。

- backend は P0–P5c で完成済み。本フェーズは既存 RPC を呼ぶ frontend のみ。
- mock: `docs/ref/mindstock.zip`(`app/screens-b.jsx` の `AddProduct`、`app/screens-master.jsx` の `MasterScreen` / `MasterItemSheet` / `ArchivedScreen`)。

## スコープ(買えるスライス)

| 機能 | UC | RPC | 画面 |
|---|---|---|---|
| 商品追加(マスタ採用) | UC10,11,12 | `catalog.search` / `catalog.lookupByJan` / `productRegister.adopt` | AddProductScreen |
| 商品追加(カスタム) | UC13 | `productRegister.addCustom` | AddProductScreen |
| 単位の設定 | UC22 | `productRegister.changeUnit` | ProductSettingsSheet |
| 最低在庫の設定 | UC22 | `productRegister.changeMinimum` | ProductSettingsSheet |
| アーカイブ(在庫0のみ) | UC23 | `productRegister.archive` | ProductSettingsSheet |
| アーカイブ一覧・復元 | UC23 | `productService.listArchived` / `productRegister.unarchive` | ArchivedScreen |
| 商品マスタ管理(一覧) | — | `productService.list` 再利用 | ProductMasterScreen |

### 意図的な見送り(未配線・申し送り)

1. **画像設定(`changeImage`)は見送る**。backend に画像アップロード/配信の経路が無い(`ImageRef` を生成する upload RPC が存在せず、`ProductHydration` の DB read のみが参照)。モックの `ImageField` は端末ファイルを base64 化するだけで保存先が無い。→ `changeImage` は未配線のまま、設定シートから画像欄(ImageField)を外す。P0–P5c の「消費予測/通知/オフライン閲覧」と同じ「モデルにはあるが実装後回し」扱い。アップロード基盤ができた段階で再着手。
2. **カメラ・バーコードスキャナは見送る**。P6-2 の RPC スコープは `catalog.search` / `lookupByJan`(JAN で照会)のみで、カメラ撮影は含まない。JAN は**テキスト手入力**で `lookupByJan` を満たす。モックの `BarcodeScanner`(getUserMedia + デコード)は Wasm では実装量が大きく OOM 懸念もある。→「バーコードでスキャン」導線は置かない。

## アーキテクチャ

### 採用案: 新 feature `feature/catalog`

「商品の採用・マスタ管理・アーカイブ」は「在庫数量操作(`feature/inventory`)」と関心が別なので、feature 別パッケージ原則(`frontend-architecture`)に沿って新 feature を切る。

却下案: `feature/inventory` に同居して `InventoryRepository` を拡張。→ inventory が肥大し、Stock 操作と商品マスタ管理が混ざるため却下。

> 補足: `ProductRegisterRpcService` は `feature/inventory` でも `setWanted` 用に既に注入済み。`CatalogRepository` が同サービスを別途ラップしても、`() -> Service` の遅延ファクトリを各自持つだけで競合しない。

### パッケージ構成

```
feature/catalog/
  data/CatalogRepository.kt          # CatalogRpcService + ProductRegisterRpcService + ProductRpcService.listArchived をラップ
  AddProductViewModel.kt
  AddProductUiState.kt               # 検索/JAN照会/採用・カスタム分岐/フォーム状態
  ProductMasterViewModel.kt
  ProductMasterUiState.kt            # 採用中商品の一覧 + 設定保存(changeUnit/changeMinimum/archive)
  ArchivedViewModel.kt
  ArchivedUiState.kt                 # listArchived / unarchive
  ui/AddProductScreen.kt             # フルスクリーン overlay: 検索 → 採用/カスタム詳細フォーム
  ui/ProductMasterScreen.kt          # フルスクリーン overlay: 採用中商品の一覧
  ui/ProductSettingsSheet.kt         # MasterItemSheet 相当: UnitPicker + 最低在庫 Stepper + アーカイブ
  ui/ArchivedScreen.kt               # フルスクリーン overlay: アーカイブ済み一覧 + 復元
  ui/UnitPicker.kt                   # 共通単位チップ + 自由入力(feature-local composable)
designsystem/atom/EmptyState.kt      # モックの空状態 atom(無ければ追加)
```

## 入口とオーナー権限

`isOwner` を `App.kt` で算出する。`session.households` の active 世帯を引き、`members.roleOf(residentId).is世帯主()`。算出結果を配下に伝播。**サーバ側も owner を強制する(`change*`/`archive`/`unarchive` は owner のみ)ので、クライアント判定は UX 用**(押せないボタンの非表示/無効化)。万一すり抜けてもサーバが `Unauthorized`/`OwnerRequired` を返しトースト化される。

### 入口一覧

1. Stock ホーム「+」: 現状 `onAddProduct` がトースト stub(`App.kt`)。これを **AddProduct overlay を開く**に差し替え。
2. **最小 Profile タブ**: 現在 `tab_profile_placeholder` のプレースホルダを置換し、2 行を置く。
   - 「商品マスタ」(owner のみ表示)→ ProductMasterScreen overlay
   - 「アーカイブした商品」(誰でも閲覧・復元は owner のみ)→ ArchivedScreen overlay
   - 残り(世帯切替/作成・メンバー管理・住人設定)は **P6-3**。本フェーズでは触らない。
3. ProductDetail ヘッダの歯車アイコン(owner のみ・**`ProductDetailScreen` に新設**)→ その商品の ProductSettingsSheet。
4. ProductMaster 内「商品を追加」→ AddProduct overlay / 行タップ → ProductSettingsSheet。

### overlay の所有

`ProductDetailOverlay` と同様、フルスクリーン overlay は **app 層(`App.kt`)が state 駆動**で重ねる。入口が複数タブ横断(Stock の「+」/ Profile / ProductDetail 歯車)のため、feature route 内ではなく app 層に状態を集約するのが自然。`App.kt` の overlay 状態を 1 箇所にまとめる(`addProductOpen` / `masterOpen` / `archivedOpen` / `settingsTarget`)。App.kt が肥大する場合は overlay 配線を `app/shell/` 内の小さな composable に切り出してよい。

## AddProduct のデータフロー

```
検索フィールド入力
  ├ 入力が有効な JAN(EAN-13 13桁 + チェックディジット)
  │    → catalog.lookupByJan(jan)
  │        ├ Hit(CatalogItem)     → 採用フォーム(商品名ロック)
  │        └ NotFound             → カスタム追加フォーム(JAN 紐付け済・商品名は手入力)
  └ それ以外(商品名)
       → catalog.search(name, limit)  → 候補一覧
           ├ 候補タップ              → 採用フォーム(商品名ロック)
           └ 「"..." を世帯に追加」  → カスタム追加フォーム(JAN なし・商品名編集可)

採用フォーム  → productRegister.adopt(householdId, catalogItemId, unit, minimumStock)
カスタムフォーム → productRegister.addCustom(householdId, AddCustomProductRequest(name, unit, barcode, minimumStock))
```

- `Jan` VO は EAN-13 を `require` で強制。手入力文字列が `Jan` を構成できる時のみ JAN 照会経路に入る(13桁数字 + チェックディジット成立)。構成できない数字列は通常の名前検索として扱う。
- `barcode` は `Barcode.Linked(jan)`(JAN 照会経由)/ `Barcode.Unlinked`(JAN なしカスタム)で表現。
- 採用時の単位の初期値は「個」(モック踏襲)。最低在庫の初期値は 1。
- 重複登録防止は JAN ベース(backend が `Conflict` を返す)。frontend はサーバの `Conflict` をトースト化する(クライアント側 dedup は最小限)。

## ProductSettings のデータフロー

- 対象商品の現在値(`product.setting.unit` / `minimumStock`)で初期化。
- 単位変更 → `changeUnit` / 最低在庫変更 → `changeMinimum`(個別 RPC)。
- アーカイブは **在庫 0 のときのみ**活性(`Stock.currentQuantity() == 0`)。在庫が残るときはボタン無効 + 説明文(モック踏襲)。`archive` 成功で overlay を閉じ、`refresh.signal` 発火。
- backend は `archive` を在庫 0 以外で `BadRequest`(または専用例外)にするので、クライアントゲートはすり抜けてもサーバが弾く。

## refresh・エラー

- 変更系(adopt / addCustom / changeUnit / changeMinimum / archive / unarchive)成功後に既存 `InventoryRefreshController.signal` を発火 → Stock / Shop / Activity が再ロード。
- ArchivedScreen は `unarchive` 後に自身を再ロード + `refresh.signal`(復元品が在庫一覧へ戻るため)。
- エラーは `core/rpc/RpcErrors.kt` の `userMessageOf(RpcError)` 経由で文言化。指針:
  - `BadRequest` → フォームのフィールドエラー(AddProduct の名前/単位、ProductSettings の最低在庫など)
  - `Conflict`(重複 JAN 等)/ `Internal` → トースト
  - `NotFound`(lookupByJan) → 制御フローの一部(カスタム追加へ誘導)であってエラー表示にしない
  - `Unauthorized` → 既存 `ReauthController` 経由で再認証

## designsystem

`frontend-designsystem` 準拠。feature 層は `material3.*` を直接 import せず atom 経由。再利用する既存 atom: `Sheet` / `Stepper` / `SearchField` / `Thumb` / `RoundBtn` / `PrimaryButton` / `AppText` / `AppIcon` / `AddTile`。新規:

- `UnitPicker`(feature-local composable。共通単位チップ + 自由入力。product 固有のため designsystem には上げない)
- `EmptyState`(モックの空状態。複数画面で使うので `designsystem/atom/` に置く。無ければ追加)

`frontend-visual-fidelity-expectation` 準拠: モック(`screens-b` / `screens-master`)の clay 配色・余白・角丸・影に寄せる。素の Material3 ラッパで放置しない。

## i18n

`frontend-i18n-and-font` 準拠。新規ユーザ向け文言は `commonMain/composeResources/values/strings.xml`(ja)に追加し `stringResource` 参照。AddProduct / ProductSettings / Archived / ProductMaster / Profile 2 行の文言を追加。

## テスト

`frontend-compose-conventions` / `frontend-kmp-test-style` 準拠。commonTest は `kotlin.test.@Test` + Kotest assertions(FunSpec 不可)。検証対象:

- `CatalogRepository` の `RpcResult → RpcOutcome` 変換
- AddProduct ViewModel: JAN 妥当性ゲート(有効 JAN のみ照会経路)・採用/カスタム分岐・lookupByJan NotFound → カスタム誘導・採用/追加成功時の refresh 発火
- ProductSettings ViewModel: changeUnit/changeMinimum 呼び出し・アーカイブ可否(在庫 0 判定)
- Archived ViewModel: listArchived 読込・unarchive 後の再ロード/refresh
- owner 算出ロジック(`members.roleOf(residentId).is世帯主()`)

UI 描画網羅は追わない。

## 実装の進め方

実装は従来どおり(P6-1 の慣行)。ユーザ承認後に writing-plans で実装計画を作成し、段階実装する。

## 申し送り(P6-3 以降)

- Profile タブの本実装(世帯切替/作成・招待・メンバー管理・住人設定)は **P6-3**。本フェーズは Profile に商品マスタ/アーカイブの 2 行のみ置く。
- 画像アップロード基盤(upload/配信 RPC + ストレージ)+ `changeImage` 配線は基盤新設後。
- カメラ・バーコードスキャナ(getUserMedia + デコード)は将来。
