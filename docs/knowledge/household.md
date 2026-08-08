# household(世帯)コンテキスト

## コンテキスト概要

household(世帯)は、mindstock における「在庫を共有する人の集まり」を表すコンテキストである。1 人の住人(resident)が複数の世帯に所属でき、世帯ごとに役割(世帯主 / メンバー / 閲覧者)を持つ。世帯は在庫・商品マスタといった他コンテキストのリソースの所有単位であり、それらのコンテキストは「操作者がその世帯のメンバーか」「マスタ管理権限を持つか」を household 集約の認可メソッド(`requireMember` / `requireCanManageMaster`)に問い合わせる。すなわち household はアプリケーション全体の横方向認可(テナント境界)の基盤でもある。

世帯への参加は招待コード方式で行う。世帯主が付与役割を指定して 6 桁の招待コードを発行し、参加者はそのコードを入力して世帯に加わる。招待には有効期限が無く、何度でも使用でき、明示的に失効させるまで有効であり続ける。世帯名・所属・招待の有効性はいずれも append-only なイベント行として永続化され、「最新行が現在状態」という読み方で集約が再構築される(物理削除・UPDATE を行わない)。

関連コンテキスト参照: resident(住人・表示名・認証アイデンティティ)、inventory / product(世帯が所有する在庫・商品マスタ)。

## 用語集

### 世帯
- **定義**: 在庫を共有する住人の集まり。名前(プロフィール)と現在のメンバー一覧を持つ集約ルートであり、在庫・商品マスタの所有単位かつ認可の境界となる。
- **別名**: Household
- **関連用語**: 世帯 ID、世帯名、世帯メンバー、メンバー一覧、招待
- **実装**: `Household`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`)

### 世帯 ID
- **定義**: 世帯を一意に識別する識別子。UUIDv7 を採番して用いる。
- **別名**: HouseholdId
- **関連用語**: 世帯
- **実装**: `HouseholdId`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdId.kt`)

### 世帯名
- **定義**: 世帯の表示名。前後の空白を除いた上で 1〜30 文字であることを要求する。世帯作成時に決まり、世帯主が後から変更できる。
- **別名**: HouseholdName
- **関連用語**: 世帯プロフィール、世帯
- **実装**: `HouseholdName`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdName.kt`)

### 世帯プロフィール
- **定義**: 世帯の属性をまとめた値オブジェクト。現状は世帯名のみを保持する。
- **別名**: HouseholdProfile
- **関連用語**: 世帯名、世帯
- **実装**: `HouseholdProfile`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdProfile.kt`)

### 所属世帯一覧
- **定義**: ある住人が現在所属している世帯の集合を表すファーストクラスコレクション。世帯切替の選択元になる。
- **別名**: Households
- **関連用語**: 世帯
- **実装**: `Households`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Households.kt`)

### 世帯メンバー
- **定義**: ある世帯に所属している住人と、その世帯での役割の組。住人を ID ではなく `Resident` そのものとして内包する(表示名まで含めて保持する)。
- **別名**: HouseholdMember
- **関連用語**: 世帯内役割、メンバー一覧、住人
- **実装**: `HouseholdMember`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/HouseholdMember.kt`)

### メンバー一覧
- **定義**: 世帯に現在所属しているメンバーの集合を表すファーストクラスコレクション。所属判定(`contains`)・役割解決(`roleOf`)・世帯主取得(`owner`)といった集合固有の判断を担う。
- **別名**: Members
- **関連用語**: 世帯メンバー、世帯内役割
- **実装**: `Members`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/Members.kt`)

### 世帯内役割
- **定義**: 世帯におけるメンバーの区分。世帯主 / メンバー / 閲覧者の 3 種。役割ごとに実行できる操作(権限)が決まる。
- **別名**: HouseholdMemberRole(値: 世帯主 / メンバー / 閲覧者)。UI 上のラベルはそれぞれ「オーナー」「編集できる」「閲覧のみ」
- **関連用語**: 世帯権限、役割別権限表、世帯メンバー
- **実装**: `HouseholdMemberRole`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/HouseholdMemberRole.kt`)、UI ラベル対応は `roleLabelResource`(`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/ui/RoleLabels.kt`)

