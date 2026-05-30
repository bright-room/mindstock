# Gradle スクリプト死コード整理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 過去の構成変更で取り残された未使用 convention plugin・未適用 Ktor plugin・convention plugin と重複する設定を、挙動を変えずに除去する。

**Architecture:** すべて Gradle 設定の削除/集約のみ。プロダクションコード・テストコードは一切変更しない。各タスクは独立して適用・コミット可能で、変更後に該当範囲のビルド/パースが通ることを検証条件とする。

**Tech Stack:** Gradle (Kotlin DSL), Kotlin 2.3.21 Multiplatform, build-logic composite build (convention plugins), version catalog (`gradle/libs.versions.toml`), spotless (ktlint, `*.gradle.kts` 対象)。

参照 spec: `docs/superpowers/specs/2026-05-30-gradle-script-cleanup-design.md`

---

## 共通の注意

- **frontend のフル build は OOM するため避ける**(memory: local-build-tips)。frontend はコンパイルタスク単体で確認する。
- 統合テストを走らせる場合は `--max-workers=1`。
  - ⚠️ **2026-05-30 以降は不要**: テスト側 HikariCP プールをキャップして接続枯渇を解消済み(`docs/superpowers/specs/2026-05-30-integration-test-pool-capping-design.md`)。本ドキュメント内の `--max-workers=1` 付きコマンドは当時の記録。
- `*.gradle.kts` は spotless(ktlint)対象。編集後にフォーマット崩れがあれば
  `./gradlew spotlessApply` で整形してからコミットする。
- 各タスクは「変更 → 検証コマンドが成功 → コミット」の順。検証コマンドが失敗したら原因を直してから次へ進む。

---

## Task 1: 未使用 convention plugin `kotlin-jvm-testcontainers` を削除

**Files:**
- Delete: `build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts`

- [ ] **Step 1: 参照ゼロを確認(削除前検証)**

Run:
```bash
grep -rn "kotlin-jvm-testcontainers" --include='*.gradle.kts' .
```
Expected: **出力ゼロ行**。どのモジュールも `id("net.brightroom.mindstock.kotlin-jvm-testcontainers")` で適用しておらず、削除対象ファイル自身もこの文字列を含まない。

- [ ] **Step 2: ファイルを削除**

```bash
git rm build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm-testcontainers.gradle.kts
```

- [ ] **Step 3: convention plugin 群が健全であることを検証**

Run:
```bash
./gradlew :domain:help --console=plain
```
Expected: BUILD SUCCESSFUL。`:domain` の configuration が `kmp-shared` convention を適用する過程で
build-logic 全体がコンパイルされる。削除した plugin への参照は元々無いためエラーにならない。

- [ ] **Step 4: コミット**

```bash
git add -A
git commit -m "build: 未使用の kotlin-jvm-testcontainers convention plugin を削除"
```

---

## Task 2: 未適用の Ktor Gradle plugin の配線を撤去

`io.ktor.plugin` はどの convention plugin・モジュールも `apply` していない。`:backend:api:run` は
`application` plugin で動作するため不要。ライブラリ(`ktorLib.*`)は対象外。

**Files:**
- Modify: `build.gradle.kts`(ルート)
- Modify: `build-logic/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Ktor plugin が未適用であることを確認(削除前検証)**

Run:
```bash
grep -rn "io.ktor.plugin\|libs.plugins.ktor\|libs.plugin.ktor" --include='*.gradle.kts' .
```
Expected: ヒットは以下の 2 箇所のみ(= 宣言だけで適用なし)。
- `build.gradle.kts`: `alias(libs.plugins.ktor) apply false`
- `build-logic/build.gradle.kts`: `implementation(libs.plugin.ktor)`

`id("io.ktor.plugin")` を **apply している箇所が無い**ことを確認する。

- [ ] **Step 2: ルート `build.gradle.kts` から alias を削除**

`build.gradle.kts` の `plugins { ... }` ブロックから次の行を削除する:
```kotlin
    alias(libs.plugins.ktor) apply false
```
削除後の `plugins` ブロックは以下になる:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
    alias(libs.plugins.exposed.migration) apply false
}
```

- [ ] **Step 3: `build-logic/build.gradle.kts` から依存を削除**

`dependencies { ... }` ブロックから次の行を削除する:
```kotlin
    implementation(libs.plugin.ktor)
```
削除後の `dependencies` ブロックは以下になる:
```kotlin
dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.kotlinx.rpc)
    implementation(libs.plugin.spotless)
}
```

- [ ] **Step 4: catalog から ktor plugin/library/version エントリを削除**

`gradle/libs.versions.toml` から次の 3 行を削除する:
- `[libraries]` 内: `plugin-ktor = { module = "io.ktor.plugin:plugin", version.ref = "ktor" }`
- `[plugins]` 内: `ktor = { id = "io.ktor.plugin", version.ref = "ktor" }`
- `[versions]` 内: `ktor = "3.5.0"`

