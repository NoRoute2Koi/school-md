package ru.school.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.school.app.data.JwtDecoder

class JwtDecoderTest {

    @Test
    fun testValidJwtDecoding() {
        // Sample JWT header: {"alg":"RS256","typ":"JWT"} -> eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9
        // Sample payload: {"sub":"user-12345","exp":4102444800,"roles":["student"]} -> eyJzdWIiOiJ1c2VyLTEyMzQ1IiwiZXhwIjo0MTAyNDQ0ODAwLCJyb2xlcyI6WyJzdHVkZW50Il19
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
    fun testExpiredJwt() {
        // Expired in 2020: exp = 1577836800
        // {"sub":"expired-user","exp":1577836800} -> eyJzdWIiOiJleHBpcmVkLXVzZXIiLCJleHAiOjE1Nzc4MzY4MDB9
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
