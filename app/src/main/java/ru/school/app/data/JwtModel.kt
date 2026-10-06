package ru.school.app.data

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

data class JwtData(
    val rawToken: String,
    val subject: String?,
    val expirationSeconds: Long?,
    val issuedAtSeconds: Long?,
    val roles: List<String>,
    val payloadJson: String
) {
    val isExpired: Boolean
        get() {
            val exp = expirationSeconds ?: return false
            val nowSec = System.currentTimeMillis() / 1000
            return nowSec >= exp
        }

    val remainingMinutes: Long
        get() {
            val exp = expirationSeconds ?: return 0
            val nowSec = System.currentTimeMillis() / 1000
            return ((exp - nowSec) / 60).coerceAtLeast(0)
        }
}

object JwtDecoder {
    fun decode(token: String): Result<JwtData> = runCatching {
        val trimmed = token.trim()
        val parts = trimmed.split(".")
        require(parts.size >= 2) { "Invalid JWT structure: expected at least 2 parts separated by '.'" }

        val decoder = Base64.getUrlDecoder()
        val payloadBytes = decoder.decode(padBase64(parts[1]))
        val payloadStr = String(payloadBytes, StandardCharsets.UTF_8)

        val json = JSONObject(payloadStr)

        val subject = json.optString("sub").takeIf { it.isNotEmpty() }
        val exp = if (json.has("exp")) json.optLong("exp") else null
        val iat = if (json.has("iat")) json.optLong("iat") else null

        val roles = mutableListOf<String>()
        val rolesArray = json.optJSONArray("roles")
        if (rolesArray != null) {
            for (i in 0 until rolesArray.length()) {
                roles.add(rolesArray.optString(i))
            }
        } else if (json.has("role")) {
            roles.add(json.optString("role"))
        }

        JwtData(
            rawToken = trimmed,
            subject = subject,
            expirationSeconds = exp,
            issuedAtSeconds = iat,
            roles = roles,
            payloadJson = payloadStr
        )
    }

    fun isValidFormat(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.startsWith("eyJ") && trimmed.count { it == '.' } == 2
    }

    private fun padBase64(str: String): String =
        str + "=".repeat((4 - str.length % 4) % 4)
}
