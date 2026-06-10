package net.brightroom.mindstock.configuration.external.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking

data class StorageBucket(
    val name: String,
)

fun Application.storageConfigure() {
    val cfg = environment.config.config("external.storage")
    val endpoint = cfg.property("endpoint").getString()
    val region = cfg.property("region").getString()
    val bucket = cfg.property("bucket").getString()
    val accessKey = cfg.property("access-key").getString()
    val secretKey = cfg.property("secret-key").getString()

    val s3 =
        S3Client {
            this.region = region
            endpointUrl = Url.parse(endpoint)
            forcePathStyle = true
            credentialsProvider =
                StaticCredentialsProvider {
                    accessKeyId = accessKey
                    secretAccessKey = secretKey
                }
        }

    monitor.subscribe(ApplicationStopped) {
        runBlocking { s3.close() }
    }

    dependencies {
        provide<S3Client> { s3 }
        provide<StorageBucket> { StorageBucket(bucket) }
    }
}
