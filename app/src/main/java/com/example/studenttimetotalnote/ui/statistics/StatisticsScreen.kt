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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object StatisticsSemantics {
    const val Screen = "statistics_screen"
    const val Back = "statistics_back"
    const val PeriodTabs = "statistics_period_tabs"
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
    val viewModel = remember(repository, initialPeriod, zone) {
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
            onBack = onBack,
            onPeriodSelected = onPeriodSelected,
        )

        if (report?.hasData == true) {
            Spacer(modifier = Modifier.height(39.dp))
            PeriodOverview(report = report)

            Spacer(modifier = Modifier.height(34.dp))
            TrendChart(points = uiState.trend)

            Spacer(modifier = Modifier.height(51.dp))
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
                        contentDescription = "选择统计周期，${periodDisplayLabel(selectedPeriod)}"
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
private fun PeriodOverview(report: PeriodReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StatisticsSemantics.PeriodLabel)
            .semantics {
                contentDescription = "${periodDisplayLabel(report.kind)}，${report.label}"
            },
    ) {
        Text(
            text = periodDisplayLabel(report.kind),
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
                    fontSize = 13.sp,
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
                    text = "删除后，这段时间不会再计入今日、周或月统计，且无法恢复。",
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

private fun periodDisplayLabel(kind: PeriodKind): String = when (kind) {
    PeriodKind.DAY -> "今日"
    PeriodKind.WEEK -> "上一周"
    PeriodKind.MONTH -> "上个月"
}

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
