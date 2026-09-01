package com.example.studenttimetotalnote.data

import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.StudyRecord

/** Persistence boundary; finishActive implementations must commit insert+clear atomically. */
interface StudyTimerStore {
    suspend fun beginIfIdle(session: ActiveSession): ActiveSession?

    suspend fun observeActive(): ActiveSession?

    suspend fun finishActive(nowEpochMs: Long): StudyRecord?

    suspend fun observeRecords(): List<StudyRecord>

    /** Deletes exactly one completed record. Returns false when the ID no longer exists. */
    suspend fun deleteRecord(recordId: Long): Boolean
}
