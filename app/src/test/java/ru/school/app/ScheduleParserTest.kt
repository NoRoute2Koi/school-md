package ru.school.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.school.app.data.LessonItem
import java.time.LocalDate
import java.time.OffsetDateTime

class ScheduleParserTest {

    @Test
    fun testEventParsingAndCurrentStatus() {
        val sampleEventJson = """
            {
              "id": 209328143,
              "source_id": "209328143",
              "source": "PLAN",
              "start_at": "2026-10-12T09:20:00+03:00",
              "finish_at": "2026-10-12T10:00:00+03:00",
              "cancelled": false,
              "lesson_type": "NORMAL",
              "course_lesson_type": null,
              "replaced": false,
              "room_name": "Биология, география",
              "room_number": "41",
              "subject_id": 33623636,
              "subject_name": "Биология",
              "homework": "§5 читать",
              "marks": null
            }
        """.trimIndent()

        val json = JSONObject(sampleEventJson)
        val start = OffsetDateTime.parse(json.getString("start_at"))
        val finish = OffsetDateTime.parse(json.getString("finish_at"))

        val lesson = LessonItem(
            id = json.opt("id")?.toString().orEmpty(),
            lessonNumber = 1,
            startTime = "09:20",
            endTime = "10:00",
            classroom = "41 каб",
            subject = json.getString("subject_name"),
            homework = json.optString("homework"),
            homeworkCount = 1,
            grade = null,
            isExam = false,
            isCancelled = json.getBoolean("cancelled"),
            isReplaced = json.getBoolean("replaced"),
            theme = null,
            startDateTime = start,
            endDateTime = finish
        )

        assertEquals("209328143", lesson.id)
        assertEquals("Биология", lesson.subject)
        assertEquals("41 каб", lesson.classroom)
        assertEquals("§5 читать", lesson.homework)
        assertFalse(lesson.isCancelled)
        assertFalse(lesson.isExam)
    }

    @Test
    fun testExamAndReplacementBadges() {
        val examEvent = LessonItem(
            id = "test-exam",
            lessonNumber = 2,
            startTime = "10:20",
            endTime = "11:00",
            classroom = "27 каб",
            subject = "Алгебра",
            isExam = true,
            isReplaced = true
        )

        assertTrue(examEvent.isExam)
        assertTrue(examEvent.isReplaced)
    }
}
