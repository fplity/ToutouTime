package com.example.studenttimetotalnote.domain

import com.example.studenttimetotalnote.data.StudyTimerStore
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.model.StudyRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DefaultStudyTimerRepository(
    private val store: StudyTimerStore,
) : StudyTimerRepository {
    override suspend fun beginSession(noteText: String, now: Instant): ActiveSession {
        val session = ActiveSession(
            noteText = noteText.trim(),
            startedAtEpochMs = now.toEpochMilli(),
        )
        return store.beginIfIdle(session)
            ?: throw IllegalStateException("A study session is already active")
    }

    override suspend fun observeActiveSession(): ActiveSession? = store.observeActive()

    override suspend fun finishSession(now: Instant): StudyRecord? =
        store.finishActive(now.toEpochMilli())

    override suspend fun observeRecords(): List<StudyRecord> = store.observeRecords()

    override suspend fun deleteRecord(recordId: Long): Boolean =
        store.deleteRecord(recordId)

    override suspend fun report(kind: PeriodKind, now: Instant, zone: ZoneId): PeriodReport =
        aggregate(observeRecords(), resolveReportPeriod(kind, now, zone))

    override suspend fun report(
        kind: PeriodKind,
        selectedDate: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): PeriodReport = aggregate(
        observeRecords(),
        resolveReportPeriod(kind, selectedDate, now, zone),
    )

    override suspend fun yearReport(year: Int, now: Instant, zone: ZoneId): PeriodReport =
        aggregate(observeRecords(), resolveYearReportPeriod(year, now, zone))

    override suspend fun todayReport(now: Instant, zone: ZoneId): PeriodReport =
        todayReport(observeRecords(), now, zone)
}
