package koi.schoolmd

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import koi.schoolmd.data.DayMarks
import koi.schoolmd.data.MarkItem
import koi.schoolmd.data.SubjectMarksSummary
import java.time.DayOfWeek
import java.time.LocalDate

class MarksParserTest {

    @Test
    fun testParseMarksPayload() {
        val payloadJson = """
        [
          {
            "subject_name": "Русский язык",
            "subject_id": 101,
            "average_by_all": "4.80",
            "periods": [
              {
                "title": "1 четверть",
                "marks": [
                  {
                    "id": 9001,
                    "value": "5",
                    "weight": 1,
                    "date": "2026-09-29 00:00:00",
                    "control_form_name": "Ответ на уроке",
                    "comment": "Отличный ответ",
                    "is_exam": false,
                    "is_point": false
                  },
                  {
                    "id": 9002,
                    "value": "4",
                    "weight": 2,
                    "date": "2026-09-30 00:00:00",
                    "control_form_name": "Контрольный диктант",
                    "comment": "",
                    "is_exam": true,
                    "is_point": false
                  }
                ]
              }
            ]
          },
          {
            "subject_name": "Алгебра",
            "subject_id": 102,
            "average_by_all": "5.00",
            "periods": [
              {
                "title": "1 четверть",
                "marks": [
                  {
                    "id": 9003,
                    "value": "5",
                    "weight": 1,
                    "date": "2026-09-29 00:00:00",
                    "control_form_name": "Самостоятельная работа",
                    "comment": null,
                    "is_exam": false,
                    "is_point": false
                  }
                ]
              }
            ]
          }
        ]
        """.trimIndent()

        val jsonArray = JSONArray(payloadJson)
        val marks = mutableListOf<MarkItem>()
        val summaries = mutableListOf<SubjectMarksSummary>()

        for (i in 0 until jsonArray.length()) {
            val subObj = jsonArray.getJSONObject(i)
            val subName = subObj.getString("subject_name")
            val subId = subObj.optLong("subject_id")
            val avg = subObj.optString("average_by_all")
            var marksCount = 0

            val periods = subObj.getJSONArray("periods")
            for (p in 0 until periods.length()) {
                val periodObj = periods.getJSONObject(p)
                val marksArr = periodObj.getJSONArray("marks")
                for (m in 0 until marksArr.length()) {
                    val mObj = marksArr.getJSONObject(m)
                    val value = mObj.getString("value")
                    val date = LocalDate.parse(mObj.getString("date").take(10))
                    marks.add(
                        MarkItem(
                            id = mObj.getLong("id"),
                            value = value,
                            weight = mObj.getInt("weight"),
                            subject = subName,
                            date = date,
                            controlFormName = mObj.getString("control_form_name"),
                            comment = mObj.optString("comment").takeIf { it.isNotBlank() },
                            isExam = mObj.getBoolean("is_exam"),
                            isPoint = mObj.getBoolean("is_point")
                        )
                    )
                    marksCount++
                }
            }

            summaries.add(
                SubjectMarksSummary(
                    subjectName = subName,
                    subjectId = subId,
                    averageMark = avg,
                    marksCount = marksCount,
                    periodTitle = "1 четверть"
                )
            )
        }

        assertEquals(3, marks.size)
        assertEquals(2, summaries.size)

        val firstMark = marks.first { it.id == 9001L }
        assertEquals("5", firstMark.value)
        assertEquals(1, firstMark.weight)
        assertEquals("Русский язык", firstMark.subject)
        assertEquals(LocalDate.of(2026, 9, 29), firstMark.date)
        assertEquals("Ответ на уроке", firstMark.controlFormName)
        assertEquals("Отличный ответ", firstMark.comment)
        assertFalse(firstMark.isExam)

        val examMark = marks.first { it.id == 9002L }
        assertEquals("4", examMark.value)
        assertEquals(2, examMark.weight)
        assertTrue(examMark.isExam)

        val rusSummary = summaries.first { it.subjectName == "Русский язык" }
        assertEquals("4.80", rusSummary.averageMark)
        assertEquals(2, rusSummary.marksCount)
    }

    @Test
    fun testGetMarksForWeekGrouping() {
        val weekAnchor = LocalDate.of(2026, 9, 30) // Wednesday
        val monday = weekAnchor.with(DayOfWeek.MONDAY) // 2026-09-28
        val sunday = monday.plusDays(6) // 2026-10-04

        val items = listOf(
            MarkItem(
                id = 1,
                value = "5",
                subject = "Русский язык",
                date = LocalDate.of(2026, 9, 28) // Monday (in week)
            ),
            MarkItem(
                id = 2,
                value = "4",
                subject = "Алгебра",
                date = LocalDate.of(2026, 9, 30) // Wednesday (in week)
            ),
            MarkItem(
                id = 3,
                value = "5",
                subject = "Физика",
                date = LocalDate.of(2026, 9, 30) // Wednesday (in week)
            ),
            MarkItem(
                id = 4,
                value = "3",
                subject = "Химия",
                date = LocalDate.of(2026, 10, 6) // Next week Tuesday (out of week)
            ),
            MarkItem(
                id = 5,
                value = "5",
                subject = "История",
                date = LocalDate.of(2026, 9, 21) // Previous week Monday (out of week)
            )
        )

        val weekMarks = items
            .filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
            .groupBy { it.date }
            .map { (date, marks) -> DayMarks(date = date, marks = marks) }
            .sortedByDescending { it.date }

        assertEquals(2, weekMarks.size)
        // Most recent date first
        assertEquals(LocalDate.of(2026, 9, 30), weekMarks[0].date)
        assertEquals(2, weekMarks[0].marks.size)

        assertEquals(LocalDate.of(2026, 9, 28), weekMarks[1].date)
        assertEquals(1, weekMarks[1].marks.size)
    }

    @Test
    fun testWeightedAverageCalculation() {
        val marks = listOf(
            MarkItem(id = 1, value = "5", weight = 1, subject = "История", date = LocalDate.now()),
            MarkItem(id = 2, value = "4", weight = 2, subject = "История", date = LocalDate.now()),
            MarkItem(id = 3, value = "5", weight = 1, subject = "История", date = LocalDate.now())
        )

        // (5*1 + 4*2 + 5*1) / (1 + 2 + 1) = (5 + 8 + 5) / 4 = 18 / 4 = 4.5
        val numeric = marks.mapNotNull { it.value.toDoubleOrNull()?.let { v -> Pair(v, it.weight) } }
        val totalWeight = numeric.sumOf { it.second }
        val weightedSum = numeric.sumOf { it.first * it.second }
        val avg = weightedSum / totalWeight

        assertEquals(4.5, avg, 0.001)
    }
}
