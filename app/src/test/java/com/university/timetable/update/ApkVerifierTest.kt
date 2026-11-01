package com.university.timetable.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkVerifierTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun sha256_matchesKnownDigestOfRealFile() {
        val file: File = temp.newFile("sample.bin")
        file.writeText("abc")
        // echo -n abc | sha256sum
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkVerifier.sha256(file)
        )
    }

    @Test
    fun sha256_changesWhenFileIsModified() {
        val file: File = temp.newFile("modified.bin")
        file.writeText("v1")
        val before = ApkVerifier.sha256(file)
        file.writeText("v2")
        val after = ApkVerifier.sha256(file)
        assertFalse(before == after)
    }

    @Test
    fun isSha256_onlyAccepts64HexChars() {
        assertTrue(ApkVerifier.isSha256("0123456789abcdef".repeat(4)))
        assertFalse(ApkVerifier.isSha256("0123456789abcdef".repeat(4) + "0"))
        assertFalse(ApkVerifier.isSha256("z".repeat(64)))
    }

    @Test
    fun normalizeSha256_stripsPrefixAndLowercases() {
        val digest = "ABCDEF0123456789".repeat(4)
        assertEquals(
            digest.lowercase(),
            ReleaseMetadataParser.normalizeSha256("sha256:" + digest)
        )
    }
}
