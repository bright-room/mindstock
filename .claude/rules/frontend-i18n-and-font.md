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
- **非 Composable 層の暫定例外**: ViewModel / Repository などの非 Composable 層で生成するメッセージ文言(例: `RpcErrors.kt` の `userMessageOf`)は、`stringResource` を呼べないため、P6-1 以降で UI 側が resource に解決する設計を導入するまで、文言 literal を暫定で持ってよい。

## Why

文言を resource に集約すれば多言語化が差分追加で済み、レビューも文言単位で完結する。

## 関連

- rule: [frontend-designsystem](frontend-designsystem.md)
