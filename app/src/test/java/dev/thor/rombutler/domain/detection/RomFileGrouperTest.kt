package dev.thor.rombutler.domain.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RomFileGrouperTest {

    private val grouper = RomFileGrouper()

    @Test
    fun `single files form their own groups`() {
        val groups = grouper.group(listOf("mario.gba", "zelda.nes"))
        assertThat(groups).hasSize(2)
        assertThat(groups.map { it.primary }).containsExactly("mario.gba", "zelda.nes")
    }

    @Test
    fun `bin and cue with same stem are grouped under the cue`() {
        val groups = grouper.group(listOf("Final Fantasy VII.bin", "Final Fantasy VII.cue"))
        assertThat(groups).hasSize(1)
        val group = groups.single()
        assertThat(group.primary).isEqualTo("Final Fantasy VII.cue")
        assertThat(group.members)
            .containsExactly("Final Fantasy VII.cue", "Final Fantasy VII.bin")
    }

    @Test
    fun `cue content references beat the stem heuristic`() {
        val cueContent = """
            FILE "Track 01.bin" BINARY
              TRACK 01 MODE2/2352
            FILE "Track 02.bin" BINARY
              TRACK 02 AUDIO
        """.trimIndent()

        val groups = grouper.group(
            fileNames = listOf("game.cue", "Track 01.bin", "Track 02.bin", "other.bin"),
            textContents = mapOf("game.cue" to cueContent),
        )

        val cueGroup = groups.first { it.primary == "game.cue" }
        assertThat(cueGroup.members).containsExactly("game.cue", "Track 01.bin", "Track 02.bin")
        // other.bin stays its own (unknown) group
        assertThat(groups.map { it.primary }).contains("other.bin")
    }

    @Test
    fun `m3u groups referenced cues with their bins`() {
        val m3uContent = """
            Final Fantasy VII (Disc 1).cue
            Final Fantasy VII (Disc 2).cue
        """.trimIndent()

        val files = listOf(
            "Final Fantasy VII.m3u",
            "Final Fantasy VII (Disc 1).cue",
            "Final Fantasy VII (Disc 1).bin",
            "Final Fantasy VII (Disc 2).cue",
            "Final Fantasy VII (Disc 2).bin",
        )
        val groups = grouper.group(files, mapOf("Final Fantasy VII.m3u" to m3uContent))

        assertThat(groups).hasSize(1)
        val group = groups.single()
        assertThat(group.primary).isEqualTo("Final Fantasy VII.m3u")
        assertThat(group.members).containsExactlyElementsIn(files)
    }

    @Test
    fun `m3u references with comments and blank lines are parsed`() {
        val refs = RomFileGrouper.parsePlaylistReferences(
            "# Playlist\n\nDisc 1.chd\n  Disc 2.chd  \n#end\n",
        )
        assertThat(refs).containsExactly("Disc 1.chd", "Disc 2.chd").inOrder()
    }

    @Test
    fun `cue reference parsing handles quoted names`() {
        val refs = RomFileGrouper.parseCueReferences(
            """FILE "My Game (Europe) (Rev 1).bin" BINARY""",
        )
        assertThat(refs).containsExactly("My Game (Europe) (Rev 1).bin")
    }

    @Test
    fun `cue reference parsing handles unquoted names`() {
        val refs = RomFileGrouper.parseCueReferences("FILE track01.bin BINARY")

        assertThat(refs).containsExactly("track01.bin")
    }

    @Test
    fun `references with path prefixes resolve to plain names`() {
        val groups = grouper.group(
            fileNames = listOf("game.cue", "game.bin"),
            textContents = mapOf("game.cue" to """FILE "CD\game.bin" BINARY"""),
        )
        assertThat(groups.single().members).containsExactly("game.cue", "game.bin")
    }

    @Test
    fun `reference matching is case insensitive`() {
        val groups = grouper.group(
            fileNames = listOf("GAME.CUE", "Game.Bin"),
            textContents = mapOf("GAME.CUE" to """FILE "game.bin" BINARY"""),
        )
        assertThat(groups.single().members).containsExactly("GAME.CUE", "Game.Bin")
    }
}
