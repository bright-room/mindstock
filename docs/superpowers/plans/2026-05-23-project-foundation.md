# Project Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up mindstock's Gradle 9.5 multi-project structure with `build-logic` composite build, version catalog, Spotless+ktlint, empty module placeholders, and CI. End state: `./gradlew build` and `./gradlew spotlessCheck` succeed.

**Architecture:** Gradle root project with composite-included `build-logic` module providing convention plugins (`mindstock.kotlin-jvm`, `mindstock.kmp-shared`, `mindstock.ktor-server`, `mindstock.compose-wasm`, `mindstock.spotless`). Module-level `build.gradle.kts` files stay tiny by applying conventions. Modules created as empty placeholders (with one no-op source file each so source sets are non-empty): `shared` (KMP Wasm+JVM), `domain`, `application`, `infrastructure` (JVM-only), `backend` (Ktor), `frontend` (CMP Wasm).

**Tech Stack:** Gradle 9.5, Kotlin 2.x, Compose Multiplatform (Wasm), Ktor 3, Spotless + ktlint, GitHub Actions.

---

## Important notes for implementers

- **Library versions in the version catalog should be verified against latest stable at implementation time.** kotlinx-rpc is experimental and updates frequently; verify the API surface matches the version selected. Same for Compose Multiplatform Wasm.
- **JDK requirement:** All JVM modules target JDK 21 (LTS). Ensure `JAVA_HOME` points to JDK 21+ before running any Gradle command in this plan.
- **`gradle` (not `./gradlew`) is needed only for Task 1** to bootstrap the wrapper. Install via SDKMAN: `sdk install gradle 9.5`. After Task 1, always use `./gradlew`.
- **Commit message format:** Follow the existing conventional-ish style (`Add ...`, `Configure ...`). Subject ≤ 60 chars. No body needed unless explaining "why".

---

## File Structure

After this plan:

```
mindstock/
├── .github/workflows/ci.yml
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew, gradlew.bat
├── gradle/
│   ├── wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
│   └── libs.versions.toml
├── build-logic/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── mindstock.kotlin-jvm.gradle.kts
│       ├── mindstock.kmp-shared.gradle.kts
│       ├── mindstock.ktor-server.gradle.kts
│       ├── mindstock.compose-wasm.gradle.kts
│       └── mindstock.spotless.gradle.kts
├── shared/{build.gradle.kts, src/commonMain/kotlin/Placeholder.kt}
├── domain/{build.gradle.kts, src/main/kotlin/Placeholder.kt}
├── application/{build.gradle.kts, src/main/kotlin/Placeholder.kt}
├── infrastructure/{build.gradle.kts, src/main/kotlin/Placeholder.kt}
├── backend/{build.gradle.kts, src/main/kotlin/Main.kt}
└── frontend/{build.gradle.kts, src/wasmJsMain/{kotlin/Main.kt, resources/index.html}}
```

---

## Task 1: Initialize Gradle wrapper and .gitignore

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` (generated)
- Create: `.gitignore`

- [ ] **Step 1: Verify JDK 21 and Gradle 9.5 are available**

Run:
```bash
java -version
gradle --version
```

Expected: Java 21+, Gradle 9.5.x. If missing, install via SDKMAN (`sdk install java 21.0.4-tem`, `sdk install gradle 9.5`).

- [ ] **Step 2: Bootstrap Gradle wrapper**

Run from repo root:
```bash
gradle wrapper --gradle-version=9.5 --distribution-type=bin
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. Verify:
```bash
./gradlew --version
```
should print `Gradle 9.5`.

- [ ] **Step 3: Write .gitignore**

Create `.gitignore` with:

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/**/build/
!**/src/**/.gradle/

# IDE
.idea/
*.iml
*.ipr
*.iws
.vscode/
.fleet/

# Kotlin / KMP
kotlin-js-store/
.kotlin/

# OS
.DS_Store
Thumbs.db

# Local config
local.properties
*.env
*.env.local
```

- [ ] **Step 4: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper/ .gitignore
git commit -m "Bootstrap Gradle 9.5 wrapper and .gitignore"
```

---

## Task 2: Create version catalog

