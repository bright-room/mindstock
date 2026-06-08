# 忠実度チェックリスト: Onboarding（4 step ウィザード・mobile）

- mock: `screens-onboard.jsx:Onboard`
- 正解: `/tmp/ms-fidelity/onboarding/mock-{welcome,name,confirm}.png`、比較: `sbs3-*.png` / `sbs2-*.png`

## 修正して一致（render 突合 ○）

| ID | 要素 | mock | 修正 |
|---|---|---|---|
| D-OB2 | ステップ見出し(Name/Household/Confirm) | `800 22px/1.3` ls-0.01 | wizardTitle()(ExtraBold 22)へ。summaryTitle(16)だった |
| D-OB1 | Welcome 項目の番号バッジ | 左上に 19px accent 円 + 白`700 10px`(1,2) | offset バッジ追加 |
| D-OB3 | Name/Household の文字数カウンタ | 入力下に「N / max」右寄せ `500 11.5px` | maxLength + カウンタ追加(100/50) |
| D-OB4 | Confirm 行の先頭 | 表示名=48 accent 円アバター(白頭文字`700 20px`)/世帯=48 r14 accentSoft home | leading スロットで追加 |
| D-OB-center | Welcome/Confirm の縦中央寄せ | content を縦中央 | body Column を step で Center/Top 分岐 |

## 一致（既存・○）
- topbar: 戻る(38 r12) + 進捗バー(2 segment) + 「step/2」
- Welcome: logo 64 r20 accent / title `800 26px/1.25` / sub / 2項目
- FormStep: icon箱 52 accentSoft / eyebrow accent / sub / 入力56 r16
- Confirm: カード(行2 + divider) + check 注記 / footer ボタン群

## 残（minor・flag）
- Welcome title が impl では 1 行(mock は `<br>` で 2 行)。文言は同一・折返しのみ差。
- Welcome footer の「別のアカウントでログイン」リンクは未配線(onboarding に cancel 経路なし)→ 省略。
- Welcome logo glyph は material(Inventory2)近似(mock は cube)。
