package dev.thor.rombutler.data.update

import com.google.common.truth.Truth.assertThat
import dev.thor.rombutler.data.update.GitHubUpdateChecker.Companion.isNewerVersion
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun `newer versions are detected`() {
        assertThat(isNewerVersion("0.4.1", "0.4.0")).isTrue()
        assertThat(isNewerVersion("0.5.0", "0.4.9")).isTrue()
        assertThat(isNewerVersion("1.0.0", "0.9.9")).isTrue()
        assertThat(isNewerVersion("0.4.0.1", "0.4.0")).isTrue()
    }

    @Test
    fun `same or older versions are not newer`() {
        assertThat(isNewerVersion("0.4.0", "0.4.0")).isFalse()
        assertThat(isNewerVersion("0.3.9", "0.4.0")).isFalse()
        assertThat(isNewerVersion("0.4", "0.4.0")).isFalse()
    }

    @Test
    fun `suffixes and junk do not crash the comparison`() {
        assertThat(isNewerVersion("0.5.0-beta", "0.4.0")).isTrue()
        assertThat(isNewerVersion("abc", "0.4.0")).isFalse()
    }
}