### 世帯権限
- **定義**: 世帯内で実行できる操作の種類。在庫編集 / マスタ管理 / 世帯管理の 3 種。役割そのものではなく「何ができるか」を表す軸であり、認可判定はこの単位で行う。
- **別名**: HouseholdCapability(値: 在庫編集 / マスタ管理 / 世帯管理)
- **関連用語**: 世帯内役割、役割別権限表
- **実装**: `HouseholdCapability`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/HouseholdCapability.kt`)

### 役割別権限表
- **定義**: 世帯内役割と世帯権限の対応表。世帯主は全権限、メンバーは在庫編集のみ、閲覧者は権限なし。
- **別名**: RolePermissions
- **関連用語**: 世帯内役割、世帯権限
- **実装**: `RolePermissions`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/RolePermissions.kt`)

### 世帯主変更可否
- **定義**: 対象メンバーの役割変更・除外・退出を許してよいかの判定区分。対象が世帯で唯一の世帯主である場合は「最後の世帯主」となり不可、それ以外は可能。
- **別名**: OwnerChangeability(値: 可能 / 最後の世帯主)
- **関連用語**: 世帯内役割、メンバー一覧
- **実装**: `OwnerChangeability`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/member/OwnerChangeability.kt`)

### 招待
- **定義**: 世帯への参加を許可する券。どの世帯に、どの役割で参加できるかと、現在有効かどうかを持つ。有効期限は持たず、失効されるまで何度でも使える。
- **別名**: Invitation
- **関連用語**: 招待コード、招待有効性、招待プレビュー
- **実装**: `Invitation`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/Invitation.kt`)

