# トランザクション境界 再設計 design

> 対象読者: Claude(セッションで本リポジトリを編集する自分)+ レビューする人間
> 出典: ブレインストーミング(2026-05-30)。実コード確認に基づく
> 関連 rule: [.claude/rules/rpc-and-transactions.md](../../../.claude/rules/rpc-and-transactions.md), [.claude/rules/software-architecture.md](../../../.claude/rules/software-architecture.md)

## 1. 背景と問題

backend の全 RPC Controller は presentation 層の `tx()` ヘルパー(`configuration/transaction/Transaction.kt`)で各メソッドを包んでいる。この `tx()` は 4 つの責務を一手に背負っている:

1. **トランザクション境界** — `supervisorScope { newSuspendedTransaction(db) { } }`
2. **例外 → RpcError 変換** — `ResourceNotFoundException → NotFound` / `IllegalArgumentException → BadRequest`(VO の require 違反)/ `Throwable → Internal` / `CancellationException` 再送出。あわせて `RpcError.BadRequest` は `(field, reason)` から `(reason)` に簡素化(構築箇所が無いため安全)
3. **セッション guard** — `session.exp` 失効チェック → `Unauthorized`
4. **呼び出しログ** — callId / userId / outcome / elapsedMs の 1 行 JSON

### 違和感(本再設計の動機)

- **戻り値が RPC に寄っていていびつ**: トランザクション管理の道具であるはずの `tx()` の戻り値が `RpcResult<T, RpcError>` に固定されている。transaction という関心と RPC という関心が型レベルで癒着している
- **エラーハンドリングを tx が担っている**: 「トランザクションを張る」と「例外を RpcError に翻訳する」は別の関心なのに 1 関数に同居している
- **tx 不要なエンドポイントも tx で囲う必要がある**: DB を触らない処理でも `tx()` を通さないと guard / log / エラー変換が効かない構造になっている
- **ユースケース全体をトランザクション境界にしているが、そもそも transaction を意識しない作りにできないか**

## 2. 調査で判明した事実(設計の根拠)

実コード・スキーマ・DB grant を確認した結果:

1. **append-only は DB 権限で強制されている。** 実行時ロール `mindstock_app` は全テーブルに `SELECT, INSERT` のみ(`V00000000000000__append_only_role.sql`)。UPDATE / DELETE は物理的に実行不可能。FK は全て `ON DELETE/UPDATE RESTRICT`
2. **全集約が「identity 行 + revision/event 行」モデル。** 現在値は read 時に `base ⋈ 最新 revision`(`max(id)` 採用)で畳んで決める。**後勝ちは既に read 側で実装済み**
3. **複数行を書くユースケースは 3 つだけ**、いずれも**単一 DataSource メソッド内に閉じている**:
   - `UserRegisterDataSource.register`(users + user_display_names)
   - `CatalogItemRegisterDataSource.register`(catalog_items + catalog_item_revisions)
   - `HouseholdRegisterDataSource.create`(households + household_memberships)
4. **複数の write をまたぐ Controller メソッドは 1 つも存在しない。** Scenario も存在しない。各 Controller メソッドは「複数 read + 最大 1 つの write サービス呼び出し」のみ
5. **書き込み時に守る業務不変条件は実質ゼロ。** 在庫マイナス禁止のような「複数行をまたいで write 時に検証する」制約は現状存在しない

### 帰結:transaction が本来守る 3 役割の現状

| transaction の役割 | append-only + 現設計での扱い |
|---|---|
| ロストアップデート防止 | UPDATE が無いので問題自体が存在しない。並行編集は両方 append、read が最新採用 |
| 単一行の原子性・耐久性 | 単一 INSERT は PostgreSQL がそれ自体で原子的。アプリ tx 不要 |
| 一意性・参照整合性 | DB 制約(UNIQUE / FK / CHECK)が担保 |

→ **1 行 INSERT のユースケースは transaction 不要。** 原子性が要るのは複数 INSERT の 3 メソッドだけで、すべて単一 DataSource メソッド内に閉じている。

### 制約(動かせない前提)

- **Exposed の DSL は `transaction{}` / `newSuspendedTransaction{}` の中でしか実行できない。** 「transaction を完全にゼロにする」は物理的に不可能。決められるのは「tx ラッパーをどこに置くか」と「後勝ちで原子性を捨てられる箇所はどこか」の 2 点のみ

## 3. 設計判断(確定事項)

### 判断 1: tx 境界を DataSource メソッド内に下ろす

presentation の `tx()` が担っていた**トランザクション境界の責務を、各 DataSource メソッドの中**へ移す。

- 各 DataSource メソッドが自分のトランザクションを張る(read メソッドも write メソッドも)
- presentation / application / domain から transaction という語が消える
- 複数 INSERT の 3 メソッドは「1 メソッド = 1 トランザクション」で原子性を維持する。途中クラッシュは両 INSERT ともロールバックされ、片身行が残らない
- read→write をまたぐ Controller メソッド(adopt 等)が複数の DataSource 呼び出しに分割されても、append-only ゆえ安全(read 時に在った行は delete が無いので write 時にも在る)

