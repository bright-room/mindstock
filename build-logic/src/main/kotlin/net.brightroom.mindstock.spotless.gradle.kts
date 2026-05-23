import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless")
}

extensions.configure<SpotlessExtension> {
    val ktlintEditorConfigOverrides = mapOf(
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        "ktlint_standard_property-naming" to "disabled",
    )

    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint().editorConfigOverride(ktlintEditorConfigOverrides)
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintEditorConfigOverrides)
    }
}