**Files:**
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Write the version catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.2.0"
kotlinx-coroutines = "1.9.0"
kotlinx-serialization = "1.7.3"
kotlinx-datetime = "0.6.1"
kotlinx-rpc = "0.4.0"
compose-multiplatform = "1.7.3"
ktor = "3.0.3"
exposed = "1.0.0-beta-4"
hikari = "6.2.1"
flyway = "11.1.0"
postgres-jdbc = "42.7.4"
koin = "4.0.0"
opentelemetry = "1.45.0"
opentelemetry-instrumentation = "2.10.0"
micrometer-otlp = "1.14.2"
logback = "1.5.12"
kotlin-logging = "7.0.3"
kotest = "5.9.1"
mockk = "1.13.13"
testcontainers = "1.20.4"
spotless = "7.0.0"
ktlint = "1.5.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-jdk8 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

kotlinx-rpc-core = { module = "org.jetbrains.kotlinx:kotlinx-rpc-core", version.ref = "kotlinx-rpc" }
kotlinx-rpc-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-rpc-serialization-json", version.ref = "kotlinx-rpc" }
kotlinx-rpc-server = { module = "org.jetbrains.kotlinx:kotlinx-rpc-krpc-server", version.ref = "kotlinx-rpc" }
kotlinx-rpc-server-ktor = { module = "org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-server", version.ref = "kotlinx-rpc" }
kotlinx-rpc-client = { module = "org.jetbrains.kotlinx:kotlinx-rpc-krpc-client", version.ref = "kotlinx-rpc" }
kotlinx-rpc-client-ktor = { module = "org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client", version.ref = "kotlinx-rpc" }

ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-websockets = { module = "io.ktor:ktor-server-websockets", version.ref = "ktor" }
ktor-server-auth = { module = "io.ktor:ktor-server-auth", version.ref = "ktor" }
ktor-server-auth-jwt = { module = "io.ktor:ktor-server-auth-jwt", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }

exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-kotlin-datetime = { module = "org.jetbrains.exposed:exposed-kotlin-datetime", version.ref = "exposed" }
exposed-migration = { module = "org.jetbrains.exposed:exposed-migration", version.ref = "exposed" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
postgres-jdbc = { module = "org.postgresql:postgresql", version.ref = "postgres-jdbc" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-database-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-ktor = { module = "io.insert-koin:koin-ktor", version.ref = "koin" }

opentelemetry-api = { module = "io.opentelemetry:opentelemetry-api", version.ref = "opentelemetry" }
opentelemetry-sdk = { module = "io.opentelemetry:opentelemetry-sdk", version.ref = "opentelemetry" }
opentelemetry-exporter-otlp = { module = "io.opentelemetry:opentelemetry-exporter-otlp", version.ref = "opentelemetry" }
opentelemetry-instrumentation-ktor = { module = "io.opentelemetry.instrumentation:opentelemetry-ktor-3.0", version.ref = "opentelemetry-instrumentation" }
opentelemetry-instrumentation-logback = { module = "io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0", version.ref = "opentelemetry-instrumentation" }
micrometer-registry-otlp = { module = "io.micrometer:micrometer-registry-otlp", version.ref = "micrometer-otlp" }

logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
kotlin-logging-jvm = { module = "io.github.oshai:kotlin-logging-jvm", version.ref = "kotlin-logging" }

kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
testcontainers-postgres = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }

# Plugin coordinates (for build-logic to depend on)
plugin-kotlin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
plugin-kotlin-serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
plugin-compose-compiler = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
plugin-compose-multiplatform = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "compose-multiplatform" }
plugin-ktor = { module = "io.ktor.plugin:plugin", version.ref = "ktor" }
plugin-kotlinx-rpc = { module = "org.jetbrains.kotlinx:kotlinx-rpc-gradle-plugin", version.ref = "kotlinx-rpc" }
plugin-spotless = { module = "com.diffplug.spotless:spotless-plugin-gradle", version.ref = "spotless" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ktor = { id = "io.ktor.plugin", version.ref = "ktor" }
kotlinx-rpc = { id = "org.jetbrains.kotlinx.rpc.plugin", version.ref = "kotlinx-rpc" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

- [ ] **Step 2: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "Add version catalog with all MVP dependencies"
```

---

## Task 3: Create root settings.gradle.kts

**Files:**
- Create: `settings.gradle.kts`

- [ ] **Step 1: Write settings.gradle.kts**

Create `settings.gradle.kts`:

```kotlin
rootProject.name = "mindstock"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-rpc/maven")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-rpc/maven")
    }
}

