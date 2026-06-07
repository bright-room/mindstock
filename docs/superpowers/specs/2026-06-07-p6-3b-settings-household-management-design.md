# P6-3b 設計: 設定タブ刷新 + 世帯管理

家庭の在庫管理 SaaS「mindstock」フルリプレイスの P6-3b。P6-3a でオンボーディング/世帯作成・参加・
アクティブ世帯永続化まで到達したので、本 spec は **設定タブを実機能化**する。素の `ProfileScreen`
(マスタ/アーカイブ 2 行のみ)を、モック `screens-d.jsx` の `Profile` 相当のフル設定画面に刷新し、
**世帯切替 / メンバー権限変更・除外 / 招待発行・失効 / 世帯名変更 / 住人(表示名)rename / 退出** を配線する。

- 起点: 画面仕様(`docs/ref/mindstock.zip` の `screens-d.jsx`(設定)/ `screens-household.jsx`(切替シート)/
  `screens-invite.jsx`(招待・メンバーシート))。見た目はモックに寄せる(`frontend-visual-fidelity-expectation`)。
- 前提: backend は P2-P5 で完全実装済み。**P6-3b は純粋なフロントエンド feature 追加**で、既存 RPC を配線する。
- 既存パターン踏襲(P6-3a 確立): feature VM が RPC + エラー処理を持ち、`AuthFlow`(= `AuthViewModel`)が
  session 変更 + `AuthState` 遷移を coordinator として握る。Repository は service opener を遅延注入。
  エラーは `RpcOutcome` + `requiresReauth()→reauth` / それ以外 `toast.show(errorText(...))`。

## P6-3 全体の分割(再掲)

