package com.example.studenttimetotalnote.domain

import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.model.StudyRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class TrendPoint(
    val label: String,
    val durationMs: Long,
    val emphasized: Boolean = false,
)

data class StatisticsRecordItem(
    val id: Long,
    val noteText: String,
    val startedAtEpochMs: Long,
    val durationInPeriodMs: Long,
) {
    val displayNote: String
        get() = noteText.ifEmpty { "未备注" }
}

data class StatisticsReport(
    val report: PeriodReport,
    val trend: List<TrendPoint>,
    val records: List<StatisticsRecordItem>,
)

/** All three views are derived from exactly the same immutable record snapshot. */
fun statisticsReport(
    records: List<StudyRecord>,
    kind: PeriodKind,
    date: LocalDate,
    now: Instant,
    zone: ZoneId,
): StatisticsReport {
    val report = aggregate(records, resolveReportPeriod(kind, date, now, zone))
    return StatisticsReport(
        report = report,
        trend = buildTrend(records, report, now, zone),
        records = recordsForReport(records, report),
    )
}

fun recordsForReport(
    records: List<StudyRecord>,
    report: PeriodReport,
): List<StatisticsRecordItem> = records.mapNotNull { record ->
    val overlapMs = overlapDuration(
        record = record,
        startInclusive = report.startInclusive,
        endExclusive = report.endExclusive,
    )
    if (overlapMs <= 0L) {
        null
    } else {
        StatisticsRecordItem(
            id = record.id,
            noteText = record.noteText,
            startedAtEpochMs = record.startedAtEpochMs,
            durationInPeriodMs = overlapMs,
        )
    }
}.sortedWith(
    compareByDescending<StatisticsRecordItem> { it.startedAtEpochMs }
        .thenByDescending { it.id },
)

fun buildTrend(
    records: List<StudyRecord>,
    report: PeriodReport,
    now: Instant,
    zone: ZoneId,
): List<TrendPoint> {
    val buckets = when (report.kind) {
        PeriodKind.DAY -> weekContainingDateBuckets(report.period.startDate, zone)
        PeriodKind.WEEK -> dailyBuckets(report.period.startDate, 7, zone)
        PeriodKind.MONTH -> monthlyBuckets(report, zone)
        PeriodKind.YEAR -> yearlyBuckets(report, zone)
        PeriodKind.ALL -> emptyList()
    }
    val durations = buckets.map { bucket ->
        records.sumOf { record ->
            overlapDuration(record, bucket.startInclusive, bucket.endExclusive)
        }
    }
    val today = now.atZone(zone).toLocalDate()
    val focusDate = when {
        report.kind == PeriodKind.DAY -> report.period.startDate
        !today.isBefore(report.period.startDate) &&
            today.isBefore(report.period.endDateExclusive) -> today
        else -> null
    }
    val emphasizedIndex = focusDate
        ?.let { date ->
            buckets.indexOfFirst { bucket ->
                !date.isBefore(bucket.startDate) && date.isBefore(bucket.endDateExclusive)
            }
        }
        ?.takeIf { it >= 0 }
        ?: (durations.indices.maxByOrNull { durations[it] } ?: 0)
    return buckets.mapIndexed { index, bucket ->
        TrendPoint(
            label = bucket.label,
            durationMs = durations[index],
            emphasized = index == emphasizedIndex,
        )
    }
}

private data class TrendBucket(
    val label: String,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val startInclusive: Instant,
    val endExclusive: Instant,
)

private fun weekContainingDateBuckets(date: LocalDate, zone: ZoneId): List<TrendBucket> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return dailyBuckets(monday, 7, zone)
}

private fun dailyBuckets(
    startDate: LocalDate,
    count: Int,
    zone: ZoneId,
): List<TrendBucket> {
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    return List(count) { index ->
        val start = startDate.plusDays(index.toLong())
        val end = start.plusDays(1)
        TrendBucket(
            label = weekdayLabels.getOrElse(index) { start.dayOfMonth.toString() },
            startDate = start,
            endDateExclusive = end,
            startInclusive = start.atStartOfDay(zone).toInstant(),
            endExclusive = end.atStartOfDay(zone).toInstant(),
        )
    }
}

private fun monthlyBuckets(report: PeriodReport, zone: ZoneId): List<TrendBucket> {
    val startDate = report.period.startDate
    val endDate = report.period.endDateExclusive
    val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()
    return List(TREND_POINT_COUNT) { index ->
        val startOffset = index * totalDays / TREND_POINT_COUNT
        val endOffset = (index + 1) * totalDays / TREND_POINT_COUNT
        val start = startDate.plusDays(startOffset.toLong())
        val end = startDate.plusDays(endOffset.toLong())
        TrendBucket(
            label = start.dayOfMonth.toString(),
            startDate = start,
            endDateExclusive = end,
            startInclusive = start.atStartOfDay(zone).toInstant(),
            endExclusive = end.atStartOfDay(zone).toInstant(),
        )
    }
}

private fun yearlyBuckets(report: PeriodReport, zone: ZoneId): List<TrendBucket> =
    List(MONTHS_IN_YEAR) { index ->
        val start = report.period.startDate.plusMonths(index.toLong())
        val end = start.plusMonths(1)
        TrendBucket(
            label = "${index + 1}月",
            startDate = start,
            endDateExclusive = end,
            startInclusive = start.atStartOfDay(zone).toInstant(),
            endExclusive = end.atStartOfDay(zone).toInstant(),
        )
    }


private const val TREND_POINT_COUNT = 7
private const val MONTHS_IN_YEAR = 12
