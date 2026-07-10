package dev.thor.rombutler.domain.model

/** Versioned collection of system definitions that can be imported/exported. */
data class SystemPack(
    val schemaVersion: Int,
    val packId: String,
    val displayName: String,
    val systems: List<SystemDefinition>,
)

/** A custom extension claimed by more than one installed system. */
data class SystemExtensionConflict(
    val extension: String,
    val systemNames: List<String>,
)

/** Stable validation categories; the UI maps them to localized messages. */
enum class SystemPackError {
    MALFORMED_JSON,
    PACK_TOO_LARGE,
    UNSUPPORTED_VERSION,
    INVALID_FIELD,
    EMPTY_PACK,
    TOO_MANY_SYSTEMS,
    DUPLICATE_ID,
    DUPLICATE_FOLDER,
    CERTAIN_EXTENSION_CONFLICT,
    UNKNOWN_MAGIC_RULE,
    BUILTIN_ID_COLLISION,
    BUILTIN_FOLDER_COLLISION,
    RESERVED_PACK_ID,
}

sealed interface SystemPackDecodeResult {
    data class Success(val pack: SystemPack) : SystemPackDecodeResult
    data class Failure(
        val error: SystemPackError,
        val detail: String? = null,
    ) : SystemPackDecodeResult
}
