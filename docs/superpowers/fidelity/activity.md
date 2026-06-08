# 忠実度チェックリスト: Activity（履歴・mobile）

- mock: `screens-d.jsx:ActivityTab`
- 正解: `/tmp/ms-fidelity/activity/mock.png`、実装: `impl.png`、比較: `sbs.png`
- sample: sampleActivityFeed（今日2行 + 1日前/4日前/日付 + 1件訂正済・2 actor）

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | 「世帯のすべての記録」`500 13/1` + 「履歴」`800 25/1.1` | greeting + screenTitle | ○ |
| 日付ラベル | `600 12px/1` faint | statusLabel→12 に修正 | ○（修正済） |
| 日カード | surface r(lg=22) border lineSoft 影sm・行は borderTop 区切り | radius 18→22 に修正 | ○（修正済） |
| 行ノード | 34 r11・補充=accentSoft+ / 消費=surface2− border lineSoft | 同 | ○ |
| 商品名 | `600 14px/1.3` ink ellipsis | cardTitle→14 に修正 | ○（修正済） |
| サブ | `500 12px/1`「補充/消費 N単位 · 名前」+ 訂正時「· 訂正済」 | summarySub→12・訂正は inline 接尾辞 | ○（修正済） |
| 時刻 | `500 11.5px/1` faint 右(HH:mm) | summarySub→11.5 に修正 | ○（修正済） |
| 訂正の扱い | 訂正行は積まず元行に「訂正済」接尾辞 | Correction 除外 + correctedIds で接尾辞 | ○（mock 挙動準拠） |
| 空状態 | EmptyState(clock) | EmptyState | ○ |

## backend gap: なし（活動フィードは backend 提供）

## メモ
- 名前: mock は自分を「あなた」固定、実装は実 displayName（「たろう」）。ProductDetail と同様に実名表示を許容（mock はプロトタイプ簡略化）。
