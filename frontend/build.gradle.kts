plugins {
    id("net.brightroom.mindstock.compose-web")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(projects.shared.rpc)
            implementation(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.material.icons.extended)
            implementation(libs.compose.adaptive)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.material3.adaptive.navigation.suite)

            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.rpc.client)
            implementation(libs.kotlinx.rpc.client.ktor)
            implementation(libs.kotlinx.rpc.serialization.json)
            implementation(ktorLib.client.core)
            implementation(ktorLib.client.contentNegotiation)
            implementation(ktorLib.serialization.kotlinx.json)

            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(ktorLib.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

val generateAuthConfig =
    tasks.register("generateAuthConfig") {
        val outDir = layout.buildDirectory.dir("generated/auth")
        outputs.dir(outDir)
        val issuer = providers.environmentVariable("AUTH_ISSUER").orElse("http://localhost:8081")
        val redirectUri = providers.environmentVariable("AUTH_REDIRECT_URI").orElse("http://localhost:8080/auth/callback")
        val postLogout = providers.environmentVariable("AUTH_POST_LOGOUT_REDIRECT_URI").orElse("http://localhost:8080/")
        val clientId = providers.environmentVariable("AUTH_CLIENT_ID")
        val audience = providers.environmentVariable("AUTH_AUDIENCE")
        val projectId = providers.environmentVariable("AUTH_PROJECT_ID")
        inputs.property("issuer", issuer)
        inputs.property("redirectUri", redirectUri)
        inputs.property("postLogout", postLogout)
        inputs.property("clientId", clientId)
        inputs.property("audience", audience)
        inputs.property("projectId", projectId)
        doLast {
            val out = outDir.get().asFile.resolve("net/brightroom/mindstock/frontend/auth/AuthConfig.kt")
            out.parentFile.mkdirs()
            out.writeText(
                """
                package net.brightroom.mindstock.frontend.auth

                object AuthConfig {
                    const val ISSUER = "${issuer.get()}"
                    const val CLIENT_ID = "${clientId.get()}"
                    const val REDIRECT_URI = "${redirectUri.get()}"
                    const val POST_LOGOUT_REDIRECT_URI = "${postLogout.get()}"
                    const val AUDIENCE = "${audience.get()}"
                    const val PROJECT_ID = "${projectId.get()}"
                }
                """.trimIndent() + "\n",
            )
        }
    }
kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateAuthConfig)
}
