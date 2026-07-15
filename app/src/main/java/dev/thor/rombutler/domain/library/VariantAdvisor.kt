package dev.thor.rombutler.domain.library

enum class VariantRecommendationReason {
    PREFERRED_LANGUAGE,
    PREFERRED_REGION,
    CLEAN_DUMP,
    NEWER_REVISION,
}

data class VariantRecommendation(
    val fileName: String,
    val reasons: Set<VariantRecommendationReason>,
)

/** Non-destructive, deterministic ranking for same-title ROM variants. */
object VariantAdvisor {

    fun recommend(
        variants: List<String>,
        localeLanguage: String,
        localeCountry: String,
    ): VariantRecommendation? {
        if (variants.size < 2) return null
        val profiles = variants.map { profile(it, localeLanguage, localeCountry) }
        val ranked = profiles.sortedByDescending { it.score }
        val best = ranked.first()
        if (ranked.getOrNull(1)?.score == best.score) return null

        val reasons = buildSet {
            if (best.languageMatch) add(VariantRecommendationReason.PREFERRED_LANGUAGE)
            if (best.preferredRegion) add(VariantRecommendationReason.PREFERRED_REGION)
            if (!best.badDump && profiles.any { it.badDump }) {
                add(VariantRecommendationReason.CLEAN_DUMP)
            }
            val revisions = profiles.map { it.revision }
            if (best.revision > 0 && best.revision == revisions.max() &&
                revisions.distinct().size > 1
            ) {
                add(VariantRecommendationReason.NEWER_REVISION)
            }
        }
        return VariantRecommendation(best.fileName, reasons)
    }

    private fun profile(
        fileName: String,
        localeLanguage: String,
        localeCountry: String,
    ): VariantProfile {
        val lower = fileName.lowercase()
        val tags = TAG_GROUP.findAll(lower).joinToString(" ") { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2] }
        }
        val language = localeLanguage.lowercase().substringBefore('-')
        val languageAliases = LANGUAGE_ALIASES[language].orEmpty() + language
        val languageTokens = LANGUAGE_TOKEN.findAll(tags).map { it.value }.toSet()
        val hasLanguageTags = languageTokens.any { it in KNOWN_LANGUAGE_TOKENS }
        val languageMatch = languageAliases.any { it in languageTokens }

        val preferredRegions = preferredRegions(localeCountry, language)
        val explicitRegion = REGION_TOKENS.firstOrNull { token ->
            Regex("\\b$token\\b").containsMatchIn(tags)
        }
        val preferredRegion = explicitRegion != null && explicitRegion in preferredRegions
        val worldRegion = explicitRegion == "world"
        val badDump = BAD_DUMP.containsMatchIn(lower)
        val revision = REVISION.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val score = when {
            badDump -> -1_000
            else -> 0
        } + when {
            languageMatch -> 200
            !hasLanguageTags -> 40
            else -> 0
        } + when {
            preferredRegion -> 100
            worldRegion -> 70
            explicitRegion == null -> 40
            else -> 0
        } + revision.coerceAtMost(20) * 5

        return VariantProfile(
            fileName = fileName,
            score = score,
            languageMatch = languageMatch,
            preferredRegion = preferredRegion,
            badDump = badDump,
            revision = revision,
        )
    }

    private fun preferredRegions(country: String, language: String): Set<String> {
        val normalizedCountry = country.uppercase()
        return when {
            normalizedCountry in EUROPEAN_COUNTRIES || language in EUROPEAN_LANGUAGES ->
                setOf("europe", "germany")
            normalizedCountry in NORTH_AMERICAN_COUNTRIES -> setOf("usa")
            normalizedCountry == "JP" || language == "ja" -> setOf("japan")
            else -> emptySet()
        }
    }

    private data class VariantProfile(
        val fileName: String,
        val score: Int,
        val languageMatch: Boolean,
        val preferredRegion: Boolean,
        val badDump: Boolean,
        val revision: Int,
    )

    private val TAG_GROUP = Regex("""\(([^)]+)\)|\[([^]]+)]""", RegexOption.IGNORE_CASE)
    private val LANGUAGE_TOKEN = Regex("[a-z]{2,3}")
    private val REVISION = Regex("\\(rev(?:ision)?\\s+(\\d+)", RegexOption.IGNORE_CASE)
    private val BAD_DUMP = Regex(
        "\\[[bhtfop](?:\\d+)?]|\\((?:beta|proto(?:type)?|demo|sample)\\)",
        RegexOption.IGNORE_CASE,
    )
    private val REGION_TOKENS = setOf("germany", "europe", "usa", "japan", "world")
    private val LANGUAGE_ALIASES = mapOf(
        "de" to setOf("ger", "deu"),
        "en" to setOf("eng"),
        "fr" to setOf("fre", "fra"),
        "es" to setOf("spa"),
        "it" to setOf("ita"),
        "ja" to setOf("jpn"),
        "pt" to setOf("por"),
        "nl" to setOf("dut", "nld"),
    )
    private val KNOWN_LANGUAGE_TOKENS = LANGUAGE_ALIASES
        .flatMap { (language, aliases) -> aliases + language }
        .toSet()
    private val EUROPEAN_LANGUAGES = setOf("de", "fr", "es", "it", "pt", "nl", "pl")
    private val EUROPEAN_COUNTRIES = setOf(
        "AT", "BE", "CH", "DE", "ES", "FR", "GB", "IE", "IT", "NL", "PL", "PT",
    )
    private val NORTH_AMERICAN_COUNTRIES = setOf("US", "CA", "MX")
}
