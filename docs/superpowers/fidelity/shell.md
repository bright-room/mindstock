# 忠実度チェックリスト — シェル(P6-4b Task 9)

mock: `app/app.jsx`(`DesktopChrome` サイドバー / `BottomNav` 浮遊ピル)。impl: `app/shell/WideShell.kt` / `BottomNav.kt`。
render: wide=1180px・bottom-nav=402px(`/tmp/ms-fidelity/{wide-shell,bottom-nav}/`)。

## WideShell(DesktopChrome)

- サイドバー width248 / padding `24px 16px` / surface ○
- **サイドバー右に borderRight 1px lineSoft を追加**(欠落していた・content との境界線)○
- ロゴ: 箱36/radius11/accent rotate-6 + box20 / `mindstock` `800 18px` ○
- 世帯スイッチャ: padding10/radius12/border line/surface2・箱30/radius9/accentSoft home17 ○
  - **名前を `15.5px`→`700 13.5px` に**(mock `700 13.5px/1.2`)○ / 副文 `切り替え・追加` faint ○
  - **chevron を ChevronRight→ChevronDown に**(mock `chevD`)○
- 商品を追加(primary full)○
- ナビ行: padding `11px 12px`/radius12/gap12・active accentSoft+accent / 他 transparent+sub・**label `600 14.5px`**(button 15.5→14.5)○
- お知らせ(bell)同スタイル ○
- フッタ: borderTop lineSoft・avatar34 円 accent・**名 `13.5px`**(15.5→13.5)・世帯名 faint11 ○
- content: 中央寄せ widthIn max880 ○

## BottomNav(浮遊ピル)

- 外側 padding `top8 / 左右14 / bottom14`(mock `8px 14px 14px`)○
- ピル: height62 / radius22 / border lineSoft / shadow lg / **bg surface alpha 0.82**(0.92→0.82。mock `surface 82%`。真の backdrop-blur は Wasm に無く近似)○
- 並び: 在庫 / 買い物 / [+] / 履歴 / 設定 ○
- 中央 FAB: 50/radius17/accent/shadow md/offset y-2/plus26 ○
- ナビ item: icon22 active accent 他 faint・**label `600 10px`**(statusLabel 12.5→10)○

原理限界: アイコン stroke の active 太線化は Compose の icon set で不可(rasterize 差・[[p6-4b-fidelity-program]])。
