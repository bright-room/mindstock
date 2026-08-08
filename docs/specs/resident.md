# resident(居住者)機能仕様書

## 機能概要

resident は mindstock の利用者(居住者)を表すコンテキスト。提供する機能は次の 3 つに集約される。

1. **初回登録** — 外部 IdP(Zitadel)で認証されたが mindstock に未登録の利用者が、表示名を入力して居住者として登録される
2. **表示名の変更** — 登録済みの居住者が自分の表示名を変更する
3. **認証アイデンティティからの居住者解決** — WebSocket 接続時に JWT の `sub` から居住者を引き当て、接続のセッション状態(登録済み / 未登録)を決める

集約 `Resident` は `id + profile(displayName)` のみを持つ薄い構造で、状態遷移や計算といったドメインロジックは持たない。業務ルールは値オブジェクトの値域検証と、presentation 層のガード(登録済み / 未登録の分岐)に現れる。

居住者は他コンテキストから広く参照される。世帯(household)のメンバーは `Resident` を composition で保持し、在庫変動(stock)は記録者を `ResidentId` で持つ。逆に resident から他コンテキストへの依存はない。

## ユースケース

### UC1: 初回登録(表示名を登録して居住者になる)

**アクター**: 未登録セッションの利用者

**基本フロー**

1. 利用者が外部 IdP で認証を済ませ、取得した JWT で WebSocket `/api/rpc` に接続する
2. サーバ(`MindstockAuthPlugin`)が JWT を検証し、`AuthIdentity(ZITADEL, AuthSubject(sub))` を組み立てる
3. サーバが `ResidentRepository.findByAuth` で居住者を探すが見つからず、セッションを `MindstockSession.Unregistered` として保存する
4. フロントエンドが `SessionRpcService.whoami()` を呼び、`SessionStatus.Unregistered` を受けてオンボーディング画面へ遷移する
5. 利用者がオンボーディングのステップ(Welcome → Name → Household → Confirm)を進め、表示名を入力して確定する
6. フロントエンドが入力文字列から `DisplayName` を生成する(この時点で trim と値域検証が走る)
7. `ResidentRegisterRpcService.register(displayName)` が呼ばれる
8. `ResidentRegisterController` が `allowUnregistered` ガードを通し、セッションが `Unregistered` であることを確認して `ResidentRegisterService.register(session.identity, displayName)` を呼ぶ
9. `ResidentRegisterDataSource.registerResident` が 1 トランザクション内で `residents` / `resident_auth_identities` / `resident_display_names` に 1 行ずつ INSERT する。`ResidentId` はここで `ResidentId.create()`(UUIDv7)により採番される
10. 採番済み `Resident` が `RpcResult.Ok` で返る
11. フロントエンドが WebSocket を再接続する(`AuthFlow.onResidentRegistered`)。再接続後のセッションは `Registered` になる
12. 続けて世帯名が入力されていれば世帯作成へ、空なら「世帯なし」状態へ遷移する(関連コンテキスト参照: household)

**代替・例外フロー**

- **6' 表示名が値域外**: フロントエンドで `IllegalArgumentException` を捕捉し、`RpcError.BadRequest("displayName", "invalid")` 相当のトーストを表示して送信しない(`OnboardingViewModel.submit`)
- **8' セッションが `Registered`**: `RpcResult.Err(RpcError.Conflict("already registered"))` を返し、`ResidentRegisterService.register` は呼ばない
- **8'' JWT の有効期限切れ**: `runGuarded` が現在時刻と `session.exp` を比較し、`RpcResult.Err(RpcError.Unauthorized("token expired"))` で短絡する
- **11' 再接続失敗**: フロントエンドがトーストを表示し、送信中フラグを戻す(登録自体はサーバ側で完了している)
- **2' JWT 検証失敗 / `sub` 欠落 / `exp` 欠落**: WebSocket ハンドシェイクの段階で HTTP 401 を返す

### UC2: 表示名を変更する

