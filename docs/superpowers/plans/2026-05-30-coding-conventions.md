# Coding Conventions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Claude 向けのコーディング規約を `CLAUDE.md` + `.claude/rules/*.md` + `.claude/settings.json` (hooks) として整備し、編集対象に応じて自動ロード・自動フォーマットされる状態を作る。

**Architecture:** ルートに薄い `CLAUDE.md` を新規作成し、規約本体はフラットな `.claude/rules/` 配下の 4 ファイルに `paths` frontmatter 付きで配置する。Spotless は `.claude/settings.json` の `PostToolUse` hook + `.claude/hooks/format-kotlin.sh` で編集後に必ず走らせる。

**Tech Stack:** Markdown / JSON / bash / Gradle Spotless plugin(`com.diffplug.spotless`)

**Spec:** `docs/superpowers/specs/2026-05-30-coding-conventions-design.md`

---

## 前提: リポジトリの実構成(plan 作成時に確認済)

```
mindstock/
├── domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/{exception,model}/...
├── backend/
│   ├── api/src/main/kotlin/net/brightroom/mindstock/{configuration,presentation}/...
│   ├── application/...
│   └── infrastructure/...
├── rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt
├── shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/serialization/Json.kt
└── frontend/...
```

- `Transaction.kt` は `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`
- Spotless plugin は build-logic 経由で `com.diffplug.spotless` を提供(`net.brightroom.mindstock.spotless` convention)。`./gradlew spotlessApply` でフォーマット実行可
- 全プロジェクトに対する `spotlessApply` の対象を絞るプロパティ名はプロジェクト規約に依存。本 plan では Spotless 公式の `-PspotlessIdeHook=<path>` または all-projects 実行(規模が小さければ十分速い)のどちらかを採用する。実装で計測して選ぶ

---

## Task 1: 現行コードと spec の整合確認

**目的:** spec §7 の確認項目を grep して、規約に書く内容と実態が一致しているか確認する。乖離があれば spec 側を訂正してから rule ファイル作成に進む。

**Files:**
- 参照のみ: 各実装ファイル(`grep`/`find`)
- 修正可能性: `docs/superpowers/specs/2026-05-30-coding-conventions-design.md`

- [ ] **Step 1: domain VO の現状確認**

実行:
```bash
find domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model -name "*.kt" \
  | xargs grep -l "@JvmInline value class" \
  | head -5
```
期待: ProductId / UserId / Quantity 等のファイルが出る。1 ファイル `cat` で読み、以下を確認:
- `@Serializable @JvmInline value class` 形式か
- バッキングフィールドが `private val value: ...` か
- `init { require(...) }` で IAE を投げているか
- `internal operator fun invoke(): T = value` があるか
- `override fun toString(): String = value.toString()` があるか

- [ ] **Step 2: ファーストクラスコレクションの現状確認**

実行:
```bash
grep -n "val list\|asList\|fun size" \
  domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Products.kt \
  domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt
```
期待: `val list: List<...>` が見え、`asList()` / `fun size()` は無いか、ある場合はメモする(refactor/plan-c-phase4 中なので未完の可能性)。

- [ ] **Step 3: Transaction.kt の `tx()` シグネチャを確認**

実行:
```bash
cat backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt
```
期待: `tx(database) { ... }` ヘルパーが定義され、内部で `supervisorScope { newSuspendedTransaction(db) { ... } }` 相当の構造を持つ。IAE / `ResourceNotFoundException` 等のドメイン例外を catch して `RpcError` に翻訳する処理がある。

- [ ] **Step 4: KrpcJson / CustomJson の現状確認**

実行:
```bash
cat shared/src/commonMain/kotlin/net/brightroom/mindstock/extensions/kotlinx/serialization/Json.kt
```
期待: `KrpcJson`(`ClassDiscriminatorMode.POLYMORPHIC`) と `CustomJson`(`NONE` 等) が定義され、両者の使い分けが明確。

- [ ] **Step 5: RpcError の現状確認**

実行:
```bash
cat rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt
```
期待: sealed interface で `Unauthorized` / `NotFound` / `BadRequest` / `Conflict` / `Internal` を持つ。

- [ ] **Step 6: 乖離があれば spec を訂正**

各 Step の期待と異なる場合:
- 実装が正の前提で spec を更新(memory `before-plan-3` 原則)
- 訂正 commit を打ってから Task 2 に進む

実行:
```bash
git diff docs/superpowers/specs/2026-05-30-coding-conventions-design.md
```
期待: 訂正があれば diff が出る。無ければ何も無し。

- [ ] **Step 7: 乖離が無ければ何もせず先に進む**

訂正不要なら次の task へ。確認結果は plan の memo として頭に残すだけで commit しない。

---

## Task 2: CLAUDE.md を新規作成

**Files:**
- Create: `CLAUDE.md`

- [ ] **Step 1: CLAUDE.md を作成**

ファイル全文:

````markdown
# mindstock

家庭の在庫管理 SaaS。Kotlin Multiplatform でバックエンド(JVM)とフロントエンド(Compose Multiplatform / Kotlin/Wasm)を共有モジュール経由で繋ぐ。

## 技術スタック

Kotlin Multiplatform / Ktor / kotlinx-rpc / Exposed / Compose Multiplatform(Kotlin/Wasm) / PostgreSQL / Zitadel OIDC

(バージョンは `gradle/libs.versions.toml` を参照。本ファイルには記載しない)

## モジュール構成

