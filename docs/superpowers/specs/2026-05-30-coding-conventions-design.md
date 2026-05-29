# コーディング規約 design

> 対象読者: Claude(セッションで本リポジトリを編集する自分)
> 配置: ルート `CLAUDE.md` + `.claude/rules/*.md`(フラット、`paths` frontmatter で条件付きロード) + `.claude/settings.json` の hooks(機械的強制)
> 出典: `.tmp/tmp/` 配下 4 ファイル(memory 抽出 / docs 抽出) + 公式ドキュメント `https://code.claude.com/docs/ja/memory`, `https://code.claude.com/docs/ja/hooks`

## 1. 背景と目的

これまでプロジェクトの設計判断・コーディング方針は次の 2 箇所に分散していた。

- `~/.claude/.../memory/` 配下の auto-memory(feedback / project 系)
- `docs/superpowers/specs/` 配下の時系列 spec(28 本)

いずれもセッションで毎回読まれる保証がなく、また spec は時系列で「後の Plan が初期方針を覆した」変遷が含まれるため、現状方針の抽出にコストがかかる。本 spec は「コーディング時の判断に直接効くルール」を **新方針側で確定** し、Claude が編集対象ファイルに応じて必要分だけ読み込める形に再配置する。

ゴール:

- セッション中、Claude が編集中ファイルに関連する規約を **自動的に** コンテキストに乗せる
- CLAUDE.md は薄く保ち、各セッション開始時の token 消費を最小化する
- 規約と詳細仕様(spec)を分離し、規約は「結論+Why+How to apply」、詳細は spec を参照する形にする

非ゴール:

- OIDC / Flyway / 観測性 / Ktor application 構成 / DB スキーマ詳細など「一度決めれば再度判断しない」項目は規約化しない。既存 spec 側に残す
- コミットメッセージ規約は本 spec の対象外(memory に残す運用を継続)
- 人間の新規参画者向けドキュメントは別途検討
- リンター / フォーマッタで強制できるスタイル詳細(インデント、import 並び順 等)は規約に書かない → §5 の hooks で機械的に強制する

## 2. 全体構成

```
mindstock/
├── CLAUDE.md                            ← 新規(薄く・200 行以内)
└── .claude/
    ├── settings.json                    ← 新規(hooks による Spotless 自動実行)
    └── rules/                           ← 新規(フラット構成)
        ├── software-architecture.md
        ├── domain-guideline.md
        ├── error-handling.md
        └── rpc-and-transactions.md
```

### 2.1 命名による scope 区別

`.claude/rules/` 直下にフラットに配置する(サブディレクトリで階層化しない)。現状は backend のみ実装されているため、ファイル名にプレフィックスを付けずシンプルな名前にする。

将来 frontend に着手して frontend 固有の規約ファイルが必要になったら、その時点で **両方に prefix を付けて rename**(例: `software-architecture.md` → `backend-software-architecture.md`、新規 `frontend-state-management.md` 等)し、フラットなまま区別する。両方に効く横断ルールが出てきた場合は prefix 無し(例: `naming.md`)で残す。

`paths` frontmatter の glob によって実際の読み込みスコープは決まるので、ファイル名は人間/Claude が一覧時に分類しやすくするためだけのもの。

### 読み込みの挙動(公式仕様)

- `CLAUDE.md` は **全セッション開始時** に常時読み込み
- `.claude/rules/*.md` は **`paths` frontmatter の glob にマッチするファイルを Claude が読むとき** にコンテキスト注入される(セッション開幕より後で届くため CLAUDE.md より遵守率が高い)
- `paths` を持たないルールは無条件・常時読み込み(本 spec では使わない)

## 3. CLAUDE.md の内容

200 行以内・薄く保つ。次のセクション構成:

1. **プロジェクト概要**(1-2 行: 家庭の在庫管理 SaaS / Kotlin Multiplatform)
2. **技術スタック**(名前のみ列挙、**バージョンは書かない**: Kotlin Multiplatform / Ktor / kotlinx-rpc / Exposed / Compose Multiplatform(Kotlin/Wasm) / PostgreSQL / Zitadel OIDC)
3. **モジュール構成マップ**(主要ディレクトリと役割、5-8 行)
   - `:domain` / `:backend:core` / `:backend:api` / `:backend:schedules` / `:rpc` / `:shared` / `:frontend`
4. **主要コマンド**(build / test / integrationTest / frontend dev、各 1 行)
   - frontend を含む `./gradlew build` は WasmJs compile で OOM るため、影響が backend に閉じる場合は `:domain:build :backend:core:build :backend:api:build :rpc:build` で frontend を外す
   - 統合テストの並列実行による Postgres 接続枯渇は **テストコード/設定側で解消すべき別件** として扱い、本規約では触れない
5. **絶対守る原則**(3-5 行、詳細は rules)
   - 層責務(依存方向)/ nullable 禁止 / リッチドメイン / `@Rpc` 必須
   - 末尾に「詳細規約は `.claude/rules/*.md` に置かれ、編集対象に応じて自動でロードされる。フォーマットは `.claude/settings.json` の hook で機械的に強制される」と一文

書かないもの:

- バージョン番号(更新忘れリスク)
- ハマりポイントの一覧(`rpc-and-transactions.md` に統合)
- コミットメッセージ規約
- リンター/フォーマッタで強制できるスタイル詳細
- 否定だけのルール(否定は代替案とセットでルール側に置く)

## 4. `.claude/rules/` 各ファイル設計

各ファイル共通のテンプレート構造:

```markdown
---
paths:
  - "<glob>"
---

# <topic>

## Rule
- 規約本体(箇条書き、検証可能な具体性)

## Why
短い理由(なぜこのルールか)

## How to apply
具体的な適用例 / アンチパターン / コードスニペット(短く)

## 関連
- spec: docs/superpowers/specs/YYYY-MM-DD-xxx-design.md
- rule: [other-rule](other-rule.md)
```

### 4.1 `software-architecture.md`

- **paths**: `domain/**/*.kt`, `backend/**/*.kt`
- 扱うトピック:
  - 層責務と依存方向(presentation → application ← infrastructure / domain は横断 / configuration は片方向 glue)
  - 各層に「何を置くか」「何を置かないか」
  - Controller(presentation): 腐敗防止層 + ユーザ入力 ↔ application の橋渡し、業務ロジックは持たず薄く保つ
  - Scenario(application): **複数 Service をまたぐユースケース単位**。配置は `application/scenario/<ctx>/`。Scenario 同士の呼び出しは不可
  - Service(application): ビジネスロジックを持たない薄い orchestration、引数・戻り値は VO / 集約 / ファーストクラスコレクションのみ(primitive・raw List 公開禁止)、Repository が返した値の null チェックは書かない(infra が例外で表現、Service は素通し)
  - Repository(application interface / infrastructure 実装): interface 側で例外 throw は規約化しない(契約のみ)、一覧は空のファーストクラスコレクション、Reader/Writer 分離(`<Ctx>Repository` / `<Ctx>RegisterRepository`)、Hydration は `<Aggregate>Hydration.kt` の internal extension
  - DataSource(infrastructure): `transaction {}` を書かない(plugin/`tx()` で境界管理)、行が無ければ `ResourceNotFoundException` を throw(Service / Scenario が素通しの前提)
  - パッケージ境界判断: 主は **概念区別**(役割が違う / 独立した塊 / 依存方向遮断)。**ファイル数も基準**(目安 5-7 を超えたら集約見直しトリガーとして使う、機械的分割はしない)
  - 命名対称(Controller / Scenario / Service / Repository / DataSource)、`Handler` 命名は採用しない
