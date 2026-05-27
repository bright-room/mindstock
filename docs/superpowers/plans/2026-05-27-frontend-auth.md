# Frontend Auth (Zitadel OIDC PKCE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose Multiplatform (Wasm) の frontend に Zitadel OIDC PKCE ログイン / token 保管 / kotlinx-rpc 接続への注入 / 未登録ユーザーの register / ログアウトまでを実装する。

**Architecture:** Kotlin/Wasm でハンドロールする PKCE Authorization Code Flow。OIDC エンドポイントは ktor-client (js engine) で叩き、token は `sessionStorage` に保持。kotlinx-rpc client は `Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<base64url(jwt)>` で token を埋めて WS 接続。401 を 1 度だけ refresh + retry する薄いラッパを用意。OIDC 設定は env → Gradle task → 生成 Kotlin object で注入。

**Tech Stack:** Kotlin 2.3.21 (wasmJs + jsIR), Compose Multiplatform 1.11.0, ktor 3.5.0 (client-core / client-js / client-mock for tests), kotlinx-rpc 0.10.2 (krpc-ktor-client), kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, Web Crypto API (JS interop), sessionStorage.

参照仕様: `docs/superpowers/specs/2026-05-27-frontend-auth-design.md`

---

## File Structure

新規ファイル(全て `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/` 以下):

```
auth/
  AuthConfig.kt           [生成] issuer/clientId/redirectUri/postLogoutRedirectUri/audience/projectId
  Pkce.kt                 [新] code_verifier / code_challenge (Web Crypto)
  Tokens.kt               [新] data class Tokens + willExpireWithin
  TokenStore.kt           [新] sessionStorage I/O
  AuthClient.kt           [新] authorize URL / token / refresh / end_session
  AuthCallbackHandler.kt  [新] callback URL の解釈 + token 交換
  AuthState.kt            [新] sealed state
  AuthBootstrap.kt        [新] 起動時の state 決定 + ping
  OidcException.kt        [新] error / error_description / reauth フラグ
rpc/
  RpcClientFactory.kt     [新] access_token を Sec-WebSocket-Protocol に乗せる
  RpcCallWrapper.kt       [新] 401 → refresh → retry
ui/
  App.kt                  [改] AuthState で分岐
  login/LoginScreen.kt    [新]
  register/RegisterDialog.kt [新]
  callback/AuthCallbackScreen.kt [新]
  shell/AppShell.kt       [新]

frontend/build.gradle.kts [改] ktor-client / generateAuthConfig task
frontend/src/commonTest/kotlin/.../auth/*Test.kt [新]
frontend/src/commonTest/kotlin/.../rpc/*Test.kt [新]
frontend/src/webMain/kotlin/.../auth/SessionStorage.kt [新] expect/actual の actual 側
frontend/src/commonMain/kotlin/.../auth/SessionStorage.kt [新] expect 宣言
README.md                 [改]
```

`webMain` (= js + wasmJs 共通) を `sessionStorage` の actual 実装に使う。

---

## Notes on conventions verified from the codebase

- 既存テストで kotlinx-rpc クライアントは `httpClient.rpc("/api/v1/$path") { headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1, mindstock.bearer.$b64") }` の形(`backend/.../e2e/E2eTestSupport.kt:152-161`)
- backend のルートは `/api/v1/user/public` (user-public) と `/api/v1/{user,household,catalog,product,stock}` (user)
- ping 用に「副作用なし・user レルム」の `HouseholdRpcService.findOf(): Household?` を採用(401 ⇒ 未登録、null ⇒ 世帯未作成だが User 登録済み、Household ⇒ Ready)
- `UserPublicRpcService.register(displayName: DisplayName): User` で登録
- `KrpcJson` は `shared/extensions` に定義済み(`net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson`)。frontend からも使う

---

## Task 1: Frontend dependencies & generateAuthConfig task

**Files:**
- Modify: `frontend/build.gradle.kts`
- Modify: `gradle/libs.versions.toml` (ktor-client-mock テスト依存追加が必要なら)

- [ ] **Step 1: Add ktor-client artifacts to commonMain and ktor-client-mock to commonTest**

Edit `frontend/build.gradle.kts`:

```kotlin
plugins {
    id("net.brightroom.mindstock.compose-web")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.rpc)
            implementation(projects.shared.extensions)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.compose.adaptive)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.material3.adaptive.navigation.suite)

            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.rpc.client)
            implementation(libs.kotlinx.rpc.client.ktor)
            implementation(libs.kotlinx.rpc.serialization.json)
            implementation(ktorLib.client.core)
            implementation(ktorLib.client.content.negotiation)
            implementation(ktorLib.serialization.kotlinx.json)

            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(ktorLib.client.mock)
            implementation(libs.kotlinx.coroutines.core) // for runTest
        }
    }
}
```

> 注: `ktorLib.client.content.negotiation` / `ktorLib.serialization.kotlinx.json` / `ktorLib.client.mock` は `io.ktor:ktor-version-catalog:3.5.0` に含まれる。シンボル名は `ktor-client-content-negotiation` → `client.content.negotiation` の慣行に従う。kotlinx-coroutines-test が必要な場合は `gradle/libs.versions.toml` に追加。

- [ ] **Step 2: Add generateAuthConfig task**

Append to `frontend/build.gradle.kts`:

```kotlin
val generateAuthConfig = tasks.register("generateAuthConfig") {
    val outDir = layout.buildDirectory.dir("generated/auth")
    outputs.dir(outDir)
    val issuer = providers.environmentVariable("AUTH_ISSUER").orElse("http://localhost:8081")
    val redirectUri = providers.environmentVariable("AUTH_REDIRECT_URI").orElse("http://localhost:8080/auth/callback")
    val postLogout = providers.environmentVariable("AUTH_POST_LOGOUT_REDIRECT_URI").orElse("http://localhost:8080/")
    val clientId = providers.environmentVariable("AUTH_CLIENT_ID")
    val audience = providers.environmentVariable("AUTH_AUDIENCE")
    val projectId = providers.environmentVariable("AUTH_PROJECT_ID")
    doLast {
        val out = outDir.get().asFile.resolve("net/brightroom/mindstock/frontend/auth/AuthConfig.kt")
        out.parentFile.mkdirs()
        out.writeText(
            """
            package net.brightroom.mindstock.frontend.auth

            object AuthConfig {
                const val ISSUER = "${issuer.get()}"
                const val CLIENT_ID = "${clientId.get()}"
                const val REDIRECT_URI = "${redirectUri.get()}"
                const val POST_LOGOUT_REDIRECT_URI = "${postLogout.get()}"
                const val AUDIENCE = "${audience.get()}"
                const val PROJECT_ID = "${projectId.get()}"
            }
            """.trimIndent() + "\n",
        )
    }
}
kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateAuthConfig)
}
```