| サブ | 範囲 | 本 spec |
|---|---|---|
| P6-3a | オンボーディング + 世帯作成 + 招待コード参加 + アクティブ世帯永続化 | 済(PR #115) |
| **P6-3b** | 設定タブ刷新・世帯切替シート・メンバー権限変更/除外・招待発行/失効・世帯名変更・住人 rename・退出 | ◎ 本書 |

P6-3 全体で **QR コード生成** と **ディープリンク参加(`/join/CODE`)** は見送り(P6-3a 同方針)。
招待は **6 桁コードのみ**で行う。

## 既存資産(調査済み・本 spec で利用/拡張)

### backend(すべて実装済み)

`HouseholdRpcService`(`requireRegistered`):
- `list(): RpcResult<Households, RpcError>` — 所属世帯一覧。**`Households` は member graph(`Members` = `HouseholdMember(resident, role)` のファーストクラスコレクション)を丸ごと hydrate 済み**。よって設定画面のメンバー表示・ロール表示は session の `households` から導出でき、別フェッチ不要。
- `previewInvite(code): RpcResult<InvitationPreview, RpcError>`

`HouseholdRegisterRpcService`(`requireRegistered`):
- `rename(householdId, name): RpcResult<Unit, RpcError>` — owner のみ(UC6)
- `leave(householdId): RpcResult<Unit, RpcError>` — UC7
- `changeRole(householdId, target, role): RpcResult<Unit, RpcError>` — owner のみ(UC9)
- `removeMember(householdId, target): RpcResult<Unit, RpcError>` — owner のみ(UC9)
- `createInvite(householdId, role): RpcResult<Invitation, RpcError>` — owner のみ(UC8、role 指定・**再発行**)
- `revokeInvite(code): RpcResult<Unit, RpcError>` — owner のみ(UC8)
- `create(name)` / `join(code)` — P6-3a で配線済(切替シートから再利用)

`ResidentRegisterRpcService`(`allowUnregistered` は register のみ):
- `rename(displayName): RpcResult<Unit, RpcError>` — 表示名変更(住人 rename)

ドメイン型:
- `Household(id, profile: Profile(name), members: Members)`、`HouseholdMember(resident: Resident, role: HouseholdMemberRole)`
- `HouseholdMemberRole`: `世帯主` / `メンバー` / `閲覧者`
- `Invitation(householdId, code: InvitationCode, grantedRole, validity)`

### 例外 → RpcError マッピング(`configuration/guard/SessionGuard.kt`)

- `LastOwnerException` → `RpcError.Conflict(reason = e.message)`(最後の owner の退出/降格/除外)
- `OwnerRequiredException` → Conflict(owner 限定操作を非 owner が叩いた場合。UI では非表示なので防御)
- `MembershipRequiredException` → Conflict
- `IllegalArgumentException`(VO 値域・不変条件)→ `RpcError.BadRequest`
- `reason` は**技術英語メッセージ**(例 `"last owner cannot leave: $id"`)。`errorText(Conflict)` はこの reason を
  そのまま埋め込むため、本 spec では last-owner 系操作のみ VM 側でコンテキスト固有の文言に寄せる(後述)。

### frontend(実装済み・本 spec で拡張または利用)

- `core/auth/AuthState`(Booting / Ready / NeedOnboarding / NeedHousehold / Failed)。
- `app/AuthViewModel`(`AuthFlow` 実装)+ `app/AuthDeps` + `webMain/WebAuthDeps`。
- `app/AuthFlow`(coordinator interface)。
- `core/session/AppSession`(`residentId` / `displayName` / `households` / `activeHouseholdId` と setter
  `setResident` / `setHouseholds` / `setActiveHousehold` / `clear`)。
- `app/Ownership.kt`(`isOwner(households, activeId, residentId): Boolean`)。
- `core/rpc/`(`RpcOutcome` / `toOutcome` / `errorText` / `requiresReauth`)。`errorText` は `RpcError` 網羅。
- `core/ui/ToastController` / `core/auth/ReauthController` / `core/ui/UiText`。
- `core/preference/PreferenceStore`(localStorage backed・P6-3a 追加)。
- `feature/household/data/HouseholdRepository`(create / join / previewInvite。**本 spec で 6 メソッド追加**)。
- `feature/resident/data/ResidentRepository`(register。**本 spec で rename 追加**)。
- `feature/household/ui/`(`NeedHouseholdScreen` / `CreateHouseholdSheet` / `JoinCodeSheet`。**本 spec で switcher / invite / member シート追加**)。
- designsystem atom: `Sheet` / `TextInput` / `PrimaryButton` / `SegmentedControl` / `HouseholdPill` /
  `AppIcon`(`AppIconName`)/ `AppText` / `EmptyState` / `SuggestionChips` / `RoundBtn` 等。
- `app/profile/ProfileScreen`(**本 spec で設定画面に刷新・移設**)。

## 設計上の核心

### 世帯切替に WS 再接続は不要

P6-3a の ★(登録直後の WS 再接続)は backend セッション種別(`Registered`/`Unregistered`)が
**ハンドシェイク時に 1 回だけ**決まることへの対処だった。一方 **アクティブ世帯は純粋なクライアント
状態**である:全 RPC は `householdId` を引数で受け、backend に「現在の世帯」という概念は無い。
したがって世帯切替は `AppSession.activeHouseholdId` の更新 + 永続化のみで成立する。各画面 VM は
`App.kt` で `remember(householdId)` により re-key され、切替後に自動でロードし直す。

### 招待は明示発行式(get RPC が無い制約)

backend には `createInvite`(発行/再発行)と `revokeInvite` はあるが、**現在有効な招待を取得する
read RPC が無い**。モックはシート展開時に自動発行する(`if (!store.invite) store.createInvite(...)`)が、
これを真似ると **オーナーがシートを開くたびに新コードが発行され、前のコードが孤児化**する。

**採用**: シート展開では発行しない。オーナーが明示的に「招待コードを発行」を押して初めて
`createInvite` を呼ぶ。発行後はコード表示 + コピー + 「新しいコード」(再発行)+ 「失効する」(revoke)。
発行した `Invitation` は **VM メモリにのみ保持**(リロードで消える = 再発行が必要)。設定画面に
「招待中」を常時表示する導線は出さない(検証できない validity 表示を出さないため)。これはモックからの
意図的な乖離であり、`frontend-visual-fidelity-expectation` の例外として本 spec で明記する。

## アーキテクチャ

### 1. `AuthFlow` / `AuthDeps` の拡張(boot テストを壊さない最小追加)

`app/AuthFlow.kt` に追加:

```kotlin
interface AuthFlow {
    // 既存(P6-3a)
    suspend fun onResidentRegistered(resident: Resident)
    suspend fun enterApp(activeId: HouseholdId)
    fun needHousehold()

    // P6-3b 追加
    /** アクティブ世帯を切り替える(WS 再接続なし)。session 反映 + 永続化。 */
    fun switchActiveHousehold(id: HouseholdId)

    /** 世帯一覧を再ロードし、現在のアクティブを維持して session 反映(rename/role/member/invite 後)。失敗時 throw。 */
    suspend fun refreshHouseholds()

    /** 表示名を session に反映する(resident rename は Unit 戻りのため VM が DisplayName を渡す)。 */
    fun applyDisplayName(name: DisplayName)

    /** アクティブ世帯から退出した後の再ロード + アクティブ再選択。退出先が active だったら残りの先頭、ゼロなら NeedHousehold。失敗時 throw。 */
    suspend fun leaveActiveHousehold()
}
```

`AuthViewModel` の実装:

```kotlin
override fun switchActiveHousehold(id: HouseholdId) {
    deps.setActiveHousehold(id)
    deps.persistActiveHousehold(id)
}

override suspend fun refreshHouseholds() {
    val households = deps.loadHouseholds()
    val active = deps.currentActiveHousehold() ?: households.list.firstOrNull()?.id
    if (active == null) {
        _state.value = AuthState.NeedHousehold
    } else {
        deps.onHouseholdsLoaded(households, active)
    }
}

override fun applyDisplayName(name: DisplayName) {
    deps.setDisplayName(name)
}

override suspend fun leaveActiveHousehold() {
    val households = deps.loadHouseholds()
    val current = deps.currentActiveHousehold()
    val active = households.list.firstOrNull { it.id == current }?.id ?: households.list.firstOrNull()?.id
    if (active == null) {
        deps.onHouseholdsLoaded(households, /* active を持たない反映 */) // ↓注記参照
        _state.value = AuthState.NeedHousehold
    } else {
        deps.onHouseholdsLoaded(households, active)
        deps.persistActiveHousehold(active)
    }
}
```

注記: `onHouseholdsLoaded(households, active)` の `active` は現状 non-null `HouseholdId`。世帯ゼロ時の反映に
`AppSession.setHouseholds(households, active = null)` が必要なため、`AuthDeps` に世帯ゼロ反映用の経路を足す
(`onHouseholdsCleared(households)` を追加し、`WebAuthDeps` で `session.setHouseholds(households, null)` を呼ぶ)。
これにより nullable を公開 API に漏らさず(`onHouseholdsLoaded` は non-null のまま)、ゼロ件遷移を表現する。

`AuthDeps` 追加メソッド(`WebAuthDeps` で `AppSession` の既存 setter に委譲):

```kotlin
fun setActiveHousehold(id: HouseholdId)        // session.setActiveHousehold(id)
fun setDisplayName(name: DisplayName)          // session.setResident(currentResidentId, name) 相当
fun currentActiveHousehold(): HouseholdId?     // session.state.value.activeHouseholdId
fun onHouseholdsCleared(households: Households) // session.setHouseholds(households, null)
```

`setDisplayName` 実装注記: `AppSession.setResident(residentId, displayName)` は両方を要求するため、
`AppSession` に `setDisplayName(name)`(`update { it.copy(displayName = name) }`)を 1 つ足し、`WebAuthDeps` が委譲する。

### 2. Repository 拡張

```kotlin
// feature/household/data/HouseholdRepository.kt（追加）
suspend fun rename(householdId: HouseholdId, name: HouseholdName): RpcOutcome<Unit> =
    householdRegisterService().rename(householdId, name).toOutcome()
suspend fun leave(householdId: HouseholdId): RpcOutcome<Unit> =
    householdRegisterService().leave(householdId).toOutcome()
suspend fun changeRole(householdId: HouseholdId, target: ResidentId, role: HouseholdMemberRole): RpcOutcome<Unit> =
    householdRegisterService().changeRole(householdId, target, role).toOutcome()
suspend fun removeMember(householdId: HouseholdId, target: ResidentId): RpcOutcome<Unit> =
    householdRegisterService().removeMember(householdId, target).toOutcome()
suspend fun createInvite(householdId: HouseholdId, role: HouseholdMemberRole): RpcOutcome<Invitation> =
    householdRegisterService().createInvite(householdId, role).toOutcome()
suspend fun revokeInvite(code: InvitationCode): RpcOutcome<Unit> =
    householdRegisterService().revokeInvite(code).toOutcome()

// feature/resident/data/ResidentRepository.kt（追加）
suspend fun rename(displayName: DisplayName): RpcOutcome<Unit> =
    residentRegisterService().rename(displayName).toOutcome()
```

### 3. `SettingsViewModel`(`feature/household/`)

世帯にロジックが集中するため household feature に置く(住人 rename は注入された 1 関数)。
`AppSession.state` を購読して UI state を導出し、操作は Repository → `AuthFlow` で session 反映。

```kotlin
class SettingsViewModel(
    session: AppSession,
    private val renameDisplayNameRpc: suspend (DisplayName) -> RpcOutcome<Unit>,
    private val renameHouseholdRpc: suspend (HouseholdId, HouseholdName) -> RpcOutcome<Unit>,
    private val changeRoleRpc: suspend (HouseholdId, ResidentId, HouseholdMemberRole) -> RpcOutcome<Unit>,
    private val removeMemberRpc: suspend (HouseholdId, ResidentId) -> RpcOutcome<Unit>,
    private val leaveRpc: suspend (HouseholdId) -> RpcOutcome<Unit>,
    private val createInviteRpc: suspend (HouseholdId, HouseholdMemberRole) -> RpcOutcome<Invitation>,
    private val revokeInviteRpc: suspend (InvitationCode) -> RpcOutcome<Unit>,
    private val flow: AuthFlow,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    val state: StateFlow<SettingsUiState>   // session 由来 + 発行済み invite(VM メモリ) + submitting フラグ

    suspend fun renameDisplayName(name: DisplayName)         // 成功 → flow.applyDisplayName(name)
    suspend fun renameHousehold(name: HouseholdName)         // 成功 → flow.refreshHouseholds()
    suspend fun changeRole(target: ResidentId, role: HouseholdMemberRole) // 成功 → flow.refreshHouseholds()
    suspend fun removeMember(target: ResidentId)             // 成功 → flow.refreshHouseholds()
    suspend fun leave()                                      // 成功 → flow.leaveActiveHousehold()
    suspend fun createInvite(role: HouseholdMemberRole)      // 成功 → state に Invitation 反映(session 変更なし)
    suspend fun revokeInvite()                               // 成功 → state の invite クリア
    fun switchHousehold(id: HouseholdId)                     // flow.switchActiveHousehold(id)
}
```

`SettingsUiState`(`sealed` は不要・単一 data class でよい。session が常に Ready 前提):

```kotlin
data class SettingsUiState(
    val displayName: String,
    val households: List<HouseholdSummary>,   // 切替シート用(id / name / 人数 / 自分のロール / active か)
    val activeId: HouseholdId?,
    val activeName: String,
    val members: List<MemberRow>,             // resident / role / 自分か
    val isOwner: Boolean,
    val issuedInvite: Invitation?,            // VM メモリのみ
    val submitting: Boolean,
)
```

導出ヘルパー(`app/Ownership.kt` の `isOwner` を流用 + 必要なら member/summary マッピングを feature 内に追加)。

エラー処理(全メソッド共通):
- `Failure(error)` で `error.requiresReauth()` → `reauth.request()`。
- それ以外は `toast.show(errorText(error))`。ただし **last-owner 系**(`leave`/`removeMember`/`changeRole`)で
  `Conflict` が返った場合は、reason の技術英語を出さず **コンテキスト固有の文言**(strings.xml)を toast:
  - `leave` Conflict → 「最後のオーナーは退出できません。先に別のメンバーをオーナーにしてください。」
  - `removeMember` Conflict → 「最後のオーナーは外せません。」
  - `changeRole` Conflict → 「最後のオーナーの権限は変更できません。」
  - 簡略化: VM は当該操作で `Conflict` を受けたら専用文言、その他 variant は `errorText` 経由。
- `AuthFlow` 呼び出し(refresh/leave)の例外は `runCatching` で捕捉し toast(`CancellationException` は再 throw)。

### 4. UI(モック忠実)

設定画面は app 層の合成画面(住人 + 世帯 + catalog ナビ横断)。現 `app/profile/ProfileScreen` を
**`app/settings/SettingsScreen`** に刷新する(現 `app/profile/` と同じ app 層に置く。catalog ナビや
logout など app 層イベントを発火する合成画面のため。`feature/household/ui/` は VM とドメインシートの
置き場とし、画面合成は app 層が担う)。ロジックは VM、画面は state + コールバック。モック `screens-d.jsx` の
`Profile` 構成を踏襲:

- **アカウントカード**: アバター頭文字 + 表示名 + 鉛筆(インライン編集 → `renameDisplayName`)+ `Zitadel · email` サブ
  (email は当面固定/プレースホルダ。表示名頭文字でアバター)。
- **世帯セクション**:
  - 世帯ゼロ(`activeId == null` だが Ready のフォールバック) → `NoHouseholdCard`(create/join)。
    ※ 通常は boot で NeedHousehold に倒れるが、退出でゼロになった直後の保険。
  - 世帯カード: home アイコン + 世帯名 + 鉛筆(owner のみ → `renameHousehold`、インライン編集)+「N人で共有」+
    「切り替え」ピル(→ `HouseholdSwitcher`)。
  - メンバー行: アバター + 名前 +(自分なら「あなた」バッジ)+ ロール(crown/edit/eye アイコン)。タップ → `MemberSheet`。
  - 招待セクション(owner のみ): 「家族を招待」→ `InviteSheet`。非 owner は「招待はオーナー(名前)が行えます」注記。
  - 退出: 世帯カード下部に「この世帯から退出」(quiet/danger、確認付き)→ `leave`。
- **商品マスタを編集**(owner のみ): 既存 `CatalogOverlay.Master` へ(`onOpenMaster`)。
- **環境設定**: `ToggleRow`(`disabled`・「将来対応予定」バッジ)で Push / Offline。状態は持たない固定 OFF。
- **その他**: 「消費の傾向」(近日バッジ・無効)、「アーカイブした商品」(count + `onOpenArchived` 既存)。
- **ログアウト**: ghost ボタン。`reauth` 相当(token 破棄 → authorize)or 明示の logout。現状 `App.kt` に
  logout 導線が無いため、`ReauthController` の signal を流用(token 破棄 → authorize redirect)で代替。

シート(`feature/household/ui/`):

- **`HouseholdSwitcher`**(`screens-household.jsx` の `HouseholdSwitcher`): 世帯一覧(active にチェック・
  「N人」・自分のロール)+「新しい世帯をつくる」(既存 `CreateHouseholdSheet` を開く)+「招待コードで参加」
  (既存 `JoinCodeSheet` を開く)。世帯選択 → `switchHousehold(id)` → シート閉じ。create/join 成功は
  P6-3a の VM ではなく、設定文脈では `flow.enterApp(h.id)`(新規/参加先を active にして reload)で合流。
- **`MemberSheet`**(`screens-invite.jsx` の `MemberSheet`): アバター + 名前 + ロール。
  - 対象が owner → crown 注記(変更不可)。
  - 自分が owner かつ対象が他人 → ロール seg(編集できる=`メンバー` / 閲覧のみ=`閲覧者`)→ `changeRole`、
    「世帯から外す」(確認 → `removeMember`)。
  - それ以外 → 「権限変更/削除は owner のみ」注記。
- **`InviteSheet`**(明示発行式・`screens-invite.jsx` の `InviteSheet` を簡略化、QR/リンク無し):
  - 参加時ロール seg(編集できる=`メンバー` / 閲覧のみ=`閲覧者`)。
  - 未発行: 「招待コードを発行」ボタン → `createInvite(role)`。
  - 発行後: 6 桁コード(モノスペース・中央)+ コピー + 「新しいコード」(再 `createInvite`)+ 「失効する」(`revokeInvite`)。
  - validity の「あとN日」表示は出さない(検証不能)。「何度でも使えます」程度の固定コピー。

### 5. designsystem 追加

- `AppIconName` に追加(`AppIcon.kt` の `vector()` にマップ・不足分のみ):
  `Crown`(`Icons.Outlined.WorkspacePremium` 等)/ `Swap`(`Icons.Outlined.SwapHoriz`)/ `Copy`(`ContentCopy`)/
  `Pencil`(`Icons.Outlined.Edit`)/ `Bell`(`Notifications`)/ `Bolt`(`Bolt`)/ `Trend`(`TrendingUp`)/
  `Eye`(`Visibility`)/ `Trash`(`DeleteOutline`)。既存にあるものは流用(`Check`/`Link`/`Users`/`Add` は 3a で追加済)。
- 新 atom(feature は material3 直 import 禁止のため atom 化):
  - `designsystem/atom/Toggle` — on/off スイッチ(`disabled` 対応。本 spec では disabled 固定で使う)。
  - `designsystem/atom/SettingsSection`(または `SectionLabel`)— 見出しラベル + カード枠の薄いラッパ
    (モックの `SectionLabel` 相当)。実装の重複が少なければ画面内ローカルでも可。
- 既存 atom(`Sheet`/`SegmentedControl`/`PrimaryButton`/`TextInput`/`AppText`/`AppIcon`/`HouseholdPill`/`EmptyState`)流用。
- ロールラベル: `HouseholdMemberRole` → 表示文字列/アイコンのマッピングは P6-3a で追加済のヘルパーを流用
  (`世帯主`=crown「オーナー」/ `メンバー`=edit「編集できる」/ `閲覧者`=eye「閲覧のみ」)。無ければ feature に追加。

### 6. App.kt 配線

`AuthState.Ready` の `profileContent` を差し替え:

```kotlin
val settingsVm = remember(householdId, sessionState.residentId) {
    SettingsViewModel(
        session = session,
        renameDisplayNameRpc = residentRepository::rename,
        renameHouseholdRpc = householdRepository::rename,
        changeRoleRpc = householdRepository::changeRole,
        removeMemberRpc = householdRepository::removeMember,
        leaveRpc = householdRepository::leave,
        createInviteRpc = householdRepository::createInvite,
        revokeInviteRpc = householdRepository::revokeInvite,
        flow = vm,
        toast = toast,
        reauth = reauth,
    )
}
profileContent = {
    val sState by settingsVm.state.collectAsState()
    SettingsScreen(
        state = sState,
        onRenameDisplayName = { scope.launch { settingsVm.renameDisplayName(it) } },
        onRenameHousehold = { scope.launch { settingsVm.renameHousehold(it) } },
        onSwitch = { settingsVm.switchHousehold(it) },         // + create/join は flow.enterApp に合流
        onChangeRole = { t, r -> scope.launch { settingsVm.changeRole(t, r) } },
        onRemoveMember = { scope.launch { settingsVm.removeMember(it) } },
        onLeave = { scope.launch { settingsVm.leave() } },
        onCreateInvite = { scope.launch { settingsVm.createInvite(it) } },
        onReissueInvite = { scope.launch { settingsVm.createInvite(it) } },
        onRevokeInvite = { scope.launch { settingsVm.revokeInvite() } },
        onOpenMaster = { catalogOverlay = CatalogOverlay.Master },
        onOpenArchived = { catalogOverlay = CatalogOverlay.Archived },
        onLogout = { reauth.request() },
        // 切替シート内の create/join はここで NeedHousehold VM 相当を remember して flow.enterApp で合流
    )
}
```

切替シート内の「世帯をつくる/参加」は、設定文脈では既存 `CreateHouseholdSheet` / `JoinCodeSheet` を
`App.kt` で開き、`NeedHouseholdViewModel`(P6-3a)を `remember` して `flow.enterApp(h.id)` に合流させる
(NeedHousehold 画面と同じ VM・同じ合流点・同じシートを再利用)。`SettingsViewModel` には create/join を
持たせず、世帯作成/参加のロジックは `NeedHouseholdViewModel` に一本化する(重複実装を作らない)。

### 7. 文言(strings.xml)

`screens-d.jsx` / `screens-invite.jsx` / `screens-household.jsx` のコピーを `ja` に追加(抜粋):
設定見出し「設定」「アカウントと世帯」、各セクションラベル(世帯/環境設定/その他)、世帯名変更・
表示名変更のヒント、切替シート(「世帯を切り替え」「新しい世帯をつくる」「招待コードで参加」)、
メンバーシート(ロール seg ラベル・「世帯から外す」・確認文言・owner 注記)、招待シート(「家族を招待」・
「招待コードを発行」・「新しいコード」・「失効する」・参加権限ラベル)、退出確認、環境設定の
「将来対応予定」/「近日」、last-owner 系のコンテキスト固有エラー文言、各種成功トースト
(「変更しました」「外しました」「退出しました」「招待を発行しました」「招待を失効しました」等)。

## エラー処理方針(まとめ)

- 全 RPC は `RpcOutcome`。`Failure` で `requiresReauth()` → `reauth.request()`、それ以外 `toast`。
- last-owner 系(`leave`/`removeMember`/`changeRole`)の `Conflict` のみ VM でコンテキスト固有文言に差し替え
  (backend reason が技術英語のため)。それ以外の variant は `errorText` 経由。
- owner 限定操作は UI で非 owner に非表示(backend の `OwnerRequiredException` は防御層)。
- `AuthFlow` 呼び出し(refresh/leave/enterApp)の通信失敗は VM `runCatching` で toast(`CancellationException` 再 throw)。

## テスト(commonTest / `kotlin.test.@Test` + Kotest assertions・ロジックのみ)

`frontend-kmp-test-style`: commonTest は Kotest FunSpec 不可。`@Test` + `shouldBe` 等。UI 描画は網羅しない。
fake の `AuthFlow` / suspend lambda / `ToastController` / `ReauthController` / `AppSession` を使う。

- **SettingsViewModel**
  - `renameDisplayName` 成功 → `flow.applyDisplayName(name)` が呼ばれる。
  - `renameHousehold` / `changeRole` / `removeMember` 成功 → `flow.refreshHouseholds()` が呼ばれる。
  - `leave` 成功 → `flow.leaveActiveHousehold()` が呼ばれる。
  - `createInvite` 成功 → state.issuedInvite に反映、`flow` は呼ばれない。`revokeInvite` 成功 → issuedInvite クリア。
  - `switchHousehold(id)` → `flow.switchActiveHousehold(id)`。
  - `leave`/`removeMember`/`changeRole` が `Conflict`(last-owner)→ コンテキスト固有文言 toast、`flow` 呼ばれず。
  - 任意操作の `Unauthorized` → `reauth.request()`。
  - state 導出: session の households/active/resident から isOwner / members / activeName が正しく出る。
- **AuthViewModel(coordinator 追加分)**
  - `switchActiveHousehold(id)` → `deps.setActiveHousehold(id)` と `deps.persistActiveHousehold(id)` が呼ばれ、AuthState は Ready 維持。
  - `refreshHouseholds()` → `loadHouseholds` → `onHouseholdsLoaded(.., 現在のアクティブ)`。現在 active が一覧に無ければ先頭。
  - `applyDisplayName(name)` → `deps.setDisplayName(name)`。
  - `leaveActiveHousehold()`:
    - 退出後も世帯あり・現在 active が残っている → それを維持 + persist。
    - 退出後も世帯あり・現在 active が消えた → 残りの先頭 + persist。
    - 退出後ゼロ → `onHouseholdsCleared` + NeedHousehold。
  - 既存 boot テスト(Registered/Unregistered/Failed/saved-active)が壊れないこと。

## 受け入れ条件

1. 設定タブがモック `screens-d.jsx` に寄った見た目(アカウント/世帯カード/メンバー行/環境設定/その他/ログアウト)で表示される。
2. 表示名の鉛筆編集 → `rename` → 画面とヘッダ等に反映される(住人 rename)。
3. owner が世帯名を鉛筆編集 → `rename` → 反映される。非 owner には鉛筆が出ない。
4. 「切り替え」→ 切替シートで別世帯を選ぶ → 在庫等が切替先の内容に変わり、リロードしても維持される。
5. 切替シートから「世帯をつくる」「招待コードで参加」ができ、成功で新世帯がアクティブになる。
6. owner がメンバー行 → メンバーシートでロール変更 / 除外ができる。owner 対象・非 owner 自分は適切に制限表示。
7. owner が招待シートで「招待コードを発行」→ 6 桁コード表示・コピー・再発行・失効ができる(明示発行式)。
8. 退出ができ、退出後は残りの先頭世帯 or 世帯ゼロなら NeedHousehold に遷移する。
9. last-owner で退出/除外/降格しようとするとコンテキスト固有のエラーが toast される。
10. `./gradlew build`(WasmJs を除く)と `./gradlew test` が通る(`local-build-tips`)。

## 非目標(本 spec では扱わない)

- QR コード生成・招待リンク・ディープリンク参加(`/join/CODE`)→ P6-3 全体で見送り。
- 招待の validity(残り日数)表示・既存有効招待の一覧/取得 → backend に read RPC が無いため非対応。
- owner 譲渡(`世帯主` への昇格 seg)→ モック非対応・スコープ外。
- お知らせ通知・オフライン閲覧・消費傾向 → 将来対応(UI は無効/将来対応予定/近日固定)。
- アバター画像 → 表示名頭文字で代替(P6-2 のカメラ見送りと同方針)。