**アクター**: 登録済みセッションの居住者(本人)

**基本フロー**

1. 利用者が設定画面で新しい表示名を入力する
2. `ResidentRegisterRpcService.rename(displayName)` が呼ばれる
3. `ResidentRegisterController` が `requireRegistered` ガードで `residentId` を取り出し、`ResidentRegisterService.rename(residentId, displayName)` を呼ぶ
4. `ResidentRegisterDataSource.appendDisplayName` が `resident_display_names` に 1 行 INSERT する(既存行は更新しない)
5. `RpcResult.Ok(Unit)` が返る
6. フロントエンドがローカル状態に新しい表示名を反映し、世帯情報を再取得してメンバー一覧の自分の表示名も更新する(`SettingsViewModel.renameDisplayName`)

**代替・例外フロー**

- **3' セッションが `Unregistered`**: `RpcResult.Err(RpcError.Unauthorized("registration required"))`
- **2' 表示名が値域外**: `DisplayName` の生成時点で `IllegalArgumentException`。RPC まで到達した場合は `runGuarded` が `RpcError.BadRequest("request", <例外メッセージ>)` に翻訳する
- **5' 失敗時**: フロントエンドは `FailureHandler.onMutationFailure` により、`Unauthorized` なら再認証、それ以外はトースト表示

### UC3: 接続時に居住者を解決する

**アクター**: サーバ(`MindstockAuthPlugin`)。利用者の接続操作が契機

**基本フロー**

1. WebSocket ハンドシェイクの subprotocol から Bearer トークンを取り出す(`WsBearerTokenExtractor`)
2. RSA256 / issuer / audience / leeway を指定した検証器で JWT を検証する
3. `sub` と `exp` を取り出し、`AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))` と接続単位のトレース ID(`callId`)を組み立てる
4. `ResidentRepository.findByAuth(identity)` を `Dispatchers.IO` 上で呼ぶ(JDBC の blocking 呼び出しのため)
5. 居住者が見つかれば `MindstockSession.Registered(identity, resident.id, exp, callId)`、`ResourceNotFoundException` なら `MindstockSession.Unregistered(identity, exp, callId)` を `call.attributes` に格納する

**代替・例外フロー**

- **1' トークンが取り出せない**: HTTP 401
- **2' 検証失敗**: HTTP 401
- **3' `sub` が null または空白 / `exp` が null**: HTTP 401
- **4' `ResourceNotFoundException` 以外の例外(DB 障害等)**: 握り潰さず伝播させる。未登録へ降格させない(`MindstockAuthPluginTest` が検証)

### UC4: 自分自身を照会する(whoami)

**アクター**: 接続中の利用者(未登録でも可)

**基本フロー**

1. フロントエンドが起動直後に `SessionRpcService.whoami()` を呼ぶ
2. `SessionController` が `allowUnregistered` ガードを通す
3. セッションが `Registered` なら `ResidentService.me(session.residentId)` で `Resident` を取得し `SessionStatus.Registered(resident)` を返す。`Unregistered` なら `SessionStatus.Unregistered` を返す
4. フロントエンドが結果でホーム画面 / オンボーディング画面を分岐する

**代替・例外フロー**

- **3' `Registered` だが `findById` が見つけられない**: `ResourceNotFoundException` → `RpcError.NotFound`

> UC4 の RPC は session コンテキストに属する。resident 側は `ResidentService.me` を提供する立場。関連コンテキスト参照: session(認証)。

## RPC インターフェース

