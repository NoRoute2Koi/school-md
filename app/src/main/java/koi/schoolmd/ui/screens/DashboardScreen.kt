package koi.schoolmd.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import koi.schoolmd.data.AppThemeMode
import koi.schoolmd.data.AuthSession
import koi.schoolmd.data.HomeworkRepository
import koi.schoolmd.data.MarksRepository
import koi.schoolmd.data.ScheduleRepository
import koi.schoolmd.data.StudentProfile

enum class MainTab(
    val title: String,
    val icon: ImageVector
) {
    SCHEDULE("Расписание", Icons.Default.CalendarMonth),
    MARKS("Оценки", Icons.Default.Grade),
    HOMEWORK("Задания", Icons.AutoMirrored.Filled.Assignment),
    PROFILE("Профиль", Icons.Default.Person)
}

@Composable
fun DashboardScreen(
    session: AuthSession,
    scheduleRepository: ScheduleRepository,
    homeworkRepository: HomeworkRepository,
    marksRepository: MarksRepository,
    onAvatarChanged: (String?) -> Unit,
    onNameChanged: (String?) -> Unit,
    onLogout: () -> Unit,
    onRefreshToken: ((Result<String>) -> Unit) -> Unit,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChanged: (AppThemeMode) -> Unit = {},
    isAutoRefreshEnabled: Boolean = true,
    onAutoRefreshChanged: (Boolean) -> Unit = {},
    onProfileLoaded: ((StudentProfile) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(MainTab.SCHEDULE) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                MainTab.SCHEDULE -> {
                    ScheduleScreen(
                        session = session,
                        scheduleRepository = scheduleRepository,
                        onProfileLoaded = onProfileLoaded
                    )
                }
                MainTab.MARKS -> {
                    MarksScreen(
                        session = session,
                        marksRepository = marksRepository
                    )
                }
                MainTab.HOMEWORK -> {
                    HomeworkScreen(
                        session = session,
                        homeworkRepository = homeworkRepository
                    )
                }
                MainTab.PROFILE -> {
                    ProfileScreen(
                        session = session,
                        onAvatarChanged = onAvatarChanged,
                        onNameChanged = onNameChanged,
                        onLogout = onLogout,
                        onRefreshToken = onRefreshToken,
                        themeMode = themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        isAutoRefreshEnabled = isAutoRefreshEnabled,
                        onAutoRefreshChanged = onAutoRefreshChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