**横断トランザクションの不在を前提とする。** 将来 1 メソッドで複数 write をまたぐユースケース(かつ原子性が必須なもの)が出たら、その時に明示的なトランザクション境界の導入を別途検討する(§6 参照)。

### 判断 2: suspend 伝播 + `newSuspendedTransaction`

DataSource メソッドを `suspend fun` 化し、内部で `newSuspendedTransaction(db = db) { }` を張る。

- Repository interface の全メソッド、Service の全メソッドが `suspend` になる
- core の既存テストは suspend 対応(`runTest` 等)が必要
- これがコルーチンとして最もイディオマティック。波及範囲が広いのは受け入れる
- `supervisorScope`(kRPC server scope への cancellation leak 防止)は presentation 境界側に残す(§4.2)

### 判断 3: presentation 境界 `rpcBoundary` への分離

`tx()` から transaction 責務を抜いた残り(guard + 例外→RpcError 変換 + log + supervisorScope)を、presentation 層の薄い境界ヘルパー `rpcBoundary` に集約する。

- 各 Controller メソッドは `rpcBoundary(session) { ... }` で包む
- 成功時は `rpcBoundary` が `RpcResult.Ok` で包み、例外は `RpcError` に翻訳する
- Controller 本体は「session.userId 取り出し → Service 呼び出し → ドメイン値を返す」だけに薄くなる(tx / catch / Err 生成を書かない)
- 1 コンテキスト 1 Controller クラスのまま(デコレータ・proxy は導入しない)

### 判断 4: register は routing guard で「未登録のみ通す」

`UserPublicController.register` の再呼び出し(登録済みユーザーが再度叩く)を、routing 層の guard で断つ。

- `RequireRegisteredUserPlugin` の逆 = `RequireUnregisteredUserPlugin`(`session.userId != null` なら弾く)を `/user/public` route に install する
- これにより「登録済みユーザーが register を再呼び出しして UNIQUE 違反になる」経路が消える
- メソッド内 tx により「クラッシュ後の片身詰み」は既に解消されるため、`insertIgnore`(ON CONFLICT)による冗長な冪等化は**採用しない**。DataSource の register は通常の INSERT のまま

## 4. 変更詳細

### 4.1 `:backend:core`(infrastructure / application)

**DataSource(infrastructure)** — 全メソッドを `suspend` 化し、`newSuspendedTransaction(db = db) { }` で内部に境界を張る。`Database` をコンストラクタで受け取る。

```kotlin
class UserRegisterDataSource(private val db: Database) : UserRegisterRepository {
    override suspend fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile =
        newSuspendedTransaction(db = db) {
            val insertedUserId = UsersTable.insert { it[zitadel_sub] = identity.subject() } get UsersTable.id
            UserDisplayNamesTable.insert {
                it[user_id] = insertedUserId
                it[display_name] = defaultDisplayName()
            }
            (UsersTable innerJoin UserDisplayNamesTable)
                .selectAll().where { UsersTable.id eq insertedUserId }.single().toProfile()
        }
}
```

- read 系 DataSource(`UserDataSource` 等)も同様に `suspend` + `newSuspendedTransaction`
- 行が無い場合の `ResourceNotFoundException` throw は現状どおり(infrastructure が不在を例外で表現)

**Repository interface(application)** — 全メソッドに `suspend` を付与。

```kotlin
interface UserRegisterRepository {
    suspend fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile
    suspend fun rename(userId: UserId, newName: DisplayName)
}
```

**Service(application)** — 全メソッドに `suspend` を付与。素通しの方針は不変。

```kotlin
class UserRegisterService(private val userRegisterRepository: UserRegisterRepository) {
    suspend fun register(identity: AuthIdentity, defaultDisplayName: DisplayName): Profile =
        userRegisterRepository.register(identity, defaultDisplayName)
}
```

### 4.2 `:backend:api`(presentation / configuration)

**`rpcBoundary`(新規、`configuration/transaction/` を `configuration/rpc/` 等へ改称も検討)** — `tx()` の後継。transaction を張らず、guard + 例外翻訳 + log + supervisorScope のみ。

```kotlin
suspend fun <T> rpcBoundary(
    session: MindstockSession,
    block: suspend () -> T,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    if (start > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result = supervisorScope { block() }   // ← transaction は張らない
        emitLog(session, start, outcome = "Ok")
        RpcResult.Ok(result)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResourceNotFoundException) {
        emitLog(session, start, outcome = "Err:NotFound")
        RpcResult.Err(RpcError.NotFound(message = e.message.orEmpty()))
    } catch (e: IllegalArgumentException) {
        emitLog(session, start, outcome = "Err:BadRequest")
        RpcResult.Err(RpcError.BadRequest(reason = e.message.orEmpty()))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        emitLog(session, start, outcome = "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

- `block` の戻り値はドメイン値 `T`。`rpcBoundary` が `RpcResult.Ok(result)` で包む(Controller が `RpcResult.Ok(...)` を書かなくてよくなる)
- `Database` を引数に取らない(transaction を張らないため)

**Controller** — `Database` 依存を外し、各メソッドを `rpcBoundary` で包む。

```kotlin
class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productService: ProductService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun replenish(
        productId: ProductId, qty: Quantity, occurredAt: OccurredAt, note: Note,
    ): RpcResult<Unit, RpcError> = rpcBoundary(session) {
        val product = productService.findById(productId)
        stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note)
    }
}
```

**`RequireUnregisteredUserPlugin`(新規)** — `/user/public` route に install。

```kotlin
val RequireUnregisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireUnregisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId != null) {
                call.respond(HttpStatusCode.Conflict)
            }
        }
    }
