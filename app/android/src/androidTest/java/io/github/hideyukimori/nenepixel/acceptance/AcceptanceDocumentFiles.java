package io.github.hideyukimori.nenepixel.acceptance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Test-provider storage policy. Java/JDK only so the exact file boundary can be host-verified. */
public final class AcceptanceDocumentFiles {
    private AcceptanceDocumentFiles() {}

    public static File directory(File privateFiles) {
        File directory = new File(privateFiles, "m3-acceptance");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create fixture directory");
        }
        return directory;
    }

    public static File document(File privateFiles, String id) {
        if (id == null || !id.matches("i89-[a-z0-9.-]{1,90}")) {
            throw new IllegalArgumentException("Invalid fixture name");
        }
        return new File(directory(privateFiles), id);
    }

    public static synchronized void delete(File privateFiles, String id) throws IOException {
        File source = document(privateFiles, id);
        if (!id.startsWith("i89-145-")) {
            if (!source.delete()) throw new FileNotFoundException("Fixture was not deleted");
            return;
        }
        if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new FileNotFoundException("Phase fixture is not a regular file");
        }
        File archive = new File(privateFiles, "p4-layer-provider-quarantine");
        if (Files.isSymbolicLink(archive.toPath())) throw new IOException("Linked phase quarantine");
        Files.createDirectories(archive.toPath());
        // No REPLACE_EXISTING: preserve both files on every collision, including empty outputs.
        Files.move(source.toPath(), new File(archive, id).toPath());
    }
}
