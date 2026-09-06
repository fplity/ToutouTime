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
    resolveReportPeriod(
        kind = kind,
        selectedDate = defaultReportDate(kind, now, zone),
        now = now,
        zone = zone,
    )

fun resolveReportPeriod(kind: PeriodKind, now: ZonedDateTime, zone: ZoneId): ReportPeriod =
    resolveReportPeriod(kind, now.toInstant(), zone)

fun defaultReportDate(kind: PeriodKind, now: Instant, zone: ZoneId): LocalDate {
    val today = now.atZone(zone).toLocalDate()
    return when (kind) {
        PeriodKind.DAY -> today
        PeriodKind.WEEK -> today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1)
        PeriodKind.MONTH -> today.withDayOfMonth(1).minusMonths(1)
        PeriodKind.YEAR -> today.withDayOfYear(1)
        PeriodKind.ALL -> today
    }
}

fun resolveReportPeriod(
    kind: PeriodKind,
    selectedDate: LocalDate,
    now: Instant,
    zone: ZoneId,
): ReportPeriod {
    if (kind == PeriodKind.ALL) {
        return ReportPeriod(
            kind = PeriodKind.ALL,
            label = "使用偷偷时间以来",
            startInclusive = Instant.ofEpochMilli(Long.MIN_VALUE),
            endExclusive = Instant.ofEpochMilli(Long.MAX_VALUE),
            startDate = LocalDate.MIN,
            endDateExclusive = LocalDate.MAX,
        )
    }

    val today = now.atZone(zone).toLocalDate()
    val startDate = normalizeReportDate(kind, selectedDate)
    require(startDate.year in MIN_REPORT_YEAR..MAX_REPORT_YEAR) {
        "Date year must be between $MIN_REPORT_YEAR and $MAX_REPORT_YEAR"
    }
    val endDateExclusive = when (kind) {
        PeriodKind.DAY -> startDate.plusDays(1)
        PeriodKind.WEEK -> startDate.plusWeeks(1)
        PeriodKind.MONTH -> startDate.plusMonths(1)
        PeriodKind.YEAR -> startDate.plusYears(1)
        PeriodKind.ALL -> error("All-time report is resolved before dated periods")
    }
    val label = reportPeriodLabel(kind, startDate, endDateExclusive, today)

    return ReportPeriod(
        kind = kind,
        label = label,
        startInclusive = startDate.atStartOfDay(zone).toInstant(),
        endExclusive = endDateExclusive.atStartOfDay(zone).toInstant(),
        startDate = startDate,
        endDateExclusive = endDateExclusive,
    )
}

fun resolveYearReportPeriod(year: Int, now: Instant, zone: ZoneId): ReportPeriod {
    require(year in MIN_REPORT_YEAR..MAX_REPORT_YEAR) {
        "Year must be between $MIN_REPORT_YEAR and $MAX_REPORT_YEAR"
    }
    return resolveReportPeriod(
        kind = PeriodKind.YEAR,
        selectedDate = LocalDate.of(year, 1, 1),
        now = now,
        zone = zone,
    )
}

fun shiftReportDate(kind: PeriodKind, selectedDate: LocalDate, steps: Int): LocalDate {
    val normalized = normalizeReportDate(kind, selectedDate)
    val shifted = when (kind) {
        PeriodKind.DAY -> normalized.plusDays(steps.toLong())
        PeriodKind.WEEK -> normalized.plusWeeks(steps.toLong())
        PeriodKind.MONTH -> normalized.plusMonths(steps.toLong())
        PeriodKind.YEAR -> normalized.plusYears(steps.toLong())
        PeriodKind.ALL -> throw IllegalArgumentException("All-time report cannot be shifted")
    }
    require(shifted.year in MIN_REPORT_YEAR..MAX_REPORT_YEAR) {
        "Date year must be between $MIN_REPORT_YEAR and $MAX_REPORT_YEAR"
    }
    return shifted
}

fun canShiftReportDate(kind: PeriodKind, selectedDate: LocalDate, steps: Int): Boolean =
    kind != PeriodKind.ALL && runCatching {
        shiftReportDate(kind, selectedDate, steps)
    }.isSuccess

private fun normalizeReportDate(kind: PeriodKind, selectedDate: LocalDate): LocalDate =
    when (kind) {
        PeriodKind.DAY -> selectedDate
        PeriodKind.WEEK -> selectedDate.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
        )
        PeriodKind.MONTH -> selectedDate.withDayOfMonth(1)
        PeriodKind.YEAR -> selectedDate.withDayOfYear(1)
        PeriodKind.ALL -> selectedDate
    }

private fun reportPeriodLabel(
    kind: PeriodKind,
    startDate: LocalDate,
    endDateExclusive: LocalDate,
    today: LocalDate,
): String {
    val endDate = endDateExclusive.minusDays(1)
    return when (kind) {
        PeriodKind.DAY -> when (startDate) {
            today -> "今日（$startDate）"
            today.minusDays(1) -> "昨天（$startDate）"
            else -> "日期（$startDate）"
        }
        PeriodKind.WEEK -> {
            val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            when (startDate) {
                currentWeek -> "本自然周（$startDate 至 $endDate，截至 $today）"
                currentWeek.minusWeeks(1) -> "上一完整自然周（$startDate 至 $endDate）"
                else -> "自然周（$startDate 至 $endDate）"
            }
        }
        PeriodKind.MONTH -> {
            val currentMonth = today.withDayOfMonth(1)
            when (startDate) {
                currentMonth -> "本自然月（$startDate 至 $endDate，截至 $today）"
                currentMonth.minusMonths(1) -> "上一完整自然月（$startDate 至 $endDate）"
                else -> "自然月（$startDate 至 $endDate）"
            }
        }
        PeriodKind.YEAR -> if (startDate.year == today.year) {
            "${startDate.year}年（截至 $today）"
        } else {
            "${startDate.year}年（$startDate 至 $endDate）"
        }
        PeriodKind.ALL -> "使用偷偷时间以来"
    }
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

private fun resolveTodayPeriod(now: Instant, zone: ZoneId): ReportPeriod =
    resolveReportPeriod(PeriodKind.DAY, now, zone)

const val MIN_REPORT_YEAR = 1
const val MAX_REPORT_YEAR = 9998
