package dev.thor.rombutler.domain.detection

import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.Confidence.CERTAIN
import dev.thor.rombutler.domain.model.Confidence.PROBABLE
import dev.thor.rombutler.domain.model.Confidence.UNKNOWN
import dev.thor.rombutler.domain.model.MagicRule
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.model.SystemExtensionConflict
import dev.thor.rombutler.domain.model.SystemPack
import dev.thor.rombutler.domain.model.SystemPackDecodeResult
import dev.thor.rombutler.domain.model.SystemPackError
import dev.thor.rombutler.domain.repository.SettingsRepository
import dev.thor.rombutler.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Last-known-good definitions used only if the bundled JSON cannot be read. */
internal object LegacySystemDefinitions {

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
                "chd" to UNKNOWN,
            ),
            magicRules = listOf(
                // CD-type CHD: PS1 / Saturn / Dreamcast / PCE share the
                // format — PS1 is by far the most common, hence PROBABLE.
                MagicRule.Predicate("chd-cd", PROBABLE, ChdHeader::isCdChd),
            ),
        ),

        SystemDefinition(
            id = "ps2",
            displayName = "PlayStation 2",
            esdeFolder = "ps2",
            extensions = mapOf("iso" to UNKNOWN, "chd" to UNKNOWN),
            magicRules = listOf(
                // DVD-sized CHD without CD codecs: practically always PS2
                MagicRule.Predicate("chd-dvd", PROBABLE, ChdHeader::isDvdChd),
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
            extensions = mapOf(
                "cdi" to CERTAIN,
                "gdi" to CERTAIN,
                "chd" to UNKNOWN,
                "bin" to UNKNOWN,
            ),
            magicRules = listOf(
                // Disc header hardware id at offset 0
                MagicRule.TextInRange(rangeStart = 0, rangeEnd = 0x120, text = "SEGA SEGAKATANA"),
            ),
            // GDI games (gdi + track files) must sit in one folder per game
            gameSubfolder = true,
        ),

        SystemDefinition(
            id = "switch",
            displayName = "Nintendo Switch",
            esdeFolder = "switch",
            extensions = mapOf("nsp" to CERTAIN, "xci" to CERTAIN),
        ),

        SystemDefinition(
            id = "amiga",
            displayName = "Commodore Amiga",
            esdeFolder = "amiga",
            extensions = mapOf("adf" to CERTAIN, "ipf" to CERTAIN, "hdf" to CERTAIN),
        ),

        SystemDefinition(
            id = "c64",
            displayName = "Commodore 64",
            esdeFolder = "c64",
            extensions = mapOf(
                "d64" to CERTAIN,
                "t64" to CERTAIN,
                "crt" to CERTAIN,
                "prg" to PROBABLE, // generic executable extension
                "tap" to PROBABLE, // tape images exist for other systems too
            ),
        ),

        SystemDefinition(
            id = "megadrive",
            displayName = "Sega Mega Drive",
            esdeFolder = "megadrive",
            extensions = mapOf(
                "md" to CERTAIN,
                "gen" to CERTAIN,
                "smd" to CERTAIN,
                "bin" to UNKNOWN,
            ),
            magicRules = listOf(
                // Cartridge header console name at 0x100
                MagicRule.TextInRange(rangeStart = 0x100, rangeEnd = 0x110, text = "SEGA MEGA DRIVE"),
                MagicRule.TextInRange(rangeStart = 0x100, rangeEnd = 0x110, text = "SEGA GENESIS"),
            ),
        ),

        SystemDefinition(
            id = "mastersystem",
            displayName = "Sega Master System",
            esdeFolder = "mastersystem",
            extensions = mapOf("sms" to CERTAIN),
        ),

        SystemDefinition(
            id = "gamegear",
            displayName = "Sega Game Gear",
            esdeFolder = "gamegear",
            extensions = mapOf("gg" to CERTAIN),
        ),

        SystemDefinition(
            id = "saturn",
            displayName = "Sega Saturn",
            esdeFolder = "saturn",
            extensions = mapOf("bin" to UNKNOWN, "iso" to UNKNOWN, "chd" to UNKNOWN),
            magicRules = listOf(
                // Disc header hardware id at offset 0
                MagicRule.TextInRange(rangeStart = 0, rangeEnd = 0x120, text = "SEGA SEGASATURN"),
            ),
        ),

        SystemDefinition(
            id = "atari2600",
            displayName = "Atari 2600",
            esdeFolder = "atari2600",
            extensions = mapOf("a26" to CERTAIN),
        ),

        SystemDefinition(
            id = "atari7800",
            displayName = "Atari 7800",
            esdeFolder = "atari7800",
            extensions = mapOf("a78" to CERTAIN),
        ),

        SystemDefinition(
            id = "atarilynx",
            displayName = "Atari Lynx",
            esdeFolder = "atarilynx",
            extensions = mapOf("lnx" to CERTAIN),
        ),

        SystemDefinition(
            id = "pcengine",
            displayName = "PC Engine / TurboGrafx-16",
            esdeFolder = "pcengine",
            extensions = mapOf("pce" to CERTAIN),
        ),

        SystemDefinition(
            id = "sega32x",
            displayName = "Sega 32X",
            esdeFolder = "sega32x",
            extensions = mapOf("32x" to CERTAIN),
        ),

        SystemDefinition(
            id = "ngp",
            displayName = "Neo Geo Pocket",
            esdeFolder = "ngp",
            extensions = mapOf("ngp" to CERTAIN),
        ),

        SystemDefinition(
            id = "ngpc",
            displayName = "Neo Geo Pocket Color",
            esdeFolder = "ngpc",
            extensions = mapOf("ngc" to CERTAIN),
        ),

        SystemDefinition(
            id = "wonderswan",
            displayName = "WonderSwan",
            esdeFolder = "wonderswan",
            extensions = mapOf("ws" to CERTAIN),
        ),

        SystemDefinition(
            id = "wonderswancolor",
            displayName = "WonderSwan Color",
            esdeFolder = "wonderswancolor",
            extensions = mapOf("wsc" to CERTAIN),
        ),

        // Arcade systems: ROM sets are ZIPs that MUST stay zipped. They
        // claim no extensions (a plain .zip is indistinguishable from a
        // ROM archive) — users assign whole archives manually in review,
        // which moves the ZIP unextracted.
        SystemDefinition(
            id = "arcade",
            displayName = "Arcade (MAME)",
            esdeFolder = "arcade",
            extensions = emptyMap(),
        ),

        SystemDefinition(
            id = "neogeo",
            displayName = "Neo Geo",
            esdeFolder = "neogeo",
            extensions = emptyMap(),
        ),
    )

}