// composite build for convention plugins
includeBuild("build-logic")

include(
    ":shared",
    ":domain",
    ":application",
    ":infrastructure",
    ":backend",
    ":frontend",
)
```

- [ ] **Step 2: Commit**

```bash
git add settings.gradle.kts
git commit -m "Configure root settings with module includes and repositories"
```

---

## Task 4: Create root build.gradle.kts

**Files:**
- Create: `build.gradle.kts`

- [ ] **Step 1: Write the root build script**

Create `build.gradle.kts`:

```kotlin
// Aggregation only. All module config goes through build-logic conventions.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
```

- [ ] **Step 2: Verify Gradle can configure**

Run:
```bash
./gradlew help
```

Expected: SUCCESS (no modules defined yet, but configuration must succeed).

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "Add root build script (aggregation only)"
```

---

## Task 5: Create build-logic composite build

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`

- [ ] **Step 1: Create build-logic/settings.gradle.kts**

```kotlin
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

- [ ] **Step 2: Create build-logic/build.gradle.kts**

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.ktor)
    implementation(libs.plugin.kotlinx.rpc)
    implementation(libs.plugin.spotless)
}
```

- [ ] **Step 3: Create build-logic source directory placeholder**

```bash
mkdir -p build-logic/src/main/kotlin
```

- [ ] **Step 4: Verify build-logic compiles (empty, but should configure)**

Run:
```bash
./gradlew :build-logic:help
```

Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add build-logic/settings.gradle.kts build-logic/build.gradle.kts build-logic/src/
git commit -m "Add build-logic composite build for convention plugins"
```

---

## Task 6: Create Spotless convention plugin

**Files:**
- Create: `build-logic/src/main/kotlin/mindstock.spotless.gradle.kts`

- [ ] **Step 1: Write the Spotless convention plugin**

Create `build-logic/src/main/kotlin/mindstock.spotless.gradle.kts`:

```kotlin
import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless")
}

extensions.configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add build-logic/src/main/kotlin/mindstock.spotless.gradle.kts
git commit -m "Add Spotless+ktlint convention plugin"
```

---

## Task 7: Create kotlin-jvm convention plugin

**Files:**
- Create: `build-logic/src/main/kotlin/mindstock.kotlin-jvm.gradle.kts`

- [ ] **Step 1: Write the convention plugin**

Create `build-logic/src/main/kotlin/mindstock.kotlin-jvm.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Commit**

```bash
git add build-logic/src/main/kotlin/mindstock.kotlin-jvm.gradle.kts
git commit -m "Add kotlin-jvm convention plugin"
```

---

## Task 8: Create kmp-shared convention plugin

**Files:**
- Create: `build-logic/src/main/kotlin/mindstock.kmp-shared.gradle.kts`

- [ ] **Step 1: Write the convention plugin**

Create `build-logic/src/main/kotlin/mindstock.kmp-shared.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add build-logic/src/main/kotlin/mindstock.kmp-shared.gradle.kts
git commit -m "Add kmp-shared convention plugin (Wasm + JVM)"
```

---

## Task 9: Create ktor-server convention plugin

**Files:**
- Create: `build-logic/src/main/kotlin/mindstock.ktor-server.gradle.kts`

- [ ] **Step 1: Write the convention plugin**

Create `build-logic/src/main/kotlin/mindstock.ktor-server.gradle.kts`:

```kotlin
plugins {
    id("mindstock.kotlin-jvm")
    application
}

application {
    // Override in module if needed
    mainClass.set("mindstock.backend.MainKt")
}
```

- [ ] **Step 2: Commit**

```bash
git add build-logic/src/main/kotlin/mindstock.ktor-server.gradle.kts
git commit -m "Add ktor-server convention plugin"
```

---

## Task 10: Create compose-wasm convention plugin

**Files:**
- Create: `build-logic/src/main/kotlin/mindstock.compose-wasm.gradle.kts`

- [ ] **Step 1: Write the convention plugin**

Create `build-logic/src/main/kotlin/mindstock.compose-wasm.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(21)

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "mindstock-frontend"
        browser {
            commonWebpackConfig {
                outputFileName = "mindstock-frontend.js"
            }
        }
        binaries.executable()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add build-logic/src/main/kotlin/mindstock.compose-wasm.gradle.kts
