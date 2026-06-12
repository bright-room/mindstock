---
paths:
  - "frontend/**/*.kt"
---

# Frontend RPC and Error Handling

frontend からの RPC 呼び出しとエラー処理の規約。

## Rule

- RPC は `RpcClientProvider` 経由でのみ開く。WS subprotocol でトークンを運ぶ(`mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` を別エントリで append)。
- Repository が `RpcResult<T, RpcError>` を受け、`toOutcome()`(`core/rpc/RpcOutcome.kt`)で `RpcOutcome<T>`(`Success(value)` / `Failure(error)`)に変換して ViewModel へ返す。ViewModel は `RpcOutcome` を分岐し、失敗時は `errorText(error)` で `UiText` 化する。`RpcError` の variant 網羅(`Unauthorized`/`NotFound`/`BadRequest`/`Conflict`/`Internal`)は `core/rpc/RpcErrors.kt` の `errorText(error: RpcError): UiText`(`else` なしの `when`)に集約し、ViewModel/UI はそれを通して文言化する。新 variant 追加にコンパイルで気づける。
- エラー表示の指針(段階的に実装): `BadRequest` → フォームのフィールドエラー / `Conflict`・`Internal` → トースト / `NotFound` → 文脈に応じ空状態 or エラー / `Unauthorized` → 再認証(token 破棄 → authorize へ)。**`Unauthorized` 起点の再認証導線は配線済み**: ViewModel が `Unauthorized` を受けると `RpcError.requiresReauth()`(`core/rpc/RpcErrors.kt`)で判定し `ReauthController`(`core/auth/ReauthController.kt`)へシグナルを emit し、`App.kt` の `LaunchedEffect` がそれを `collectLatest` で購読して `rpc.close()` → authorize へ redirect する。失効間際トークンは `loadValidToken()` が先回りで redirect する。
- **nullable 戻り値原則禁止**(横断ルール `error-handling.md` 踏襲)。不在は `RpcError` / sealed で表す。frontend の公開関数も `T?` を安易に返さない。
- 登録状態判定: 全 RPC は単一エンドポイント `/api/rpc` に相乗りし、有効 JWT なら未登録でも接続できる。boot は `SessionRpcService.whoami()` の `SessionStatus`(`Registered(resident)` / `Unregistered`)で home / onboarding を分岐する(例外を制御フローに使わない)。通信失敗等の例外は `AuthState.Failed` に倒す。

## Why

RpcClientProvider 集約で transport/トークン載せ替えを 1 箇所に閉じ込める。`errorText` の `else` なし `when` で API エラー語彙の変化にコンパイル時追従。

> ViewModel 側のエラーハンドリング共通化(現状 7 VM にコピペの `handleFailure`)はフェーズ 4-4 で `ReauthController` + `ToastController` 受けの共通ヘルパーに集約予定。完了後に本ルールへパターンを追記する。

## 関連

- rule: [error-handling](error-handling.md)(横断・nullable 禁止)/ [frontend-architecture](frontend-architecture.md)
- backend: `backend/api/.../configuration/auth/`(WS subprotocol / JWT 検証)