### 招待コード
- **定義**: 招待を指し示す 6 文字のコード。見間違いやすい `0` `O` `1` `I` を除いた 32 種の英数字(`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`)のみで構成され、暗号論的乱数で採番する。システム全体で一意。
- **別名**: InvitationCode
- **関連用語**: 招待
- **実装**: `InvitationCode`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationCode.kt`)

### 招待有効性
- **定義**: 招待が今使える状態かどうかの区分。有効 / 無効の 2 値。
- **別名**: InvitationValidity(値: 有効 / 無効)
- **関連用語**: 招待
- **実装**: `InvitationValidity`(`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/invitation/InvitationValidity.kt`)

### 招待プレビュー
- **定義**: 参加前の利用者に見せる招待の射影。招待コードから「参加することになる世帯の名前」と「付与される役割」だけを取り出したもの。世帯 ID など内部識別子は見せない。
- **別名**: InvitationPreview
- **関連用語**: 招待、招待コード
- **実装**: `InvitationPreview`(`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/InvitationPreview.kt`)

### 所属状態
- **定義**: 所属イベント行が「所属」を表すか「除外(tombstone)」を表すかの判別子。永続化専用の概念でドメインモデルには現れない。
- **別名**: MembershipStatus(値: 所属 / 除外)
- **関連用語**: 世帯メンバー
- **実装**: `MembershipStatus`(`backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/schemas/MembershipStatus.kt`)

### 操作者
- **定義**: RPC を呼び出しているログイン中の住人。認証セッションから解決され、すべての世帯操作の認可判定に使われる。RPC の引数には現れない(クライアントが偽装できない)。
- **別名**: actor、by、residentId
- **関連用語**: 世帯権限、住人
- **実装**: `requireRegistered`(`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`)

## 業務イベント

### 世帯が作成される
- **概要**: 住人が新しい世帯を作り、自身がその世帯主になる。
- **アクター**: 登録済みの住人(操作者)
- **対象**: 新規の世帯
- **事前条件**: 操作者が住人として登録済みであること。世帯名が trim 後 1〜30 文字であること。
- **事後条件**: 世帯 ID が新規に採番され、世帯が作成される。操作者が唯一の世帯主として所属する。作成された世帯が呼び出し元に返る。
- **取消・失敗**: 世帯名が不正なら `IllegalArgumentException`(→ BadRequest)。操作者が住人として未登録なら Unauthorized。作成後の取消(世帯の削除)手段は実装されていない(要確認: 世帯削除・解散は未実装)。
- **順序・タイミング**: 住人登録の後。所属世帯が 0 件の利用者に対しては、フロントエンドが「世帯が必要」画面を出し、世帯作成か招待コード参加のいずれかを促す。

### 世帯名が変更される
- **概要**: 世帯主が世帯の名前を変更する。
- **アクター**: 対象世帯の世帯主
- **対象**: 既存の世帯
- **事前条件**: 操作者が対象世帯のメンバーで、世帯管理権限(=世帯主)を持つこと。新しい世帯名が trim 後 1〜30 文字であること。
- **事後条件**: 世帯名として新しい名前が 1 行追記され、以後この世帯を読み出すと最新の名前になる。過去の名前は履歴として残る。
- **取消・失敗**: 世帯が存在しなければ `ResourceNotFoundException`(→ NotFound)。操作者が世帯のメンバーでない場合も `ResourceNotFoundException`(→ NotFound。要確認: 他の操作では非メンバーは `MembershipRequiredException` → Unauthorized になるため扱いが非対称)。メンバーだが世帯主でなければ `OwnerRequiredException`(→ Unauthorized)。元に戻すには再度改名する。
- **順序・タイミング**: 世帯作成後、任意のタイミング。

### 招待が発行される
- **概要**: 世帯主が、参加者に渡すための招待コードを付与役割つきで発行する。
- **アクター**: 対象世帯の世帯主
- **対象**: 対象世帯に対する新しい招待
- **事前条件**: 世帯が存在し、操作者が世帯管理権限(=世帯主)を持つこと。
- **事後条件**: 一意な 6 桁の招待コードが採番され、招待が「有効」として登録される。発行された招待(コードと付与役割を含む)が呼び出し元に返り、画面に表示・コピーできる。
- **取消・失敗**: 世帯が存在しなければ NotFound。世帯主でなければ `OwnerRequiredException`(→ Unauthorized)。コードが既存コードと衝突した場合はコードを再採番して最大 3 回まで自動再試行し、それでも衝突すれば失敗する。発行済みの招待は「招待が失効される」で取り消せる。
- **順序・タイミング**: 世帯作成後。既存の招待がある状態でも新規発行でき(再発行)、その場合も古い招待は自動では失効しない(要確認: 再発行時に旧コードを失効させる意図があるかは実装からは読み取れない。UI 上は「新しいコード」ボタンで再発行し、画面が保持する招待だけが差し替わる)。

### 招待が失効される
- **概要**: 世帯主が発行済みの招待を無効にし、以後そのコードで参加できないようにする。
- **アクター**: 招待が属する世帯の世帯主
- **対象**: 既存の招待
- **事前条件**: 招待コードに対応する招待が存在すること。操作者がその招待の世帯の世帯管理権限(=世帯主)を持つこと。
- **事後条件**: 招待の有効性が「無効」として追記され、以後その招待は使用不可になる。招待行そのものは削除されない。
- **取消・失敗**: 招待が存在しなければ NotFound。世帯主でなければ `OwnerRequiredException`(→ Unauthorized)。一度失効した招待を再度有効化する手段は実装されていない(要確認)。
- **順序・タイミング**: 「招待が発行される」の後、任意のタイミング。

### 招待コードが照会される
- **概要**: 参加しようとしている住人が、手元の招待コードでどの世帯にどの役割で入ることになるかを参加前に確認する。
- **アクター**: 登録済みの住人(まだその世帯のメンバーでなくてよい)
- **対象**: 招待コード
- **事前条件**: 操作者が住人として登録済みであること。コードが 6 桁・許可文字のみであること。
- **事後条件**: 世帯名と付与役割からなる招待プレビューが返る。永続状態は変化しない。
- **取消・失敗**: コードの形式が不正なら `IllegalArgumentException`(→ BadRequest。フロントエンドはサーバに送る前に自前で弾き、コード不正メッセージを出す)。該当する招待や世帯が無ければ NotFound。要確認: 失効済みの招待でもプレビューは成功する(`HouseholdController.previewInvite` は `usable()` を確認していない)。
- **順序・タイミング**: 「世帯に参加する」の直前。フロントエンドの参加コードシートで、コード入力後にプレビューが取れて初めて「参加する」ボタンが有効になる。

### 世帯に参加する
- **概要**: 住人が招待コードを使って世帯のメンバーになる。
- **アクター**: 登録済みの住人(参加者自身)
- **対象**: 招待が指す世帯
- **事前条件**: 招待が存在し、有効であること。操作者が住人として登録済みであること。世帯が存在すること。
- **事後条件**: 参加者が招待の付与役割で世帯のメンバーになる。参加後の世帯が返り、フロントエンドはその世帯をアクティブにしてアプリ本体へ遷移する。
- **取消・失敗**: 招待が無効なら `InvitationInvalidException`(→ Conflict)で、所属は一切変化しない。招待・世帯・住人が存在しなければ NotFound。参加後に抜けるには「世帯から退出する」。
- **順序・タイミング**: 「招待が発行される」の後、「招待が失効される」より前。要確認: すでにその世帯のメンバーである住人が同じコードで再度参加した場合、ドメインモデル上は何も起きない(役割も変わらない)が、`HouseholdRegisterService.join` は所属イベントの追記を無条件に行うため、永続層では最新の所属イベントとして招待の付与役割が記録され、結果として既存メンバーの役割が上書きされうる。意図された挙動かは実装から読み取れない。

### メンバーの役割が変更される
- **概要**: 世帯主が、世帯内の他メンバー(または自分)の役割を変更する。
- **アクター**: 対象世帯の世帯主
- **対象**: 同じ世帯のメンバー
- **事前条件**: 世帯が存在し、操作者が世帯管理権限(=世帯主)を持つこと。対象が現在その世帯のメンバーであること。対象が世帯で唯一の世帯主である場合、世帯主以外の役割への変更(降格)でないこと。
- **事後条件**: 対象メンバーの役割として新しい役割が追記され、以後この世帯を読み出すと新しい役割になる。
- **取消・失敗**: 対象が世帯のメンバーでなければ `ResourceNotFoundException`(→ NotFound)。操作者が世帯主でなければ `OwnerRequiredException`(→ Unauthorized)。最後の世帯主を降格しようとすると `LastOwnerException`(→ Conflict)。取り消すには再度役割を変更する。
- **順序・タイミング**: 対象が世帯に参加した後、除外・退出より前。

### メンバーが除外される
- **概要**: 世帯主が、世帯から他のメンバーを外す。
- **アクター**: 対象世帯の世帯主
- **対象**: 同じ世帯のメンバー
- **事前条件**: 世帯が存在し、操作者が世帯管理権限(=世帯主)を持つこと。対象が現在その世帯のメンバーであること。対象が世帯で唯一の世帯主でないこと。
- **事後条件**: 除外を表すイベント(tombstone)が追記され、以後その住人は世帯のメンバーとして読み出されなくなる。過去の所属記録は削除されない。
- **取消・失敗**: 対象が世帯のメンバーでなければ NotFound。操作者が世帯主でなければ Unauthorized。唯一の世帯主を外そうとすると `LastOwnerException`(→ Conflict)。取り消すには招待コードで再参加してもらう。
- **順序・タイミング**: 対象が世帯に参加した後。

### 世帯から退出する
- **概要**: メンバーが自分の意思で世帯を抜ける。
- **アクター**: 対象世帯のメンバー(自分自身)
- **対象**: 自分が所属している世帯
- **事前条件**: 世帯が存在し、操作者がその世帯のメンバーであること。操作者が世帯で唯一の世帯主でないこと。世帯管理権限は不要(閲覧者でも退出できる)。
- **事後条件**: 除外を表すイベントが追記され、以後その世帯は自分の所属世帯一覧に現れない。フロントエンドはアクティブ世帯を切り替える。
- **取消・失敗**: メンバーでなければ `ResourceNotFoundException`(→ NotFound)。唯一の世帯主が退出しようとすると `LastOwnerException`(→ Conflict)。取り消すには招待コードで再参加する。
- **順序・タイミング**: 参加後、任意のタイミング。

### 所属世帯が一覧される
- **概要**: ログイン中の住人が、自分が現在所属している世帯を一覧する。
- **アクター**: 登録済みの住人(操作者)
- **対象**: 操作者自身の所属
- **事前条件**: 操作者が住人として登録済みであること。
- **事後条件**: 現在所属している世帯が、それぞれのメンバー一覧(各メンバーの表示名と役割を含む)つきで返る。所属が無ければ空の一覧が返る(例外にはしない)。永続状態は変化しない。
- **取消・失敗**: 未登録なら Unauthorized。世帯名や住人の表示名が引けない場合は NotFound。
- **順序・タイミング**: ログイン直後のアプリ起動時、および世帯の作成・参加・改名・役割変更・除外・退出のたびに再取得される。

## 業務ルール

- 世帯名は trim 後 1 文字以上 30 文字以内でなければならない。前後の空白は自動的に除去される(根拠: `HouseholdName`、`requireTrimmedWithin`)。DB 側も `varchar(30)`(根拠: `HouseholdNamesTable`)。
- 世帯は作成時、作成者を唯一の世帯主として持つ(根拠: `Household.create`)。
- 世帯内役割は 世帯主 / メンバー / 閲覧者 の 3 種のみ(根拠: `HouseholdMemberRole`)。
- 世帯権限は 在庫編集 / マスタ管理 / 世帯管理 の 3 種。世帯主は全権限、メンバーは在庫編集のみ、閲覧者は一切の権限を持たない(根拠: `RolePermissions`)。
- 世帯名変更・役割変更・メンバー除外・招待発行・招待失効はいずれも世帯管理権限を要し、実質的に世帯主のみが実行できる(根拠: `Household.rename` / `changeRole` / `removeMember` / `requireCanManage`)。
- 世帯からの退出は権限を要さず、メンバーであれば誰でも実行できる(根拠: `Household.leave` が `requireCapability` を呼ばない)。
- 世帯で唯一の世帯主は、降格・除外・退出のいずれもできない(根拠: `OwnerChangeability.on` と `Household.changeRole` / `removeMember` / `leave` の `LastOwnerException`)。世帯主が 2 人以上いれば、そのうちの 1 人は変更・除外・退出できる。
- すでにメンバーである住人を再度参加させても、メンバーは重複せず役割も変わらない(根拠: `Household.join` の `members.contains` 分岐、`HouseholdMembershipTest.既存メンバーの再参加は重複しない`)。
- 存在しないメンバーに対する役割変更・除外・退出は `ResourceNotFoundException` になる(根拠: `Household.changeRole` / `removeMember` / `leave`)。
- 世帯のメンバーでない住人が世帯リソースへアクセスすると `MembershipRequiredException` になる(根拠: `Household.requireMember`)。商品マスタ編集は「非メンバーは `MembershipRequiredException`、メンバーだが権限不足なら `OwnerRequiredException`」の順で判定する(根拠: `Household.requireCanManageMaster` とそのコメント)。
- 招待コードは 6 文字固定で、`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`(0 / O / 1 / I を除外した 32 文字)のみで構成されなければならない(根拠: `InvitationCode`)。
- 招待コードは暗号論的乱数生成器で採番する(根拠: `InvitationCode.generate` の `CryptoRand`)。
- 招待コードはシステム全体で一意でなければならない(根拠: `InvitationsTable` の主キーが code)。採番衝突時はコードを再生成して最大 3 回まで再試行する(根拠: `InvitationRegisterDataSource.issue`)。
- 招待は有効期限を持たず、失効されるまで何度でも使用できる(根拠: `Invitation` に期限フィールドが無いこと、`InvitationValidity` が有効 / 無効の 2 値のみであること、UI 文言「何度でも使えます」)。
- 無効な招待では世帯に参加できない(根拠: `JoinHouseholdScenario` の `InvitationInvalidException`)。
- 世帯名・所属・招待有効性はいずれも物理更新・削除をせず、イベント行の追記で表現し、最新行を現在状態とみなす(根拠: `HouseholdNamesTable` / `HouseholdMembershipEventsTable` / `InvitationValidityEventsTable` と各 DataSource の `rowNumber()` を使った最新行抽出)。
- 所属世帯一覧は、住人ごとに「最新の所属イベントが『所属』である世帯」だけを返す(根拠: `HouseholdDataSource.currentHouseholdIds`)。
- すべての世帯操作の操作者はセッションから解決され、RPC 引数として受け取らない(根拠: 各 Controller の `requireRegistered(session) { residentId -> ... }`)。
- 未登録の利用者は household の全 RPC を呼べない(根拠: 全メソッドが `requireRegistered` を使っている)。

## 設計判断

### 世帯の状態を append-only なイベント行として永続化する
- **判断**: 世帯名(`household_names`)・所属と役割(`household_membership_events`)・招待の有効性(`invitation_validity_events`)を、それぞれ追記専用のテーブルに 1 行ずつ足していく形で保存し、読み出し時にウィンドウ関数で世帯ごと・メンバーごとの最新行を採用する。除外は行の削除ではなく「除外」ステータスの行(tombstone)の追記で表す。
- **理由**: 「誰がいつどの役割で所属していたか」「世帯名がいつ変わったか」の履歴を失わないため。ドメインガイドラインが「append-only な履歴行は Repository 内部の永続化単位として残し、ドメインでは現在状態を持つ集約に集約する」と定めており、その方針に従っている。

### 認可を Service ではなく domain 集約のメソッドとして持たせる
- **判断**: `Household.requireMember` / `requireCanManage` / `requireCanManageMaster` を集約のメソッドとして公開し、household 以外のコンテキスト(product / stock)の Service もこれを呼んで認可する。
- **理由**: 「この世帯で何ができるか」は世帯のメンバー構成と役割から決まる世帯自身の知識であり、リッチドメインの方針上ドメインに置くべきものだから。各 Service に権限判定を書くと同じ判定が散らばる。

### 役割と権限を別概念に分け、対応表で結ぶ
- **判断**: `HouseholdMemberRole`(誰であるか)と `HouseholdCapability`(何ができるか)を別の区分として定義し、`RolePermissions` の対応表で結ぶ。認可判定は capability 単位で書く。
- **理由**: 役割を増やしたり権限の割当を変えたりする際の変更が対応表 1 か所に閉じるため。呼び出し側は「世帯主かどうか」ではなく「世帯管理ができるか」で書けるので、意図が明示される。

### 招待コードから曖昧な文字を除外する
- **判断**: 招待コードの文字集合から `0` `O` `1` `I` を除き、32 文字の英数字のみを使う。
- **理由**: 招待コードは口頭やメモで人から人へ伝えられることを想定しており、見間違い・書き間違いを避けるため(根拠: `InvitationCode` のコメント「曖昧字 0 / O / 1 / I を除外した英数字」)。32 が 256 の約数であるため、乱数バイトの剰余を取っても文字の出現が偏らないという副次的な利点もコメントで明示されている。

### 招待に有効期限を持たせない
- **判断**: 招待は有効 / 無効の 2 状態のみを持ち、期限切れという概念を持たない。無効化は世帯主の明示的な失効操作でのみ起きる。
- **理由**: 要確認。RPC interface のコメントに「role 指定・期限なし」とあり意図的な省略であることは読み取れるが、その理由(家庭内での利用が前提で期限管理が過剰、といった判断)はコードからは読み取れない。忠実度チェックリスト(`docs/superpowers/fidelity/household-sheets.md`)では、モックにあった有効期限カウントダウン・共有リンク・QR が「バックエンド未実装」として明示的に除外されている。

### 招待プレビューを集約ではなく専用の射影として返す
- **判断**: 参加前の照会には `Invitation` をそのまま返さず、世帯名と付与役割だけを持つ `InvitationPreview` を rpc モジュールに定義して返す。
- **理由**: 招待は内部に世帯 ID を持つが、まだメンバーでない利用者に世帯の内部識別子を渡す必要がないため(根拠: `InvitationPreview` の KDoc「joiner には世帯名と付与ロールだけを見せる」)。

### 招待コードの衝突をリトライで吸収し、専用例外にしない
- **判断**: 招待コードの主キー衝突(SQLState 23505)を検知したら、コードを再採番して最大 3 回まで再試行する。試行ごとに独立したトランザクションを張る。専用の衝突例外は定義しない。
- **理由**: 衝突は業務上意味のある失敗ではなく採番の技術的事故であり、呼び出し側に見せる必要がないため。1 つのトランザクション内で再試行すると abort 状態になって 2 回目以降が必ず失敗するため、トランザクションを分けている(根拠: `InvitationRegisterDataSource.issue` のコメント)。

### 世帯の切替(アクティブ世帯)をサーバに持たせない
- **判断**: バックエンドは「所属世帯の一覧」だけを返し、どの世帯を今見ているかはフロントエンドのセッション状態(`AppSession.activeHouseholdId`)として保持する。
- **理由**: 要確認。コードからは事実としてサーバ側にアクティブ世帯の永続化が無いことしか読み取れず、意図的な判断か未実装かは判別できない。

### 招待発行・失効・参加を Scenario として切り出す
- **判断**: 招待の発行・失効・世帯参加は、複数の Service(household / invitation / resident)をまたぐため `CreateInvitationScenario` / `RevokeInvitationScenario` / `JoinHouseholdScenario` として application 層の Scenario に置く。単一 Service で完結する作成・改名・退出・役割変更・除外は Controller から Service を直接呼ぶ。
- **理由**: プロジェクト規約が「Scenario = 複数 Service をまたぐユースケース、1 Service で表現できるなら Scenario を作らない」と定めているため。招待は世帯の認可(household)と招待の書き込み(invitation)という別集約の合成を必要とする。
