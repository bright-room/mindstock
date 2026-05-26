package net.brightroom.mindstock.configuration.logging

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.configuration.Environment
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson
import org.slf4j.event.Level
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Application.loggingConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    if (!environment.isProduction()) {
        install(DoubleReceive) {
            excludeFromCache { call, _ ->
                call.request.headers[HttpHeaders.Upgrade]?.contains("websocket", ignoreCase = true) == true
            }
        }
    }

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate {
            Uuid.random().toString()
        }

        verify { callId: String ->
            if (callId.isEmpty()) return@verify false
            runCatching { Uuid.parse(callId) }.isSuccess
        }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")

        filter { call ->
            val path = call.request.path()
            path.startsWith("/api/v1")
        }

        format { call ->
            val remoteUrl = call.request.origin.remoteHost

            val serverHost = call.request.origin.serverHost
            val serverPort = call.request.origin.serverPort
            val serverUrl = "$serverHost${if (serverPort != 80) ":$serverPort" else ""}"

            val path = call.request.path()
            val method = call.request.httpMethod

            val headers =
                call.request.headers
                    .entries()
                    .associate { it.key to it.value }

            val queryParameters = call.request.queryParameters
            val queryParams =
                queryParameters
                    .entries()
                    .associate { it.key to it.value.first() }

            val isWebSocketUpgrade =
                call.request.headers[HttpHeaders.Upgrade]?.contains("websocket", ignoreCase = true) == true
            // WARNING: runBlocking + receiveText consumes the body stream and blocks the
            // coroutine dispatcher thread. The isWebSocketUpgrade guard above is REQUIRED —
            // attempting receiveText() on a WebSocket upgrade request hangs the connection.
            // Any future non-body request type (SSE / streaming endpoints / file uploads)
            // must extend this guard before being added to the routing.
            // TODO: replace with a proper suspend body-capture mechanism when available.
            val requestBody =
                if (environment.isProduction() || isWebSocketUpgrade) {
                    ""
                } else {
                    runBlocking { call.receiveText() }
                }

            val requestLogging =
                RequestLogging(
                    remoteUrl = remoteUrl,
                    serverUrl = serverUrl,
                    path = path,
                    method = method.value,
                    headers = headers,
                    content =
                        RequestContent(
                            params = queryParams,
                            body = requestBody,
                        ),
                )

            val status = call.response.status()
            val size = call.response.headers[HttpHeaders.ContentLength]?.toInt() ?: 0

            val responseLogging =
                ResponseLogging(
                    status = "${status?.value ?: ""} ${status?.description ?: ""}",
                    size = size,
                )

            val processingTimeMillis = call.processingTimeMillis()

            val logging =
                CallLoggingModel(
                    request = requestLogging,
                    response = responseLogging,
                    processingTimeMillis = processingTimeMillis,
                )

            val json = CustomJson
            val jsonLogging =
                json
                    .encodeToString(logging)
                    .trimIndent()

            "call logging\n$jsonLogging"
        }
    }
}

@Serializable
private data class CallLoggingModel(
    val request: RequestLogging,
    val response: ResponseLogging,
    val processingTimeMillis: Long,
)

@Serializable
private data class RequestLogging(
    val remoteUrl: String,
    val serverUrl: String,
    val path: String,
    val method: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val content: RequestContent,
)

@Serializable
private data class RequestContent(
    val params: Map<String, String> = emptyMap(),
    val body: String,
)

@Serializable
private data class ResponseLogging(
    val status: String,
    val size: Int,
)
