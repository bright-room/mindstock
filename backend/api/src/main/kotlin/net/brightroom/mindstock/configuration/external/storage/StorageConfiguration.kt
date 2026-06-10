package net.brightroom.mindstock.configuration.external.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking

data class StorageBucket(
    val name: String,
)

fun Application.storageConfigure(
    @Property("external.storage") properties: StorageProperties,
) {
    val s3 =
        S3Client {
            region = properties.region
            endpointUrl = Url.parse(properties.endpoint)
            forcePathStyle = true
            credentialsProvider =
                StaticCredentialsProvider {
                    accessKeyId = properties.accessKey
                    secretAccessKey = properties.secretKey
                }
        }

    monitor.subscribe(ApplicationStopped) {
        runBlocking { s3.close() }
    }

    dependencies {
        provide<S3Client> { s3 }
        provide<StorageBucket> { StorageBucket(properties.bucket) }
    }
}
