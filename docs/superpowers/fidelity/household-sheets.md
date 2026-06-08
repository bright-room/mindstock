# 忠実度チェックリスト — 世帯シート群(P6-4b Task 8)

mock: `screens-household.jsx`(NoHouseholdCard / HouseholdSwitcher / CreateHouseholdSheet / JoinCodeSheet)・
`screens-invite.jsx`(InviteSheet / MemberSheet)。実描画 A/B(`/tmp/ms-fidelity/<screen>/{mock,impl2}.png`、402px・dsf2)で照合。

合否凡例: ○=一致 / `[backend]`=バックエンド不足で再現不可・明示削除 / `[省略・要確認]`=意図的に mock と変えた(実プロダクト都合)。

## 横断: 入力 atom の clay 化(PR #120 既存画面も影響)

- **`TextInput` を素の Material3 `OutlinedTextField` → clay 標準フィールドに作り直し**: height56 / radius16 / 1.5px border(focus か入力済で accent・他 line)/ surface / shadow.sm / `600 17px` ink / placeholder faint。mock `screens-onboard.jsx:FormStep` と `screens-household.jsx` の input が同一値であることを確認して atom 1 本に集約。
- 影響 callsite(全 re-render で非退行を確認、むしろ忠実化向上): onboarding-name ○ / move-sheet メモ ○ / settings(inline-edit のため既定表示に出ない)○ / add-product(custom 名は別 state)/ create-household ○ / shopping AddToList / product-detail 訂正理由 / unit-picker。
- **`CodeInput`(新規 atom)**: 招待コード用。height64 / radius16 / 中央寄せ monospace `700 26px` / letterSpacing 0.22em。JoinCode で使用。

## NoHouseholdCard → NeedHouseholdScreen

- アイコン箱 52/radius16/accentSoft + home accent26 ○ / 見出し `700 17px/1.35` ○ / 副文 `400 13.5px/1.65` sub ○
- カード radius=lg(22)へ修正(20→tokens.radiusLg)○ / padding22 ○ / border lineSoft ○
- ボタン: 世帯をつくる(primary,home) / 招待コードで参加(ghost,link) lg ○

## HouseholdSwitcher

- タイトル `世帯を切り替え` summaryTitle / 副文 sub ○
- 行(active): 箱44/radius13 accent + home onAccent / 名 `700 15.5px` / `N人`・区切り`·`・role(owner=crown accent) / 右 24 円 check ○
- **メンバー数を `N人で共有`→`N人` に修正**(switcher は人数のみ。`switcher_member_count` 新設。Settings カードは `N人で共有` のまま=mock 一致)○
- **SwitcherAction を実線→破線ボーダーに修正**(mock `1px dashed`、accent/line)。drawBehind + dashPathEffect ○
- 新しい世帯をつくる(accent 破線・箱40 accentSoft)/ 招待コードで参加(line 破線)○

## CreateHouseholdSheet

- タイトル `世帯をつくる` / 副文 sub ○
- **home アイコン箱 52/radius16/accentSoft を追加**(mock にあり impl 欠落していた)○
- 入力(clay TextInput・例: 別荘の在庫)○ / 候補チップ `600 13px`(SuggestionChips)○
- CTA: この世帯をつくる(primary,home,lg,disabled=空)○

## JoinCodeSheet

- タイトル `招待コードで参加` / 副文 sub ○
- **入力を CodeInput(中央 monospace 26px/0.22em)に変更**(plain TextInput→)○
- `[省略・要確認]` mock はデモ用ヒント「どのコードでも『ゆいの家』に参加」。実装は **実バックエンドのコード照合プレビューカード**(参加する世帯 / 権限)を表示。実プロダクトでは任意コードを受け付けられないため、デモ文言ではなく実プレビューを採用。
- CTA: 参加する(link,lg,disabled=preview無し)○

## MemberSheet

- タイトル `メンバー` ○ / ヘッダ avatar 52円
  - **avatar を accent 単色→利用者別色(`avatarColorOf(name)`)+ 白頭文字 `700 21px` に修正**(mock は USERS 別色。hue 完全一致は世帯データ依存で不可、別色化挙動を再現)○
- 名 `700 17px` + (自分)バッジ / role 行(icon+label faint)○
- 権限 Seg(編集できる/閲覧のみ)○ / **role 説明文を追加**(seg 直下 `400 12px` faint。`role_member_desc`/`role_viewer_desc` 新設)○
- 世帯から外す(quiet, trash, statusOut)/ 確認カード(statusOutSoft + やめる/外す)○
- owner 時=crown note / 非 owner-self=user note ○

## InviteSheet(明示発行式)

- 構造的差分(working agreement で既決・PR #116): mock の **リンク / QR タブ / 共有する / 有効期限カウントダウン** は `[backend]`(共有可能 URL・QR 生成・共有基盤・有効期限が未実装)。Track A では作らない。
- 既存 UI(発行→コード表示→コピー/再発行/失効)を clay で点検: 説明文 / 参加時権限ラベル ○
- **role 説明文を追加**(Seg 直下 role icon + desc faint)○
- コード表示: surface2 箱 + monospace `700 26px`/0.22em + コピー ○ / 何度でも使えます caption ○ / 新しいコード(ghost)・失効する(quiet) ○
