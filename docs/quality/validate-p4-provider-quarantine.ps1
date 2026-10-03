param([Parameter(Mandatory)][string] $OutputDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) { throw 'A new evidence directory is required' }
New-Item -ItemType Directory -Path $root | Out-Null
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$source = Join-Path $repo 'app/android/src/androidTest/java/io/github/hideyukimori/nenepixel/acceptance/AcceptanceDocumentFiles.java'
$harness = @'
import io.github.hideyukimori.nenepixel.acceptance.AcceptanceDocumentFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public final class ProviderQuarantineContract {
    private static int assertions;
    private static void require(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Provider quarantine assertion " + assertions);
    }
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        Files.createDirectory(root.toPath());
        File source = AcceptanceDocumentFiles.document(root, "i89-145-partial.nenepixel");
        byte[] bytes = new byte[] {0, 13, 10, (byte)255, 42};
        Files.write(source.toPath(), bytes);
        AcceptanceDocumentFiles.delete(root, source.getName());
        File archive = new File(root, "p4-layer-provider-quarantine/" + source.getName());
        require(!source.exists());
        require(Arrays.equals(bytes, Files.readAllBytes(archive.toPath())));
        require(AcceptanceDocumentFiles.directory(root).list().length == 0);
        Files.write(source.toPath(), new byte[] {7, 8});
        try { AcceptanceDocumentFiles.delete(root, source.getName()); throw new AssertionError("collision accepted"); }
        catch (IOException expected) { assertions++; }
        require(Arrays.equals(new byte[] {7, 8}, Files.readAllBytes(source.toPath())));
        require(Arrays.equals(bytes, Files.readAllBytes(archive.toPath())));
        File empty = AcceptanceDocumentFiles.document(root, "i89-145-empty.png");
        require(empty.createNewFile());
        AcceptanceDocumentFiles.delete(root, empty.getName());
        require(new File(root, "p4-layer-provider-quarantine/" + empty.getName()).isFile());
        require(new File(root, "p4-layer-provider-quarantine/" + empty.getName()).length() == 0);
        require(!empty.exists());
        File legacy = AcceptanceDocumentFiles.document(root, "i89-prior-saved.nenepixel");
        Files.write(legacy.toPath(), bytes);
        AcceptanceDocumentFiles.delete(root, legacy.getName());
        require(!legacy.exists());
        require(!new File(root, "p4-layer-provider-quarantine/" + legacy.getName()).exists());
        try { AcceptanceDocumentFiles.delete(root, "i89-145-missing.png"); throw new AssertionError("missing accepted"); }
        catch (IOException expected) { assertions++; }
        for (String name : new String[] {null, "../escape", "i89-145-../escape", "i89-BAD", "i89-"}) {
            try { AcceptanceDocumentFiles.document(root, name); throw new AssertionError("name accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        System.out.println("PASS " + assertions + " provider filesystem assertions; all fixtures retained");
    }
}
'@
$harnessPath = Join-Path $root 'ProviderQuarantineContract.java'
[IO.File]::WriteAllText($harnessPath, $harness, [Text.UTF8Encoding]::new($false))
$classes = Join-Path $root 'classes'
New-Item -ItemType Directory -Path $classes | Out-Null
& javac -d $classes $source $harnessPath
if ($LASTEXITCODE -ne 0) { throw 'Provider host compilation failed' }
& java -cp $classes ProviderQuarantineContract (Join-Path $root 'private-files')
if ($LASTEXITCODE -ne 0) { throw 'Provider filesystem contract failed' }
