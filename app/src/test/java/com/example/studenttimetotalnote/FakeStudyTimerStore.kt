package com.example.studenttimetotalnote

import com.example.studenttimetotalnote.data.StudyTimerStore
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.StudyRecord
import com.example.studenttimetotalnote.domain.model.StudySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FakeStudyTimerStore(initial: StudySnapshot = StudySnapshot()) : StudyTimerStore {
    private val data = MutableStateFlow(initial)
    private val mutex = Mutex()
    private var nextId = (initial.records.maxOfOrNull { it.id } ?: 0L) + 1
    var readCount = 0
    var beginCount = 0
    var finishCount = 0
    var failReads = false
    var failDelete = false
    var subscribers = 0
    var deleteGate: CompletableDeferred<Unit>? = null

    override suspend fun readSnapshot(): StudySnapshot {
        readCount++
        check(!failReads)
        return data.value
    }

    override fun observeSnapshots(): Flow<StudySnapshot> = flow {
        check(!failReads)
        subscribers++
        try { emitAll(data) } finally { subscribers-- }
    }

    override suspend fun beginIfIdle(session: ActiveSession): ActiveSession? = mutex.withLock {
        beginCount++
        if (data.value.activeSession != null) return@withLock null
        data.value = data.value.copy(activeSession = session)
        session
    }

    override suspend fun finishActive(nowEpochMs: Long): StudyRecord? = mutex.withLock {
        finishCount++
        val active = data.value.activeSession ?: return@withLock null
        require(nowEpochMs >= active.startedAtEpochMs)
        val record = StudyRecord(
            id = nextId++, noteText = active.noteText,
            startedAtEpochMs = active.startedAtEpochMs, endedAtEpochMs = nowEpochMs,
        )
        data.value = StudySnapshot(records = data.value.records + record)
        record
    }

    override suspend fun deleteRecord(recordId: Long): Boolean {
        deleteGate?.await()
        check(!failDelete)
        return mutex.withLock {
            val remaining = data.value.records.filterNot { it.id == recordId }
            val changed = remaining.size != data.value.records.size
            data.value = data.value.copy(records = remaining)
            changed
        }
    }
}
