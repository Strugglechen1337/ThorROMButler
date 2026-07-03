package dev.thor.rombutler.domain.detection

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognizes emulator BIOS/firmware files so they are not offered as ROMs.
 *
 * BIOS archives typically contain `.bin`/`.rom` files that would otherwise
 * clutter the review list as UNKNOWN entries. Heuristics (case-insensitive):
 * - "bios" anywhere in the file name (covers the No-Intro "[BIOS]" tag)
 * - well-known BIOS file names (PS1 scph*, GBA/NDS/GB bios files,
 *   Dreamcast boot/flash, Amiga Kickstart, PC-Engine syscard, ...)
 */
@Singleton
class BiosDetector @Inject constructor() {

    /** True when [fileName] looks like a BIOS/firmware file, not a game. */
    fun isBios(fileName: String): Boolean {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")

        if ("bios" in name) return true
        if (name in KNOWN_BIOS_NAMES) return true
        // Prefix heuristics only for dump-typical extensions — a game like
        // "Kickle Cubicle.nes" must never be flagged by the "kick" prefix.
        if (extension in BIOS_DUMP_EXTENSIONS &&
            KNOWN_BIOS_PREFIXES.any { stem.startsWith(it) }
        ) {
            return true
        }
        return false
    }

    companion object {
        /** Exact well-known BIOS file names (lowercase). */
        private val KNOWN_BIOS_NAMES = setOf(
            // Nintendo handhelds
            "bios7.bin", "bios9.bin", "firmware.bin",   // NDS
            "gba_bios.bin",                              // GBA
            "gb_bios.bin", "gbc_bios.bin", "sgb_bios.bin",
            "dmg_boot.bin", "cgb_boot.bin", "sgb_boot.bin",
            // 3DS
            "boot9.bin", "boot11.bin", "aes_keys.txt", "seeddb.bin",
            // Dreamcast
            "dc_boot.bin", "dc_flash.bin", "naomi.bin",
            // PC Engine CD
            "syscard1.pce", "syscard2.pce", "syscard3.pce", "gexpress.pce",
            // Sega CD
            "bios_cd_e.bin", "bios_cd_u.bin", "bios_cd_j.bin",
        )

        /** File-name prefixes that identify BIOS families (lowercase stems). */
        private val KNOWN_BIOS_PREFIXES = listOf(
            "scph",       // PS1/PS2 (scph1001.bin, scph39001.bin, ...)
            "ps2-",       // PS2 dumps (ps2-0230e-...)
            "kick",       // Amiga Kickstart (kick34005.a500, kickstart...)
            "cx4",        // SNES coprocessor dumps
            "dsp1", "dsp2", "dsp3", "dsp4", "st010", "st011", "st018",
        )

        /** Extensions BIOS dumps typically use (games rarely do). */
        private val BIOS_DUMP_EXTENSIONS = setOf(
            "bin", "rom", "a500", "a600", "a1200", "a3000", "a4000",
        )
    }
}
