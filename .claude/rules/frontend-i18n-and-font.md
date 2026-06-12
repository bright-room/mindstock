---
paths:
  - "frontend/**/*.kt"
---

# Frontend i18n and Font

文言とフォントの規約。

## Rule

- **UI(Composable)層のユーザ向け文言をコードに直書きしない**。Compose Resources の string resources(`commonMain/composeResources/values/strings.xml`、まず `ja`)に置き、`stringResource(Res.string.xxx)` で参照する。将来 `values-<lang>/strings.xml` を足すだけで多言語化。
- フォントは `commonMain/composeResources/font` の NotoSansJP(9 ウェイト)を `org.jetbrains.compose.resources.Font` で `FontFamily` 化し、Material3 `Typography` に適用(`designsystem/theme/Typography.kt`)。
- 例外: ログ / 例外メッセージ / 識別子など非ユーザ向け文字列は対象外。
- **非 Composable 層は `UiText` で文言を表す**: ViewModel / Repository などは `stringResource` を呼べないため、`UiText`(`core/ui/UiText.kt`、`StringResource` + args を保持する data class)を返し、Composable 側で `UiText` を解決して描画する。RPC エラーは `errorText(error: RpcError): UiText`(`core/rpc/RpcErrors.kt`)で `UiText` 化する。
- 例外: `RpcError.Internal("...")` 等の英語識別子・ログ・例外メッセージは非ユーザ向けなので strings 対象外。

## Why

文言を resource に集約すれば多言語化が差分追加で済み、レビューも文言単位で完結する。

## 関連

- rule: [frontend-designsystem](frontend-designsystem.md)
