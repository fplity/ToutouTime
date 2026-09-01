package com.example.studenttimetotalnote.domain.model

import java.time.Instant
import java.time.LocalDate

enum class PeriodKind {
    DAY,
    WEEK,
    MONTH,
}

data class ReportPeriod(
    val kind: PeriodKind,
    val label: String,
    val startInclusive: Instant,
    val endExclusive: Instant,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
)

data class NoteAggregate(
    val noteText: String,
    val displayNote: String = if (noteText.isEmpty()) "未备注" else noteText,
    val durationMs: Long,
    val recordCount: Int,
)

data class PeriodReport(
    val period: ReportPeriod,
    val totalDurationMs: Long,
    val groups: List<NoteAggregate>,
) {
    val kind: PeriodKind
        get() = period.kind

    val label: String
        get() = period.label

    val startInclusive = period.startInclusive

    val endExclusive = period.endExclusive

    val hasData: Boolean
        get() = groups.isNotEmpty() && totalDurationMs > 0L
}
