package com.example.studenttimetotalnote

import com.example.studenttimetotalnote.data.StudyTimerStore
import com.example.studenttimetotalnote.domain.DefaultStudyTimerRepository
import com.example.studenttimetotalnote.domain.aggregate
import com.example.studenttimetotalnote.domain.resolveReportPeriod
import com.example.studenttimetotalnote.domain.resolveYearReportPeriod
import com.example.studenttimetotalnote.domain.todayReport
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.ReportPeriod
import com.example.studenttimetotalnote.domain.model.StudyRecord
import com.example.studenttimetotalnote.ui.statistics.buildTrend
import com.example.studenttimetotalnote.ui.statistics.recordsForReport
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimerDomainTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun beginTrimsOnlyEdgesAndDoesNotCreateCompletedRecord() = runBlocking {
        val store = FakeStudyTimerStore()
        val repository = DefaultStudyTimerRepository(store)

        val active = repository.beginSession("  数学  题  ", instant("2026-08-26T10:00:00Z"))

        assertEquals("数学  题", active.noteText)
        assertEquals(active, repository.observeActiveSession())
        assertTrue(repository.observeRecords().isEmpty())
    }

    @Test
    fun finishIsAtomicAndIdempotent() = runBlocking {
        val store = FakeStudyTimerStore()
        val repository = DefaultStudyTimerRepository(store)
        val start = instant("2026-08-26T10:00:00Z")
        repository.beginSession("阅读", start)

        val first = repository.finishSession(start.plusSeconds(90))
        val second = repository.finishSession(start.plusSeconds(90))

        assertNotNull(first)
        assertEquals(90_000L, first!!.durationMs)
        assertNull(repository.observeActiveSession())
        assertNull(second)
        assertEquals(1, repository.observeRecords().size)
    }

    @Test
    fun deletingOneRecordRemovesOnlyItsTimeFromReports() = runBlocking {
        val repository = DefaultStudyTimerRepository(FakeStudyTimerStore())
        val firstStart = instant("2026-08-26T09:00:00Z")
        repository.beginSession("Java", firstStart)
        val first = repository.finishSession(firstStart.plusSeconds(30 * 60))!!
        val secondStart = instant("2026-08-26T10:00:00Z")
        repository.beginSession("Java", secondStart)
        val second = repository.finishSession(secondStart.plusSeconds(20 * 60))!!

        assertTrue(repository.deleteRecord(first.id))
        assertFalse(repository.deleteRecord(first.id))

        val remaining = repository.observeRecords()
        val report = todayReport(remaining, instant("2026-08-26T12:00:00Z"), zone)
        assertEquals(listOf(second.id), remaining.map { it.id })
        assertEquals(20 * 60_000L, report.totalDurationMs)
        assertEquals(1, report.groups.single().recordCount)
    }

    @Test(expected = IllegalStateException::class)
    fun secondBeginCannotReplaceTheRecoverableActiveSession(): Unit = runBlocking {
        val repository = DefaultStudyTimerRepository(FakeStudyTimerStore())
        repository.beginSession("第一次", instant("2026-08-26T10:00:00Z"))

        repository.beginSession("第二次", instant("2026-08-26T10:01:00Z"))
    }

    @Test
    fun naturalIsoWeekAndMonthResolveToPreviousCompletePeriods() {
        val now = instant("2026-08-26T12:00:00Z")

        val week = resolveReportPeriod(PeriodKind.WEEK, now, zone)
        assertEquals(LocalDate.of(2026, 8, 17), week.startDate)
        assertEquals(LocalDate.of(2026, 8, 24), week.endDateExclusive)
        assertEquals("上一完整自然周（2026-08-17 至 2026-08-23）", week.label)
        assertEquals(7, Duration.between(week.startInclusive, week.endExclusive).toDays())

        val month = resolveReportPeriod(PeriodKind.MONTH, now, zone)
        assertEquals(LocalDate.of(2026, 7, 1), month.startDate)
        assertEquals(LocalDate.of(2026, 8, 1), month.endDateExclusive)
        assertEquals("上一完整自然月（2026-07-01 至 2026-07-31）", month.label)
    }

    @Test
    fun monthResolutionHandles28And29And30And31DayMonths() {
        assertEquals(
            28L,
            resolveReportPeriod(PeriodKind.MONTH, instant("2023-03-15T12:00:00Z"), zone)
                .endDateExclusive.toEpochDay() - resolveReportPeriod(
                PeriodKind.MONTH,
                instant("2023-03-15T12:00:00Z"),
                zone,
            ).startDate.toEpochDay(),
        )
        assertEquals(
            29L,
            resolveReportPeriod(PeriodKind.MONTH, instant("2024-03-15T12:00:00Z"), zone)
                .endDateExclusive.toEpochDay() - resolveReportPeriod(
                PeriodKind.MONTH,
                instant("2024-03-15T12:00:00Z"),
                zone,
            ).startDate.toEpochDay(),
        )
        assertEquals(
            30L,
            resolveReportPeriod(PeriodKind.MONTH, instant("2026-05-15T12:00:00Z"), zone)
                .endDateExclusive.toEpochDay() - resolveReportPeriod(
                PeriodKind.MONTH,
                instant("2026-05-15T12:00:00Z"),
                zone,
            ).startDate.toEpochDay(),
        )
        assertEquals(
            31L,
            resolveReportPeriod(PeriodKind.MONTH, instant("2026-02-15T12:00:00Z"), zone)
                .endDateExclusive.toEpochDay() - resolveReportPeriod(
                PeriodKind.MONTH,
                instant("2026-02-15T12:00:00Z"),
                zone,
            ).startDate.toEpochDay(),
        )
    }

    @Test
    fun annualPeriodsUseNaturalYearAndIdentifyTheExactSelectedYear() {
        val now = instant("2026-09-04T12:00:00Z")

        val current = resolveYearReportPeriod(2026, now, zone)
        assertEquals(PeriodKind.YEAR, current.kind)
        assertEquals(LocalDate.of(2026, 1, 1), current.startDate)
        assertEquals(LocalDate.of(2027, 1, 1), current.endDateExclusive)
        assertEquals("2026年（截至 2026-09-04）", current.label)

        val leapYear = resolveYearReportPeriod(2024, now, zone)
        assertEquals(366L, Duration.between(leapYear.startInclusive, leapYear.endExclusive).toDays())
        assertEquals("2024年（2024-01-01 至 2024-12-31）", leapYear.label)

        val future = resolveYearReportPeriod(2027, now, zone)
        assertEquals(LocalDate.of(2027, 1, 1), future.startDate)
        assertEquals(LocalDate.of(2028, 1, 1), future.endDateExclusive)
        assertEquals("2027年（2027-01-01 至 2027-12-31）", future.label)
    }

    @Test
    fun annualAggregationSplitsARecordAtTheYearBoundary() {
        val now = instant("2026-09-04T12:00:00Z")
        val crossing = record(
            note = "跨年学习",
            start = "2025-12-31T23:30:00Z",
            end = "2026-01-01T00:30:00Z",
        )

        val report2025 = aggregate(listOf(crossing), resolveYearReportPeriod(2025, now, zone))
        val report2026 = aggregate(listOf(crossing), resolveYearReportPeriod(2026, now, zone))

        assertEquals(30 * 60_000L, report2025.totalDurationMs)
        assertEquals(30 * 60_000L, report2026.totalDurationMs)
        assertEquals("跨年学习", report2026.groups.single().noteText)
    }

    @Test
    fun annualTrendHasTwelveMonthsAndHighlightsTheCurrentMonth() {
        val now = instant("2026-09-04T12:00:00Z")
        val records = listOf(
            record("Java", "2026-01-10T09:00:00Z", "2026-01-10T09:10:00Z"),
            record("Java", "2026-09-03T09:00:00Z", "2026-09-03T09:30:00Z"),
        )
        val report = aggregate(records, resolveYearReportPeriod(2026, now, zone))

        val trend = buildTrend(records, report, now, zone)

        assertEquals((1..12).map { "${it}月" }, trend.map { it.label })
        assertEquals(10 * 60_000L, trend[0].durationMs)
        assertEquals(30 * 60_000L, trend[8].durationMs)
        assertTrue(trend[8].emphasized)
        assertEquals(1, trend.count { it.emphasized })
    }

    @Test
    fun repositoryCanLoadAndDeleteFromASelectedAnnualReport() = runBlocking {
        val repository = DefaultStudyTimerRepository(FakeStudyTimerStore())
        val startedAt = instant("2026-06-01T09:00:00Z")
        repository.beginSession("年度复习", startedAt)
        val record = repository.finishSession(startedAt.plusSeconds(45 * 60))!!
        val now = instant("2026-09-04T12:00:00Z")

        assertEquals(45 * 60_000L, repository.yearReport(2026, now, zone).totalDurationMs)
        assertTrue(repository.deleteRecord(record.id))
        assertFalse(repository.yearReport(2026, now, zone).hasData)
    }

    @Test
    fun intersectionSplitsAcrossDayWeekAndMonthBoundaries() {
        val weekBefore = resolveReportPeriod(PeriodKind.WEEK, instant("2026-08-26T12:00:00Z"), zone)
        val weekAfter = resolveReportPeriod(PeriodKind.WEEK, instant("2026-09-02T12:00:00Z"), zone)
        val record = record(
            note = "边界",
            start = "2026-08-23T23:59:00Z",
            end = "2026-08-24T00:01:00Z",
        )
        assertEquals(60_000L, aggregate(listOf(record), weekBefore).totalDurationMs)
        assertEquals(60_000L, aggregate(listOf(record), weekAfter).totalDurationMs)

        val month = resolveReportPeriod(PeriodKind.MONTH, instant("2026-09-10T12:00:00Z"), zone)
        val monthRecord = record(
            note = "月界",
            start = "2026-07-31T23:59:00Z",
            end = "2026-08-01T00:01:00Z",
        )
        assertEquals(60_000L, aggregate(listOf(monthRecord), month).totalDurationMs)

        val day = dayPeriod(LocalDate.of(2026, 8, 26), zone)
        val dayRecord = record(
            note = "日界",
            start = "2026-08-25T23:59:00Z",
            end = "2026-08-26T00:01:00Z",
        )
        assertEquals(60_000L, aggregate(listOf(dayRecord), day).totalDurationMs)
    }

    @Test
    fun dstUsesInstantIntersectionWithoutDoubleCounting() {
        val ny = ZoneId.of("America/New_York")
        val day = dayPeriod(LocalDate.of(2026, 3, 8), ny)
        val start = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, ny).toInstant()
        val end = ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny).toInstant()
        val record = StudyRecord(
            noteText = "DST",
            startedAtEpochMs = start.toEpochMilli(),
            endedAtEpochMs = end.toEpochMilli(),
        )

        assertEquals(60 * 60 * 1_000L, aggregate(listOf(record), day).totalDurationMs)
    }

    @Test
    fun notesGroupByExactTextAndSortByDurationThenText() {
        val period = dayPeriod(LocalDate.of(2026, 8, 26), zone)
        val records = listOf(
            record("数学", "2026-08-26T09:00:00Z", "2026-08-26T09:30:00Z"),
            record("数学", "2026-08-26T10:00:00Z", "2026-08-26T10:30:00Z"),
            record("数学题", "2026-08-26T11:00:00Z", "2026-08-26T12:00:00Z"),
            record("MATH", "2026-08-26T13:00:00Z", "2026-08-26T14:00:00Z"),
            record("b", "2026-08-26T15:00:00Z", "2026-08-26T16:00:00Z"),
            record("a", "2026-08-26T17:00:00Z", "2026-08-26T18:00:00Z"),
            record("", "2026-08-26T19:00:00Z", "2026-08-26T19:30:00Z"),
        )

        val report = aggregate(records, period)

        assertTrue(report.hasData)
        assertEquals(listOf("MATH", "a", "b", "数学", "数学题", ""), report.groups.map { it.noteText })
        assertEquals(2, report.groups.single { it.noteText == "数学" }.recordCount)
        assertEquals("未备注", report.groups.single { it.noteText.isEmpty() }.displayNote)
        assertEquals(6, report.groups.size)
    }

    @Test
    fun noDataMeansNoPositiveIntersectionAndEmptyGroups() {
        val period = dayPeriod(LocalDate.of(2026, 8, 26), zone)
        val outside = record("其他日", "2026-08-27T00:00:00Z", "2026-08-27T00:01:00Z")

        val report = aggregate(listOf(outside), period)

        assertFalse(report.hasData)
        assertEquals(0L, report.totalDurationMs)
        assertTrue(report.groups.isEmpty())
        assertFalse(todayReport(listOf(outside), instant("2026-08-26T12:00:00Z"), zone).hasData)
    }

    @Test
    fun todayTrendUsesTheCurrentNaturalWeekAndHighlightsToday() {
        val now = instant("2026-08-28T12:00:00Z")
        val records = listOf(
            record("周一", "2026-08-24T09:00:00Z", "2026-08-24T09:10:00Z"),
            record("周五", "2026-08-28T09:00:00Z", "2026-08-28T09:30:00Z"),
        )
        val report = todayReport(records, now, zone)

        val trend = buildTrend(records, report, now, zone)

        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), trend.map { it.label })
        assertEquals(10 * 60_000L, trend[0].durationMs)
        assertEquals(30 * 60_000L, trend[4].durationMs)
        assertTrue(trend[4].emphasized)
        assertEquals(1, trend.count { it.emphasized })
    }

    @Test
    fun recordDetailsUsePeriodOverlapAndNewestFirst() {
        val period = dayPeriod(LocalDate.of(2026, 8, 26), zone)
        val records = listOf(
            record("Java", "2026-08-25T23:50:00Z", "2026-08-26T00:10:00Z").copy(id = 1L),
            record("Java", "2026-08-26T09:00:00Z", "2026-08-26T09:30:00Z").copy(id = 2L),
            record("其他日", "2026-08-27T09:00:00Z", "2026-08-27T10:00:00Z").copy(id = 3L),
        )
        val report = aggregate(records, period)

        val details = recordsForReport(records, report)

        assertEquals(listOf(2L, 1L), details.map { it.id })
        assertEquals(30 * 60_000L, details[0].durationInPeriodMs)
        assertEquals(10 * 60_000L, details[1].durationInPeriodMs)
    }

    private fun record(note: String, start: String, end: String): StudyRecord {
        val started = instant(start)
        val ended = instant(end)
        return StudyRecord(
            noteText = note,
            startedAtEpochMs = started.toEpochMilli(),
            endedAtEpochMs = ended.toEpochMilli(),
        )
    }

    private fun dayPeriod(date: LocalDate, zone: ZoneId): ReportPeriod {
        val next = date.plusDays(1)
        return ReportPeriod(
            kind = PeriodKind.DAY,
            label = "测试日（$date）",
            startInclusive = date.atStartOfDay(zone).toInstant(),
            endExclusive = next.atStartOfDay(zone).toInstant(),
            startDate = date,
            endDateExclusive = next,
        )
    }

    private fun instant(value: String): Instant = Instant.parse(value)
}

private class FakeStudyTimerStore : StudyTimerStore {
    private var active: ActiveSession? = null
    private val records = mutableListOf<StudyRecord>()
    private var nextId = 1L

    override suspend fun beginIfIdle(session: ActiveSession): ActiveSession? {
        if (active != null) return null
        active = session
        return session
    }

    override suspend fun observeActive(): ActiveSession? = active

    override suspend fun finishActive(nowEpochMs: Long): StudyRecord? {
        val session = active ?: return null
        require(nowEpochMs >= session.startedAtEpochMs)
        val record = StudyRecord(
            id = nextId++,
            noteText = session.noteText,
            startedAtEpochMs = session.startedAtEpochMs,
            endedAtEpochMs = nowEpochMs,
        )
        records += record
        active = null
        return record
    }

    override suspend fun observeRecords(): List<StudyRecord> = records.toList()

    override suspend fun deleteRecord(recordId: Long): Boolean =
        records.removeAll { it.id == recordId }
}
