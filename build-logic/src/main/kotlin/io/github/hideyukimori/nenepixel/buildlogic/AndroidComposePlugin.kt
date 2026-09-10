package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

public class AndroidComposePlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("org.jetbrains.kotlin.plugin.compose")
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        target.configureAndroidQuality(libraries)

        target.pluginManager.withPlugin("com.android.application") {
            target.configureAndroidConvention(composeEnabled = true, explicitApiEnabled = false)
        }
        target.pluginManager.withPlugin("com.android.library") {
            target.configureAndroidConvention(composeEnabled = true, explicitApiEnabled = true)
        }
    }
}
