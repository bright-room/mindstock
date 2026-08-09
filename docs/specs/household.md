# household(世帯)機能仕様書

## 機能概要

household(世帯)は、在庫を共有する住人の集まりを管理する機能である。提供する操作は次の 5 系統。

1. **世帯の作成** — 住人が新しい世帯を作り、自分が世帯主になる。
2. **世帯への参加** — 世帯主が発行した 6 桁の招待コードを使って、他の住人が世帯に加わる。参加前にコードのプレビュー(どの世帯にどの役割で入るか)を確認できる。
3. **メンバー管理** — 世帯主がメンバーの役割を変更したり、メンバーを世帯から外したりする。メンバー自身は自分の意思で退出できる。
4. **世帯情報の管理** — 世帯主が世帯名を変更する。
5. **所属世帯の一覧** — 住人が自分の所属している世帯を一覧し、フロントエンドで切り替える。

household は同時に、アプリケーション全体の横方向認可(テナント境界)の基盤でもある。product / stock といった他コンテキストの Service は `Household.requireMember` / `requireCanManageMaster` を呼んで「操作者がその世帯のメンバーか」「マスタ管理権限を持つか」を検証する。

関連コンテキスト参照: resident(住人・表示名)、inventory / product(世帯が所有する在庫・商品マスタ)。

## ユースケース

### UC3: 世帯を作成する

- **アクター**: 登録済みの住人
- **基本フロー**:
  1. 利用者が世帯名を入力して作成を実行する。
  2. システムはセッションから操作者の住人 ID を解決し、住人を取得する。
  3. `Household.create` で世帯 ID を採番し、操作者を唯一の世帯主として世帯を組み立てる。
  4. 世帯・初回の世帯名・世帯主の所属イベントを 1 トランザクションで登録する。
  5. 作成された世帯を返す。フロントエンドはその世帯をアクティブにしてアプリ本体へ遷移する。
- **代替・例外フロー**:
  - 世帯名が空白のみ、または trim 後 30 文字超 → `IllegalArgumentException` → `RpcError.BadRequest`。フロントエンドは送信前にも同じ検証を行い、不正ならエラートーストを出して RPC を呼ばない。
  - 操作者が住人として未登録 → `RpcError.Unauthorized`。
  - 操作者の住人レコードが見つからない → `RpcError.NotFound`。

### UC4: 招待コードで世帯に参加する

- **アクター**: 登録済みの住人(参加者)
- **基本フロー**:
  1. 利用者が招待コードを入力する。フロントエンドは大文字化・trim した上で `InvitationCode` に変換する。
  2. `previewInvite` を呼び、参加先の世帯名と付与される役割を表示する。プレビューが取れて初めて「参加する」ボタンが有効になる。
  3. 利用者が参加を実行する。
  4. システムは招待をコードで検索し、有効性を検証する。
  5. 操作者の住人を解決し、招待が指す世帯に付与役割で参加させる(所属イベントを追記)。
  6. 参加後の世帯を返す。フロントエンドはその世帯をアクティブにしてアプリ本体へ遷移する。
- **代替・例外フロー**:
  - コードが 6 桁でない、または許可されていない文字を含む → フロントエンドが RPC 送信前に弾き、「コードが不正」メッセージを表示する。RPC まで到達した場合は `IllegalArgumentException` → `RpcError.BadRequest`。
  - コードに対応する招待が存在しない → `ResourceNotFoundException` → `RpcError.NotFound`。
  - 招待が失効している → `InvitationInvalidException` → `RpcError.Conflict`。所属は変化しない(`JoinHouseholdScenarioTest` が `join` を呼ばないことを検証)。
  - 要確認: `previewInvite` は招待の有効性を検証していないため、失効済みのコードでもプレビューは成功し、参加実行時に初めて Conflict になる。

### UC5: 所属世帯を一覧する / 切り替える

- **アクター**: 登録済みの住人
- **基本フロー**:
  1. アプリ起動時、および世帯に関する更新操作の後にシステムが `list` を呼ぶ。
  2. 操作者が現在所属している世帯を、各メンバーの表示名と役割つきで返す。
  3. フロントエンドは世帯切替シートに一覧を表示し、選択された世帯をアクティブ世帯としてクライアント側セッションに保持する。
