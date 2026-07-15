package dev.thor.rombutler.domain.model

/** One locally learned relationship between a file extension and a target system. */
data class LearnedAssignment(
    val extension: String,
    val systemId: String,
    val confirmations: Int = 1,
)

/** Pure helper for bounded, conflict-aware assignment learning. */
object AssignmentAdvisor {

    /** Returns a safe normalized extension, or null for unsuitable input. */
    fun extensionOf(fileName: String): String? {
        val extension = fileName
            .substringAfterLast('.', missingDelimiterValue = "")
            .trim()
            .lowercase()
        return extension.takeIf(::isValidExtension)
    }

    /**
     * Returns the uniquely strongest learned target for [extension]. Ties are
     * deliberately unresolved so learning can never hide real ambiguity.
     */
    fun suggestion(
        extension: String,
        assignments: List<LearnedAssignment>,
    ): LearnedAssignment? {
        val normalized = extension.lowercase()
        val candidates = assignments
            .filter { it.extension == normalized && isValid(it) }
            .groupBy { it.systemId }
            .map { (systemId, entries) ->
                LearnedAssignment(
                    extension = normalized,
                    systemId = systemId,
                    confirmations = entries.sumOf { it.confirmations }.coerceAtMost(MAX_CONFIRMATIONS),
                )
            }
            .sortedByDescending { it.confirmations }
        val best = candidates.firstOrNull() ?: return null
        return best.takeIf { candidates.getOrNull(1)?.confirmations != best.confirmations }
    }

    /** Adds one successful user-confirmed decision while keeping storage bounded. */
    fun remember(
        assignments: List<LearnedAssignment>,
        extension: String,
        systemId: String,
    ): List<LearnedAssignment> {
        val normalizedExtension = extension.lowercase()
        val candidate = LearnedAssignment(normalizedExtension, systemId)
        if (!isValid(candidate)) return assignments.filter(::isValid).takeLast(MAX_ASSIGNMENTS)

        val cleaned = assignments.filter(::isValid).toMutableList()
        val index = cleaned.indexOfFirst {
            it.extension == normalizedExtension && it.systemId == systemId
        }
        if (index >= 0) {
            val current = cleaned[index]
            cleaned[index] = current.copy(
                confirmations = (current.confirmations + 1).coerceAtMost(MAX_CONFIRMATIONS),
            )
        } else {
            cleaned += candidate
        }
        return cleaned.takeLast(MAX_ASSIGNMENTS)
    }

    fun isValid(assignment: LearnedAssignment): Boolean =
        isValidExtension(assignment.extension) &&
            SYSTEM_ID.matches(assignment.systemId) &&
            assignment.confirmations in 1..MAX_CONFIRMATIONS

    private fun isValidExtension(extension: String): Boolean =
        extension.length in 1..MAX_EXTENSION_LENGTH && EXTENSION.matches(extension)

    const val MAX_ASSIGNMENTS = 256
    const val MAX_CONFIRMATIONS = 10_000
    private const val MAX_EXTENSION_LENGTH = 16
    private val EXTENSION = Regex("[a-z0-9][a-z0-9+_-]*")
    private val SYSTEM_ID = Regex("[a-z0-9][a-z0-9_-]{0,31}")
}
