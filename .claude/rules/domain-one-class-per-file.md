---
paths:
  - "domain/**/*.kt"
---

# One Class Per File

1 ファイル = 1 トップレベル型。独立した型(集約・エンティティ・VO・区分・read-model 等)は同一ファイルに同居させず、型ごとにファイルを分ける。

## Rule

- 1 つの `.kt` ファイルにトップレベルの型は 1 つだけ(`data class` / `enum class` / `value class` / `object` / `sealed interface` 等)。
- **sealed 階層は例外**: sealed interface とその variant は「1 つの概念単位」として同一ファイルに置く(variant を sealed の中にネストする)。トップレベルに variant を並べない。
- `companion object`・同一型に属する nested helper は同居してよい。
- ファイル名は含まれるトップレベル型名と一致させる。

## Why

- 型ごとにファイルが分かれていれば、変更差分・レビュー・grep が型単位で完結し、見通しが良い。
- sealed 階層だけは網羅すべき variant を 1 画面で把握できるほうが正しさを確認しやすいため、ネストで 1 ファイルにまとめる。

## How to apply

### ✅ sealed 階層は variant をネストして 1 ファイル

```kotlin
sealed interface StockMovement {
    data class Replenishment(...) : StockMovement
    data class Consumption(...) : StockMovement
    data class Correction(...) : StockMovement
}
```

### ❌ 独立した型の同居

```kotlin
// ShoppingList.kt に 2 型 — NG。ShoppingEntry.kt と ShoppingList.kt に分ける
data class ShoppingEntry(...)
data class ShoppingList(...)
```
