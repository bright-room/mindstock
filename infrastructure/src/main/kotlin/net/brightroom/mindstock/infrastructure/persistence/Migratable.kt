package net.brightroom.mindstock.infrastructure.persistence

/**
 * Marks an Exposed [org.jetbrains.exposed.v1.core.Table] as a target for
 * migration script generation. The generator iterates [MigratableTables.all]
 * (which lists each annotated table explicitly) to keep classpath scanning
 * out of the runtime.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Migratable
