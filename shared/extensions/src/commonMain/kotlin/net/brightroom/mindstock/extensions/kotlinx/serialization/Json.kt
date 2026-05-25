package net.brightroom.mindstock.extensions.kotlinx.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
val CustomJson =
    Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

/**
 * kRPC-specific [Json] format.
 *
 * Mirrors [CustomJson] for API payload shape, but restores the polymorphic class discriminator
 * because kRPC's internal `KrpcMessage` protocol relies on it for message dispatch.
 * Using [ClassDiscriminatorMode.NONE] (as [CustomJson] does) breaks kRPC decoding.
 */
@OptIn(ExperimentalSerializationApi::class)
val KrpcJson =
    Json(from = CustomJson) {
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
    }
