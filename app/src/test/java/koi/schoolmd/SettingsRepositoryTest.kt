package koi.schoolmd

import koi.schoolmd.data.AppThemeMode
import koi.schoolmd.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun testDefaultSettings() {
        val repo = SettingsRepository()
        assertEquals(AppThemeMode.SYSTEM, repo.getThemeMode())
        assertTrue(repo.isAutoRefreshEnabled())
    }

    @Test
    fun testThemeModeUpdate() {
        val repo = SettingsRepository()
        repo.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, repo.getThemeMode())

        repo.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, repo.getThemeMode())
    }

    @Test
    fun testAutoRefreshToggle() {
        val repo = SettingsRepository()
        repo.setAutoRefreshEnabled(false)
        assertFalse(repo.isAutoRefreshEnabled())

        repo.setAutoRefreshEnabled(true)
        assertTrue(repo.isAutoRefreshEnabled())
    }
}
