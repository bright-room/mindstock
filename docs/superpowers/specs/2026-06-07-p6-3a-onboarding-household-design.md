# P6-3a 設計: オンボーディング + 世帯作成/参加

家庭の在庫管理 SaaS「mindstock」フルリプレイスの P6-3a。boot 後の `NeedOnboarding` /
`NeedHousehold` プレースホルダを実機能に置き換え、初回ユーザが **表示名登録 → 世帯作成
または招待コード参加 → アプリ本体(Ready)** まで到達できるようにする。アクティブ世帯を
リロードまたいで永続化する。

- 起点: 画面仕様(`docs/ref/mindstock.zip` の `screens-onboard.jsx` / `screens-household.jsx`)。
  見た目はモックに寄せる(`frontend-visual-fidelity-expectation`)。
- 前提: backend は P2-P5 で完全実装済み(domain / rpc / service / controller / datasource /
  schema / 認証フロー)。**P6-3a は純粋なフロントエンド feature 追加**で、既存 RPC を配線する。
- 既存パターン踏襲: feature = `ViewModel` + `UiState`(sealed) + `data/Repository`(RPC 隠蔽) + `ui/`。
  ViewModel は `suspend (...) -> RpcOutcome<...>` を constructor 注入。Repository は service の
  「開く関数」を遅延注入。エラーは `RpcOutcome` + `requiresReauth()→reauth` / それ以外 `toast`。

## P6-3 全体の分割

| サブ | 範囲 | 本 spec |
|---|---|---|
| **P6-3a** | オンボーディング(表示名登録)+ 世帯作成 + 招待コード参加(アプリ内)+ アクティブ世帯永続化 | ◎ 本書 |
| P6-3b | 設定タブ刷新・世帯切替シート・メンバー権限変更/除外・招待発行/失効・世帯名変更・住人 rename・退出 | 別 spec(後続) |

P6-3 全体で **QR コード生成** と **ディープリンク参加(`/join/CODE`)** は見送り(P6-2 のカメラ
見送りと同方針)。招待参加は設定/空状態の「6桁コード入力」で行う。

## 既存資産(調査済み・変更しないもの)

backend(すべて実装済み・本 spec で利用):

- `SessionRpcService.whoami(): RpcResult<SessionStatus, RpcError>` — `Registered(resident)` /
  `Unregistered`。`allowUnregistered`。
- `ResidentRegisterRpcService.registerDisplayName(displayName): RpcResult<Resident, RpcError>` —
  `allowUnregistered`。**既に登録済みなら `RpcError.Conflict`** を返す。
- `HouseholdRpcService.list(): RpcResult<Households, RpcError>` / `previewInvite(code): RpcResult<InvitationPreview, RpcError>`
  — `requireRegistered`。`InvitationPreview(householdName, role)`。
- `HouseholdRegisterRpcService.create(name): RpcResult<Household, RpcError>` /
  `join(code): RpcResult<Household, RpcError>` — `requireRegistered`。

frontend(実装済み・本 spec で拡張または利用):

- `core/auth/AuthState`(Booting / Ready / NeedOnboarding / NeedHousehold / Failed)。
- `app/AuthViewModel` + `app/AuthDeps`(boot の副作用境界)+ `webMain/WebAuthDeps`(web 実装)。
- `core/session/AppSession`(residentId / displayName / households / activeHouseholdId)。
- `core/rpc/RpcClientProvider`(単一 WS `/api/rpc`・`connect` / `close` / `service<T>()`)、
  `RpcOutcome` / `toOutcome()` / `errorText()` / `requiresReauth()`。
- `core/ui/ToastController` / `core/auth/ReauthController` / `core/ui/UiText`。
- `auth/SessionStorage`(expect/actual・**sessionStorage**)、`auth/TokenStore`、`auth/Pkce` 等。
- designsystem atom: `Sheet` / `TextInput` / `PrimaryButton`(`PrimaryButton`)/ `SegmentedControl` /
  `HouseholdPill` / `AppIcon`(`AppIconName`)/ `AppText` / `EmptyState` 等。

## 設計上の核心: 登録後の WS 再接続(★)

backend のセッション種別(`Registered` / `Unregistered`)は **WS ハンドシェイク時に 1 回だけ**
決まる(`MindstockAuthPlugin` の `onCall` が `ResidentRepository.findByAuth` を引いて
`call.attributes` に `MindstockSession` を格納。RPC service impl は per-connection)。

したがって `Unregistered` 接続上で `registerDisplayName` が Resident を作っても、**その接続の
セッションは `Unregistered` のまま**であり、続く `create` / `join` / `list`(すべて
`requireRegistered`)は `Unauthorized` になる。

