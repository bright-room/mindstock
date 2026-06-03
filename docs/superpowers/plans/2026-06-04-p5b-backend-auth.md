# P5b backend 認証(Zitadel OIDC / JWT 検証)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:backend:api` に Zitadel access_token(JWT)を JWKS 検証して接続単位の認証セッションを組み立てる自作 Ktor plugin 群を新設する(配線は P5c)。

**Architecture:** フルリプレイス前の実装(commit `11c9b31`)を土台に、新アーキへ移植する。`com.auth0:java-jwt` で JWT 検証 → `AuthIdentity` を組み立て → `ResidentRepository.findByAuth` で Resident 解決(not-found=未登録)→ sealed `MindstockSession`(`Registered`/`Unregistered`)を `call.attributes` に格納。WS は `Sec-WebSocket-Protocol: mindstock.bearer.<b64(jwt)>` で token を運ぶ。ヘッダ解析は `AuthorizationHeader` / `WebSocketProtocols` の value class に閉じ込め、各部品は薄い委譲 + 早期リターンで保つ。配線から独立し `testApplication`(と value class は純粋関数)で単体検証する。

**Tech Stack:** Kotlin(`@JvmInline value class`)/ Ktor server plugin API(`createApplicationPlugin` / `createRouteScopedPlugin`)/ `com.auth0:java-jwt` + `jwks-rsa` / kotlinx-coroutines / Exposed(`ResidentRepository`)/ Kotest FunSpec + `ktor-server-test-host` + mockk。

**設計の出典:** `docs/superpowers/specs/2026-06-03-p5b-backend-auth-design.md`

---

## ファイル構成

main(すべて `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/` 配下):
- Create: `MindstockSession.kt` — sealed セッション + AttributeKey
- Create: `JwksKeyProvider.kt` — `JwkProvider` → `RSAKeyProvider` 橋渡し
- Create: `AuthorizationHeader.kt` — `Authorization` ヘッダ value class(Bearer 抽出を閉じ込め)
- Create: `WebSocketProtocols.kt` — `Sec-WebSocket-Protocol` value class(エントリ判定 / bearer 抽出。extractor と echo plugin で共用)
- Create: `WsBearerTokenExtractor.kt` — token 抽出の薄い委譲
- Create: `WsSubprotocolEchoPlugin.kt` — subprotocol echo
- Create: `RequireRegisteredUserPlugin.kt` — 登録済み境界
- Create: `MindstockAuthPlugin.kt` — JWT 検証 + session 組み立て

test:
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/AuthorizationHeaderTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocolsTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractorTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsSubprotocolEchoPluginTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPluginTest.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestKeyPair.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestJwks.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestJwtIssuer.kt`

`build.gradle.kts` 変更なし(`auth0.java.jwt` / `auth0.jwks.rsa` / `server.websockets` / `client.websockets` / `mockk` / `kotest` は classpath 済。`exp` に使う `kotlin.time.Instant` は stdlib)。

**ビルド確認コマンド:** `./gradlew :backend:api:compileKotlin` / テスト: `./gradlew :backend:api:test`

---

## Task 1: `MindstockSession`(sealed セッション型)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt`

依存される土台。テストは後続 Task の plugin 経由で検証するため、本 Task はコンパイルのみ確認。

- [ ] **Step 1: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.util.AttributeKey
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * WS upgrade 時に [MindstockAuthPlugin] が組み立て、call.attributes に格納する。
 * 接続単位で immutable。
 *
 * - [identity]: JWT 検証成功時に組み立てた AuthIdentity
 * - [exp]: JWT の expiresAt(時間軸上の一点)。P5c の per-message guard が
 *   `kotlin.time.Clock.System.now()` と比較して失効判定する。
 *   kotlinx.datetime.Instant は非推奨のため後継の kotlin.time.Instant を使う。
 * - [callId]: 接続単位のトレース ID。構造化ログに紐付ける
 *
 * 「JWT 有効だが Resident 未登録」を nullable で表さず sealed 2 状態で表現する
 * (nullable 戻り値禁止原則。承認済)。
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
sealed interface MindstockSession {
    val identity: AuthIdentity
    val exp: Instant
    val callId: Uuid