- **代替・例外フロー**:
  - 所属世帯が 0 件 → 空の `Households` を返す(例外にしない)。フロントエンドは「世帯が必要」画面を表示し、世帯作成か招待コード参加を促す。
  - 世帯名の履歴行が引けない、またはメンバーの表示名が引けない → `ResourceNotFoundException` → `RpcError.NotFound`。
  - 要確認: どの世帯をアクティブにしているかはサーバに永続化されない(フロントエンドの `AppSession` が保持する)。

### UC6: 世帯名を変更する

- **アクター**: 対象世帯の世帯主
- **基本フロー**:
  1. 世帯主が設定画面から新しい世帯名を入力する。
  2. システムは世帯を取得し、`Household.rename` で世帯管理権限を検証する。
  3. 世帯名を 1 行追記する。
  4. フロントエンドは世帯一覧を再取得し、完了トーストを表示する。
- **代替・例外フロー**:
  - 世帯名が不正 → `RpcError.BadRequest`。
  - 世帯が存在しない → `RpcError.NotFound`。
  - 操作者がメンバーだが世帯主でない → `OwnerRequiredException` → `RpcError.Unauthorized`。フロントエンドは世帯主でない場合に改名 UI を出さない。
  - 操作者が世帯のメンバーでない → `ResourceNotFoundException` → `RpcError.NotFound`(`Members.roleOf` 由来)。要確認: 他の認可経路(`requireCanManageMaster` / `requireMember`)では非メンバーは `MembershipRequiredException` → Unauthorized になるため、扱いが非対称。

### UC7: 世帯から退出する

- **アクター**: 対象世帯のメンバー(自分自身)
- **基本フロー**:
  1. メンバーが設定画面から退出を実行する。
  2. システムは世帯を取得し、`Household.leave` で所属と「最後の世帯主でないこと」を検証する。
  3. 除外イベント(tombstone)を追記する。
  4. フロントエンドはアクティブ世帯を切り替え、完了トーストを表示する。
- **代替・例外フロー**:
  - 操作者がその世帯のメンバーでない → `ResourceNotFoundException` → `RpcError.NotFound`。
  - 操作者が世帯で唯一の世帯主 → `LastOwnerException` → `RpcError.Conflict`。フロントエンドは専用のメッセージを表示する。
  - 権限(世帯管理)は不要。閲覧者でも退出できる。

### UC8: 招待を発行する / 失効する

- **アクター**: 対象世帯の世帯主
- **基本フロー(発行)**:
  1. 世帯主が招待シートで付与役割(UI 上は「編集できる」= メンバー、「閲覧のみ」= 閲覧者の 2 択)を選び発行する。
  2. システムは世帯を取得し `requireCanManage` で世帯管理権限を検証する。
  3. `Invitation.issue` で 6 桁のコードを採番し、招待を「有効」として登録する。
  4. 発行された招待を返す。画面にコードが表示され、コピー・再発行・失効ができる。
- **基本フロー(失効)**:
  1. 世帯主が失効を実行する。
  2. システムは招待をコードで検索し、その招待が属する世帯の世帯管理権限を検証する。
  3. 招待の有効性を「無効」として追記する。
- **代替・例外フロー**:
  - 操作者が世帯主でない → `OwnerRequiredException` → `RpcError.Unauthorized`。書き込みは行われない(`CreateInvitationScenarioTest` / `RevokeInvitationScenarioTest` が検証)。
  - 世帯・招待が存在しない → `RpcError.NotFound`。
  - コード採番が既存コードと衝突 → コードを再採番して最大 3 回まで自動再試行。3 回とも衝突すると SQL 例外が伝播し `RpcError.Internal`。
  - 要確認: 再発行しても旧コードは自動失効しない。失効した招待を再有効化する手段は無い。

### UC9: メンバーの役割を変更する / メンバーを除外する

- **アクター**: 対象世帯の世帯主
- **基本フロー(役割変更)**:
  1. 世帯主がメンバーシートで対象メンバーの役割(UI 上は「編集できる」/「閲覧のみ」の 2 択)を選ぶ。
  2. システムは世帯を取得し、`Household.changeRole` で世帯管理権限・対象の所属・最後の世帯主保護を検証する。
  3. 新しい役割の所属イベントを追記する。