**対策**: `registerDisplayName` 成功後、frontend は WS を **再接続**(`rpc.close()` →
`rpc.connect(token)`)し、ハンドシェイクを再実行させて `Registered` セッションを獲得してから
世帯系 RPC を呼ぶ。`rpc.service<T>()` は呼び出しごとに解決される(opener lambda)ので、再接続後の
サービス取得は新接続を使う。

## アーキテクチャ

### 1. 状態遷移 — `AuthViewModel` を coordinator 化(`AuthFlow`)

`AuthViewModel` は既に `AuthState` と(`AuthDeps` 経由で)`AppSession` 反映を握る。feature VM が
完了時に呼び戻す薄い interface を `AuthViewModel` に実装させる:

```kotlin
// app/ に新規。AuthViewModel が実装し、Onboarding/NeedHousehold VM に注入する。
interface AuthFlow {
    /** 登録済み Resident を session に反映し、WS を再接続して Registered セッションを獲得する(★)。失敗時 throw。 */
    suspend fun onResidentRegistered(resident: Resident)

    /** 世帯一覧を再ロードし、activeId をアクティブにして session 反映+永続化し、Ready に遷移。失敗時 throw。 */
    suspend fun enterApp(activeId: HouseholdId)

    /** 世帯ゼロ(スキップ)へ。NeedHousehold に遷移。 */
    fun needHousehold()
}
```

`AuthViewModel` の実装:

```kotlin
override suspend fun onResidentRegistered(resident: Resident) {
    deps.onAuthenticated(resident)
    val token = deps.loadValidToken() ?: error("token lost")
    deps.reconnect(token) // close + connect → 再ハンドシェイク → Registered
}

override suspend fun enterApp(activeId: HouseholdId) {
    val households = deps.loadHouseholds()
    deps.onHouseholdsLoaded(households, activeId)
    deps.persistActiveHousehold(activeId)
    _state.value = AuthState.Ready
}

override fun needHousehold() { _state.value = AuthState.NeedHousehold }
```

`AuthDeps` への追加メソッド:

```kotlin
/** WS を貼り直す(close → connect)。登録直後のセッション昇格用。 */
suspend fun reconnect(token: Tokens)
/** アクティブ世帯を永続化(localStorage)。 */
fun persistActiveHousehold(id: HouseholdId)
/** 永続化済みアクティブ世帯。無ければ null。 */
fun savedActiveHousehold(): HouseholdId?
```

### 2. boot のアクティブ世帯選択を「保存済み優先」に変更

`AuthViewModel.boot()` の `Registered` 分岐:

```kotlin
val households = deps.loadHouseholds()
val saved = deps.savedActiveHousehold()
val active = households.list.firstOrNull { it.id == saved } ?: households.list.firstOrNull()
if (active == null) {
    _state.value = AuthState.NeedHousehold
} else {
    deps.onHouseholdsLoaded(households, active.id)
    deps.persistActiveHousehold(active.id) // fallback で先頭になった場合も保存を揃える
    _state.value = AuthState.Ready
}
```

### 3. アクティブ世帯永続化 — `core` に `PreferenceStore`

`auth/SessionStorage`(sessionStorage)と同じ expect/actual 構成で、**localStorage** backed の
薄いキー値ストアを `core/` に追加(リロード・タブ復帰をまたいで保持する要件のため localStorage)。

- `commonMain core/preference/PreferenceStore`(expect object: `get(key)` / `set(key, value)` / `remove(key)`)
- `jsMain` / `wasmJsMain` で `window.localStorage` を actual 実装。
- キー: `mindstock.active_household.v1`(値は `HouseholdId` の Uuid 文字列)。
- `WebAuthDeps` が `persistActiveHousehold` / `savedActiveHousehold` でこれを読み書き
  (`HouseholdId` ⇄ String の変換は WebAuthDeps 側)。

### 4. Repository(RPC 隠蔽・service opener 遅延注入)

```kotlin
// feature/resident/data/ResidentRepository.kt
class ResidentRepository(
    private val residentRegisterService: () -> ResidentRegisterRpcService,
) {
    suspend fun register(displayName: DisplayName): RpcOutcome<Resident> =
        residentRegisterService().registerDisplayName(displayName).toOutcome()
    // 3b: rename(displayName) を追加
}

// feature/household/data/HouseholdRepository.kt
class HouseholdRepository(
    private val householdService: () -> HouseholdRpcService,
    private val householdRegisterService: () -> HouseholdRegisterRpcService,
) {
    suspend fun create(name: HouseholdName): RpcOutcome<Household> =
        householdRegisterService().create(name).toOutcome()
    suspend fun join(code: InvitationCode): RpcOutcome<Household> =
        householdRegisterService().join(code).toOutcome()
    suspend fun previewInvite(code: InvitationCode): RpcOutcome<InvitationPreview> =
        householdService().previewInvite(code).toOutcome()
    // 3b: rename / leave / changeRole / removeMember / createInvite / revokeInvite を追加
}
```