注意点:
- `clientId` / `audience` / `projectId` は `.orElse(...)` を付けない → `.get()` でビルド失敗(`Cannot query the value of this provider`)。これが「必須 env なら失敗する」挙動。
- 文字列補間する値はあらかじめ Zitadel 側のルールで `"` を含まない前提なのでエスケープは不要(README で注意喚起)。

- [ ] **Step 3: Verify the task fails without env**

Run: `./gradlew :frontend:generateAuthConfig`
Expected: FAIL with `Cannot query the value of this provider because it has no value available.`

- [ ] **Step 4: Verify the task succeeds with env**

Run: `AUTH_CLIENT_ID=test-client AUTH_AUDIENCE=test-aud AUTH_PROJECT_ID=test-proj ./gradlew :frontend:generateAuthConfig`
Expected: PASS, `frontend/build/generated/auth/net/brightroom/mindstock/frontend/auth/AuthConfig.kt` が生成される。

- [ ] **Step 5: Commit**

```bash
git add frontend/build.gradle.kts
git commit -m "build(frontend): add ktor-client deps + AuthConfig generator"
```

---

## Task 2: Pkce code_verifier / code_challenge

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt`
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/auth/PkceWeb.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/PkceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/PkceTest.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun verifier_is_43_to_128_chars_in_unreserved_set() {
        val v = Pkce.newVerifier()
        assertTrue(v.length in 43..128, "length=${v.length}")
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertTrue(v.all { it in allowed }, "unexpected char in $v")
    }

    @Test
    fun challenge_is_base64url_sha256_of_verifier() = runTest {
        val v = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk" // RFC 7636 Appendix B
        val c = Pkce.challenge(v)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", c)
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error / missing symbol)**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*PkceTest*"`
Expected: FAIL — `Unresolved reference: Pkce`

- [ ] **Step 3: Implement Pkce (common + web actual)**

Create `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlin.random.Random

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun newVerifier(length: Int = 64): String {
        require(length in 43..128) { "verifier length must be 43..128, was $length" }
        val sb = StringBuilder(length)
        repeat(length) { sb.append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }

    suspend fun challenge(verifier: String): String =
        base64UrlNoPad(sha256(verifier.encodeToByteArray()))
}

internal expect suspend fun sha256(bytes: ByteArray): ByteArray
internal expect fun base64UrlNoPad(bytes: ByteArray): String
```

Create `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/auth/PkceWeb.kt`:

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.Window
import kotlin.js.Promise

internal actual suspend fun sha256(bytes: ByteArray): ByteArray {
    val input = Int8Array(bytes.toTypedArray())
    val digest = subtleDigest("SHA-256", input.buffer).await()
    val view = Uint8Array(digest)
    return ByteArray(view.length) { view[it].toByte() }
}

internal actual fun base64UrlNoPad(bytes: ByteArray): String {
    // btoa は ASCII 限定。バイト列を 1 文字 = 1 バイトの文字列にしてから btoa。
    val sb = StringBuilder(bytes.size)
    for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
    val b64 = jsBtoa(sb.toString())
    return b64.replace('+', '-').replace('/', '_').trimEnd('=')
}

private external interface SubtleCrypto {
    fun digest(algorithm: String, data: ArrayBuffer): Promise<ArrayBuffer>
}
private external interface Crypto { val subtle: SubtleCrypto }
private external val crypto: Crypto

private fun subtleDigest(algo: String, data: ArrayBuffer): Promise<ArrayBuffer> = crypto.subtle.digest(algo, data)
private fun jsBtoa(s: String): String = js("globalThis.btoa(s)")
```

> wasmJs と jsIR で `org.khronos.webgl.*` の API はほぼ同じ。`external` 宣言は両ターゲットでビルドできる形にする。`js("...")` は wasmJs でも `@JsFun` 不要で許容される。具体的な FFI 関数名・ファイル分割は実装時に wasmJs が要求する形に微調整(jsMain と wasmJsMain で別 actual に分ける必要が出れば、`webMain` ではなく 2 つに分割する)。

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*PkceTest*"`
Expected: PASS。`challenge` が RFC 7636 Appendix B の参照値と一致すること。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Pkce.kt \
        frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/auth/PkceWeb.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/PkceTest.kt
git commit -m "feat(frontend/auth): add Pkce verifier/challenge with Web Crypto"
```

---

## Task 3: Tokens data class

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Tokens.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokensTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokensTest {
    @Test
    fun willExpireWithin_true_when_expiry_is_inside_window() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens("a", "r", "i", expiresAt = Instant.fromEpochSeconds(1_000_030))
        assertTrue(t.willExpireWithin(60, now))
    }

    @Test
    fun willExpireWithin_false_when_expiry_is_outside_window() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens("a", "r", "i", expiresAt = Instant.fromEpochSeconds(1_000_120))
        assertFalse(t.willExpireWithin(60, now))
    }

    @Test
    fun fromTokenResponse_computes_expiresAt() {
        val now = Instant.fromEpochSeconds(1_000_000)
        val t = Tokens.fromTokenResponse(
            accessToken = "a", refreshToken = "r", idToken = "i", expiresInSeconds = 3600, now = now,
        )
        assertEquals(Instant.fromEpochSeconds(1_003_600), t.expiresAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*TokensTest*"`
Expected: FAIL — `Unresolved reference: Tokens`

