# WebSocket 認証と「登録必須ガード」の不具合 — 原因整理と対応方針

- 日付: 2026-06-06
- 対象: `:backend:api` の認証 / 登録ガード(`configuration/auth/`, `configuration/routing/`)
- 関連: 暫定修正 PR #109(本ドキュメントで方式を再検討する)
- ステータス: **検討中(実装前)**。本書で方針を合意してから実装する。

---

## 0. この文書の目的

「認証付き WebSocket が有効トークンでも 401 になる」不具合について、

1. **何が原因か**を人間が読んで分かる形に整理し、
2. その上で**どう直すかの案を複数**出し、
3. 各案を**深掘り検証**(確実に直るか / 将来の技術負債にならないか / そもそも現在の全体方針が破綻していないか)し、
4. 合意できたら実装する、

ための土台にする。前提知識(WebSocket の通信と認証方式)も合わせて整理する。

---

## 1. 前提:kotlinx-rpc は WebSocket で喋っている

mindstock の RPC 層は **kotlinx-rpc(kRPC)** を使い、**トランスポートは WebSocket**。

- クライアント(frontend)は各サービスのパスへ WS 接続する: `ws://host/api/v1/<service>`
  - 例: `RpcClientProvider.open("resident", token)` → `ws://.../api/v1/resident`
- サーバ(Ktor)側は `rpc("/resident") { registerService<...>() }` で、その**パスごとに WebSocket ルート**を張る(`KrpcRoute` は `DefaultWebSocketServerSession` を実装)。
- **1 つの RPC メソッド呼び出しも、suspend の戻り値も、Flow のストリームも、確立した 1 本の WS 上をフレームで多重化して**やり取りする。HTTP の「1 リクエスト = 1 レスポンス」ではない。

```text
frontend                         backend (Ktor + kRPC)
  │   ws://.../api/v1/resident      │
  ├───────── WS handshake ─────────►│  ← 認証はここで効く(後述)
  │◄──────── 101 Switching ─────────┤
  │                                 │
  │== me() 呼び出し(フレーム)====►│
  │◄===== 結果(フレーム)==========│  ← 以降は同じ接続上を双方向に流れる
  │== 別の呼び出し ================►│
```

> **ここが今回の核心**:RPC が全部 WS で張られるということは、**WS のハンドシェイクで認証がコケると、その RPC は一切呼べない**。今回「全 RPC が 401」に見えたのはこのため。

---

## 2. WebSocket の通信のしくみ(認証がどこで効くか)

WebSocket は 2 フェーズある。

### フェーズ1:ハンドシェイク = ただの HTTP GET + Upgrade

クライアントはまず**通常の HTTP GET リクエスト**を送る:

```http
GET /api/v1/resident HTTP/1.1
Host: ...
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: <ランダム>
Sec-WebSocket-Version: 13
Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<...>   ← 任意(サブプロトコル)
Authorization: Bearer <jwt>                                    ← 任意(ヘッダ)
```

