package io.github.hideyukimori.nenepixel.core.application.persistence

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PersistencePortsContractTest {
    @Test
    fun `capability bindings retain each injected port without another instance`() {
        val fixture = Fixture()
        val project = ProjectPersistencePorts(fixture.storage, fixture.recovery)
        val png = PngPersistencePorts(fixture.exporter, fixture.pngImports)
        val palette = PalettePersistencePorts(fixture.paletteExporter, fixture.paletteImporter)
        val underlay = UnderlayPersistencePorts(fixture.referenceImages, fixture.underlayMemory)
        val ports = PersistencePorts(project, png, palette, underlay)

        assertSame(project, ports.project)
        assertSame(png, ports.png)
        assertSame(palette, ports.palette)
        assertSame(underlay, ports.underlay)
        assertSame(fixture.storage, ports.projectStorage)
        assertSame(fixture.recovery, ports.recoveryRecord)
        assertSame(fixture.exporter, ports.pngExport)
        assertSame(fixture.pngImports, ports.pngImport)
        assertSame(fixture.paletteExporter, ports.paletteJsonExport)
        assertSame(fixture.paletteImporter, ports.paletteJsonImport)
        assertSame(fixture.referenceImages, ports.referenceImage)
        assertSame(fixture.underlayMemory, ports.underlayMemory)
    }
}
