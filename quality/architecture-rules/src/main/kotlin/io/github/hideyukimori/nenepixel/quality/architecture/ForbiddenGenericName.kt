package io.github.hideyukimori.nenepixel.quality.architecture

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

public class ForbiddenGenericName(
    config: Config,
) : Rule(
        config,
        "Rejects generic type and package names prohibited by KOT-011.",
    ) {
    override fun visitKtFile(file: KtFile) {
        val forbiddenSegment =
            file.packageFqName
                .pathSegments()
                .map { it.asString() }
                .firstOrNull(forbiddenPackageSegments::contains)
        if (forbiddenSegment != null) {
            report(
                Finding(
                    Entity.atPackageOrFirstDecl(file),
                    "KOT-011 prohibits package segment '$forbiddenSegment'.",
                ),
            )
        }
        super.visitKtFile(file)
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        val name = classOrObject.name
        if (name != null && forbiddenTypeSuffixes.any(name::endsWith)) {
            report(
                Finding(
                    Entity.atName(classOrObject),
                    "KOT-011 prohibits generic type name '$name'.",
                ),
            )
        }
        super.visitClassOrObject(classOrObject)
    }

    private companion object {
        val forbiddenTypeSuffixes = setOf("Manager", "Helper", "Util", "Utils", "Common")
        val forbiddenPackageSegments = setOf("utils", "helpers", "managers", "misc")
    }
}
