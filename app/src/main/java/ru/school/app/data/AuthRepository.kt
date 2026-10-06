package ru.school.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthSession(
    val region: Region,
    val token: String,
    val jwtData: JwtData?
)

class AuthRepository(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "school_auth_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // Fallback for devices where KeyStore has issues
        context.getSharedPreferences("school_auth_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getSession(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val regionName = prefs.getString(KEY_REGION, Region.MOSCOW.name)
        val region = Region.fromName(regionName)
        val jwt = JwtDecoder.decode(token).getOrNull()
        return AuthSession(region, token, jwt)
    }

    fun saveSession(region: Region, token: String): AuthSession {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REGION, region.name)
            .apply()

        val jwt = JwtDecoder.decode(token).getOrNull()
        return AuthSession(region, token, jwt)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REGION)
            .apply()
    }

    fun testOrRefreshToken(
        session: AuthSession,
        onComplete: (Result<String>) -> Unit
    ) {
        val request = Request.Builder()
            .url(session.region.tokenRefreshUrl)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onComplete(Result.failure(IOException("HTTP ${it.code}: $body")))
                        return
                    }

                    // Try to parse refreshed token from body if present
                    val newToken = extractTokenFromBody(body) ?: session.token
                    if (newToken != session.token) {
                        saveSession(session.region, newToken)
                    }
                    onComplete(Result.success(newToken))
                }
            }
        })
    }

    private fun extractTokenFromBody(body: String): String? {
        return runCatching {
            val json = JSONObject(body)
            when {
                json.has("token") -> json.getString("token")
                json.has("access_token") -> json.getString("access_token")
                json.has("data") && json.getJSONObject("data").has("token") ->
                    json.getJSONObject("data").getString("token")
                else -> null
            }
        }.getOrNull() ?: if (body.trim().startsWith("eyJ")) body.trim() else null
    }

    companion object {
        private const val KEY_TOKEN = "auth_jwt_token"
        private const val KEY_REGION = "auth_region"
    }
}
