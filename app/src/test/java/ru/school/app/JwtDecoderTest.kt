package ru.school.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.school.app.data.JwtDecoder

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
        // payload: {"sub":"100","first_name":"Иван","last_name":"Иванов"} -> eyJzdWIiOiIxMDAiLCJmaXJzdF9uYW1lIjoi0JjQstCw0L0iLCJsYXN0X25hbWUiOiLQmNCy0LDQvdC+0LIifQ
        val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMDAiLCJmaXJzdF9uYW1lIjoi0JjQstCw0L0iLCJsYXN0X25hbWUiOiLQmNCy0LDQvdC+0LIifQ.sig"
        val result = JwtDecoder.decode(token)

        assertTrue(result.isSuccess)
        val jwt = result.getOrThrow()
        assertEquals("Иван", jwt.firstName)
        assertEquals("Иванов", jwt.lastName)
        assertEquals("Иванов Иван", jwt.displayName)
        assertEquals("ИИ", jwt.initials)
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
