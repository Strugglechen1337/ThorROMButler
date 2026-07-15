package dev.thor.rombutler.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AssignmentAdvisorTest {

    @Test
    fun `extension is normalized and unsafe input is rejected`() {
        assertThat(AssignmentAdvisor.extensionOf("Game.CISO")).isEqualTo("ciso")
        assertThat(AssignmentAdvisor.extensionOf("README")).isNull()
        assertThat(AssignmentAdvisor.extensionOf("Game.bad extension")).isNull()
    }

    @Test
    fun `remembered choices become a uniquely strongest suggestion`() {
        var assignments = emptyList<LearnedAssignment>()
        assignments = AssignmentAdvisor.remember(assignments, "iso", "ps2")
        assignments = AssignmentAdvisor.remember(assignments, "ISO", "ps2")
        assignments = AssignmentAdvisor.remember(assignments, "iso", "gamecube")

        assertThat(AssignmentAdvisor.suggestion("iso", assignments))
            .isEqualTo(LearnedAssignment("iso", "ps2", confirmations = 2))
    }

    @Test
    fun `conflicting choices with equal strength stay unresolved`() {
        val assignments = listOf(
            LearnedAssignment("iso", "ps2", confirmations = 2),
            LearnedAssignment("iso", "gamecube", confirmations = 2),
        )

        assertThat(AssignmentAdvisor.suggestion("iso", assignments)).isNull()
    }

    @Test
    fun `invalid learning input is ignored`() {
        val original = listOf(LearnedAssignment("gba", "gba", confirmations = 3))

        assertThat(AssignmentAdvisor.remember(original, "../iso", "ps2"))
            .containsExactlyElementsIn(original)
    }
}
