package koi.schoolmd.data

import java.time.LocalDate

data class HomeworkAttachment(
    val id: String? = null,
    val name: String,
    val url: String? = null
)

data class HomeworkMaterial(
    val uuid: String? = null,
    val title: String,
    val typeName: String? = null,
    val actionName: String? = null,
    val url: String? = null
)

data class HomeworkItem(
    val id: String,
    val homeworkId: Long? = null,
    val homeworkEntryId: Long? = null,
    val homeworkEntryStudentId: Long? = null,
    val subject: String,
    val date: LocalDate, // Due date (lesson date)
    val assignedDate: LocalDate? = null,
    val description: String,
    val isDone: Boolean = false,
    val attachments: List<HomeworkAttachment> = emptyList(),
    val materials: List<HomeworkMaterial> = emptyList(),
    val teacherName: String? = null,
    val lessonId: Long? = null,
    val isSmart: Boolean = false
)

enum class HomeworkStatusFilter(val title: String) {
    ALL("Все"),
    PENDING("К сдаче"),
    COMPLETED("Выполнено")
}
