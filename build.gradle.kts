// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

// Guardrail: the compose foundation version each module COMPILES against must equal the version
// :app actually SHIPS on its debug runtime classpath. This class of drift broke the habits forms
// in production once: the BOM pinned foundation 1.7.6 while androidx.emoji2's transitive
// constraint forced 1.9.0 onto the app runtime; modules compiled a FlowRow call against 1.7.6's
// signature and it crashed at runtime with NoSuchMethodError. The BOM bump (2025.08.01) fixed it;
// this task makes a recurrence fail CI instead of a user's phone.
//
// Scope note: only each module's COMPILE classpath is compared, because that is the axis that can
// drift silently — the app runtime is the aggregate of all module runtime classpaths, so :app's
// debugRuntimeClasspath is the authoritative "shipped" version. Debug is sufficient: foundation
// resolution is identical across debug/release (the only buildType-specific dep is ui-tooling,
// which is debugImplementation and unrelated).
tasks.register("verifyComposeFoundationAlignment") {
    group = "verification"
    description = "Fails if any module compiles against a compose foundation version that differs from what :app ships."
    // Resolves project configurations at execution time, which the configuration cache forbids.
    // If the cache is ever enabled, fail fast with this message instead of a cryptic error.
    notCompatibleWithConfigurationCache("resolves project configurations at execution time")
    doLast {
        val groupId = "androidx.compose.foundation"
        val artifactId = "foundation"

        fun foundationVersions(project: Project, configurationName: String): Set<String> {
            val config = project.configurations.findByName(configurationName) ?: return emptySet()
            return config.incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                .filter { it.group == groupId && it.module == artifactId }
                .map { it.version }
                .toSet()
        }

        val shipped = foundationVersions(rootProject.project(":app"), "debugRuntimeClasspath")
        if (shipped.size != 1) {
            throw GradleException(
                ":app debugRuntimeClasspath resolves compose foundation to $shipped — expected exactly " +
                    "one version. Something (a transitive constraint?) is producing an incoherent compose graph."
            )
        }
        val shippedVersion = shipped.single()

        val offenders = StringBuilder()
        fun audit(project: Project) {
            val compileVersions = foundationVersions(project, "debugCompileClasspath")
            // Modules without foundation on their compile classpath don't compile compose layout
            // code, so they can't drift — skip them.
            if (compileVersions.isNotEmpty() && compileVersions != setOf(shippedVersion)) {
                offenders.appendLine("  ${project.path}: compiles against foundation $compileVersions but :app ships $shippedVersion")
            }
        }

        // :app is a subproject too, so subprojects covers the app module and every library module.
        rootProject.subprojects.forEach(::audit)

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Compose foundation version drift — modules compile against a different foundation " +
                    "than :app ships (this class of mismatch crashed the habits forms with " +
                    "NoSuchMethodError on FlowRow before the 2025.08.01 BOM bump):\n$offenders"
            )
        }
        logger.lifecycle("OK: every module compiles against compose foundation $shippedVersion (matches :app runtime).")
    }
}
