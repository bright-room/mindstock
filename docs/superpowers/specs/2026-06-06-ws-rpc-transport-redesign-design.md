# WS-RPC トランスポート再設計 — 設計

- 日付: 2026-06-06
- 対象: `:rpc` / `:backend:api`(`configuration/`, `presentation/rpc/`)/ `:frontend`(`core/rpc`, boot)
- 背景・原因分析: [2026-06-06-ws-auth-registration-guard-analysis.md](2026-06-06-ws-auth-registration-guard-analysis.md)
- ステータス: **設計合意済(実装計画前)**

## 1. 目的と方針

「認証付き WS が有効トークンでも 401」不具合の**根本原因(route-scoped な登録ガードが WS upgrade 経路で漏れる)を構造ごと除去**する。分析の結論(§10)に従い、REST 風のパス設計(per-service パス / `/api/v1` の URL バージョン / route による認可)をやめ、kotlinx-rpc-over-WebSocket に忠実な形へ作り替える。

**ゲートを 2 層に分け、各層を「WS がきれいに扱える場所」に置く:**

```
ゲート①認証(JWT の有無)= ハンドシェイクで確実に効く → エンドポイントで表現
ゲート②認可(登録の有無)= 接続後のメッセージ単位     → アプリ層(guard ヘルパー)で表現
```

プレリリース(P6 着手直後)につき**後方互換は取らず big-bang** で入れ替える。

## 2. 決定事項(サマリ)

| 論点 | 決定 |
|---|---|
| エンドポイント | **認証必須の単一 `/api/rpc`** に全サービスを相乗り(per-service パス廃止) |
| 認証ゲート | `MindstockAuthPlugin`(現行ロジック)をハンドシェイクで。有効 JWT なら未登録でも接続可 |
| 登録ガード | `RequireRegisteredUserPlugin` を**削除**。各 RPC メソッドが guard ヘルパーで宣言 |
| guard ヘルパー | `requireRegistered(session){ residentId -> }`(既定・fail-closed)/ `allowUnregistered(session){ }`(register・whoami 専用) |
| 未登録判定 | `SessionRpcService.whoami(): RpcResult<SessionStatus, RpcError>`、`SessionStatus = Registered(Resident) | Unregistered` |
| バージョニング | サブプロトコル `mindstock.v1`(URL に `/v1` を出さない)。v2 実装は今回しない |
| frontend | 単一接続 + boot で `whoami()` 分岐 |
| 認証不要エンドポイント | 今回作らない(将来 `/api/public` を兄弟として追加) |

## 3. backend 設計

### 3.1 ルーティング

```kotlin
install(MindstockAuthPlugin) { /* 現行どおり JWKS/issuer/audience/repo */ }   // app レベル維持
// RequireRegisteredUserPlugin の install は削除

routing {
    rpc("/api/rpc") {
        registerService<SessionRpcService> { SessionController(residentService, sessionOf(call)) }
        registerService<ResidentRegisterRpcService> { ResidentRegisterController(...) }
        registerService<CatalogRpcService> { ... }
        registerService<HouseholdRpcService> { ... }
        registerService<HouseholdRegisterRpcService> { ... }
        registerService<ProductRpcService> { ... }
        registerService<ProductRegisterRpcService> { ... }
        registerService<StockRpcService> { ... }
        registerService<StockRegisterRpcService> { ... }
        // ResidentRpcService.me() は §3.5 参照
    }
}
```

- `route("/api/v1")` 階層・per-service パス・`route("")` の入れ子はすべて廃止。
- `MindstockAuthPlugin` は app レベルのまま(エンドポイントは 1 本なので実質 `/api/rpc` を守る)。将来 `/api/public` を足す時に scope 化を検討。
- `WsSubprotocolEchoPlugin` は現行どおり(`mindstock.v1` のみ echo、bearer は echo しない)。

### 3.2 guard ヘルパー(`configuration/guard/`)

現行 `guarded(session){block}` を 2 つに置き換える。共通処理(`exp` 失効チェック + ドメイン例外→`RpcError` 翻訳 + `supervisorScope`)は private に括り出して共有する。

```kotlin
// 既定。登録必須。fail-closed(Unregistered は Unauthorized で短絡)。residentId を block に渡す。
suspend fun <T : Any> requireRegistered(
    session: MindstockSession,
    block: suspend (ResidentId) -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError>

// 認証のみ(未登録 OK)。register / whoami だけが使う。
suspend fun <T : Any> allowUnregistered(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError>
```

- `requireRegistered`: `exp` 切れ → `Unauthorized`。`session` が `Unregistered` → `Unauthorized`(fail-closed)。`Registered` → `block(residentId)`。これにより `session.requireResidentId()` は不要になる(ヘルパーが residentId を供給)。
- `allowUnregistered`: `exp` チェックと例外翻訳のみ。登録は問わない。
- **fail-closed の含意**: 保護メソッドで誤って `allowUnregistered` を書く / どちらも書かないと、residentId が手に入らずコンパイル or 実行で破綻する方向(=うっかり公開されない)。緩い方(`allowUnregistered`)が明示的に目立つ。

### 3.3 `SessionRpcService` / `SessionStatus`(`:rpc`)

```kotlin
// :rpc / session/
@Serializable
sealed interface SessionStatus {
    @Serializable data class Registered(val resident: Resident) : SessionStatus
    @Serializable data object Unregistered : SessionStatus
}

@Rpc
interface SessionRpcService {
    /** 現在の接続の登録状態を返す。boot 時の分岐に使う。 */
    suspend fun whoami(): RpcResult<SessionStatus, RpcError>
}
```

