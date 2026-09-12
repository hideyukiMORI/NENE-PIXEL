package io.github.hideyukimori.nenepixel.acceptance;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Test-APK process fixture. Framework/Java only: Kotlin is deduplicated into the target APK. */
public final class AcceptanceDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "io.github.hideyukimori.nenepixel.test.acceptance.documents";
    public static final String ROOT_TITLE = "NENE-PIXEL Acceptance";
    private static final String ROOT_ID = "root";
    private static final String[] ROOT_COLUMNS = {
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
        Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES
    };
    private static final String[] DOCUMENT_COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
    };

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            .add(Root.COLUMN_TITLE, ROOT_TITLE)
            .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY)
            .add(Root.COLUMN_MIME_TYPES, "application/octet-stream\nimage/png");
        return cursor;
    }

    @Override public Cursor queryDocument(String id, String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        addDocument(cursor, id);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
        requireRoot(parent);
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        File[] files = directory().listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) addDocument(cursor, file.getName());
        }
        return cursor;
    }

    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        requireRoot(parent);
        if (!"application/octet-stream".equals(mime) && !"image/png".equals(mime)) {
            throw new IllegalArgumentException("Unexpected fixture MIME type");
        }
        try {
            if (!documentFile(name).createNewFile()) throw new FileNotFoundException("Fixture already exists");
        } catch (IOException failure) {
            throw new FileNotFoundException(failure.getMessage());
        }
        return name;
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(documentFile(id), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (!documentFile(id).delete()) throw new FileNotFoundException("Fixture was not deleted");
    }

    private File directory() {
        File directory = new File(getContext().getFilesDir(), "m3-acceptance");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create fixture directory");
        }
        return directory;
    }

    private File documentFile(String id) {
        if (id == null || !id.matches("i89-[a-z0-9.-]{1,90}")) {
            throw new IllegalArgumentException("Invalid fixture name");
        }
        return new File(directory(), id);
    }

    private void requireRoot(String id) {
        if (!ROOT_ID.equals(id)) throw new IllegalArgumentException("Invalid fixture root");
    }

    private void addDocument(MatrixCursor cursor, String id) {
        boolean root = ROOT_ID.equals(id);
        File file = root ? directory() : documentFile(id);
        String mime = root ? Document.MIME_TYPE_DIR : id.endsWith(".png") ? "image/png" : "application/octet-stream";
        int flags = root ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE;
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, root ? ROOT_TITLE : id)
            .add(Document.COLUMN_MIME_TYPE, mime)
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, root ? 0L : file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }
}
