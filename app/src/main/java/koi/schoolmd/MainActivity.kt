package koi.schoolmd

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import koi.schoolmd.data.AppThemeMode
import koi.schoolmd.data.AuthRepository
import koi.schoolmd.data.AuthSession
import koi.schoolmd.data.HomeworkRepository
import koi.schoolmd.data.MarksRepository
import koi.schoolmd.data.Region
import koi.schoolmd.data.ScheduleRepository
import koi.schoolmd.data.SettingsRepository
import koi.schoolmd.data.TeacherRepository
import koi.schoolmd.ui.screens.DashboardScreen
import koi.schoolmd.ui.screens.LoginScreen
import koi.schoolmd.ui.theme.SchoolTheme

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var homeworkRepository: HomeworkRepository
    private lateinit var marksRepository: MarksRepository
    private lateinit var settingsRepository: SettingsRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val teacherRepository = TeacherRepository(applicationContext)
        authRepository = AuthRepository(applicationContext)
        scheduleRepository = ScheduleRepository(applicationContext, teacherRepository)
        homeworkRepository = HomeworkRepository(applicationContext, scheduleRepository, teacherRepository)
        marksRepository = MarksRepository(applicationContext, scheduleRepository, teacherRepository)
        settingsRepository = SettingsRepository(applicationContext)

        intent.getStringExtra("token")?.takeIf { it.isNotBlank() }?.let { intentToken ->
            val regName = intent.getStringExtra("region")
            val reg = if (regName != null) Region.fromName(regName) else Region.MOSCOW_REGION
            authRepository.saveSession(reg, intentToken)
        }

        setContent {
            var currentSession by remember {
                mutableStateOf(authRepository.getSession())
            }
            var themeMode by remember {
                mutableStateOf(settingsRepository.getThemeMode())
            }
            var isAutoRefreshEnabled by remember {
                mutableStateOf(settingsRepository.isAutoRefreshEnabled())
            }

            // Auto-refresh token on startup using saved cookies
            LaunchedEffect(Unit) {
                if (settingsRepository.isAutoRefreshEnabled()) {
                    val sess = authRepository.getSession()
                    if (sess != null) {
                        authRepository.testOrRefreshToken(sess) { result ->
                            result.onSuccess {
                                mainHandler.post {
                                    currentSession = authRepository.getSession()
                                }
                            }
                        }
                    }
                }
            }

            LaunchedEffect(currentSession?.token) {
                val sess = currentSession
                if (sess != null && sess.studentProfile == null) {
                    scheduleRepository.fetchProfile(sess) { result ->
                        result.onSuccess { profile ->
                            authRepository.saveStudentProfile(profile)
                            mainHandler.post {
                                currentSession = authRepository.getSession()
                            }
                        }
                    }
                }
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            SchoolTheme(darkTheme = isDark) {
                val session = currentSession
                if (session != null) {
                    DashboardScreen(
                        session = session,
                        scheduleRepository = scheduleRepository,
                        homeworkRepository = homeworkRepository,
                        marksRepository = marksRepository,
                        themeMode = themeMode,
                        onThemeModeChanged = { mode ->
                            settingsRepository.setThemeMode(mode)
                            themeMode = mode
                        },
                        isAutoRefreshEnabled = isAutoRefreshEnabled,
                        onAutoRefreshChanged = { enabled ->
                            settingsRepository.setAutoRefreshEnabled(enabled)
                            isAutoRefreshEnabled = enabled
                        },
                        onAvatarChanged = { uri ->
                            authRepository.saveCustomAvatar(uri)
                            currentSession = authRepository.getSession()
                        },
                        onNameChanged = { name ->
                            authRepository.saveCustomName(name)
                            currentSession = authRepository.getSession()
                        },
                        onLogout = {
                            authRepository.clearSession()
                            scheduleRepository.clearCache()
                            homeworkRepository.clearCache()
                            marksRepository.clearCache()
                            currentSession = null
                        },
                        onRefreshToken = { callback ->
                            authRepository.testOrRefreshToken(session) { result ->
                                mainHandler.post {
                                    if (result.isSuccess) {
                                        currentSession = authRepository.getSession()
                                    }
                                    callback(result)
                                }
                            }
                        },
                        onProfileLoaded = { profile ->
                            authRepository.saveStudentProfile(profile)
                            mainHandler.post {
                                currentSession = authRepository.getSession()
                            }
                        }
                    )
                } else {
                    LoginScreen(
                        onLoginSuccess = { region, token ->
                            val newSession = authRepository.saveSession(region, token)
                            currentSession = newSession
                        },
                        onCookiesCaptured = { cookies ->
                            authRepository.saveCookies(cookies)
                        }
                    )
                }
            }
        }
    }
}
