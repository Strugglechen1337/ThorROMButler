package dev.thor.rombutler.domain.verification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DatIndexTest {

    private val sampleDat = """
        <?xml version="1.0"?>
        <datafile>
          <header>
            <name>Nintendo - Game Boy Advance</name>
            <version>2026-01-01</version>
          </header>
          <game name="Metroid Fusion (Europe)">
            <rom name="Metroid Fusion (Europe).gba" size="8388608" crc="5d80fbfe" md5="x" sha1="y"/>
          </game>
          <game name="Pixel &amp; Kingdom (World)">
            <rom crc="0012ABCD" size="1024" name="Pixel &amp; Kingdom (World).gba"/>
          </game>
          <game name="No CRC entry">
            <rom name="broken.gba" size="1"/>
          </game>
        </datafile>
    """.trimIndent()

    @Test
    fun `parses rom entries with any attribute order and casing`() {
        val index = DatIndex.parse(sampleDat)

        assertThat(index.entryCount).isEqualTo(2)
        assertThat(index.datNames).containsExactly("Nintendo - Game Boy Advance")

        val metroid = index.lookup(0x5D80FBFE)
        assertThat(metroid?.name).isEqualTo("Metroid Fusion (Europe).gba")
        assertThat(metroid?.sizeBytes).isEqualTo(8388608)

        // XML entities are unescaped, leading zeros in the key handled
        assertThat(index.lookup(0x0012ABCD)?.name)
            .isEqualTo("Pixel & Kingdom (World).gba")
    }

    @Test
    fun `unknown crc returns null`() {
        val index = DatIndex.parse(sampleDat)
        assertThat(index.lookup(0xDEADBEEF)).isNull()
    }

    @Test
    fun `merge combines indexes and keeps first entry on conflict`() {
        val a = DatIndex.parse(sampleDat)
        val b = DatIndex.parse(
            """<datafile><header><name>Other</name></header>
               <rom name="other.gba" crc="11111111"/></datafile>""",
        )
        val merged = DatIndex.merge(listOf(a, b))
        assertThat(merged.entryCount).isEqualTo(3)
        assertThat(merged.lookup(0x11111111)?.datName).isEqualTo("Other")
        assertThat(merged.datNames).hasSize(2)
    }
}
