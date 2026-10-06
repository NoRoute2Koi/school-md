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
import ru.school.app.ui.screens.DashboardScreen
import ru.school.app.ui.screens.LoginScreen
import ru.school.app.ui.theme.SchoolTheme

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authRepository = AuthRepository(applicationContext)

        setContent {
            var currentSession by remember {
                mutableStateOf(authRepository.getSession())
            }

            SchoolTheme {
                val session = currentSession
                if (session != null) {
                    DashboardScreen(
                        session = session,
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
