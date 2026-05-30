# Gradle スクリプト死コード整理 設計

- 日付: 2026-05-30
- 対象: ルート / `build-logic` convention plugins / 各モジュールの `*.gradle.kts`、`gradle/libs.versions.toml`
- 方針: 挙動を変えずに、過去の構成変更(subproject-restructure / backend-module-restructure)で取り残された死コード・重複設定を除去する

## 背景

2026-05-23 の subproject-restructure と 2026-05-29 の backend-module-restructure で、旧
`:shared:rpc` / `:shared:extensions` / `:backend:application:api` / `:backend:infrastructure:*` /
`:backend:infrastructure:migration:executor` といったツリーが、現行の
`:domain` / `:rpc` / `:shared` / `:backend:core` / `:backend:api` / `:backend:schedules`
へ再編された。この過程で Gradle 側の convention plugin・プラグイン宣言・重複設定が完全には追従しておらず、
未使用ファイルや冗長記述が残っている。

JS ターゲット(`js(IR)` + `wasmJs` の二本立て、`webMain` 中間ソースセット、`*Js.kt` actual)は
**意図的に維持する**(ユーザ判断)。本整理の対象外とする。

## スコープ(採用: 案 B = dead removal + 軽い DRY 整理)

挙動は不変。convention plugin が既に提供している設定の重複を取り除き、convention plugin と
モジュール build script の責務境界を整理する。

### 1. `kotlin-jvm-testcontainers` convention plugin を削除

- 削除: `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts`
- 根拠: 全 `build.gradle.kts` を grep して参照ゼロ。`:backend:infrastructure:migration:executor`
  撤去(testcontainers を外部 Postgres + integration タグへ置換した時点)で孤児化した。
- 影響: なし(誰も適用していない)。

### 2. Ktor Gradle plugin の配線を撤去(ライブラリは残す)

`io.ktor.plugin`(fat-jar / EngineMain パッケージング用)は宣言だけ存在し、どの convention plugin・
モジュールも `apply` していない。`:backend:api:run` は `application` plugin で動作しており、Ktor plugin に依存しない。

- 削除: ルート `build.gradle.kts` の `alias(libs.plugins.ktor) apply false`
- 削除: `build-logic/build.gradle.kts` の `implementation(libs.plugin.ktor)`
- 削除: `gradle/libs.versions.toml`
  - `[libraries]` の `plugin-ktor = { module = "io.ktor.plugin:plugin", version.ref = "ktor" }`
  - `[plugins]` の `ktor = { id = "io.ktor.plugin", version.ref = "ktor" }`
  - `[versions]` の `ktor = "3.5.0"` は上記 2 つ撤去後に未使用となるため削除する。
    `settings.gradle.kts` の `from("io.ktor:ktor-version-catalog:3.5.0")` はリテラル指定で
    この version 変数を参照していないため影響なし(外部 Ktor version catalog 経由の `ktorLib.*` も無関係)。
- **残す**: `ktorLib.server.*` / `ktorLib.client.*` / `ktorLib.serialization.kotlinx.json` 等の
  Ktor ライブラリ一式(これらは使用中)。
- 影響: なし(Ktor plugin の機能は未使用)。将来 fat-jar 等のデプロイパッケージングが必要になった時点で再導入する。

### 3. `:backend:api` の重複設定を除去

`backend/api/build.gradle.kts` は `ktor-server` convention(= `kotlin-jvm` + `application`)を適用している。

- 削除: `application { mainClass.set("net.brightroom.mindstock.MainKt") }`
  - `ktor-server` convention が同一値をデフォルト設定済み(`mainClass.set("net.brightroom.mindstock.MainKt")`)。
- 削除: `tasks.test { useJUnitPlatform() }` 内の `useJUnitPlatform()` 呼び出し、および
  `integrationTest` 登録ブロック内の `useJUnitPlatform()` 呼び出し。
  - `kotlin-jvm` convention の `tasks.withType<Test>().configureEach { useJUnitPlatform() }` が
    全 `Test` タスク(`test` と、`Test` 型として登録される `integrationTest` の両方)に付与済み。
