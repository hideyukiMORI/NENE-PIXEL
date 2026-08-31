package io.github.hideyukimori.nenepixel.buildlogic

internal data class DeclaredModuleDependency(
    val source: String,
    val configuration: String,
    val target: String,
)

internal data class DeclaredExternalDependency(
    val source: String,
    val configuration: String,
    val group: String,
    val name: String,
)

internal class ModuleArchitectureValidator(
    private val modulePaths: Set<String>,
    private val moduleDependencies: List<DeclaredModuleDependency>,
    private val externalDependencies: List<DeclaredExternalDependency>,
) {
    fun validate(): List<ArchitectureViolation> =
        buildList {
            addAll(validateModuleNames())
            addAll(validateDependencyDirections())
            addAll(validateCycles())
            addAll(validateCorePlatformDependencies())
            addAll(validateDomainProductionDependencies())
        }.sorted()

    private fun validateModuleNames(): List<ArchitectureViolation> =
        modulePaths.filterNot(knownModules::contains).map { module ->
            ArchitectureViolation(module, "ARC-002 prohibits unapproved Gradle module '$module'.")
        }

    private fun validateDependencyDirections(): List<ArchitectureViolation> =
        moduleDependencies.mapNotNull { dependency ->
            when {
                dependency.source !in knownModules || dependency.target !in knownModules -> {
                    null
                }

                dependency.source == dependency.target -> {
                    null
                }

                dependency.isArchitectureToolingDependency() -> {
                    null
                }

                dependency.target in allowedDependencies.getValue(dependency.source) -> {
                    null
                }

                else -> {
                    ArchitectureViolation(
                        dependency.source,
                        "ARC-002 prohibits ${dependency.configuration} dependency on '${dependency.target}'.",
                    )
                }
            }
        }

    private fun validateCycles(): List<ArchitectureViolation> {
        val productionEdges =
            moduleDependencies
                .filterNot { dependency -> dependency.source == dependency.target }
                .filterNot { dependency -> dependency.isArchitectureToolingDependency() }
                .groupBy(DeclaredModuleDependency::source)
                .mapValues { (_, dependencies) -> dependencies.map(DeclaredModuleDependency::target).toSet() }
        return CycleDetector(productionEdges).findCycles().map { cycle ->
            ArchitectureViolation(cycle.first(), "ARC-002 prohibits module cycle: ${cycle.joinToString(" -> ")}")
        }
    }

    private fun validateCorePlatformDependencies(): List<ArchitectureViolation> =
        externalDependencies
            .filter { dependency -> dependency.source.startsWith(":core:") && dependency.isPlatformDependency() }
            .map { dependency ->
                ArchitectureViolation(
                    dependency.source,
                    "ARC-003 prohibits ${dependency.configuration} dependency on " +
                        "'${dependency.group}:${dependency.name}'.",
                )
            }

    private fun validateDomainProductionDependencies(): List<ArchitectureViolation> =
        externalDependencies
            .filter { dependency -> dependency.source == DOMAIN_MODULE }
            .filter { dependency -> dependency.isProductionDependency() }
            .filterNot { dependency -> dependency.isKotlinStandardLibrary() }
            .map { dependency ->
                ArchitectureViolation(
                    dependency.source,
                    "ARC-003 permits only Kotlin standard library production dependencies; " +
                        "found ${dependency.configuration} dependency on '${dependency.group}:${dependency.name}'.",
                )
            }

    private fun DeclaredModuleDependency.isArchitectureToolingDependency(): Boolean =
        configuration == "detektPlugins" && target == ARCHITECTURE_RULES_MODULE

    private fun DeclaredExternalDependency.isPlatformDependency(): Boolean =
        platformGroupPrefixes.any(group::startsWith) || name.endsWith("-android")

    private fun DeclaredExternalDependency.isProductionDependency(): Boolean =
        configuration in productionDependencyConfigurations

    private fun DeclaredExternalDependency.isKotlinStandardLibrary(): Boolean =
        group == KOTLIN_GROUP && name in kotlinStandardLibraryModules

    private companion object {
        const val ARCHITECTURE_RULES_MODULE = ":quality:architecture-rules"
        const val DOMAIN_MODULE = ":core:domain"
        const val KOTLIN_GROUP = "org.jetbrains.kotlin"

        val knownModules =
            setOf(
                ":",
                ":adapters",
                ":app",
                ":app:android",
                ":core",
                ":presentation",
                ":presentation:compose",
                ":core:application",
                DOMAIN_MODULE,
                ":core:pixel-engine",
                ":core:project-format",
                ":adapters:persistence",
                ":adapters:automation",
                ":quality",
                ARCHITECTURE_RULES_MODULE,
            )

        val containerModules = setOf(":", ":adapters", ":app", ":core", ":presentation", ":quality")
        val productionModules = knownModules - containerModules - ARCHITECTURE_RULES_MODULE
        val allowedDependencies =
            mapOf(
                ":" to emptySet(),
                ":adapters" to emptySet(),
                ":app" to emptySet(),
                ":app:android" to productionModules - ":app:android",
                ":core" to emptySet(),
                ":presentation" to emptySet(),
                ":presentation:compose" to setOf(":core:application", ":core:domain"),
                ":core:application" to setOf(":core:domain", ":core:pixel-engine"),
                DOMAIN_MODULE to emptySet(),
                ":core:pixel-engine" to setOf(":core:domain"),
                ":core:project-format" to setOf(":core:domain"),
                ":adapters:persistence" to setOf(":core:application", ":core:project-format"),
                ":adapters:automation" to setOf(":core:application"),
                ":quality" to emptySet(),
                ARCHITECTURE_RULES_MODULE to emptySet(),
            )

        val platformGroupPrefixes =
            setOf(
                "androidx.",
                "com.android.",
                "com.google.android.",
                "org.jetbrains.compose",
            )

        val productionDependencyConfigurations = setOf("api", "compileOnly", "implementation", "runtimeOnly")
        val kotlinStandardLibraryModules =
            setOf("kotlin-stdlib", "kotlin-stdlib-common", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8")
    }
}

private class CycleDetector(
    private val edges: Map<String, Set<String>>,
) {
    private val completed = mutableSetOf<String>()
    private val visiting = mutableListOf<String>()
    private val cycles = mutableSetOf<List<String>>()

    fun findCycles(): List<List<String>> {
        edges.keys.sorted().forEach(::visit)
        return cycles.sortedBy { cycle -> cycle.joinToString() }
    }

    private fun visit(module: String) {
        if (module in completed) return
        val cycleStart = visiting.indexOf(module)
        if (cycleStart >= 0) {
            cycles.add(canonicalize(visiting.drop(cycleStart) + module))
            return
        }
        visiting.add(module)
        edges[module].orEmpty().sorted().forEach(::visit)
        visiting.removeAt(visiting.lastIndex)
        completed.add(module)
    }

    private fun canonicalize(closedCycle: List<String>): List<String> {
        val openCycle = closedCycle.dropLast(1)
        val start = openCycle.indices.minBy { index -> openCycle[index] }
        val rotated = openCycle.drop(start) + openCycle.take(start)
        return rotated + rotated.first()
    }
}