git commit -m "Add compose-wasm convention plugin"
```

---

## Task 11: Create domain module (JVM-only)

**Files:**
- Create: `domain/build.gradle.kts`
- Create: `domain/src/main/kotlin/mindstock/domain/Placeholder.kt`

- [ ] **Step 1: Write module build script**

Create `domain/build.gradle.kts`:

```kotlin
plugins {
    id("mindstock.kotlin-jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}
```

- [ ] **Step 2: Add placeholder source file**

Create `domain/src/main/kotlin/mindstock/domain/Placeholder.kt`:

```kotlin
package mindstock.domain

internal const val PLACEHOLDER = "domain"
```

- [ ] **Step 3: Verify the module builds**

Run:
```bash
./gradlew :domain:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add domain/
git commit -m "Add empty domain module"
```

---

## Task 12: Create application module (JVM-only)

**Files:**
- Create: `application/build.gradle.kts`
- Create: `application/src/main/kotlin/mindstock/application/Placeholder.kt`

- [ ] **Step 1: Write module build script**

Create `application/build.gradle.kts`:

```kotlin
plugins {
    id("mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
```

- [ ] **Step 2: Add placeholder source file**

Create `application/src/main/kotlin/mindstock/application/Placeholder.kt`:

```kotlin
package mindstock.application

internal const val PLACEHOLDER = "application"
```

- [ ] **Step 3: Verify the module builds**

Run:
```bash
./gradlew :application:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add application/
git commit -m "Add empty application module"
```

---

## Task 13: Create infrastructure module (JVM-only)

**Files:**
- Create: `infrastructure/build.gradle.kts`
- Create: `infrastructure/src/main/kotlin/mindstock/infrastructure/Placeholder.kt`

- [ ] **Step 1: Write module build script**

Create `infrastructure/build.gradle.kts`:

```kotlin
plugins {
    id("mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.application)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    implementation(libs.hikari)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
```

- [ ] **Step 2: Add placeholder source file**

Create `infrastructure/src/main/kotlin/mindstock/infrastructure/Placeholder.kt`:

```kotlin
package mindstock.infrastructure

internal const val PLACEHOLDER = "infrastructure"
```

- [ ] **Step 3: Verify the module builds**

Run:
```bash
./gradlew :infrastructure:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/
git commit -m "Add empty infrastructure module"
```

---

## Task 14: Create shared module (KMP Wasm + JVM)

**Files:**
- Create: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/mindstock/shared/Placeholder.kt`

- [ ] **Step 1: Write module build script**

Create `shared/build.gradle.kts`:

```kotlin
plugins {
    id("mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.rpc.core)
                implementation(libs.kotlinx.rpc.serialization.json)
            }
        }
    }
}
```

- [ ] **Step 2: Add placeholder source file**

Create `shared/src/commonMain/kotlin/mindstock/shared/Placeholder.kt`:

```kotlin
package mindstock.shared

internal const val PLACEHOLDER = "shared"
```

- [ ] **Step 3: Verify the module builds**

Run:
```bash
./gradlew :shared:build
```

Expected: BUILD SUCCESSFUL. Both `wasmJsMain` and `jvmMain` source sets should compile.

- [ ] **Step 4: Commit**

```bash
git add shared/
git commit -m "Add empty shared KMP module (Wasm + JVM)"
```

---

## Task 15: Create backend module (Ktor)

**Files:**
- Create: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/mindstock/backend/Main.kt`
- Create: `backend/src/main/resources/logback.xml`
- Create: `backend/src/main/resources/application.conf`

- [ ] **Step 1: Write module build script**

Create `backend/build.gradle.kts`:

```kotlin
plugins {
    id("mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass.set("mindstock.backend.MainKt")
}

dependencies {
    implementation(projects.shared)
    implementation(projects.domain)
    implementation(projects.application)
    implementation(projects.infrastructure)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.server.test.host)
}
```

- [ ] **Step 2: Write minimal Ktor Main.kt**

Create `backend/src/main/kotlin/mindstock/backend/Main.kt`:

```kotlin
package mindstock.backend

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
```

- [ ] **Step 3: Add minimal logback config**

Create `backend/src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 4: Add empty application.conf placeholder**

Create `backend/src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
}
```

- [ ] **Step 5: Verify the module builds**

Run:
```bash
./gradlew :backend:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/
git commit -m "Add empty backend module with health endpoint"
```

---

## Task 16: Create frontend module (CMP Wasm)

**Files:**
- Create: `frontend/build.gradle.kts`
- Create: `frontend/src/wasmJsMain/kotlin/mindstock/frontend/Main.kt`
- Create: `frontend/src/wasmJsMain/resources/index.html`

- [ ] **Step 1: Write module build script**

Create `frontend/build.gradle.kts`:

```kotlin
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    id("mindstock.compose-wasm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(projects.shared)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.rpc.client)
                implementation(libs.kotlinx.rpc.client.ktor)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
            }
        }
    }
}
```

- [ ] **Step 2: Write minimal Compose entry point**

Create `frontend/src/wasmJsMain/kotlin/mindstock/frontend/Main.kt`:

```kotlin
package mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        Text("mindstock")
    }
}
```

- [ ] **Step 3: Add index.html**

Create `frontend/src/wasmJsMain/resources/index.html`:

```html
<!doctype html>
<html lang="en">
    <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width,initial-scale=1" />
        <title>mindstock</title>
        <script type="application/javascript" src="mindstock-frontend.js"></script>
    </head>
    <body></body>
</html>
```

- [ ] **Step 4: Verify the module builds**

Run:
```bash
./gradlew :frontend:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add frontend/
git commit -m "Add empty frontend module with placeholder Compose app"
```

---

## Task 17: Full build verification

- [ ] **Step 1: Run full build from scratch**

Run:
```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL with all 6 modules compiling.

- [ ] **Step 2: Run Spotless check**

Run:
```bash
./gradlew spotlessCheck
```

Expected: BUILD SUCCESSFUL (no formatting issues). If failures, run `./gradlew spotlessApply` to fix, then re-run check.

- [ ] **Step 3: Commit any Spotless fixes (if applied)**

If `spotlessApply` made changes:
```bash
git add -u
git commit -m "Apply Spotless formatting"
```

---

## Task 18: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "9.5"

      - name: Spotless check
        run: ./gradlew spotlessCheck

      - name: Build
        run: ./gradlew build

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: build-reports
          path: |
            **/build/reports/
            **/build/test-results/
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "Add CI workflow (Spotless + build)"
```

---

## Task 19: Final smoke test

- [ ] **Step 1: Verify the backend runs**

Run:
```bash
./gradlew :backend:run
```

In a separate terminal:
```bash
curl http://localhost:8080/health
```

Expected: `OK` returned. Stop the server (Ctrl+C).

- [ ] **Step 2: Verify the frontend builds for distribution**

Run:
```bash
./gradlew :frontend:wasmJsBrowserDistribution
```

Expected: BUILD SUCCESSFUL. Output in `frontend/build/dist/wasmJs/productionExecutable/`.

- [ ] **Step 3: Final commit (if anything changed)**

If any generated config (.gradle/, .idea/, etc.) leaked into tracked files, clean up. Otherwise no commit needed.

```bash
git status
```

Expected: clean working tree.

---

## Done state

After this plan:

- ✅ `./gradlew build` succeeds
- ✅ `./gradlew spotlessCheck` succeeds
- ✅ `./gradlew :backend:run` starts a Ktor server with `/health` endpoint
- ✅ `./gradlew :frontend:wasmJsBrowserDistribution` produces a static PWA bundle
- ✅ CI runs on push/PR and verifies Spotless + build
- ✅ All 6 modules (shared, domain, application, infrastructure, backend, frontend) compile and are wired through convention plugins
- ✅ Version catalog is the single source of dependency versions

Next plan: **Plan 2 — DB Schema + Migrations** (PostgreSQL via Docker Compose, Exposed Tables for all domains, `@Migratable` annotation, migration generator/applier, Testcontainers integration test).
