package koi.schoolmd.data

import org.json.JSONObject

object TokenExtractor {
    fun extractToken(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("ey") && trimmed.split(".").size == 3 && trimmed.length > 50) {
            return trimmed
        }
        return runCatching {
            val json = JSONObject(trimmed)
            when {
                json.has("token") -> json.getString("token")
                json.has("access_token") -> json.getString("access_token")
                json.has("data") && json.getJSONObject("data").has("token") ->
                    json.getJSONObject("data").getString("token")
                else -> null
            }
        }.getOrNull()
    }
}