- `:domain` — 純粋なドメインモデル(集約・VO・例外)。KMP common。外部依存は kotlin stdlib / kotlinx-serialization / kotlinx-datetime のみ
- `:rpc` — RPC interface 定義(`@Rpc` service interface、`RpcError`、`RpcResult`)。KMP common
- `:shared` — frontend と backend 双方で使う薄い共通ロジック(`KrpcJson` / `CustomJson` 等)。KMP common + wasmJs
- `:backend:core` — application 層 interface(Repository / Service interface)。JVM
- `:backend:application:api` — Ktor 起動モジュール(`configuration/`, `presentation/rpc/`)。JVM
- `:backend:infrastructure` — Exposed DataSource 実装、Repository 実装。JVM
- `:frontend` — Compose Multiplatform / Kotlin/Wasm の UI

## 主要コマンド

- Backend に閉じた full build(frontend WasmJs の OOM 回避): `./gradlew :domain:build :backend:core:build :backend:api:build :rpc:build`
- 単体テスト: `./gradlew test`
- 統合テスト: `./gradlew :backend:api:integrationTest`
- Frontend dev server: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
- フォーマット適用: `./gradlew spotlessApply`(編集後の hook が自動実行する。手動は通常不要)

## 絶対に守る原則

詳細は `.claude/rules/*.md` に置かれ、編集対象に応じて自動ロードされる。ここでは絶対に外せない 4 原則だけ:

1. **層責務と依存方向**: presentation → application ← infrastructure / domain は横断 / 逆方向の依存禁止
2. **nullable 戻り値原則禁止**: 公開 API の `T?` は原則禁止。「不在」は例外 or sealed 型で表現する。導入が必要なら事前にユーザ承認を得る
3. **リッチドメイン**: ビジネスロジックは domain に。Service は薄い orchestration。集約は object graph(子を ID で持たない)
4. **`@Rpc` annotation 必須**: RPC service interface に `@kotlinx.rpc.annotations.Rpc` を必ず付ける。`RemoteService` 継承は使わない

フォーマット(インデント、import 並び順、空行)は `.claude/settings.json` の `PostToolUse` hook が編集後に Spotless で自動修復するため、規約には書かない。
````

実行:
```bash
ls -la CLAUDE.md
```
期待: ファイルが存在し、内容が上記と一致。

- [ ] **Step 2: CLAUDE.md が `/memory` で読み込まれることを確認**

実行(現セッションでなく、次の Claude Code セッション内で):
```
/memory
```
期待: CLAUDE.md が "loaded files" として表示される。

(本 step は手動確認。plan 実行時に Claude Code を立ち上げ直す余裕がなければ Task 9 に統合してまとめて検証する)

- [ ] **Step 3: Commit**

実行:
```bash
git add CLAUDE.md
git commit -m "docs: add CLAUDE.md as project entry point for Claude

プロジェクト概要・技術スタック(バージョン除く)・モジュール構成・主要コマンド・
絶対守る 4 原則。詳細規約は .claude/rules/*.md に分割し paths frontmatter で
編集対象に応じて自動ロードする方針。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```
期待: 1 file changed (CLAUDE.md 新規)。

---

## Task 3: `.claude/rules/software-architecture.md` を新規作成

**Files:**
- Create: `.claude/rules/software-architecture.md`

- [ ] **Step 1: `.claude/rules/` ディレクトリを作成**

実行:
```bash
mkdir -p .claude/rules
```
期待: ディレクトリが作成される(既存なら何も起こらない)。

- [ ] **Step 2: software-architecture.md を作成**

ファイル全文:

````markdown
---
paths:
  - "domain/**/*.kt"
  - "backend/**/*.kt"
---

# Software Architecture

mindstock の層責務と依存方向。Controller / Service / Repository / DataSource / Handler の責務分担と命名対称。

## Rule

### 層と依存方向

```
        presentation (rpc Controller, RpcError, MindstockSession)
              │
              ▼
        application (Service, Repository interface)
              ▲
              │
        infrastructure (DataSource = Repository 実装)

  domain (model, value object, exception) ← 全層が依存可能・横断
  configuration (Ktor plugin / DI / routing / tx ヘルパー)
    ← presentation か infrastructure の片方向 glue
```

- application / infrastructure は presentation に依存禁止
  - Service / DataSource は `RpcError` / `RpcResult` / `MindstockSession` を import しない
- domain は全層に依存される
- configuration は片方向 glue。双方向の逃げ道を作らない

### Controller(presentation)

- **Repository を直接呼ばない**。集約取得・存在チェックも必ず Service 経由
- 例外(`ResourceNotFoundException` 等)を直接 catch しない。`tx()` ヘルパーが集約捕捉して `RpcError` に翻訳する
- 業務ロジックは Service / Domain に置き、Controller は薄く保つ

### Service(application)

- **ビジネスロジックを持たない薄い orchestration**
  - ✅ Repository から集約 fetch、Domain メソッドを呼ぶ、Repository に保存、呼び出し順序の制御
  - ❌ 条件分岐によるビジネス判定、状態遷移、計算ロジック(全部 Domain へ)
- 戻り値は **non-null の単一クラス**。無ければ事象に沿った例外を throw
- 一覧の場合は **空のファーストクラスコレクション**(`Products`, `Stocks` 等)を返す。`throw` しない、raw `List<T>` も公開しない

### Repository(application interface / infrastructure 実装)

- interface も実装も **non-null**
- 行が無ければ infrastructure 側で `ResourceNotFoundException` を throw
- 一覧 method は空のファーストクラスコレクションを返す
- Reader / Writer 分離: `<Ctx>Repository`(read) / `<Ctx>RegisterRepository`(write)
- Hydration ロジックは `<Aggregate>Hydration.kt` の internal extension に集約(命名: `ResultRow.to<Aggregate>()`)

### DataSource(infrastructure)

- 実装内では `transaction {}` を書かない(plugin / `tx()` で境界管理)
- INSERT 後は RETURNING 相当(`insertAndGetId` + hydration)で読み戻して domain object を返す

