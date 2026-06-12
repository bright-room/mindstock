---
paths:
  - "domain/**/*.kt"
  - "backend/**/*.kt"
---

# Software Architecture

mindstock の層責務と依存方向。Controller / Scenario / Service / Repository / DataSource の責務分担と命名対称。

## Rule

### 層と依存方向

```
        presentation (rpc Controller, RpcError, MindstockSession)
              │
              ▼
        application (Scenario, Service, Repository interface)
              ▲
              │
        infrastructure (Repository 実装 = DataSource[DB] / Transfer[送信] / Receive[受信])

  domain (model, value object, exception) ← 全層が依存可能・横断
  configuration (Ktor plugin / DI / routing / tx ヘルパー)
    ← presentation か infrastructure の片方向 glue
```

- application / infrastructure は presentation に依存禁止
  - Scenario / Service / DataSource は `RpcError` / `RpcResult` / `MindstockSession` を import しない
- domain は全層に依存される
- configuration は片方向 glue。双方向の逃げ道を作らない

### Controller(presentation)

- presentation 層は「ユーザ入力 ↔ Scenario/Service の橋渡し」+ 腐敗防止層
- API 上の Request / Response 型と application 層の型が一致するなら、VO / 集約 / ファーストクラスコレクションを **直接** RPC method の引数・戻り値として使ってよい
- 型がずれる(API 仕様が application 内部表現と異なる)場合は、`presentation/rpc/<ctx>/` 配下に Request / Response data class を作りマッピングする。application 以降を汚さないための防波堤
- 業務ロジックは Domain と Scenario / Service に置き、Controller 自身は **薄く** 保つ
- 認可・session.userId 取り出しなど presentation 固有の前処理は Controller の責務

### Scenario(application)

- **複数 Service をまたぐユースケース単位** の作業オーケストレーション
- 配置: `application/scenario/<ctx>/<UseCase>Scenario.kt`
- 1 つの Scenario が複数の Service を順番に呼ぶ。Scenario 同士の呼び出しは不可(複雑性が連鎖するため)
- 1 つの Service で表現できるユースケースなら Scenario を作らず Controller から直接 Service を呼ぶ

### Service(application)

- **薄い orchestration**。ビジネスロジック自体は Domain に持たせる
  - ✅ Repository から集約 fetch、Domain メソッドを呼ぶ、Repository に保存、複数 Repository を順序制御
  - ❌ 条件分岐によるビジネス判定、状態遷移、計算ロジック(全部 Domain へ)
- 引数・戻り値はすべて VO / 集約 / ファーストクラスコレクション。primitive(`Int` / `String` 等)や raw `List<T>` を公開しない
- **Repository が返した値の null チェックを Service で書かない**。不在は infrastructure が例外で表現し、Service は素通しで forward する
- 戻り値:
  - 単一値: 集約 or VO(non-null)
  - 一覧: ファーストクラスコレクション(空でも `Stocks(emptyList())` を返す)
  - 戻り値の無いコマンド: `Unit`

### Repository(application interface / infrastructure 実装)

- interface 側で例外の throw は **規約化しない**(interface は契約だけを示す)
- 一覧 method は空のファーストクラスコレクションを返す(`Stocks(emptyList())` 等)
- Reader / Writer 分離:
  - 読み: `<Ctx>Repository`
  - 書き: `<Ctx>RegisterRepository`
- Hydration ロジックは `<Aggregate>Hydration.kt` の internal extension に集約。単一行マッピングは `ResultRow.to<Aggregate>()`、**複数テーブルを組み立てる集約は `assemble<Aggregate>(...)` 形も許容**(例: `assembleHousehold()` / `assembleInvitation()`。member や validity event を別テーブルから組み立てる)

### DataSource(infrastructure)

