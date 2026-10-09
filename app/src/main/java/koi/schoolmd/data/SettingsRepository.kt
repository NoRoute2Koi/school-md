package koi.schoolmd.data

import android.content.Context
import android.content.SharedPreferences

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

class SettingsRepository(context: Context? = null) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("school_settings_prefs", Context.MODE_PRIVATE)

    private var memoryThemeMode: AppThemeMode = AppThemeMode.SYSTEM
    private var memoryAutoRefresh: Boolean = true

    init {
        prefs?.let { p ->
            val name = p.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            memoryThemeMode = runCatching { AppThemeMode.valueOf(name!!) }.getOrDefault(AppThemeMode.SYSTEM)
            memoryAutoRefresh = p.getBoolean(KEY_AUTO_REFRESH, true)
        }
    }

    fun getThemeMode(): AppThemeMode {
        return prefs?.let { p ->
            val name = p.getString(KEY_THEME_MODE, memoryThemeMode.name)
            runCatching { AppThemeMode.valueOf(name!!) }.getOrDefault(AppThemeMode.SYSTEM)
        } ?: memoryThemeMode
    }

    fun setThemeMode(mode: AppThemeMode) {
        memoryThemeMode = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun isAutoRefreshEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_REFRESH, memoryAutoRefresh) ?: memoryAutoRefresh
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        memoryAutoRefresh = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_REFRESH, enabled)?.apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_AUTO_REFRESH = "app_auto_refresh_token"
    }
}