### パッケージ境界の判断軸

- ファイル数で切らない(5 件ルール等は不採用)
- サブパッケージを切る条件は **概念区別** が成立した時のみ:
  - 役割が違う(集約状態 vs 派生 read-model)
  - 集約内サブエンティティ群が独立した塊
  - 依存方向を遮断したい
- 例: `stock/` は `Stock` 集約 / `StockMovement` fact / `ShoppingList` read-model の 3 概念が並ぶので `movement/` `shopping/` でサブパッケージ化

### 命名対称

- Controller / Service / Repository / DataSource / Handler は **同じコンテキスト名のプレフィックス** を持つ
  - 例: `StockController` / `StockService` + `StockRegisterService` / `StockRepository` + `StockRegisterRepository` / `StockDataSource` + `StockRegisterDataSource`

## Why

- 関心の分離: ビジネスロジックは domain、orchestration は application、I/O は infrastructure、wire/HTTP/UI は presentation
- 逆向きの依存を許すと、テスト容易性とモジュール独立性が崩れる
- Controller が Repository を直接呼ぶと、業務ロジックの組み立て責務が Controller に漏れ出す
- Service にロジックを書くと貧血モデルになり、ロジックが Service と Domain に二重化される
- nullable 戻り値は呼び出し側に「null チェック + 別エラー化」の責務を漏らす。例外で表現すれば Service は「正常時は確実に値を返す契約」を持てる

## How to apply

### ✅ Service が行ってよいこと

```kotlin
class StockRegisterService(
    private val stockRepository: StockRepository,
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(stockId: StockId, quantity: Quantity, note: Note, actor: UserId) {
        val stock = stockRepository.findById(stockId)  // 不在は ResourceNotFoundException
        val movement = stock.replenish(quantity, note, actor)  // domain method
        stockRegisterRepository.appendMovement(stockId, movement)
    }
}
```

### ❌ Controller が Repository を直接呼ぶ

```kotlin
// アンチパターン
class StockController(
    private val stockRepository: StockRepository,  // ← NG
) {
    suspend fun getStock(id: StockId): RpcResult<Stock, RpcError> {
        val stock = stockRepository.findById(id)  // ← Service 経由にする
        return RpcResult.Ok(stock)
    }
}
```

### ❌ Service にビジネス判定

```kotlin
// アンチパターン
fun replenish(stockId: StockId, qty: Quantity) {
    val stock = stockRepository.findById(stockId)
    if (stock.product.archived) {  // ← Domain メソッドにする
        throw IllegalStateException("archived")
    }
    // ...
}

// 正解: stock.replenish(...) 内で archived チェックを行う
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [domain-guideline](domain-guideline.md) — 各層の domain 部分の詳細
- rule: [error-handling](error-handling.md) — 例外と nullable の方針
- rule: [rpc-and-transactions](rpc-and-transactions.md) — presentation の詳細
````

実行:
```bash
ls -la .claude/rules/software-architecture.md
```
期待: ファイル存在。

- [ ] **Step 3: Commit**

実行:
```bash
git add .claude/rules/software-architecture.md
git commit -m "docs: add software-architecture rule

domain/**/*.kt と backend/**/*.kt スコープで層責務・依存方向・各層の責務分担と
命名対称を規約化。Controller は Repository を直接呼ばない、Service は薄く、
Repository は non-null、パッケージ境界は概念区別で切る方針を明文化。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `.claude/rules/domain-guideline.md` を新規作成

**Files:**
- Create: `.claude/rules/domain-guideline.md`

- [ ] **Step 1: ファイル作成**

ファイル全文:

````markdown
---
paths:
  - "domain/**/*.kt"
---

# Domain Guideline

mindstock のドメインモデルはリッチドメインモデル指向。テーブル定義の写し(貧血モデル)は避ける。集約は object graph を持ち、ロジックは domain メソッドとして表現する。

## Rule

### domain で許容する外部ライブラリ

以下のみ許容。これ以外は domain に持ち込まない:

- `kotlin` stdlib(`kotlin.time.Clock` 含む)
- `kotlinx-serialization`(`@Serializable`, `@SerialName`)
- `kotlinx-datetime`

新規依存が必要に思えた時は「外して複雑化しないか / 取り込んでも品質を保てるか」で個別判断。ユーザに事前確認する。

### リッチドメイン 7 原則

1. **behavior-rich**: 集約に「なぜ存在するか / 何ができるか」を言語化できないなら貧血。メソッドや composition で責務を持たせるか、概念ごと削る
2. **集約は object graph**: 子を `XxxId` で持たず、子モデルそのものを保持。例: `Household.members: HouseholdMembers`(`List<HouseholdMember>` を内包)、`HouseholdMember.profile: Profile`(`userId` だけではない)。Repository が JOIN で hydrate する
3. **composition 優先**: 集約間で「ID 参照」を取る前に composition を検討。同一トランザクション/文脈なら composition、完全に別文脈なら ID 参照可
4. **`id` は private**: ドメインロジック内で `a.id == b.id` 比較を書かない。Equality は本質属性(`zitadelSub`, `name` 等)で。永続化用には `internal` getter で Repository に渡す
5. **`createdAt` は集約ルートから削除**(インフラメタ扱い)。例外: ユーザー入力の出来事時刻 `occurredAt` はドメイン概念なので残す
6. **不変更新**: 操作メソッドは新インスタンスを返す(`renameTo(newName): User`、`archive(): Product`)。可変 setter は持たない
7. **fact クラスは domain から消える**: append-only な履歴行は Repository 内部の永続化単位として残し、ドメインでは「現在状態」を持つ集約ルートに集約。履歴閲覧は別 read model

### User 表現は 3 分解

