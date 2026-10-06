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
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val avatarUrl: String?,
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

    val displayName: String
        get() {
            val full = listOfNotNull(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
            return when {
                full.isNotBlank() -> full
                !firstName.isNullOrBlank() -> firstName
                !subject.isNullOrBlank() -> "ID $subject"
                else -> "Пользователь"
            }
        }

    val initials: String
        get() {
            val f = firstName?.firstOrNull()?.uppercaseChar()
            val l = lastName?.firstOrNull()?.uppercaseChar()
            return when {
                l != null && f != null -> "$l$f"
                f != null -> "$f"
                l != null -> "$l"
                else -> "МШ"
            }
        }
}

object JwtDecoder {
    fun decode(token: String): Result<JwtData> = runCatching {
        val trimmed = token.trim()
        val parts = trimmed.split(".")
        require(parts.size >= 2) { "Invalid JWT structure: expected at least 2 parts separated by '.'" }

        val normalized = parts[1].replace('-', '+').replace('_', '/')
        val payloadBytes = Base64.getDecoder().decode(padBase64(normalized))
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

        val profileObj = json.optJSONObject("profile") ?: json.optJSONObject("user")

        val firstName = json.optStringOrNull("first_name")
            ?: json.optStringOrNull("given_name")
            ?: profileObj?.optStringOrNull("first_name")
            ?: profileObj?.optStringOrNull("given_name")

        val lastName = json.optStringOrNull("last_name")
            ?: json.optStringOrNull("family_name")
            ?: profileObj?.optStringOrNull("last_name")
            ?: profileObj?.optStringOrNull("family_name")

        val middleName = json.optStringOrNull("middle_name")
            ?: json.optStringOrNull("patronymic")
            ?: profileObj?.optStringOrNull("middle_name")

        val avatarUrl = json.optStringOrNull("avatar_url")
            ?: json.optStringOrNull("picture")
            ?: json.optStringOrNull("avatar")
            ?: profileObj?.optStringOrNull("avatar_url")
            ?: profileObj?.optStringOrNull("avatar")

        JwtData(
            rawToken = trimmed,
            subject = subject,
            expirationSeconds = exp,
            issuedAtSeconds = iat,
            roles = roles,
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            avatarUrl = avatarUrl,
            payloadJson = payloadStr
        )
    }

    fun isValidFormat(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.startsWith("eyJ") && trimmed.count { it == '.' } == 2
    }

    private fun padBase64(str: String): String =
        str + "=".repeat((4 - str.length % 4) % 4)

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key)
        return if (v.isNullOrEmpty() || v == "null") null else v
    }
}
