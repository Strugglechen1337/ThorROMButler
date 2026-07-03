package dev.thor.rombutler.domain.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BiosDetectorTest {

    private val detector = BiosDetector()

    @Test
    fun `no-intro bios tag is detected`() {
        assertThat(detector.isBios("[BIOS] Nintendo Game Boy Boot ROM (World).gb")).isTrue()
        assertThat(detector.isBios("PSX BIOS Pack.zip")).isTrue()
        assertThat(detector.isBios("gba_bios.bin")).isTrue()
    }

    @Test
    fun `known bios file names are detected`() {
        val biosFiles = listOf(
            "scph1001.bin", "SCPH5502.BIN", "scph39001.bin",
            "bios7.bin", "bios9.bin", "firmware.bin",
            "dc_boot.bin", "dc_flash.bin",
            "kick34005.A500", "kickstart-v3.1.rom",
            "syscard3.pce",
        )
        for (file in biosFiles) {
            assertThat(detector.isBios(file)).isTrue()
        }
    }

    @Test
    fun `paths inside archives are handled`() {
        assertThat(detector.isBios("bios/scph1001.bin")).isTrue()
        assertThat(detector.isBios("PS1\\SCPH7502.bin")).isTrue()
    }

    @Test
    fun `games are not flagged as bios`() {
        val games = listOf(
            "Final Fantasy VII (Disc 1).bin",
            "Super Mario World.sfc",
            "Metroid Fusion.gba",
            "Gran Turismo 2.chd",
            // Prefix collisions on game extensions must stay games:
            "Kickle Cubicle (USA).nes",
            "Kick Off 2 (Europe).adf",
        )
        for (file in games) {
            assertThat(detector.isBios(file)).isFalse()
        }
    }
}