`[versions]` の `ktor` を消す前に、他参照が無いことを再確認する:
```bash
grep -n 'version.ref = "ktor"\|version.ref="ktor"' gradle/libs.versions.toml
```
Expected: `plugin-ktor` と `ktor`(plugin)の 2 行のみがヒット。この 2 行を消せば `ktor` version は未参照になる。
`settings.gradle.kts` の `from("io.ktor:ktor-version-catalog:3.5.0")` はリテラルで、この version 変数を参照しない(影響なし)。

- [ ] **Step 5: catalog パースと build-logic コンパイルを検証**

Run:
```bash
./gradlew :domain:help --console=plain
```
Expected: BUILD SUCCESSFUL。settings 評価で catalog がパースされ(削除した `ktor` エントリへの未解決参照が無いこと)、
`:domain` 構成で build-logic がコンパイルされる(`implementation(libs.plugin.ktor)` 削除でコンパイルが壊れていないこと)。

- [ ] **Step 6: backend:api がビルドできることを検証**

Run:
```bash
./gradlew :backend:api:build --console=plain
```
Expected: BUILD SUCCESSFUL(Ktor plugin 撤去後も `application` plugin 経由で `:backend:api` が成立)。

- [ ] **Step 7: コミット**

```bash
git add -A
git commit -m "build: 未適用の Ktor Gradle plugin 宣言を撤去(ライブラリは維持)"
```

---

## Task 3: `:backend:api` の重複設定を除去

`backend/api/build.gradle.kts` は `ktor-server` convention(= `kotlin-jvm` + `application`)を適用済み。
`mainClass` のデフォルトと `useJUnitPlatform()` は convention 側で設定済みのため重複。

**Files:**
- Modify: `backend/api/build.gradle.kts`

- [ ] **Step 1: convention 側のデフォルトを確認(削除前検証)**

Run:
```bash
grep -n "mainClass\|useJUnitPlatform" build-logic/src/main/kotlin/net.brightroom.mindstock.ktor-server.gradle.kts build-logic/src/main/kotlin/net.brightroom.mindstock.kotlin-jvm.gradle.kts
```
Expected:
- `ktor-server` convention に `mainClass.set("net.brightroom.mindstock.MainKt")`
- `kotlin-jvm` convention に `tasks.withType<Test>().configureEach { useJUnitPlatform() }`

これにより `:backend:api` 側の同一値 `mainClass` と、`test`/`integrationTest` 内の `useJUnitPlatform()` が冗長と分かる。

- [ ] **Step 2: `application { mainClass.set(...) }` ブロックを削除**

`backend/api/build.gradle.kts` から次のブロックを削除する:
```kotlin
application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}
```
(`ktor-server` convention が同一値をデフォルト設定済み。`:backend:schedules` は `kotlin-jvm` 直適用で
convention に mainClass デフォルトが無いため、そちらの `application` ブロックは**消さない**ことに注意。本タスクは `:backend:api` のみ。)

- [ ] **Step 3: `tasks.test` 内の `useJUnitPlatform()` を削除**

`backend/api/build.gradle.kts` の `tasks.test { ... }` を次のように変更する。

変更前:
```kotlin
tasks.test {
    useJUnitPlatform()
    // Exclude "integration" and "manual" tagged specs by default.
    // Override on the command line with -Dkotest.tags.exclude= (empty string) to run all.
    systemProperty("kotest.tags.exclude", "integration | manual")
}
```
変更後:
```kotlin
tasks.test {
    // Exclude "integration" and "manual" tagged specs by default.
    // Override on the command line with -Dkotest.tags.exclude= (empty string) to run all.
    systemProperty("kotest.tags.exclude", "integration | manual")
}
```

- [ ] **Step 4: `integrationTest` 登録内の `useJUnitPlatform()` を削除**

同ファイルの `val integrationTest by tasks.registering(Test::class) { ... }` から `useJUnitPlatform()` の行のみを削除する。

変更前(冒頭部):
```kotlin
val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against TEST_DB_URL."
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
```
変更後(冒頭部):
```kotlin
val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against TEST_DB_URL."
    testClassesDirs = sourceSets["test"].output.classesDirs
```
(`group` / `description` / `testClassesDirs` / `classpath` / `shouldRunAfter` / `systemProperty` /
`TEST_DB_*` env 転送、ファイル末尾の `tasks.check { dependsOn(integrationTest) }` は**すべて残す**。)

- [ ] **Step 5: backend:api のテストタスクが JUnit Platform で解決・実行できることを検証**

Run:
```bash
./gradlew :backend:api:test --console=plain
```
Expected: BUILD SUCCESSFUL。convention 由来の `useJUnitPlatform()` が効いてテストが JUnit5 で実行される
(0 件でもタスク自体は成功)。`integrationTest` タスクの登録もパース時に解決される。

