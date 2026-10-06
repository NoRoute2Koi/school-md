package koi.schoolmd.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import koi.schoolmd.data.AuthSession
import koi.schoolmd.data.DaySchedule
import koi.schoolmd.data.LessonItem
import koi.schoolmd.data.ScheduleRepository
import koi.schoolmd.data.StudentProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    session: AuthSession,
    scheduleRepository: ScheduleRepository,
    modifier: Modifier = Modifier,
    onProfileLoaded: ((StudentProfile) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }

    var weekSchedule by remember { mutableStateOf<List<DaySchedule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Rolling ribbon of dates: 12 weeks before today to 12 weeks after today (25 weeks = 175 days)
    val startMonday = remember(today) { today.minusWeeks(12).with(DayOfWeek.MONDAY) }
    val allRibbonDays = remember(startMonday) { (0 until 25 * 7).map { startMonday.plusDays(it.toLong()) } }
    val todayIndex = remember(allRibbonDays, today) { allRibbonDays.indexOf(today).coerceAtLeast(0) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 2).coerceAtLeast(0))

    val currentMonday = remember(selectedDate) { selectedDate.with(DayOfWeek.MONDAY) }
    val currentSunday = remember(currentMonday) { currentMonday.plusDays(6) }
    val isSelectedToday = selectedDate == today

    fun loadSchedule(monday: LocalDate, force: Boolean = false) {
        if (force) isRefreshing = true else if (weekSchedule.isEmpty()) isLoading = true
        errorMessage = null

        scheduleRepository.fetchWeekSchedule(
            session = session,
            anchorDate = monday,
            forceRefresh = force
        ) { result ->
            isLoading = false
            isRefreshing = false
            result.fold(
                onSuccess = { days ->
                    weekSchedule = days
                    errorMessage = null
                },
                onFailure = { err ->
                    if (weekSchedule.isEmpty()) {
                        errorMessage = err.localizedMessage ?: "Не удалось загрузить расписание"
                    }
                }
            )
        }
    }

    // Trigger schedule load on currentMonday or session change
    LaunchedEffect(currentMonday, session.token) {
        loadSchedule(currentMonday, force = false)
    }

    // Auto-scroll ribbon to keep selectedDate in view
    LaunchedEffect(selectedDate) {
        val idx = allRibbonDays.indexOf(selectedDate)
        if (idx >= 0) {
            val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (idx !in visibleIndices) {
                listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

    // Trigger profile fetch if profile or name not yet loaded
    LaunchedEffect(session.token) {
        if (session.studentProfile == null) {
            scheduleRepository.fetchProfile(session) { profResult ->
                profResult.onSuccess { profile ->
                    onProfileLoaded?.invoke(profile)
                }
            }
        }
    }

    val currentDay = weekSchedule.firstOrNull { it.date == selectedDate }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Top Header: schoolmd + Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "schoolmd",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
            )

            IconButton(
                onClick = { loadSchedule(currentMonday, force = true) },
                enabled = !isLoading && !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Обновить расписание",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Week Navigation Header (NO ARROWS! Smooth scrollable ribbon instead)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatWeekRange(currentMonday, currentSunday),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (!isSelectedToday) {
                FilledTonalButton(
                    onClick = {
                        selectedDate = today
                        val idx = allRibbonDays.indexOf(today)
                        if (idx >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
                            }
                        }
                    },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Сегодня",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Horizontally Scrollable Days Ribbon (влево-вправо)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(allRibbonDays, key = { it.toString() }) { date ->
                val isSelected = date == selectedDate
                val isDateToday = date == today
                val ruLocale = remember { Locale("ru", "RU") }
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ruLocale)
                    .replace(".", "")
                    .replaceFirstChar { it.uppercase() }

                val bgColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isDateToday -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> Color.Transparent
                    },
                    label = "ribbonDayBg"
                )
                val textColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        isDateToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    label = "ribbonDayText"
                )

                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .clickable {
                            selectedDate = date
                            val idx = allRibbonDays.indexOf(date)
                            if (idx >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected || isDateToday) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))

                    if (isDateToday) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Content Area with swipe support
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedDate) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragAccumulator > 70f) {
                                // Swiped right -> previous day
                                val prev = selectedDate.minusDays(1)
                                if (prev in allRibbonDays) {
                                    selectedDate = prev
                                }
                            } else if (dragAccumulator < -70f) {
                                // Swiped left -> next day
                                val next = selectedDate.plusDays(1)
                                if (next in allRibbonDays) {
                                    selectedDate = next
                                }
                            }
                            dragAccumulator = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
                .padding(horizontal = 16.dp)
        ) {
            when {
                isLoading && weekSchedule.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Загрузка расписания...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null && weekSchedule.isEmpty() -> {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Не удалось загрузить расписание",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { loadSchedule(currentMonday, force = true) }) {
                                Text("Попробовать снова")
                            }
                        }
                    }
                }

                currentDay == null || currentDay.lessons.isEmpty() -> {
                    val allLessonsInWeek = weekSchedule.sumOf { it.lessons.size }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))

                        if (selectedDate.dayOfWeek == DayOfWeek.SUNDAY) {
                            Text(
                                text = "Воскресенье — выходной",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Уроков нет, можно отдохнуть :3",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else if (allLessonsInWeek == 0) {
                            Text(
                                text = "Тут пусто, Каникулы либо нет уроков",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "На этой неделе (${formatWeekRange(currentMonday, currentSunday)}) занятия не запланированы.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "На этот день уроков нет",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Свободный день или выходной",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    AnimatedContent(
                        targetState = currentDay,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "DayLessonsTransition"
                    ) { targetDay ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(targetDay.lessons, key = { it.id }) { lesson ->
                                LessonCard(lesson = lesson)
                            }

                            item {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonCard(
    lesson: LessonItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Row: Lesson number, time, classroom, badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val roomText = if (lesson.classroom.isNotBlank()) "   ${lesson.classroom}" else ""
                Text(
                    text = "Урок ${lesson.lessonNumber}   ${lesson.startTime} - ${lesson.endTime}$roomText",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Ongoing lesson badge
                    if (lesson.isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Сейчас",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Exam / Test badge
                    if (lesson.isExam) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "КР",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Replaced badge
                    if (lesson.isReplaced) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = "Замена",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Cancelled badge
                    if (lesson.isCancelled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "Отменён",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Grade badge
                    if (lesson.grade != null) {
                        Surface(
                            shape = CircleShape,
                            color = when (lesson.grade) {
                                "5" -> Color(0xFF2E7D32)
                                "4" -> MaterialTheme.colorScheme.primary
                                "3" -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = lesson.grade,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Main Subject Title
            Text(
                text = lesson.subject,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Lesson Theme subtitle if present
            if (!lesson.theme.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = lesson.theme,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Teacher info if known
            if (!lesson.teacherName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = lesson.teacherName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Footer: Homework info
            if (lesson.homeworkCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    val count = lesson.homeworkCount
                    val remainder10 = count % 10
                    val remainder100 = count % 100
                    val word = when {
                        remainder100 in 11..19 -> "заданий"
                        remainder10 == 1 -> "задание"
                        remainder10 in 2..4 -> "задания"
                        else -> "заданий"
                    }
                    val countText = "Есть $count $word"
                    val desc = if (!lesson.homework.isNullOrBlank()) ": ${lesson.homework}" else ""
                    Text(
                        text = "$countText$desc",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "Нет заданий",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatWeekRange(monday: LocalDate, sunday: LocalDate): String {
    val fullMonths = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )
    val shortMonths = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек"
    )
    return if (monday.monthValue == sunday.monthValue) {
        "${monday.dayOfMonth} – ${sunday.dayOfMonth} ${fullMonths[monday.monthValue - 1]}"
    } else {
        "${monday.dayOfMonth} ${shortMonths[monday.monthValue - 1]} – ${sunday.dayOfMonth} ${shortMonths[sunday.monthValue - 1]}"
    }
}