実装(`SessionController`、`allowUnregistered` で):

```kotlin
override suspend fun whoami(): RpcResult<SessionStatus, RpcError> =
    allowUnregistered(session) {
        when (session) {
            is MindstockSession.Registered ->
                RpcResult.Ok(SessionStatus.Registered(residentService.me(session.residentId)))
            is MindstockSession.Unregistered ->
                RpcResult.Ok(SessionStatus.Unregistered)
        }
    }
```

> `SessionStatus` は wire 契約なので `:rpc` に置く(`RpcError` と同じ階層)。`Resident` は `:domain`。

### 3.4 Controller の移行

- 保護コントローラ(Catalog/Household/Product/Stock/…): `guarded(session){…requireResidentId()}` → `requireRegistered(session){ residentId -> … }`。
- `ResidentRegisterController`(register): `allowUnregistered(session){ … }`(未登録ユーザが初回登録するため)。
- `SessionController`(whoami): `allowUnregistered`。

### 3.5 `ResidentRpcService.me()` の扱い

- 現状 `me()` の唯一の用途は frontend boot の登録判定。これは `whoami()` に置き換わる。
- 実装計画時に**他の呼び出し元を grep で確認**し、無ければ `ResidentRpcService` / `ResidentController` / `me()` を削除する(`residentService.me(residentId)` という application 層メソッドは whoami や他 Scenario が使うので残す)。他に呼び出し元があれば `requireRegistered` 化して残す。

## 4. frontend 設計

### 4.1 `RpcClientProvider`(単一接続)

```kotlin
class RpcClientProvider(http: HttpClient, baseUrl: String) {
    // /api/rpc に 1 本だけ張る。mindstock.v1 + mindstock.bearer.<jwt> をサブプロトコルで送る。
    fun connect(accessToken: String): Unit          // 既存接続があれば張り直す
    inline fun <reified T> service(): T              // 単一 client から withService<T>()
    fun close()                                      // 再認証/ログアウト時
}
```

- 現行の `open(path, token)`(per-service)は廃止。各 Repository は `provider.service<XxxRpcService>()` を受け取る。
- 接続は有効トークン取得後に 1 回 `connect()`。再認証/ログアウトで `close()` → 必要なら再 `connect()`。
- 自動再接続は対象外(`Unauthorized → 再認証` は P6-1 の導線)。

### 4.2 boot 分岐

- 現行「`me()` の WS 例外を捕まえて未登録判定」を廃止。
- boot: `connect(token)` → `service<SessionRpcService>().whoami()`:
  - `Registered(resident)` → home(StockHome)へ。resident をそのまま使える。
  - `Unregistered` → onboarding(P6-3)へ。
- `WebAuthDeps` の登録判定ロジックを whoami ベースに差し替え。

## 5. バージョニング規約

- 契約のバージョンは**サブプロトコルで交渉**する(`mindstock.v1`)。URL には出さない。
- 今回は v1 のみ。将来の v2 は「`mindstock.v2` をサブプロトコルに追加し、サーバが対応版を選ぶ」方式で、URL は不変。**今回コードでの v2 対応は実装しない**(規約の明文化のみ)。

## 6. テスト

- **単体**:
  - `requireRegistered` / `allowUnregistered`:登録必須で Unregistered は Unauthorized(fail-closed)/ 認証のみは未登録通過 / `exp` 切れ Unauthorized / ドメイン例外→RpcError 翻訳。
  - `whoami`:Registered/Unregistered の分岐。
- **e2e(CIO 実機 + 実 kRPC client、testApplication は WS 不可なので不採用)**:
  - 単一 `/api/rpc` に複数サービス相乗りで 1 接続から両方呼べる。
  - 未登録 JWT:`whoami → Unregistered`、保護メソッドは `Unauthorized`、`register` は成功。
  - 登録済み JWT:`whoami → Registered`、保護メソッド成功。
  - トークン無し:ハンドシェイクで 401(接続不可)。
- 既存 `WsUpgradeAuthTest` / `KrpcWsUpgradeAuthTest` は新構造へ作り替え。

## 7. 移行・廃棄

- big-bang 入れ替え(後方互換なし)。
- **削除**:`RequireRegisteredUserPlugin`(+ そのテスト)、per-service ルーティング、`guarded`(→ 2 ヘルパーへ)、(条件付き)`ResidentRpcService.me()`。
- **PR #109(案A 暫定修正)は close**。WS e2e の検証手法だけ本実装へ引き継ぐ。

## 8. 対象外(YAGNI)

- 認証不要エンドポイント `/api/public`(将来兄弟として追加)。
- v2 の実装 / 自動再接続 / IdP revocation 追従。

## 9. 未決(実装計画前に確定)

1. `ResidentRpcService.me()` を削除してよいか(§3.5、呼び出し元 grep 次第)。
2. 検証用に local DB へ手挿入した admin の resident 行(display_name=Admin)を残すか消すか。

## 10. 影響範囲(ファイル目安)

- `:rpc`:`session/SessionRpcService.kt`・`session/SessionStatus.kt` 追加。
- `:backend:api`:`RoutingConfiguration.kt`(単一エンドポイント化)、`configuration/guard/`(ヘルパー 2 つ)、`presentation/rpc/session/SessionController.kt` 追加、各 Controller 移行、`RequireRegisteredUserPlugin.kt` + テスト削除。
- `:frontend`:`core/rpc/RpcClientProvider.kt`、`WebAuthDeps.kt`、boot/ViewModel の登録判定、各 Repository の service 取得。
- ルール doc:`frontend-rpc-and-error.md`(「me() が WS 例外」記述を whoami ベースへ更新)。