サーバが受理すると:

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: <Key から計算>
Sec-WebSocket-Protocol: mindstock.v1     ← 提示された中から 1 つだけ echo
```

**この HTTP リクエストは普通のリクエストなので、`Authorization` ヘッダや Cookie、`Sec-WebSocket-Protocol` がここで読める。= 認証できるのはこの瞬間だけ。**

### フェーズ2:101 以降は「フレーム」の双方向ストリーム

`101` が返るとコネクションは WebSocket フレーム(RFC 6455)に切り替わる。

- もう HTTP ヘッダは飛ばない。1 本の TCP/TLS の上を、小さなフレーム(opcode + payload)が双方向に流れ続ける。
- **個々のフレームには認証情報が無い。** 接続が確立した時点の認証状態が、その接続が切れるまで引き継がれる。

### この性質から来る重要な帰結

- **認証はハンドシェイク 1 回が基本。** その後にトークンが失効しても、接続自体は生き続ける(=放っておくと「失効後も使える」)。
- 逆に**ハンドシェイクで弾けば、その接続では一切 RPC を通さない**(今回の症状)。

---

## 3. WebSocket の認証方式 — 一般論と pros/cons

WS でクライアントを認証する代表的なやり方:

| 方式 | 概要 | Pros | Cons / 課題 |
|---|---|---|---|
| **Authorization ヘッダ** | ハンドシェイク GET に `Authorization: Bearer` | REST と同じ・素直 | **ブラウザの WebSocket API はカスタムヘッダを付けられない**(Node/ネイティブのみ可) |
| **サブプロトコル smuggling** | `Sec-WebSocket-Protocol` にトークンを忍ばせる(`new WebSocket(url, [proto])`) | **ブラウザで唯一ヘッダ的にトークンを運べる** | トークンが中間 proxy のログに残りうる/server は echo しないなど配慮要・長さ制約 |
| **Cookie(HttpOnly)** | ハンドシェイクに自動付与される Cookie | ブラウザネイティブ・JS から触れない | CSRF/クロスサイト考慮・SPA とドメイン構成に依存 |
| **クエリパラメータ** | `ws://...?token=...` | 最も簡単 | **URL がアクセスログ/履歴/Referer に残る → 漏洩**。非推奨 |
| **接続後メッセージ認証** | 未認証で接続し、最初のフレームでトークンを送る。サーバはそれまでゲート | ヘッダ制約を回避・失効を都度判定しやすい | プロトコルを自前で作る・「未認証で繋がっている窓」が一瞬できる |
| **ワンタイムチケット** | REST で短命チケットを発行 → WS はチケットで接続 | トークン本体を WS に載せない | 余分な往復・チケット管理 |

### mindstock の選択

- **ハンドシェイク時に JWT 検証**(`MindstockAuthPlugin` の `onCall`)。
- トークンの運び方は **2 系統**を許容:
  - `Authorization: Bearer`(REST 互換・テスト容易。**ブラウザ不可**)
  - `Sec-WebSocket-Protocol: mindstock.bearer.<base64url(jwt)>`(**ブラウザ経路**)
- `mindstock.v1`(アプリプロトコル識別)だけを echo し、**bearer サブプロトコル(=トークン)は echo しない**(`WsSubprotocolEchoPlugin`、ログ漏洩対策)。

### この選択の本質的なデメリットと、現状の対処

| デメリット(WS + ハンドシェイク認証の宿命) | mindstock の現状 |
|---|---|
| ハンドシェイク後にトークンが失効しても接続が生き続ける | **対処済**:`guarded()`(SessionGuard)が **RPC メッセージごとに `session.exp` を再判定**し、失効していれば `Unauthorized` で短絡(L2 ガード) |
| IdP 側の即時失効(revocation)に追従できない | **未対応(意図的)**:守るのは JWT の有効期限のみ、と明文化 |
| サブプロトコルにトークンが乗る → proxy ログ漏洩 | **緩和**:server は bearer を echo しない。さらに短命トークン前提 |
| サブプロトコルヘッダが長い(~1100 字) | 実機検証で CIO は truncate しないことを確認済 |

> つまり「WS 認証の一般的な弱点」はおおむね設計に織り込まれている。**今回の不具合はこの認証設計の欠陥ではなかった**(次節)。

---

## 4. 今回の不具合の正体

### 症状(同一の有効 JWT・同一時刻・backend :8090 直叩き)

| リクエスト | 結果 | 期待 |
|---|---|---|
| 通常 GET `/api/v1/resident/register` + 有効トークン | **400** | 400(WS 専用ルートに GET したから。=認証は通っている) |
| **WS upgrade** `/api/v1/resident/register` + 有効トークン | **401** | **101 を期待(public 登録ルートなので)** ← これがバグ |
| WS upgrade `/api/v1/resident` + 未登録ユーザ | 401 | 401(登録必須ルート。未登録なので正しい) |

「GET は通るのに WS upgrade だけ 401」「**通常 GET では再現しない**」が決定的なヒント。

### 原因:認証ではなく「登録必須ガード」の適用範囲

切り分けの結果、**JWT 認証(`MindstockAuthPlugin`)は GET でも WS upgrade でも正常に通っていた**(実機ログで `verify OK` を確認)。401 を出していたのは **`RequireRegisteredUserPlugin`(登録済みユーザだけ通すガード)**。

