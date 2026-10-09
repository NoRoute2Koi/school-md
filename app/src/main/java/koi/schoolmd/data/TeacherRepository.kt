package koi.schoolmd.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

class TeacherRepository(context: Context? = null) {

    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("school_teachers_prefs", Context.MODE_PRIVATE)

    private val cache = ConcurrentHashMap<String, String>()
    private val normalizedCache = ConcurrentHashMap<String, String>()

    init {
        // Load dynamically saved teachers from local device private preferences
        prefs?.all?.forEach { (k, v) ->
            if (v is String && v.isNotBlank()) {
                cache[k] = v
                normalizedCache[normalize(k)] = v
            }
        }
    }

    private fun normalize(name: String): String {
        var n = name.lowercase().trim()
        val removePrefixes = listOf("8а_", "группа ", "2 группа ")
        for (p in removePrefixes) {
            if (n.startsWith(p)) n = n.removePrefix(p)
        }
        val removeWords = listOf("иностранный (", ") язык", " язык", " класс")
        for (w in removeWords) {
            n = n.replace(w, "")
        }
        return n.trim()
    }

    fun getTeacher(subject: String): String? {
        val trimmed = subject.trim()
        cache[trimmed]?.let { return it }

        // Specific aliases
        val lower = trimmed.lowercase()
        if (lower.contains("обзр") || lower.contains("основы безопасности")) {
            cache.entries.firstOrNull { it.key.contains("безопасности", ignoreCase = true) || it.key.contains("обзр", ignoreCase = true) }?.let { return it.value }
        }
        if (lower.contains("физ-ра") || lower.contains("физкультура") || lower.contains("физическая культура")) {
            cache.entries.firstOrNull { it.key.contains("физ", ignoreCase = true) }?.let { return it.value }
        }
        if (lower.contains("английск") || lower.contains("english")) {
            cache.entries.firstOrNull { it.key.contains("английск", ignoreCase = true) }?.let { return it.value }
        }

        val normTarget = normalize(trimmed)
        if (normTarget.isNotEmpty()) {
            normalizedCache[normTarget]?.let { return it }
            for ((normSub, name) in normalizedCache) {
                if (normSub.isNotEmpty() && (normTarget.contains(normSub) || normSub.contains(normTarget))) {
                    return name
                }
            }
        }
        return null
    }

    fun saveTeacher(subject: String, teacherName: String) {
        val trimmedSub = subject.trim()
        val trimmedTeacher = teacherName.trim()
        if (trimmedSub.isNotBlank() && trimmedTeacher.isNotBlank()) {
            cache[trimmedSub] = trimmedTeacher
            normalizedCache[normalize(trimmedSub)] = trimmedTeacher
            prefs?.edit()?.putString(trimmedSub, trimmedTeacher)?.apply()
        }
    }

    fun clearCache() {
        cache.clear()
        normalizedCache.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
