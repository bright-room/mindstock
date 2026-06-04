---
paths:
  - "frontend/**/*.kt"
---

# Frontend RPC and Error Handling

frontend からの RPC 呼び出しとエラー処理の規約。

## Rule

- RPC は `RpcClientProvider` 経由でのみ開く。WS subprotocol でトークンを運ぶ(`mindstock.v1` と `mindstock.bearer.<base64url(jwt)>` を別エントリで append)。
- Repository が `RpcResult<T, RpcError>` を受け、`RpcOutcome`(成功 / 失敗)に変換して ViewModel へ返す。ViewModel は `RpcError` の variant を `when` で **網羅** する(`Unauthorized`/`NotFound`/`BadRequest`/`Conflict`/`Internal`)。新 variant 追加にコンパイルで気づける。
- エラー表示: `Unauthorized` → 再認証(token 破棄して authorize へ)/ `BadRequest` → フォームのフィールドエラー / `Conflict`・`Internal` → トースト / `NotFound` → 文脈に応じ空状態 or エラー。
- **nullable 戻り値原則禁止**(横断ルール `error-handling.md` 踏襲)。不在は `RpcError` / sealed で表す。frontend の公開関数も `T?` を安易に返さない。
- 登録状態判定: `/resident` は登録済み必須ルートのため、未登録ユーザの `me()` は WS ハンドシェイクで拒否され **例外を throw** する(`RpcResult.Err` にならない)。boot はこの例外を捕捉して未登録 → オンボーディングへ倒す。

## Why

RpcClientProvider 集約で transport/トークン/再接続を 1 箇所に閉じ込める。`when` 網羅で API エラー語彙の変化に追従。

## 関連

- rule: [error-handling](error-handling.md)(横断・nullable 禁止)/ [frontend-architecture](frontend-architecture.md)
- backend: `backend/api/.../configuration/auth/`(WS subprotocol / JWT 検証)
