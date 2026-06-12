---
paths:
  - "domain/**/*.kt"
---

# Immutable Construction

集約 / エンティティの不変更新は `data class` の `.copy()` を使わず、**明示的にコンストラクタで新インスタンスを生成する**。

## Rule

- 集約ルート・エンティティの状態を更新するメソッドは、新しい状態を `.copy(...)` ではなく **コンストラクタ呼び出しで明示的に組み立てて** 返す
- VO(`@JvmInline value class`)は単一フィールドのため対象外
- 集約ルートだけでなく、集約内の**小さなエンティティ**(`HouseholdMember.withRole` 等)の不変更新も同ルールに服する。`.copy()` でなくコンストラクタで明示構築する

## Why

- `.copy()` はフィールド追加時に古い値を暗黙に引き継ぎ、全項目を意図して設定する保証が無い(将来の不変条件漏れの温床)
- 明示的構築なら、フィールドが増減しても新しい状態を毎回意識的に組み立てることになり、設定漏れがコンパイルエラーとして顕在化する

## How to apply

### ✅ コンストラクタで明示的に構築

```kotlin
fun join(resident: Resident, grantedRole: HouseholdMemberRole): Household =
    Household(id, profile, members.add(HouseholdMember(resident, grantedRole)))

fun rename(name: HouseholdName, by: ResidentId): Household {
    requireCapability(by, HouseholdCapability.世帯管理)
    return Household(id, Profile(name), members)
}
```

### ❌ copy() による更新

```kotlin
fun join(resident: Resident, grantedRole: HouseholdMemberRole): Household =
    copy(members = members.add(HouseholdMember(resident, grantedRole)))  // ← フィールド追加時に古い値を暗黙に引き継ぐ
```

## 関連

- rule: [domain-guideline](domain-guideline.md) — リッチドメイン 7 原則(不変更新)
