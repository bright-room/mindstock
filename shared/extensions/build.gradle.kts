plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
        }

        jvmMain.dependencies {}
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }

        wasmJsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        wasmJsTest.dependencies {}
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
