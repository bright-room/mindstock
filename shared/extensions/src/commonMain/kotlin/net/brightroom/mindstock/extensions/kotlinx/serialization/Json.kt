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
