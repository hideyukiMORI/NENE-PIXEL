package io.github.hideyukimori.nenepixel.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

public class AndroidLibraryPlugin : Plugin<Project> {
    public override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("dev.detekt")
            apply("org.jlleitschuh.gradle.ktlint")
        }

        val libraries = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        target.configureAndroidQuality(libraries)
        target.pluginManager.withPlugin("com.android.library") {
            target.configureAndroidConvention(composeEnabled = false, explicitApiEnabled = true)
        }
    }
}