- 各メソッドは `transaction(database) { }` で自前にトランザクション境界を張る(`tx()` ヘルパー / `ExposedTransactionPlugin` は廃止。取り回しの煩雑さ回避)。`Database` はコンストラクタ注入する
- INSERT 後は RETURNING 相当(`insertAndGetId` + hydration)で読み戻して domain object を返す
- 行が無かった場合は `ResourceNotFoundException` を throw する(Service / Scenario は素通しの前提)
- 永続化メタの「現在時刻」は `Created.now()`(`infrastructure/datasource/Created.kt`)で取得し、**原則 `transaction(database) { }` ブロックの内側で呼ぶ**(挿入時刻とトランザクション境界を揃える)。例外的に retry ループでトランザクションを跨いで同一時刻を使いたい場合のみ外側で取得してよい(現状 `InvitationRegisterDataSource.issue` がこれに該当)。フェーズ 3-5 で「原則 tx 内」へ統一予定

### 外部システム実装(infrastructure・Transfer / Receive)

DB ではない外部システム(S3 / Garage、外部 API 等)との境界の実装。DataSource(DB 専用)とは別系統で扱う。

- **interface は通常どおり Repository**(`application/repository/<ctx>/<Name>Repository`)。「DB か外部か」を application 層に漏らさず、application からは常に Repository に見える。Reader / Writer 分離(`<Name>Repository` / `<Name>RegisterRepository`)も同じ
  - **interface を `infrastructure` 側に置かない**。application が infrastructure を import する逆方向依存になる(過去にこの層違反を踏んだ)
- **実装は通信方向で分類し `infrastructure/{transfer,receive}/<ctx>/` に置く**:
  - `transfer`(送信): 自システムから外部へデータを送り出す(例: 画像バイトを Garage へ put)
  - `receive`(受信): 外部からデータを取り込む(例: JAN で外部商品 API を照会して CatalogItem を得る)
- **実装クラス名は「目的 + 方向 suffix `{Transfer|Receive}`」**。`DataSource` のように機械的な接尾辞や安易な `Register` を付けず、「何を目的とし、メソッドが何をするか」が伝わる意味のある名前にする
- トランザクション境界は持たない(DB ではない)。不在 / 障害は DataSource と同じく `ResourceNotFoundException` 等で表現し、Service / Scenario は素通し

### パッケージ境界の判断軸

- **サブパッケージを切る基準は「概念区別」**(優先):
  - 役割が違う(集約状態 vs 派生 read-model)
  - 集約内サブエンティティ群が独立した塊
  - 依存方向を遮断したい
- 例: `stock/` は `Stock` 集約 / `StockMovement` fact / `ShoppingList` read-model の 3 概念が並ぶので `movement/` `shopping/` でサブパッケージ化
- **ファイル数も基準**: 1 パッケージ内が一定数(目安 5-7 ファイル)を超えたら、概念区別で更に分けられないかを見直すトリガー。安易に「ファイル数で機械的に分割」はしない

### 命名対称

- Controller / Scenario / Service / Repository / DataSource は **同じコンテキスト名のプレフィックス** を持つ
  - 例: `StockController` / `RegisterStockScenario` / `StockService` + `StockRegisterService` / `StockRepository` + `StockRegisterRepository` / `StockDataSource` + `StockRegisterDataSource`

## Why

- 関心の分離: ビジネスロジックは domain、orchestration は application、I/O は infrastructure、wire/HTTP/UI は presentation
- 逆向きの依存を許すと、テスト容易性とモジュール独立性が崩れる
- presentation 層の責務は「API 仕様の宣言」と「application との腐敗防止」であり、ロジックを持ち込まないことで application 以降のコードを純粋に保つ
- Service にロジックを書くと貧血モデルになり、ロジックが Service と Domain に二重化される
- Scenario を Service と別概念にすることで「Service = 単一集約のオーケストレーション」「Scenario = 複数 Service を跨ぐユースケース」と責務が明確になる
- Service に null チェックを散らすと、infrastructure の不在表現と Service の判断が二重化する。infrastructure に「不在は例外」を集めることで Service / Scenario / Controller の本流を直線的に保つ
- Repository interface で例外を約束しないのは、interface は「呼び方」を示すものであり「実装の挙動」を縛らないため。実装側(infrastructure)で適切な例外を throw する
- ファイル数を「機械的な分割トリガー」ではなく「概念見直しのトリガー」とするのは、ファイル数の増加が往々にして集約や責務の混在を示すサインだから
- 外部システムの境界 interface を application 側(Repository)に置くのは、application が「DB か外部か」を知らずに済ませるため。interface を infrastructure に置くと application → infrastructure の逆方向依存になり層が崩れる。実装を `transfer` / `receive` に分けるのは、外部 I/O の向き(送信 / 受信)が責務とテスト観点(何を送るか / 何を受けるか)を分けるから。`DataSource` を共用しないのは、DB(トランザクション境界・RETURNING)と外部 I/O では実装規約が異なるため