- **基本フロー(除外)**:
  1. 世帯主が対象メンバーの除外を実行し、確認カードで確定する。
  2. システムは `Household.removeMember` で世帯管理権限・対象の所属・最後の世帯主保護を検証する。
  3. 除外イベント(tombstone)を追記する。
- **代替・例外フロー**:
  - 操作者が世帯主でない → `OwnerRequiredException` → `RpcError.Unauthorized`。フロントエンドは世帯主でない場合に役割変更・除外 UI を出さない。
  - 対象がその世帯のメンバーでない → `ResourceNotFoundException` → `RpcError.NotFound`。
  - 対象が唯一の世帯主で、世帯主以外への変更または除外 → `LastOwnerException` → `RpcError.Conflict`。
  - 対象が世帯主の場合、フロントエンドは役割変更 UI を出さず注記のみ表示する。

## RPC インターフェース

エンドポイントは全 service 共通で `rpc("/api/rpc")` の単一 WebSocket。すべてのメソッドが `requireRegistered` を通るため、未登録の利用者は `RpcError.Unauthorized(reason = "registration required")` になる。JWT の期限切れも同ガードで `Unauthorized(reason = "token expired")` になる。操作者(actor)は常にセッション由来で、引数には現れない。

### HouseholdRpcService(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRpcService.kt`)

実装: `HouseholdController`(`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt`)

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `list` | なし | `RpcResult<Households, RpcError>` | `Unauthorized`(未登録 / 期限切れ)、`NotFound`(世帯名・表示名の解決失敗)、`Internal` |
| `previewInvite` | `code: InvitationCode` | `RpcResult<InvitationPreview, RpcError>` | `BadRequest`(コード形式不正)、`Unauthorized`、`NotFound`(招待・世帯が無い)、`Internal` |

### HouseholdRegisterRpcService(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRegisterRpcService.kt`)

実装: `HouseholdRegisterController`(`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdRegisterController.kt`)

| メソッド | リクエスト | レスポンス | 発生しうるエラー |
|---|---|---|---|
| `create` | `name: HouseholdName` | `RpcResult<Household, RpcError>` | `BadRequest`(世帯名不正)、`Unauthorized`、`NotFound`(住人未解決)、`Internal` |
| `rename` | `householdId: HouseholdId`, `name: HouseholdName` | `RpcResult<Unit, RpcError>` | `BadRequest`、`Unauthorized`(世帯主でない)、`NotFound`(世帯が無い / 操作者が非メンバー)、`Internal` |
| `leave` | `householdId: HouseholdId` | `RpcResult<Unit, RpcError>` | `Unauthorized`、`NotFound`(世帯が無い / 非メンバー)、`Conflict`(最後の世帯主)、`Internal` |
| `changeRole` | `householdId: HouseholdId`, `target: ResidentId`, `role: HouseholdMemberRole` | `RpcResult<Unit, RpcError>` | `Unauthorized`(世帯主でない)、`NotFound`(世帯・対象が無い)、`Conflict`(最後の世帯主の降格)、`Internal` |
| `removeMember` | `householdId: HouseholdId`, `target: ResidentId` | `RpcResult<Unit, RpcError>` | `Unauthorized`、`NotFound`、`Conflict`(最後の世帯主の除外)、`Internal` |
| `createInvite` | `householdId: HouseholdId`, `role: HouseholdMemberRole` | `RpcResult<Invitation, RpcError>` | `Unauthorized`(世帯主でない)、`NotFound`(世帯が無い)、`Internal`(コード衝突が 3 回続いた場合) |
| `revokeInvite` | `code: InvitationCode` | `RpcResult<Unit, RpcError>` | `BadRequest`(コード形式不正)、`Unauthorized`、`NotFound`(招待・世帯が無い)、`Internal` |
| `join` | `code: InvitationCode` | `RpcResult<Household, RpcError>` | `BadRequest`、`Unauthorized`、`NotFound`(招待・世帯・住人が無い)、`Conflict`(招待が無効)、`Internal` |

要確認: `createInvite` / `changeRole` の `role` は `HouseholdMemberRole` をそのまま受けるため、RPC 上は `世帯主` も指定できる。フロントエンドの選択肢は メンバー / 閲覧者 の 2 択に限定されており(`InviteSheet` / `MemberSheet`)、世帯主の付与・移譲を UI から行う手段は無い。サーバ側にこれを禁じる検証も無い。