App.kt で `RpcClientProvider` の `service<T>()` を opener として渡して生成する(既存 Repository と同様)。

### 5. ViewModel

`OnboardingViewModel`(`feature/onboarding/`):

```kotlin
class OnboardingViewModel(
    private val registerDisplayName: suspend (DisplayName) -> RpcOutcome<Resident>,
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    val state: StateFlow<OnboardingUiState> // step + 入力 + submitting
    fun next() / fun back() / fun setName(...) / fun setHouseholdName(...)
    suspend fun submit() // 確認 step の確定
}
```

`submit()` のロジック:

1. `register(displayName)` を呼ぶ。
   - `Success(resident)` → `flow.onResidentRegistered(resident)`。
   - `Failure(Conflict)` → 既に登録済み。MVP の未登録導線では通常起きないため、`errorText` を
     toast 表示し step に留まる(完全な冪等回復は 3b 以降で検討)。
   - `Failure(Unauthorized)` → `reauth.request()`。
   - その他 `Failure` → `errorText` を toast、step に留まる。
2. 世帯名が入力済みなら `createHousehold(name)`:
   - `Success(h)` → `flow.enterApp(h.id)`。
   - `Failure(Unauthorized)` → `reauth.request()`。
   - その他 `Failure` → `errorText` を toast。Resident は作成済みなので step には戻さず
     `flow.needHousehold()` に逃がす(NeedHousehold 画面で世帯作成/参加をやり直せる)。
3. 世帯名が空(スキップ)なら `flow.needHousehold()`。

`NeedHouseholdViewModel`(`feature/household/`):

```kotlin
class NeedHouseholdViewModel(
    private val createHousehold: suspend (HouseholdName) -> RpcOutcome<Household>,
    private val previewInvite: suspend (InvitationCode) -> RpcOutcome<InvitationPreview>,
    private val joinByCode: suspend (InvitationCode) -> RpcOutcome<Household>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    suspend fun create(name: HouseholdName)        // → flow.enterApp(h.id)
    suspend fun preview(code: InvitationCode)      // 参加前プレビュー取得(state に反映)
    suspend fun join(code: InvitationCode)         // → flow.enterApp(h.id)
}
```

両 VM とも失敗は `requiresReauth()→reauth.request()` / それ以外 `toast.show(errorText(...))`。

### 6. UI(モック忠実)

- `feature/onboarding/ui/OnboardingScreen` — 4 step ウィザード(`screens-onboard.jsx` 準拠):
  - step0 ようこそ(ロゴ・2 項目の手順カード・「はじめる」)
  - step1 表示名(`FormStep`: アイコン・eyebrow・title・sub・入力・文字数・最大 100)
  - step2 世帯名(任意。候補チップ「わたしの家/田中家/自宅」・最大 50・「確認する」「あとで設定する(スキップ)」)
  - step3 確認(表示名 + 世帯のカード・「mindstock を始める」「修正する」)
  - 上部: 戻るボタン + 進捗バー(`WizardProgress`)+ `step / 2`。
- `feature/household/ui/NeedHouseholdScreen` — `NoHouseholdCard` 相当のフルスクリーン
  (`screens-household.jsx`): 「まだ世帯がありません」+「世帯をつくる」「招待コードで参加」。
- `feature/household/ui/CreateHouseholdSheet` — 名前入力 + 候補チップ + 「この世帯をつくる」。
- `feature/household/ui/JoinCodeSheet` — 6桁コード入力(大文字英数・モノスペース・中央寄せ)。
  入力が 6 桁になったら `preview` を引いて世帯プレビュー(世帯名・付与ロール)を表示 → 「参加する」で `join`。
  プレビュー取得失敗(無効コード等)は inline メッセージ + toast。

これらの Sheet/Screen は P6-3b の設定タブからも再利用する(`feature/household/ui/` に置く)。

### 7. designsystem 追加

- `AppIconName` に追加(`AppIcon.kt` の `vector()` にマップ):
  - `Check` → `Icons.Outlined.Check`
  - `Link` → `Icons.Outlined.Link`
  - `Users` → `Icons.Outlined.Group`
- 新 atom(feature は material3 を直接 import しない方針のため atom 化):
  - `designsystem/atom/WizardProgress` — セグメント進捗バー(total / current)。
  - `designsystem/atom/SuggestionChips` — 候補文字列のチップ行(クリックで値セット)。
- 既存 atom(`Sheet` / `TextInput` / `PrimaryButton` / `AppText` / `AppIcon` / `EmptyState`)を流用。
  オンボーディングのグラデーション背景・カード等は foundation レイアウト + theme トークンで構成
  (material3 コンポーネントは使わない)。

### 8. App.kt 配線

`when (state)` の分岐を実装で置換:

