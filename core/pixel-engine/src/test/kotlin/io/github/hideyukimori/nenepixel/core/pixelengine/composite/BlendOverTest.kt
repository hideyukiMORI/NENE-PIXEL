package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlendOverTest {
    @Test
    fun `alpha zero source keeps the destination`() {
        assertEquals(0xC8643280.toInt(), blendOver(0xC8643280.toInt(), 0x09090900))
    }

    @Test
    fun `opaque source replaces the destination`() {
        assertEquals(0x010203FF, blendOver(0x0A141E28, 0x010203FF))
    }

    @Test
    fun `partial source over partial destination rounds to the golden vector`() {
        // Sa=128 (100,0,255) over Da=128 (200,50,255): N=48896, outA=192, R=133, G=17, B=255.
        assertEquals(0x8511FFC0.toInt(), blendOver(0xC832FF80.toInt(), 0x6400FF80))
    }

    @Test
    fun `partial source over opaque destination reaches exact halves`() {
        // Sa=128 (255,0,0) over (0,255,0,255): N=65025, outA=255, R=128, G=127, B=0.
        assertEquals(0x807F00FF.toInt(), blendOver(0x00FF00FF, 0xFF000080.toInt()))
    }

    @Test
    fun `partial source over transparent destination keeps the source color`() {
        // Da=0: N=Sa*255, so outA=Sa and outC=Sc.
        assertEquals(0x7B2D4364, blendOver(0x32404600, 0x7B2D4364))
    }

    @Test
    fun `saturated partial alpha does not overflow`() {
        assertEquals(-1, blendOver(-1, 0xFFFFFFFE.toInt()))
        assertEquals(-1, blendOver(-1, 0xFFFFFF01.toInt()))
        assertEquals(-1, blendOver(0xFFFFFFFE.toInt(), 0xFFFFFFFE.toInt()))
    }
}
