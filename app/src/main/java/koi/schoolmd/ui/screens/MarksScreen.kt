package koi.schoolmd.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import koi.schoolmd.ui.components.DetailCard
import koi.schoolmd.ui.components.DetailRow
import koi.schoolmd.ui.components.SheetHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import koi.schoolmd.data.AuthSession
import koi.schoolmd.data.DayMarks
import koi.schoolmd.data.MarkItem
import koi.schoolmd.data.MarksRepository
import koi.schoolmd.data.SubjectMarksSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class MarksViewTab(val title: String) {
    BY_DAYS("По датам"),
    BY_SUBJECTS("По предметам")
}

@Composable
fun MarksScreen(
    session: AuthSession,
    marksRepository: MarksRepository,
    modifier: Modifier = Modifier
) {
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    var allMarks by remember {
        mutableStateOf(marksRepository.getCachedMarks() ?: emptyList())
    }
    var summaries by remember {
        mutableStateOf(marksRepository.getCachedSummaries() ?: emptyList())
    }
    var isLoading by remember { mutableStateOf(allMarks.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf(MarksViewTab.BY_DAYS) }
    var selectedMarkForDetail by remember { mutableStateOf<MarkItem?>(null) }
    var selectedSummaryForDetail by remember { mutableStateOf<SubjectMarksSummary?>(null) }

    fun loadMarks(force: Boolean = false) {
        if (force) isRefreshing = true else if (allMarks.isEmpty()) isLoading = true
        errorMessage = null

        marksRepository.fetchMarks(session = session, forceRefresh = force) { result ->
            isLoading = false
            isRefreshing = false
            result.fold(
                onSuccess = { items ->
                    allMarks = items
                    summaries = marksRepository.getCachedSummaries() ?: emptyList()
                    errorMessage = null
                },
                onFailure = { error ->
                    if (allMarks.isEmpty()) {
                        errorMessage = error.localizedMessage ?: "Не удалось загрузить оценки"
                    }
                }
            )
        }
    }

    LaunchedEffect(session.token) {
        loadMarks(force = false)
    }

    val monday = remember(anchorDate) { anchorDate.with(DayOfWeek.MONDAY) }
    val sunday = remember(monday) { monday.plusDays(6) }
    val isCurrentWeek = remember(monday, today) {
        monday == today.with(DayOfWeek.MONDAY)
    }

    val weekDayMarks = remember(allMarks, anchorDate) {
        marksRepository.getMarksForWeek(allMarks, anchorDate)
    }

    val weekTotalMarks = remember(weekDayMarks) {
        weekDayMarks.sumOf { it.marks.size }
    }

    val weekAverageGrade = remember(weekDayMarks) {
        val numericMarks = weekDayMarks.flatMap { it.marks }.mapNotNull { item ->
            item.value.toDoubleOrNull()?.let { v -> Pair(v, item.weight) }
        }
        if (numericMarks.isNotEmpty()) {
            val totalWeight = numericMarks.sumOf { it.second }.coerceAtLeast(1)
            val weightedSum = numericMarks.sumOf { it.first * it.second }
            String.format(Locale.US, "%.2f", weightedSum / totalWeight)
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SchoolMD",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "Оценки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { loadMarks(force = true) },
                enabled = !isLoading && !isRefreshing
            ) {
                if (isLoading || isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Обновить оценки",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // View Mode Filter Chips (По датам / По предметам)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarksViewTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    label = {
                        Text(
                            text = tab.title,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "MarksViewTabContent"
        ) { currentTab ->
            when (currentTab) {
                MarksViewTab.BY_DAYS -> {
                    MarksFeedByDays(
                        monday = monday,
                        sunday = sunday,
                        isCurrentWeek = isCurrentWeek,
                        weekTotalMarks = weekTotalMarks,
                        weekAverageGrade = weekAverageGrade,
                        weekDayMarks = weekDayMarks,
                        allMarks = allMarks,
                        marksRepository = marksRepository,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onPreviousWeek = { anchorDate = anchorDate.minusWeeks(1) },
                        onNextWeek = { anchorDate = anchorDate.plusWeeks(1) },
                        onCurrentWeek = { anchorDate = today },
                        onRetry = { loadMarks(force = true) },
                        onMarkClick = { selectedMarkForDetail = it }
                    )
                }

                MarksViewTab.BY_SUBJECTS -> {
                    MarksBySubjectsView(
                        summaries = summaries,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        allMarks = allMarks,
                        onRetry = { loadMarks(force = true) },
                        onSummaryClick = { selectedSummaryForDetail = it }
                    )
                }
            }
        }
    }

    // Detail Bottom Sheet for Mark (by date or by subject)
    selectedMarkForDetail?.let { mark ->
        MarkDetailBottomSheet(
            mark = mark,
            onDismiss = { selectedMarkForDetail = null }
        )
    }

    // Detail Bottom Sheet for Subject Summary
    selectedSummaryForDetail?.let { summary ->
        SubjectSummaryDetailBottomSheet(
            summary = summary,
            subjectMarks = allMarks.filter { it.subject == summary.subjectName },
            onDismiss = { selectedSummaryForDetail = null },
            onMarkClick = { mark ->
                selectedSummaryForDetail = null
                selectedMarkForDetail = mark
            }
        )
    }
}

@Composable
private fun MarksFeedByDays(
    monday: LocalDate,
    sunday: LocalDate,
    isCurrentWeek: Boolean,
    weekTotalMarks: Int,
    weekAverageGrade: String?,
    weekDayMarks: List<DayMarks>,
    allMarks: List<MarkItem>,
    marksRepository: MarksRepository,
    isLoading: Boolean,
    errorMessage: String?,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onRetry: () -> Unit,
    onMarkClick: (MarkItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Week Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Предыдущая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (!isCurrentWeek) onCurrentWeek()
                }
            ) {
                Text(
                    text = formatMarksWeekRange(monday, sunday),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isCurrentWeek) {
                    Text(
                        text = "Вернуться к текущей",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onNextWeek) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Следующая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Weekly Summary Card (MD3 container)
        if (weekTotalMarks > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Оценок за неделю: $weekTotalMarks",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (weekAverageGrade != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Ср. балл: $weekAverageGrade",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Main Feed with swipe support
        var dragAccumulator by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragAccumulator = 0f
                        },
                        onDragEnd = {
                            if (dragAccumulator > 60f) {
                                onPreviousWeek()
                            } else if (dragAccumulator < -60f) {
                                onNextWeek()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = {
                            dragAccumulator = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        }
                    )
                }
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = onRetry) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                else -> {
                    AnimatedContent(
                        targetState = monday,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> width / 4 } +
                                        fadeIn(animationSpec = tween(200)))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                                                fadeOut(animationSpec = tween(180))
                                    )
                            } else {
                                (slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width / 4 } +
                                        fadeIn(animationSpec = tween(200)))
                                    .togetherWith(
                                        slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> width / 4 } +
                                                fadeOut(animationSpec = tween(180))
                                    )
                            }
                        },
                        label = "MarksFeedTransition"
                    ) { targetMonday ->
                        val targetSunday = targetMonday.plusDays(6)
                        val targetWeekDayMarks = remember(allMarks, targetMonday) {
                            marksRepository.getMarksForWeek(allMarks, targetMonday)
                        }

                        if (targetWeekDayMarks.isEmpty()) {
                            // Unified empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.EventBusy,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "На этой неделе оценок нет",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "С ${formatShortDate(targetMonday)} по ${formatShortDate(targetSunday)} оценок не выставлено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
                            ) {
                                items(targetWeekDayMarks, key = { it.date.toString() }) { dayMarks ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Section header: Date above the card (matching header-example-md3.jpg)
                                        Text(
                                            text = formatMarksDateHeader(dayMarks.date),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
                                        )

                                        // MD3 Card containing marks for this day (matching header-example-md3.jpg)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                dayMarks.marks.forEachIndexed { index, mark ->
                                                    if (index > 0) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                            thickness = 0.8.dp
                                                        )
                                                    }
                                                    MarkItemRow(
                                                        mark = mark,
                                                        onClick = { onMarkClick(mark) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkItemRow(
    mark: MarkItem,
    onClick: () -> Unit
) {
    val (badgeBg, badgeText) = getMarkColors(mark.value)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Squircle mark badge on left (44.dp, RoundedCornerShape 14.dp)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mark.value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = badgeText
            )
        }

        Spacer(Modifier.width(16.dp))

        // Center: Subject and control form info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = mark.subject,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            val subtitle = mark.controlFormName.ifBlank { "Оценка за урок" }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!mark.comment.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mark.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!mark.teacherName.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mark.teacherName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right side badges: Weight pill (x2), Exam pill (КР)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (mark.weight > 1) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "×${mark.weight}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (mark.isExam) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "КР",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (mark.isPoint) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "Точка",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarksBySubjectsView(
    summaries: List<SubjectMarksSummary>,
    isLoading: Boolean,
    errorMessage: String?,
    allMarks: List<MarkItem>,
    onRetry: () -> Unit,
    onSummaryClick: (SubjectMarksSummary) -> Unit
) {
    when {
        isLoading && summaries.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        errorMessage != null && summaries.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text("Повторить")
                    }
                }
            }
        }

        summaries.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет данных по предметам",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "Итоги по предметам",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            summaries.forEachIndexed { index, summary ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        thickness = 0.8.dp
                                    )
                                }
                                SubjectSummaryRow(
                                    summary = summary,
                                    onClick = { onSummaryClick(summary) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectSummaryRow(
    summary: SubjectMarksSummary,
    onClick: () -> Unit
) {
    val avgScore = summary.averageMark
    val (badgeBg, badgeText) = getMarkColors(avgScore?.take(1).orEmpty())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Average score pill / squircle
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (avgScore != null) badgeBg else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avgScore ?: "—",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (avgScore != null) badgeText else MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.subjectName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            val period = summary.periodTitle ?: "Текущий период"
            val c = summary.marksCount
            val countText = when {
                c % 10 == 1 && c % 100 != 11 -> "$c оценка"
                c % 10 in 2..4 && c % 100 !in 12..14 -> "$c оценки"
                else -> "$c оценок"
            }
            Text(
                text = "$period • $countText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkDetailBottomSheet(
    mark: MarkItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (badgeBg, badgeText) = getMarkColors(mark.value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(
                title = mark.subject,
                subtitle = formatDetailedDate(mark.date),
                badgeContent = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mark.value,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = badgeText
                        )
                    }

                    if (mark.weight > 1) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "Вес: ×${mark.weight}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (mark.isExam) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "КР / Экзамен",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (mark.isPoint) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = "Точка",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(18.dp))

            DetailCard {
                DetailRow(
                    icon = Icons.AutoMirrored.Outlined.FactCheck,
                    label = "Форма контроля",
                    value = mark.controlFormName.ifBlank { "Текущая оценка" }
                )

                DetailRow(
                    icon = Icons.Outlined.Scale,
                    label = "Вес оценки",
                    value = if (mark.weight > 1) "×${mark.weight} (влияет на средний балл с коэффициентом ${mark.weight})" else "1"
                )

                if (mark.isExam) {
                    DetailRow(
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        label = "Тип работы",
                        value = "Контрольная работа / Экзамен"
                    )
                }

                if (mark.isPoint) {
                    DetailRow(
                        icon = Icons.Outlined.WarningAmber,
                        label = "Статус",
                        value = "Точка (требуется сдать задолженность)"
                    )
                }

                if (!mark.comment.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.AutoMirrored.Outlined.Comment,
                        label = "Комментарий учителя",
                        value = mark.comment
                    )
                }

                if (!mark.teacherName.isNullOrBlank()) {
                    DetailRow(
                        icon = Icons.Outlined.Person,
                        label = "Преподаватель",
                        value = mark.teacherName
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Закрыть")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectSummaryDetailBottomSheet(
    summary: SubjectMarksSummary,
    subjectMarks: List<MarkItem>,
    onDismiss: () -> Unit,
    onMarkClick: (MarkItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val avgScore = summary.averageMark
    val (badgeBg, badgeText) = getMarkColors(avgScore?.take(1).orEmpty())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(
                title = summary.subjectName,
                subtitle = summary.periodTitle ?: "Текущий период",
                badgeContent = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (avgScore != null) badgeBg else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avgScore ?: "—",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (avgScore != null) badgeText else MaterialTheme.colorScheme.outline
                        )
                    }

                    val c = summary.marksCount
                    val countText = when {
                        c % 10 == 1 && c % 100 != 11 -> "$c оценка"
                        c % 10 in 2..4 && c % 100 !in 12..14 -> "$c оценки"
                        else -> "$c оценок"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            )

            Spacer(Modifier.height(18.dp))

            DetailCard {
                DetailRow(
                    icon = Icons.Outlined.Grade,
                    label = "Средний балл за период",
                    value = summary.averageMark ?: "Нет данных"
                )

                DetailRow(
                    icon = Icons.Outlined.Assessment,
                    label = "Всего оценок",
                    value = "${summary.marksCount}"
                )
            }

            if (subjectMarks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Оценки за период (нажмите для деталей):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val displayMarks = subjectMarks.take(10)
                            displayMarks.forEach { m ->
                                val (bg, txt) = getMarkColors(m.value)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bg,
                                    modifier = Modifier.clickable { onMarkClick(m) }
                                ) {
                                    Text(
                                        text = m.value,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = txt,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Закрыть")
            }
        }
    }
}

@Composable
private fun getMarkColors(markValue: String): Pair<Color, Color> {
    val isDark = isSystemInDarkTheme()
    return when (markValue.trim()) {
        "5" -> if (isDark) Pair(Color(0xFF1B3B22), Color(0xFF81C784)) else Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "4" -> if (isDark) Pair(Color(0xFF122E4E), Color(0xFF90CAF9)) else Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "3" -> if (isDark) Pair(Color(0xFF3E2723), Color(0xFFFFB74D)) else Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "2", "1" -> if (isDark) Pair(Color(0xFF3E1C1C), Color(0xFFEF9A9A)) else Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMarksDateHeader(date: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val formatter = DateTimeFormatter.ofPattern("d MMMM", ruLocale)
    val dayMonth = date.format(formatter)

    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, ruLocale)
        .replaceFirstChar { it.uppercase() }

    return when (date) {
        today -> "Сегодня, $dayMonth"
        yesterday -> "Вчера, $dayMonth"
        else -> "$dayOfWeek, $dayMonth"
    }
}

private fun formatMarksWeekRange(monday: LocalDate, sunday: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val monthMonday = monday.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")
    val monthSunday = sunday.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")

    return if (monday.month == sunday.month) {
        "${monday.dayOfMonth} – ${sunday.dayOfMonth} $monthMonday"
    } else {
        "${monday.dayOfMonth} $monthMonday – ${sunday.dayOfMonth} $monthSunday"
    }
}

private fun formatDetailedDate(date: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy г.", ruLocale)
    return date.format(formatter)
}

private fun formatShortDate(date: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val monthName = date.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")
    return "${date.dayOfMonth} $monthName"
}
