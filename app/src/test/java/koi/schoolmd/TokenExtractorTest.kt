package koi.schoolmd

import koi.schoolmd.data.TokenExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenExtractorTest {

    private val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @Test
    fun testExtractFromJsonWithTokenKey() {
        val json = """{"token": "$sampleJwt"}"""
        val extracted = TokenExtractor.extractToken(json)
        assertEquals(sampleJwt, extracted)
    }

    @Test
    fun testExtractFromJsonWithAccessTokenKey() {
        val json = """{"access_token": "$sampleJwt"}"""
        val extracted = TokenExtractor.extractToken(json)
        assertEquals(sampleJwt, extracted)
    }

    @Test
    fun testExtractFromNestedDataToken() {
        val json = """{"data": {"token": "$sampleJwt"}}"""
        val extracted = TokenExtractor.extractToken(json)
        assertEquals(sampleJwt, extracted)
    }

    @Test
    fun testExtractPlainJwtString() {
        val extracted = TokenExtractor.extractToken(sampleJwt)
        assertEquals(sampleJwt, extracted)
    }

    @Test
    fun testMalformedOrEmptyInputReturnsNull() {
        assertNull(TokenExtractor.extractToken(""))
        assertNull(TokenExtractor.extractToken("    "))
        assertNull(TokenExtractor.extractToken("<html><body>Error</body></html>"))
        assertNull(TokenExtractor.extractToken("{\"error\": \"not_found\"}"))
    }
}
