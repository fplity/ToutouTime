package com.example.studenttimetotalnote.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.ui.components.StudyTimerSemantics
import com.example.studenttimetotalnote.ui.theme.Cobalt
import com.example.studenttimetotalnote.ui.theme.Hairline
import com.example.studenttimetotalnote.ui.theme.Ink
import com.example.studenttimetotalnote.ui.theme.MenuPaper
import com.example.studenttimetotalnote.ui.theme.MutedInk
import com.example.studenttimetotalnote.ui.theme.Paper

object HomeSemantics {
    const val TodaySummary = "home_today_summary"
    const val StatisticsButton = "home_statistics_button"
    const val WeeklyButton = "home_weekly_button"
    const val MonthlyButton = "home_monthly_button"
    const val Feedback = "home_feedback"
    const val NoteDialog = "home_note_dialog"
    const val NoteDialogCancel = "home_note_dialog_cancel"
    const val NoteDialogConfirm = "home_note_dialog_confirm"
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenStatistics: (PeriodKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onLifecycleResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.feedbackMessage) {
        if (state.feedbackMessage != null) {
            kotlinx.coroutines.delay(FEEDBACK_DURATION_MS)
            viewModel.onFeedbackConsumed()
        }
    }

    BackHandler(enabled = state.noteDialogVisible) {
        viewModel.onNoteDialogCancelled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        HomeHeader(onOpenStatistics = { onOpenStatistics(PeriodKind.DAY) })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            TimerFocus(
                elapsedTime = formatClockDuration(state.elapsedMs),
                note = state.activeSession?.noteText,
                isRunning = state.isRunning,
                feedback = state.feedbackMessage,
            )
        }

        Button(
            onClick = {
                if (state.isRunning) {
                    viewModel.onFinishRequested()
                } else {
                    viewModel.onStartRequested()
                }
            },
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag(StudyTimerSemantics.PrimaryButton)
                .semantics {
                    contentDescription = if (state.isRunning) "结束并保存" else "开始学习"
                },
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Color.White,
                disabledContainerColor = Ink.copy(alpha = 0.52f),
                disabledContentColor = Color.White.copy(alpha = 0.72f),
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        ) {
            Text(
                text = if (state.isRunning) "结束计时" else "开始学习",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }

        TextButton(
            onClick = { onOpenStatistics(PeriodKind.DAY) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .height(52.dp)
                .testTag(HomeSemantics.TodaySummary)
                .semantics {
                    contentDescription = if (state.hasTodaySummary) {
                        "查看今日记录"
                    } else {
                        "查看今日记录，暂无数据"
                    }
                },
            colors = ButtonDefaults.textButtonColors(contentColor = MutedInk),
        ) {
            Text(
                text = "查看今日记录",
                fontSize = 14.sp,
                letterSpacing = 0.3.sp,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
    }

    if (state.noteDialogVisible) {
        NoteDialog(
            note = state.noteDraft,
            onNoteChanged = viewModel::onNoteChanged,
            onCancel = viewModel::onNoteDialogCancelled,
            onConfirm = viewModel::onNoteDialogConfirmed,
        )
    }
}

@Composable
private fun HomeHeader(onOpenStatistics: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "偷偷时间",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onOpenStatistics,
            modifier = Modifier
                .size(44.dp)
                .testTag(HomeSemantics.StatisticsButton)
                .semantics { contentDescription = "查看统计" },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            colors = ButtonDefaults.textButtonColors(contentColor = Ink),
        ) {
            BarChartIcon()
        }
    }
}

@Composable
private fun BarChartIcon() {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 22.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        val bottom = size.height
        val xPositions = listOf(size.width * 0.18f, size.width * 0.50f, size.width * 0.82f)
        val topPositions = listOf(size.height * 0.54f, size.height * 0.18f, size.height * 0.36f)
        xPositions.forEachIndexed { index, x ->
            drawLine(
                color = Ink,
                start = androidx.compose.ui.geometry.Offset(x, topPositions[index]),
                end = androidx.compose.ui.geometry.Offset(x, bottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Square,
            )
        }
    }
}

@Composable
private fun TimerFocus(
    elapsedTime: String,
    note: String?,
    isRunning: Boolean,
    feedback: String?,
) {
    val supportingText = when {
        feedback != null -> feedback
        isRunning && note.isNullOrEmpty() -> "正在记录这段专注"
        isRunning -> note.orEmpty()
        else -> "把时间留给重要的事"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StudyTimerSemantics.Timer)
            .semantics {
                contentDescription = "学习计时器，$elapsedTime"
                stateDescription = if (isRunning) "正在计时" else "未开始"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isRunning) "正在专注" else "准备专注",
            color = if (isRunning) Cobalt else MutedInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = elapsedTime,
            modifier = Modifier.testTag(StudyTimerSemantics.TimerValue),
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
            color = Ink,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = supportingText,
            modifier = if (feedback != null) {
                Modifier
                    .testTag(HomeSemantics.Feedback)
                    .semantics { contentDescription = feedback }
                    .padding(horizontal = 12.dp)
            } else {
                Modifier.padding(horizontal = 12.dp)
            },
            color = MutedInk,
            fontSize = 15.sp,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteDialog(
    note: String,
    onNoteChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeSemantics.NoteDialog),
            shape = RoundedCornerShape(8.dp),
            color = MenuPaper,
            contentColor = Ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Hairline),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "这次学习什么？",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "完全相同的备注会合并统计",
                    fontSize = 13.sp,
                    color = MutedInk,
                )
                Spacer(modifier = Modifier.height(22.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(StudyTimerSemantics.NoteField),
                    placeholder = {
                        Text(
                            text = "例如：Java",
                            color = MutedInk.copy(alpha = 0.72f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedBorderColor = Ink,
                        unfocusedBorderColor = Hairline,
                        cursorColor = Cobalt,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag(HomeSemantics.NoteDialogCancel),
                        colors = ButtonDefaults.textButtonColors(contentColor = MutedInk),
                    ) {
                        Text(text = "取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag(HomeSemantics.NoteDialogConfirm),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ink,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                        ),
                    ) {
                        Text(text = "开始计时")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100L)
        focusRequester.requestFocus()
    }
}

private fun formatClockDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
}

private const val FEEDBACK_DURATION_MS = 2_000L
