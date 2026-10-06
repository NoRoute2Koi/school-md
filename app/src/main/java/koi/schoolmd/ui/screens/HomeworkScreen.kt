package koi.schoolmd.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import koi.schoolmd.data.AuthSession
import koi.schoolmd.data.HomeworkItem
import koi.schoolmd.data.HomeworkRepository
import koi.schoolmd.data.HomeworkStatusFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeworkScreen(
    session: AuthSession,
    homeworkRepository: HomeworkRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var anchorDate by remember { mutableStateOf(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    var homeworkList by remember {
        mutableStateOf(homeworkRepository.getCachedHomeworks(session, anchorDate) ?: emptyList())
    }
    var isLoading by remember { mutableStateOf(homeworkList.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var statusFilter by remember { mutableStateOf(HomeworkStatusFilter.ALL) }
    var selectedSubjectFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    fun loadData(forceRefresh: Boolean = false) {
        isLoading = true
        errorMessage = null
        homeworkRepository.fetchHomeworks(session, anchorDate, forceRefresh = forceRefresh) { result ->
            result.fold(
                onSuccess = { items ->
                    homeworkList = items
                    isLoading = false
                    errorMessage = null
                },
                onFailure = { error ->
                    isLoading = false
                    if (homeworkList.isEmpty()) {
                        errorMessage = error.message ?: "Не удалось загрузить задания"
                    }
                }
            )
        }
    }

    LaunchedEffect(anchorDate) {
        loadData(forceRefresh = false)
    }

    val monday = remember(anchorDate) { anchorDate.with(DayOfWeek.MONDAY) }
    val sunday = remember(monday) { monday.plusDays(6) }
    val isCurrentWeek = remember(monday, today) {
        monday == today.with(DayOfWeek.MONDAY)
    }

    // Filter items
    val filteredHomeworks = remember(homeworkList, statusFilter, selectedSubjectFilter, searchQuery) {
        homeworkList.filter { item ->
            val matchesStatus = when (statusFilter) {
                HomeworkStatusFilter.ALL -> true
                HomeworkStatusFilter.PENDING -> !item.isDone
                HomeworkStatusFilter.COMPLETED -> item.isDone
            }
            val matchesSubject = selectedSubjectFilter == null || item.subject == selectedSubjectFilter
            val matchesSearch = searchQuery.isBlank() ||
                    item.subject.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true)

            matchesStatus && matchesSubject && matchesSearch
        }
    }

    // Available subjects for chips
    val availableSubjects = remember(homeworkList) {
        homeworkList.map { it.subject }.distinct().sorted()
    }

    val totalCount = homeworkList.size
    val completedCount = homeworkList.count { it.isDone }
    val pendingCount = totalCount - completedCount
    val progressFraction by animateFloatAsState(
        targetValue = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f,
        label = "progress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Top Header: "schoolmd" / "Задания" + Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Задания",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                )
                if (totalCount > 0) {
                    Text(
                        text = if (pendingCount > 0) "К сдаче: $pendingCount" else "Все задания выполнены 🎉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { loadData(forceRefresh = true) },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Обновить задания",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Expandable Search Bar
        AnimatedVisibility(visible = isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по предмету или тексту...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Week Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { anchorDate = anchorDate.minusWeeks(1) }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Предыдущая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    if (!isCurrentWeek) anchorDate = today
                }
            ) {
                Text(
                    text = formatHwWeekRange(monday, sunday),
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

            IconButton(
                onClick = { anchorDate = anchorDate.plusWeeks(1) }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Следующая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress Card (MD3 Expressive container)
        if (totalCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Прогресс выполнения",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$completedCount из $totalCount (${(progressFraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (completedCount == totalCount) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        strokeCap = StrokeCap.Round,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        // Status Filter Chips (Material 3 Expressive)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HomeworkStatusFilter.entries) { filter ->
                val isSelected = statusFilter == filter
                val count = when (filter) {
                    HomeworkStatusFilter.ALL -> totalCount
                    HomeworkStatusFilter.PENDING -> pendingCount
                    HomeworkStatusFilter.COMPLETED -> completedCount
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { statusFilter = filter },
                    label = {
                        Text("${filter.title} ($count)")
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }

            if (availableSubjects.size > 1) {
                item {
                    Spacer(Modifier.width(4.dp))
                }

                items(availableSubjects) { subject ->
                    val isSelected = selectedSubjectFilter == subject
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedSubjectFilter = if (isSelected) null else subject
                        },
                        label = { Text(subject) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Content Area: Loading / Error / Empty / List
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                isLoading && homeworkList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Загрузка заданий...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null && homeworkList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Ошибка",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { loadData(forceRefresh = true) }) {
                            Text("Повторить запрос")
                        }
                    }
                }

                filteredHomeworks.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 70.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (statusFilter == HomeworkStatusFilter.PENDING && totalCount > 0)
                                Icons.Outlined.CheckCircle
                            else Icons.Outlined.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(Modifier.height(16.dp))

                        val emptyTitle = when {
                            searchQuery.isNotBlank() -> "Ничего не найдено"
                            statusFilter == HomeworkStatusFilter.PENDING && totalCount > 0 -> "Все задания выполнены!"
                            statusFilter == HomeworkStatusFilter.COMPLETED -> "Нет выполненных заданий"
                            else -> "На эту неделю заданий нет"
                        }

                        val emptySubtitle = when {
                            searchQuery.isNotBlank() -> "По запросу «$searchQuery» ничего не найдено"
                            statusFilter == HomeworkStatusFilter.PENDING && totalCount > 0 -> "Вы сделали все домашние задания на эту неделю 🎉"
                            statusFilter == HomeworkStatusFilter.COMPLETED -> "Отметьте выполненные задания галочкой"
                            else -> "Учителя пока не добавили домашние задания на этот период"
                        }

                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = emptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(20.dp))
                        if (!isCurrentWeek) {
                            FilledTonalButton(onClick = { anchorDate = today }) {
                                Text("К текущей неделе")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick = { anchorDate = anchorDate.minusWeeks(1) }) {
                                    Text("← Прошлая неделя")
                                }
                                FilledTonalButton(onClick = { anchorDate = anchorDate.plusWeeks(1) }) {
                                    Text("Следующая неделя →")
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredHomeworks, key = { it.id }) { item ->
                            HomeworkCard(
                                item = item,
                                onToggleDone = { targetItem ->
                                    coroutineScope.launch {
                                        homeworkRepository.toggleHomeworkDone(session, targetItem) { res ->
                                            res.onSuccess { newDone ->
                                                homeworkList = homeworkList.map {
                                                    if (it.id == targetItem.id) it.copy(isDone = newDone) else it
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeworkCard(
    item: HomeworkItem,
    onToggleDone: (HomeworkItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = item.isDone
    val containerBg by animateColorAsState(
        targetValue = if (isDone) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "hwCardBg"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Row: Subject, Due Date, Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Subject Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = item.subject,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Due Date Badge
                    val dateFormatted = formatDueDate(item.date)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.date == LocalDate.now() && !isDone)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (item.date == LocalDate.now() && !isDone)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = "К уроку: $dateFormatted",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Assigned Date Badge
                    if (item.assignedDate != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "Задано: ${formatShortDate(item.assignedDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Checkbox Button (Expressive circular check)
                val checkBg by animateColorAsState(
                    targetValue = if (isDone) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    label = "checkBg"
                )
                val checkBorder = if (isDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(checkBg)
                        .clickable { onToggleDone(item) }
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Выполнено",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .padding(2.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxSize(),
                                border = androidx.compose.foundation.BorderStroke(2.dp, checkBorder)
                            ) {}
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Homework Description
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface
            )

            // Materials and Attachments chips
            if (item.materials.isNotEmpty() || item.attachments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.materials.forEach { mat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Quiz,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = mat.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    item.attachments.forEach { att ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = att.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Teacher info footer
            if (!item.teacherName.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Учитель: ${item.teacherName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private fun formatHwWeekRange(monday: LocalDate, sunday: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val monthMonday = monday.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")
    val monthSunday = sunday.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")

    return if (monday.month == sunday.month) {
        "${monday.dayOfMonth} – ${sunday.dayOfMonth} $monthMonday"
    } else {
        "${monday.dayOfMonth} $monthMonday – ${sunday.dayOfMonth} $monthSunday"
    }
}

private fun formatDueDate(date: LocalDate): String {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val ruLocale = Locale("ru", "RU")
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")
        .replaceFirstChar { it.uppercase() }
    val monthName = date.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")

    return when (date) {
        today -> "Сегодня (${date.dayOfMonth} $monthName)"
        tomorrow -> "Завтра (${date.dayOfMonth} $monthName)"
        else -> "$dayName, ${date.dayOfMonth} $monthName"
    }
}

private fun formatShortDate(date: LocalDate): String {
    val ruLocale = Locale("ru", "RU")
    val monthName = date.month.getDisplayName(TextStyle.SHORT, ruLocale).replace(".", "")
    return "${date.dayOfMonth} $monthName"
}
