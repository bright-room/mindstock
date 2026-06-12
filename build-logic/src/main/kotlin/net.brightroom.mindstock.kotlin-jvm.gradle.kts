import net.brightroom.mindstock.gradle.IntegrationTestDbLock

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("net.brightroom.mindstock.spotless")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// 統合テストが共有する単一 test DB(mindstock_test)へのアクセスを直列化する共有 BuildService。
// registerIfAbsent は名前でビルド全体に 1 インスタンスを共有するため、全モジュールの integrationTest が
// 同じロックを参照する。maxParallelUsages=1 でモジュール横断の同時実行を抑止する(IntegrationTestDbLock 参照)。
val integrationTestDbLock =
    gradle.sharedServices.registerIfAbsent("integrationTestDbLock", IntegrationTestDbLock::class.java) {
        maxParallelUsages.set(1)
    }

// 通常 test は integration/manual タグを除外(全 JVM モジュール共通)。
// -Dkotest.tags.exclude= (空文字) で全件実行に上書きできる。
tasks.named<Test>("test") {
    systemProperty("kotest.tags.exclude", "integration | manual")
}

// @Tags("integration") のみを実行する統合テストタスク(core / api 共通の受け皿)。
// 外部依存(DB / Garage)に当てるためキャッシュさせず毎回実行する。
// 環境変数は System.getenv ではなく providers.environmentVariable で読み、Gradle が build input として
// 追跡できるようにする(未追跡読みによる configuration cache の不要な無効化を避ける)。
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against live external dependencies (local mindstock_test DB / STORAGE_*)."
    doNotTrackState("integration tests run against live external dependencies")
    // 共有 test DB(mindstock_test)へのアクセスをモジュール横断で直列化する(全表 TRUNCATE の相互汚染防止)。
    usesService(integrationTestDbLock)
    val testSourceSet = sourceSets["test"]
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    systemProperty("kotest.tags.include", "integration")
    systemProperty("kotest.tags.exclude", "manual")
    // storage は app と同じ external.storage.*(STORAGE_*)の env を読むため、設定時のみ test JVM へ転送する。
    // DB は TestDatabase の定数(固定フィクスチャ mindstock_test)に当たるので転送する env はない。
    listOf("STORAGE_ENDPOINT", "STORAGE_REGION", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY")
        .forEach { key ->
            providers.environmentVariable(key).orNull?.let { environment(key, it) }
        }
}
