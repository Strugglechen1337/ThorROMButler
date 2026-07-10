package dev.thor.rombutler.domain.detection

import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.SystemDefinition
import dev.thor.rombutler.domain.model.SystemPack
import dev.thor.rombutler.domain.model.SystemPackDecodeResult
import dev.thor.rombutler.domain.model.SystemPackError
import org.json.JSONArray
import org.json.JSONObject

/** Strict schema-v1 JSON codec for built-in and user-provided system packs. */
object SystemPackCodec {

    fun decode(json: String): SystemPackDecodeResult {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_PACK_BYTES) {
            return failure(SystemPackError.PACK_TOO_LARGE)
        }
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return failure(SystemPackError.MALFORMED_JSON)
        }

        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            return failure(
                SystemPackError.UNSUPPORTED_VERSION,
                root.opt("schemaVersion")?.toString(),
            )
        }
        val packId = root.optString("packId").trim()
        val displayName = root.optString("displayName").trim()
        if (!isValidPackId(packId) || !isValidDisplayName(displayName)) {
            return failure(SystemPackError.INVALID_FIELD, "packId/displayName")
        }
        val array = root.optJSONArray("systems")
            ?: return failure(SystemPackError.INVALID_FIELD, "systems")
        if (array.length() == 0) return failure(SystemPackError.EMPTY_PACK)
        if (array.length() > MAX_SYSTEMS) return failure(SystemPackError.TOO_MANY_SYSTEMS)

        val systems = mutableListOf<SystemDefinition>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: return failure(SystemPackError.INVALID_FIELD, "systems[$index]")
            val decoded = decodeSystem(item, index)
            when (decoded) {
                is SystemDecode.Success -> systems += decoded.system
                is SystemDecode.Failure -> return decoded.result
            }
        }

        systems.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }?.let {
            return failure(SystemPackError.DUPLICATE_ID, it.key)
        }
        systems.groupBy { it.esdeFolder.lowercase() }.entries
            .firstOrNull { it.value.size > 1 }?.let {
                return failure(SystemPackError.DUPLICATE_FOLDER, it.value.first().esdeFolder)
        }
        val certainConflict = systems
            .flatMap { system -> system.extensions.map { (ext, confidence) -> Triple(ext, confidence, system.id) } }
            .groupBy { it.first }
            .entries
            .firstOrNull { (_, claims) ->
                claims.size > 1 && claims.any { it.second == Confidence.CERTAIN }
            }
        if (certainConflict != null) {
            return failure(SystemPackError.CERTAIN_EXTENSION_CONFLICT, certainConflict.key)
        }

        return SystemPackDecodeResult.Success(
            SystemPack(
                schemaVersion = SCHEMA_VERSION,
                packId = packId,
                displayName = displayName,
                systems = systems,
            ),
        )
    }

    fun encode(pack: SystemPack): String {
        val systems = JSONArray()
        for (system in pack.systems) {
            val extensions = JSONObject()
            system.extensions.toSortedMap().forEach { (extension, confidence) ->
                extensions.put(extension, confidence.name)
            }
            val encoded = JSONObject()
                .put("id", system.id)
                .put("displayName", system.displayName)
                .put("folder", system.esdeFolder)
                .put("extensions", extensions)
                .put("gameSubfolder", system.gameSubfolder)
            if (system.magicRules.isNotEmpty()) {
                val ruleIds = JSONArray()
                system.magicRules.forEach { rule ->
                    MagicRuleCatalog.idFor(rule)?.let(ruleIds::put)
                }
                encoded.put("magicRuleIds", ruleIds)
            }
            systems.put(encoded)
        }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("packId", pack.packId)
            .put("displayName", pack.displayName)
            .put("systems", systems)
            .toString(2)
    }

    private fun decodeSystem(item: JSONObject, index: Int): SystemDecode {
        val id = item.optString("id").trim()
        val displayName = item.optString("displayName").trim()
        val folder = item.optString("folder").trim()
        if (!isValidSystemId(id) || !isValidDisplayName(displayName) || !isValidFolder(folder)) {
            return SystemDecode.Failure(
                failure(SystemPackError.INVALID_FIELD, "systems[$index]"),
            )
        }

        val extensionObject = item.optJSONObject("extensions")
            ?: return SystemDecode.Failure(
                failure(SystemPackError.INVALID_FIELD, "$id.extensions"),
            )
        val extensions = linkedMapOf<String, Confidence>()
        for (extension in extensionObject.keys()) {
            if (!isValidExtension(extension)) {
                return SystemDecode.Failure(
                    failure(SystemPackError.INVALID_FIELD, "$id.$extension"),
                )
            }
            val confidence = runCatching {
                Confidence.valueOf(extensionObject.getString(extension))
            }.getOrNull() ?: return SystemDecode.Failure(
                failure(SystemPackError.INVALID_FIELD, "$id.$extension.confidence"),
            )
            extensions[extension] = confidence
        }

        val magicRules = mutableListOf<dev.thor.rombutler.domain.model.MagicRule>()
        val ruleIds = item.optJSONArray("magicRuleIds") ?: JSONArray()
        for (ruleIndex in 0 until ruleIds.length()) {
            val ruleId = ruleIds.optString(ruleIndex)
            val rule = MagicRuleCatalog.resolve(ruleId)
                ?: return SystemDecode.Failure(
                    failure(SystemPackError.UNKNOWN_MAGIC_RULE, ruleId),
                )
            magicRules += rule
        }

        return SystemDecode.Success(
            SystemDefinition(
                id = id,
                displayName = displayName,
                esdeFolder = folder,
                extensions = extensions,
                magicRules = magicRules,
                gameSubfolder = item.optBoolean("gameSubfolder", false),
            ),
        )
    }

    fun isValidPackId(value: String): Boolean = PACK_ID.matches(value)

    fun isValidSystemId(value: String): Boolean = SYSTEM_ID.matches(value)

    fun isValidFolder(value: String): Boolean = FOLDER.matches(value)

    fun isValidExtension(value: String): Boolean = EXTENSION.matches(value)

    fun isValidDisplayName(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_DISPLAY_NAME && value.none(Char::isISOControl)

    private fun failure(error: SystemPackError, detail: String? = null) =
        SystemPackDecodeResult.Failure(error, detail)

    private sealed interface SystemDecode {
        data class Success(val system: SystemDefinition) : SystemDecode
        data class Failure(val result: SystemPackDecodeResult.Failure) : SystemDecode
    }

    const val SCHEMA_VERSION = 1
    const val MAX_PACK_BYTES = 1024 * 1024
    private const val MAX_SYSTEMS = 128
    private const val MAX_DISPLAY_NAME = 80
    private val PACK_ID = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    private val SYSTEM_ID = Regex("[a-z0-9][a-z0-9_-]{0,31}")
    private val FOLDER = Regex("[A-Za-z0-9][A-Za-z0-9 ._+()-]{0,63}")
    private val EXTENSION = Regex("[a-z0-9][a-z0-9+_-]{0,15}")
}