- `User` という名前のクラスは作らない
- `UserId` を identity の主役にする
- composition は `Profile(userId, displayName)` 経由
  - 例: `HouseholdMember(profile: Profile, role: HouseholdMemberRole)`、`sealed interface StockMovement { val actor: Profile }`
- 外部認証文脈は `AuthIdentity(provider, subject)`(`user/auth/`)。メンバー一覧/履歴 API に漏らさない

### Value Object 規約

- `@Serializable @JvmInline value class` 形式
- バッキングフィールドは統一して `value`、可視性 `private`
- バリデーションは `init { require(...) }` で `IllegalArgumentException` を throw(stdlib)
- `internal operator fun invoke(): T = value`(内部/infrastructure から値を取り出す)
- `override fun toString(): String = value.toString()`
- ファクトリ関数(`Quantity.of(...)`)は持たない。`init` で完結する
- 例外: ID 発番のみ `XxxId.create()` companion で UUIDv7 を生成

```kotlin
@Serializable
@JvmInline
value class Quantity(private val value: Int) {
    init { require(value > 0) { "Quantity must be positive: $value" } }
    internal operator fun invoke(): Int = value
    override fun toString(): String = value.toString()
}
```

### ID 型規約

- UUID 系(集約ルート): `value class ProductId(private val value: Uuid)`、バリデーション無し
- Long 系(履歴テーブル): `value class StockReplenishmentId(private val value: Long)`、`init` で非負強制
- ID 発番は呼び出し側責任。`UserId.create()` で UUIDv7 生成

### ファーストクラスコレクション(First-class Collection)

`Products` / `Stocks` / `HouseholdMembers` / `StockMovements` / `CatalogItems` 等。

```kotlin
@Serializable
data class Stocks(val list: List<Stock>) {
    fun needsReplenishment(): Stocks = Stocks(list.filter { it.belowThreshold() })
}
```

- `val list: List<T>` を public で持つ(直接アクセス OK、`.list` / `.list.size` で使う)
- `asList()` / `fun size()` メソッドは **持たない**(`.list` で十分)
- `@Serializable` を付ける
- ドメイン固有操作のみメソッド化(`owner()`, `activeOnly()`, `netQuantity()`, `Stocks.needsReplenishment()` 等)

### 時間

- VO 内で `kotlin.time.Clock.System.now()` の呼び出し許容(`OccurredAt` 等)
- `OccurredAt` は `init` で「未来日不許容」を検証

```kotlin
@Serializable
@JvmInline
value class OccurredAt(private val value: Instant) {
    init {
        val now = Clock.System.now()
        require(value <= now) { "occurredAt must not be in the future: $value > $now" }
    }
    internal operator fun invoke(): Instant = value
    override fun toString(): String = value.toString()
}
```

### sealed interface でポリモフィズム

- `StockMovement` は sealed interface で `Replenishment` / `Consumption` を subclass 化。`type` フィールドは持たない(sealed 網羅判別で型識別)
- `MinimumStock` は sealed interface で `Set(@JvmInline value class)` / `NotSet`。null で未設定を表現しない
- sealed interface の subclass に `@JvmInline value class` 使用可(Kotlin 2.x)
- polymorphic serialization は kotlinx-serialization 標準(type discriminator + FQN)。カスタム discriminator は導入しない

## Why

- 貧血モデルは「テーブル定義の写し」になり、ロジックが Service に流出する
- `id` を public にして `a.id == b.id` で比較すると、識別の意味が漏れ、本質属性での equality が書かれなくなる
- `createdAt` を集約に持たせると、永続化前の「createdAt = null」状態を扱わないといけなくなる。出来事時刻 `occurredAt` はドメイン概念だが、永続化メタは別物
- VO のファクトリ関数(`Quantity.of(...)`)を作ると、呼び出し側が `Quantity(123)` も使えてしまい二重化する。`init` で完結させればコンストラクタ一本道
- ファーストクラスコレクションに `size()` メソッドを持たせると `list.size` との二重化が起きる。`val list` 直公開で十分
- `User` 集約を作ると、`AuthIdentity`(OIDC sub)が世帯メンバー一覧 API に漏れる。3 分解すれば identity と表示と認証文脈が明確に分かれる

## How to apply

### ✅ リッチドメイン

```kotlin
data class Stock(
    private val id: StockId,
    val product: Product,
    val movements: StockMovements,
) {
    fun replenish(quantity: Quantity, note: Note, actor: Profile): Stock {
        require(!product.archived) { "cannot replenish archived product: ${product.name}" }
        return copy(movements = movements + StockMovement.Replenishment(quantity, note, actor, OccurredAt(Clock.System.now())))
    }

    fun currentQuantity(): Quantity = movements.netQuantity()

    internal fun id(): StockId = id  // Repository に渡す用
}
```

### ❌ 貧血モデル

```kotlin
// アンチパターン
data class Stock(
    val id: StockId,      // ← public
    val productId: ProductId,  // ← ID 参照のみ、composition していない
    val quantity: Int,    // ← VO ではなく primitive
    val createdAt: Instant,  // ← インフラメタが集約に
)
// → Service 側で if (stock.quantity < threshold) ... と判定するハメに
```

### ❌ User 集約

```kotlin
// アンチパターン
data class User(
    val id: UserId,
    val displayName: DisplayName,
    val authIdentity: AuthIdentity,  // ← メンバー一覧 API に漏れる
)
```

### ✅ User 3 分解

