# 既知の不具合(後で対応)

実機で見つかった不具合のメモ。**トラック B(backend gap: occurredAt/画像/消費予測/通知)には含めない**。優先度を見て後日対応する。

各項目: 発見日 / 再現手順 / 期待 / 実際 / 推定原因(調査メモ)。

---

## 1. アーカイブ後も在庫操作ができてしまう

- **発見**: 2026-06-08(ユーザ実機)
- **再現**: 在庫の詳細画面(ProductDetail オーバーレイ)→ 歯車 → 商品設定シート → アーカイブする。
- **期待**: アーカイブした商品は在庫として存在しないので、補充・消費はできない(導線が閉じる/操作が拒否される)。
- **実際**: アーカイブ後も ProductDetail オーバーレイが開いたまま残り、補充・消費ボタンが押せてしまう。アーカイブ済み商品に在庫が積める。

- **推定原因(調査メモ)**:
  - **frontend**: `frontend/src/webMain/.../App.kt` の `CatalogOverlay.Settings` の `onArchive`(~580-583 行)が `catalogOverlay = null`(設定シートを閉じる)だけで、**親の `opened`(ProductDetailOverlay)を閉じていない**。アーカイブ後も詳細オーバーレイが残り、補充/消費が活きる。アーカイブ成功時に `opened = null` も必要。
    - 補足: ProductMaster 経由(`CatalogOverlay.Settings` でなく Master のリスト → 設定)のアーカイブは詳細を開いていないので影響なし。詳細起点(歯車)のみの導線バグ。
  - **backend(要確認)**: ドメインに「アーカイブ済み(=`採用中` でない)商品の在庫変動を拒否する」ガードがあるか未確認。`domain/.../inventory/stock/Stock.kt` には `CannotArchiveWithStockException`(在庫があるとアーカイブ不可=アーカイブ時 qty0 前提)はあるが、`replenish`/`consume` 側に `ProductStatus.採用中` 要求は見当たらない。frontend を閉じても、API を直接叩けばアーカイブ済みに積めるなら**ドメイン不変条件としてのガードも追加**を検討(フェイルセーフ)。

---

## 2. 買い物リストの商品を補充してもリストから消えない

- **発見**: 2026-06-08(ユーザ実機)
- **再現**: 買い物タブ → リストにある商品(切らし/そろそろ)の「補充」を押す。
- **期待**: 補充して在庫が十分になれば、自動表示(在庫不足由来)の商品は買い物リストから外れる。
- **実際**: 補充しても買い物リストに残り続ける。

- **推定原因(調査メモ)**:
  - リロード経路自体は配線済み: `App.kt` shopContent の `LaunchedEffect(refresh) { refresh.signal.collect { shopVm.load() } }`、`ShoppingListViewModel.replenish` は `write(...)` 後に `load()` + `refresh.request()`。→ 補充後にリストは再取得されている。
  - よって不具合は **`ShoppingList` 読み取りモデルのメンバーシップ判定** の可能性が高い(`onShoppingList`/`ShoppingEntry`/`manuallyWanted` 周り):
    - (a) 自動表示(低在庫)なのに「補充後も低在庫のまま」=最低在庫(minimumStock)を跨いでおらず正しく残っている可能性(補充量が足りないだけ。仕様どおりかも)。→ 実データで qty/min を確認。
    - (b) `manuallyWanted` が補充で解除されない設計で、手動希望の商品は補充してもリストに残る(仕様の可能性)。ユーザ期待と乖離するなら「補充で manuallyWanted を解除/または十分になったら自動的に外す」挙動を検討。
    - (c) `shoppingList` 読み取りモデルが補充後の最新在庫を反映していない(現在在庫の集計タイミング/キャッシュ)。
  - 要調査: 当該商品の `currentQuantity` / `minimumStock` / `manuallyWanted` を補充前後で確認し、(a)(b)(c) のどれかを切り分ける。
