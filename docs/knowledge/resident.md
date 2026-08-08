# resident(居住者)コンテキスト

## コンテキスト概要

resident は mindstock を利用する「人」を表すコンテキスト。外部 IdP(Zitadel)で認証された主体を、mindstock 内部の恒久的な識別子 `ResidentId` に対応づけ、その人の表示名(`DisplayName`)を管理する。集約 `Resident` は `id + profile` だけを持つ薄い集約で、業務ルールらしいルールは値オブジェクトの値域検証(表示名 1〜100 文字、auth subject 非空白)に集中している。

resident は他コンテキストから常に参照される基点でもある。世帯(household)のメンバーは `Resident` を composition で保持し、在庫変動(stock)の記録者(actor)は `ResidentId` で表される。認証まわりでは、WebSocket 接続確立時に `MindstockAuthPlugin` が JWT の `sub` から `AuthIdentity` を組み立て、`ResidentRepository.findByAuth` で resident を解決する。解決できなければ「JWT は有効だが未登録」という状態(`MindstockSession.Unregistered`)として扱われ、初回登録(`register`)だけが許可される。この「認証済みだが未登録」の橋渡しが resident コンテキストの中心的な役割になっている。

## 用語集

### 居住者(Resident)

- **定義**: mindstock を利用する個人。外部 IdP の認証主体と 1 対 1 で結びつき、システム内では `ResidentId` で恒久的に識別される。表示名を 1 つ持つ。
- **別名**: Resident、住人
- **関連用語**: 居住者 ID、居住者プロフィール、表示名、認証アイデンティティ、世帯メンバー(household コンテキスト)
- **実装**: `Resident`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/Resident.kt`)

### 居住者 ID(ResidentId)

- **定義**: 居住者を一意に識別する UUID。発番は登録時にサーバ側で行い、以後変わらない。
- **別名**: ResidentId
- **関連用語**: 居住者
- **実装**: `ResidentId`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/ResidentId.kt`)。`ResidentId.create()` が `Uuid.generateV7()`(時刻順序を持つ UUIDv7)で採番する。値域検証は持たない。

### 居住者プロフィール(ResidentProfile)

- **定義**: 居住者の表示に関わる属性の集まり。現状は表示名 1 項目のみを保持する。
- **別名**: ResidentProfile
- **関連用語**: 表示名、居住者
- **実装**: `ResidentProfile`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/ResidentProfile.kt`)

### 表示名(DisplayName)

- **定義**: 居住者が画面上で識別される名前。世帯のメンバー一覧や在庫変動の記録者表示に使われる。
- **別名**: DisplayName、display_name
- **関連用語**: 居住者プロフィール、表示名変更
- **実装**: `DisplayName`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/profile/DisplayName.kt`)。生成時に前後空白を trim し、trim 後 1〜100 文字(`MAX_LENGTH = 100`)であることを検証する。

### 認証アイデンティティ(AuthIdentity)

- **定義**: 外部 IdP における認証主体を表す組。認証プロバイダと、そのプロバイダ内での主体識別子(subject)のペアで構成される。居住者を外部認証から解決する鍵になる。
- **別名**: AuthIdentity
- **関連用語**: 認証プロバイダ、認証サブジェクト、居住者
- **実装**: `AuthIdentity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthIdentity.kt`)

### 認証プロバイダ(AuthProvider)

- **定義**: 認証を提供する外部 IdP の区分。現状 `ZITADEL` の 1 種類のみ。
- **別名**: AuthProvider
- **関連用語**: 認証アイデンティティ、認証サブジェクト
- **実装**: `AuthProvider`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthProvider.kt`)

### 認証サブジェクト(AuthSubject)

- **定義**: 認証プロバイダ内で認証主体を一意に識別する文字列。OIDC の JWT `sub` クレームがそのまま入る。
- **別名**: AuthSubject、subject、sub
- **関連用語**: 認証アイデンティティ、認証プロバイダ
- **実装**: `AuthSubject`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/resident/identity/auth/AuthSubject.kt`)。空白のみの文字列を拒否する(`isNotBlank`)。

### 未登録セッション(Unregistered)

- **定義**: JWT の検証には成功したが、その認証アイデンティティに対応する居住者がまだ登録されていない接続状態。初回登録の入口となる。
- **別名**: `MindstockSession.Unregistered`
- **関連用語**: 登録済みセッション、居住者が登録される
- **実装**: `MindstockSession.Unregistered`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)。関連コンテキスト参照: session(認証)。

