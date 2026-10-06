package ru.school.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ru.school.app.data.AuthRepository
import ru.school.app.data.AuthSession
import ru.school.app.data.Region
import ru.school.app.data.ScheduleRepository
import ru.school.app.ui.screens.DashboardScreen
import ru.school.app.ui.screens.LoginScreen
import ru.school.app.ui.theme.SchoolTheme

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authRepository = AuthRepository(applicationContext)
        scheduleRepository = ScheduleRepository(applicationContext)

        intent.getStringExtra("token")?.takeIf { it.isNotBlank() }?.let { intentToken ->
            val regName = intent.getStringExtra("region")
            val reg = if (regName != null) Region.fromName(regName) else Region.MOSCOW_REGION
            authRepository.saveSession(reg, intentToken)
        }

        setContent {
            var currentSession by remember {
                mutableStateOf(authRepository.getSession())
            }

            androidx.compose.runtime.LaunchedEffect(currentSession?.token) {
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

            SchoolTheme {
                val session = currentSession
                if (session != null) {
                    DashboardScreen(
                        session = session,
                        scheduleRepository = scheduleRepository,
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
                        }
                    )
                }
            }
        }
    }
}