```

**`RoutingConfiguration`** — `Database` の Controller への引き渡しを削除。`/user/public` に `RequireUnregisteredUserPlugin` を install。ControllerFactory から `Database` 引数を除去。

**`MindstockAuthPlugin`** — `newSuspendedTransaction` を直接張っている箇所(L86)は、認証が WS upgrade 時の処理で **RPC 境界の外**(`rpcBoundary` を通らない)であるため、判断 1「tx を DataSource に下ろす」の対象外とする。`userRepository.findProfileByAuthIdentity` を呼ぶための独立した小さな tx として現状維持する(将来 DataSource が自前で tx を張るようになれば、この `newSuspendedTransaction` ラップは外して Repository 呼び出しだけ残す形に簡約できる)。

### 4.3 削除されるもの

- `tx()`(`Transaction.kt`)→ `rpcBoundary` に置換
- Controller / ControllerFactory の `Database` 依存
- `DatabaseConfig { useNestedTransactions = true }` の必要性再検討(ネストしたトランザクションが無くなるなら不要)

## 5. テスト方針

- **core の DataSource 統合テスト**: 既存の `RepositoryTestSupport.tx { }` ヘルパーは「テスト内で複数 Repository 呼び出しを 1 tx にまとめる」用途。DataSource が自前で tx を張るようになるため、テストヘルパーの役割を見直す(各 DataSource 呼び出しがそれぞれ tx を張る形に。テスト内の `tx { a(); b() }` は `a(); b()` の連続 suspend 呼び出しになる)
- **Controller テスト**: `rpcBoundary` 経由のエラー翻訳(NotFound / Internal / Unauthorized)を検証。`Database` モックの引き回しが消える
- **`TxWithGuardTest`**: `tx()` の guard ロジックを検証していたもの → `rpcBoundary` のテストに移行
- **register の二重呼び出し**: `RequireUnregisteredUserPlugin` が登録済み session を弾くことを E2E で検証
- 既存の E2E(`UserPublicRpcServiceE2eTest` 等)で「重複 sub の登録が弾かれる」挙動の期待値を更新(`tx() の catch で Internal に変換` → `routing guard で Conflict` へ)

## 6. 受け入れる前提・将来の注意点

- **業務不変条件は read-time / DB 制約側に寄る。** 将来「書き込み時に厳密に守りたい条件」(例: 並行下で在庫を絶対マイナスにしない)が出たら、その箇所だけ DB 制約 / 直列化 / 明示トランザクションが要る。**現時点でそのような条件は 1 つも無い**ため、この再設計で失う正しさはゼロ
- **横断トランザクションが必要になったら。** 1 メソッドで複数 write をまたぎ、かつ原子性が必須なユースケースが出た場合、DataSource メソッド内 tx では足りない。その時は application 層に明示的なトランザクション境界(`UnitOfWork` 的なもの)を導入する設計を別途行う。今は不要なので作らない(YAGNI)
- **`append_only_role` の前提が崩れたら。** もし将来 UPDATE / DELETE を許可するテーブルが出たら、後勝ち前提が崩れ、ロストアップデート防止のための tx が必要になる箇所が生まれる

## 7. 作業順序(実装計画の素案)

1. `:backend:core` の Repository interface に `suspend` 付与
2. DataSource 実装を `suspend` + `newSuspendedTransaction(db)` 化、コンストラクタで `Database` 受け取り
3. Service に `suspend` 付与
4. core のテストを suspend 対応(`runTest`)
5. `:backend:api` に `rpcBoundary` を追加、`tx()` を削除
6. Controller を `rpcBoundary` 化、`Database` 依存除去
7. `RequireUnregisteredUserPlugin` 追加、`RoutingConfiguration` 更新
8. Controller テスト / E2E の期待値更新
9. `.claude/rules/rpc-and-transactions.md` の `tx()` 記述を `rpcBoundary` + 「tx は DataSource 内」に更新
10. フル build + integrationTest

## 8. 関連 rule の更新が必要な箇所

- **`.claude/rules/rpc-and-transactions.md`**: 「DB を触る RPC method は `tx(database) { }` で包む」→「DataSource が自分の tx を張る。Controller は `rpcBoundary` で包む」へ全面改訂
- **`.claude/rules/software-architecture.md`**: 「DataSource 実装内では `transaction {}` を書かない(`tx()` で境界管理)」→「DataSource 実装が `newSuspendedTransaction` で自分の境界を張る」へ改訂(現行ルールと逆になる重要な変更)