- [ ] **Step 6: コミット**

```bash
git add backend/api/build.gradle.kts
git commit -m "build(api): convention plugin と重複する mainClass / useJUnitPlatform を除去"
```

---

## Task 4: `useJUnitPlatform()` を `kmp-shared` convention に集約

`kmp-shared` は `jvm()` を持ち、`:domain` / `:rpc` / `:shared` が JVM テストで JUnit5 を要する。
現状 `:domain` のみが個別に宣言しているので convention へ集約して DRY 化する。

**Files:**
- Modify: `build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts`
- Modify: `domain/build.gradle.kts`

- [ ] **Step 1: 現状を確認(変更前検証)**

Run:
```bash
grep -n "useJUnitPlatform" build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts domain/build.gradle.kts rpc/build.gradle.kts shared/build.gradle.kts
```
Expected: `domain/build.gradle.kts` のみにヒット。`kmp-shared` convention・`rpc`・`shared` には無い。

- [ ] **Step 2: `kmp-shared` convention に `useJUnitPlatform()` を追加**

`build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts` の `kotlin { ... }` ブロックの
**閉じ括弧の後**に、次を追記する(ファイル末尾):
```kotlin

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```
(`kmp-shared` の冒頭 import `org.jetbrains.kotlin.gradle.ExperimentalWasmDsl` はそのまま。`Test` は
`org.gradle.api.tasks.testing.Test` で追加 import 不要。)

- [ ] **Step 3: `:domain` から重複ブロックを削除**

`domain/build.gradle.kts` 末尾の次のブロックを削除する:
```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```
削除後、`domain/build.gradle.kts` は `plugins { ... }` と `kotlin { ... }` ブロックで終わる。

- [ ] **Step 4: :domain テストが convention 由来の設定で動くことを検証**

Run:
```bash
./gradlew :domain:test --console=plain
```
Expected: BUILD SUCCESSFUL。`:domain` 構成で build-logic(変更した `kmp-shared`)がコンパイルされ、
JVM テストが convention 由来の `useJUnitPlatform()` で実行される(kotest runner が JUnit Platform 経由で起動)。

- [ ] **Step 5: :rpc / :shared のテストタスクも解決できることを検証**

Run:
```bash
./gradlew :rpc:build :shared:build --console=plain
```
Expected: BUILD SUCCESSFUL(両モジュールも `kmp-shared` 経由で `useJUnitPlatform()` を得る。テストが無くてもタスクは成功)。

- [ ] **Step 6: コミット**

```bash
git add build-logic/src/main/kotlin/net.brightroom.mindstock.kmp-shared.gradle.kts domain/build.gradle.kts
git commit -m "build: useJUnitPlatform を kmp-shared convention に集約し domain の重複を解消"
```

---

## Task 5: `:shared` の空 `dependencies {}` ブロックを削除

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: 空ブロックを削除**

`shared/build.gradle.kts` の `sourceSets { ... }` を次のように変更する。

変更前:
```kotlin
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {}

        jvmMain.dependencies {}
        jvmTest.dependencies {}

        wasmJsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        wasmJsTest.dependencies {}
    }
```
変更後:
```kotlin
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        wasmJsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
    }
```

- [ ] **Step 2: :shared がビルドできることを検証**

Run:
```bash
./gradlew :shared:build --console=plain
```
Expected: BUILD SUCCESSFUL(空ブロック削除で依存関係は変わらない)。

- [ ] **Step 3: コミット**

```bash
git add shared/build.gradle.kts
git commit -m "build(shared): 空の dependencies ブロックを削除"
```

---

## 最終検証(全タスク完了後)

- [ ] **Step 1: spotless 整形チェック**

Run:
```bash
./gradlew spotlessCheck --console=plain
```
Expected: BUILD SUCCESSFUL。失敗時は `./gradlew spotlessApply` で整形し、差分を該当タスクのコミットに含める(または追補コミット)。

- [ ] **Step 2: バックエンド中心のフルビルド(frontend フル build は OOM 回避で除外)**

Run:
```bash
./gradlew :domain:build :backend:core:build :backend:api:build :rpc:build :shared:build --console=plain
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: frontend のコンパイル確認(フル build は避ける)**

Run:
```bash
./gradlew :frontend:compileKotlinWasmJs --console=plain
```
Expected: BUILD SUCCESSFUL(js + wasmJs の二本立て・`webMain` 構成は変更しておらず、従来どおりコンパイルできる)。

- [ ] **Step 4: 統合テスト(任意・DB 必要)**

Run:
```bash
./gradlew integrationTest --max-workers=1 --console=plain
```
Expected: `TEST_DB_*` 環境変数が設定されていれば integration タグのテストが実行される。未設定環境ではスキップ可。