    /** JWT 有効だが Resident 未登録。register route でのみ通過を許す。 */
    data class Unregistered(
        override val identity: AuthIdentity,
        override val exp: Instant,
        override val callId: Uuid,
    ) : MindstockSession

    /** 登録済み Resident。residentId を保持。 */
    data class Registered(
        override val identity: AuthIdentity,
        val residentId: ResidentId,
        override val exp: Instant,
        override val callId: Uuid,
    ) : MindstockSession
}

internal val MindstockSessionKey: AttributeKey<MindstockSession> =
    AttributeKey("net.brightroom.mindstock.MindstockSession")
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockSession.kt
git commit -m "feat(api): sealed MindstockSession(Registered/Unregistered)を追加"
```

---

## Task 2: `JwksKeyProvider`(JwkProvider → RSAKeyProvider 橋渡し)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwksKeyProvider.kt`

純粋な橋渡し。後続 Task 9 の MindstockAuthPlugin 経由で間接検証するため、本 Task はコンパイルのみ。

- [ ] **Step 1: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * java-jwt の `Algorithm.RSA256(...)` が要求する [RSAKeyProvider] を
 * `auth0/jwk` の [JwkProvider] から橋渡しする。
 *
 * - JWKS の cache / rate-limit は [JwkProvider] 側で行う
 * - 秘密鍵は不要(検証専用)
 */