### `ResidentRegisterRpcService`

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRegisterRpcService.kt`(`@Rpc`)
実装: `ResidentRegisterController`(`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/resident/ResidentRegisterController.kt`)

| メソッド名 | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `register` | `displayName: DisplayName` | `RpcResult<Resident, RpcError>` | `Conflict("already registered")`(セッションが `Registered`)/ `BadRequest`(表示名の値域違反)/ `Unauthorized("token expired")` / `Internal`(上記以外の例外) |
| `rename` | `displayName: DisplayName` | `RpcResult<Unit, RpcError>` | `Unauthorized("registration required")`(セッションが `Unregistered`)/ `BadRequest`(表示名の値域違反)/ `Unauthorized("token expired")` / `Internal` |

- ガード: `register` は `allowUnregistered`、`rename` は `requireRegistered`
- 認証アイデンティティと居住者 ID はいずれも引数に取らず、接続時のセッションから取る
- resident コンテキストに読み取り専用の RPC service は存在しない。居住者の参照は `SessionRpcService.whoami()`(session コンテキスト)経由

### 参考: 隣接コンテキストの関連メソッド

| service | メソッド | 関連 |
|---|---|---|
| `SessionRpcService` | `whoami()` | `SessionStatus.Registered(resident: Resident)` として `Resident` を返す。関連コンテキスト参照: session(認証) |

## データモデル

### 集約: `Resident`

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/Resident.kt`

```
Resident
├── id: ResidentId
└── profile: ResidentProfile
    └── displayName: DisplayName
```

- `@Serializable data class`。操作メソッドを持たない(表示名の変更は Repository への追記として行われ、集約にメソッドは無い)
- `createdAt` は集約に持たない(インフラメタとして `residents.created_at` にのみ存在)

### 値オブジェクト

| 型 | 実体 | 不変条件 | 違反時 |
|---|---|---|---|
| `ResidentId` | `Uuid` | なし。`create()` が UUIDv7 を生成 | — |
| `DisplayName` | `String` | 生成時に trim。trim 後 1〜100 文字(`MAX_LENGTH = 100`) | `IllegalArgumentException("DisplayName must be 1..100 chars after trim")` |
| `AuthSubject` | `String` | 空白のみを禁止(`isNotBlank`) | `IllegalArgumentException("AuthSubject must not be blank")` |
| `AuthProvider` | enum | `ZITADEL` の 1 値のみ | — |
| `AuthIdentity` | `provider + subject` の data class | なし(構成要素の不変条件に委ねる) | — |
| `ResidentProfile` | `displayName` の data class | なし | — |

`DisplayName` はコンストラクタが `private` で、`companion object` の `operator fun invoke(raw: String)` が `raw.trim()` を渡して生成する。したがって `DisplayName("  たろう  ")` は `"たろう"` になる。

### DB テーブル(Exposed 定義 / `V1__init.sql`)

#### `residents`(`ResidentsTable`)

| カラム | 型 | 制約 |
|---|---|---|
| `id` | `uuid` | PRIMARY KEY |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

#### `resident_auth_identities`(`ResidentAuthIdentitiesTable`)

| カラム | 型 | 制約 |
|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY |
| `resident_id` | `uuid` | NOT NULL, FK → `residents(id)` ON DELETE RESTRICT |
| `provider` | `VARCHAR(20)` | NOT NULL(`AuthProvider` の名前を格納) |
| `subject` | `VARCHAR(255)` | NOT NULL |
| `linked_at` | `TIMESTAMP` | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

- UNIQUE `(provider, subject)` — 1 つの認証アイデンティティは高々 1 居住者
- INDEX `(resident_id)`
- テーブル構造上は 1 居住者に複数の認証アイデンティティを紐づけられるが、追加リンクを行う機能は実装されていない

#### `resident_display_names`(`ResidentDisplayNamesTable`)

| カラム | 型 | 制約 |
|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY |
| `resident_id` | `uuid` | NOT NULL, FK → `residents(id)` ON DELETE RESTRICT |
| `display_name` | `VARCHAR(100)` | NOT NULL |
| `recorded_at` | `TIMESTAMP` | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

- INDEX `(resident_id, id)`
- append-only。現在の表示名は「同一 `resident_id` の中で `id` が最大の行」

### 現在値の解決(Hydration)

