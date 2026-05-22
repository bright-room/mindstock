import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    js(IR) {
        browser()
        binaries.library()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
    }

    sourceSets {
        val webMain by creating {
            dependsOn(commonMain.get())
        }
        jsMain {
            dependsOn(webMain)
        }
        wasmJsMain {
            dependsOn(webMain)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