- 採用しない変遷項目: Koin(→ Ktor 標準 DI)、`Handler` 命名(→ `Service` / `Scenario` に統合)など、最新方針のみ採録

### 4.2 `domain-guideline.md`

- **paths**: `domain/**/*.kt`
- 扱うトピック:
  - **domain で許容する外部ライブラリ**(明示・列挙):
    - `kotlin` stdlib(`kotlin.time.Clock` 含む)
    - `kotlinx-serialization`(`@Serializable`, `@SerialName`)
    - `kotlinx-datetime`
    - 上記以外は domain に持ち込まない。新規依存は「外して複雑化しないか / 取り込んでも品質を保てるか」で個別判断
  - リッチドメイン 7 原則(behavior-rich / aggregate は object graph / composition 優先 / `id` private / `createdAt` 排除 / 不変更新 / fact は repository 内部)
  - Value Object 規約: `@Serializable @JvmInline value class`、バッキングフィールド `value`(private)、`init { require(...) }` で IAE、`internal operator fun invoke(): T = value`、`toString()` 必須
  - **ファクトリ関数の方針**: 意味のあるもの(UUID 生成・Clock 呼び出しなど副作用を伴う / コンストラクタだけでは作れないもの)は許容(例: `UserId.create()`、`OccurredAt.now()`)。意味のないもの(`Quantity.of(123)` のようにコンストラクタと等価)は NG
  - **ファーストクラスコレクション**(First-class Collection): `Products` / `Stocks` / `HouseholdMembers` / `StockMovements` / `CatalogItems`、`val list: List<T>` を public(イテレーション / map / filter は `.list` を使う)、`fun size()` を持つ(`.list.size` 経由の件数取得は NG、集合の責務として `size()` を呼ぶ)、`asList()` は廃止、ドメイン操作のみメソッド化(`owner()`, `activeOnly()`, `netQuantity()` 等)
  - 時間: VO 内で `kotlin.time.Clock.System.now()` 許容
  - sealed interface でポリモフィズム、`@JvmInline value class` を variant に使用可
- 採用しない変遷項目: `id` public、`asList()` 保持、`sealed DomainException`、`type` フィールド残し など

### 4.3 `error-handling.md`

- **paths**: `**/*.kt`(全層に効くため広め)
- 扱うトピック:
  - 戻り値・公開 API に `T?` を使わない(原則禁止、導入は事前にユーザ承認)
  - 「不在」は例外 or sealed 型で表現(空 List は別概念。空が正常なら空のファーストクラスコレクションを返す)
  - 単一値の不在: `domain/exception/ResourceNotFoundException(reason: String)` を throw
  - VO の値域違反: stdlib `IllegalArgumentException`(`require`)
  - Service / Scenario は素通し(Repository の戻り値の null チェックや再 catch を書かない)
  - ドメイン例外はワイヤー越境しない
  - どこで catch するかは状況に応じて柔軟に決める(規約上の固定ルールは設けない)

### 4.4 `rpc-and-transactions.md`