- **残す**: `tasks.test` / `integrationTest` の `systemProperty("kotest.tags.*", ...)`、
  `group` / `description` / `testClassesDirs` / `classpath` / `shouldRunAfter` /
  `TEST_DB_*` env 転送、`tasks.check { dependsOn(integrationTest) }` 等のタグ・配線ロジック。
  - 結果として `tasks.test { ... }` ブロックは `systemProperty(...)` のみを残す。

### 4. `useJUnitPlatform()` を `kmp-shared` convention に集約

- 追加: `build-logic/.../net.brightroom.mindstock.kmp-shared.gradle.kts` に
  `tasks.withType<Test>().configureEach { useJUnitPlatform() }`
- 削除: `domain/build.gradle.kts` 末尾の同一ブロック。
- 根拠: `kmp-shared` は `jvm()` ターゲットを持ち、`:domain` / `:rpc` / `:shared` が JVM テストで
  JUnit5 を要する。convention に集約することで DRY 化し、`:domain` の重複宣言を解消する。
- 注意: `compose-web`(`:frontend`)は `jvm()` ターゲットを持たないため対象外で問題なし。

### 5. `:shared` の空 `dependencies {}` ブロックを削除

- 削除: `shared/build.gradle.kts` の `commonTest.dependencies {}` / `jvmMain.dependencies {}` /
  `jvmTest.dependencies {}` / `wasmJsTest.dependencies {}`(中身が空のブロック)。
- **残す**: `commonMain.dependencies { ... }`(coroutines / serialization / datetime)と
  `wasmJsMain.dependencies { implementation(npm("@js-joda/timezone", "2.3.0")) }`。
- 影響: なし(空ブロックはノイズ)。

## 明示的に対象外

- **JS ターゲット / `webMain` / `*Js.kt` actual**: 両ターゲット維持の判断に従い一切触らない。
  `webMain` は `applyDefaultHierarchyTemplate` が `js`/`wasmJs` → `webMain` の `dependsOn` を
  名前ベースで張るため正常動作しており、壊れていない。
- **`ktorLib.*` ライブラリ**: 使用中。
- **`exposed.migration` plugin**: `:backend:core` で正しく適用中(ルートは `apply false` で classpath 供給)。
- **`foojay-resolver-convention`**: `jvmToolchain(25)` の JDK 自動プロビジョニングに必要。
- **`google()` リポジトリ(androidx/com.android/com.google フィルタ付き)**: Compose Multiplatform が
  `androidx.*` を推移依存で要求するため必要。
- **ルートの他の `apply false` エイリアス**(kotlin.jvm / multiplatform / serialization / compose.* /
  kotlinx.rpc / exposed.migration): いずれも convention plugin かモジュールで適用されているため残す。
  これらが `includeBuild("build-logic")` により冗長化していないかの棚卸しは案 C の領域であり、本スコープ外。

## 検証

memory の local-build-tips(frontend WasmJs は OOM、統合テストは `--max-workers=1`)に従う。

- バックエンド中心のビルド:
  `./gradlew :domain:build :backend:core:build :backend:api:build :rpc:build`
- frontend のコンパイルのみ確認(フル build は OOM 回避で避ける):
  `./gradlew :frontend:compileKotlinWasmJs`
- テストタスクが解決・実行できること:
  `./gradlew test` / `./gradlew integrationTest`(必要に応じて `--max-workers=1`)
- `./gradlew help` 等で settings / catalog のパースエラーが出ないこと(catalog エントリ削除の影響確認)。

## 想定リスク

- 低。すべて未使用または convention plugin が既に提供している重複の除去であり、挙動は不変。
- 唯一の注意点は catalog の `ktor` version 削除。`version.ref = "ktor"` の他参照が無いことを
  削除前に再 grep して確認する(現時点の調査では `plugin-ktor` と `ktor` plugin の 2 箇所のみ)。
