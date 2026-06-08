# 忠実度チェックリスト: ProductSettings（商品マスタの編集・mobile）

- mock: `screens-master.jsx:MasterItemSheet`（+ `UnitPicker` / `miniStep`）
- 正解: `/tmp/ms-fidelity/product-settings/mock.png`、実装: `impl-full.png`、比較: `sbs2.png`

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| Sheet タイトル | 「商品マスタの編集」 | settings_master_edit_title | ○ |
| 商品名 | `700 16.5px/1.3` ink | summaryTitle | ○(16 vs 16.5 微差) |
| 注記 | 「商品名は変更できません。」faint | settings_name_immutable | ○ |
| 数える単位 | label + UnitPicker(chips r99 1.5px・active=accentSoft+accent + その他入力) | UnitPicker | ○ |
| 最低在庫 | 上下ボーダー・label+caption・**miniStep(40 r11 角丸スクエア ± + 22px tnum)** | MiniStepper(新atom)に修正 | ○(修正済) |
| 変更を保存 | primary lg・**disabled は accent を opacity0.45 にフェード** | PrimaryButton disabled色を自色0.45に修正 | ○(修正済・系統的) |
| アーカイブ | quiet・qty>0 で disabled + 警告箱 / qty0 で有効 + 注記 | 同 | ○ |

## 差分（backend 起因・非表示を明示）

| ID | 要素 | 状態 |
|---|---|---|
| D-PS-img | 画像セクション（画像 label + Thumb64 + 画像を追加/変更・削除） | `[backend]` 画像アップロード/配信基盤なし(gap #2) → 非表示。基盤実装時に追加（トラック B 別PR） |

## 副次対応
- **MiniStepper 新設**で AddProduct の最低在庫(2箇所)も Stepper→MiniStepper に是正（AddProduct 全体の忠実化は Task 7）。
- PrimaryButton/AppButton の disabled 色フェードは全画面の disabled ボタンに波及（mock 準拠）。
