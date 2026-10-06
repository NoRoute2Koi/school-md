package koi.schoolmd.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class HomeworkRepository(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository? = null,
    private val teacherRepository: TeacherRepository = TeacherRepository(context)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_homework_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Key: "${region}_${mondayDate}" -> List<HomeworkItem>
    private val memoryCache = ConcurrentHashMap<String, List<HomeworkItem>>()

    // Key: "lesson_${lessonId}" -> JSONObject (lesson details)
    private val lessonDetailsCache = ConcurrentHashMap<Long, JSONObject>()

    init {
        // Purge any legacy mock items stored in prefs
        runCatching {
            val editor = prefs.edit()
            var changed = false
            prefs.all.forEach { (key, value) ->
                if (value is String && value.contains("mock_")) {
                    editor.remove(key)
                    changed = true
                }
            }
            if (changed) editor.apply()
        }
    }

    fun getCachedHomeworks(session: AuthSession, anchorDate: LocalDate): List<HomeworkItem>? {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        val key = "${session.region.name}_$monday"
        memoryCache[key]?.let { cached ->
            if (cached.any { it.id.startsWith("mock_") } && session.token.isNotBlank()) {
                memoryCache.remove(key)
            } else {
                return cached
            }
        }

        // Try loading from prefs
        val savedJson = prefs.getString(key, null) ?: return null
        return runCatching {
            val list = parseHomeworkList(JSONArray(savedJson))
            if (list.any { it.id.startsWith("mock_") } && session.token.isNotBlank()) {
                prefs.edit().remove(key).apply()
                null
            } else {
                memoryCache[key] = list
                list
            }
        }.getOrNull()
    }

    fun fetchHomeworks(
        session: AuthSession,
        anchorDate: LocalDate,
        forceRefresh: Boolean = false,
        onComplete: (Result<List<HomeworkItem>>) -> Unit
    ) {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        val cacheKey = "${session.region.name}_$monday"

        if (!forceRefresh) {
            getCachedHomeworks(session, anchorDate)?.let { cached ->
                onComplete(Result.success(cached))
                return
            }
        }

        val studentId = session.studentProfile?.studentId
            ?: scheduleRepository?.getCachedProfile()?.studentId
        val guid = session.studentProfile?.contingentGuid
            ?: scheduleRepository?.getCachedProfile()?.contingentGuid
            ?: session.jwtData?.contingentGuid
            ?: session.jwtData?.subject

        if (studentId == null && scheduleRepository != null) {
            scheduleRepository.fetchProfile(session) { profResult ->
                profResult.fold(
                    onSuccess = { profile ->
                        val resolvedStudentId = profile.studentId
                        val resolvedGuid = profile.contingentGuid ?: session.jwtData?.subject
                        fetchHomeworksWithIds(
                            session = session,
                            monday = monday,
                            studentId = resolvedStudentId,
                            guid = resolvedGuid,
                            cacheKey = cacheKey,
                            onComplete = onComplete
                        )
                    },
                    onFailure = {
                        fetchHomeworksWithIds(
                            session = session,
                            monday = monday,
                            studentId = null,
                            guid = guid,
                            cacheKey = cacheKey,
                            onComplete = onComplete
                        )
                    }
                )
            }
        } else {
            fetchHomeworksWithIds(
                session = session,
                monday = monday,
                studentId = studentId,
                guid = guid,
                cacheKey = cacheKey,
                onComplete = onComplete
            )
        }
    }

    private fun fetchHomeworksWithIds(
        session: AuthSession,
        monday: LocalDate,
        studentId: Long?,
        guid: String?,
        cacheKey: String,
        onComplete: (Result<List<HomeworkItem>>) -> Unit
    ) {
        val sunday = monday.plusDays(6)
        val beginDate = monday.toString()
        val endDate = sunday.toString()

        val gatheredItems = mutableListOf<HomeworkItem>()

        // Primary: Official mobile endpoint
        if (studentId != null) {
            val mobileUrl = "https://${session.region.apiHost}/api/family/mobile/v1/homeworks" +
                    "?student_id=$studentId&from=$beginDate&to=$endDate"
            val request = Request.Builder()
                .url(mobileUrl)
                .addHeader("Authorization", "Bearer ${session.token}")
                .addHeader("X-Mes-Subsystem", "familymp")
                .addHeader("X-Mes-Role", "student")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("HomeworkRepo", "Mobile homeworks call failed, trying fallback: ${e.message}")
                    fetchViaEventsAndLessonDetails(session, monday, sunday, studentId, guid, cacheKey, gatheredItems, onComplete)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            runCatching {
                                val json = JSONObject(body)
                                val payload = json.optJSONArray("payload")
                                if (payload != null) {
                                    val parsed = parseMobileHomeworksPayload(payload)
                                    synchronized(gatheredItems) {
                                        gatheredItems.addAll(parsed)
                                    }
                                }
                            }.onFailure {
                                Log.e("HomeworkRepo", "Failed to parse mobile homeworks", it)
                            }
                            finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
                        } else {
                            Log.w("HomeworkRepo", "Mobile homeworks returned ${resp.code}, falling back")
                            fetchViaEventsAndLessonDetails(session, monday, sunday, studentId, guid, cacheKey, gatheredItems, onComplete)
                        }
                    }
                }
            })
        } else {
            fetchViaEventsAndLessonDetails(session, monday, sunday, null, guid, cacheKey, gatheredItems, onComplete)
        }
    }

    private fun fetchViaEventsAndLessonDetails(
        session: AuthSession,
        monday: LocalDate,
        sunday: LocalDate,
        studentId: Long?,
        guid: String?,
        cacheKey: String,
        gatheredItems: MutableList<HomeworkItem>,
        onComplete: (Result<List<HomeworkItem>>) -> Unit
    ) {
        if (guid.isNullOrBlank()) {
            finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
            return
        }

        val beginDate = monday.toString()
        val endDate = sunday.toString()
        val eventsUrl = "https://${session.region.apiHost}/api/eventcalendar/v1/api/events" +
                "?person_ids=$guid&begin_date=$beginDate&end_date=$endDate"

        val request = Request.Builder()
            .url(eventsUrl)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("X-Mes-Subsystem", "familyweb")
            .addHeader("X-Mes-Role", "student")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HomeworkRepo", "Failed to fetch events: ${e.message}")
                finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.e("HomeworkRepo", "Events HTTP ${resp.code}: $body")
                        finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
                        return
                    }

                    val events = runCatching {
                        val json = JSONObject(body)
                        json.optJSONArray("response") ?: JSONArray()
                    }.getOrDefault(JSONArray())

                    val lessonIdsToFetch = mutableListOf<Long>()
                    for (i in 0 until events.length()) {
                        val ev = events.optJSONObject(i) ?: continue
                        val evId = ev.optLong("id").takeIf { it != 0L }
                            ?: ev.optLong("source_id").takeIf { it != 0L }

                        // Check if event has inline homework
                        val subName = ev.optString("subject_name").ifEmpty { "Урок" }
                        val startAt = ev.optString("start_at")
                        val date = runCatching { LocalDate.parse(startAt.take(10)) }.getOrDefault(monday)

                        val hwRaw = ev.opt("homework")
                        if (hwRaw is String && hwRaw.isNotBlank() && hwRaw != "null") {
                            synchronized(gatheredItems) {
                                gatheredItems.add(
                                    HomeworkItem(
                                        id = "inline_${evId ?: i}",
                                        subject = subName,
                                        date = date,
                                        description = hwRaw,
                                        lessonId = evId,
                                        teacherName = teacherRepository.getTeacher(subName)
                                    )
                                )
                            }
                        }

                        if (evId != null && studentId != null) {
                            lessonIdsToFetch.add(evId)
                        }
                    }

                    if (lessonIdsToFetch.isNotEmpty() && studentId != null) {
                        fetchLessonDetailsBatch(session, studentId, lessonIdsToFetch) { detailItems ->
                            synchronized(gatheredItems) {
                                gatheredItems.addAll(detailItems)
                            }
                            finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
                        }
                    } else {
                        finishWithResult(session, cacheKey, gatheredItems, monday, onComplete)
                    }
                }
            }
        })
    }

    private fun fetchLessonDetailsBatch(
        session: AuthSession,
        studentId: Long,
        lessonIds: List<Long>,
        onDone: (List<HomeworkItem>) -> Unit
    ) {
        val results = mutableListOf<HomeworkItem>()
        val latch = CountDownLatch(lessonIds.size)

        for (lessonId in lessonIds) {
            lessonDetailsCache[lessonId]?.let { cachedDetail ->
                results.addAll(parseLessonHomeworks(cachedDetail))
                latch.countDown()
                return@let
            }

            val detailUrl = "https://${session.region.apiHost}/api/family/web/v1/lesson_schedule_items" +
                    "/$lessonId?student_id=$studentId"
            val req = Request.Builder()
                .url(detailUrl)
                .addHeader("Authorization", "Bearer ${session.token}")
                .addHeader("X-Mes-Subsystem", "familyweb")
                .addHeader("X-Mes-Role", "student")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    latch.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            runCatching {
                                val json = JSONObject(body)
                                lessonDetailsCache[lessonId] = json
                                val parsed = parseLessonHomeworks(json)
                                synchronized(results) {
                                    results.addAll(parsed)
                                }
                            }
                        }
                    }
                    latch.countDown()
                }
            })
        }

        Thread {
            latch.await(10, TimeUnit.SECONDS)
            onDone(results)
        }.start()
    }

    private fun finishWithResult(
        session: AuthSession,
        cacheKey: String,
        gatheredItems: List<HomeworkItem>,
        monday: LocalDate,
        onComplete: (Result<List<HomeworkItem>>) -> Unit
    ) {
        // Deduplicate gathered items
        val distinct = gatheredItems
            .distinctBy { it.homeworkEntryStudentId ?: it.homeworkEntryId ?: it.id }
            .sortedWith(compareBy({ it.date }, { it.subject }))

        val finalItems = if (distinct.isNotEmpty()) {
            distinct
        } else {
            // For authenticated users, empty list means there is truly no homework for this period!
            // Never fall back to mock data when logged in.
            if (session.token.isNotBlank()) {
                emptyList()
            } else {
                getMockHomeworks(monday)
            }
        }

        memoryCache[cacheKey] = finalItems
        saveHomeworksToPrefs(cacheKey, finalItems)
        onComplete(Result.success(finalItems))
    }

    fun toggleHomeworkDone(
        session: AuthSession,
        item: HomeworkItem,
        onComplete: (Result<Boolean>) -> Unit
    ) {
        val targetState = !item.isDone
        val studentEntryId = item.homeworkEntryStudentId

        // Optimistically update memory and prefs cache
        updateItemInCache(session, item.id, targetState)

        if (studentEntryId == null) {
            onComplete(Result.success(targetState))
            return
        }

        val url = "https://${session.region.apiHost}/api/family/mobile/v1/homeworks/$studentEntryId/done?type=oo"
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${session.token}")
            .addHeader("X-Mes-Subsystem", "familymp")
            .addHeader("X-Mes-Role", "student")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")

        val request = if (targetState) {
            requestBuilder.post(ByteArray(0).toRequestBody()).build()
        } else {
            requestBuilder.delete().build()
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HomeworkRepo", "Failed to update homework done status: ${e.message}")
                // On failure, rollback local state
                updateItemInCache(session, item.id, item.isDone)
                onComplete(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        onComplete(Result.success(targetState))
                    } else {
                        Log.e("HomeworkRepo", "Server returned ${resp.code} for done toggle")
                        updateItemInCache(session, item.id, item.isDone)
                        onComplete(Result.failure(IOException("HTTP ${resp.code}")))
                    }
                }
            }
        })
    }

    private fun updateItemInCache(session: AuthSession, itemId: String, isDone: Boolean) {
        for ((key, items) in memoryCache) {
            if (items.any { it.id == itemId }) {
                val updated = items.map {
                    if (it.id == itemId) it.copy(isDone = isDone) else it
                }
                memoryCache[key] = updated
                saveHomeworksToPrefs(key, updated)
            }
        }
    }

    private fun parseLessonHomeworks(json: JSONObject): List<HomeworkItem> {
        val list = mutableListOf<HomeworkItem>()
        val lessonId = json.optLong("id").takeIf { it != 0L }
        val subjectName = json.optString("subject_name").ifEmpty { "Урок" }
        val dateStr = json.optString("date")
        val lessonDate = runCatching { LocalDate.parse(dateStr) }.getOrDefault(LocalDate.now())

        val teacherObj = json.optJSONObject("teacher")
        val teacherFromJson = teacherObj?.let {
            val last = it.optString("last_name")
            val first = it.optString("first_name")
            val mid = it.optString("middle_name")
            listOf(last, first, mid).filter { s -> s.isNotBlank() }.joinToString(" ")
        }?.takeIf { it.isNotBlank() }

        if (teacherFromJson != null) {
            teacherRepository.saveTeacher(subjectName, teacherFromJson)
        }
        val resolvedTeacher = teacherFromJson ?: teacherRepository.getTeacher(subjectName)

        val hwArray = json.optJSONArray("lesson_homeworks") ?: JSONArray()
        for (i in 0 until hwArray.length()) {
            val hw = hwArray.optJSONObject(i) ?: continue
            val desc = hw.optString("homework").ifEmpty {
                hw.optString("description")
            }
            if (desc.isBlank()) continue

            val hwId = hw.optLong("homework_id").takeIf { it != 0L }
            val hwEntryId = hw.optLong("homework_entry_id").takeIf { it != 0L }
            val hwEntryStudentId = hw.optLong("homework_entry_student_id").takeIf { it != 0L }
            val isDone = hw.optBoolean("is_done", false)

            val datePreparedFor = hw.optString("date_prepared_for").take(10).let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: lessonDate

            val dateAssigned = hw.optString("date_assigned_on").take(10).let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }

            // Parse attachments
            val attachmentsList = mutableListOf<HomeworkAttachment>()
            val attachArray = hw.optJSONArray("attachments")
            if (attachArray != null) {
                for (j in 0 until attachArray.length()) {
                    val att = attachArray.optJSONObject(j) ?: continue
                    attachmentsList.add(
                        HomeworkAttachment(
                            id = att.optString("id"),
                            name = att.optString("name").ifEmpty { "Файл" },
                            url = att.optString("url").ifEmpty { att.optString("path") }
                        )
                    )
                }
            }

            // Parse additional materials
            val materialsList = mutableListOf<HomeworkMaterial>()
            val matArray = hw.optJSONArray("additional_materials")
            if (matArray != null) {
                for (j in 0 until matArray.length()) {
                    val mat = matArray.optJSONObject(j) ?: continue
                    val title = mat.optString("title").ifEmpty { mat.optString("type_name") }
                    if (title.isNotBlank()) {
                        materialsList.add(
                            HomeworkMaterial(
                                uuid = mat.optString("uuid"),
                                title = title,
                                typeName = mat.optString("type_name"),
                                actionName = mat.optString("action_name"),
                                url = mat.optJSONArray("urls")?.optString(0)
                            )
                        )
                    }
                }
            }

            val stableId = hwEntryStudentId?.toString()
                ?: hwEntryId?.toString()
                ?: "${lessonId}_$i"

            list.add(
                HomeworkItem(
                    id = stableId,
                    homeworkId = hwId,
                    homeworkEntryId = hwEntryId,
                    homeworkEntryStudentId = hwEntryStudentId,
                    subject = subjectName,
                    date = datePreparedFor,
                    assignedDate = dateAssigned,
                    description = desc,
                    isDone = isDone,
                    attachments = attachmentsList,
                    materials = materialsList,
                    teacherName = resolvedTeacher,
                    lessonId = lessonId,
                    isSmart = hw.optBoolean("is_smart", false)
                )
            )
        }

        return list
    }

    private fun parseMobileHomeworksPayload(payload: JSONArray): List<HomeworkItem> {
        val list = mutableListOf<HomeworkItem>()
        for (i in 0 until payload.length()) {
            val item = payload.optJSONObject(i) ?: continue
            val desc = item.optString("homework").ifEmpty { item.optString("description") }
            if (desc.isBlank()) continue

            val subject = item.optString("subject_name").ifEmpty { "Урок" }
            val datePreparedStr = item.optString("date_prepared_for").take(10).ifEmpty {
                item.optString("date").take(10)
            }
            val date = runCatching { LocalDate.parse(datePreparedStr) }.getOrDefault(LocalDate.now())

            val assignedDateStr = item.optString("date_assigned_on").take(10)
            val assignedDate = runCatching { LocalDate.parse(assignedDateStr) }.getOrNull()

            val isDone = item.optBoolean("is_done", false)
            val hwEntryStudentId = item.optLong("homework_entry_student_id").takeIf { it != 0L }
            val hwEntryId = item.optLong("homework_entry_id").takeIf { it != 0L }
            val hwId = item.optLong("homework_id").takeIf { it != 0L }

            // Parse materials
            val materialsList = mutableListOf<HomeworkMaterial>()
            val matArray = item.optJSONArray("materials")
            if (matArray != null) {
                for (j in 0 until matArray.length()) {
                    val mat = matArray.optJSONObject(j) ?: continue
                    val title = mat.optString("title").ifEmpty { mat.optString("type_name") }
                    if (title.isNotBlank()) {
                        materialsList.add(
                            HomeworkMaterial(
                                uuid = mat.optString("uuid"),
                                title = title,
                                typeName = mat.optString("type_name"),
                                actionName = mat.optString("action_name"),
                                url = mat.optJSONArray("urls")?.optString(0)
                            )
                        )
                    }
                }
            }

            // Parse attachments
            val attachmentsList = mutableListOf<HomeworkAttachment>()
            val attachArray = item.optJSONArray("attachments")
            if (attachArray != null) {
                for (j in 0 until attachArray.length()) {
                    val att = attachArray.optJSONObject(j) ?: continue
                    attachmentsList.add(
                        HomeworkAttachment(
                            id = att.optString("id"),
                            name = att.optString("name").ifEmpty { "Файл" },
                            url = att.optString("url").ifEmpty { att.optString("path") }
                        )
                    )
                }
            }

            val teacherName = teacherRepository.getTeacher(subject)

            list.add(
                HomeworkItem(
                    id = hwEntryStudentId?.toString() ?: hwEntryId?.toString() ?: "mobile_$i",
                    homeworkId = hwId,
                    homeworkEntryId = hwEntryId,
                    homeworkEntryStudentId = hwEntryStudentId,
                    subject = subject,
                    date = date,
                    assignedDate = assignedDate,
                    description = desc,
                    isDone = isDone,
                    attachments = attachmentsList,
                    materials = materialsList,
                    teacherName = teacherName,
                    isSmart = item.optBoolean("is_smart", false)
                )
            )
        }
        return list
    }

    private fun saveHomeworksToPrefs(key: String, items: List<HomeworkItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
                .put("id", item.id)
                .put("homework_id", item.homeworkId ?: 0L)
                .put("homework_entry_id", item.homeworkEntryId ?: 0L)
                .put("homework_entry_student_id", item.homeworkEntryStudentId ?: 0L)
                .put("subject", item.subject)
                .put("date", item.date.toString())
                .put("assigned_date", item.assignedDate?.toString())
                .put("description", item.description)
                .put("is_done", item.isDone)
                .put("teacher_name", item.teacherName)
                .put("lesson_id", item.lessonId ?: 0L)
                .put("is_smart", item.isSmart)
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun parseHomeworkList(arr: JSONArray): List<HomeworkItem> {
        val list = mutableListOf<HomeworkItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val sub = obj.optString("subject")
            val desc = obj.optString("description")
            val date = runCatching { LocalDate.parse(obj.optString("date")) }.getOrDefault(LocalDate.now())
            val isDone = obj.optBoolean("is_done", false)
            val hwStudentId = obj.optLong("homework_entry_student_id").takeIf { it != 0L }

            val teacherName = obj.optString("teacher_name").takeIf { it.isNotBlank() && it != "null" }
                ?: teacherRepository.getTeacher(sub)

            list.add(
                HomeworkItem(
                    id = id,
                    homeworkId = obj.optLong("homework_id").takeIf { it != 0L },
                    homeworkEntryId = obj.optLong("homework_entry_id").takeIf { it != 0L },
                    homeworkEntryStudentId = hwStudentId,
                    subject = sub,
                    date = date,
                    assignedDate = obj.optString("assigned_date").takeIf { it.isNotBlank() && it != "null" }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    description = desc,
                    isDone = isDone,
                    teacherName = teacherName,
                    lessonId = obj.optLong("lesson_id").takeIf { it != 0L },
                    isSmart = obj.optBoolean("is_smart", false)
                )
            )
        }
        return list
    }

    fun getMockHomeworks(anchorDate: LocalDate): List<HomeworkItem> {
        val monday = anchorDate.with(DayOfWeek.MONDAY)
        return listOf(
            HomeworkItem(
                id = "mock_1",
                homeworkEntryStudentId = 1001L,
                subject = "Русский язык",
                date = monday,
                description = "Упражнение 45 (письменно в тетради), повторить правила орфографии",
                isDone = false,
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_2",
                homeworkEntryStudentId = 1002L,
                subject = "Химия",
                date = monday,
                description = "§9 и §10, выполнить упражнения 1-4 письменно в тетради",
                isDone = false,
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_3",
                homeworkEntryStudentId = 1003L,
                subject = "Биология",
                date = monday.plusDays(1),
                description = "§7 читать, ответить на вопросы в конце параграфа устно",
                isDone = true,
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_4",
                homeworkEntryStudentId = 1004L,
                subject = "Английский язык",
                date = monday.plusDays(1),
                description = "Учебник: стр. 22 №60 (читать, переводить), рабочая тетрадь: стр. 13 №5",
                isDone = false,
                materials = listOf(
                    HomeworkMaterial(
                        title = "Online Practice Unit 2",
                        typeName = "Интерактивное задание"
                    )
                ),
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_5",
                homeworkEntryStudentId = 1005L,
                subject = "Геометрия",
                date = monday.plusDays(2),
                description = "Выучить теорему о сумме углов треугольника и следствия из неё",
                isDone = false,
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_6",
                homeworkEntryStudentId = 1006L,
                subject = "Литература",
                date = monday.plusDays(3),
                description = "Знать содержание повести «Капитанская дочка», прочесть главы 3-5",
                isDone = false,
                teacherName = null
            ),
            HomeworkItem(
                id = "mock_7",
                homeworkEntryStudentId = 1007L,
                subject = "Физика",
                date = monday.plusDays(4),
                description = "§8-12 доклад: «Практическое использование тепловых свойств веществ в энергосбережении»",
                isDone = false,
                teacherName = null
            )
        )
    }
}
