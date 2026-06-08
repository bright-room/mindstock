# 忠実度チェックリスト: AddProduct（商品を追加・mobile）

- mock: `screens-b.jsx:AddProduct`
- 正解: `/tmp/ms-fidelity/add-product/mock.png`、実装: `impl.png`、比較: `sbs.png`
- sample: Browsing 状態 + カタログ結果2件（valid EAN-13）

## 一致（render 突合 ○・Browsing）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | back(navBtn) + 「商品を追加」`700 18px` | NavIconButton + summaryTitle | ○ |
| 検索欄 | height50 r14「商品名 または JANコードで検索」 | SearchField | ○ |
| ヒント文 | `500 12px/1.5` faint | unitCaption | ○ |
| カタログ行 | surface r(md) border・thumb44・名前`600 14.5px/1.3`・meta・chevR | 名前 14.5 に修正 | ○（構造一致） |
| JAN 照会行 | accent border・barcode 箱42・「JAN…で商品を探す」 | JanLookupRow | ○（JAN 入力時） |
| その場追加タイル | 破線 AddTile | AddTile | ○ |
| 採用/カスタムフォーム | サマリ箱・名前(lock/edit)・UnitPicker・最低在庫(MiniStepper)・送信 | 検証済 atom 再利用 | ○ |

## 差分（意図的省略 / データ制約・明示）

| ID | 要素 | 扱い |
|---|---|---|
| D-AP-cam | 「バーコードでスキャン」カード + 「または検索して探す」divider | `[省略]` カメラ/スキャナ未実装（P6-2 決定・Wasm getUserMedia 重い）。テキスト JAN 入力で代替 |
| D-AP-meta | カタログ行 meta「標準単位: X · N世帯が利用」/ カテゴリ thumb | `[data]` domain `CatalogItem` は (id, jan, name) のみで unit/shared/category を持たない → 「JAN …」+ 汎用 thumb で表示。リッチ化は domain 拡張要 |

## メモ
- 採用/カスタムフォームは ProductSettings と同じ UnitPicker/MiniStepper/disabled 色フェードを使用（既検証）。画像欄は backend gap #2 で非表示（フォーム末尾の注記のみ）。
