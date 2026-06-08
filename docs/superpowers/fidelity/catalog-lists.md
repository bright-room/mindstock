# 忠実度チェックリスト: ProductMaster / Archived（商品マスタ・アーカイブ・mobile）

- mock: `screens-master.jsx:MasterScreen` / `ArchivedScreen`
- 正解: `/tmp/ms-fidelity/product-master/mock.png`、実装: `product-master/impl.png` / `archived/impl.png`、比較: `product-master/sbs.png`

## ProductMaster 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | back + 「商品マスタ」`700 18px` + 「{世帯} · N品目」`500 11.5px` | NavIconButton + summaryTitle + unitCaption | ○ |
| 商品を追加 | **accent 破線**(1.5px dashed) + `700 14.5px` accent + plus・h54 | AddTile に accent variant 追加 | ○（修正済） |
| ヒント | `500 12px/1.55` faint | unitCaption | ○ |
| 商品行 | surface r(md) border 影sm・**glyph thumb46**・名前`600 14.5px/1.3`・「単位: X · 在庫 N」・pencil | thumb を glyphForProductName・名前14.5 に修正 | ○（修正済） |
| 空状態 | EmptyState(box) | EmptyState | ○ |

## Archived 一致（render 確認 ○・mock seed は archived 0 のため mock JSX 突合 + 構造は Master と同一）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | back + 「アーカイブした商品」+ 「{世帯} · N件」 | 同 | ○ |
| ヒント | owner/viewer で文言分岐 | archived_hint_owner/viewer | ○ |
| 行 | **thumb opacity0.7** glyph・名前14.5・「単位: X · 最低 N」・「在庫に戻す」soft(owner) | thumb alpha0.7 + glyph・名前14.5 | ○（修正済） |
| 空状態 | EmptyState(archive) | EmptyState | ○ |

## メモ
- AddTile に `accent` フラグ追加（StockHome/Shopping の中立破線と区別）。
- 「在庫に戻す」アイコンは material 近似（restore≒Unarchive、mock は swap）。