`ResidentHydration.kt` の `latestResidentDisplayNames()` が、`rowNumber().over().partitionBy(resident_id).orderBy(id DESC)` を付けたサブクエリ(alias `latest_display_names`)を組み立て、`ResidentDataSource` がそれを `residents` と INNER JOIN したうえで `rn = 1` で絞る。

### application 層インターフェース

| interface | メソッド | 説明 |
|---|---|---|
| `ResidentRepository` | `findByAuth(authIdentity): Resident` | 認証アイデンティティで解決。未登録なら `ResourceNotFoundException` |
| `ResidentRepository` | `findById(id): Resident` | ID で解決。不在なら `ResourceNotFoundException` |
| `ResidentRegisterRepository` | `registerResident(authIdentity, displayName): Resident` | 3 テーブルへ INSERT し、採番済み `Resident` を返す |
| `ResidentRegisterRepository` | `appendDisplayName(residentId, displayName)` | `resident_display_names` へ 1 行追記(register / rename 兼用) |

| service | メソッド | 説明 |
|---|---|---|
| `ResidentService` | `me(actor: ResidentId): Resident` | `findById` へ素通し |
| `ResidentRegisterService` | `register(authIdentity, displayName): Resident` | `registerResident` へ素通し |
| `ResidentRegisterService` | `rename(actor, displayName)` | `appendDisplayName` へ素通し |

いずれの Service も orchestration のみで判定・分岐を持たない。

### 他コンテキストからの参照

| 参照元 | 用途 |
|---|---|
| `HouseholdRegisterService.register` / `join` | `residentRepository.findById(actor)` で世帯主 / 参加者の `Resident` を取得(関連コンテキスト参照: household) |
| `StockRegisterService`(3 箇所) | `residentRepository.findById(actor)` で在庫変動の記録者を取得(関連コンテキスト参照: stock) |
| `JoinHouseholdScenario` | `residentService.me(actor)` で参加者を取得(関連コンテキスト参照: household) |
| `MindstockAuthPlugin` | `residentRepository.findByAuth(identity)` でセッション状態を決定(関連コンテキスト参照: session) |

## エラー・例外

### ドメイン例外

| 例外 | 発生条件 | 発生箇所 |
|---|---|---|
| `IllegalArgumentException` | 表示名が trim 後 1〜100 文字の範囲外 | `DisplayName` の `init` |
| `IllegalArgumentException` | 認証サブジェクトが空白のみ | `AuthSubject` の `init` |
| `ResourceNotFoundException` | 認証アイデンティティに対応する居住者が無い(= 未登録) | `ResidentDataSource.findByAuth`。メッセージ: `"resident not found for auth: ${provider}"` |
| `ResourceNotFoundException` | 指定 ID の居住者が無い | `ResidentDataSource.hydrate`。メッセージ: `"resident not found: $id"` |

resident コンテキスト専用の例外クラスは定義されていない。

### RPC エラーへの翻訳

`SessionGuard.runGuarded` が例外を `RpcError` に翻訳する。resident に関係するものは次のとおり。

| 例外・条件 | `RpcError` |
|---|---|
| `Clock.System.now() > session.exp` | `Unauthorized("token expired")` |
| セッションが `Unregistered` かつ `requireRegistered` | `Unauthorized("registration required")` |
| `IllegalArgumentException` | `BadRequest(field = "request", reason = <例外メッセージ>)` |
| `ResourceNotFoundException` | `NotFound(message = <例外メッセージ>)` |
| その他の `Throwable` | `Internal("unexpected server error")`(構造化ログに `call_id` / `auth_subject` / `resident_id` を記録) |

Controller が直接返すもの:

| 条件 | `RpcError` |
|---|---|
| `register` をセッション `Registered` で呼んだ | `Conflict("already registered")` |

### フロントエンドでの扱い

`ResidentRepository`(`frontend/.../feature/resident/data/ResidentRepository.kt`)が `RpcResult` を `RpcOutcome`(`Success` / `Failure`)に変換して ViewModel に返す。ViewModel 側の扱いは次のとおり。

