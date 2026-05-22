// Aggregation only. All module config goes through build-logic conventions.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