class JwksKeyProvider(
    private val jwkProvider: JwkProvider,
) : RSAKeyProvider {
    override fun getPublicKeyById(keyId: String?): RSAPublicKey = jwkProvider.get(keyId).publicKey as RSAPublicKey

    override fun getPrivateKey(): RSAPrivateKey? = null

    override fun getPrivateKeyId(): String? = null
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/JwksKeyProvider.kt
git commit -m "feat(api): JwksKeyProvider(JwkProvider->RSAKeyProvider)を追加"
```

---

## Task 3: `AuthorizationHeader`(value class)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthorizationHeader.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/AuthorizationHeaderTest.kt`

`Authorization` ヘッダ生値をラップし `Bearer <token>` の抽出ロジックを閉じ込める。純粋関数なので Ktor 不要で単体検証できる。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AuthorizationHeaderTest :
    FunSpec({
        test("Bearer <token> → token") {
            AuthorizationHeader("Bearer abc.def.ghi").bearerToken() shouldBe "abc.def.ghi"
        }

        test("scheme は大文字小文字を無視") {
            AuthorizationHeader("bearer abc").bearerToken() shouldBe "abc"
        }

        test("前後・scheme と credentials 間の余分な空白を trim") {
            AuthorizationHeader("  Bearer   abc.def  ").bearerToken() shouldBe "abc.def"
        }

        test("Bearer 以外の scheme → null") {
            AuthorizationHeader("Basic dXNlcjpwYXNz").bearerToken().shouldBeNull()
        }

        test("scheme のみ(credentials 無し)→ null") {
            AuthorizationHeader("Bearer").bearerToken().shouldBeNull()
        }

        test("空文字 → null") {
            AuthorizationHeader("").bearerToken().shouldBeNull()
        }
    })
```

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:test --tests "*AuthorizationHeaderTest*"`
Expected: FAIL(`AuthorizationHeader` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.auth.AuthScheme
import kotlin.jvm.JvmInline

/**
 * `Authorization` ヘッダの生値をラップし、Bearer token 抽出を閉じ込める。
 */
@JvmInline
value class AuthorizationHeader(
    private val raw: String,
) {
    /** `Bearer <token>` 形式なら token を返す。それ以外は null。 */
    fun bearerToken(): String? {
        val parts = raw.trim().split(" ", limit = 2)
        if (parts.size != 2) return null
        val (scheme, credentials) = parts
        if (!scheme.equals(AuthScheme.Bearer, ignoreCase = true)) return null
        return credentials.trim()
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*AuthorizationHeaderTest*"`
Expected: PASS(6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthorizationHeader.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/AuthorizationHeaderTest.kt
git commit -m "feat(api): AuthorizationHeader value class(Bearer 抽出)を追加"
```

---

## Task 4: `WebSocketProtocols`(value class・extractor と echo plugin で共用)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocols.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocolsTest.kt`

`Sec-WebSocket-Protocol` ヘッダ群を「カンマ分割・trim 済みエントリ集合」として扱い、`has()`(echo 判定用)と `bearerToken()`(token 抽出用)を閉じ込める。`from(List<String>)` を公開して純粋に単体検証する。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.Base64

class WebSocketProtocolsTest :
    FunSpec({
        fun protocols(vararg raw: String) = WebSocketProtocols.from(raw.toList())

        fun b64(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())

        test("has: 提示された protocol を検出") {
            protocols("mindstock.v1, mindstock.bearer.x").has("mindstock.v1") shouldBe true
        }

        test("has: 提示されていない protocol は false") {
            protocols("other.proto").has("mindstock.v1") shouldBe false
        }

        test("bearerToken: mindstock.bearer.<b64> を decode") {
            val token = "abc.def.ghi"
            protocols("mindstock.v1, mindstock.bearer.${b64(token)}").bearerToken() shouldBe token
        }

        test("bearerToken: bearer entry 無し → null") {
            protocols("mindstock.v1, other.proto").bearerToken().shouldBeNull()
        }

        test("複数ヘッダ行・entry 間の空白を trim") {
            val token = "abc"
            WebSocketProtocols.from(listOf("mindstock.v1 ", " mindstock.bearer.${b64(token)}")).bearerToken() shouldBe token
        }

        test("bearerToken: 不正 base64 → null") {
            protocols("mindstock.bearer.!!!notbase64!!!").bearerToken().shouldBeNull()
        }
    })
```

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:test --tests "*WebSocketProtocolsTest*"`
Expected: FAIL(`WebSocketProtocols` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.jvm.JvmInline

/**
 * `Sec-WebSocket-Protocol` ヘッダ群を「カンマ分割・trim 済みエントリ集合」として扱う。
 * アプリプロトコル判定([has])と bearer token 抽出([bearerToken])を閉じ込め、
 * [WsBearerTokenExtractor] と [WsSubprotocolEchoPlugin] で共用する。
 */
@JvmInline
value class WebSocketProtocols private constructor(
    private val entries: List<String>,
) {
    /** 指定 subprotocol が提示されているか。 */
    fun has(protocol: String): Boolean = protocol in entries

    /** `mindstock.bearer.<base64url(jwt)>` があれば decode した JWT を返す。無ければ null。 */
    fun bearerToken(): String? {
        val entry = entries.firstOrNull { it.startsWith(BEARER_PREFIX) } ?: return null
        return decodeBase64Url(entry.removePrefix(BEARER_PREFIX))
    }

    companion object {
        /** アプリプロトコル識別子(echo 対象)。 */
        const val APP_PROTOCOL: String = "mindstock.v1"

        /** JWT を運ぶ bearer subprotocol の prefix(echo してはならない)。 */
        private const val BEARER_PREFIX: String = "mindstock.bearer."

        fun from(call: ApplicationCall): WebSocketProtocols =
            from(call.request.headers.getAll(HttpHeaders.SecWebSocketProtocol).orEmpty())

        fun from(rawHeaders: List<String>): WebSocketProtocols =
            WebSocketProtocols(rawHeaders.flatMap { it.split(",") }.map { it.trim() })

        private fun decodeBase64Url(value: String): String? =
            runCatching { String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8) }.getOrNull()
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*WebSocketProtocolsTest*"`
Expected: PASS(6 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocols.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WebSocketProtocolsTest.kt
git commit -m "feat(api): WebSocketProtocols value class(has/bearerToken)を追加"
```

---

## Task 5: `WsBearerTokenExtractor`(薄い委譲)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractor.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractorTest.kt`

解析ロジックは Task 3/4 の value class が持つ。本オブジェクトは「Authorization 優先 → WS protocol」の委譲のみ。テストは call からの取り出しと優先順位の結線確認に絞る(細かい解析ケースは value class 側でカバー済)。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.Base64

class WsBearerTokenExtractorTest :
    FunSpec({
        suspend fun extractedTokenWith(
            authHeader: String? = null,
            wsProtocol: String? = null,
        ): String? {
            var captured: String? = null
            testApplication {
                application {
                    routing {
                        get("/probe") {
                            captured = WsBearerTokenExtractor.extractRaw(call)
                            call.respondText("ok")
                        }
                    }
                }
                client.get("/probe") {
                    if (authHeader != null) header(HttpHeaders.Authorization, authHeader)
                    if (wsProtocol != null) header(HttpHeaders.SecWebSocketProtocol, wsProtocol)
                }
            }
            return captured
        }

        fun b64(token: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray())

        test("どちらも無し → null") {
            extractedTokenWith().shouldBeNull()
        }

        test("Authorization から抽出") {
            extractedTokenWith(authHeader = "Bearer abc.def") shouldBe "abc.def"
        }

        test("Sec-WebSocket-Protocol から抽出") {
            val token = "abc.def"
            extractedTokenWith(wsProtocol = "mindstock.v1, mindstock.bearer.${b64(token)}") shouldBe token
        }

        test("両方ある場合は Authorization を優先") {
            extractedTokenWith(
                authHeader = "Bearer auth.token",
                wsProtocol = "mindstock.v1, mindstock.bearer.${b64("ws.token")}",
            ) shouldBe "auth.token"
        }
    })
```

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:test --tests "*WsBearerTokenExtractorTest*"`
Expected: FAIL(`WsBearerTokenExtractor` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * 生の JWT を取り出す。Browser の WebSocket API は Authorization ヘッダを付けられないため
 * `Sec-WebSocket-Protocol` の `mindstock.bearer.<base64url(jwt)>` でも運ぶ。
 * Authorization ヘッダを優先(REST 互換 / テスト容易性)。
 */
object WsBearerTokenExtractor {
    fun extractRaw(call: ApplicationCall): String? = authorizationBearer(call) ?: webSocketProtocolBearer(call)

    private fun authorizationBearer(call: ApplicationCall): String? {
        val header = call.request.header(HttpHeaders.Authorization) ?: return null
        return AuthorizationHeader(header).bearerToken()
    }

    private fun webSocketProtocolBearer(call: ApplicationCall): String? = WebSocketProtocols.from(call).bearerToken()
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*WsBearerTokenExtractorTest*"`
Expected: PASS(4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractor.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsBearerTokenExtractorTest.kt
git commit -m "feat(api): WsBearerTokenExtractor(value class への委譲)を追加"
```

---

## Task 6: `WsSubprotocolEchoPlugin`(subprotocol echo)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsSubprotocolEchoPlugin.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsSubprotocolEchoPluginTest.kt`

`WebSocketProtocols`(Task 4)を使い、早期リターンで「upgrade でなければ素通し / app protocol 未提示なら素通し / それ以外は echo」を表す。WS upgrade を testApplication で完結させるのは煩雑なため、`Upgrade: websocket` + `Sec-WebSocket-Protocol` ヘッダを付けた通常リクエストで応答ヘッダ書き込みを検証する。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class WsSubprotocolEchoPluginTest :
    FunSpec({
        suspend fun probeWith(
            upgrade: String? = null,
            wsProtocol: String? = null,
        ): HttpResponse {
            lateinit var response: HttpResponse
            testApplication {
                application {
                    install(WsSubprotocolEchoPlugin)
                    routing { get("/probe") { call.respondText("ok") } }
                }
                response =
                    client.get("/probe") {
                        if (upgrade != null) header(HttpHeaders.Upgrade, upgrade)
                        if (wsProtocol != null) header(HttpHeaders.SecWebSocketProtocol, wsProtocol)
                    }
            }
            return response
        }

        test("upgrade=websocket + mindstock.v1 提示 → mindstock.v1 を echo") {
            val res = probeWith(upgrade = "websocket", wsProtocol = "mindstock.v1, mindstock.bearer.xyz")
            res.headers[HttpHeaders.SecWebSocketProtocol] shouldBe "mindstock.v1"
        }

        test("mindstock.bearer.* は response header に echo しない") {
            val res = probeWith(upgrade = "websocket", wsProtocol = "mindstock.v1, mindstock.bearer.secrettoken")
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "bearer"
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "secrettoken"
        }

        test("upgrade でない通常リクエストは素通し(echo しない)") {
            val res = probeWith(wsProtocol = "mindstock.v1")
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "mindstock.v1"
        }

        test("mindstock.v1 を提示しない場合は echo しない") {
            val res = probeWith(upgrade = "websocket", wsProtocol = "other.proto")
            (res.headers[HttpHeaders.SecWebSocketProtocol] ?: "") shouldNotContain "mindstock.v1"
        }
    })
```

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:test --tests "*WsSubprotocolEchoPluginTest*"`
Expected: FAIL(`WsSubprotocolEchoPlugin` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.header

/**
 * WHATWG WebSocket 仕様上、client が Sec-WebSocket-Protocol を提示したら server は
 * 受理した subprotocol を 1 つ echo しないとブラウザが接続を fail させる。
 *
 * - [WebSocketProtocols.APP_PROTOCOL] (mindstock.v1) のみ echo する。
 * - bearer subprotocol (JWT を含む) は echo しない。token を response header や
 *   中間 proxy のログに漏らさないため([WebSocketProtocols] は bearer を echo 対象に含めない)。
 *
 * kotlinx-rpc-krpc-ktor-server の `rpc(path)` builder は subprotocol 応答制御 API を
 * 公開しないため、本 plugin が response header を上書きする。
 */
val WsSubprotocolEchoPlugin =
    createApplicationPlugin(name = "WsSubprotocolEcho") {
        onCall { call ->
            if (!call.isWebSocketUpgrade()) return@onCall
            val protocols = WebSocketProtocols.from(call)
            if (!protocols.has(WebSocketProtocols.APP_PROTOCOL)) return@onCall
            call.response.header(HttpHeaders.SecWebSocketProtocol, WebSocketProtocols.APP_PROTOCOL)
        }
    }

private fun ApplicationCall.isWebSocketUpgrade(): Boolean =
    request.headers[HttpHeaders.Upgrade].equals("websocket", ignoreCase = true)
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*WsSubprotocolEchoPluginTest*"`
Expected: PASS(4 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/WsSubprotocolEchoPlugin.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/WsSubprotocolEchoPluginTest.kt
git commit -m "feat(api): WsSubprotocolEchoPlugin(bearer は echo しない)を追加"
```

---

## Task 7: `RequireRegisteredUserPlugin`(登録済み境界)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun stubSessionPlugin(session: MindstockSession) =
    createApplicationPlugin(name = "StubSession-${Uuid.random()}") {
        onCall { call -> call.attributes.put(MindstockSessionKey, session) }
    }

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class RequireRegisteredUserPluginTest :
    FunSpec({
        val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub"))
        // 本 plugin は exp を見ないため任意の値でよい(失効判定は P5c の guard)。
        val farFuture = Instant.DISTANT_FUTURE

        fun registered() = MindstockSession.Registered(identity, ResidentId.create(), farFuture, Uuid.random())

        fun unregistered() = MindstockSession.Unregistered(identity, farFuture, Uuid.random())

        suspend fun guardedStatusWith(session: MindstockSession?): HttpStatusCode {
            lateinit var status: HttpStatusCode
            testApplication {
                application {
                    if (session != null) install(stubSessionPlugin(session))
                    routing {
                        route("/guarded") {
                            install(RequireRegisteredUserPlugin)
                            get { call.respondText("ok") }
                        }
                    }
                }
                status = client.get("/guarded").status
            }
            return status
        }

        test("Registered → 200") {
            guardedStatusWith(registered()) shouldBe HttpStatusCode.OK
        }

        test("Unregistered → 401") {
            guardedStatusWith(unregistered()) shouldBe HttpStatusCode.Unauthorized
        }

        test("session 無し → 401") {
            guardedStatusWith(null) shouldBe HttpStatusCode.Unauthorized
        }
    })
```

- [ ] **Step 2: テストが失敗(コンパイル不可)することを確認**

Run: `./gradlew :backend:api:test --tests "*RequireRegisteredUserPluginTest*"`
Expected: FAIL(`RequireRegisteredUserPlugin` 未定義)

- [ ] **Step 3: 実装を書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Route subtree に install することで「登録済み Resident しか通さない」境界を作る。
 * [MindstockAuthPlugin] が組み立てた [MindstockSession] を見て、
 * [MindstockSession.Registered] でなければ 401。register route には install しない。
 */
val RequireRegisteredUserPlugin =
    createRouteScopedPlugin(name = "RequireRegisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session !is MindstockSession.Registered) {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*RequireRegisteredUserPluginTest*"`
Expected: PASS(3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPlugin.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/RequireRegisteredUserPluginTest.kt
git commit -m "feat(api): RequireRegisteredUserPlugin(Registered のみ通過)を追加"
```

---

## Task 8: テスト用 JWT 基盤(TestKeyPair / TestJwks / TestJwtIssuer)

**Files:**
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestKeyPair.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestJwks.kt`
- Create: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/TestJwtIssuer.kt`

Task 9 が `TestKeyPair` / `TestJwtIssuer` を使う(`TestJwks` は P5c の e2e 用に用意)。テストヘルパーのみでプロダクションコードを伴わないため、TDD の赤段階は無くコンパイル確認のみ。

- [ ] **Step 1: `TestKeyPair.kt` を書く**

```kotlin
package net.brightroom.mindstock.e2e.auth

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * テスト suite 単位で 1 つ生成される RSA 鍵ペア。
 * 全テスト共通の kid="test-key-1" を使う。
 */
object TestKeyPair {
    const val KID: String = "test-key-1"

    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
    val privateKey: RSAPrivateKey get() = keyPair.private as RSAPrivateKey
}
```

- [ ] **Step 2: `TestJwks.kt` を書く**

```kotlin
package net.brightroom.mindstock.e2e.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.util.Base64

/**
 * [TestKeyPair] の公開鍵を JWKS (RFC 7517) JSON 文字列にする。
 * `kid` は [TestKeyPair.KID] と一致。アルゴリズムは RS256。
 */
object TestJwks {
    fun asJsonString(): String {
        val pub = TestKeyPair.publicKey
        val n = Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(pub.modulus.toByteArray()))
        val e = Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(pub.publicExponent.toByteArray()))
        val keys =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("kty", JsonPrimitive("RSA"))
                        put("kid", JsonPrimitive(TestKeyPair.KID))
                        put("alg", JsonPrimitive("RS256"))
                        put("use", JsonPrimitive("sig"))
                        put("n", JsonPrimitive(n))
                        put("e", JsonPrimitive(e))
                    },
                )
            }
        return JsonObject(mapOf("keys" to keys)).toString()
    }

    // BigInteger.toByteArray() は符号付きで先頭 0x00 が付くことがある。JWK は magnitude のみ。
    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}
```

- [ ] **Step 3: `TestJwtIssuer.kt` を書く**

```kotlin
package net.brightroom.mindstock.e2e.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date

/**
 * テスト用 JWT 発行器。デフォルトは「本番経路に通る」JWT。
 * 必要に応じて exp / iss / aud / 鍵を上書きしてエラーケースを作る。
 */
object TestJwtIssuer {
    const val DEFAULT_ISSUER = "test-issuer"
    const val DEFAULT_AUDIENCE = "mindstock-backend-test"

    fun issue(
        subject: String,
        issuer: String = DEFAULT_ISSUER,
        audience: String = DEFAULT_AUDIENCE,
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = issuedAt.plusSeconds(3600),
        signWith: Algorithm = Algorithm.RSA256(TestKeyPair.publicKey, TestKeyPair.privateKey),
        kid: String = TestKeyPair.KID,
    ): String =
        JWT
            .create()
            .withKeyId(kid)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(subject)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(signWith)
}
```

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :backend:api:compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/auth/
git commit -m "test(api): テスト用 JWT 基盤(TestKeyPair/TestJwks/TestJwtIssuer)を追加"
```

---

## Task 9: `MindstockAuthPlugin`(JWT 検証 + session 組み立て)

**Files:**
- Create: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`
- Test: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPluginTest.kt`

Task 1〜8 の全部品を統合する中核。`testApplication` のインメモリエンジンには `JwkProviderBuilder(URL)` の実 HTTP fetch が届かないため、**`JwkProvider` を mockk で差し込む**(`TestKeyPair.publicKey` を返す)。署名・issuer・audience・exp の検証は本物の `verifier` がそのまま評価する。`residentRepository` も mockk。

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.e2e.auth.TestJwtIssuer
import net.brightroom.mindstock.e2e.auth.TestKeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.RSAPrivateKey
import java.time.Instant

class MindstockAuthPluginTest :
    FunSpec({
        val issuer = TestJwtIssuer.DEFAULT_ISSUER
        val audience = TestJwtIssuer.DEFAULT_AUDIENCE

        // TestKeyPair の公開鍵を返す JwkProvider。kid に依らず常に同じ鍵を返す
        // (TestJwtIssuer は kid=TestKeyPair.KID を付与する)。
        fun stubJwkProvider(): JwkProvider =
            mockk<JwkProvider>().also { provider ->
                val jwk = mockk<Jwk>()
                every { jwk.publicKey } returns TestKeyPair.publicKey
                every { provider.get(any<String>()) } returns jwk
            }

        // Pair<probe の HTTP status, probe route で観測した session(または null)>
        suspend fun runProbe(
            repo: ResidentRepository,
            authHeader: String?,
        ): Pair<HttpStatusCode, MindstockSession?> {
            var seen: MindstockSession? = null
            lateinit var status: HttpStatusCode
            testApplication {
                application {
                    install(MindstockAuthPlugin) {
                        jwkProvider = stubJwkProvider()
                        this.issuer = issuer
                        this.audience = audience
                        residentRepository = repo
                    }
                    routing {
                        get("/probe") {
                            seen = call.attributes.getOrNull(MindstockSessionKey)
                            call.respondText("ok")
                        }
                    }
                }
                status =
                    client.get("/probe") {
                        if (authHeader != null) header(HttpHeaders.Authorization, authHeader)
                    }.status
            }
            return status to seen
        }

        fun registeredRepo(residentId: ResidentId): ResidentRepository =
            mockk<ResidentRepository>().also {
                every { it.findByAuth(any()) } returns Resident(residentId, Profile(DisplayName("Alice")))
            }

        fun unregisteredRepo(): ResidentRepository =
            mockk<ResidentRepository>().also {
                every { it.findByAuth(any<AuthIdentity>()) } throws ResourceNotFoundException("not found")
            }

        test("有効 JWT + 登録済み sub → Registered(residentId 一致)") {
            val residentId = ResidentId.create()
            val token = TestJwtIssuer.issue(subject = "zitadel-sub-1")
            val (status, session) = runProbe(registeredRepo(residentId), "Bearer $token")
            status shouldBe HttpStatusCode.OK
            val registered = session.shouldBeInstanceOf<MindstockSession.Registered>()
            registered.residentId shouldBe residentId
        }

        test("有効 JWT + 未登録 sub → Unregistered") {
            val token = TestJwtIssuer.issue(subject = "zitadel-sub-new")
            val (status, session) = runProbe(unregisteredRepo(), "Bearer $token")
            status shouldBe HttpStatusCode.OK
            session.shouldBeInstanceOf<MindstockSession.Unregistered>()
        }

        test("token 無し → 401, session 未付与") {
            val (status, session) = runProbe(registeredRepo(ResidentId.create()), null)
            status shouldBe HttpStatusCode.Unauthorized
            session shouldBe null
        }

        test("不正署名(別鍵)→ 401") {
            val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val wrongAlg = Algorithm.RSA256(otherKeys.public as RSAPublicKey, otherKeys.private as RSAPrivateKey)
            val token = TestJwtIssuer.issue(subject = "sub", signWith = wrongAlg)
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("issuer 不一致 → 401") {
            val token = TestJwtIssuer.issue(subject = "sub", issuer = "evil-issuer")
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("audience 不一致 → 401") {
            val token = TestJwtIssuer.issue(subject = "sub", audience = "other-aud")
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }

        test("exp 切れ(leeway 超過)→ 401") {
            val past = Instant.now().minusSeconds(7200)
            val token = TestJwtIssuer.issue(subject = "sub", issuedAt = past, expiresAt = past.plusSeconds(60))
            val (status, _) = runProbe(registeredRepo(ResidentId.create()), "Bearer $token")
            status shouldBe HttpStatusCode.Unauthorized
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*MindstockAuthPluginTest*"`
Expected: FAIL(`MindstockAuthPlugin` / `MindstockAuthConfig` 未定義のコンパイルエラー)

- [ ] **Step 3: 実装を書く**

```kotlin
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MindstockAuthConfig {
    var jwkProvider: JwkProvider? = null
    var issuer: String? = null
    var audience: String? = null
    var residentRepository: ResidentRepository? = null
    var leewaySeconds: Long = 30
}

/**
 * JWT 検証 + [MindstockSession] 組み立てを担う Ktor plugin。
 *
 * Security Invariants(spec §セキュリティ不変条件):
 * 1. JWT 検証 crypto は自前で書かない → `com.auth0:java-jwt` の verify() 経由
 * 2. Algorithm は RSA256 固定([JwksKeyProvider] 経由)
 * 3. JWKS は cache + rate-limit(呼び出し側で [JwkProvider] を渡す前提)
 * 4. `withIssuer` / `withAudience` を必ず指定
 * 5. `acceptLeeway` を明示
 * 6. token 値を含む `Sec-WebSocket-Protocol` は response header に echo しない([WsSubprotocolEchoPlugin])
 *
 * 「JWT 有効だが Resident 未登録」は [ResidentRepository.findByAuth] の
 * ResourceNotFoundException を runCatching で吸収し [MindstockSession.Unregistered] にする。
 * findByAuth は blocking JDBC transaction なので Dispatchers.IO に逃がす。
 */
val MindstockAuthPlugin =
    createApplicationPlugin(name = "MindstockAuth", createConfiguration = ::MindstockAuthConfig) {
        val jwkProvider = requireNotNull(pluginConfig.jwkProvider) { "jwkProvider required" }
        val issuer = requireNotNull(pluginConfig.issuer) { "issuer required" }
        val audience = requireNotNull(pluginConfig.audience) { "audience required" }
        val residentRepository = requireNotNull(pluginConfig.residentRepository) { "residentRepository required" }
        val leewaySeconds = pluginConfig.leewaySeconds

        val verifier: JWTVerifier =
            JWT
                .require(Algorithm.RSA256(JwksKeyProvider(jwkProvider)))
                .withIssuer(issuer)
                .withAudience(audience)
                .acceptLeeway(leewaySeconds)
                .build()

        onCall { call ->
            val token = WsBearerTokenExtractor.extractRaw(call)
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val decoded = runCatching { verifier.verify(token) }.getOrNull()
            if (decoded == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val sub = decoded.subject
            if (sub.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val expDate = decoded.expiresAt
            if (expDate == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@onCall
            }
            val exp = Instant.fromEpochMilliseconds(expDate.time)  // java.util.Date -> kotlin.time.Instant
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
            val callId = Uuid.random()

            val resident =
                withContext(Dispatchers.IO) {
                    runCatching { residentRepository.findByAuth(identity) }.getOrNull()
                }
            val session =
                if (resident != null) {
                    MindstockSession.Registered(identity, resident.id, exp, callId)
                } else {
                    MindstockSession.Unregistered(identity, exp, callId)
                }
            call.attributes.put(MindstockSessionKey, session)
        }
    }
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*MindstockAuthPluginTest*"`
Expected: PASS(7 tests)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt \
        backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPluginTest.kt
git commit -m "feat(api): MindstockAuthPlugin(JWT 検証 + sealed session 組み立て)を追加"
```

---

## Task 10: 全体検証(完了の定義)

**Files:** なし(検証のみ)

- [ ] **Step 1: backend:api の全テストを実行**

Run: `./gradlew :backend:api:test`
Expected: PASS(本 plan の 6 テストクラス含め全 green)

- [ ] **Step 2: spotless / lint(プロジェクト規約)**

Run: `./gradlew :backend:api:spotlessApply`
Expected: BUILD SUCCESSFUL(差分が出たら再コミット)

- [ ] **Step 3: backend モジュールのフルビルド(統合テスト除く軽量確認)**

Run: `./gradlew :backend:api:build -x integrationTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: spotless 差分があればコミット**

```bash
git add -A
git commit -m "style(api): spotless apply" || echo "no changes"
```

---

## P5c への申し送り(本 plan では実装しない)

- `JwkProviderBuilder(URL(jwksUrl)).cached(10, 1, HOURS).rateLimited(10, 1, MINUTES).build()` を構築し plugin config に渡す
- `application.yaml` の `external.auth.{issuer,audience,jwks-url}` 読み込み + `Environment` 連携
- plugin install 順(`WsSubprotocolEchoPlugin` → `MindstockAuthPlugin`)と Routing(register route は `RequireRegisteredUserPlugin` 非適用)
- per-message `session.exp` 再チェックガード `tx()`(`kotlin.time.Clock.System.now() > session.exp` で失効判定、`RpcError`/`RpcResult` 翻訳込み)
- Controller での `MindstockSession` 消費(`when` 網羅・`Registered.residentId` を Service へ)