```kotlin
@Serializable @JvmInline
value class UserId(private val value: Uuid) { ... }

@Serializable
data class Profile(val userId: UserId, val displayName: DisplayName)

@Serializable
data class AuthIdentity(val provider: AuthProvider, val subject: AuthSubject)
// 表示文脈は Profile、認証文脈は AuthIdentity、identity は UserId
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [software-architecture](software-architecture.md) — 層全体の依存方向
- rule: [error-handling](error-handling.md) — IAE / ResourceNotFoundException の扱い
````

- [ ] **Step 2: Commit**

実行:
```bash
git add .claude/rules/domain-guideline.md
git commit -m "docs: add domain-guideline rule

domain/**/*.kt スコープでリッチドメイン 7 原則・User 3 分解(UserId/Profile/
AuthIdentity)・VO 規約(@Serializable @JvmInline value class + init require +
internal invoke + toString)・ファーストクラスコレクション(val list 公開)・
sealed interface ポリモフィズムを規約化。domain で許容する外部ライブラリも
明示(kotlin stdlib / kotlinx-serialization / kotlinx-datetime のみ)。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: `.claude/rules/error-handling.md` を新規作成

**Files:**
- Create: `.claude/rules/error-handling.md`

- [ ] **Step 1: ファイル作成**

ファイル全文:

````markdown
---
paths:
  - "**/*.kt"
---

# Error Handling

mindstock の例外設計と nullable 利用ルール。全層に適用。

## Rule

### nullable 戻り値原則禁止

- すべての公開 API(Repository / Service / Controller / Domain / Infrastructure)で **戻り値・パラメータの `T?` は原則禁止**
- 「不在」が意味を持つ状況は **例外 or sealed 型** で表現する
- 空 List は別概念。「件数 0 が正常」なら例外不要、**空のファーストクラスコレクション** を返せばよい
- どうしても nullable を返したい場合は **必ず事前にユーザに「なぜ nullable が必要か」を提示して承認を得る**。勝手に `T?` を導入しない

### 単一値の不在

`domain/exception/ResourceNotFoundException(reason: String)` を throw。

- Repository(infrastructure 実装)が、行が無ければこれを throw する
- Service は Repository をそのまま呼んで結果を返す(null チェック不要)
- Controller は catch しない(`tx()` ヘルパーが集約捕捉して `RpcError.NotFound` に翻訳)
- message には何が見つからなかったかを書く: `"household not found: $id"`

### Value Object の値域違反

stdlib `IllegalArgumentException`(`require(...)` で throw)。`sealed DomainException` 階層は廃止済み。

- Application / RPC 層は IAE を catch して `RpcError.BadRequest` に翻訳。`tx()` が一括で行う
- ドメイン例外はワイヤー越境しない(domain 例外型を `:rpc` から見えるようにしない)
- IAE では意味が歪む箇所(「アーカイブ済み商品の在庫変動」「OWNER が自分自身を revoke」等)のみ、その時に専用例外を定義する

### presentation 層への翻訳

`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt` の `tx()` ヘルパーが一括で行う。Controller / Service / DataSource は個別に catch を書かない。

## Why

- Nullable は「呼び出し側で必ず分岐すべき情報」を型から消す。例外 or sealed 型なら、不在の意味と理由が型シグネチャに現れて無視できなくなる
- `Service.findById(id): T?` は Repository の null をそのまま転送しているだけで application 層としての契約を放棄している
- `sealed DomainException` 階層は実質「IAE + reason 文字列」の薄いラッパーで、IAE 直接の方が stdlib に寄せられて簡潔
- 例外を一箇所(`tx()`)で翻訳すると、各 Controller に try/catch が散らからず、`RpcError` への mapping が単一の真実の場所になる

## How to apply

### ✅ 例外で不在を表現

```kotlin
// infrastructure
internal fun findById(id: StockId): Stock {
    return StockTable.selectAll()
        .where { StockTable.id eq id() }
        .singleOrNull()
        ?.toStock()
        ?: throw ResourceNotFoundException("stock not found: $id")
}

// Service は素通し
class StockService(private val repo: StockRepository) {
    fun get(id: StockId): Stock = repo.findById(id)  // 不在は ResourceNotFoundException
}

// Controller は catch しない(tx() が翻訳)
class StockController(...) {
    suspend fun getStock(id: StockId): RpcResult<Stock, RpcError> = tx(database) {
        RpcResult.Ok(stockService.get(id))
    }
}
```

### ✅ 空はファーストクラスコレクションで

```kotlin
fun findByHousehold(id: HouseholdId): Stocks =
    Stocks(StockTable.selectAll().where { ... }.map { it.toStock() })
// 空でも Stocks(emptyList()) を返す。throw しない、null を返さない
```

### ❌ nullable 戻り値

```kotlin
// アンチパターン
fun findById(id: StockId): Stock? = ...
// → 呼び出し側で stock ?: throw NotFoundException(...) が散らかる
```

### ❌ Service で再 catch

```kotlin
// アンチパターン
class StockService(private val repo: StockRepository) {
    fun get(id: StockId): Stock {
        return try {
            repo.findById(id)
        } catch (e: ResourceNotFoundException) {
            throw RpcError.NotFound(e.message)  // ← presentation 概念が application に漏れる
        }
    }
}
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [software-architecture](software-architecture.md) — 層責務との関係
- rule: [rpc-and-transactions](rpc-and-transactions.md) — `tx()` ヘルパーの詳細
````

- [ ] **Step 2: Commit**

実行:
```bash
git add .claude/rules/error-handling.md
git commit -m "docs: add error-handling rule

全 .kt スコープで nullable 戻り値原則禁止・ResourceNotFoundException(単一値の
不在)・IllegalArgumentException(VO 値域違反)・tx() による一括翻訳という
方針を規約化。sealed DomainException 廃止の経緯と、空はファーストクラス
コレクションで表現する原則も明記。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: `.claude/rules/rpc-and-transactions.md` を新規作成

**Files:**
- Create: `.claude/rules/rpc-and-transactions.md`

- [ ] **Step 1: ファイル作成**

