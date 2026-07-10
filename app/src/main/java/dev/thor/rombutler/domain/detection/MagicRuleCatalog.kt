package dev.thor.rombutler.domain.detection

import dev.thor.rombutler.domain.model.Confidence
import dev.thor.rombutler.domain.model.MagicRule

/** Code-backed rules referenced by stable ids from JSON system packs. */
object MagicRuleCatalog {
    private val rules: Map<String, MagicRule> = mapOf(
        "nes-ines" to MagicRule.BytesAt(
            offset = 0x00,
            bytes = byteArrayOf(0x4E, 0x45, 0x53, 0x1A),
        ),
        "n64-native" to MagicRule.BytesAt(
            offset = 0x00,
            bytes = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40),
        ),
        "chd-cd" to MagicRule.Predicate(
            name = "chd-cd",
            confidence = Confidence.PROBABLE,
            test = ChdHeader::isCdChd,
        ),
        "chd-dvd" to MagicRule.Predicate(
            name = "chd-dvd",
            confidence = Confidence.PROBABLE,
            test = ChdHeader::isDvdChd,
        ),
        "playstation-iso" to MagicRule.TextInRange(
            rangeStart = 0x8000,
            rangeEnd = 0x9000,
            text = "PLAYSTATION",
            confidence = Confidence.PROBABLE,
        ),
        "psp-iso" to MagicRule.TextInRange(
            rangeStart = 0x8000,
            rangeEnd = 0x9000,
            text = "PSP GAME",
        ),
        "gamecube-disc" to MagicRule.BytesAt(
            offset = 0x1C,
            bytes = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D),
        ),
        "gamecube-rvz" to MagicRule.BytesInRange(
            rangeStart = 0x40,
            rangeEnd = 0x200,
            bytes = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D),
        ),
        "wii-disc" to MagicRule.BytesAt(
            offset = 0x18,
            bytes = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()),
        ),
        "wii-rvz" to MagicRule.BytesInRange(
            rangeStart = 0x40,
            rangeEnd = 0x200,
            bytes = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte()),
        ),
        "dreamcast-disc" to MagicRule.TextInRange(
            rangeStart = 0,
            rangeEnd = 0x120,
            text = "SEGA SEGAKATANA",
        ),
        "megadrive" to MagicRule.TextInRange(
            rangeStart = 0x100,
            rangeEnd = 0x110,
            text = "SEGA MEGA DRIVE",
        ),
        "genesis" to MagicRule.TextInRange(
            rangeStart = 0x100,
            rangeEnd = 0x110,
            text = "SEGA GENESIS",
        ),
        "saturn-disc" to MagicRule.TextInRange(
            rangeStart = 0,
            rangeEnd = 0x120,
            text = "SEGA SEGASATURN",
        ),
    )

    fun resolve(id: String): MagicRule? = rules[id]

    fun idFor(rule: MagicRule): String? = rules.entries.firstOrNull { it.value == rule }?.key
}
