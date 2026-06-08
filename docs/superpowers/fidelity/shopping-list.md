# 忠実度チェックリスト: ShoppingList（買い物リスト・mobile）

- mock: `screens-c.jsx:ShoppingList`
- 正解: `/tmp/ms-fidelity/shopping-list/mock.png`、実装: `impl.png`、比較: `sbs.png` / `sbs-top.png`
- sample: previewStocks(7件)を ShoppingEntry 化・牛乳(十分)を手動希望にして「自分で追加」セクションも描画

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | 「在庫が少ない商品と…」`500 13px/1` + 「買い物リスト」`800 25px/1.1` ls-0.02 | greeting + screenTitle | ○ |
| 在庫から探して追加 | **破線ボーダー** + アイコン箱34(accentSoft)+title+sub+plus | drawBehind dashPathEffect に修正 | ○（修正済） |
| 進捗バナー | accent bg・cart・「あと N 点で買い物完了」`700 16/1.2`・「done/total」tnum・影md | ProgressBanner | ○ |
| セクション見出し | `700 12px/1` faint（auto+manual 両方あるとき） | statusLabel→700/12 に修正 | ○（修正済） |
| 自動行 | checkbox丸28・名前`600 15/1.3`・StatusDot+「· 目安 N単位」・補充soft sm | ShopRow | ○ |
| 手動行 | 「自分で追加」badge`600 10.5/1`+「在庫 N単位」・x外すボタン30・補充 | 10.5 に修正 | ○（修正済） |
| 空状態 | EmptyState(check・買うものはありません) | EmptyState | ○ |

## backend gap: なし
「目安 N」(shortage)は qty/min から算出＝frontend で完結。予測「目安」は consume ペース不要のため backend 不要。

## メモ
- mock SEED は wanted 商品が無く手動セクションが出ないため、手動行は impl サンプルで確認（JSX 実数値と突合）。
- 名前 15 vs 15.5 は許容微差。
