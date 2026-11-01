package com.university.timetable.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** versionCode 推导必须和 app/build.gradle.kts 里的 versionCodeOf 完全一致。 */
class VersionNumbersTest {

    @Test
    fun codeOf_followsBuildFormula() {
        assertEquals(10000L, VersionNumbers.codeOf(1, 0, 0))
        assertEquals(10203L, VersionNumbers.codeOf(1, 2, 3))
        assertEquals(221L, VersionNumbers.codeOf(0, 2, 21))
    }

    @Test
    fun parseCode_acceptsPlainAndVPrefixedNames() {
        assertEquals(10000L, VersionNumbers.parseCode("1.0.0"))
        assertEquals(11204L, VersionNumbers.parseCode("v1.12.4"))
        assertEquals(999999L, VersionNumbers.parseCode("99.99.99"))
    }

    @Test
    fun parseCode_rejectsMalformedNames() {
        assertNull(VersionNumbers.parseCode("1.0"))
        assertNull(VersionNumbers.parseCode("1.0.0-beta"))
        assertNull(VersionNumbers.parseCode("v"))
        assertNull(VersionNumbers.parseCode("1.100.0"))
        assertNull(VersionNumbers.parseCode("latest"))
    }

    @Test
    fun codeComparison_isNumericNotLexicographic() {
        val older = VersionNumbers.parseCode("1.9.0")!!
        val newer = VersionNumbers.parseCode("1.10.0")!!
        // 字符串比较会认为 "1.10.0" < "1.9.0"，versionCode 比较不会。
        assertTrue(newer > older)
    }

    @Test
    fun displayName_stripsVPrefix() {
        assertEquals("1.0.0", VersionNumbers.displayName("v1.0.0"))
        assertEquals("1.0.0", VersionNumbers.displayName(" V1.0.0 "))
    }

    @Test
    fun isStableName_onlyAcceptsThreePartVersions() {
        assertTrue(VersionNumbers.isStableName("v2.3.4"))
        assertFalse(VersionNumbers.isStableName("v2.3.4-rc1"))
        assertFalse(VersionNumbers.isStableName("2.3"))
    }
}
