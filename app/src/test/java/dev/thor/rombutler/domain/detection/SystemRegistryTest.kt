package dev.thor.rombutler.domain.detection

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.domain.model.Confidence
import org.junit.Test

class SystemRegistryTest {

    private val registry = SystemRegistry()

    @Test
    fun `registry contains all 16 launch systems`() {
        val expected = setOf(
            "nes", "snes", "gb", "gbc", "gba", "n64", "nds", "n3ds",
            "psx", "ps2", "psp", "gc", "wii", "wiiu", "dreamcast", "switch",
        )
        assertThat(registry.systems.map { it.id }.toSet()).isEqualTo(expected)
    }

    @Test
    fun `ids and folders are unique`() {
        val ids = registry.systems.map { it.id }
        val folders = registry.systems.map { it.esdeFolder }
        assertThat(ids).containsNoDuplicates()
        assertThat(folders).containsNoDuplicates()
    }

    @Test
    fun `id equals esde folder name`() {
        // Convention keeps lookups and folder creation trivial.
        for (system in registry.systems) {
            assertThat(system.id).isEqualTo(system.esdeFolder)
        }
    }

    @Test
    fun `extensions shared by several systems must not be certain`() {
        // A CERTAIN extension claimed by two systems would be a registry bug:
        // the engine could silently pick the wrong one.
        val claimCounts = registry.systems
            .flatMap { system -> system.extensions.keys.map { it to system } }
            .groupBy({ it.first }, { it.second })

        for ((extension, claimants) in claimCounts) {
            if (claimants.size > 1) {
                for (system in claimants) {
                    assertThat(system.extensions.getValue(extension))
                        .isNotEqualTo(Confidence.CERTAIN)
                }
            }
        }
    }

    @Test
    fun `ambiguous extensions have at least one magic rule among claimants`() {
        // .iso and .rvz must be resolvable; .chd and .bin are known-unsolvable
        // and stay UNKNOWN by design.
        val resolvable = listOf("iso", "rvz")
        for (extension in resolvable) {
            val claimants = registry.systemsForExtension(extension)
            assertThat(claimants.size).isAtLeast(2)
            assertThat(claimants.any { it.magicRules.isNotEmpty() }).isTrue()
        }
    }

    @Test
    fun `extensions are lowercase without dot`() {
        for (system in registry.systems) {
            for (extension in system.extensions.keys) {
                assertThat(extension).isEqualTo(extension.lowercase())
                assertThat(extension).doesNotContain(".")
            }
        }
    }

    @Test
    fun `byId finds systems`() {
        assertThat(registry.byId("psx")?.displayName).isEqualTo("PlayStation 1")
        assertThat(registry.byId("does-not-exist")).isNull()
    }
}
