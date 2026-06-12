plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    alias(libs.plugins.exposed.migration)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.shared)
    implementation(projects.domain)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hikari)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.aws.sdk.kotlin.s3)

    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.postgres.jdbc)
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.database.postgresql)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(ktorLib.client.cio)
}

tasks.test {
    // Exclude "integration" and "manual" tagged specs by default.
    // Override on the command line with -Dkotest.tags.exclude= (empty string) to run all.
    systemProperty("kotest.tags.exclude", "integration | manual")
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against the Garage storage (STORAGE_* / application.yaml デフォルト)."
    // 外部ストレージに当てる統合テストはキャッシュさせず毎回実行する(stale 結果防止)。
    doNotTrackState("integration tests run against a live external storage")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    systemProperty("kotest.tags.include", "integration")
    systemProperty("kotest.tags.exclude", "manual")
    // app と同一の STORAGE_* env(external.storage.* / application.yaml デフォルト)を test JVM へ転送する。
    listOf("STORAGE_ENDPOINT", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
    // DataSource(DB)統合テスト用の TEST_DB_* も転送する(未設定なら TestDatabase が既定値へフォールバック)。
    listOf("TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD")
        .forEach { key -> System.getenv(key)?.let { environment(key, it) } }
}

exposed {
    migrations {
        tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource.schemas")
        fileDirectory.set(
            layout.projectDirectory.dir("src/main/resources/db/migration").asFile,
        )
        testContainersImageName.set("postgres:18.4-alpine")
    }
}