当時のルーティング構造:

```kotlin
route("/api/v1") {
    rpc("/resident/register") { ... }     // public(未登録でも登録のために通したい)
    route("") {                            // ★ 空パスの入れ子
        install(RequireRegisteredUserPlugin)   // route-scoped プラグイン
        rpc("/resident") { ... }           // 登録必須
        rpc("/stock") { ... }
        ...
    }
}
```

ここに **Ktor の route-scoped プラグイン × WebSocket ハンドシェイク経路** という落とし穴が 2 つ重なっていた:

1. **空の `route("") { install(...) }` が、WS upgrade の経路でだけガードを隣接ルートへ漏らす。**
   通常 GET ではガードは `/resident/register` に掛からない(だから GET は 400 まで到達)。ところが **WS upgrade のときだけ**、ルート解決の過程で空ルートのパイプライン(=ガード)が走ってしまう。→ public なはずの `/resident/register` が未登録ユーザに対し 401。
2. **`/resident`(保護)と `/resident/register`(public)がパスセグメント `resident` を共有している。**
   ガードを `resident` ノード(やその祖先)に置くと、子の `register` まで巻き込む。route スコープでは両者を分離できない。

> 補足:旧調査メモにあった「401 応答に `mindstock.v1` だけ echo されて bearer が消える」は **仕様どおりの意図した挙動**(トークンは echo しない)で、バグ症状ではなかった。ここを症状と誤読して「WS 認証が壊れている」と結論していた。

### なぜ「未登録だと何もできない」が問題なのか

- 未登録ユーザが `/resident`(me)で 401 → オンボーディングに倒れるのは**正しい**。
- しかし `/resident/register`(初回登録のための public ルート)まで 401 になると、**未登録ユーザは登録する手段が無い**=詰む。これが実害。

### 検証で使った事実(再掲・今後も有効)

- `MindstockAuthPlugin.onCall` は app レベル。**ルートに関係なく、ルーティングより前に走る**。GET と WS upgrade で同一に走ることを実機ログで確認。→ 「WS だけ認証が違う」ことは原理的に起きない。
- route-scoped プラグインは WS upgrade の解決過程で隣接/子に漏れる(ハーネスで A/B/C/G/D/H 候補を実測して確認)。
- testApplication は WS upgrade ができないため、この層のバグはユニットテストをすり抜ける(実機 or `embeddedServer(CIO)` + 実 kRPC client が必要)。

---

## 5. 「現状の暫定修正(PR #109)」とその弱点

PR #109 では `RequireRegisteredUserPlugin` を **application レベル**化し、public は**完全一致 allowlist**で除外した:

```kotlin
install(RequireRegisteredUserPlugin) { publicPaths.add("/api/v1/resident/register") }
// guard: path.startsWith("/api/v1") かつ publicPaths に無ければ Registered 必須
```

- **良い点**:app レベル onCall は GET / WS で同一に走り、ルートツリーの解決順にも依存しない → §4 の 2 つの罠に構造的に強い。実機 + Chrome で動作確認済み。
- **弱点(レビュー指摘)**:
  1. **`apiPathPrefix = "/api/v1"` 固定が fail-open**。将来 `/api/v2/...` を生やすと **prefix 外なのでガードを素通り → v2 が丸ごと無防備**になる。付け忘れが「開く」方向に倒れるのは危険。
  2. **public パスの文字列がルーティング定義と二重持ち**。ルート名を変えると allowlist と静かにズレる(ドリフト)。

この 2 点を解消する形に作り直したい、というのが本ドキュメントの主題。

---

## 6. 対応案

### 案A:バージョン非依存 prefix + deny-by-default + 共有定数 allowlist(app レベル維持・最小修正)

- ガードの対象を **バージョン非依存の `/api/` 接頭辞**にする(`/api/v1` 固定をやめる)。
  `/api/` 配下は「**allowlist に無ければ全部 登録必須**」(deny-by-default)。`/api/` 配下でないパス(`/healthz` 等)は素通し。
- public パスは **routing と共有する単一定数**から供給する。

