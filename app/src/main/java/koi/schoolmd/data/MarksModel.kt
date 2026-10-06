package koi.schoolmd.data

import java.time.LocalDate

data class MarkItem(
    val id: Long,
    val value: String,
    val weight: Int = 1,
    val subject: String,
    val date: LocalDate,
    val controlFormName: String = "",
    val comment: String? = null,
    val isExam: Boolean = false,
    val isPoint: Boolean = false,
    val teacherName: String? = null
)

data class DayMarks(
    val date: LocalDate,
    val marks: List<MarkItem>
)

data class SubjectMarksSummary(
    val subjectName: String,
    val subjectId: Long? = null,
    val averageMark: String? = null,
    val marksCount: Int = 0,
    val periodTitle: String? = null
)
