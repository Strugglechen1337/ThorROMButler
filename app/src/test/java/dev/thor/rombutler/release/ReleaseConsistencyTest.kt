package dev.thor.rombutler.release

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards the release chores that are easy to forget (or break by a bad
 * search-and-replace): matching version metadata and a well-formed
 * "what's new" text in both languages.
 */
class ReleaseConsistencyTest {

    // Gradle runs unit tests with the module directory (app/) as working dir
    private val moduleDir = File(".").canonicalFile.let {
        generateSequence(it) { dir -> dir.parentFile }.first { dir -> File(dir, "build.gradle.kts").isFile }
    }

    private fun versionName(): String {
        val gradle = File(moduleDir, "build.gradle.kts").readText()
        return Regex("""versionName\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
    }

    private fun whatsNewBody(resDir: String): String {
        val xml = File(moduleDir, "src/main/res/$resDir/strings.xml").readText()
        val match = Regex("""<string name="whatsnew_body">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
        assertThat(match).isNotNull()
        return match!!.groupValues[1]
    }

    @Test
    fun `whats new text is a single escaped line in both languages`() {
        for (dir in listOf("values", "values-de")) {
            val body = whatsNewBody(dir)
            // Real line breaks inside the element would be collapsed by Android
            assertThat(body).doesNotContain("\n")
            assertThat(body).contains("\\n")
            // Raw ampersands break the XML; they must be written as &amp;
            assertThat(Regex("""&(?!amp;|lt;|gt;|quot;|apos;)""").containsMatchIn(body)).isFalse()
        }
    }

    @Test
    fun `changelog and release notes exist for the current version`() {
        val version = versionName()
        val root = moduleDir.parentFile
        val changelog = File(root, "CHANGELOG.md").readText()
        assertThat(changelog).contains("## $version")
        assertThat(File(root, "docs/release-notes/v$version.md").isFile).isTrue()
    }

    @Test
    fun `release notes are bilingual with German first`() {
        val notes = File(moduleDir.parentFile, "docs/release-notes/v${versionName()}.md").readText()
        val german = notes.indexOf("## Deutsch")
        val english = notes.indexOf("## English")
        assertThat(german).isAtLeast(0)
        assertThat(english).isGreaterThan(german)
    }
}
