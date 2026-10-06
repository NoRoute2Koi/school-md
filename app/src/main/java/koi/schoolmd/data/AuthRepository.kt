package koi.schoolmd.data

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
    val jwtData: JwtData?,
    val customAvatarUri: String? = null,
    val customName: String? = null,
    val studentProfile: StudentProfile? = null
) {
    val effectiveDisplayName: String
        get() = customName?.takeIf { it.isNotBlank() }
            ?: studentProfile?.fullName
            ?: jwtData?.displayName
            ?: "Пользователь"

    val effectiveAvatarUri: String?
        get() = customAvatarUri ?: jwtData?.avatarUrl
}

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
        val customAvatar = prefs.getString(KEY_AVATAR, null)
        val customName = prefs.getString(KEY_CUSTOM_NAME, null)
        val studentProfile = getSavedStudentProfile()
        return AuthSession(region, token, jwt, customAvatar, customName, studentProfile)
    }

    fun saveSession(region: Region, token: String): AuthSession {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REGION, region.name)
            .apply()

        val jwt = JwtDecoder.decode(token).getOrNull()
        val customAvatar = prefs.getString(KEY_AVATAR, null)
        val customName = prefs.getString(KEY_CUSTOM_NAME, null)
        val studentProfile = getSavedStudentProfile()
        return AuthSession(region, token, jwt, customAvatar, customName, studentProfile)
    }

    fun saveCustomAvatar(uriString: String?) {
        prefs.edit().apply {
            if (uriString != null) putString(KEY_AVATAR, uriString) else remove(KEY_AVATAR)
        }.apply()
    }

    fun saveCustomName(name: String?) {
        prefs.edit().apply {
            if (name != null) putString(KEY_CUSTOM_NAME, name) else remove(KEY_CUSTOM_NAME)
        }.apply()
    }

    fun saveStudentProfile(profile: StudentProfile) {
        prefs.edit()
            .putString("saved_prof_first_name", profile.firstName)
            .putString("saved_prof_last_name", profile.lastName)
            .putString("saved_prof_middle_name", profile.middleName)
            .putString("saved_prof_class_name", profile.className)
            .putString("saved_prof_school_name", profile.schoolName)
            .putString("saved_prof_guid", profile.contingentGuid)
            .putLong("saved_prof_student_id", profile.studentId ?: -1L)
            .apply()
    }

    fun getSavedStudentProfile(): StudentProfile? {
        val first = prefs.getString("saved_prof_first_name", null) ?: return null
        val last = prefs.getString("saved_prof_last_name", null) ?: ""
        val middle = prefs.getString("saved_prof_middle_name", null)
        val clazz = prefs.getString("saved_prof_class_name", null)
        val school = prefs.getString("saved_prof_school_name", null)
        val guid = prefs.getString("saved_prof_guid", null)
        val sId = prefs.getLong("saved_prof_student_id", -1L).takeIf { it != -1L }
        return StudentProfile(first, last, middle, clazz, school, guid, sId)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REGION)
            .remove(KEY_AVATAR)
            .remove(KEY_CUSTOM_NAME)
            .remove("saved_prof_first_name")
            .remove("saved_prof_last_name")
            .remove("saved_prof_middle_name")
            .remove("saved_prof_class_name")
            .remove("saved_prof_school_name")
            .remove("saved_prof_guid")
            .remove("saved_prof_student_id")
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
        private const val KEY_AVATAR = "auth_custom_avatar_uri"
        private const val KEY_CUSTOM_NAME = "auth_custom_name"
    }
}