- `OnboardingViewModel.submit`: `register` 失敗時、`Unauthorized` なら再認証、それ以外はトースト。表示名の生成失敗は送信前に捕捉してトースト
- `SettingsViewModel.renameDisplayName`: `FailureHandler.onMutationFailure` に委譲(`Unauthorized` → 再認証、それ以外 → トースト)

## 制約事項・要確認

### 実装されていない機能

- **居住者の削除・退会**: RPC も Service も存在しない。参照する行の外部キーがすべて `ON DELETE RESTRICT` のため、DB 上も削除できない
- **認証アイデンティティの追加リンク・付け替え**: テーブル構造は 1 居住者に複数のアイデンティティを許すが、登録時に 1 件挿入する経路しかない
- **表示名以外のプロフィール項目**: `ResidentProfile` は `displayName` のみ
- **表示名履歴の参照**: append-only で記録しているが、過去の表示名を読み出す経路(read model / RPC)は存在しない
- **他人の居住者情報の直接照会**: `ResidentRepository.findById` は他コンテキストの Service から呼ばれるのみで、RPC としては公開されていない。世帯メンバーの表示名は household の集約経由で取得する

### 要確認

- **(要確認: 同時 register の競合)** 同一 `(provider, subject)` に対して `register` が同時に 2 回走った場合、`resident_auth_identities` のユニーク制約違反が起きる。これに対応する専用のドメイン例外は定義されておらず、`SessionGuard` の翻訳マップにも該当エントリが無いため `RpcError.Internal` に落ちる。意図的な割り切りか、未対応かはコードから読み取れない
- **(要確認: `findByAuth` の例外メッセージ)** `"resident not found for auth: ${authIdentity.provider}"` は provider しか含まず subject を出さない。PII / 機密配慮の意図と読めるが、コード上に根拠の記述はない
- **(要確認: 登録時に読み戻さない)** `ResidentRegisterDataSource.registerResident` は INSERT 後に DB から読み戻さず、メモリ上で `Resident` を組み立てて返す。プロジェクト規約(`backend-software-architecture.md`)の「INSERT 後は RETURNING 相当で読み戻して domain object を返す」から外れており、意図的な例外か見落としか判断できない
- **(要確認: `Resident` が rename メソッドを持たない)** ドメイン規約は「操作メソッドは新インスタンスを返す」「ビジネスロジックは domain に」としているが、表示名変更は集約のメソッドを経由せず Repository へ直接 append される。表示名変更に不変条件が無い(値域は `DisplayName` が担保する)ための割り切りと読めるが、明記はない
- **(要確認: 表示名の一意性)** 同名の居住者を許すかどうかは DB 制約・ドメイン検証のどちらにも現れていない。制約が無いという事実から「許す」と読んだが、要件として明示されているかは不明
- **(要確認: `rename` の対象が常に本人)** 世帯主が他メンバーの表示名を変更するといった要件は現状存在しない。将来必要になった場合、`rename` のシグネチャ変更が必要になる
- **(要確認: `whoami` が返す `Resident` の鮮度)** `SessionController.whoami` は毎回 `ResidentService.me` で DB を引くが、`MindstockSession` の `residentId` は接続時に固定される。表示名変更後も同一接続で `whoami` を呼べば最新値が返る一方、`MindstockSession` 自体は更新されない。実害があるかは他コンテキストの利用状況次第
- **(要確認: `DisplayName` の 100 文字と DB の `VARCHAR(100)`)** ドメインの `MAX_LENGTH = 100` と DB のカラム長は一致しているが、マルチバイト文字における「文字数」の解釈が Kotlin の `String.length`(UTF-16 コードユニット数)と PostgreSQL の `VARCHAR(n)`(文字数)で異なる。サロゲートペア(絵文字等)を含む表示名で、ドメイン検証を通過しても DB 挿入で失敗する可能性がある。テストでは検証されていない