/** Immutable view consumed by detection and the settings UI. */
data class SystemRegistryState(
    val builtInSystems: List<SystemDefinition>,
    val customPack: SystemPack? = null,
    val conflicts: List<SystemExtensionConflict> = emptyList(),
    val customPackError: SystemPackDecodeResult.Failure? = null,
    val builtInFallbackUsed: Boolean = false,
) {
    val customSystems: List<SystemDefinition> = customPack?.systems.orEmpty()
    val systems: List<SystemDefinition> = builtInSystems + customSystems
}

/**
 * Single source of truth backed by a versioned bundled JSON pack plus an
 * optional user pack persisted in DataStore. Invalid user data never replaces
 * the built-ins; shared extensions become explicit warnings and stay ambiguous.
 */
@Singleton
class SystemRegistry private constructor(
    private val builtInSystems: List<SystemDefinition>,
    builtInFallbackUsed: Boolean,
    settingsRepository: SettingsRepository?,
    ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        load = loadBundledSystems(),
        settingsRepository = settingsRepository,
        ioDispatcher = ioDispatcher,
    )

    private constructor(
        load: BuiltInLoad,
        settingsRepository: SettingsRepository?,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        builtInSystems = load.systems,
        builtInFallbackUsed = load.fallbackUsed,
        settingsRepository = settingsRepository,
        ioDispatcher = ioDispatcher,
    )

    /** JVM-friendly constructor; production uses the injected JSON-backed one. */
    constructor() : this(
        builtInSystems = LegacySystemDefinitions.systems,
        builtInFallbackUsed = true,
        settingsRepository = null,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private val _state = MutableStateFlow(
        SystemRegistryState(
            builtInSystems = builtInSystems,
            builtInFallbackUsed = builtInFallbackUsed,
        ),
    )
    val state: StateFlow<SystemRegistryState> = _state.asStateFlow()

    private val aliases = mapOf(
        "supernintendo" to "snes", "famicom" to "nes",
        "gameboy" to "gb", "gameboycolor" to "gbc", "gameboyadvance" to "gba",
        "nintendo64" to "n64", "nintendods" to "nds", "ds" to "nds",
        "3ds" to "n3ds", "nintendo3ds" to "n3ds",
        "ps1" to "psx", "psone" to "psx", "playstation" to "psx",
        "playstation1" to "psx", "playstation2" to "ps2",
        "gamecube" to "gc", "ngc" to "gc", "dc" to "dreamcast",
        "nsw" to "switch", "nintendoswitch" to "switch",
        "commodore64" to "c64", "commodoreamiga" to "amiga",
        "genesis" to "megadrive", "md" to "megadrive", "segamegadrive" to "megadrive",
        "sms" to "mastersystem", "segamastersystem" to "mastersystem",
        "gg" to "gamegear", "segagamegear" to "gamegear",
        "segasaturn" to "saturn", "32x" to "sega32x",
        "2600" to "atari2600", "7800" to "atari7800", "lynx" to "atarilynx",
        "turbografx" to "pcengine", "turbografx16" to "pcengine",
        "tg16" to "pcengine", "pce" to "pcengine", "ws" to "wonderswan",
        "wsc" to "wonderswancolor", "mame" to "arcade",
    )

    init {
        if (settingsRepository != null) {
            CoroutineScope(SupervisorJob() + ioDispatcher).launch {
                settingsRepository.settings
                    .map { it.customSystemPackJson }
                    .distinctUntilChanged()
                    .collect(::applyCustomPackJson)
            }
        }
    }

    val systems: List<SystemDefinition>
        get() = _state.value.systems

    val allRomExtensions: Set<String>
        get() = systems.flatMapTo(linkedSetOf()) { it.extensions.keys }

    /** Validates against schema rules and immutable built-in ids/folders. */
    fun validateCustomPack(json: String): SystemPackDecodeResult {
        val decoded = SystemPackCodec.decode(json)
        if (decoded !is SystemPackDecodeResult.Success) return decoded
        if (decoded.pack.packId == BUILTIN_PACK_ID) {
            return SystemPackDecodeResult.Failure(SystemPackError.RESERVED_PACK_ID, BUILTIN_PACK_ID)
        }
        decoded.pack.systems.firstOrNull { candidate ->
            builtInSystems.any { it.id == candidate.id }
        }?.let {
            return SystemPackDecodeResult.Failure(SystemPackError.BUILTIN_ID_COLLISION, it.id)
        }
        decoded.pack.systems.firstOrNull { candidate ->
            builtInSystems.any { it.esdeFolder.equals(candidate.esdeFolder, ignoreCase = true) }
        }?.let {
            return SystemPackDecodeResult.Failure(
                SystemPackError.BUILTIN_FOLDER_COLLISION,
                it.esdeFolder,
            )
        }
        return decoded
    }

    /** Applies a persisted pack immediately; malformed data falls back safely. */
    fun applyCustomPackJson(json: String?) {
        if (json.isNullOrBlank()) {
            _state.value = _state.value.copy(
                customPack = null,
                conflicts = emptyList(),
                customPackError = null,
            )
            return
        }
        when (val decoded = validateCustomPack(json)) {
            is SystemPackDecodeResult.Success -> {
                _state.value = _state.value.copy(
                    customPack = decoded.pack,
                    conflicts = conflictsForCustomSystems(decoded.pack.systems),
                    customPackError = null,
                )
            }

            is SystemPackDecodeResult.Failure -> {
                _state.value = _state.value.copy(
                    customPack = null,
                    conflicts = emptyList(),
                    customPackError = decoded,
                )
            }
        }
    }

    fun encodeCustomPack(
        systems: List<SystemDefinition>,
        packId: String = DEFAULT_CUSTOM_PACK_ID,
        displayName: String = DEFAULT_CUSTOM_PACK_NAME,
    ): String = SystemPackCodec.encode(
        SystemPack(
            schemaVersion = SystemPackCodec.SCHEMA_VERSION,
            packId = packId,
            displayName = displayName,
            systems = systems,
        ),
    )

    /** System hinted at by a folder name, or `null`. */
    fun systemForFolderName(folderName: String): SystemDefinition? {
        val normalized = normalizeAlias(folderName)
        val dynamic = systems.firstOrNull {
            normalizeAlias(it.id) == normalized || normalizeAlias(it.esdeFolder) == normalized
        }
        return dynamic ?: aliases[normalized]?.let(::byId)
    }

    fun systemsForExtension(extension: String): List<SystemDefinition> =
        systems.filter { extension.lowercase() in it.extensions }

    fun byId(id: String): SystemDefinition? = systems.find { it.id == id }

    /** Predicts warnings for a validated pack without activating it. */
    fun conflictsForCustomSystems(
        customSystems: List<SystemDefinition>,
    ): List<SystemExtensionConflict> {
        val customIds = customSystems.mapTo(hashSetOf()) { it.id }
        return (builtInSystems + customSystems)
            .flatMap { system -> system.extensions.keys.map { it to system } }
            .groupBy({ it.first }, { it.second })
            .filterValues { claimants ->
                claimants.size > 1 && claimants.any { it.id in customIds }
            }
            .map { (extension, claimants) ->
                SystemExtensionConflict(extension, claimants.map { it.displayName }.sorted())
            }
            .sortedBy { it.extension }
    }

    private fun normalizeAlias(value: String): String = value.lowercase()
        .replace(" ", "").replace("-", "").replace("_", "")

    companion object {
        const val DEFAULT_CUSTOM_PACK_ID = "user.custom"
        const val DEFAULT_CUSTOM_PACK_NAME = "My custom systems"
        private const val BUILTIN_PACK_ID = "thor.builtin"
        private const val BUILTIN_RESOURCE = "system-packs/builtin-v1.json"

        private fun loadBundledSystems(): BuiltInLoad {
            val json = SystemRegistry::class.java.classLoader
                ?.getResourceAsStream(BUILTIN_RESOURCE)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return BuiltInLoad(LegacySystemDefinitions.systems, fallbackUsed = true)
            return when (val decoded = SystemPackCodec.decode(json)) {
                is SystemPackDecodeResult.Success -> BuiltInLoad(
                    decoded.pack.systems,
                    fallbackUsed = false,
                )
                is SystemPackDecodeResult.Failure -> BuiltInLoad(
                    LegacySystemDefinitions.systems,
                    fallbackUsed = true,
                )
            }
        }

        private data class BuiltInLoad(
            val systems: List<SystemDefinition>,
            val fallbackUsed: Boolean,
        )
    }
}