## データモデル

### 集約・値オブジェクト

**`Household`(集約ルート)** — `domain/.../model/household/Household.kt`

| フィールド | 型 | 説明 |
|---|---|---|
| `id` | `HouseholdId` | UUIDv7。`HouseholdId.create()` で採番 |
| `profile` | `HouseholdProfile` | 世帯名を保持 |
| `members` | `Members` | 現在のメンバー一覧(子を ID ではなく `Resident` ごと内包する object graph) |

操作メソッド(いずれも新インスタンスを返す不変更新): `rename` / `join` / `changeRole` / `removeMember` / `leave`。認可メソッド: `requireCanManage`(世帯管理)/ `requireCanManageMaster`(マスタ管理)/ `requireMember`(所属)。ファクトリ: `Household.create(name, owner)`。

**値オブジェクト・区分**

| 型 | 実体 | 制約 |
|---|---|---|
| `HouseholdId` | `Uuid` | 制約なし。`create()` が UUIDv7 を採番 |
| `HouseholdName` | `String` | trim 後 1〜30 文字(`MAX_LENGTH = 30`)。コンストラクタが自動 trim |
| `HouseholdProfile` | `HouseholdName` | — |
| `Households` | `List<Household>` | ファーストクラスコレクション。`size()` |
| `HouseholdMember` | `Resident` + `HouseholdMemberRole` | `withRole(newRole)` で役割を差し替えた新インスタンスを返す |
| `Members` | `List<HouseholdMember>` | ファーストクラスコレクション。`size()` / `owner()` / `contains()` / `roleOf()` / `add()` / `changeRole()` / `remove()` |
| `HouseholdMemberRole` | enum | 世帯主 / メンバー / 閲覧者。`is世帯主()` |
| `HouseholdCapability` | enum | 在庫編集 / マスタ管理 / 世帯管理 |
| `RolePermissions` | 役割 + 権限 | `isAllowed()`。世帯主=全権限 / メンバー=在庫編集のみ / 閲覧者=なし |
| `OwnerChangeability` | enum | 可能(allowed=true)/ 最後の世帯主(allowed=false)。`on(members, target)` で判定 |
| `Invitation` | 世帯 ID + コード + 付与役割 + 有効性 | `usable()` / `revoke()` / `Invitation.issue(householdId, role)` |
| `InvitationCode` | `String` | 6 文字固定、文字集合 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`。`generate()` が CSPRNG で採番 |
| `InvitationValidity` | enum | 有効 / 無効。`is有効()` |
| `InvitationPreview` | 世帯名 + 付与役割 | `:rpc` モジュールの射影型(ドメインモデルではない) |

### 不変条件

- 世帯名は trim 後 1〜30 文字(違反時 `IllegalArgumentException`)。
- 招待コードは 6 文字かつ許可文字のみ(違反時 `IllegalArgumentException`)。
- 世帯には常に少なくとも 1 人の世帯主がいる(唯一の世帯主の降格・除外・退出を `LastOwnerException` で拒否することで維持)。
- メンバーは重複しない(`join` が既存メンバーを検出して何もしない)。

### DB テーブル(Exposed 定義)

`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/`

**`households`**(`HouseholdsTable`)

| 列 | 型 | 備考 |
|---|---|---|
| `id` | uuid | PK |
| `created_at` | datetime | 既定値 `CurrentDateTime` |

**`household_names`**(`HouseholdNamesTable`)— append-only。最新行が現在の世帯名

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / autoIncrement |
| `household_id` | uuid | `households.id` 参照(onDelete = RESTRICT) |
| `name` | varchar(30) | |
| `recorded_at` | datetime | 既定値 `CurrentDateTime` |

索引: `(household_id, id)`

**`household_membership_events`**(`HouseholdMembershipEventsTable`)— append-only。世帯 × 住人ごとの最新行が現在の所属・役割

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / autoIncrement |
| `household_id` | uuid | `households.id` 参照(RESTRICT) |
| `resident_id` | uuid | `residents.id` 参照(RESTRICT) |
| `role` | varchar(20) | `HouseholdMemberRole` の名前。除外行では意味を持たないが NOT NULL を満たすため `閲覧者` を入れる |
| `status` | varchar(10) | `MembershipStatus`(所属 / 除外)。永続専用の判別子 |
| `recorded_at` | datetime | 既定値 `CurrentDateTime` |

索引: `(household_id, resident_id, id)`、`(resident_id, household_id, id)`

**`invitations`**(`InvitationsTable`)— insert-once

| 列 | 型 | 備考 |
|---|---|---|
| `code` | varchar(6) | PK(システム全体で一意) |
| `household_id` | uuid | `households.id` 参照(RESTRICT) |
| `granted_role` | varchar(20) | `HouseholdMemberRole` の名前 |

索引: `(household_id)`

**`invitation_validity_events`**(`InvitationValidityEventsTable`)— append-only。最新行が現在の有効性

| 列 | 型 | 備考 |
|---|---|---|
| `id` | long | PK / autoIncrement |
| `invitation_code` | varchar(6) | `invitations.code` 参照(RESTRICT) |
| `validity` | varchar(10) | `InvitationValidity`(有効 / 無効) |
| `recorded_at` | datetime | 既定値 `CurrentDateTime` |

索引: `(invitation_code, id)`

### 読み出し(hydration)

`HouseholdDataSource` はウィンドウ関数 `row_number() over (partition by ... order by id desc)` で最新行を取り、`rn = 1` かつ `status = 所属` の行だけを現在のメンバーとする。世帯名も同様に最新 1 行を採用する。メンバーの表示名は resident 側の最新 display_name をバッチロードして突き合わせる(N+1 を避けるため世帯一覧取得時も一括問い合わせ)。

`InvitationDataSource.findByCode` は `invitations` の 1 行に、`invitation_validity_events` の最新行を組み合わせて `Invitation` を組み立てる。

### アプリケーション層の構成

| 種別 | クラス | 場所 |
|---|---|---|
| Repository(読み) | `HouseholdRepository` / `InvitationRepository` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/{household,invitation}/` |
| Repository(書き) | `HouseholdRegisterRepository` / `InvitationRegisterRepository` | 同上 |
| Service | `HouseholdService` / `HouseholdRegisterService` / `InvitationService` / `InvitationRegisterService` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/{household,invitation}/` |
| Scenario | `JoinHouseholdScenario` / `CreateInvitationScenario` / `RevokeInvitationScenario` | `backend/core/src/main/kotlin/net/brightroom/mindstock/application/scenario/{household,invitation}/` |
| DataSource | `HouseholdDataSource` / `HouseholdRegisterDataSource` / `InvitationDataSource` / `InvitationRegisterDataSource` | `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/{household,invitation}/` |

household 関連の Transfer / Receive(外部システム連携)は存在しない。招待メール送信などの外部通知機能は実装されていない。

### フロントエンド

| 要素 | クラス / ファイル |
|---|---|
| RPC ラッパ | `HouseholdRepository`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/data/HouseholdRepository.kt`) |
| 世帯未所属時の ViewModel | `NeedHouseholdViewModel` / `NeedHouseholdUiState` |
| 設定画面の ViewModel | `SettingsViewModel` / `SettingsUiState`(`HouseholdSummary` / `MemberRow`) |
| 画面 | `NeedHouseholdScreen` / `CreateHouseholdSheet` / `JoinCodeSheet` / `HouseholdSwitcher` / `InviteSheet` / `MemberSheet` |
| 役割ラベル対応 | `RoleLabels.kt`(`roleLabelResource` / `roleIcon` / `roleDescResource`) |

