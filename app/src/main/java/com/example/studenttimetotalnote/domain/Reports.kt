package com.example.studenttimetotalnote.domain

import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.model.ReportPeriod
import com.example.studenttimetotalnote.domain.model.NoteAggregate
import com.example.studenttimetotalnote.domain.model.StudyRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

fun resolveReportPeriod(kind: PeriodKind, now: Instant, zone: ZoneId): ReportPeriod =
    resolveReportPeriod(kind, now.atZone(zone), zone)

fun resolveReportPeriod(kind: PeriodKind, now: ZonedDateTime, zone: ZoneId): ReportPeriod {
    val localDate = now.withZoneSameInstant(zone).toLocalDate()
    val (startDate, endDateExclusive, label) = when (kind) {
        PeriodKind.DAY -> Triple(
            localDate,
            localDate.plusDays(1),
            "今日（$localDate）",
        )
        PeriodKind.WEEK -> {
            val currentStart = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val previousStart = currentStart.minusWeeks(1)
            Triple(
                previousStart,
                currentStart,
                "上一完整自然周（$previousStart 至 ${currentStart.minusDays(1)}）",
            )
        }
        PeriodKind.MONTH -> {
            val currentStart = localDate.withDayOfMonth(1)
            val previousStart = currentStart.minusMonths(1)
            Triple(
                previousStart,
                currentStart,
                "上一完整自然月（$previousStart 至 ${currentStart.minusDays(1)}）",
            )
        }
    }

    return ReportPeriod(
        kind = kind,
        label = label,
        startInclusive = startDate.atStartOfDay(zone).toInstant(),
        endExclusive = endDateExclusive.atStartOfDay(zone).toInstant(),
        startDate = startDate,
        endDateExclusive = endDateExclusive,
    )
}

fun todayReport(records: Iterable<StudyRecord>, now: Instant, zone: ZoneId): PeriodReport =
    aggregate(records, resolveTodayPeriod(now, zone))

fun aggregate(records: Iterable<StudyRecord>, period: ReportPeriod): PeriodReport {
    val grouped = linkedMapOf<String, MutableList<Long>>()
    for (record in records) {
        val recordStart = Instant.ofEpochMilli(record.startedAtEpochMs)
        val recordEnd = Instant.ofEpochMilli(record.endedAtEpochMs)
        val overlapStart = maxOf(recordStart, period.startInclusive)
        val overlapEnd = minOf(recordEnd, period.endExclusive)
        val overlapMs = overlapEnd.toEpochMilli() - overlapStart.toEpochMilli()
        if (overlapMs > 0L) {
            grouped.getOrPut(record.noteText) { mutableListOf() }.add(overlapMs)
        }
    }

    val groups = grouped.entries
        .map { (note, durations) ->
            NoteAggregate(
                noteText = note,
                durationMs = durations.sum(),
                recordCount = durations.size,
            )
        }
        .sortedWith(compareByDescending<NoteAggregate> { it.durationMs }.thenBy { it.noteText })

    return PeriodReport(
        period = period,
        totalDurationMs = groups.sumOf { it.durationMs },
        groups = groups,
    )
}

private fun resolveTodayPeriod(now: Instant, zone: ZoneId): ReportPeriod {
    val date = now.atZone(zone).toLocalDate()
    val nextDate = date.plusDays(1)
    return ReportPeriod(
        kind = PeriodKind.DAY,
        label = "今日（$date）",
        startInclusive = date.atStartOfDay(zone).toInstant(),
        endExclusive = nextDate.atStartOfDay(zone).toInstant(),
        startDate = date,
        endDateExclusive = nextDate,
    )
}