```kotlin
// 単一の真実(routing からも guard からも参照)
object ApiRoutes {
    const val BASE = "/api/"                       // ← バージョン非依存(/api/v1, /api/v2, ... を含む)
    const val RESIDENT_REGISTER = "/api/v1/resident/register"
    val PUBLIC: Set<String> = setOf(RESIDENT_REGISTER)
}

// guard(app レベル):/api/ 配下 かつ allowlist に無ければ Registered 必須
install(RequireRegisteredUserPlugin) {
    apiPathPrefix = ApiRoutes.BASE            // "/api/"
    publicPaths.addAll(ApiRoutes.PUBLIC)
}

// routing も同じ定数を使う
rpc(ApiRoutes.RESIDENT_REGISTER) { ... }
```

- **v2 が来ても自動で保護される**:`/api/v2/...` は `/api/` 配下なので、allowlist に入れない限り Registered 必須。付け忘れは「閉じる」方向=**fail-closed**(`/api/v1` 固定だと v2 が prefix 外で素通し=fail-open になるのが #109 の弱点だった)。
- **非 API パスは巻き込まない**:`/api/` で絞ることで、将来の `/healthz` `/metrics` `/readiness` や静的アセットを「登録済み必須」で 401 にしてしまう事故を避ける(prefix を完全撤廃した純粋 deny-by-default だとここが壊れる)。
- 文字列の二重持ちが消える(定数 1 か所)。
- **PR #109 / 実機検証済の candI との差分はごく小さい**(`apiPathPrefix` を `/api/v1` → `/api/` に広げ、public パスを定数化するだけ)。`/api/v1/...` の実機挙動は不変、追加で「v2 も fail-closed」「非 API は素通し」を単体テストで担保する。

> 注:接頭辞は末尾スラッシュ込みの `"/api/"` にする(`/apiX` のような別パスを誤って巻き込まないため)。現行ルートは全て `/api/v1/...` 配下なので既存挙動は変わらない。

### 案B:route-scoped を「正しく」分離する

- 「このサブツリーは登録必須」を**宣言的**に表す。route-scoped の WS 漏れは、**空ルートを使わず & public と衝突しない専用サブツリー**にすれば回避できる(ハーネスの候補 H で実証済)。

```kotlin
route("/api/v1") {
    rpc("/resident/register") { ... }        // public(衝突しない位置)
    route("/secured") {                       // 衝突しない専用枝
        install(RequireRegisteredUserPlugin)  // route-scoped でも正しく効く
        rpc("/resident") { ... }              // = /api/v1/secured/resident
        rpc("/stock") { ... }
        ...
    }
}
```

- **保護ルートのパスが全部変わる**(`/api/v1/secured/...`)。frontend の `open()` パスも全面変更。
- 「登録必須かどうか」がルート構造に出るのは分かりやすいが、**変更コストが大きい**。

### 案C:登録判定を「アプリ境界(guarded)」に寄せ、ルーティング層のガードを撤廃(方針見直し系)

- 現状すでに **per-message の `guarded(session)`** が存在し、失効判定・例外翻訳をやっている。**登録必須もここに寄せる**。
- 保護コントローラは `guarded` の代わりに **登録済みを要求する変種**を使い、`ResidentId` を受け取る:

```kotlin
// 認証済みなら誰でも(public 登録ルート用)
guarded(session) { ... }

// 登録済み必須(residentId を渡す)。Unregistered なら Unauthorized で短絡
guardedRegistered(session) { residentId -> ... }
```

- ルーティング層の `RequireRegisteredUserPlugin` を**廃止**できる → §4 の Ktor-WS-ルーティングの罠そのものから解放される。
- パス/バージョン非依存(transport ではなくアプリ境界で判定)。
- 「認証ポリシーを 1 箇所(`guarded` 系)に集約」でき、`requireResidentId()` が "到達不能な不変条件違反" 前提だったのを、**正式な判定**に格上げできる。
- コスト:各保護コントローラが `guardedRegistered` を使うよう一括変更(機械的)。「付け忘れ」は `guarded`(認証のみ)で通ってしまう=**fail-open のリスクが個々のコントローラに移る**点に注意(型で縛る工夫が要る)。

---

## 7. 深掘り比較(確実に直るか / 負債 / 方針の妥当性)

| 観点 | 案A deny-by-default | 案B route 分離 | 案C アプリ境界 |
|---|---|---|---|
| 今回のバグを確実に直すか | ◎(実証済の app レベル方式) | ◎(候補 H で実証) | ◎(ルーティング層を使わない) |
| v2/バージョニング | ◎ 自動保護(fail-closed) | ◎ 枝ごと | ◎ パス非依存 |
| パス文字列のドリフト | ◎ 定数 1 か所 | ○ 構造で表現 | ◎ パスに依存しない |
| 付け忘れの倒れ方 | ◎ fail-closed | ○ 枝に入れ忘れると public 化 | △ コントローラが `guarded` を選ぶと素通し(fail-open) |
| frontend への影響 | なし | **大(全保護パス変更)** | なし |
| 複雑さ / 変更量 | 小 | 中〜大 | 中 |
| レイヤの適切さ | 認可を transport 前段で(現行踏襲) | 同左 | **認可をアプリ境界に集約(より本筋)** |
| Ktor-WS の罠への耐性 | ◎(app レベル) | ○(構造を守る限り) | ◎(そもそも使わない) |

> 案A の prefix は **`/api/`(バージョン非依存)** にするのが肝。`/api/v1` 固定は v2 が素通し(fail-open)、逆に prefix 完全撤廃の純 deny-by-default は `/healthz` 等の非 API パスまで「登録済み必須」で 401 にしてしまう。`/api/` で絞ると「v2 も自動保護(fail-closed)」かつ「非 API は素通し」を両立できる。

### 「そもそも現方針は破綻していないか?」への所見

- **認証(誰か)= ハンドシェイクで JWT 検証**、**失効= per-message guard**、という二段構えは WS の性質に対して妥当。破綻していない。
- 唯一きしんでいるのは **「登録済みか(認可の一種)」を transport/ルーティング層の route-scoped プラグインで表現していた**点。ここが Ktor の WS ルート解決の癖と相性が悪かった。
  - 短期的には **app レベル化(案A)** で十分堅牢。
  - 中期的には **認可をアプリ境界(`guarded` 系)に一元化(案C)** が最も筋が良い(失効判定と同じ場所・パス非依存・transport の癖から独立)。

---

## 8. 推奨

- **まず案A**(`/api/` バージョン非依存 prefix + deny-by-default + 共有定数 allowlist)で PR #109 を作り直す。差分が小さく(実機実証済 candI の prefix を `/api/v1`→`/api/` に広げ、public パスを定数化するだけ)、v2/fail-open とドリフトという指摘を直接つぶせる。非 API パスは素通しのまま。
- **案C は別タスク**として検討(認可のアプリ境界一元化)。`guardedRegistered` の型設計(付け忘れを fail-open にしない工夫)をちゃんとやる価値があるので、今回のバグ修正とは分ける。
- 案B は frontend 影響が大きい割に、案A/C 比で旨味が薄いので非推奨。

## 9. 未決事項(合意したいこと)

1. 当面の修正は **案A** でよいか(それとも §10 の根本再設計に進むか)。
2. API バージョニングは実際に来るか/来るならパス(`/api/v2`)方式か、それとも §10 のサブプロトコル方式か。
3. 案C(アプリ境界一元化)を将来タスクとして起票するか。
4. **§10 のパス設計再考**(単一エンドポイント化)を将来タスクにするか。
5. 検証で local DB に手挿入した admin の resident 行(display_name=Admin)を残すか消すか。

---

## 10. 補足:パス設計そのものの再考(Q1–Q3)

レビューで「そもそもパスの切り方/`/api`/`v1` は WS-RPC に適切か?」という根本的な問いが出た。3 点とも妥当な指摘で、結論は同じ方向(現在の REST 風パス設計は kotlinx-rpc-over-WS には噛み合っていない)を指す。

### Q1. RPC ごとにパスを分ける必要があるか? → **無い(実機確認済)**

- kotlinx-rpc は **1 本の WS エンドポイントに複数 @Rpc サービスを相乗り**できる。1 接続を多重化して `withService<A>()`/`withService<B>()` の両方を呼べることをハーネスで確認した:
  ```kotlin
  rpc("/rpc") {                               // 単一エンドポイント
      registerService<ResidentRegisterRpcService> { ... }
      registerService<ResidentRpcService> { ... }
  }
  // client: 1 接続で両方呼べる
  val c = client.rpc("ws://.../rpc")
  c.withService<ResidentRegisterRpcService>().rename(...)
  c.withService<ResidentRpcService>().me()
  ```
- 現状(サービス 1 つ = パス 1 つ)の帰結:
  - frontend は使うサービスごとに **WS 接続を別々に張る**(接続数が増える)。
  - **パスベースのルーティング複雑性**が生まれ、まさに今回のバグ(route-scoped ガードの WS 漏れ)の温床になった。
- 単一エンドポイント化すると:接続数減・パスルーティング消滅・**route ベースの認可ゲートが構造的に不可能**になり、認可は自動的にアプリ/メッセージ層(=案C)へ寄る。→ Q1 と修正方針は地続き。
- 逆に「パスを分ける」唯一の実利は「サービス単位で HTTP 層のルーティング/ゲートを変えられる」点だが、**それが今回の事故原因**なので mindstock にとっては利点になっていない。

### Q2. パスに `/api` を入れるのは適切か? → **REST 由来。必須ではないが運用上は有用**

- WS-RPC のエンドポイントは「マウント点」でしかなく、`/api` は REST の名残で RPC 的な意味は持たない。
- ただし **リバースプロキシ / dev-server proxy のルーティング、静的アセットとの分離**には接頭辞があると便利(deux: 現に dev は `:8080→:8090` を `/api` 系で振り分けている想定)。= 「意味」ではなく「運用上の都合」で残す価値はある。
- 正直に言えば、単一マウント `/rpc`(または `/api/rpc`)の方が実体に忠実。

### Q3. パスに `v1` を付けるのは、ステートレスでない WS-RPC で弊害が出ないか? → **出る。指摘どおり**

- REST がパスにバージョンを置くのは、各リクエストが独立・ステートレスで URL でルーティングするから。**WS-RPC は永続・ステートフルな 1 接続**で、バージョンを付けたいのは URL リソースではなく **RPC 契約(@Rpc インターフェース群 + シリアライズ形式)**。
- WS エンドポイントをパスでバージョニングするのは噛み合わせが悪く(今回もパスにバージョンが絡んで route 解決の癖を踏んだ)、**バージョンはハンドシェイクのサブプロトコル交渉で表すのが筋**。mindstock は既に `mindstock.v1` を**アプリプロトコル識別子としてサブプロトコルに持っている** → 契約を上げるときは URL ではなくここを `mindstock.v2` にするのが自然。
- kotlinx-rpc 自体にパスバージョニングの仕組みは無い(契約 = コンパイル済みインターフェース)。

### 「正直な」ターゲット像

3 つの答えを合わせると、WS-RPC に忠実な形はこうなる:

```text
- エンドポイント: 単一(例 /api/rpc)。全サービスを 1 接続に相乗り
- 認証:           ハンドシェイクで JWT 検証(現行 MindstockAuth のまま、1 接続 1 回)
- バージョン:     サブプロトコル mindstock.v1 / v2 で交渉(URL に出さない)
- 認可(登録要否): アプリ/メッセージ層(guarded 系)で判定(=案C)。route で分けない
```

これは「§6 案C(アプリ境界で認可)」+「単一エンドポイント化」の合わせ技で、**今回のバグのクラス(Ktor の WS ルート解決の癖)を構造ごと消す**。

### ただし「いま」やるべきか

- **実害(未登録ユーザが登録できない)は今ブロッカー**なので、まず小さく塞ぐ価値はある(案A)。
- 単一エンドポイント化 + 案C は **frontend(`RpcClientProvider` の per-service open → 単一 open)・全コントローラ・サブプロトコル versioning** に波及する**設計変更**で、別 PR/別タスクが妥当。
- 注意:もし「どうせ単一エンドポイント+案C に作り替える」と今決めるなら、**案A の修正は捨て駒**になる。その場合は PR #109 を最小限の暫定(または close)にして、本設計に直行する手もある。ここは方針判断が要る。
