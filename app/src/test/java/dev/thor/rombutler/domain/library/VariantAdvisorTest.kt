package dev.thor.rombutler.domain.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VariantAdvisorTest {

    @Test
    fun `German locale prefers a German European variant`() {
        val recommendation = VariantAdvisor.recommend(
            variants = listOf(
                "Adventure (USA) (En).gba",
                "Adventure (Europe) (En,Fr,De).gba",
                "Adventure (Japan).gba",
            ),
            localeLanguage = "de",
            localeCountry = "DE",
        )

        assertThat(recommendation?.fileName)
            .isEqualTo("Adventure (Europe) (En,Fr,De).gba")
        assertThat(recommendation?.reasons).containsAtLeast(
            VariantRecommendationReason.PREFERRED_LANGUAGE,
            VariantRecommendationReason.PREFERRED_REGION,
        )
    }

    @Test
    fun `US locale prefers USA over Europe`() {
        val recommendation = VariantAdvisor.recommend(
            variants = listOf("Racer (Europe).sfc", "Racer (USA).sfc"),
            localeLanguage = "en",
            localeCountry = "US",
        )

        assertThat(recommendation?.fileName).isEqualTo("Racer (USA).sfc")
    }

    @Test
    fun `clean dump wins over a bad dump`() {
        val recommendation = VariantAdvisor.recommend(
            variants = listOf("Puzzle (World).gb", "Puzzle (World) [b].gb"),
            localeLanguage = "en",
            localeCountry = "",
        )

        assertThat(recommendation?.fileName).isEqualTo("Puzzle (World).gb")
        assertThat(recommendation?.reasons)
            .contains(VariantRecommendationReason.CLEAN_DUMP)
    }

    @Test
    fun `newer revision wins when other signals match`() {
        val recommendation = VariantAdvisor.recommend(
            variants = listOf("Game (Europe) (Rev 1).gba", "Game (Europe) (Rev 2).gba"),
            localeLanguage = "de",
            localeCountry = "DE",
        )

        assertThat(recommendation?.fileName).isEqualTo("Game (Europe) (Rev 2).gba")
        assertThat(recommendation?.reasons)
            .contains(VariantRecommendationReason.NEWER_REVISION)
    }

    @Test
    fun `equal candidates produce no arbitrary recommendation`() {
        val recommendation = VariantAdvisor.recommend(
            variants = listOf("Game (World) (A).gba", "Game (World) (B).gba"),
            localeLanguage = "en",
            localeCountry = "",
        )

        assertThat(recommendation).isNull()
    }
}
