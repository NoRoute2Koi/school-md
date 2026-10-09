package koi.schoolmd.data

import android.content.Context
import android.content.SharedPreferences

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("school_settings_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(name!!) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isAutoRefreshEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_REFRESH, true)
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_AUTO_REFRESH = "app_auto_refresh_token"
    }
}
