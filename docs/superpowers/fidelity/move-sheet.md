# 忠実度チェックリスト: MoveSheet（補充/消費シート・mobile）

- mock: `screens-b.jsx:MoveSheet`（+ `core.jsx:Stepper`）
- 正解: `/tmp/ms-fidelity/move-sheet/mock.png`、実装: `impl-full.png`、比較: `sbs.png`

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| Sheet | ハンドル + タイトル「補充する/消費する」 | Sheet | ○ |
| 商品サマリ箱 | surface2 r(md) padding14/16 gap13・Thumb42(**glyph**)・名前`600 15px/1.3`・「現在 N単位」`500 12.5px/1` faint | 同（glyph 修正済） | ○ |
| Stepper | 両端に円形 ±(58)・中央 大数字 `700 52px/1` tnum + 単位 `500 13px/1` faint を**下に**stack | SpaceBetween + Column(数字52/単位下) に修正 | ○（修正済） |
| 増減プレビュー | 中央 `現在単位 › 後単位`(`700 16px/1`)・マイナス時 red + 「マイナス在庫」 | 同 | ○ |
| メモ入力 | height46 r12 border line・placeholder | TextInput | ○ |
| 送信 | primary lg「N単位 補充する/消費する」 | PrimaryButton | ○ |

## 差分（backend 起因・非表示を明示）

| ID | 要素 | 状態 |
|---|---|---|
| D-MS-date | DatePick「いつの出来事？」(今日/昨日/おととい/cal) | `[backend]` occurredAt サーバ確定のため非表示。backend gap #1(occurredAt 設定)実装時に復活（トラック B 別PR） |

## メモ
- Stepper 修正は CorrectionSheet（ProductDetail の訂正シート）にも波及＝モック準拠で改善。
- サンプル qty が mock(2)と異なる(3)のはデータ差のみ。
