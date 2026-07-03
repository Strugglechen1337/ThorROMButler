package dev.thor.rombutler.domain.detection

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.MatchSource
import org.junit.Before
import org.junit.Test

class DetectionEngineTest {

    private lateinit var engine: DetectionEngine

    @Before
    fun setUp() {
        engine = DetectionEngine(SystemRegistry())
    }

    // --- unique extensions -> CERTAIN ---

    @Test
    fun `unique extension yields certain result`() {
        val cases = mapOf(
            "Super Mario World.sfc" to "snes",
            "Pokemon Rot.gb" to "gb",
            "Pokemon Kristall.gbc" to "gbc",
            "Metroid Fusion.gba" to "gba",
            "Zelda OOT.z64" to "n64",
            "Mario Kart DS.nds" to "nds",
            "Zelda ALBW.3ds" to "n3ds",
            "Mario Kart 7.cci" to "n3ds",
            "Luigis Mansion 2.cia" to "n3ds",
            "Crisis Core.cso" to "psp",
            "Wind Waker.gcm" to "gc",
            "Xenoblade.wbfs" to "wii",
            "BotW.wua" to "wiiu",
            "Shenmue.gdi" to "dreamcast",
            "Odyssey.nsp" to "switch",
        )
        for ((fileName, expectedSystem) in cases) {
            val result = engine.detect(fileName)
            assertThat(result.system?.id).isEqualTo(expectedSystem)
            assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
            assertThat(result.source).isEqualTo(MatchSource.EXTENSION)
        }
    }

    @Test
    fun `extension matching is case insensitive`() {
        val result = engine.detect("MARIO.NES")
        assertThat(result.system?.id).isEqualTo("nes")
        assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
    }

    // --- conventions -> PROBABLE ---

    @Test
    fun `cue sheet is probable ps1`() {
        val result = engine.detect("Final Fantasy VII (Disc 1).cue")
        assertThat(result.system?.id).isEqualTo("psx")
        assertThat(result.confidence).isEqualTo(Confidence.PROBABLE)
    }

    @Test
    fun `m3u playlist is probable ps1`() {
        val result = engine.detect("Final Fantasy VII.m3u")
        assertThat(result.system?.id).isEqualTo("psx")
        assertThat(result.confidence).isEqualTo(Confidence.PROBABLE)
    }

    // --- ambiguity without magic bytes -> UNKNOWN ---

    @Test
    fun `iso without header stays unknown`() {
        val result = engine.detect("Some Game.iso")
        assertThat(result.system).isNull()
        assertThat(result.confidence).isEqualTo(Confidence.UNKNOWN)
        assertThat(result.source).isEqualTo(MatchSource.NONE)
    }

    @Test
    fun `lone bin stays unknown`() {
        val result = engine.detect("track01.bin")
        assertThat(result.confidence).isEqualTo(Confidence.UNKNOWN)
    }

    @Test
    fun `chd stays unknown`() {
        // CHD containers carry no system marker — honest UNKNOWN.
        val result = engine.detect("Gran Turismo 2.chd")
        assertThat(result.confidence).isEqualTo(Confidence.UNKNOWN)
    }

    @Test
    fun `unregistered extension stays unknown`() {
        assertThat(engine.detect("readme.txt")).isEqualTo(
            dev.thor.rombutler.domain.model.DetectionResult.UNKNOWN,
        )
    }

    @Test
    fun `file without extension stays unknown`() {
        assertThat(engine.detect("SLUS_012").confidence).isEqualTo(Confidence.UNKNOWN)
    }

    // --- magic byte resolution ---

    @Test
    fun `iso with gamecube magic is certain gc`() {
        val header = ByteArray(0x40)
        header[0x1C] = 0xC2.toByte()
        header[0x1D] = 0x33
        header[0x1E] = 0x9F.toByte()
        header[0x1F] = 0x3D

        val result = engine.detect("Wind Waker.iso", header)
        assertThat(result.system?.id).isEqualTo("gc")
        assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
        assertThat(result.source).isEqualTo(MatchSource.MAGIC_BYTES)
    }

    @Test
    fun `iso with wii magic is certain wii`() {
        val header = ByteArray(0x40)
        header[0x18] = 0x5D
        header[0x19] = 0x1C
        header[0x1A] = 0x9E.toByte()
        header[0x1B] = 0xA3.toByte()

        val result = engine.detect("Mario Galaxy.iso", header)
        assertThat(result.system?.id).isEqualTo("wii")
        assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `iso with psp game marker is certain psp`() {
        val header = ByteArray(0x9000)
        "PSP GAME".toByteArray(Charsets.US_ASCII).copyInto(header, 0x8200)

        val result = engine.detect("Crisis Core.iso", header)
        assertThat(result.system?.id).isEqualTo("psp")
        assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `iso with playstation marker is probable ps2`() {
        val header = ByteArray(0x9000)
        "PLAYSTATION".toByteArray(Charsets.US_ASCII).copyInto(header, 0x8020)

        val result = engine.detect("Gran Turismo 4.iso", header)
        assertThat(result.system?.id).isEqualTo("ps2")
        // PS1 discs share the marker -> PROBABLE by design, never CERTAIN
        assertThat(result.confidence).isEqualTo(Confidence.PROBABLE)
        assertThat(result.source).isEqualTo(MatchSource.MAGIC_BYTES)
    }

    @Test
    fun `rvz with embedded gamecube magic is certain gc`() {
        val header = ByteArray(0x200)
        // Disc-header copy somewhere in the RVZ file header
        byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D).copyInto(header, 0x74)

        val result = engine.detect("Metroid Prime.rvz", header)
        assertThat(result.system?.id).isEqualTo("gc")
        assertThat(result.confidence).isEqualTo(Confidence.CERTAIN)
    }

    @Test
    fun `iso with unrecognized header stays unknown`() {
        val header = ByteArray(0x9000) // all zeros, no marker
        val result = engine.detect("Mystery.iso", header)
        assertThat(result.confidence).isEqualTo(Confidence.UNKNOWN)
    }

    @Test
    fun `nes magic in renamed file is detected`() {
        // A .nes file has a unique extension anyway, but magic bytes must
        // also work when the header is provided.
        val header = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12)
        val result = engine.detect("mario.nes", header)
        assertThat(result.system?.id).isEqualTo("nes")
        assertThat(result.source).isEqualTo(MatchSource.MAGIC_BYTES)
    }

    // --- helper ---

    @Test
    fun `isRomFileName recognizes registered extensions`() {
        assertThat(engine.isRomFileName("game.gba")).isTrue()
        assertThat(engine.isRomFileName("game.iso")).isTrue()
        assertThat(engine.isRomFileName("cover.jpg")).isFalse()
        assertThat(engine.isRomFileName("noextension")).isFalse()
    }
}
