package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import kotlinx.rpc.krpc.ktor.server.KrpcRoute

/**
 * kotlinx-rpc 0.10.2 の Ktor サーバ統合における "current ApplicationCall" 解決。
 *
 * ## API 調査結果 (kotlinx-rpc-krpc-ktor-server 0.10.2)
 *
 * - `Route.rpc(path) { builder: suspend KrpcRoute.() -> Unit }` は内部で
 *   `webSocket { KrpcRoute(this).apply { builder() } }` を実行する
 *   (`kotlinx/rpc/krpc/ktor/server/KtorServerDsl.kt`)。
 * - `KrpcRoute` は `DefaultWebSocketServerSession` を実装し、
 *   `WebSocketServerSession.call: ApplicationCall` を公開する
 *   (`kotlinx/rpc/krpc/ktor/server/KrpcRoute.kt` および
 *   `io/ktor/server/websocket/WebSocketServerSession`)。
 * - サービス登録は `registerService<T> { () -> T }` という *引数なし* の factory:
 *   ```
 *   public inline fun <@Rpc reified Service : Any> registerService(
 *       noinline serviceFactory: () -> Service,
 *   )
 *   ```
 * - factory は WebSocket アップグレード時に *接続単位で 1 回* 呼ばれる
 *   (メソッド毎ではない)。 factory は `KrpcRoute` のレシーバを *closure* で
 *   キャプチャできるため、`call` をコンストラクタへ渡すことで
 *   サービス実装は自前のフィールドとして `ApplicationCall` を保持できる。
 *
 * ## カノニカル パターン
 *
 * ```
 * authenticate("stub") {
 *     rpc("/rpc") {
 *         val call = this.call // KrpcRoute is a WebSocketServerSession
 *         registerService<StockService> { StockServiceImpl(call, ...) }
 *         registerService<HouseholdService> { HouseholdServiceImpl(call, ...) }
 *         // ...
 *     }
 * }
 * ```
 *
 * Principal は WebSocket アップグレード時に確立され、その後そのソケット上で
 * 実行される全 RPC 呼び出しに対して不変。よってサービス実装は接続単位の
 * インスタンスとして `ApplicationCall` (および解決済み actor) を保持できる。
 *
 * ## このファイルが提供するもの
 *
 * `KrpcRoute.applicationCall` という極小エクステンション。
 * 呼び出しサイトで `this.call` と書く代わりに、意図 (RPC 接続の
 * 認証コンテキストを取得している) を明示化するための糖衣。
 */
val KrpcRoute.applicationCall: ApplicationCall
    get() = call
