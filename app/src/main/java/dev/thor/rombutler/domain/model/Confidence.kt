package dev.thor.rombutler.domain.model

/**
 * How sure the detection engine is about a system assignment.
 *
 * Core rule of the app: only [CERTAIN] results get an automatic target-folder
 * suggestion. Anything else requires an explicit user decision — the app
 * NEVER moves files on an uncertain guess.
 */
enum class Confidence {
    /** Unambiguous: unique extension or verified magic bytes. */
    CERTAIN,

    /** Educated guess (e.g. `.cue` is almost always PS1) — user must confirm. */
    PROBABLE,

    /** No reliable assignment possible — user must choose the system. */
    UNKNOWN,
}
