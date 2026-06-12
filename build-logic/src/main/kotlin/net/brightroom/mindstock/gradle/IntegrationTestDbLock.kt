package net.brightroom.mindstock.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * 統合テストが共有する単一の test DB(mindstock_test)へのアクセスを直列化するためのマーカー BuildService。
 *
 * 各モジュールの `integrationTest` タスクが `usesService(...)` で参照し、`maxParallelUsages = 1` により
 * **モジュール横断で同時に走らない**ことを保証する。`TestDatabase.clean()` が全テーブルを TRUNCATE するため、
 * `org.gradle.parallel=true` 下で `:backend:core:integrationTest` と `:backend:api:integrationTest` が
 * 同一 DB に対して並列実行されると相互汚染して flaky になる。これを Gradle 標準の共有リソース機構で防ぐ。
 */
abstract class IntegrationTestDbLock : BuildService<BuildServiceParameters.None>
