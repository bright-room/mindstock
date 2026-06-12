---
paths:
  - "frontend/**/*.kt"
---

# Frontend RPC and Error Handling

frontend からの RPC 呼び出しとエラー処理の規約。

## Rule

- RPC は `RpcClientProvider` 経由でのみ開く。WS subprotocol でトークンを運ぶ(`mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` を別エントリで append)。
- Repository が `RpcResult<T, RpcError>` を受け、`toOutcome()` で `RpcOutcome`(成功 / 失敗)に変換して ViewModel へ返す。`RpcError` の variant 網羅(`Unauthorized`/`NotFound`/`BadRequest`/`Conflict`/`Internal`)は `core/rpc/RpcErrors.kt` の `errorText(error: RpcError): UiText`(`else` なしの `when`)に集約し、ViewModel/UI はそれを通して文言化する。新 variant 追加にコンパイルで気づける。
- エラー表示の指針(段階的に実装): `BadRequest` → フォームのフィールドエラー / `Conflict`・`Internal` → トースト / `NotFound` → 文脈に応じ空状態 or エラー / `Unauthorized` → 再認証(token 破棄 → authorize へ)。**`Unauthorized` 起点の再認証導線は P6-1 で配線**(P6-0 時点では `loadValidToken()` が失効間際トークンで authorize へ先回り redirect する。`RpcError.requiresReauth()` / `RpcClientProvider.closeAll()` はその配線用に用意済み)。
- **nullable 戻り値原則禁止**(横断ルール `error-handling.md` 踏襲)。不在は `RpcError` / sealed で表す。frontend の公開関数も `T?` を安易に返さない。
- 登録状態判定: 全 RPC は単一エンドポイント `/api/rpc` に相乗りし、有効 JWT なら未登録でも接続できる。boot は `SessionRpcService.whoami()` の `SessionStatus`(`Registered(resident)` / `Unregistered`)で home / onboarding を分岐する(例外を制御フローに使わない)。通信失敗等の例外は `AuthState.Failed` に倒す。

## Why

RpcClientProvider 集約で transport/トークン載せ替えを 1 箇所に閉じ込める。`errorText` の `else` なし `when` で API エラー語彙の変化にコンパイル時追従。

## 関連

- rule: [error-handling](error-handling.md)(横断・nullable 禁止)/ [frontend-architecture](frontend-architecture.md)
- backend: `backend/api/.../configuration/auth/`(WS subprotocol / JWT 検証)
