---
paths:
  - "frontend/**/*.kt"
---

# Frontend i18n and Font

文言とフォントの規約。

## Rule

- **ユーザ向け文言をコードに直書きしない**。Compose Resources の string resources(`commonMain/composeResources/values/strings.xml`、まず `ja`)に置き、`stringResource(Res.string.xxx)` で参照する。将来 `values-<lang>/strings.xml` を足すだけで多言語化。
- フォントは `commonMain/composeResources/font` の NotoSansJP(9 ウェイト)を `org.jetbrains.compose.resources.Font` で `FontFamily` 化し、Material3 `Typography` に適用(`designsystem/theme/Typography.kt`)。
- 例外: ログ / 例外メッセージ / 識別子など非ユーザ向け文字列は対象外。

## Why

文言を resource に集約すれば多言語化が差分追加で済み、レビューも文言単位で完結する。

## 関連

- rule: [frontend-designsystem](frontend-designsystem.md)
