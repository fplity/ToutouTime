package com.example.studenttimetotalnote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.studenttimetotalnote.ui.theme.Charcoal
import com.example.studenttimetotalnote.ui.theme.DeepSage
import com.example.studenttimetotalnote.ui.theme.MutedCharcoal
import com.example.studenttimetotalnote.ui.theme.PaleSage
import com.example.studenttimetotalnote.ui.theme.SoftIvory
import com.example.studenttimetotalnote.ui.theme.ThinDivider
import com.example.studenttimetotalnote.ui.theme.WarmOutline

object StudyTimerSemantics {
    const val Timer = "study_timer"
    const val TimerValue = "study_timer_value"
    const val PrimaryButton = "study_primary_button"
    const val NoteField = "study_note_field"
    const val StatisticsSection = "study_statistics_section"
    const val StatCard = "study_stat_card"
    const val RankingRow = "study_ranking_row"
    const val SegmentedControl = "study_segmented_control"
    const val Segment = "study_segment"
}

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun StudyTimer(
    elapsedTime: String = "00:00:00",
    statusLabel: String = "准备开始",
    note: String? = null,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StudyTimerSemantics.Timer)
            .semantics {
                contentDescription = "学习计时器，$elapsedTime"
                stateDescription = if (isRunning) "正在计时" else "未开始"
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SoftIvory),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusLabel,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = MutedCharcoal,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = elapsedTime,
                modifier = Modifier.testTag(StudyTimerSemantics.TimerValue),
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                color = Charcoal,
            )
            if (!note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    color = DeepSage,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    semanticTag: String = StudyTimerSemantics.PrimaryButton,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(semanticTag),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = DeepSage,
            contentColor = Color.White,
            disabledContainerColor = PaleSage,
            disabledContentColor = MutedCharcoal,
        ),
    ) {
        Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StudyNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "学习备注",
    placeholder: String = "例如：数学",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(StudyTimerSemantics.NoteField),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DeepSage,
            unfocusedBorderColor = WarmOutline,
            focusedLabelColor = DeepSage,
            cursorColor = DeepSage,
        ),
    )
}

@Composable
fun StatisticsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StudyTimerSemantics.StatisticsSection),
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = Charcoal,
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    semanticTag: String = StudyTimerSemantics.StatCard,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(semanticTag),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = PaleSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = MutedCharcoal,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Charcoal,
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = supportingText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = MutedCharcoal,
                )
            }
        }
    }
}

@Composable
fun RankingRow(
    rank: Int,
    label: String,
    duration: String,
    modifier: Modifier = Modifier,
    semanticTag: String = StudyTimerSemantics.RankingRow,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(semanticTag)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PaleSage),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = DeepSage,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = Charcoal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = duration,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = MutedCharcoal,
        )
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semanticTag: String = StudyTimerSemantics.SegmentedControl,
) {
    if (options.isEmpty()) return

    val safeSelectedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, ThinDivider), RoundedCornerShape(14.dp))
            .testTag(semanticTag)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == safeSelectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) PaleSage else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = { onSelectedIndexChange(index) },
                        role = Role.Tab,
                    )
                    .testTag("${StudyTimerSemantics.Segment}-$index")
                    .semantics {
                        this.selected = selected
                        contentDescription = option
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = if (selected) DeepSage else MutedCharcoal,
                )
            }
        }
    }
}

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = ThinDivider,
    )
}
