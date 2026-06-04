---
paths:
  - "frontend/**/*.kt"
---

# Frontend KMP Structure

将来 android / ios / desktop(JVM) ターゲットを足せる構成を保つための規律。

## Rule

- 移植可能なコード(UI・ViewModel・テーマ・デザイン部品・RPC ラッパ・認証ロジック本体)は **`commonMain`** に置く。
- platform 依存は **`expect/actual` でのみ**逃がす。現状で actual が要るもの: PKCE の乱数/SHA-256/base64url(`secureRandomBytes`/`sha256`/`base64UrlNoPad`)、`SessionStorage`、ブラウザ遷移/コールバック取得(`BrowserNav`)。
- **落とし穴(明記)**: 現ターゲットは `js` と `wasmJs` の **web 2 種のみ**。両方 web なので `commonMain` に `kotlinx.browser.window` 等の web 前提を書いてもコンパイルが通る。これは移植性の保証にならない。web 固有 API を `commonMain` に直書きしない — 必ず `expect/actual` か platform source set へ。

## Why

将来ターゲット追加を「ターゲット + actual を足す」追加作業に留め、書き直しを避ける。web2種だけでは型検査が移植性を担保しないため、`expect/actual` が唯一の実効境界。

## How to apply

✅ `internal expect fun secureRandomBytes(n: Int): ByteArray` を common に、`actual` を jsMain/wasmJsMain に。
❌ `commonMain` の Composable から `kotlinx.browser.window.location` を直接参照。

## 関連

- rule: [frontend-architecture](frontend-architecture.md)
- 検証は `./gradlew :frontend:compileKotlinWasmJs`(フルビルドは OOM)
