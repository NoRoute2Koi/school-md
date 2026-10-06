package koi.schoolmd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import koi.schoolmd.data.JwtDecoder

class JwtDecoderTest {

    @Test
    fun testValidJwtDecoding() {
        val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMzQ1IiwiZXhwIjo0MTAyNDQ0ODAwLCJyb2xlcyI6WyJzdHVkZW50Il19.signature"

        assertTrue(JwtDecoder.isValidFormat(token))

        val result = JwtDecoder.decode(token)
        assertTrue(result.isSuccess)

        val jwt = result.getOrThrow()
        assertEquals("user-12345", jwt.subject)
        assertEquals(4102444800L, jwt.expirationSeconds)
        assertEquals(listOf("student"), jwt.roles)
        assertFalse(jwt.isExpired)
    }

    @Test
    fun testNameAndInitialsExtraction() {
        // payload: {"sub":"100","first_name":"Тест","last_name":"Пользователь"} -> eyJzdWIiOiIxMDAiLCJmaXJzdF9uYW1lIjoi0KLQtdGB0YIiLCJsYXN0X25hbWUiOiLQn9C-0LvRjNC30L7QstCw0YLQtdC70YwifQ
        val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMDAiLCJmaXJzdF9uYW1lIjoi0KLQtdGB0YIiLCJsYXN0X25hbWUiOiLQn9C-0LvRjNC30L7QstCw0YLQtdC70YwifQ.sig"
        val result = JwtDecoder.decode(token)

        assertTrue(result.isSuccess)
        val jwt = result.getOrThrow()
        assertEquals("Тест", jwt.firstName)
        assertEquals("Пользователь", jwt.lastName)
        assertEquals("Пользователь Тест", jwt.displayName)
        assertEquals("ПТ", jwt.initials)
    }

    @Test
    fun testExpiredJwt() {
        val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJleHBpcmVkLXVzZXIiLCJleHAiOjE1Nzc4MzY4MDB9.signature"
        val result = JwtDecoder.decode(token)

        assertTrue(result.isSuccess)
        val jwt = result.getOrThrow()
        assertTrue(jwt.isExpired)
    }

    @Test
    fun testInvalidFormat() {
        assertFalse(JwtDecoder.isValidFormat("not-a-jwt"))
        val result = JwtDecoder.decode("bad.token")
        assertTrue(result.isFailure)
    }
}