- **paths**: `backend/api/**/presentation/rpc/**/*.kt`, `backend/api/**/configuration/{routing,logging,transaction}/**/*.kt`
- 扱うトピック:
  - `@kotlinx.rpc.annotations.Rpc` 必須、`RemoteService` 継承不可(0.10.2 で `@Deprecated(ERROR)`)
  - Service Impl は **WebSocket 接続単位で 1 度だけ instantiate**(`registerService<T> { factory }` の factory)
  - factory は非 suspend。Service/Repository は `RoutingConfiguration` で `val by dependencies` で先取り
  - `ApplicationCall` 取得: `this.applicationCall`(`configuration.auth.applicationCall` extension)
  - **Json 分離**: `Krpc` plugin は `ClassDiscriminatorMode.POLYMORPHIC` 必須 → `KrpcJson`。HTTP `ContentNegotiation` は `CustomJson`
  - **`tx()` の位置付け**: DB を触る RPC method のみ必須(`ExposedTransactionPlugin` は WS upgrade 時 1 回しか張らないため、RPC method ごとに `tx(database) { ... }` で transaction を張り直す)。`tx` = `supervisorScope { newSuspendedTransaction(db) { ... } }`。DB を触らない RPC(S3 upload 等)は `tx()` 不要
  - RPC 戻り値: `RpcResult<T, RpcError>`、`T` は non-null
  - `RpcError` は sealed interface(`:rpc/RpcError.kt`): `Unauthorized` / `NotFound` / `BadRequest` / `Conflict` / `Internal`
  - **RPC 引数・戻り値の型**: API 仕様と application 内部の型が一致するなら VO / ID / 集約 / ファーストクラスコレクションを直接受ける。型がずれる(API 仕様として複合パラメータをまとめたい / 内部表現を露出したくない / 互換維持等)場合のみ `presentation/rpc/<ctx>/` 配下に `Request` / `Response` data class を作りマッピングする。kotlinx-serialization の標準 deserialize は data class / `@JvmInline value class` のコンストラクタを呼ぶので `init { require(...) }` の不変条件は wire 経由でも保たれる
  - domain = wire-format(中間 DTO/mapper は必要な時のみ、改変時は同時 deploy 前提)
  - routing は認証レルムで nest(`authenticate("user") { rpc("/api/v1/...") { ... } }`)
- 採用しない変遷項目: `RemoteService` 継承、`type` フィールド残し、Koin

## 5. 機械的強制: `.claude/settings.json` の hooks

リンター / フォーマッタで強制できるスタイル詳細(インデント、import 並び順、空行など)は **規約に書かない**。代わりに Claude Code の **`PostToolUse` hook** で **編集後に必ず Spotless を走らせる** ことで、確率的でなく毎回確実に強制する。

### 5.1 方針

- 規約(`.claude/rules/`)に書くのは「ロジック上の判断」が必要なルールのみ
- 機械的に判定・修復可能なものは `.claude/settings.json` の hook に寄せる
- hook は **リポジトリにコミット**(`.claude/settings.json`)し、チーム/将来の自分のセッションでも同じ強制が効くようにする

### 5.2 hook 仕様(概要)

`.claude/settings.json` に `PostToolUse` の `matcher: "Write|Edit|MultiEdit"` を登録し、`*.kt` / `*.kts` を編集した直後にプロジェクトの Spotless task を該当ファイル限定で走らせる。

```jsonc
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/format-kotlin.sh",
            "timeout": 120
          }
        ]
      }
    ]
  }
}
```

### 5.3 `.claude/hooks/format-kotlin.sh`(概要)

- stdin の JSON から `.tool_input.file_path` を抽出
- 拡張子が `.kt` / `.kts` の場合のみ `./gradlew spotlessApply -PspotlessFiles=<absolute path>` を呼ぶ(Spotless の `spotlessFiles` プロパティで対象を限定し全プロジェクトに広げない)
- それ以外は `exit 0`
- Spotless が変更しても hook は `exit 0`(成功扱い)
- 致命的失敗(Spotless 実行不能 等)時のみ `exit 2`(Claude にエラーを返す)

### 5.4 注意事項

- `.zshrc` 等の対話シェル出力が stdout に混ざると JSON parse が壊れる → スクリプトは `#!/usr/bin/env bash`(非対話)で書く
- timeout は Gradle daemon の cold start を考慮して 120s
- gradle warm-up コスト軽減のため Gradle daemon を有効に保つ(`org.gradle.daemon=true`)
- パフォーマンスが厳しい場合の代替案として `ktlint` バイナリ直接呼び出しも検討するが、初版は Spotless で統一する(プロジェクトの既存設定と整合)
- frontend を扱うようになったら同 hook で TypeScript/Compose にも分岐を足す

## 6. 「変遷あり」項目の確定方針

