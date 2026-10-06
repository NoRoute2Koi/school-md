package koi.schoolmd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import koi.schoolmd.data.TeacherRepository

class TeacherRepositoryTest {

    @Test
    fun testDynamicTeacherSaveAndRetrieve() {
        val repo = TeacherRepository()
        assertNull(repo.getTeacher("Физика"))

        repo.saveTeacher("Физика", "Преподаватель Физики")
        assertEquals("Преподаватель Физики", repo.getTeacher("Физика"))
    }

    @Test
    fun testSubjectNormalization() {
        val repo = TeacherRepository()
        repo.saveTeacher("Иностранный (английский) язык", "Преподаватель Английского")
        assertEquals("Преподаватель Английского", repo.getTeacher("Английский язык"))
        assertEquals("Преподаватель Английского", repo.getTeacher("Иностранный (английский) язык"))
    }

    @Test
    fun testAliasResolution() {
        val repo = TeacherRepository()
        repo.saveTeacher("Основы безопасности и защиты Родины", "Преподаватель ОБЗР")
        assertEquals("Преподаватель ОБЗР", repo.getTeacher("ОБЗР"))
        assertEquals("Преподаватель ОБЗР", repo.getTeacher("Основы безопасности"))
    }
}