- `AuthState.NeedOnboarding` → `OnboardingScreen` + `OnboardingViewModel`(`vm` を `AuthFlow` として注入)。
- `AuthState.NeedHousehold` → `NeedHouseholdScreen` + `NeedHouseholdViewModel`。
- `AuthState.Ready` の `activeHouseholdId == null` フォールバック表示も `NeedHouseholdScreen` に統一可。

`ResidentRepository` / `HouseholdRepository` を `remember` で生成(opener = `{ rpc.service<...>() }`)。
`AuthViewModel`(= `AuthFlow`)を両 VM に渡す。

### 9. 文言(strings.xml)

既存 `onboarding_placeholder` / `need_household` を実文言群に置換・追加(抜粋):
オンボーディング各 step のタイトル/サブ/プレースホルダ/ボタン、候補チップ、世帯作成・参加シートの
コピー、6桁コードのヒント、各種ボタン/トースト(作成しました・参加しました等)。

## エラー処理方針

- 全 RPC は `RpcOutcome`。`Failure(error)` で `error.requiresReauth()`(=`Unauthorized`)なら
  `reauth.request()`(token 破棄 → authorize redirect)、それ以外は `toast.show(errorText(error))`。
- `AuthFlow.onResidentRegistered` / `enterApp` の例外(通信失敗等)は VM 側 `runCatching` で捕捉し
  toast 表示(`CancellationException` は再 throw)。回復不能な状態には落とさない。
- `registerDisplayName` の `Conflict`(登録済)は MVP の通常導線では発生しない。発生時は toast で
  通知し step に留める(簡略化。完全な冪等回復は 3b 以降で検討)。

## テスト(commonTest / `kotlin.test.@Test` + Kotest assertions・ロジックのみ)

`frontend-kmp-test-style`: commonTest は Kotest FunSpec 不可。`@Test` + `shouldBe` 等を使う。
UI 描画網羅はしない。fake の `AuthFlow` / suspend lambda / `ToastController` / `ReauthController` を使う。

- **OnboardingViewModel**
  - 世帯名あり確定 → `register` 成功後 `onResidentRegistered` → `createHousehold` 成功 → `enterApp(h.id)` が呼ばれる。
  - スキップ(世帯名空)確定 → `register` 成功後 `needHousehold()` が呼ばれる。
  - `register` が `Failure(Internal)` → toast、`onResidentRegistered`/`enterApp` 呼ばれず、step に留まる。
  - `register` が `Failure(Unauthorized)` → `reauth.request()`。
  - step 遷移(next/back・入力反映)。
- **NeedHouseholdViewModel**
  - `create` 成功 → `enterApp(h.id)`。
  - `preview` 無効コード(`Failure(NotFound)`)→ state にエラー反映 + toast、`join` 呼ばれず。
  - `join` 成功 → `enterApp(h.id)`。
  - `Unauthorized` → `reauth.request()`。
- **AuthViewModel(boot / coordinator)**
  - boot: 保存済み active が一覧に居る → それを active にして Ready + persist。
  - boot: 保存済み active が一覧に無い/null → 先頭を active にして Ready + persist。
  - boot: 世帯ゼロ → NeedHousehold。
  - `onResidentRegistered` → `deps.onAuthenticated` と `deps.reconnect` が呼ばれる。
  - `enterApp(id)` → `loadHouseholds`→`onHouseholdsLoaded(.., id)`→`persistActiveHousehold(id)`→Ready。
  - 既存 boot テスト(Registered/Unregistered/Failed)が壊れないこと。

## 受け入れ条件

1. 未登録ユーザがログイン → オンボーディングで表示名登録 → 世帯作成 → 在庫タブ(Ready)に到達できる。
2. オンボーディングで世帯をスキップ → NeedHousehold 画面 → 「世帯をつくる」で作成 → Ready。
3. NeedHousehold 画面 → 「招待コードで参加」→ 有効コードでプレビュー → 参加 → Ready。
4. 無効な招待コードはプレビュー段でエラー表示され、参加できない。
5. アクティブ世帯がリロードをまたいで保持される(localStorage)。
6. オンボーディング/世帯画面の見た目がモック(`screens-onboard` / `screens-household`)に寄っている。
7. `./gradlew build`(WasmJs を除く)と `./gradlew test` が通る(`local-build-tips`)。

## 非目標(本 spec では扱わない)

- 設定タブ刷新・世帯切替シート(複数世帯間の切替 UI)・住人 rename・世帯名変更・退出・
  メンバー権限変更/除外・招待発行/失効 → **P6-3b**。
- QR コード生成・ディープリンク参加(`/join/CODE`)→ P6-3 全体で見送り。
- お知らせ通知・オフライン閲覧・消費傾向 → 将来対応(UI は無効/OFF 固定)。