ファイル全文:

````markdown
---
paths:
  - "backend/**/presentation/rpc/**/*.kt"
  - "backend/**/configuration/**/*.kt"
  - "rpc/**/*.kt"
---

# RPC and Transactions

kotlinx-rpc 0.10.x と Ktor WebSocket を組み合わせた RPC 層の規約。Json 分離・WS upgrade ガード・transaction 境界のハマりポイントを含む。

## Rule

### RPC service interface

- **`@kotlinx.rpc.annotations.Rpc` を必ず付ける**。`RemoteService` 継承は使わない(0.10.x で `@Deprecated(ERROR)`)
- service interface は `<Ctx>RpcService` suffix、実装(Controller)は `<Ctx>Controller`(`presentation.rpc.<ctx>` パッケージ)
- メソッド名は domain command 名そのまま(`Query` / `Command` suffix を付けない)
- 認証なし public service(`UserPublicRpcService` 等)は別 interface に分離

### Service 実装(Controller)の lifecycle

- **WebSocket 接続単位で 1 度だけ instantiate**(`registerService<T> { factory }` の factory は接続確立時に 1 回呼ばれる)
- factory は **非 suspend**。Ktor DI の `dependencies.resolve<T>()` は suspend なので、`RoutingConfiguration` で `val handler by dependencies` で **先に解決** し factory closure 内で参照する
- `ApplicationCall` は `this.applicationCall`(`configuration.auth.applicationCall` extension)で取得
- auth user 取得は constructor で `ApplicationCall` を受け、`by lazy { call.actor(userRepository) }` で接続単位 memoize

### Json 分離

- `Krpc` plugin は **`ClassDiscriminatorMode.POLYMORPHIC` の Json を要求**
  - `KrpcJson` を `shared/.../extensions/kotlinx/serialization/Json.kt` に定義
- HTTP の `ContentNegotiation` は **`CustomJson`(`NONE`)** を使う
- `RoutingConfiguration` の `install(Krpc) { serialization { json(KrpcJson) } }` と HTTP `install(ContentNegotiation) { json(CustomJson) }` を **使い分ける**

### WS upgrade ガード(ハマりポイント)

- **`CallLogging` の `runBlocking { call.receiveText() }` が WS upgrade フレームを盗み読む** → 静かに WS 接続が失敗する
  - 対処: `LoggingConfiguration` で `call.request.headers[HttpHeaders.Upgrade]?.contains("websocket") == true` をガード条件にする
- **`DoubleReceive` も WS upgrade と race**
  - 対処: `install(DoubleReceive) { excludeFromCache { call, _ -> isWebSocketUpgrade } }`

### Transaction 境界(ハマりポイント)

- `ExposedTransactionPlugin`(`ApplicationCallPipeline.Call` で intercept)は **WS upgrade 時 1 回しか張らない**。RPC method は確立済み WS の message として transaction の外で走る
- **各 Service Impl の method を `tx(database) { ... }` で包む** こと
- `tx` = **`supervisorScope { newSuspendedTransaction(db) { ... } }`**
  - `newSuspendedTransaction` の cancellation は Job tree を遡って Ktor scope を倒すため、supervisorScope なしだと server 例外が client に届かず testApplication が落ちる
- 場所: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`

### RPC 戻り値

- 全 RPC メソッドの戻り値は **`RpcResult<T, RpcError>`**
- `T` は **non-null**(`T?` は禁止)
- `RpcError` は `:rpc/RpcError.kt` の sealed interface:
  - `Unauthorized(reason)` / `NotFound(message)` / `BadRequest(field, reason)` / `Conflict(reason)` / `Internal(reason)`
- 例外メッセージ・型は安定 contract(Controller / `tx()` がパターンマッチする)。リネーム時は両側を同時に更新する

### RPC 引数

- **Request で集約を丸ごと受け取らない**。VO / ID のみを引数で受ける
  - 集約を丸ごとシリアライズ経由で渡すと、`init` バリデーション factory を迂回されて不変条件を破壊できてしまう
- domain = wire-format(中間 DTO / mapper を作らない)。改変時は backend / frontend 同時 deploy 前提
- 過剰露出フィールド(`User.authIdentity` 等)は **構造で解決**(`User` を 3 分解、`AuthIdentity` を別型にする)。`@Transient` で除外しない(暗黙の漏れを誘発する)

### Routing

- 認証レルムで rpc route を nest: `authenticate("user") { rpc("/api/v1/...") { ... } }`
- 中の `registerService<T> { ImplClass(...) }` で 1 service ずつ登録

## Why

- `@Rpc` annotation を強制することで、`RemoteService` 継承 → 0.10.x で ERROR の罠を避ける
- Json の `ClassDiscriminatorMode` を間違えると Krpc envelope を decode できず **静かに失敗する**(ログにも出にくい)ため、Json を 2 つに物理分離する
- WS upgrade フレームを CallLogging が消費すると、handshake は成立したように見えて以後の RPC frame が来ない・client が無限待ちになる
- `ExposedTransactionPlugin` の HTTP request intercept は WS では「upgrade 1 回」しか走らないため、RPC method ごとに tx を張り直さないと「コネクション無し」または「同一 tx 使い回し」になる
- `supervisorScope` なしの `newSuspendedTransaction` は失敗時に親 scope を巻き込んで Ktor server まで倒してしまう
- 集約を丸ごと RPC 引数にすると、`init` の検証を経由しない deserialize で不正値が入る

## How to apply

### ✅ Service interface

```kotlin
@Rpc
interface StockRpcService : RemoteService {  // ← RemoteService 継承「不可」。次のように書く:
}

