# 既知の不具合(後で対応)

実機で見つかった不具合のメモ。**トラック B(backend gap: occurredAt/画像/消費予測/通知)には含めない**。優先度を見て後日対応する。

各項目: 発見日 / 再現手順 / 期待 / 実際 / 推定原因(調査メモ)。

---

## 1. アーカイブ後も在庫操作ができてしまう 【解決済 2026-06-13】

- **発見**: 2026-06-08(ユーザ実機)
- **再現**: 在庫の詳細画面(ProductDetail オーバーレイ)→ 歯車 → 商品設定シート → アーカイブする。
- **期待**: アーカイブした商品は在庫として存在しないので、補充・消費はできない(導線が閉じる/操作が拒否される)。
- **実際(修正前)**: アーカイブ後も ProductDetail オーバーレイが開いたまま残り、補充・消費ボタンが押せてしまう。アーカイブ済み商品に在庫が積める。

- **対応(2026-06-13)**: frontend 導線 + domain 不変条件の 2 系統で修正。
  - **frontend**: `App.kt` の詳細(歯車)起点アーカイブ(`CatalogOverlay.Settings` の `onArchive`)で `catalogOverlay` に加えて `opened`(詳細オーバーレイ)も閉じる。`CatalogOverlayContent` に `opened` を渡す配線を追加。
  - **domain(フェイルセーフ)**: `Stock.replenish`/`consume` が `product.status.isアーカイブ済()` を見て `ArchivedProductMovementException` を throw。API 直叩きでもアーカイブ済みに積めない。RPC では `SessionGuard` が同例外を `RpcError.Conflict` に翻訳。
  - **対象外**: `correct`(訂正)は履歴修正なのでガードしない。ProductMaster 経由のアーカイブは元々詳細を開いていないので影響なし。

---

## 2. 買い物リストの商品を補充してもリストから消えない 【解決済 2026-06-13】

- **発見**: 2026-06-08(ユーザ実機)
- **再現**: 買い物タブ → リストにある商品(切らし/そろそろ)の「補充」を押す。
- **期待**: 補充して在庫が十分になれば、自動表示(在庫不足由来)の商品は買い物リストから外れる。
- **実際(修正前)**: 補充しても買い物リストに残り続ける。

- **真因**: 上記調査メモの **(b)**。`manuallyWanted`(手動希望)が補充で解除されない設計だったため、手動希望の商品は十分まで補充してもリスト(手動希望由来)に残り続けた。リロード経路((c))・低在庫判定((a))は正常だった。
- **対応(2026-06-13)**: `StockRegisterService.replenish` の末尾で `productRegisterRepository.setWanted(productId, Wanted(false))` を呼び、補充時に手動希望を解除する(案A・unconditional clear)。`consume` は解除しない。manuallyWanted は read-model 合成入力のため、解除は application 層の orchestration として表現(Stock 集約は不変)。モック `data.jsx`(補充で `wanted:false`)と一致。replenish→解除 / consume→非解除 を `StockRegisterServiceTest` で検証。