## How to apply

### ✅ Scenario(複数 Service を跨ぐユースケース)

```kotlin
// application/scenario/onboarding/RegisterFirstHouseholdScenario.kt
class RegisterFirstHouseholdScenario(
    private val userService: UserRegisterService,
    private val householdService: HouseholdRegisterService,
) {
    fun run(authIdentity: AuthIdentity, displayName: DisplayName, householdName: HouseholdName) {
        val userId = userService.register(authIdentity, displayName)
        householdService.register(householdName, ownerId = userId)
    }
}
```

### ✅ Service が VO / 集約 / ファーストクラスコレクションを扱う

```kotlin
class StockRegisterService(
    private val stockRepository: StockRepository,
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun replenish(stockId: StockId, quantity: Quantity, note: Note, actor: ResidentId) {
        val stock = stockRepository.findById(stockId)  // 不在は infra が例外を throw、Service は素通し
        val replenished = stock.replenish(quantity, note, actor)  // domain method がロジックを持つ
        stockRegisterRepository.appendMovement(stockId, replenished.latestMovement())
    }
}
```

### ❌ Controller にロジック

```kotlin
// アンチパターン: stock.replenish の閾値判定や archived チェックを Controller でやる
override suspend fun replenish(...): RpcResult<Unit, RpcError> {
    val stock = stockRepository.findById(stockId)  // ← Repository を直接呼んでロジックも書いている
    if (stock.product.archived) return RpcResult.Err(RpcError.Conflict("archived"))
    // ...
}
```

### ❌ Service が null チェック

```kotlin
// アンチパターン
fun replenish(stockId: StockId, qty: Quantity) {
    val stock = stockRepository.findById(stockId)
        ?: throw ResourceNotFoundException("not found")  // ← infra で throw 済み、Service で重複
    // ...
}
```

### ❌ Service / Repository が raw List / primitive を公開

```kotlin
// アンチパターン
fun findActive(): List<Product> = ...           // ← Products を返す
fun count(householdId: HouseholdId): Int = ...  // ← 専用 VO を返すか、ユースケース次第で集約に折り畳む
```

### ✅ 外部システム境界(interface=Repository / 実装=Transfer・Receive)

```kotlin
// interface は application 側に Repository として置く(DB か外部かを application に漏らさない)
// application/repository/catalog/ExternalProductRepository.kt
interface ExternalProductRepository {
    fun findByJan(jan: Jan): CatalogItem  // master 不在 JAN を外部から補完(参照=受信)
}

// 受信(receive): 外部から取り込む。infrastructure/receive/<ctx>/<目的>Receive
// infrastructure/receive/catalog/UnconfiguredProductReceive.kt
class UnconfiguredProductReceive : ExternalProductRepository {
    override fun findByJan(jan: Jan): CatalogItem = throw ResourceNotFoundException("...: $jan")
}

// 送信(transfer): 外部へ送り出す。infrastructure/transfer/<ctx>/<目的>Transfer
// infrastructure/transfer/product/ProductImageTransfer.kt
class ProductImageTransfer(private val s3: S3Client, private val bucket: String) : ProductImageStorageRepository {
    override suspend fun store(upload: RawImageUpload): ImageRef { /* Garage へ put */ }
}
```

### ❌ 外部境界の interface を infrastructure に置く

```kotlin
// アンチパターン: interface を infrastructure に置くと application が infra を import する逆方向依存になる
// infrastructure/gateway/ExternalProductGateway.kt  ← application/repository/<ctx>/ へ置くべき
interface ExternalProductGateway { fun fetch(jan: Jan): CatalogItem }
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- rule: [domain-guideline](domain-guideline.md) — domain メソッドの詳細
- rule: [error-handling](error-handling.md) — 例外と nullable の方針
- rule: [backend-rpc-and-transactions](backend-rpc-and-transactions.md) — Controller の Ktor 部分
