package net.brightroom.mindstock.configuration.external.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CorsConfiguration
import aws.sdk.kotlin.services.s3.model.CorsRule
import aws.sdk.kotlin.services.s3.model.PutBucketCorsRequest
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

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

    monitor.subscribe(ApplicationStarted) {
        val origins =
            properties.corsAllowedOrigins
                .map { it.trim() }
                .filter { it.isNotBlank() }
        if (origins.isEmpty()) {
            log.info("bucket CORS skipped: no allowed origins configured")
            return@subscribe
        }
        try {
            runBlocking {
                withTimeout(10.seconds) {
                    s3.putBucketCors(
                        PutBucketCorsRequest {
                            bucket = properties.bucket
                            corsConfiguration =
                                CorsConfiguration {
                                    corsRules =
                                        listOf(
                                            CorsRule {
                                                allowedOrigins = origins
                                                allowedMethods = listOf("GET", "HEAD")
                                                allowedHeaders = listOf("*")
                                                maxAgeSeconds = 3000
                                            },
                                        )
                                }
                        },
                    )
                }
            }
            log.info("bucket CORS applied: origins=$origins")
        } catch (e: Exception) {
            log.warn("bucket CORS could not be applied (continuing startup): ${e.message}", e)
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
