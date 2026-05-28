package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * 1 RPC / HTTP 呼び出し = 1 Exposed transaction を境界で開閉する Ktor plugin。
 *
 * Handler / Repository 実装は `transaction {}` を書かず、本 plugin が張った transaction を
 * `TransactionManager.currentOrNull()` 経由で拾う前提。
 *
 * 詳細設計: docs/superpowers/specs/2026-05-24-usecase-design.md §4、
 * 実装方針:    docs/superpowers/specs/2026-05-24-repository-implementation-design.md §6
 *
 * `newSuspendedTransaction` は Exposed v1 で deprecated だが、後継 `suspendTransaction()`
 * は exposed-r2dbc 専用。本プロジェクトは JDBC を継続使用するため代替が無く、JetBrains の
 * 公式メッセージ(YouTrack EXPOSED-74)でも JDBC 用途は use case を残すと明言されている。
 * Ktor の suspend pipeline で transaction context を伝播するには本 API が必要。
 * Plan 6 以降で R2DBC 移行を検討するまでは @Suppress で抑制する。
 */
@Suppress("DEPRECATION")
val ExposedTransactionPlugin =
    createApplicationPlugin(
        name = "ExposedTransaction",
        createConfiguration = ::ExposedTransactionConfig,
    ) {
        val database =
            pluginConfig.database
                ?: error("ExposedTransactionPlugin requires `database` to be set in configuration")

        application.intercept(ApplicationCallPipeline.Call) {
            val pipelineContext = this
            newSuspendedTransaction(db = database) {
                pipelineContext.proceed()
            }
        }
    }

class ExposedTransactionConfig {
    var database: Database? = null
}