### 登録済みセッション(Registered)

- **定義**: JWT が有効で、かつ認証アイデンティティから居住者が解決できた接続状態。`residentId` を保持する。
- **別名**: `MindstockSession.Registered`
- **関連用語**: 未登録セッション、居住者 ID
- **実装**: `MindstockSession.Registered`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`)。関連コンテキスト参照: session(認証)。

### 表示名履歴(resident_display_names)

- **定義**: 表示名の変更を追記のみ(append-only)で記録する永続化単位。「現在の表示名」は同一居住者の中で ID が最大の行として求める。
- **別名**: resident_display_names、display name history
- **関連用語**: 表示名、居住者の表示名が変更される
- **実装**: `ResidentDisplayNamesTable`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/ResidentDisplayNamesTable.kt`)、最新行の抽出は `latestResidentDisplayNames()`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/resident/ResidentHydration.kt`)

## 業務イベント

### 居住者が登録される

- **概要**: 認証済みだが未登録の利用者が表示名を入力し、mindstock 内に居住者として作られる。同時に `ResidentId` が採番され、認証アイデンティティとの紐付けと初回の表示名が記録される。
- **アクター**: 未登録セッションの利用者本人(`ResidentRegisterRpcService.register` を呼ぶ)
- **対象**: 新しく作られる居住者、および認証アイデンティティとの紐付け
- **事前条件**: 有効な JWT で WebSocket 接続が確立していること(`MindstockAuthPlugin` の検証を通過)。かつセッションが `Unregistered` であること。表示名が trim 後 1〜100 文字であること。
- **事後条件**: `residents` / `resident_auth_identities` / `resident_display_names` の 3 テーブルに 1 行ずつ挿入され、採番済み `Resident` が返る。以後の接続では `findByAuth` が成功し、セッションが `Registered` になる。
- **取消・失敗**: 取消(退会・削除)機能は存在しない。失敗ケースは、セッションが既に `Registered` なら `RpcError.Conflict("already registered")`、表示名が値域外なら `IllegalArgumentException` が `RpcError.BadRequest` に翻訳される。(要確認: 同一 `(provider, subject)` に対する同時 register が DB のユニーク制約違反を起こした場合、専用の例外は定義されておらず `RpcError.Internal` に落ちる)
- **順序・タイミング**: 先行イベントは外部 IdP での認証成功。後続としてフロントエンドはこの直後に WebSocket を再接続し(`AuthFlow.onResidentRegistered`)、続けて世帯の作成へ進むか、世帯なしの状態へ進む。関連コンテキスト参照: household(世帯が作成される)。

### 居住者の表示名が変更される

- **概要**: 登録済みの居住者が自分の表示名を変更する。過去の表示名は上書きされず、新しい行が追記される。
- **アクター**: 登録済みセッションの居住者本人(`ResidentRegisterRpcService.rename` を呼ぶ)
- **対象**: 呼び出した本人の表示名(`session` から取り出した `residentId`。他人の表示名は指定できない)
- **事前条件**: セッションが `Registered` であること。新しい表示名が trim 後 1〜100 文字であること。
- **事後条件**: `resident_display_names` に 1 行追記され、以後 `findById` / `findByAuth` が返す `Resident` の表示名が新しい値になる。
- **取消・失敗**: 取消操作はない(元の名前に戻す場合も同じ rename イベントとして追記される)。未登録セッションからの呼び出しは `RpcError.Unauthorized("registration required")`、値域外は `RpcError.BadRequest`。
- **順序・タイミング**: 先行イベントは「居住者が登録される」。フロントエンドでは設定画面から実行され、成功後に世帯情報を再取得してメンバー一覧の表示名を更新する(`SettingsViewModel.renameDisplayName`)。

### 認証アイデンティティから居住者が解決される

- **概要**: WebSocket 接続確立時に、JWT の `sub` から組み立てた認証アイデンティティで既存の居住者を検索し、接続のセッション状態(登録済み / 未登録)を決める。
- **アクター**: サーバ(`MindstockAuthPlugin`)。利用者の接続操作が契機。
- **対象**: 確立中の接続に紐づくセッション
- **事前条件**: `Sec-WebSocket-Protocol` から取り出した JWT が RSA256 / issuer / audience / 有効期限の検証を通り、`sub` が非空白であること。
- **事後条件**: 居住者が見つかれば `MindstockSession.Registered(identity, residentId, exp, callId)`、見つからなければ `MindstockSession.Unregistered(identity, exp, callId)` が `call.attributes` に格納される。
- **取消・失敗**: JWT 検証失敗・`sub` 欠落・`exp` 欠落は HTTP 401。`findByAuth` が `ResourceNotFoundException` 以外の例外(インフラ障害等)を投げた場合は握り潰さず伝播させ、未登録へ降格させない。
- **順序・タイミング**: すべての RPC 呼び出しに先行する。1 接続につき 1 回だけ実行され、以後接続中は不変。関連コンテキスト参照: session(認証)。

### 居住者が自分自身を照会する

- **概要**: 接続の登録状態と、登録済みなら自分の `Resident` を取得する。フロントエンドの起動時分岐(オンボーディング画面かホーム画面か)に使われる。
- **アクター**: 接続中の利用者(`SessionRpcService.whoami` を呼ぶ)
- **対象**: 自分自身の居住者
- **事前条件**: 有効な JWT で接続済みであること(未登録でも呼べる)。
- **事後条件**: `SessionStatus.Registered(resident)` または `SessionStatus.Unregistered` が返る。状態は変化しない(参照のみ)。
- **取消・失敗**: 登録済みセッションなのに `findById` が居住者を見つけられなければ `RpcError.NotFound`。
- **順序・タイミング**: フロントエンドの起動直後、認証完了後に 1 回呼ばれる。関連コンテキスト参照: session(認証)。

## 業務ルール

- 表示名は前後の空白を取り除いたうえで保存する(根拠: `DisplayName` の `companion object invoke(raw)` が `raw.trim()` を渡す)
- 表示名は trim 後 1 文字以上 100 文字以下でなければならない。違反時は `IllegalArgumentException`(メッセージ `"DisplayName must be 1..100 chars after trim"`)(根拠: `DisplayName`、`requireTrimmedWithin`)
- 表示名に一意性の制約はない。同じ表示名の居住者が複数存在してよい(根拠: `ResidentDisplayNamesTable` にユニークインデックスが無い)
- 認証サブジェクトは空白のみであってはならない(根拠: `AuthSubject` の `require(value.isNotBlank())`)
- 1 つの認証アイデンティティ(プロバイダ + サブジェクトの組)は高々 1 人の居住者にしか紐づかない(根拠: `ResidentAuthIdentitiesTable` の `uniqueIndex(provider, subject)`)
- 居住者 ID はサーバ側で採番する。クライアントから ID を指定して登録することはできない(根拠: `ResidentRegisterDataSource.registerResident` が `ResidentId.create()` を呼ぶ。`ResidentRegisterRpcService.register` の引数は表示名のみ)
- 認証アイデンティティは RPC の引数として受け取らず、接続時のセッションから取る(根拠: `ResidentRegisterRpcService.register` の KDoc「AuthIdentity は session 由来(引数で受けない)」、`ResidentRegisterController` が `session.identity` を渡す)
- 既に登録済みのセッションから再度 `register` を呼ぶことはできない(根拠: `ResidentRegisterController.register` が `Registered` に対して `RpcError.Conflict("already registered")` を返す)
- 表示名の変更は本人のみが行える。変更対象の居住者を引数で指定する手段はない(根拠: `ResidentRegisterRpcService.rename` の引数が表示名のみ、`requireRegistered` が渡す `residentId` を使う)
- 未登録セッションが呼べる RPC は `register`(resident)と `whoami`(session)だけで、他はすべて `RpcError.Unauthorized("registration required")` になる(根拠: `SessionGuard` の `requireRegistered` / `allowUnregistered`、`ResidentRegisterController`)
- 表示名の変更は上書きではなく追記で記録し、現在値は同一居住者の中で ID が最大の行とする(根拠: `ResidentRegisterRepository.appendDisplayName`、`latestResidentDisplayNames()` の `rowNumber().partitionBy(residentId).orderBy(id DESC)` を `rn = 1` で絞る実装)
- 居住者を参照する行(表示名履歴・認証アイデンティティ・世帯メンバーイベント・在庫変動)は外部キーが `ON DELETE RESTRICT` であり、参照が残る居住者は削除できない(根拠: `V1__init.sql` の各 FK 定義)
- 認証アイデンティティで居住者が見つからないことは「エラー」ではなく「未登録」を意味し、`ResourceNotFoundException` のみを未登録へ降格させる。それ以外の例外は降格させない(根拠: `MindstockAuthPlugin` の catch 節が `ResourceNotFoundException` 限定、`MindstockAuthPluginTest` の「`findByAuth` が `ResourceNotFoundException` 以外を投げたら Unregistered に降格しない」)

## 設計判断

### 居住者を「認証情報を持たない薄い集約」にする

- **判断**: `Resident` は `id` と `profile` のみを持ち、`AuthIdentity` を集約に含めない。認証アイデンティティは登録時の引数と検索キーとしてのみ現れ、永続化は別テーブル(`resident_auth_identities`)で行う。
- **理由**: 認証は外部 IdP の関心事であり、業務上の「居住者」は表示名で識別されれば足りる。世帯メンバーや在庫変動の記録者として `Resident` を composition で持ち回る際、認証情報が付いてこないことで、それらのコンテキストが認証の詳細に依存せずに済む。(要確認: この意図はコード上のコメントとして明記されておらず、構造からの読み取り)

### 「JWT 有効だが未登録」を nullable ではなく sealed 2 状態で表す

- **判断**: `MindstockSession` を `Registered` / `Unregistered` の sealed interface とし、`residentId` を `Registered` にだけ持たせる。
- **理由**: プロジェクトの「nullable 戻り値原則禁止」に沿った表現。`MindstockSession.kt` の KDoc に「『JWT 有効だが Resident 未登録』を nullable で表さず sealed 2 状態で表現する(nullable 戻り値禁止原則。承認済)」と明記されている。呼び出し側は `when` の網羅性で両状態の扱いを強制される。

### 登録要件をルート単位ではなくメソッド単位のガードで宣言する

- **判断**: 全 RPC を単一エンドポイント `/api/rpc` に相乗りさせたうえで、登録済みが必要かどうかは各 RPC メソッド内の `requireRegistered` / `allowUnregistered` で宣言する。`register` だけが `allowUnregistered`、既定は `requireRegistered`(フェイルクローズ)。
- **理由**: `SessionGuard.kt` の KDoc に「登録ガードは route ではなくここ=アプリ境界で行う」とある。JWT が有効なら未登録でも WebSocket 接続自体は張れる設計なので、登録要件はメソッドの契約として表現するほうが実態に合う。また未登録用の RPC interface を分離しないことで interface の数が増えない。

### 表示名を追記のみの履歴として持つ

- **判断**: `resident_display_names` を append-only とし、UPDATE を行わない。集約 `Resident` は「現在の表示名」だけを持つ。
- **理由**: プロジェクトのドメイン規約「fact クラスは domain から消える — append-only な履歴行は Repository 内部の永続化単位として残し、ドメインでは『現在状態』を持つ集約ルートに集約」に沿う。世帯名(`household_names`)や商品リビジョン(`product_revisions`)も同じ形をとっており、コンテキスト横断で一貫している。(要確認: 過去の表示名を業務上参照する要件があるかは実装から読み取れない。現状、履歴を読み出す経路は存在しない)

### 登録時の RPC 引数を表示名のみに絞る

- **判断**: `register(displayName: DisplayName)` とし、認証アイデンティティも居住者 ID も引数に取らない。
- **理由**: 認証アイデンティティをクライアントから受け取ると、他人の subject を騙る余地が生まれる。接続時に検証済みのセッションから取ることで、なりすましの入口を塞いでいる。(要確認: この理由はコード上に明記されておらず、`ResidentRegisterRpcService` の KDoc「AuthIdentity は session 由来(引数で受けない)」という事実からの推定)

### 登録直後に WebSocket を張り直す

- **判断**: フロントエンドは `register` 成功後、直ちに WebSocket を再接続する(`AuthViewModel.onResidentRegistered` → `deps.reconnect(token)`)。
- **理由**: `MindstockSession` は接続確立時に 1 回だけ組み立てられ、接続中は不変。登録前に張った接続は `Unregistered` のままなので、張り直さないと登録済み専用の RPC が `Unauthorized` になる。

### 登録時に INSERT した行を読み戻さない

- **判断**: `ResidentRegisterDataSource.registerResident` は 3 テーブルへ INSERT したあと、DB から読み戻さずメモリ上で `Resident(residentId, ResidentProfile(displayName))` を組み立てて返す。
- **理由**: (要確認)プロジェクト規約は「INSERT 後は RETURNING 相当(`insertAndGetId` + hydration)で読み戻して domain object を返す」としており、この実装はそこから外れている。ID も表示名も呼び出し側が確定させた値なので読み戻す必要がない、という判断とも読めるが、コード上に理由の記述はない。