- [ ] **Step 3: Implement Tokens**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAt: Instant,
) {
    fun willExpireWithin(seconds: Int, now: Instant = Clock.System.now()): Boolean =
        expiresAt <= now.plusSeconds(seconds)

    private fun Instant.plusSeconds(s: Int): Instant = Instant.fromEpochSeconds(epochSeconds + s)

    companion object {
        fun fromTokenResponse(
            accessToken: String,
            refreshToken: String,
            idToken: String,
            expiresInSeconds: Long,
            now: Instant = Clock.System.now(),
        ): Tokens = Tokens(accessToken, refreshToken, idToken, Instant.fromEpochSeconds(now.epochSeconds + expiresInSeconds))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*TokensTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/Tokens.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokensTest.kt
git commit -m "feat(frontend/auth): add Tokens data class with expiry helpers"
```

---

## Task 4: SessionStorage expect/actual

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorage.kt`
- Create: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorageWeb.kt`

- [ ] **Step 1: Write expect declarations (no test for the bare wrapper — it's exercised in Task 5)**

```kotlin
package net.brightroom.mindstock.frontend.auth

internal expect object SessionStorage {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}
```

- [ ] **Step 2: Implement actual for webMain**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.browser.window

internal actual object SessionStorage {
    actual fun get(key: String): String? = window.sessionStorage.getItem(key)
    actual fun set(key: String, value: String) { window.sessionStorage.setItem(key, value) }
    actual fun remove(key: String) { window.sessionStorage.removeItem(key) }
}
```

> `kotlinx.browser.window` は Compose Multiplatform Web ターゲットで利用可能。wasmJs / jsIR どちらでもインポートできる。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorage.kt \
        frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/auth/SessionStorageWeb.kt
git commit -m "feat(frontend/auth): add SessionStorage expect/actual"
```

---

## Task 5: TokenStore (sessionStorage)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokenStoreTest.kt`

- [ ] **Step 1: Write the failing test (uses real sessionStorage on browser test runner)**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {
    @AfterTest fun cleanup() { TokenStore.clear() }

    @Test
    fun save_and_load_roundtrip() {
        val t = Tokens("a", "r", "i", Instant.fromEpochSeconds(2_000_000))
        TokenStore.save(t)
        assertEquals(t, TokenStore.load())
    }

    @Test
    fun load_returns_null_when_empty() {
        TokenStore.clear()
        assertNull(TokenStore.load())
    }

    @Test
    fun clear_removes_value() {
        TokenStore.save(Tokens("a", "r", "i", Instant.fromEpochSeconds(2_000_000)))
        TokenStore.clear()
        assertNull(TokenStore.load())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*TokenStoreTest*"`
Expected: FAIL — `Unresolved reference: TokenStore`

- [ ] **Step 3: Implement TokenStore**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.serialization.json.Json

internal object TokenStore {
    private const val KEY = "mindstock.tokens.v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(tokens: Tokens) { SessionStorage.set(KEY, json.encodeToString(Tokens.serializer(), tokens)) }
    fun load(): Tokens? = SessionStorage.get(KEY)?.let { runCatching { json.decodeFromString(Tokens.serializer(), it) }.getOrNull() }
    fun clear() { SessionStorage.remove(KEY) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*TokenStoreTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/TokenStoreTest.kt
git commit -m "feat(frontend/auth): add TokenStore on sessionStorage"
```

---

## Task 6: OidcException

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/OidcException.kt`

- [ ] **Step 1: Create the exception class (no test — exercised in Task 7)**

```kotlin
package net.brightroom.mindstock.frontend.auth

class OidcException(
    val errorCode: String,
    val errorDescription: String?,
    /** true = refresh_token が失効した等、ユーザーに再ログインを求めるべきケース */
    val reauthRequired: Boolean = false,
) : RuntimeException("$errorCode: ${errorDescription ?: "(no description)"}")
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/OidcException.kt
git commit -m "feat(frontend/auth): add OidcException"
```

---

## Task 7: AuthClient (authorize URL / token / refresh / end_session)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthClientTest.kt`

- [ ] **Step 1: Write the failing test using ktor MockEngine**

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthClientTest {
    private fun client(handler: (io.ktor.client.engine.mock.MockRequestHandleScope, io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient =
        HttpClient(MockEngine { req -> handler(this, req) }) {
            install(ContentNegotiation) { json() }
        }

    @Test
    fun buildAuthorizeUrl_includes_pkce_state_scope() {
        val url = AuthClient.buildAuthorizeUrl(
            issuer = "https://idp.example",
            clientId = "c1",
            redirectUri = "https://app.example/auth/callback",
            scope = "openid profile",
            state = "state-xyz",
            codeChallenge = "chal-abc",
        )
        assertTrue(url.startsWith("https://idp.example/oauth/v2/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=c1"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.example%2Fauth%2Fcallback"))
        assertTrue(url.contains("scope=openid+profile") || url.contains("scope=openid%20profile"))
        assertTrue(url.contains("state=state-xyz"))
        assertTrue(url.contains("code_challenge=chal-abc"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun exchangeCode_posts_form_and_parses_token_response() = runTest {
        val http = client { _, req ->
            assertEquals("https://idp.example/oauth/v2/token", req.url.toString())
            val body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("code=THE_CODE"))
            assertTrue(body.contains("client_id=c1"))
            assertTrue(body.contains("code_verifier=THE_VERIFIER"))
            respond(
                """{"access_token":"AT","refresh_token":"RT","id_token":"IT","expires_in":3600,"token_type":"Bearer"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "https://app.example/auth/callback")
        val tokens = ac.exchangeCode(code = "THE_CODE", codeVerifier = "THE_VERIFIER", now = Instant.fromEpochSeconds(100))
        assertEquals("AT", tokens.accessToken)
        assertEquals("RT", tokens.refreshToken)
        assertEquals("IT", tokens.idToken)
        assertEquals(Instant.fromEpochSeconds(3700), tokens.expiresAt)
    }

    @Test
    fun refresh_uses_refresh_token_grant() = runTest {
        val http = client { _, req ->
            val body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("grant_type=refresh_token"))
            assertTrue(body.contains("refresh_token=OLD_RT"))
            respond(
                """{"access_token":"AT2","refresh_token":"NEW_RT","id_token":"IT2","expires_in":3600,"token_type":"Bearer"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "x")
        val tokens = ac.refresh("OLD_RT", now = Instant.fromEpochSeconds(100))
        assertEquals("AT2", tokens.accessToken)
        assertEquals("NEW_RT", tokens.refreshToken)
    }

    @Test
    fun refresh_invalid_grant_throws_reauth_required() = runTest {
        val http = client { _, _ ->
            respond(
                """{"error":"invalid_grant","error_description":"refresh token expired"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ac = AuthClient(http, issuer = "https://idp.example", clientId = "c1", redirectUri = "x")
        val ex = assertFailsWith<OidcException> { ac.refresh("OLD_RT") }
        assertEquals("invalid_grant", ex.errorCode)
        assertTrue(ex.reauthRequired)
    }

    @Test
    fun endSessionUrl_includes_id_token_hint_and_post_logout() {
        val url = AuthClient.endSessionUrl(
            issuer = "https://idp.example",
            idToken = "IT",
            postLogoutRedirectUri = "https://app.example/",
        )
        assertTrue(url.startsWith("https://idp.example/oidc/v1/end_session?"))
        assertTrue(url.contains("id_token_hint=IT"))
        assertTrue(url.contains("post_logout_redirect_uri=https%3A%2F%2Fapp.example%2F"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthClientTest*"`
Expected: FAIL — Unresolved reference: AuthClient

- [ ] **Step 3: Implement AuthClient**

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AuthClient(
    private val http: HttpClient,
    private val issuer: String,
    private val clientId: String,
    private val redirectUri: String,
) {
    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val refresh_token: String = "",
        val id_token: String = "",
        val expires_in: Long,
    )

    @Serializable
    private data class ErrorResponse(val error: String, val error_description: String? = null)

    suspend fun exchangeCode(code: String, codeVerifier: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            params = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            },
            now = now,
        )

    suspend fun refresh(refreshToken: String, now: Instant = Clock.System.now()): Tokens =
        postToken(
            params = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
            now = now,
        )

    private suspend fun postToken(params: Parameters, now: Instant): Tokens {
        val resp: HttpResponse = http.submitForm(url = "$issuer/oauth/v2/token", formParameters = params)
        if (!resp.status.isSuccess()) {
            val text = resp.bodyAsText()
            val err = runCatching { JSON.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
            val code = err?.error ?: "http_${resp.status.value}"
            val desc = err?.error_description ?: text.take(200)
            val reauth = code == "invalid_grant"
            throw OidcException(code, desc, reauthRequired = reauth)
        }
        val tr: TokenResponse = resp.body()
        return Tokens.fromTokenResponse(tr.access_token, tr.refresh_token, tr.id_token, tr.expires_in, now)
    }

    companion object {
        internal val JSON = Json { ignoreUnknownKeys = true }

        fun buildAuthorizeUrl(
            issuer: String,
            clientId: String,
            redirectUri: String,
            scope: String,
            state: String,
            codeChallenge: String,
        ): String {
            val base = URLBuilder("$issuer/oauth/v2/authorize").buildString()
            val q = listOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$base?$q"
        }

        fun endSessionUrl(issuer: String, idToken: String, postLogoutRedirectUri: String): String {
            val q = listOf(
                "id_token_hint" to idToken,
                "post_logout_redirect_uri" to postLogoutRedirectUri,
            ).joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            return "$issuer/oidc/v1/end_session?$q"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthClientTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthClient.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthClientTest.kt
git commit -m "feat(frontend/auth): add AuthClient for PKCE token endpoints"
```

---

## Task 8: AuthState sealed class

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthState.kt`

- [ ] **Step 1: Define the states**

```kotlin
package net.brightroom.mindstock.frontend.auth

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object Authenticating : AuthState
    data object NeedRegister : AuthState
    data class Ready(val tokens: Tokens) : AuthState
    data class Error(val message: String) : AuthState
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthState.kt
git commit -m "feat(frontend/auth): add AuthState sealed interface"
```

---

## Task 9: RpcClientFactory (Sec-WebSocket-Protocol injection)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/rpc/RpcClientFactory.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/rpc/RpcClientFactoryTest.kt`

- [ ] **Step 1: Write the failing test (verify Sec-WebSocket-Protocol via MockEngine)**

```kotlin
package net.brightroom.mindstock.frontend.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpcClientFactoryTest {
    @Test
    fun secWebSocketProtocol_includes_mindstock_v1_and_bearer_token() = runTest {
        var capturedProtocol: String? = null
        val engine = MockEngine { req ->
            capturedProtocol = req.headers[HttpHeaders.SecWebSocketProtocol]
            respondError(HttpStatusCode.NotImplemented)
        }
        val http = HttpClient(engine)
        val factory = RpcClientFactory(http, baseUrl = "http://localhost:8080")
        runCatching { factory.openRaw(path = "user", accessToken = "MY.JWT.TOKEN") }
        val proto = capturedProtocol ?: error("Sec-WebSocket-Protocol not set")
        val parts = proto.split(",").map { it.trim() }
        assertEquals("mindstock.v1", parts[0])
        assertTrue(parts[1].startsWith("mindstock.bearer."), "got $proto")
        val b64 = parts[1].removePrefix("mindstock.bearer.")
        val decoded = base64UrlNoPadDecode(b64).decodeToString()
        assertEquals("MY.JWT.TOKEN", decoded)
    }
}

private fun base64UrlNoPadDecode(s: String): ByteArray {
    val padded = s.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
    // Use platform Base64; for browser tests we can call jsAtob via helper.
    return jsAtobToBytes(padded)
}
private fun jsAtobToBytes(s: String): ByteArray { val r = js("globalThis.atob(s)") as String; return ByteArray(r.length) { r[it].code.toByte() } }
```

> 注: テスト用の base64url decode が必要。本物の `kotlin.io.encoding.Base64` (KMP) は安定 API として stdlib 2.x で使えるならそれに置換。実装時、`@OptIn(ExperimentalEncodingApi::class) Base64.UrlSafe.decode(s)` を試して動けば使う(test も実装も)。

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*RpcClientFactoryTest*"`
Expected: FAIL — Unresolved reference: RpcClientFactory

- [ ] **Step 3: Implement RpcClientFactory**

```kotlin
package net.brightroom.mindstock.frontend.rpc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class RpcClientFactory(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    /** 開いた RPC クライアント。close 時に [closeAll] でまとめて閉じる。 */
    private val opened = mutableListOf<RpcClient>()

    /**
     * 認証済み Krpc クライアントを開く。[path] は "user" / "household" 等。
     * "user/public" は未登録ユーザー用。
     */
    suspend fun open(path: String, accessToken: String): RpcClient {
        val b64 = Base64.UrlSafe.encode(accessToken.encodeToByteArray()).trimEnd('=')
        val httpWithKrpc = http.config {
            installKrpc {
                serialization { json(KrpcJson) }
            }
            install(WebSockets)
        }
        val rpc = httpWithKrpc.rpc("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1, mindstock.bearer.$b64")
        }
        opened += rpc
        return rpc
    }

    /** テスト用: krpc/serializer なしで生 HTTP リクエストを 1 回投げる(ヘッダ検証用)。 */
    internal suspend fun openRaw(path: String, accessToken: String) {
        val b64 = Base64.UrlSafe.encode(accessToken.encodeToByteArray()).trimEnd('=')
        io.ktor.client.request.get("$baseUrl/api/v1/$path") {
            headers.append(HttpHeaders.SecWebSocketProtocol, "mindstock.v1, mindstock.bearer.$b64")
        }
    }

    fun closeAll() {
        opened.forEach { runCatching { it.close("reauth or logout") } }
        opened.clear()
    }
}
```

> 実装時の注意:
> - `http.config { ... }` は HttpClient のスナップショット config を返す。kotlinx-rpc の docs では `HttpClient { installKrpc {...} }` で作る例が多いが、ここでは「access_token を持ち回る側」が動的に factory を切り替えられる必要があるため `config { ... }` を使う。動かない場合は factory が `HttpClient` を都度作り直す形にフォールバック。
> - `openRaw` のテスト helper は実 WS 接続を避けるための回避。MockEngine では get で十分。
> - `RpcClient.close(reason)` シグネチャは kotlinx-rpc 0.10.x に存在(`E2eTestSupport.kt:164` で利用)。

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*RpcClientFactoryTest*"`
Expected: PASS — Sec-WebSocket-Protocol が `mindstock.v1, mindstock.bearer.<b64url>` の形になっている。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/rpc/RpcClientFactory.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/rpc/RpcClientFactoryTest.kt
git commit -m "feat(frontend/rpc): add RpcClientFactory with Sec-WebSocket-Protocol token"
```

---

## Task 10: RpcCallWrapper (401 → refresh → retry)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/rpc/RpcCallWrapper.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/rpc/RpcCallWrapperTest.kt`

- [ ] **Step 1: Write the failing test (use a fake `Reauth` interface to avoid real WS)**

```kotlin
package net.brightroom.mindstock.frontend.rpc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class StubReauth(var nextSuccess: Boolean) : Reauth {
    var refreshCount = 0
    override suspend fun refresh(): Boolean {
        refreshCount++
        return nextSuccess
    }
}

class RpcCallWrapperTest {
    @Test
    fun success_first_try_no_refresh() = runTest {
        val reauth = StubReauth(nextSuccess = true)
        val w = RpcCallWrapper(reauth) { isUnauthorized = false }
        val result = w.call { 42 }
        assertEquals(42, result)
        assertEquals(0, reauth.refreshCount)
    }

    @Test
    fun unauthorized_then_refresh_then_success() = runTest {
        val reauth = StubReauth(nextSuccess = true)
        var attempts = 0
        val w = RpcCallWrapper(reauth) { isUnauthorized = attempts == 0 }
        val result = w.call { attempts++; if (attempts == 1) throw UnauthorizedException() else "ok" }
        assertEquals("ok", result)
        assertEquals(1, reauth.refreshCount)
    }

    @Test
    fun unauthorized_then_refresh_then_unauthorized_throws_reauthRequired() = runTest {
        val reauth = StubReauth(nextSuccess = true)
        val w = RpcCallWrapper(reauth) { isUnauthorized = true }
        val ex = assertFailsWith<ReauthRequiredException> {
            w.call<Unit> { throw UnauthorizedException() }
        }
        assertTrue(true, ex.message ?: "")
        assertEquals(1, reauth.refreshCount)
    }

    @Test
    fun unauthorized_then_refresh_fails_throws_reauthRequired() = runTest {
        val reauth = StubReauth(nextSuccess = false)
        val w = RpcCallWrapper(reauth) { isUnauthorized = true }
        assertFailsWith<ReauthRequiredException> {
            w.call<Unit> { throw UnauthorizedException() }
        }
        assertEquals(1, reauth.refreshCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*RpcCallWrapperTest*"`
Expected: FAIL — multiple unresolved references

- [ ] **Step 3: Implement RpcCallWrapper**

```kotlin
package net.brightroom.mindstock.frontend.rpc

/** RPC 呼び出しが 401 相当だった事を表す例外。kotlinx-rpc 側の 401 マッピングはこちらに変換する。 */
class UnauthorizedException(message: String = "401") : RuntimeException(message)

/** refresh しても 401 が続く / refresh 自体が失敗した。呼び出し元は LoggedOut に遷移すべき。 */
class ReauthRequiredException(message: String = "re-authentication required") : RuntimeException(message)

interface Reauth {
    /** 成功なら true、refresh_token が失効していたら false。 */
    suspend fun refresh(): Boolean
}

/**
 * @param isUnauthorized 例外を 401 として扱うかを判定するフック。
 *   kotlinx-rpc 側の例外型がランタイム依存のため、呼び出し側で判定式を差し込める。
 */
class RpcCallWrapper(
    private val reauth: Reauth,
    private val isUnauthorized: Throwable.() -> Boolean = { this is UnauthorizedException },
) {
    suspend fun <T> call(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            if (!e.isUnauthorized()) throw e
            val refreshed = reauth.refresh()
            if (!refreshed) throw ReauthRequiredException()
            try {
                block()
            } catch (e2: Throwable) {
                if (e2.isUnauthorized()) throw ReauthRequiredException()
                throw e2
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*RpcCallWrapperTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/rpc/RpcCallWrapper.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/rpc/RpcCallWrapperTest.kt
git commit -m "feat(frontend/rpc): add RpcCallWrapper for 401 retry with refresh"
```

---

## Task 11: AuthCallbackHandler

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthCallbackHandler.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthCallbackHandlerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthCallbackHandlerTest {
    private fun client(json: String): HttpClient =
        HttpClient(MockEngine {
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }

    @Test
    fun mismatched_state_rejected() = runTest {
        val ac = AuthClient(client("{}"), "https://idp.example", "c1", "x")
        val h = AuthCallbackHandler(ac, savedState = "expected-S", savedVerifier = "V")
        assertFailsWith<OidcException> { h.handle(receivedState = "wrong-S", code = "C", now = Instant.fromEpochSeconds(0)) }
    }

    @Test
    fun matched_state_exchanges_code_and_returns_tokens() = runTest {
        val ac = AuthClient(
            client("""{"access_token":"AT","refresh_token":"RT","id_token":"IT","expires_in":3600}"""),
            "https://idp.example", "c1", "x",
        )
        val h = AuthCallbackHandler(ac, savedState = "S", savedVerifier = "V")
        val tokens = h.handle(receivedState = "S", code = "C", now = Instant.fromEpochSeconds(100))
        assertEquals("AT", tokens.accessToken)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthCallbackHandlerTest*"`
Expected: FAIL — Unresolved reference

- [ ] **Step 3: Implement**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class AuthCallbackHandler(
    private val authClient: AuthClient,
    private val savedState: String?,
    private val savedVerifier: String?,
) {
    suspend fun handle(receivedState: String, code: String, now: Instant = Clock.System.now()): Tokens {
        if (savedState == null || savedVerifier == null) {
            throw OidcException("state_missing", "no PKCE state saved for this callback")
        }
        if (savedState != receivedState) {
            throw OidcException("state_mismatch", "expected $savedState, got $receivedState")
        }
        return authClient.exchangeCode(code = code, codeVerifier = savedVerifier, now = now)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthCallbackHandlerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthCallbackHandler.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthCallbackHandlerTest.kt
git commit -m "feat(frontend/auth): add AuthCallbackHandler for state-checked token exchange"
```

---

## Task 12: AuthBootstrap

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthBootstrap.kt`
- Create: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthBootstrapTest.kt`

このタスクは「state 機械」をテストする。実 RPC を呼ばずに済むよう、ping は関数型の `suspend (Tokens) -> PingResult` で抽象化する。

- [ ] **Step 1: Write the failing test**

```kotlin
package net.brightroom.mindstock.frontend.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthBootstrapTest {
    @AfterTest fun cleanup() { TokenStore.clear() }

    private fun freshAuthClient(refreshJson: String = """{"access_token":"AT2","refresh_token":"RT2","id_token":"IT2","expires_in":3600}""", status: HttpStatusCode = HttpStatusCode.OK): AuthClient {
        val http = HttpClient(MockEngine { respond(refreshJson, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }) {
            install(ContentNegotiation) { json() }
        }
        return AuthClient(http, "https://idp.example", "c1", "x")
    }

    @Test
    fun no_tokens_results_in_logged_out() = runTest {
        TokenStore.clear()
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        assertEquals(AuthState.LoggedOut, bs.start(now = Instant.fromEpochSeconds(100)))
    }

    @Test
    fun valid_tokens_and_ping_success_results_in_ready() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(200_000)))
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        val state = bs.start(now = Instant.fromEpochSeconds(100_000))
        assertTrue(state is AuthState.Ready)
        assertEquals("AT", state.tokens.accessToken)
    }

    @Test
    fun valid_tokens_and_ping_unauthorized_results_in_need_register() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(200_000)))
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Unauthorized })
        assertEquals(AuthState.NeedRegister, bs.start(now = Instant.fromEpochSeconds(100_000)))
    }

    @Test
    fun expiring_tokens_trigger_refresh_then_ready() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(100_030))) // 30s 後に切れる
        val bs = AuthBootstrap(freshAuthClient(), ping = { PingResult.Success })
        val state = bs.start(now = Instant.fromEpochSeconds(100_000))
        assertTrue(state is AuthState.Ready)
        assertEquals("AT2", state.tokens.accessToken)
        assertEquals("AT2", TokenStore.load()!!.accessToken)
    }

    @Test
    fun refresh_failure_results_in_logged_out_and_clear() = runTest {
        TokenStore.save(Tokens("AT", "RT", "IT", Instant.fromEpochSeconds(100_030)))
        val bs = AuthBootstrap(
            freshAuthClient("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest),
            ping = { PingResult.Success },
        )
        assertEquals(AuthState.LoggedOut, bs.start(now = Instant.fromEpochSeconds(100_000)))
        assertEquals(null, TokenStore.load())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthBootstrapTest*"`
Expected: FAIL — Unresolved references

- [ ] **Step 3: Implement**

```kotlin
package net.brightroom.mindstock.frontend.auth

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class PingResult { Success, Unauthorized, Other }

class AuthBootstrap(
    private val authClient: AuthClient,
    private val ping: suspend (Tokens) -> PingResult,
    private val refreshLeewaySeconds: Int = 60,
) {
    suspend fun start(now: Instant = Clock.System.now()): AuthState {
        var tokens = TokenStore.load() ?: return AuthState.LoggedOut
        if (tokens.willExpireWithin(refreshLeewaySeconds, now)) {
            tokens = try {
                authClient.refresh(tokens.refreshToken, now).also { TokenStore.save(it) }
            } catch (_: OidcException) {
                TokenStore.clear()
                return AuthState.LoggedOut
            }
        }
        return when (ping(tokens)) {
            PingResult.Success -> AuthState.Ready(tokens)
            PingResult.Unauthorized -> AuthState.NeedRegister
            PingResult.Other -> AuthState.Error("接続できませんでした")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :frontend:wasmJsBrowserTest --tests "*AuthBootstrapTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/AuthBootstrap.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/auth/AuthBootstrapTest.kt
git commit -m "feat(frontend/auth): add AuthBootstrap state machine"
```

---

## Task 13: UI — LoginScreen, AuthCallbackScreen, RegisterDialog, AppShell

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/ui/login/LoginScreen.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/ui/callback/AuthCallbackScreen.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/ui/register/RegisterDialog.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/ui/shell/AppShell.kt`

このタスクはレンダリングテストを書かない(spec §6 の方針)。手動検証で担保する。

- [ ] **Step 1: LoginScreen**

```kotlin
package net.brightroom.mindstock.frontend.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("mindstock", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) { Text("ログイン") }
    }
}
```

- [ ] **Step 2: AuthCallbackScreen**

```kotlin
package net.brightroom.mindstock.frontend.ui.callback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AuthCallbackScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("ログイン処理中...")
    }
}
```

- [ ] **Step 3: RegisterDialog**

```kotlin
package net.brightroom.mindstock.frontend.ui.register

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RegisterDialog(
    onSubmit: (displayName: String) -> Unit,
    errorMessage: String? = null,
    submitting: Boolean = false,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("ようこそ") },
        text = {
            Column {
                Text("表示名を入力してください")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, enabled = !submitting)
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name) }, enabled = !submitting && name.isNotBlank()) {
                Text(if (submitting) "登録中..." else "登録")
            }
        },
        dismissButton = null,
    )
}
```

- [ ] **Step 4: AppShell**

```kotlin
package net.brightroom.mindstock.frontend.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppShell(displayName: String, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TopAppBar(
            title = { Text("mindstock") },
            actions = { TextButton(onClick = onLogout) { Text("ログアウト") } },
        )
        Spacer(Modifier.height(24.dp))
        Text("Hello, $displayName", style = MaterialTheme.typography.headlineMedium)
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/ui/
git commit -m "feat(frontend/ui): add LoginScreen, AuthCallbackScreen, RegisterDialog, AppShell"
```

---

## Task 14: Wire App.kt to AuthBootstrap

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/App.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/Main.kt`

このタスクは Compose を起点に AuthBootstrap → state → 各画面 を結ぶ。kotlinx-rpc 接続は `RpcClientFactory` 経由。`HouseholdRpcService.findOf()` を ping に使う。

- [ ] **Step 1: Rewrite App.kt**

```kotlin
package net.brightroom.mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.coroutines.launch
import net.brightroom.mindstock.frontend.auth.*
import net.brightroom.mindstock.frontend.rpc.RpcClientFactory
import net.brightroom.mindstock.frontend.rpc.UnauthorizedException
import net.brightroom.mindstock.frontend.ui.callback.AuthCallbackScreen
import net.brightroom.mindstock.frontend.ui.login.LoginScreen
import net.brightroom.mindstock.frontend.ui.register.RegisterDialog
import net.brightroom.mindstock.frontend.ui.shell.AppShell
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService
import net.brightroom.mindstock.domain.model.user.DisplayName
import kotlinx.rpc.withService

private const val STATE_KEY = "mindstock.oauth.state.v1"
private const val VERIFIER_KEY = "mindstock.oauth.verifier.v1"
private const val RETURN_TO_KEY = "mindstock.oauth.return_to.v1"

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AuthState>(AuthState.Authenticating) }
    var registerError by remember { mutableStateOf<String?>(null) }
    var registerSubmitting by remember { mutableStateOf(false) }

    val httpClient = remember {
        HttpClient {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
    }
    val authClient = remember {
        AuthClient(httpClient, AuthConfig.ISSUER, AuthConfig.CLIENT_ID, AuthConfig.REDIRECT_URI)
    }
    val rpcFactory = remember {
        // baseUrl は redirect_uri と同じ origin から推定
        val origin = window.location.origin
        RpcClientFactory(httpClient, baseUrl = origin)
    }

    LaunchedEffect(Unit) {
        if (window.location.pathname == "/auth/callback") {
            handleCallback(authClient) { newState ->
                state = newState
                // URL を / に戻す
                window.history.replaceState(null, "", "/")
            }
            return@LaunchedEffect
        }
        val ping: suspend (Tokens) -> PingResult = { tokens ->
            try {
                val rpc = rpcFactory.open("household", tokens.accessToken)
                rpc.withService<HouseholdRpcService>().findOf()
                PingResult.Success
            } catch (e: UnauthorizedException) {
                PingResult.Unauthorized
            } catch (_: Throwable) {
                PingResult.Other
            }
        }
        state = AuthBootstrap(authClient, ping).start()
    }

    MaterialTheme {
        when (val s = state) {
            is AuthState.LoggedOut -> LoginScreen(onLogin = { startLogin(authClient) })
            is AuthState.Authenticating -> AuthCallbackScreen()
            is AuthState.NeedRegister -> RegisterDialog(
                errorMessage = registerError,
                submitting = registerSubmitting,
                onSubmit = { name ->
                    registerSubmitting = true
                    registerError = null
                    scope.launch {
                        try {
                            // public realm に access_token を乗せて register
                            val tokens = TokenStore.load() ?: error("no tokens")
                            val rpc = rpcFactory.open("user/public", tokens.accessToken)
                            rpc.withService<UserPublicRpcService>().register(DisplayName(name))
                            // 登録成功 → Ready に遷移するため再度 ping
                            state = AuthState.Ready(tokens)
                        } catch (e: Throwable) {
                            registerError = e.message ?: "登録に失敗しました"
                        } finally {
                            registerSubmitting = false
                        }
                    }
                },
            )
            is AuthState.Ready -> AppShell(
                displayName = "user", // displayName は User RPC で別途取得する Plan で改善
                onLogout = {
                    val idToken = s.tokens.idToken
                    TokenStore.clear()
                    rpcFactory.closeAll()
                    window.location.assign(AuthClient.endSessionUrl(AuthConfig.ISSUER, idToken, AuthConfig.POST_LOGOUT_REDIRECT_URI))
                },
            )
            is AuthState.Error -> Text(s.message)
        }
    }
}

private fun startLogin(authClient: AuthClient) {
    val verifier = Pkce.newVerifier()
    val state = Pkce.newVerifier(length = 43) // 同じ alphabet を流用
    val returnTo = window.location.pathname + window.location.search
    window.sessionStorage.setItem(STATE_KEY, state)
    window.sessionStorage.setItem(VERIFIER_KEY, verifier)
    window.sessionStorage.setItem(RETURN_TO_KEY, returnTo)
    // challenge は async なので一旦 sessionStorage に verifier を入れて、URL を作ってから飛ぶ
    kotlinx.coroutines.GlobalScope.launch {
        val challenge = Pkce.challenge(verifier)
        val scope = "openid profile offline_access urn:zitadel:iam:org:project:id:${AuthConfig.PROJECT_ID}:aud"
        val url = AuthClient.buildAuthorizeUrl(AuthConfig.ISSUER, AuthConfig.CLIENT_ID, AuthConfig.REDIRECT_URI, scope, state, challenge)
        window.location.assign(url)
    }
}

private suspend fun handleCallback(authClient: AuthClient, setState: (AuthState) -> Unit) {
    val params = window.location.search.removePrefix("?").split("&").associate {
        val (k, v) = it.split("=", limit = 2).let { p -> p[0] to (p.getOrNull(1) ?: "") }
        k to v
    }
    val savedState = window.sessionStorage.getItem(STATE_KEY)
    val savedVerifier = window.sessionStorage.getItem(VERIFIER_KEY)
    val handler = AuthCallbackHandler(authClient, savedState, savedVerifier)
    try {
        val tokens = handler.handle(receivedState = params["state"] ?: "", code = params["code"] ?: "")
        TokenStore.save(tokens)
        window.sessionStorage.removeItem(STATE_KEY)
        window.sessionStorage.removeItem(VERIFIER_KEY)
        // ping は次の LaunchedEffect で行わせるため、Authenticating のまま遷移
        setState(AuthState.Authenticating)
        // 再ロードで bootstrap 経路を通す
        window.location.replace(window.sessionStorage.getItem(RETURN_TO_KEY) ?: "/")
    } catch (e: OidcException) {
        setState(AuthState.Error("ログインに失敗しました: ${e.errorCode}"))
    }
}
```

> 実装メモ:
> - `kotlinx.rpc.withService<T>()` は 0.10.x の慣行(spec の `kotlinx-rpc-0.10.2-conventions` メモ参照)。
> - `state` 用の nonce は `Pkce.newVerifier(43)` を流用しているが、別途乱数 helper にしてもよい。
> - `displayName` の表示は MVP では `"user"` 固定で良し(後続 Plan で `UserRpcService.findMe()` 相当を追加した時に置換)。spec の完了条件には displayName 表示が含まれるが、registerダイアログで入力した値を一時保持して `AppShell` に渡す。実装時にこの線で対応 → 下の修正版を採用。

- [ ] **Step 2: Refine: AppShell に displayName を流す**

`State<String?>` で displayName を保持し、register 成功時 / Ready 確定時に値を入れる。実装の概略は上記コードに `var displayName by remember { mutableStateOf<String?>(null) }` を足して、register 成功時に `displayName = name` を設定、`AppShell(displayName = displayName ?: "user", ...)` を渡すこと。

- [ ] **Step 3: Manual smoke (build only)**

Run: `AUTH_CLIENT_ID=stub AUTH_AUDIENCE=stub AUTH_PROJECT_ID=stub ./gradlew :frontend:wasmJsBrowserDistribution`
Expected: ビルドが通る。型エラーが無いことだけ確認(実行は Task 16 の手動検証で)。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): wire AuthBootstrap and screens in App"
```

---

## Task 15: Update README with Zitadel + AUTH_* env setup

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Append frontend auth setup section**

`README.md` の Zitadel セクション末尾(または新セクション)に追加:

````markdown
## frontend 認証 (Zitadel OIDC PKCE) ローカルセットアップ

backend Plan 8 と同じ Zitadel インスタンスを使う。以下は frontend を起動するための追加手順。

### Zitadel 側

1. http://localhost:8081 にログイン
2. Project `mindstock` → Application を `User Agent (PKCE)` で作成。アプリ名は `mindstock-frontend`
3. Redirect URIs に `http://localhost:8080/auth/callback` を追加
4. Post Logout Redirect URIs に `http://localhost:8080/` を追加
5. 作成後に表示される **Client ID** を控える(`AUTH_CLIENT_ID`)
6. 同じ Project の API `mindstock-backend` の Resource ID(数値文字列)を控える(`AUTH_AUDIENCE`)
7. Project `mindstock` 自体の ID(URL に表示される `projects/<id>`)を控える(`AUTH_PROJECT_ID`)

### env 設定

`mise.toml`(or `.envrc`)に追加:

```toml
[env]
AUTH_ISSUER = "http://localhost:8081"
AUTH_CLIENT_ID = "<Client ID>"
AUTH_REDIRECT_URI = "http://localhost:8080/auth/callback"
AUTH_POST_LOGOUT_REDIRECT_URI = "http://localhost:8080/"
AUTH_AUDIENCE = "<API mindstock-backend の Resource ID>"
AUTH_PROJECT_ID = "<Project mindstock の ID>"
```

未設定だと `./gradlew :frontend:generateAuthConfig` がビルド失敗する(意図的)。

### 起動

```sh
docker compose up -d
./gradlew :backend:application:api:run            # 別ターミナル
./gradlew :frontend:wasmJsBrowserRun              # ブラウザが http://localhost:8080 を開く
```

ログイン → 表示名入力(初回のみ)→ `Hello, <name>` 画面が出れば成功。
````

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add frontend Zitadel/AUTH_* setup"
```

---

## Task 16: Manual verification

**Files:** なし(ローカル動作確認のみ)

- [ ] **Step 1: Compose 一式起動**

```bash
docker compose up -d
```

- [ ] **Step 2: Zitadel UI で前述の手順を実施し、AUTH_* env を設定**

- [ ] **Step 3: backend / frontend を起動**

```bash
./gradlew :backend:application:api:run            # ターミナル A
./gradlew :frontend:wasmJsBrowserRun              # ターミナル B
```

- [ ] **Step 4: 動線を踏む**

1. http://localhost:8080 を開く → LoginScreen
2. 「ログイン」→ Zitadel ログイン → callback で戻る
3. 初回: RegisterDialog → 表示名入力 → AppShell に遷移し `Hello, <name>`
4. 「ログアウト」→ Zitadel セッション終了 → LoginScreen

- [ ] **Step 5: DevTools で確認**

- Application タブ > sessionStorage > `mindstock.tokens.v1` に Tokens JSON が入っている
- Network タブ > WS 接続のリクエストヘッダに `Sec-WebSocket-Protocol: mindstock.v1, mindstock.bearer.<base64url>` が乗っている
- `expiresAt` が `Date.now()/1000` + 3600 程度

- [ ] **Step 6: 401 リトライの確認(任意)**

sessionStorage の `accessToken` を手で壊して RPC を発火 → 自動で refresh → 再接続して通ることを Network タブで確認。

- [ ] **Step 7: 完了の commit (不要なら省略)**

(コードを触らずに済めば commit 不要)

---

## Self-review checklist applied

- **Spec coverage:** §3.1 PKCE flow → Task 7+13+14、§3.2 bootstrap → Task 12、§3.3 401 retry → Task 10、§3.4 logout → Task 14、§4 components → Task 2-13、§4.1 env injection → Task 1、§5 error handling → 各 Task のテストでカバー、§6 tests → Task 2-12 で commonTest を網羅、§7-8 完了条件 → Task 14-16 で担保。
- **Placeholder scan:** 残「実装時に微調整」コメント = Task 2 / Task 9 の wasmJs FFI と HttpClient.config の挙動依存箇所。これは「実装段階で動かなければフォールバック」が明示してあるので意図的に残置。それ以外は具体コード。
- **Type consistency:** `Tokens(accessToken, refreshToken, idToken, expiresAt)` を全 Task で同じ順で使用。`PingResult` / `AuthState` / `Reauth` 各列挙の値名も統一済。`HouseholdRpcService.findOf()` を ping 用 RPC として spec から差し替え(spec は `list` だったが実 IF は `findOf`)。
- **Future spec update:** spec §3.2 の `HouseholdRpcService.list` を `HouseholdRpcService.findOf()` に追記修正する作業を、本 plan の Task 0 として手前に置くか、別 commit で対応する選択肢。本 plan では Task 14 のコードを `findOf()` で書いているため、spec と plan の不整合は plan 側が正しい。実装着手前に spec を 1 行 PR で揃えるのが望ましい。