@Rpc
interface StockRpcService {  // ← これが正
    suspend fun replenish(stockId: StockId, quantity: Quantity, note: Note): RpcResult<Unit, RpcError>
    suspend fun getStock(stockId: StockId): RpcResult<Stock, RpcError>
}
```

### ✅ Routing(factory は非 suspend)

```kotlin
fun Application.routingConfigure() {
    val stockService by dependencies   // ← suspend 解決を先取り
    val userRepository by dependencies

    routing {
        authenticate("user") {
            rpc("/api/v1/stock") {
                registerService<StockRpcService> { StockController(applicationCall, stockService, userRepository) }
            }
        }
    }
}
```

### ✅ Controller(method を `tx()` で包む)

```kotlin
class StockController(
    private val call: ApplicationCall,
    private val stockService: StockService,
    private val userRepository: UserRepository,
) : StockRpcService {
    private val actor by lazy { call.actor(userRepository) }

    override suspend fun replenish(stockId: StockId, quantity: Quantity, note: Note): RpcResult<Unit, RpcError> =
        tx(database) {
            stockService.replenish(stockId, quantity, note, actor.id)
            RpcResult.Ok(Unit)
        }
}
```

### ❌ Json を取り違える

```kotlin
// アンチパターン: Krpc に CustomJson を渡してしまう
install(Krpc) { serialization { json(CustomJson) } }  // ← envelope decode 失敗で静かに死ぬ
```

### ❌ Controller で個別 catch

```kotlin
// アンチパターン
override suspend fun getStock(id: StockId): RpcResult<Stock, RpcError> {
    return try {
        RpcResult.Ok(stockService.get(id))
    } catch (e: ResourceNotFoundException) {
        RpcResult.Err(RpcError.NotFound(e.message ?: "not found"))
    }
    // ← tx() が一括翻訳するので Controller で catch しない
}
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- spec(歴史): [docs/superpowers/specs/2026-05-25-rpc-layer-design.md](../../docs/superpowers/specs/2026-05-25-rpc-layer-design.md)
- rule: [software-architecture](software-architecture.md) — Controller の責務
- rule: [error-handling](error-handling.md) — 例外 → RpcError 翻訳の方針
````

- [ ] **Step 2: Commit**

実行:
```bash
git add .claude/rules/rpc-and-transactions.md
git commit -m "docs: add rpc-and-transactions rule

backend presentation/rpc + configuration + rpc/ スコープで @Rpc annotation 必須・
KrpcJson と CustomJson の分離・WS upgrade ガード(CallLogging / DoubleReceive)・
tx() ヘルパーの supervisorScope ラップ・RpcResult<T, RpcError> 戻り値・RPC 引数は
VO/ID のみという方針を規約化。kotlinx-rpc 0.10.x で踏んだハマりポイントを
編集対象パスで自然にロードされる場所に統合。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: `.claude/hooks/format-kotlin.sh` を作成

**Files:**
- Create: `.claude/hooks/format-kotlin.sh`

- [ ] **Step 1: hooks ディレクトリを作成**

実行:
```bash
mkdir -p .claude/hooks
```

- [ ] **Step 2: スクリプト作成**

ファイル全文:

```bash
#!/usr/bin/env bash
# .claude/hooks/format-kotlin.sh
#
# PostToolUse hook for Write/Edit/MultiEdit.
# Reads tool input JSON from stdin, extracts the edited file_path,
# and runs `./gradlew spotlessApply` scoped to that file if it is a Kotlin source.
#
# Exit codes:
#   0  - success (file formatted, or non-kotlin file skipped)
#   2  - blocking error reported back to Claude (Gradle unavailable / Spotless fatal)

set -euo pipefail

# Read entire stdin (Claude Code sends a single JSON object).
INPUT="$(cat)"

# Extract the file path Claude just touched.
FILE_PATH="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')"

# No file path? Nothing to format.
if [[ -z "$FILE_PATH" ]]; then
    exit 0
fi

# Only act on Kotlin sources.
case "$FILE_PATH" in
    *.kt|*.kts) ;;
    *) exit 0 ;;
esac

# Resolve project root (hook runs with CWD = project root, but be explicit).
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# Run Spotless scoped to the edited file.
# `-PspotlessIdeHook=<absolute>` is the Spotless-recommended way to limit
# the apply target to a single file. If the project's convention plugin
# does not support it, fall back to a broader `spotlessApply` (slower).
cd "$PROJECT_DIR"

if ! ./gradlew \
        -PspotlessIdeHook="$FILE_PATH" \
        spotlessApply \
        --quiet \
        --console=plain \
        2>/tmp/format-kotlin.err
then
    {
        echo "spotlessApply failed for $FILE_PATH"
        echo "--- gradle output ---"
        cat /tmp/format-kotlin.err
    } >&2
    exit 2
fi

exit 0
```

- [ ] **Step 3: 実行権限付与**

実行:
```bash
chmod +x .claude/hooks/format-kotlin.sh
ls -la .claude/hooks/format-kotlin.sh
```
期待: `-rwxr-xr-x`(または相当)で実行権が付いている。

- [ ] **Step 4: 手動で hook の挙動を試す**

実行(適当な kt ファイルを対象に dry-run):
```bash
TARGET="$(find domain/src/commonMain -name "*.kt" | head -1)"
echo "{\"tool_input\":{\"file_path\":\"$TARGET\"}}" | \
    CLAUDE_PROJECT_DIR="$(pwd)" .claude/hooks/format-kotlin.sh
