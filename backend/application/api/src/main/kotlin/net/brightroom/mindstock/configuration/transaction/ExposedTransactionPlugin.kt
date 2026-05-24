package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Exposed transaction 境界を将来提供するための Ktor plugin **スケルトン**。
 *
 * **Plan 4 時点では transaction 開始は未実装**。`onCall` で `Database` を
 * `call.attributes` に置くだけで、`transaction {}` は張らない。利用側は本 plugin
 * 経由で active transaction が得られると仮定してはならない。
 *
 * 最終形は「1 RPC 呼び出し = 1 Exposed transaction を境界で開閉する」。
 * Handler / Repository 実装が `transaction {}` を書かずに済むよう、本 plugin が
 * transaction を張り、`TransactionManager.currentOrNull()` 経由で拾う設計
 * (詳細: docs/superpowers/specs/2026-05-24-usecase-design.md §4)。
 *
 * Transaction 開始ロジックと `install(...)` 登録は **Plan 6** で kotlinx-rpc
 * サービス配線と同時に追加する。Plan 4 段階で install しても、Repository 実装が
 * 無いため Handler を呼ぶエンドポイント自体が存在せず動作確認できないため。
 */
val ExposedTransactionPlugin =
    createApplicationPlugin(
        name = "ExposedTransaction",
        createConfiguration = ::ExposedTransactionConfig,
    ) {
        val database =
            pluginConfig.database
                ?: error("ExposedTransactionPlugin requires `database` to be set in configuration")

        onCall { call ->
            call.attributes.put(DatabaseAttributeKey, database)
        }

        // TODO(Plan 6): ここで RPC 呼び出しを `transaction(database) { ... }` で囲む。
        // 実装フック点(call interceptor / rpc service decorator のいずれか)は Plan 6 で確定。
        // 現状は skeleton で、transaction は張られない。
    }

class ExposedTransactionConfig {
    var database: Database? = null
}

internal val DatabaseAttributeKey = AttributeKey<Database>("ExposedTransactionDatabase")
