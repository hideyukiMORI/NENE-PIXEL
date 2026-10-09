package io.github.hideyukimori.nenepixel.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import dev.detekt.gradle.Detekt
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named

internal fun Project.configureAndroidDetekt() {
    extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
        afterEvaluate {
            variant.components.forEach { component ->
                val suffix = component.name.replaceFirstChar(Char::uppercaseChar)
                tasks.named<Detekt>("detekt$suffix").configure {
                    // Append after detekt's task registration has established the Kotlin classpath convention.
                    classpath.from(
                        tasks.named<JavaCompile>("compile${suffix}JavaWithJavac").flatMap { it.destinationDirectory },
                    )
                }
            }
        }
    }
}