echo "exit=$?"
```
期待: `exit=0`。Spotless が走り、ターゲットファイルが既にフォーマット済みなら出力なし。Gradle daemon の cold start で初回は遅い(60-120s)。

**もし `spotlessIdeHook` プロパティを convention plugin が認識せず失敗する場合:**
- `set -euo pipefail` の `pipefail` で検出される
- Step 2 のスクリプトを `-PspotlessIdeHook=...` 行 → `spotlessApply` 単体に変更し、対象を絞らず全プロジェクトに適用する fallback に切り替える(コメントで残す)
- これは Task 9 の検証で発見される可能性が高い

- [ ] **Step 5: 非 Kotlin ファイルの skip 確認**

実行:
```bash
echo '{"tool_input":{"file_path":"README.md"}}' | \
    CLAUDE_PROJECT_DIR="$(pwd)" .claude/hooks/format-kotlin.sh
echo "exit=$?"
```
期待: `exit=0`、Gradle 起動なし、即座に終わる。

- [ ] **Step 6: 空入力の skip 確認**

実行:
```bash
echo '{}' | CLAUDE_PROJECT_DIR="$(pwd)" .claude/hooks/format-kotlin.sh
echo "exit=$?"
```
期待: `exit=0`、即座に終わる。

- [ ] **Step 7: Commit**

実行:
```bash
git add .claude/hooks/format-kotlin.sh
git commit -m "build: add format-kotlin.sh PostToolUse hook

Claude Code が Write/Edit/MultiEdit で .kt / .kts ファイルを編集した直後に
Spotless を該当ファイル限定で走らせる hook。stdin の JSON から file_path を
抽出し、Kotlin source の場合のみ ./gradlew -PspotlessIdeHook=<path>
spotlessApply を実行する。非 Kotlin ファイルや空入力は即座に exit 0。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: `.claude/settings.json` を作成して hook を登録

**Files:**
- Create: `.claude/settings.json`

- [ ] **Step 1: 既存 settings の有無確認**

実行:
```bash
ls -la .claude/ 2>/dev/null
```
期待: `settings.local.json` は存在するかもしれない(gitignored)。`settings.json` は存在しない(これから作る)。

- [ ] **Step 2: settings.json を作成**

ファイル全文:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/format-kotlin.sh",
            "timeout": 180
          }
        ]
      }
    ]
  }
}
```

実行:
```bash
cat .claude/settings.json | jq .
```
期待: JSON が valid で `hooks.PostToolUse[0].matcher` が `"Write|Edit|MultiEdit"`。

- [ ] **Step 3: Commit**

実行:
```bash
git add .claude/settings.json
git commit -m "build: register format-kotlin.sh as PostToolUse hook

Claude Code の Write/Edit/MultiEdit 後に .claude/hooks/format-kotlin.sh が
必ず走るよう settings.json を作成。timeout は Gradle daemon の cold start を
考慮して 180s。リポジトリ commit してチーム/将来セッションで同じ強制が
効くようにする。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: エンドツーエンド動作確認

**目的:** 新しい Claude Code セッションを開いて、(a) CLAUDE.md がロードされる、(b) rule ファイルが paths にマッチしたファイル編集時にロードされる、(c) Edit 後に hook が Spotless を走らせる、の 3 つを確認する。

**Files:** なし(検証のみ)

- [ ] **Step 1: 新セッションで `/memory` を確認**

操作:
1. 新しい Claude Code セッションを起動(`claude` コマンド再実行)
2. `/memory` を実行

期待:
- `CLAUDE.md` が "loaded files" に表示される
- `.claude/rules/*.md` は paths スコープなので「conditional rules」として **列挙** されるが、起動時にはまだロードされていない状態(条件付き)

- [ ] **Step 2: rule の条件付きロード確認**

操作: Claude に `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Products.kt` を読ませる(例えば「Products.kt を読んで」と依頼)。

期待:
- `domain-guideline.md` / `software-architecture.md` / `error-handling.md` が `paths` glob にマッチしてコンテキストに注入される
- `rpc-and-transactions.md` はマッチしないのでロードされない

確認方法: `/memory` をもう一度開き、注入済み rules を見る。または `InstructionsLoaded` hook をデバッグ用に一時的に有効化してログを取る(後者は任意)。

- [ ] **Step 3: hook の実機動作確認**

操作: Claude に適当な .kt ファイルを Edit させる(無害な変更で OK、例: コメント追加して削除)。

期待:
- Edit 後に Claude Code の出力に hook 実行のログが出る(成功なら通知無しの場合あり)
- 該当ファイルが Spotless で再フォーマットされる(diff で確認)
- Gradle daemon の warm 化により 2 回目以降は数秒で完了する

- [ ] **Step 4: 失敗ケースの確認(任意)**

操作: 一時的に `.claude/hooks/format-kotlin.sh` の Spotless 呼び出しを `false` に置き換えて、`exit 2` が返り、Claude に「spotlessApply failed」が伝わることを確認(その後 revert)。

- [ ] **Step 5: 動作確認完了の memo**

実行:
```bash
git status
```
期待: clean(検証だけなので変更なし)。

確認結果は会話または ad-hoc な memo に残すだけで commit はしない。問題があれば該当 Task に戻って修正 commit を打つ。

---

## Self-Review(plan 作成者向け、参考)

- §1 規約読者=Claude、§2 全体構成 → Task 2-8 でカバー
- §3 CLAUDE.md の 5 セクション → Task 2 で全文埋め込み
- §4 4 つの rule ファイル → Task 3-6 で個別に作成
- §5 hooks → Task 7-8 で script + settings
- §6 「変遷あり」テーブル → 各 rule の「採用しない変遷項目」セクション or 本文に統合済み
- §7 整合確認 → Task 1 で実施
- §8 想定作業の流れ 1-6 → Task 1, 2-6, 7-8, 9 が対応
- 命名統一(ファーストクラスコレクション)を全 rule で確認済み
- 各 rule の `paths` は実ファイル配置(`backend/api/src/main/kotlin/...` 等)を捉える glob になっている

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-30-coding-conventions.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
