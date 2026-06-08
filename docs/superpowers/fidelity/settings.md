# 忠実度チェックリスト: Settings（設定タブ・mobile）

- mock: `screens-d.jsx:Profile`（+ `SectionLabel`/`ToggleRow`/`LinkRow`）
- 正解: `/tmp/ms-fidelity/settings/mock.png`、実装: `impl.png`、比較: `sbs.png` / `sbs-hh.png`
- sample: owner（たろう）+ メンバー（ゆい）の2人世帯

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | 「アカウントと世帯」+「設定」 | settings_eyebrow + tab_title | ○ |
| アカウントカード | 58 円アバター(accent塗り・白頭文字`700 24px`)+ 名前+pencil+ provider | 頭文字を 24px に修正 | ○（修正済） |
| 世帯カード | home箱(accentSoft)+名前+pencil+「N人で共有」+ 切り替えpill(accentSoft swap) | 同 | ○ |
| メンバー行 | 38円アバター **利用者別色塗り+白頭文字** ・名前+あなたbadge・role icon+label | accentSoft→avatarColorOf+onAccent に修正 | ○（修正済） |
| 家族を招待 | soft ボタン | AppButton Soft | ○ |
| 商品マスタを編集 | accentSoft box + オーナーbadge + sub + chevR | MasterEntryCard | ○ |
| 環境設定 | 2 トグル行(将来対応予定 badge + 無効トグル) | ToggleRow ×2 | ○ |
| その他 | 消費の傾向(近日) + アーカイブした商品 | LinkRow ×2 | ○ |
| ログアウト + footer | ghost + 「mindstock · MVP プレビュー」 | 同 | ○ |

## mock 逸脱（意図的・記録）

| ID | 要素 | 扱い |
|---|---|---|
| D-SET-leave | 「この世帯から退出」ボタン | mock Profile に無いが P6-3b で実装した退出機能（backend `leave` あり・ユーザ承認済）。維持。 |
| D-SET-mail | アカウント副文「Zitadel でログイン中」 | mock は「Zitadel · you@example.com」。実 OIDC でメール未取得のため汎用文言。許容。 |

## メモ
- ゆい avatar 色（amber）は世帯データ依存で mock(indigo)と hue 不一致＝[[avatar-color]]の決定通り許容（人ごとに別色がつく挙動が要点）。
- role アイコンは material 近似（crown≒WorkspacePremium）。
- 画面背景は surface2（mock の bg と近い別トークン）。許容。
