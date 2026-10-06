package com.example.studenttimetotalnote.data

import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.StudyRecord
import com.example.studenttimetotalnote.domain.model.StudySnapshot
import kotlinx.coroutines.flow.Flow

/** Persistence boundary; finishActive implementations must commit insert+clear atomically. */
interface StudyTimerStore {
    suspend fun readSnapshot(): StudySnapshot

    fun observeSnapshots(): Flow<StudySnapshot>

    suspend fun beginIfIdle(session: ActiveSession): ActiveSession?

    suspend fun finishActive(nowEpochMs: Long): StudyRecord?

    /** Deletes exactly one completed record. Returns false when the ID no longer exists. */
    suspend fun deleteRecord(recordId: Long): Boolean
}