`SettingsViewModel` は発行済み招待を ViewModel のメモリにのみ保持する(`issuedInvite`)。画面を離れると発行済みコードは画面から失われるが、招待自体はサーバ側で有効なまま残る。

## エラー・例外

ドメイン / インフラが投げた例外は presentation 境界の `runGuarded`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`)で `RpcError` に翻訳される。household に関係する翻訳は次のとおり。

| 例外 | 発生源 | RpcError | 意味 |
|---|---|---|---|
| `IllegalArgumentException` | `HouseholdName` / `InvitationCode` の `init` | `BadRequest` | 値オブジェクトの値域違反 |
| `ResourceNotFoundException` | `HouseholdDataSource` / `InvitationDataSource` / `Members.roleOf` / `Members.owner` / `Household.changeRole` / `removeMember` / `leave` | `NotFound` | 世帯・招待・メンバー・表示名が見つからない |
| `OwnerRequiredException` | `Household.requireCapability` | `Unauthorized` | メンバーではあるが必要な権限(世帯管理・マスタ管理)を持たない |
| `MembershipRequiredException` | `Household.requireMember` | `Unauthorized` | 世帯のメンバーでない(横方向認可の失敗) |
| `LastOwnerException` | `Household.changeRole` / `removeMember` / `leave` | `Conflict` | 唯一の世帯主を降格・除外・退出させようとした |
| `InvitationInvalidException` | `JoinHouseholdScenario` | `Conflict` | 失効済みの招待で参加しようとした |
| `SQLException`(23505 以外、または 3 回リトライ後) | `InvitationRegisterDataSource.issue` | `Internal` | 招待コードの採番衝突が解消しなかった、その他 DB 障害 |

未登録・トークン期限切れは例外ではなくガード自身が `Unauthorized` を返す。

フロントエンドは `FailureHandler` で失敗を処理し、`LastOwnerException` 由来の Conflict に対しては操作ごとの専用メッセージ(`settings_error_last_owner_change_role` / `settings_error_last_owner_remove` / `settings_error_last_owner_leave`)を表示する。認証系のエラーは再認証フローに倒す。

## 制約事項・要確認

**仕様として確認できる制約**

- 招待に有効期限は無い。失効させるまで何度でも使える。
- 招待コードは 6 文字・32 種の文字集合であり、組み合わせは約 10.7 億通り。総当たり試行に対するレート制限は実装されていない。
- 世帯の削除・解散、世帯のアーカイブは実装されていない。
- 招待の共有(リンク・QR コード・メール送信)は実装されていない。`docs/superpowers/fidelity/household-sheets.md` でモックの「リンク / QR タブ / 共有する / 有効期限カウントダウン」がバックエンド未実装として明示的に除外されている。
- 1 人の住人が所属できる世帯数、1 世帯のメンバー数に上限は無い。
- 世帯に所属できるのは登録済みの住人のみ。未登録の利用者は household の全 RPC を呼べない。

**要確認**

- `HouseholdController.previewInvite` は招待の有効性(`usable()`)を検証していない。失効済みのコードでもプレビューが成功し、参加実行時に初めて Conflict になる。意図的か漏れかは実装から読み取れない。
- `HouseholdRegisterService.join` は `Household.join` の結果を見ずに `joinMember`(所属イベントの追記)を無条件に実行する。ドメイン上は「既存メンバーの再参加では役割が変わらない」が、永続層では最新の所属イベントとして招待の付与役割が記録されるため、既存メンバーの役割が招待の役割で上書きされうる。この差異が意図されたものかは読み取れない。
- `Household.rename` は非メンバーに対して `ResourceNotFoundException`(→ NotFound)を返す。`requireCanManageMaster` / `requireMember` の経路では非メンバーは `MembershipRequiredException`(→ Unauthorized)になるため、非メンバーの扱いが操作ごとに非対称。`HouseholdRenameTest.部外者は世帯名を変更できない` は現在の挙動(NotFound)を仕様として固定している。
- `createInvite` / `changeRole` は `世帯主` ロールの指定をサーバ側で禁じていない。UI が 2 択に絞っているだけであり、RPC を直接呼べば世帯主を増やせる。世帯主の移譲を正式機能とみなすかは要確認。
- 失効した招待を再有効化する手段が無い。また招待を再発行しても旧コードは自動失効しない。
- どの世帯をアクティブにしているかはサーバに永続化されない。クライアント(`AppSession`)のみが保持する。
- `HouseholdRegisterService` の `rename` / `leave` / `changeRole` / `removeMember` は、ドメインメソッドの戻り値(新しい `Household`)を捨てて認可・検証の副作用としてのみ利用し、永続化はイベント追記で別途行っている。append-only 設計上は整合するが、ドメインメソッドの戻り値が使われないという形になっている点は設計意図の確認対象。
- 招待発行時のトランザクション境界外で `Created.now()` を取得している(リトライ間で同一時刻を使うため)。`.claude/rules/backend-software-architecture.md` に「フェーズ 3-5 で原則 tx 内へ統一予定」と記載されており、暫定状態。
