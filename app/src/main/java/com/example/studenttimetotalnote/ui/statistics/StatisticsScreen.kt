package com.example.studenttimetotalnote.ui.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.NoteAggregate
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.ui.components.StudyTimerSemantics
import com.example.studenttimetotalnote.ui.theme.Cobalt
import com.example.studenttimetotalnote.ui.theme.Hairline
import com.example.studenttimetotalnote.ui.theme.Ink
import com.example.studenttimetotalnote.ui.theme.MenuPaper
import com.example.studenttimetotalnote.ui.theme.MutedInk
import com.example.studenttimetotalnote.ui.theme.Paper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object StatisticsSemantics {
    const val Screen = "statistics_screen"
    const val Back = "statistics_back"
    const val PeriodTabs = "statistics_period_tabs"
    const val PeriodSelector = "statistics_period_selector"
    const val PreviousPeriod = "statistics_previous_period"
    const val NextPeriod = "statistics_next_period"
    const val PeriodLabel = "statistics_period_label"
    const val PeriodRange = "statistics_period_range"
    const val TotalCard = "statistics_total_card"
    const val Chart = "statistics_chart"
    const val Rankings = "statistics_rankings"
    const val EmptyState = "statistics_empty_state"
    const val RecordDetails = "statistics_record_details"
    const val RecordDeletePrefix = "statistics_record_delete"
    const val DeleteConfirmation = "statistics_delete_confirmation"
    const val DeleteConfirm = "statistics_delete_confirm"
    const val DeleteCancel = "statistics_delete_cancel"
}

private val PeriodChoices = listOf(
    PeriodKind.DAY to "今日",
    PeriodKind.WEEK to "上一周",
    PeriodKind.MONTH to "上个月",
    PeriodKind.YEAR to "年度",
    PeriodKind.ALL to "使用以来",
)
private val RecordDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
private val Destructive = Color(0xFFB4433D)

