package ru.school.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ScheduleRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_schedule_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val memoryCache = ConcurrentHashMap<String, List<DaySchedule>>()
    private var cachedProfile: StudentProfile? = null

    init {
        loadCachedProfileFromPrefs()
    }

    fun getCachedProfile(): StudentProfile? = cachedProfile

    fun getCachedWeek(session: AuthSession, anchorDate: LocalDate): List<DaySchedule>? {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        val key = "${session.region.name}_$monday"
        return memoryCache[key]
    }

    fun fetchProfile(
        session: AuthSession,
        onComplete: (Result<StudentProfile>) -> Unit
    ) {
        val url = "https://${session.region.apiHost}/api/family/web/v1/profile"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("X-Mes-Subsystem", "familyweb")
            .addHeader("X-Mes-Role", "student")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ScheduleRepo", "Failed to fetch profile: ${e.message}")
                onComplete(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.e("ScheduleRepo", "Profile response code ${resp.code}: $body")
                        onComplete(Result.failure(IOException("HTTP ${resp.code}: $body")))
                        return
                    }

                    runCatching {
                        val json = JSONObject(body)
                        val profile = parseProfile(json)
                        cachedProfile = profile
                        saveCachedProfileToPrefs(profile)
                        onComplete(Result.success(profile))
                    }.onFailure {
                        Log.e("ScheduleRepo", "Profile parse error", it)
                        onComplete(Result.failure(it))
                    }
                }
            }
        })
    }

    fun fetchWeekSchedule(
        session: AuthSession,
        anchorDate: LocalDate,
        forceRefresh: Boolean = false,
        onComplete: (Result<List<DaySchedule>>) -> Unit
    ) {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        val cacheKey = "${session.region.name}_$monday"

        if (!forceRefresh) {
            memoryCache[cacheKey]?.let { cached ->
                onComplete(Result.success(cached))
                return
            }
        }

        val guid = session.jwtData?.contingentGuid
            ?: cachedProfile?.contingentGuid

        if (guid.isNullOrBlank()) {
            fetchProfile(session) { profileRes ->
                profileRes.fold(
                    onSuccess = { prof ->
                        val resolvedGuid = prof.contingentGuid
                            ?: session.jwtData?.subject
                            ?: ""
                        if (resolvedGuid.isBlank()) {
                            onComplete(Result.failure(IllegalStateException("Не удалось определить ID ученика")))
                        } else {
                            fetchWeekFromApi(session, monday, resolvedGuid, cacheKey, onComplete)
                        }
                    },
                    onFailure = {
                        onComplete(Result.failure(it))
                    }
                )
            }
        } else {
            fetchWeekFromApi(session, monday, guid, cacheKey, onComplete)
        }
    }

    private fun fetchWeekFromApi(
        session: AuthSession,
        monday: LocalDate,
        guid: String,
        cacheKey: String,
        onComplete: (Result<List<DaySchedule>>) -> Unit
    ) {
        val sunday = monday.plusDays(6)
        val beginDate = monday.toString()
        val endDate = sunday.toString()
        val url = "https://${session.region.apiHost}/api/eventcalendar/v1/api/events" +
                "?person_ids=$guid&begin_date=$beginDate&end_date=$endDate"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("X-Mes-Subsystem", "familyweb")
            .addHeader("X-Mes-Role", "student")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ScheduleRepo", "Schedule request failed: ${e.message}")
                onComplete(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.e("ScheduleRepo", "Schedule HTTP error ${resp.code}: $body")
                        onComplete(Result.failure(IOException("HTTP ${resp.code}: $body")))
                        return
                    }

                    runCatching {
                        val json = JSONObject(body)
                        val weekSchedule = parseEventsToWeekSchedule(json, monday)
                        memoryCache[cacheKey] = weekSchedule
                        onComplete(Result.success(weekSchedule))
                    }.onFailure {
                        Log.e("ScheduleRepo", "Schedule parse error", it)
                        onComplete(Result.failure(it))
                    }
                }
            }
        })
    }

    private fun parseProfile(json: JSONObject): StudentProfile {
        val profileObj = json.optJSONObject("profile")
        val childrenArr = json.optJSONArray("children")
        val firstChild = childrenArr?.optJSONObject(0)

        val firstName = profileObj?.optString("first_name").orEmpty().ifEmpty {
            firstChild?.optString("first_name").orEmpty()
        }
        val lastName = profileObj?.optString("last_name").orEmpty().ifEmpty {
            firstChild?.optString("last_name").orEmpty()
        }
        val middleName = profileObj?.optString("middle_name")
            ?: firstChild?.optString("middle_name")
        val className = firstChild?.optString("class_name")
        val schoolObj = firstChild?.optJSONObject("school")
        val schoolName = schoolObj?.optString("short_name")
            ?: schoolObj?.optString("name")
        val contingentGuid = firstChild?.optString("contingent_guid")
            ?: profileObj?.optString("contingent_guid")
        val studentId = if (profileObj != null && profileObj.has("id")) {
            profileObj.optLong("id")
        } else if (firstChild != null && firstChild.has("id")) {
            firstChild.optLong("id")
        } else null

        return StudentProfile(
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            className = className,
            schoolName = schoolName,
            contingentGuid = contingentGuid,
            studentId = studentId
        )
    }

    private fun parseEventsToWeekSchedule(
        json: JSONObject,
        monday: LocalDate
    ): List<DaySchedule> {
        val russianLocale = Locale("ru", "RU")
        val eventsArray = json.optJSONArray("response") ?: JSONArray()
        val eventsByDate = mutableMapOf<LocalDate, MutableList<LessonItem>>()

        for (i in 0 until eventsArray.length()) {
            val eventObj = eventsArray.optJSONObject(i) ?: continue
            val startAtStr = eventObj.optString("start_at")
            val finishAtStr = eventObj.optString("finish_at")
            if (startAtStr.isNullOrBlank()) continue

            val startDateTime = runCatching { OffsetDateTime.parse(startAtStr) }.getOrNull() ?: continue
            val endDateTime = runCatching { OffsetDateTime.parse(finishAtStr) }.getOrNull()
            val date = startDateTime.toLocalDate()

            val startTime = String.format(Locale.getDefault(), "%02d:%02d", startDateTime.hour, startDateTime.minute)
            val endTime = if (endDateTime != null) {
                String.format(Locale.getDefault(), "%02d:%02d", endDateTime.hour, endDateTime.minute)
            } else ""

            val subjectName = eventObj.optString("subject_name").ifEmpty { "Урок" }
            val roomNumber = eventObj.optString("room_number").takeIf { it.isNotBlank() && it != "null" }
            val roomName = eventObj.optString("room_name").takeIf { it.isNotBlank() && it != "null" }
            val classroom = when {
                roomNumber != null && (roomNumber.contains("каб", ignoreCase = true) || roomNumber.contains("зал", ignoreCase = true)) -> roomNumber
                roomNumber != null -> "$roomNumber каб"
                roomName != null -> roomName
                else -> ""
            }

            val cancelled = eventObj.optBoolean("cancelled", false)
            val replaced = eventObj.optBoolean("replaced", false)
            val courseLessonType = eventObj.optString("course_lesson_type")
            val isExam = courseLessonType.equals("THEMATIC_TEST", ignoreCase = true) ||
                    courseLessonType.equals("EXAM", ignoreCase = true) ||
                    subjectName.contains("контрольн", ignoreCase = true)
            val lessonTheme = eventObj.optString("lesson_theme").takeIf { it.isNotBlank() && it != "null" }

            var homeworkText: String? = null
            var homeworkCount = 0
            if (eventObj.has("homework")) {
                val hw = eventObj.get("homework")
                if (hw is String && hw.isNotBlank() && hw != "null") {
                    homeworkText = hw
                    homeworkCount = 1
                } else if (hw is JSONObject) {
                    homeworkText = hw.optString("description").takeIf { it.isNotBlank() }
                    homeworkCount = 1
                }
            }

            var grade: String? = null
            if (eventObj.has("marks")) {
                val marksArr = eventObj.optJSONArray("marks")
                if (marksArr != null && marksArr.length() > 0) {
                    val markObj = marksArr.optJSONObject(0)
                    grade = markObj?.optString("value")
                }
            }

            val lesson = LessonItem(
                id = eventObj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                lessonNumber = 1,
                startTime = startTime,
                endTime = endTime,
                classroom = classroom,
                subject = subjectName,
                homework = homeworkText,
                homeworkCount = homeworkCount,
                grade = grade,
                isExam = isExam,
                isCancelled = cancelled,
                isReplaced = replaced,
                theme = lessonTheme,
                startDateTime = startDateTime,
                endDateTime = endDateTime
            )

            eventsByDate.getOrPut(date) { mutableListOf() }.add(lesson)
        }

        val today = LocalDate.now()
        // 6 days: Monday through Saturday (matching mockup)
        return (0..5).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, russianLocale)
                .replace(".", "")
                .replaceFirstChar { it.uppercase() }

            val dayLessons = eventsByDate[date]?.sortedBy { it.startDateTime } ?: emptyList()
            val indexedLessons = dayLessons.mapIndexed { idx, lesson ->
                lesson.copy(lessonNumber = idx + 1)
            }

            DaySchedule(
                date = date,
                dayName = dayName,
                dayNumber = date.dayOfMonth,
                isToday = date == today,
                lessons = indexedLessons
            )
        }
    }

    private fun saveCachedProfileToPrefs(profile: StudentProfile) {
        prefs.edit()
            .putString("prof_first_name", profile.firstName)
            .putString("prof_last_name", profile.lastName)
            .putString("prof_middle_name", profile.middleName)
            .putString("prof_class_name", profile.className)
            .putString("prof_school_name", profile.schoolName)
            .putString("prof_guid", profile.contingentGuid)
            .putLong("prof_student_id", profile.studentId ?: -1L)
            .apply()
    }

    private fun loadCachedProfileFromPrefs() {
        val firstName = prefs.getString("prof_first_name", null) ?: return
        val lastName = prefs.getString("prof_last_name", null) ?: ""
        val middleName = prefs.getString("prof_middle_name", null)
        val className = prefs.getString("prof_class_name", null)
        val schoolName = prefs.getString("prof_school_name", null)
        val guid = prefs.getString("prof_guid", null)
        val studentId = prefs.getLong("prof_student_id", -1L).takeIf { it != -1L }

        cachedProfile = StudentProfile(
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            className = className,
            schoolName = schoolName,
            contingentGuid = guid,
            studentId = studentId
        )
    }
}
