package dev.thor.rombutler.domain.detection

import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.Confidence.CERTAIN
import dev.thor.rombutler.domain.model.Confidence.PROBABLE
import dev.thor.rombutler.domain.model.Confidence.UNKNOWN
import dev.thor.rombutler.domain.model.MagicRule
import dev.thor.rombutler.domain.model.SystemDefinition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for all supported systems (v0.1: 16 systems).
 *
 * Folder names follow the ES-DE convention (`roms/nes`, `roms/psx`, ...).
 * Extending the app with a new system means appending ONE entry here —
 * detection, review UI and move logic pick it up automatically.
 *
 * Confidence policy per extension:
 * - CERTAIN: the extension exists only for this system (`.gba`, `.nsp`, ...)
 * - PROBABLE: strong convention, but not guaranteed (`.cue` -> PS1)
 * - UNKNOWN: listed for grouping only, no assignment (`.bin` alone)
 * Extensions claimed by SEVERAL systems (`.iso`, `.rvz`, `.chd`) are resolved
 * via magic bytes; without a match the result stays UNKNOWN.
 */
@Singleton
class SystemRegistry @Inject constructor() {

    val systems: List<SystemDefinition> = listOf(

        SystemDefinition(
            id = "nes",
            displayName = "Nintendo Entertainment System",
            esdeFolder = "nes",
            extensions = mapOf("nes" to CERTAIN, "fds" to CERTAIN),
            magicRules = listOf(
                // iNES header: "NES<EOF>"
                MagicRule.BytesAt(0x00, byteArrayOf(0x4E, 0x45, 0x53, 0x1A)),
            ),
        ),

        SystemDefinition(
            id = "snes",
            displayName = "Super Nintendo",
            esdeFolder = "snes",
            extensions = mapOf("sfc" to CERTAIN, "smc" to CERTAIN),
        ),

        SystemDefinition(
            id = "gb",
            displayName = "Game Boy",
            esdeFolder = "gb",
            extensions = mapOf("gb" to CERTAIN),
        ),

        SystemDefinition(
            id = "gbc",
            displayName = "Game Boy Color",
            esdeFolder = "gbc",
            extensions = mapOf("gbc" to CERTAIN),
        ),

        SystemDefinition(
            id = "gba",
            displayName = "Game Boy Advance",
            esdeFolder = "gba",
            extensions = mapOf("gba" to CERTAIN),
        ),

        SystemDefinition(
            id = "n64",
            displayName = "Nintendo 64",
            esdeFolder = "n64",
            extensions = mapOf("n64" to CERTAIN, "z64" to CERTAIN, "v64" to CERTAIN),
            magicRules = listOf(
                // Native byte order (.z64)
                MagicRule.BytesAt(0x00, byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)),
            ),
        ),

        SystemDefinition(
            id = "nds",
            displayName = "Nintendo DS",
            esdeFolder = "nds",
            extensions = mapOf("nds" to CERTAIN),
        ),

        SystemDefinition(
            id = "n3ds",
            displayName = "Nintendo 3DS",
            esdeFolder = "n3ds",
            // cci = NCSD cartridge dump (same container as .3ds),
            // cxi = NCCH executable, 3dsx = homebrew format
            extensions = mapOf(
                "3ds" to CERTAIN,
                "cia" to CERTAIN,
                "cci" to CERTAIN,
                "cxi" to CERTAIN,
                "3dsx" to CERTAIN,
            ),
        ),

        SystemDefinition(
            id = "psx",
            displayName = "PlayStation 1",
            esdeFolder = "psx",
            extensions = mapOf(
                "cue" to PROBABLE,  // cue sheets: almost always PS1 in practice
                "m3u" to PROBABLE,  // multi-disc playlists: PS1 convention
                "bin" to UNKNOWN,   // listed for bin+cue grouping only
                "chd" to UNKNOWN,   // CHD carries no system marker
            ),
        ),

        SystemDefinition(
            id = "ps2",
            displayName = "PlayStation 2",
            esdeFolder = "ps2",
            extensions = mapOf("iso" to UNKNOWN, "chd" to UNKNOWN),
            magicRules = listOf(
                // ISO9660 volume descriptor area: system identifier
                // "PLAYSTATION" — PS1 discs share it, but PS1 images rarely
                // come as .iso, hence PROBABLE instead of CERTAIN.
                MagicRule.TextInRange(
                    rangeStart = 0x8000,
                    rangeEnd = 0x9000,
                    text = "PLAYSTATION",
                    confidence = PROBABLE,
                ),
            ),
        ),

        SystemDefinition(
            id = "psp",
            displayName = "PlayStation Portable",
            esdeFolder = "psp",
            extensions = mapOf(
                "cso" to CERTAIN,
                "iso" to UNKNOWN,
                "pbp" to PROBABLE,  // EBOOT.PBP: usually PSP, sometimes PS1 classics
            ),
            magicRules = listOf(
                // UMD volume descriptor area contains "PSP GAME"
                MagicRule.TextInRange(rangeStart = 0x8000, rangeEnd = 0x9000, text = "PSP GAME"),
            ),
        ),

        SystemDefinition(
            id = "gc",
            displayName = "GameCube",
            esdeFolder = "gc",
            extensions = mapOf("gcm" to CERTAIN, "iso" to UNKNOWN, "rvz" to UNKNOWN),
            magicRules = listOf(
                // GameCube disc magic C2 33 9F 3D at 0x1C
                MagicRule.BytesAt(0x1C, byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)),
                // Same magic inside an RVZ/WIA header copy of the disc header
                MagicRule.BytesInRange(
                    rangeStart = 0x40,
                    rangeEnd = 0x200,
                    bytes = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D),
                ),
            ),
        ),

        SystemDefinition(
            id = "wii",
            displayName = "Wii",
            esdeFolder = "wii",
            extensions = mapOf("wbfs" to CERTAIN, "iso" to UNKNOWN, "rvz" to UNKNOWN),
            magicRules = listOf(
                // Wii disc magic 5D 1C 9E A3 at 0x18
                MagicRule.BytesAt(0x18, byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())),
                // Same magic inside an RVZ/WIA header copy of the disc header
                MagicRule.BytesInRange(
                    rangeStart = 0x40,
                    rangeEnd = 0x200,
                    bytes = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()),
                ),
            ),
        ),

        SystemDefinition(
            id = "wiiu",
            displayName = "Wii U",
            esdeFolder = "wiiu",
            extensions = mapOf("wud" to CERTAIN, "wux" to CERTAIN, "wua" to CERTAIN),
        ),

        SystemDefinition(
            id = "dreamcast",
            displayName = "Dreamcast",
            esdeFolder = "dreamcast",
            extensions = mapOf("cdi" to CERTAIN, "gdi" to CERTAIN, "chd" to UNKNOWN),
        ),

        SystemDefinition(
            id = "switch",
            displayName = "Nintendo Switch",
            esdeFolder = "switch",
            extensions = mapOf("nsp" to CERTAIN, "xci" to CERTAIN),
        ),
    )

    /** All ROM extensions any system claims (lowercase, no dot). */
    val allRomExtensions: Set<String> by lazy {
        systems.flatMap { it.extensions.keys }.toSet()
    }

    /** Systems that claim [extension] (lowercase, no dot). */
    fun systemsForExtension(extension: String): List<SystemDefinition> =
        systems.filter { extension in it.extensions }

    /** Lookup by stable id (= ES-DE folder name). */
    fun byId(id: String): SystemDefinition? = systems.find { it.id == id }
}
