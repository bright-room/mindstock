package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 1 RPC 呼び出し = 1 Exposed transaction を境界で開閉する Ktor plugin。
 *
 * Handler / Repository 実装は `transaction {}` を一切書かず、本 plugin が張った
 * transaction を `TransactionManager.currentOrNull()` 経由で拾う前提。
 *
 * 注: 本 plugin は Plan 4 時点ではファイルのみ用意し、`install(...)` は Plan 6 の
 * kotlinx-rpc サービス配線と同時に行う。Plan 4 段階で install すると、Repository
 * 実装が無いため Handler を呼ぶエンドポイント自体が存在せず動作確認できない。
 *
 * 詳細設計: docs/superpowers/specs/2026-05-24-usecase-design.md §4
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

        // Plan 6 で kotlinx-rpc 配線時、ここで RPC 呼び出しを transaction { ... } で囲む。
        // 実装フック点(call interceptor / rpc service decorator のいずれか)は Plan 6 で確定。
    }

class ExposedTransactionConfig {
    var database: Database? = null
}

internal val DatabaseAttributeKey = AttributeKey<Database>("ExposedTransactionDatabase")