`from-docs` 抽出資料に明示されていた変遷を、新方針側で固定する:

| 項目 | 旧方針 | 新方針(本規約) |
|---|---|---|
| RPC service interface | `RemoteService` 継承 | `@Rpc` annotation |
| 集約 `id` 可視性 | `public val` | `private` + internal accessor |
| 集合型 API | `asList()` / `size` メソッド | `val list` 公開 + `fun size()`、`asList()` 廃止、`.list.size` 直接アクセスは NG |
| ドメイン例外 | `sealed DomainException` | `IllegalArgumentException`(VO) + 専用例外(必要時のみ) |
| DI | Koin | Ktor 標準 DI(`dependencies {}`) |
| Stock movement ID | domain で持つ | domain で持たない(`occurredAt` で順序付け) |
| RPC 引数 | 「集約丸ごと禁止、VO/ID のみ」 | 「腐敗防止層の必要性で判断、不要なら集約も直接 OK」(kotlinx-serialization が init を呼ぶ事実を踏まえる) |
| 例外翻訳 | `tx()` が一括 | 規約上の固定ルールは設けない(状況に応じて) |
| Handler 命名 | あり | 廃止(`Service` / `Scenario` に統合) |

## 7. 実装に必要な確認事項(plan 着手前に)

memory `before-plan-3` の方針に基づき、規約として書き出す前に **現行コードと整合確認** が必要な項目:

- `software-architecture.md` の「各層の責務分担」が実コードと一致するか(特に `:backend:api` のサブパッケージ構成)
- `domain-guideline.md` の VO テンプレが現行 domain VO と一致するか(`init { require }` / `internal invoke()` / `toString()` の徹底度)
- `rpc-and-transactions.md` の `KrpcJson` / `tx()` パス指定が現行コードと一致するか
- 集合型 wrapper の `val list` 統一が完了しているか(`refactor/plan-c-phase4-occurredat-init` 中なので未完の可能性あり)

乖離があった場合、規約側ではなく **実装側 or spec 側を真として** 規約を寄せる(memory `domain-cohesion-coupling-review-2026-05` 原則)。

## 8. 想定する作業の流れ(writing-plans に引き継ぐ)

1. 上記「7. 確認事項」を現行コードで grep し、規約のドラフトを実装に合わせる
2. `CLAUDE.md` を新規作成
3. `.claude/rules/{software-architecture,domain-guideline,error-handling,rpc-and-transactions}.md` を新規作成
4. `.claude/settings.json` と `.claude/hooks/format-kotlin.sh` を新規作成、実機で hook が走ることを確認
5. `/memory` または手元で読み込み挙動を確認(`paths` が効くか)
6. 既存 spec 内で本規約と重複する記述は触らない(spec は時系列の記録として残す)

## 9. 参考文献

- 公式: `https://code.claude.com/docs/ja/memory`(`.claude/rules/` + `paths` frontmatter)
- 公式: `https://code.claude.com/docs/ja/hooks`(`PostToolUse` の matcher / JSON schema)
- 関連 spec(本規約のソース):
  - `docs/superpowers/specs/2026-05-23-domain-layer-design.md`
  - `docs/superpowers/specs/2026-05-23-mindstock-design.md`
  - `docs/superpowers/specs/2026-05-24-domain-richness-design.md`
  - `docs/superpowers/specs/2026-05-24-repository-implementation-design.md`
  - `docs/superpowers/specs/2026-05-24-usecase-design.md`
  - `docs/superpowers/specs/2026-05-25-rpc-layer-design.md`
  - `docs/superpowers/specs/2026-05-29-backend-module-restructure-design.md`
  - `docs/superpowers/specs/2026-05-29-domain-cohesion-coupling-design.md`
- 関連 memory: `domain-model-style` / `domain-refactor-policy-2026-05` / `kotlinx-rpc-0.10.2-conventions` / `krpc-ws-pipeline-gotchas` / `before-plan-3`
