package koi.schoolmd

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import koi.schoolmd.data.HomeworkItem
import koi.schoolmd.data.HomeworkStatusFilter
import koi.schoolmd.ui.screens.formatHomeworkDateHeader
import java.time.LocalDate

class HomeworkParserTest {

    @Test
    fun testLessonHomeworkParsing() {
        val jsonString = """
        {
          "id": 100000001,
          "date": "2026-10-01",
          "subject_name": "Русский язык",
          "teacher": {
            "last_name": "Тестовый",
            "first_name": "Преподаватель",
            "middle_name": "Предметович"
          },
          "lesson_homeworks": [
            {
              "homework": "Выполнить упражнение 1",
              "homework_entry_student_id": 1000000001,
              "homework_id": 2000000001,
              "homework_entry_id": 3000000001,
              "attachments": [],
              "homework_created_at": "2026-09-29 16:22:00",
              "homework_updated_at": "2026-09-29 16:22:00",
              "is_done": false,
              "additional_materials": [
                {
                  "uuid": "00000000-0000-0000-0000-000000000001",
                  "title": "Тестовые материалы",
                  "type_name": "Тест",
                  "action_name": "Пройти"
                }
              ],
              "date_assigned_on": "2026-09-29T00:00:00",
              "date_prepared_for": "2026-10-01T00:00:00"
            }
          ]
        }
        """.trimIndent()

        val json = JSONObject(jsonString)
        val hwArray = json.getJSONArray("lesson_homeworks")
        val hwObj = hwArray.getJSONObject(0)

        val item = HomeworkItem(
            id = hwObj.getLong("homework_entry_student_id").toString(),
            homeworkId = hwObj.getLong("homework_id"),
            homeworkEntryId = hwObj.getLong("homework_entry_id"),
            homeworkEntryStudentId = hwObj.getLong("homework_entry_student_id"),
            subject = json.getString("subject_name"),
            date = LocalDate.parse(hwObj.getString("date_prepared_for").take(10)),
            description = hwObj.getString("homework"),
            isDone = hwObj.getBoolean("is_done")
        )

        assertEquals("1000000001", item.id)
        assertEquals("Русский язык", item.subject)
        assertEquals(LocalDate.of(2026, 10, 1), item.date)
        assertEquals("Выполнить упражнение 1", item.description)
        assertFalse(item.isDone)
    }

    @Test
    fun testHomeworkFilters() {
        val monday = LocalDate.of(2026, 10, 5)
        val items = listOf(
            HomeworkItem(
                id = "1",
                subject = "Алгебра",
                date = monday,
                description = "№ 100",
                isDone = true
            ),
            HomeworkItem(
                id = "2",
                subject = "Физика",
                date = monday.plusDays(1),
                description = "§5 задачи",
                isDone = false
            ),
            HomeworkItem(
                id = "3",
                subject = "Химия",
                date = monday.plusDays(2),
                description = "§8 тест",
                isDone = false
            )
        )

        val pending = items.filter { !it.isDone }
        val completed = items.filter { it.isDone }

        assertEquals(2, pending.size)
        assertEquals(1, completed.size)
        assertEquals("Алгебра", completed[0].subject)
    }

    @Test
    fun testFormatHomeworkDateHeader() {
        val anchorWednesday = LocalDate.of(2026, 10, 7)

        // Today
        assertEquals("На сегодня, 7 октября", formatHomeworkDateHeader(anchorWednesday, anchorWednesday))

        // Tomorrow
        val tomorrow = anchorWednesday.plusDays(1)
        assertEquals("На завтра, 8 октября", formatHomeworkDateHeader(tomorrow, anchorWednesday))

        // Yesterday
        val yesterday = anchorWednesday.minusDays(1)
        assertEquals("На вчера, 6 октября", formatHomeworkDateHeader(yesterday, anchorWednesday))

        // Other days (accusative)
        val friday = LocalDate.of(2026, 10, 9)
        assertEquals("На пятницу, 9 октября", formatHomeworkDateHeader(friday, anchorWednesday))

        val nextMonday = LocalDate.of(2026, 10, 12)
        assertEquals("На понедельник, 12 октября", formatHomeworkDateHeader(nextMonday, anchorWednesday))

        val nextTuesday = LocalDate.of(2026, 10, 13)
        assertEquals("На вторник, 13 октября", formatHomeworkDateHeader(nextTuesday, anchorWednesday))

        val nextWednesday = LocalDate.of(2026, 10, 14)
        assertEquals("На среду, 14 октября", formatHomeworkDateHeader(nextWednesday, anchorWednesday))

        val nextThursday = LocalDate.of(2026, 10, 15)
        assertEquals("На четверг, 15 октября", formatHomeworkDateHeader(nextThursday, anchorWednesday))

        val nextSaturday = LocalDate.of(2026, 10, 17)
        assertEquals("На субботу, 17 октября", formatHomeworkDateHeader(nextSaturday, anchorWednesday))

        val nextSunday = LocalDate.of(2026, 10, 18)
        assertEquals("На воскресенье, 18 октября", formatHomeworkDateHeader(nextSunday, anchorWednesday))
    }
}
