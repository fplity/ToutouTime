package com.example.studenttimetotalnote.domain

import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.model.StudyRecord
import java.time.Instant
import java.time.ZoneId

interface StudyTimerRepository {
    suspend fun beginSession(noteText: String, now: Instant): ActiveSession

    suspend fun observeActiveSession(): ActiveSession?

    /** Returns the saved record, or null when the active session was already finished. */
    suspend fun finishSession(now: Instant): StudyRecord?

    suspend fun observeRecords(): List<StudyRecord>

    /** Permanently removes one completed session so it no longer contributes to reports. */
    suspend fun deleteRecord(recordId: Long): Boolean

    suspend fun report(kind: PeriodKind, now: Instant, zone: ZoneId): PeriodReport

    suspend fun yearReport(year: Int, now: Instant, zone: ZoneId): PeriodReport

    suspend fun todayReport(now: Instant, zone: ZoneId): PeriodReport
}
