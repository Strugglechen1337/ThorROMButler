package dev.thor.rombutler.domain.verification

/**
 * Provides the current DAT index for dump verification.
 */
fun interface VerificationRepository {

    /** Merged index of all configured DAT files; empty when none are set. */
    suspend fun index(): DatIndex
}
