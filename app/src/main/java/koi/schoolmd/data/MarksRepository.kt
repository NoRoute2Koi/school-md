package koi.schoolmd.data

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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MarksRepository(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository? = null,
    private val teacherRepository: TeacherRepository = TeacherRepository(context)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_marks_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var memoryMarks: List<MarkItem>? = null
    private var memorySummaries: List<SubjectMarksSummary>? = null

    init {
        loadMarksFromPrefs()
    }

    fun getCachedMarks(): List<MarkItem>? = memoryMarks

    fun getCachedSummaries(): List<SubjectMarksSummary>? = memorySummaries

    fun getMarksForWeek(allMarks: List<MarkItem>, anchorDate: LocalDate): List<DayMarks> {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)

        return allMarks
            .filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
            .groupBy { it.date }
            .map { (date, items) ->
                DayMarks(date = date, marks = items)
            }
            .sortedByDescending { it.date }
    }

    fun fetchMarks(
        session: AuthSession,
        forceRefresh: Boolean = false,
        onComplete: (Result<List<MarkItem>>) -> Unit
    ) {
        if (!forceRefresh) {
            memoryMarks?.let { cached ->
                onComplete(Result.success(cached))
                return
            }
        }

        val studentId = session.studentProfile?.studentId
            ?: scheduleRepository?.getCachedProfile()?.studentId

        if (studentId == null && scheduleRepository != null) {
            scheduleRepository.fetchProfile(session) { profRes ->
                profRes.fold(
                    onSuccess = { prof ->
                        fetchFromApi(session, prof.studentId, onComplete)
                    },
                    onFailure = {
                        fetchFromApi(session, null, onComplete)
                    }
                )
            }
        } else {
            fetchFromApi(session, studentId, onComplete)
        }
    }

    private fun fetchFromApi(
        session: AuthSession,
        studentId: Long?,
        onComplete: (Result<List<MarkItem>>) -> Unit
    ) {
        if (studentId == null) {
            onComplete(Result.failure(IllegalStateException("Не удалось определить ID ученика")))
            return
        }

        val url = "https://${session.region.apiHost}/api/family/mobile/v1/subject_marks?student_id=$studentId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("X-Mes-Subsystem", "familymp")
            .addHeader("X-Mes-Role", "student")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MarksRepo", "Failed to fetch marks: ${e.message}")
                val fallback = memoryMarks ?: emptyList()
                if (fallback.isNotEmpty()) {
                    onComplete(Result.success(fallback))
                } else {
                    onComplete(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.e("MarksRepo", "HTTP error ${resp.code}: $body")
                        val fallback = memoryMarks ?: emptyList()
                        if (fallback.isNotEmpty()) {
                            onComplete(Result.success(fallback))
                        } else {
                            onComplete(Result.failure(IOException("HTTP ${resp.code}")))
                        }
                        return
                    }

                    runCatching {
                        val json = JSONObject(body)
                        val payload = json.optJSONArray("payload") ?: JSONArray()
                        val (marksList, summariesList) = parseMarksPayload(payload)
                        memoryMarks = marksList
                        memorySummaries = summariesList
                        saveMarksToPrefs(marksList, summariesList)
                        onComplete(Result.success(marksList))
                    }.onFailure {
                        Log.e("MarksRepo", "Parse marks error", it)
                        onComplete(Result.failure(it))
                    }
                }
            }
        })
    }

    internal fun parseMarksPayload(payload: JSONArray): Pair<List<MarkItem>, List<SubjectMarksSummary>> {
        val marks = mutableListOf<MarkItem>()
        val summaries = mutableListOf<SubjectMarksSummary>()

        for (i in 0 until payload.length()) {
            val subObj = payload.optJSONObject(i) ?: continue
            val subjectName = subObj.optString("subject_name").ifEmpty { "Предмет" }
            val subjectId = subObj.optLong("subject_id").takeIf { it != 0L }
            val avg = subObj.optString("average_by_all").takeIf { it.isNotBlank() && it != "0.00" }

            var totalSubjectMarks = 0
            var periodTitle: String? = null

            val periods = subObj.optJSONArray("periods")
            if (periods != null) {
                for (p in 0 until periods.length()) {
                    val periodObj = periods.optJSONObject(p) ?: continue
                    if (periodTitle == null) {
                        periodTitle = periodObj.optString("title").takeIf { it.isNotBlank() }
                    }

                    val marksArray = periodObj.optJSONArray("marks") ?: continue
                    for (m in 0 until marksArray.length()) {
                        val mObj = marksArray.optJSONObject(m) ?: continue
                        val value = mObj.optString("value")
                        if (value.isBlank()) continue

                        val id = mObj.optLong("id")
                        val weight = mObj.optInt("weight", 1)
                        val controlForm = mObj.optString("control_form_name").ifEmpty { "Оценка" }
                        val dateStr = mObj.optString("date")
                        val date = runCatching { LocalDate.parse(dateStr.take(10)) }.getOrNull() ?: continue
                        val comment = mObj.optString("comment").takeIf { it.isNotBlank() }
                        val isExam = mObj.optBoolean("is_exam", false)
                        val isPoint = mObj.optBoolean("is_point", false)
                        val teacher = teacherRepository.getTeacher(subjectName)

                        marks.add(
                            MarkItem(
                                id = id,
                                value = value,
                                weight = weight,
                                subject = subjectName,
                                date = date,
                                controlFormName = controlForm,
                                comment = comment,
                                isExam = isExam,
                                isPoint = isPoint,
                                teacherName = teacher
                            )
                        )
                        totalSubjectMarks++
                    }
                }
            }

            summaries.add(
                SubjectMarksSummary(
                    subjectName = subjectName,
                    subjectId = subjectId,
                    averageMark = avg,
                    marksCount = totalSubjectMarks,
                    periodTitle = periodTitle
                )
            )
        }

        val sortedMarks = marks.sortedWith(compareByDescending<MarkItem> { it.date }.thenBy { it.subject })
        val sortedSummaries = summaries.sortedBy { it.subjectName }
        return Pair(sortedMarks, sortedSummaries)
    }

    private fun saveMarksToPrefs(marks: List<MarkItem>, summaries: List<SubjectMarksSummary>) {
        runCatching {
            val marksArray = JSONArray()
            for (m in marks) {
                marksArray.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("value", m.value)
                        .put("weight", m.weight)
                        .put("subject", m.subject)
                        .put("date", m.date.toString())
                        .put("control_form_name", m.controlFormName)
                        .put("comment", m.comment.orEmpty())
                        .put("is_exam", m.isExam)
                        .put("is_point", m.isPoint)
                        .put("teacher_name", m.teacherName.orEmpty())
                )
            }

            val sumArray = JSONArray()
            for (s in summaries) {
                sumArray.put(
                    JSONObject()
                        .put("subject_name", s.subjectName)
                        .put("subject_id", s.subjectId ?: 0L)
                        .put("average_mark", s.averageMark.orEmpty())
                        .put("marks_count", s.marksCount)
                        .put("period_title", s.periodTitle.orEmpty())
                )
            }

            prefs.edit()
                .putString("cached_marks_json", marksArray.toString())
                .putString("cached_summaries_json", sumArray.toString())
                .apply()
        }
    }

    private fun loadMarksFromPrefs() {
        runCatching {
            val marksStr = prefs.getString("cached_marks_json", null)
            if (!marksStr.isNullOrBlank()) {
                val arr = JSONArray(marksStr)
                val list = mutableListOf<MarkItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val date = runCatching { LocalDate.parse(obj.getString("date")) }.getOrNull() ?: continue
                    val sub = obj.getString("subject")
                    list.add(
                        MarkItem(
                            id = obj.optLong("id"),
                            value = obj.optString("value"),
                            weight = obj.optInt("weight", 1),
                            subject = sub,
                            date = date,
                            controlFormName = obj.optString("control_form_name"),
                            comment = obj.optString("comment").takeIf { it.isNotBlank() },
                            isExam = obj.optBoolean("is_exam", false),
                            isPoint = obj.optBoolean("is_point", false),
                            teacherName = obj.optString("teacher_name").takeIf { it.isNotBlank() } ?: teacherRepository.getTeacher(sub)
                        )
                    )
                }
                memoryMarks = list
            }

            val sumStr = prefs.getString("cached_summaries_json", null)
            if (!sumStr.isNullOrBlank()) {
                val arr = JSONArray(sumStr)
                val list = mutableListOf<SubjectMarksSummary>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        SubjectMarksSummary(
                            subjectName = obj.getString("subject_name"),
                            subjectId = obj.optLong("subject_id").takeIf { it != 0L },
                            averageMark = obj.optString("average_mark").takeIf { it.isNotBlank() },
                            marksCount = obj.optInt("marks_count", 0),
                            periodTitle = obj.optString("period_title").takeIf { it.isNotBlank() }
                        )
                    )
                }
                memorySummaries = list
            }
        }
    }

    fun clearCache() {
        memoryMarks = null
        memorySummaries = null
        prefs.edit().clear().apply()
    }
}
