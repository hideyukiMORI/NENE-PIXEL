package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name

internal object RepositoryFileTraversal {
    fun regularFiles(repositoryRoot: Path): List<Path> =
        buildList {
            Files.walkFileTree(
                repositoryRoot,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult =
                        if (directory.name in ignoredDirectoryNames) {
                            FileVisitResult.SKIP_SUBTREE
                        } else {
                            FileVisitResult.CONTINUE
                        }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (Files.isRegularFile(file)) add(file)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }

    private val ignoredDirectoryNames: Set<String> =
        setOf(".git", ".gradle", ".idea", ".kotlin", "build")
}