@Composable
fun StatisticsScreen(
    repository: StudyTimerRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPeriod: PeriodKind = PeriodKind.DAY,
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val viewModel = remember(repository, initialPeriod, clock, zone) {
        StatisticsViewModel(
            repository = repository,
            initialPeriod = initialPeriod,
            clock = clock,
            zone = zone,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StatisticsContent(
        uiState = uiState,
        onBack = onBack,
        onPeriodSelected = viewModel::selectPeriod,
        onPreviousPeriod = viewModel::selectPreviousPeriod,
        onNextPeriod = viewModel::selectNextPeriod,
        onOpenRecords = viewModel::openRecordsForNote,
        modifier = modifier,
    )

    val pendingDelete = uiState.pendingDelete
    if (pendingDelete != null) {
        DeleteRecordConfirmation(
            record = pendingDelete,
            zone = zone,
            isDeleting = uiState.isDeleting,
            errorMessage = uiState.deleteError,
            onCancel = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete,
        )
    } else if (uiState.openedNoteText != null) {
        RecordDetailsDialog(
            records = uiState.openedRecords,
            zone = zone,
            onDismiss = viewModel::closeRecordDetails,
            onDeleteRequested = viewModel::requestDelete,
        )
    }
}

@Composable
private fun StatisticsContent(
    uiState: StatisticsUiState,
    onBack: () -> Unit,
    onPeriodSelected: (PeriodKind) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onOpenRecords: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = uiState.report

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .testTag(StatisticsSemantics.Screen),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        StatisticsTopBar(
            selectedPeriod = uiState.selectedPeriod,
            selectedDate = uiState.selectedDate,
            onBack = onBack,
            onPeriodSelected = onPeriodSelected,
        )

        Spacer(modifier = Modifier.height(25.dp))
        PeriodSelector(
            kind = uiState.selectedPeriod,
            selectedDate = uiState.selectedDate,
            today = uiState.today,
            previousEnabled = uiState.canSelectPreviousPeriod,
            nextEnabled = uiState.canSelectNextPeriod,
            onPreviousPeriod = onPreviousPeriod,
            onNextPeriod = onNextPeriod,
        )

        if (report?.hasData == true) {
            Spacer(modifier = Modifier.height(29.dp))
            PeriodOverview(
                report = report,
                today = uiState.today,
            )

            if (uiState.trend.isNotEmpty()) {
                Spacer(modifier = Modifier.height(34.dp))
                TrendChart(points = uiState.trend)
                Spacer(modifier = Modifier.height(51.dp))
            } else {
                Spacer(modifier = Modifier.height(34.dp))
            }
            LearningContent(
                groups = report.groups,
                onOpenRecords = onOpenRecords,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatisticsTopBar(
    selectedPeriod: PeriodKind,
    selectedDate: LocalDate,
    onBack: () -> Unit,
    onPeriodSelected: (PeriodKind) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag(StatisticsSemantics.Back)
                .semantics { contentDescription = "返回" },
        ) {
            BackIcon()
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "统计",
            modifier = Modifier.testTag("statistics_title"),
            color = Ink,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(44.dp)
                    .testTag(StatisticsSemantics.PeriodTabs)
                    .semantics {
                        contentDescription = "选择统计周期，" +
                            "${periodModeLabel(selectedPeriod)}，" +
                            periodNavigationCopy(selectedPeriod, selectedDate, selectedDate).title
                    },
            ) {
                FilterIcon()
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = MenuPaper,
                tonalElevation = 0.dp,
                shadowElevation = 3.dp,
            ) {
                PeriodChoices.forEach { (kind, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (kind == selectedPeriod) Cobalt else Ink,
                                fontWeight = if (kind == selectedPeriod) {
                                    FontWeight.Medium
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onPeriodSelected(kind)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    kind: PeriodKind,
    selectedDate: LocalDate,
    today: LocalDate,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
) {
    val copy = periodNavigationCopy(kind, selectedDate, today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StatisticsSemantics.PeriodSelector)
            .semantics {
                contentDescription = "${copy.title}，${copy.subtitle}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind == PeriodKind.ALL) {
            Spacer(modifier = Modifier.size(40.dp))
        } else {
            IconButton(
                onClick = onPreviousPeriod,
                enabled = previousEnabled,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(StatisticsSemantics.PreviousPeriod)
                    .semantics { contentDescription = previousPeriodDescription(kind) },
            ) {
                PeriodArrowIcon(
                    pointsForward = false,
                    enabled = previousEnabled,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = copy.title,
                color = Ink,
                fontSize = when (kind) {
                    PeriodKind.WEEK -> 19.sp
                    PeriodKind.YEAR -> 24.sp
                    else -> 22.sp
                },
                lineHeight = 29.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = if (kind == PeriodKind.WEEK) 0.sp else 0.5.sp,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = copy.subtitle,
                color = if (copy.isLive) Cobalt else MutedInk,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (kind == PeriodKind.ALL) {
            Spacer(modifier = Modifier.size(40.dp))
        } else {
            IconButton(
                onClick = onNextPeriod,
                enabled = nextEnabled,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(StatisticsSemantics.NextPeriod)
                    .semantics { contentDescription = nextPeriodDescription(kind) },
            ) {
                PeriodArrowIcon(
                    pointsForward = true,
                    enabled = nextEnabled,
                )
            }
        }
    }
}

@Composable
private fun PeriodArrowIcon(
    pointsForward: Boolean,
    enabled: Boolean,
) {
    Canvas(modifier = Modifier.size(17.dp)) {
        val startX = if (pointsForward) size.width * 0.32f else size.width * 0.68f
        val endX = if (pointsForward) size.width * 0.70f else size.width * 0.30f
        val color = Ink.copy(alpha = if (enabled) 1f else 0.22f)
        drawLine(
            color = color,
            start = Offset(startX, size.height * 0.16f),
            end = Offset(endX, size.height * 0.50f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Square,
        )
        drawLine(
            color = color,
            start = Offset(endX, size.height * 0.50f),
            end = Offset(startX, size.height * 0.84f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Square,
        )
    }
}

@Composable
private fun BackIcon() {
    Canvas(modifier = Modifier.size(23.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(
            color = Ink,
            start = Offset(size.width * 0.74f, size.height * 0.12f),
            end = Offset(size.width * 0.28f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Square,
        )
        drawLine(
            color = Ink,
            start = Offset(size.width * 0.28f, size.height * 0.50f),
            end = Offset(size.width * 0.74f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Square,
        )
    }
}

@Composable
private fun FilterIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.25.dp.toPx()
        val radius = 1.8.dp.toPx()
        val rows = listOf(0.25f, 0.50f, 0.75f)
        val knobs = listOf(0.67f, 0.38f, 0.61f)
        rows.forEachIndexed { index, yFraction ->
            val y = size.height * yFraction
            drawLine(
                color = Ink,
                start = Offset(size.width * 0.12f, y),
                end = Offset(size.width * 0.88f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
            drawCircle(
                color = Paper,
                radius = radius + 0.8.dp.toPx(),
                center = Offset(size.width * knobs[index], y),
            )
            drawCircle(
                color = Ink,
                radius = radius,
                center = Offset(size.width * knobs[index], y),
            )
            drawCircle(
                color = Paper,
                radius = radius * 0.42f,
                center = Offset(size.width * knobs[index], y),
            )
        }
    }
}

@Composable
private fun PeriodOverview(
    report: PeriodReport,
    today: LocalDate,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StatisticsSemantics.PeriodLabel)
            .semantics {
                contentDescription = "${periodModeLabel(report.kind)}，${report.label}"
            },
    ) {
        Text(
            text = periodOverviewLabel(report, today),
            color = MutedInk,
            fontSize = 15.sp,
            letterSpacing = 0.8.sp,
        )
        Spacer(modifier = Modifier.height(7.dp))
        TotalDuration(
            durationMs = report.totalDurationMs,
            modifier = Modifier
                .testTag(StatisticsSemantics.TotalCard)
                .semantics {
                    contentDescription = "累计学习时长，${formatListDuration(report.totalDurationMs)}"
                },
        )
    }
}

@Composable
private fun TotalDuration(
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(1L)
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (totalMinutes == 0L) {
            DurationPart(value = totalSeconds, unit = "秒")
        } else if (hours == 0L) {
            DurationPart(value = totalMinutes, unit = "分钟")
        } else {
            DurationPart(value = hours, unit = "小时")
            Spacer(modifier = Modifier.width(15.dp))
            DurationPart(value = minutes, unit = "分")
        }
    }
}

@Composable
private fun DurationPart(value: Long, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value.toString(),
            color = Ink,
            fontSize = 52.sp,
            lineHeight = 57.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = unit,
            modifier = Modifier.padding(bottom = 7.dp),
            color = Ink,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>) {
    if (points.isEmpty()) return

    val maxDuration = points.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
    val description = points.joinToString(separator = "、") {
        "${it.label}${formatListDuration(it.durationMs)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StatisticsSemantics.Chart)
            .semantics { contentDescription = "学习趋势，$description" },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(205.dp),
        ) {
            val baselineY = size.height - 3.dp.toPx()
            val sideInset = 21.dp.toPx()
            val usableWidth = size.width - sideInset * 2f
            val maxLineHeight = size.height - 17.dp.toPx()
            val minLineHeight = 15.dp.toPx()
            val inactive = Color(0xFF949590)

            drawLine(
                color = Hairline,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = 1.dp.toPx(),
            )

            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) {
                    size.width / 2f
                } else {
                    sideInset + usableWidth * index / (points.size - 1)
                }
                val fraction = point.durationMs.toFloat() / maxDuration.toFloat()
                val lineHeight = if (point.durationMs > 0L) {
                    minLineHeight + (maxLineHeight - minLineHeight) * fraction.coerceIn(0f, 1f)
                } else {
                    minLineHeight
                }
                val color = if (point.emphasized) Cobalt else inactive
                drawLine(
                    color = color,
                    start = Offset(x, baselineY - lineHeight),
                    end = Offset(x, baselineY),
                    strokeWidth = if (point.emphasized) 1.8.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Square,
                )
                drawCircle(
                    color = color,
                    radius = if (point.emphasized) 3.2.dp.toPx() else 2.8.dp.toPx(),
                    center = Offset(x, baselineY),
                )
            }
        }
        Spacer(modifier = Modifier.height(13.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    color = MutedInk,
                    fontSize = if (points.size > 7) 10.sp else 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LearningContent(
    groups: List<NoteAggregate>,
    onOpenRecords: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StatisticsSemantics.Rankings)
            .semantics { contentDescription = "按累计时长排序的学习内容" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "学习内容",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "点开管理记录",
                color = MutedInk,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        groups.forEachIndexed { index, aggregate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clickable { onOpenRecords(aggregate.noteText) }
                    .testTag(StudyTimerSemantics.RankingRow)
                    .semantics {
                        contentDescription =
                            "${aggregate.displayNote}，${formatListDuration(aggregate.durationMs)}，" +
                                "${aggregate.recordCount}条记录，点开管理"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = aggregate.displayNote,
                    modifier = Modifier.weight(1f),
                    color = Ink,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = formatListDuration(aggregate.durationMs),
                    color = Ink,
                    fontSize = 15.sp,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(11.dp))
                Text(
                    text = "›",
                    color = MutedInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            if (index < groups.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Hairline,
                )
            }
        }
    }
}

@Composable
private fun RecordDetailsDialog(
    records: List<StatisticsRecordItem>,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onDeleteRequested: (Long) -> Unit,
) {
    if (records.isEmpty()) return
    val noteLabel = records.first().displayNote

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(StatisticsSemantics.RecordDetails),
            shape = RoundedCornerShape(8.dp),
            color = MenuPaper,
            contentColor = Ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(top = 23.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = noteLabel,
                            color = Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "${records.size} 条学习记录",
                            color = MutedInk,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Ink),
                    ) {
                        Text(text = "完成")
                    }
                }

                Spacer(modifier = Modifier.height(17.dp))
                HorizontalDivider(thickness = 1.dp, color = Hairline)
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(
                        items = records,
                        key = { it.id },
                    ) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .padding(start = 22.dp, end = 10.dp)
                                .semantics {
                                    contentDescription =
                                        "${formatRecordDate(record.startedAtEpochMs, zone)}，" +
                                            "${formatListDuration(record.durationInPeriodMs)}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatRecordDate(record.startedAtEpochMs, zone),
                                    color = Ink,
                                    fontSize = 14.sp,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = formatListDuration(record.durationInPeriodMs),
                                    color = MutedInk,
                                    fontSize = 12.sp,
                                )
                            }
                            TextButton(
                                onClick = { onDeleteRequested(record.id) },
                                modifier = Modifier
                                    .testTag("${StatisticsSemantics.RecordDeletePrefix}-${record.id}")
                                    .semantics {
                                        contentDescription =
                                            "删除${formatRecordDate(record.startedAtEpochMs, zone)}的记录"
                                    },
                                colors = ButtonDefaults.textButtonColors(contentColor = Destructive),
                            ) {
                                Text(text = "删除", fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 22.dp),
                            thickness = 1.dp,
                            color = Hairline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteRecordConfirmation(
    record: StatisticsRecordItem,
    zone: ZoneId,
    isDeleting: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!isDeleting) onCancel()
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(StatisticsSemantics.DeleteConfirmation),
            shape = RoundedCornerShape(8.dp),
            color = MenuPaper,
            contentColor = Ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "删除这条记录？",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = record.displayNote,
                    color = Ink,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "${formatRecordDate(record.startedAtEpochMs, zone)} · " +
                        formatListDuration(record.durationInPeriodMs),
                    color = MutedInk,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "删除后，这段时间不会再计入任何统计，且无法恢复。",
                    color = MutedInk,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = Destructive,
                        fontSize = 13.sp,
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !isDeleting,
                        modifier = Modifier.testTag(StatisticsSemantics.DeleteCancel),
                        colors = ButtonDefaults.textButtonColors(contentColor = MutedInk),
                    ) {
                        Text(text = "取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag(StatisticsSemantics.DeleteConfirm),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Destructive,
                            contentColor = Color.White,
                            disabledContainerColor = Destructive.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.75f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            disabledElevation = 0.dp,
                        ),
                    ) {
                        Text(text = if (isDeleting) "删除中" else "确认删除")
                    }
                }
            }
        }
    }
}

private data class PeriodNavigationCopy(
    val title: String,
    val subtitle: String,
    val isLive: Boolean,
)

private fun periodNavigationCopy(
    kind: PeriodKind,
    selectedDate: LocalDate,
    today: LocalDate,
): PeriodNavigationCopy = when (kind) {
    PeriodKind.DAY -> {
        val subtitle = when (selectedDate) {
            today -> "今天 · 实时累计"
            today.minusDays(1) -> "昨天"
            today.plusDays(1) -> "明天"
            else -> chineseWeekday(selectedDate)
        }
        PeriodNavigationCopy(
            title = "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
            subtitle = subtitle,
            isLive = selectedDate == today,
        )
    }
    PeriodKind.WEEK -> {
        val start = selectedDate
        val end = start.plusDays(6)
        val currentWeek = mondayOf(today)
        val title = "${start.monthValue}月${start.dayOfMonth}日 — " +
            "${end.monthValue}月${end.dayOfMonth}日"
        val yearContext = if (start.year == end.year) {
            "${start.year}年"
        } else {
            "${start.year}—${end.year}年"
        }
        val subtitle = when (start) {
            currentWeek -> "$yearContext · 本周实时累计"
            currentWeek.minusWeeks(1) -> "$yearContext · 上一完整自然周"
            else -> "$yearContext · 自然周"
        }
        PeriodNavigationCopy(
            title = title,
            subtitle = subtitle,
            isLive = start == currentWeek,
        )
    }
    PeriodKind.MONTH -> {
        val currentMonth = today.withDayOfMonth(1)
        val subtitle = when (selectedDate) {
            currentMonth -> "本月 · 实时累计"
            currentMonth.minusMonths(1) -> "上个月 · 完整自然月"
            else -> "自然月"
        }
        PeriodNavigationCopy(
            title = "${selectedDate.year}年${selectedDate.monthValue}月",
            subtitle = subtitle,
            isLive = selectedDate == currentMonth,
        )
    }
    PeriodKind.YEAR -> PeriodNavigationCopy(
        title = "${selectedDate.year} 年",
        subtitle = if (selectedDate.year == today.year) "今年 · 实时累计" else "自然年",
        isLive = selectedDate.year == today.year,
    )
    PeriodKind.ALL -> PeriodNavigationCopy(
        title = "使用以来",
        subtitle = "全部学习记录",
        isLive = false,
    )
}

private fun periodModeLabel(kind: PeriodKind): String = when (kind) {
    PeriodKind.DAY -> "日统计"
    PeriodKind.WEEK -> "周统计"
    PeriodKind.MONTH -> "月统计"
    PeriodKind.YEAR -> "年统计"
    PeriodKind.ALL -> "全部统计"
}

private fun periodOverviewLabel(report: PeriodReport, today: LocalDate): String =
    when (report.kind) {
        PeriodKind.DAY -> if (report.period.startDate == today) "今日" else "当日累计"
        PeriodKind.WEEK -> "当周累计"
        PeriodKind.MONTH -> "当月累计"
        PeriodKind.YEAR -> "年度累计"
        PeriodKind.ALL -> "总学习时间"
    }

private fun previousPeriodDescription(kind: PeriodKind): String = when (kind) {
    PeriodKind.DAY -> "查看前一天"
    PeriodKind.WEEK -> "查看前一周"
    PeriodKind.MONTH -> "查看上个月"
    PeriodKind.YEAR -> "查看上一年"
    PeriodKind.ALL -> "使用以来没有上一周期"
}

private fun nextPeriodDescription(kind: PeriodKind): String = when (kind) {
    PeriodKind.DAY -> "查看后一天"
    PeriodKind.WEEK -> "查看后一周"
    PeriodKind.MONTH -> "查看下个月"
    PeriodKind.YEAR -> "查看下一年"
    PeriodKind.ALL -> "使用以来没有下一周期"
}

private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

private fun chineseWeekday(date: LocalDate): String =
    listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        .elementAt(date.dayOfWeek.value - 1)

private fun formatRecordDate(startedAtEpochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).format(RecordDateFormatter)

private fun formatListDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}小时${minutes}分"
        hours > 0L -> "${hours}小时"
        totalMinutes > 0L -> "${totalMinutes}分钟"
        durationMs > 0L -> "不到1分钟"
        else -> "0分钟"
    }
}
