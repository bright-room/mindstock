package net.brightroom.mindstock.extensions.kotlinx.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * JSON format for HTTP ContentNegotiation and general serialization.
 *
 * Uses [ClassDiscriminatorMode.NONE] to keep API payloads clean of a `type` discriminator field.
 * For kRPC server / client wiring, use [KrpcJson] instead — kRPC's internal `KrpcMessage`
 * protocol relies on the polymorphic class discriminator for message dispatch.
 */
@OptIn(ExperimentalSerializationApi::class)
val CustomJson =
    Json {
        prettyPrint = false
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
 *
 * [namingStrategy] is explicitly reset to `null` to prevent [JsonNamingStrategy.SnakeCase]
 * (inherited from [CustomJson]) from being applied to kRPC's internal `KrpcMessage` types.
 */
@OptIn(ExperimentalSerializationApi::class)
val KrpcJson =
    Json(from = CustomJson) {
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
        namingStrategy = null
    }
