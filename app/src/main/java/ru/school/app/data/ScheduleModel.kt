package ru.school.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

data class LessonItem(
    val id: String,
    val lessonNumber: Int,
    val startTime: String,
    val endTime: String,
    val classroom: String,
    val subject: String,
    val homework: String? = null,
    val homeworkCount: Int = 0,
    val grade: String? = null,
    val isExam: Boolean = false,
    val isCancelled: Boolean = false,
    val isReplaced: Boolean = false,
    val theme: String? = null,
    val startDateTime: OffsetDateTime? = null,
    val endDateTime: OffsetDateTime? = null
) {
    val isCurrent: Boolean
        get() {
            if (startDateTime == null || endDateTime == null) return false
            val now = OffsetDateTime.now()
            return !now.isBefore(startDateTime) && !now.isAfter(endDateTime)
        }
}

data class DaySchedule(
    val date: LocalDate,
    val dayName: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val lessons: List<LessonItem>
)

data class StudentProfile(
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val className: String? = null,
    val schoolName: String? = null,
    val contingentGuid: String? = null,
    val studentId: Long? = null
) {
    val fullName: String
        get() = listOfNotNull(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
            .ifEmpty { "Пользователь" }

    val schoolInfo: String?
        get() = listOfNotNull(className?.let { "$it класс" }, schoolName).filter { it.isNotBlank() }
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
}

object MockScheduleRepository {
    fun getWeekSchedule(anchorDate: LocalDate = LocalDate.now()): List<DaySchedule> {
        val russianLocale = Locale("ru", "RU")
        val monday = anchorDate.with(DayOfWeek.MONDAY)

        return (0..5).map { offset ->
            val date = monday.plusDays(offset.toLong())
            val rawDayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, russianLocale)
                .replace(".", "")
                .replaceFirstChar { it.uppercase() }

            val lessons = createLessonsForDay(date.dayOfWeek)
            DaySchedule(
                date = date,
                dayName = rawDayName,
                dayNumber = date.dayOfMonth,
                isToday = date == LocalDate.now(),
                lessons = lessons
            )
        }
    }

    private fun createLessonsForDay(day: DayOfWeek): List<LessonItem> {
        return when (day) {
            DayOfWeek.MONDAY -> listOf(
                LessonItem("m1", 1, "08:30", "09:10", "27 каб", "Алгебра", "№ 234, 236", 1, "5"),
                LessonItem("m2", 2, "09:25", "10:05", "14 каб", "Русский язык", "Упр. 152", 1),
                LessonItem("m3", 3, "10:20", "11:00", "14 каб", "Литература", "Читать стр. 45-60", 1),
                LessonItem("m4", 4, "11:20", "12:00", "31 каб", "Физика", null, 0, "4"),
                LessonItem("m5", 5, "12:20", "13:00", "18 каб", "История", "Параграф 14", 1)
            )
            DayOfWeek.TUESDAY -> listOf(
                LessonItem("t1", 1, "08:30", "09:10", "27 каб", "Геометрия", "Теорема 3.2", 1),
                LessonItem("t2", 2, "09:25", "10:05", "12 каб", "Английский язык", "Unit 4 ex. 3, 4", 2, "5"),
                LessonItem("t3", 3, "10:20", "11:00", "22 каб", "Химия", "Конспект §8", 1),
                LessonItem("t4", 4, "11:20", "12:00", "Спортзал", "Физкультура", null, 0),
                LessonItem("t5", 5, "12:20", "13:00", "10 каб", "Информатика", "Практическая работа №5", 1)
            )
            DayOfWeek.WEDNESDAY -> listOf(
                LessonItem("w1", 1, "08:30", "09:10", "27 каб", "Алгебра", "№ 240, 241", 1),
                LessonItem("w2", 2, "09:25", "10:05", "19 каб", "Биология", "Заполнить таблицу", 1, "5"),
                LessonItem("w3", 3, "10:20", "11:00", "15 каб", "Обществознание", "Вопросы 1-5", 1),
                LessonItem("w4", 4, "11:20", "12:00", "14 каб", "Русский язык", "Сочинение", 1),
                LessonItem("w5", 5, "12:20", "13:00", "11 каб", "География", null, 0)
            )
            DayOfWeek.THURSDAY -> listOf(
                LessonItem("th1", 1, "08:30", "09:10", "31 каб", "Физика", "Задачи 4.1-4.3", 1),
                LessonItem("th2", 2, "09:25", "10:05", "27 каб", "Геометрия", "№ 105", 1, "4"),
                LessonItem("th3", 3, "10:20", "11:00", "12 каб", "Английский язык", "Слова к диктанту", 1),
                LessonItem("th4", 4, "11:20", "12:00", "14 каб", "Литература", "Выучить стихотворение", 1, "5"),
                LessonItem("th5", 5, "12:20", "13:00", "Спортзал", "Физкультура", null, 0)
            )
            DayOfWeek.FRIDAY -> listOf(
                LessonItem("f1", 1, "08:30", "09:10", "27 каб", "Алгебра", "Контрольная подготовка", 1, isExam = true),
                LessonItem("f2", 2, "09:25", "10:05", "14 каб", "Русский язык", "Правило стр. 112", 1),
                LessonItem("f3", 3, "10:20", "11:00", "18 каб", "История", "Доклад", 1, "5"),
                LessonItem("f4", 4, "11:20", "12:00", "22 каб", "Химия", "Лабораторная работа", 1),
                LessonItem("f5", 5, "12:20", "13:00", "10 каб", "Информатика", null, 0)
            )
            else -> listOf(
                LessonItem("s1", 1, "09:00", "09:40", "Актовый зал", "Классный час", null, 0),
                LessonItem("s2", 2, "09:55", "10:35", "15 каб", "Профориентация", null, 0)
            )
        }
    }
}
