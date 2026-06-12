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
    description = "Runs @Tags(\"integration\") specs against live external dependencies (TEST_DB_* / STORAGE_*)."
    doNotTrackState("integration tests run against live external dependencies")
    val testSourceSet = sourceSets["test"]
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    systemProperty("kotest.tags.include", "integration")
    systemProperty("kotest.tags.exclude", "manual")
    listOf(
        "TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD",
        "STORAGE_ENDPOINT", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY",
    ).forEach { key ->
        providers.environmentVariable(key).orNull?.let { environment(key, it) }
    }
}
