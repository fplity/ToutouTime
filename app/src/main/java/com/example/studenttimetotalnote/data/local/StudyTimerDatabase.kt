package com.example.studenttimetotalnote.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.studenttimetotalnote.data.StudyTimerStore
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.StudyRecord

@Entity(tableName = "study_records")
data class StudyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteText: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
)

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = ActiveSession.SINGLETON_ID,
    val noteText: String,
    val startedAtEpochMs: Long,
)

@Dao
abstract class StudyTimerDao {
    @Query("SELECT * FROM active_session WHERE id = 1 LIMIT 1")
    abstract suspend fun getActive(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertActive(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session WHERE id = 1")
    protected abstract suspend fun clearActive()

    @Insert
    protected abstract suspend fun insertRecord(record: StudyRecordEntity): Long

    @Query("SELECT * FROM study_records ORDER BY id ASC")
    abstract suspend fun getRecords(): List<StudyRecordEntity>

    @Query("DELETE FROM study_records WHERE id = :recordId")
    abstract suspend fun deleteRecord(recordId: Long): Int

    @Transaction
    open suspend fun beginIfIdle(session: ActiveSessionEntity): Boolean {
        if (getActive() != null) return false
        insertActive(session)
        return true
    }

    @Transaction
    open suspend fun finishActive(nowEpochMs: Long): StudyRecordEntity? {
        val active = getActive() ?: return null
        require(nowEpochMs >= active.startedAtEpochMs) { "Finish time cannot precede start time" }
        val record = StudyRecordEntity(
            noteText = active.noteText,
            startedAtEpochMs = active.startedAtEpochMs,
            endedAtEpochMs = nowEpochMs,
            durationMs = nowEpochMs - active.startedAtEpochMs,
        )
        val id = insertRecord(record)
        clearActive()
        return record.copy(id = id)
    }
}

@Database(
    entities = [StudyRecordEntity::class, ActiveSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class StudyTimerDatabase : RoomDatabase() {
    abstract fun studyTimerDao(): StudyTimerDao
}

class RoomStudyTimerStore(
    private val database: StudyTimerDatabase,
) : StudyTimerStore {
    private val dao: StudyTimerDao = database.studyTimerDao()

    override suspend fun beginIfIdle(session: ActiveSession): ActiveSession? =
        if (dao.beginIfIdle(session.toEntity())) session else null

    override suspend fun observeActive(): ActiveSession? = dao.getActive()?.toDomain()

    override suspend fun finishActive(nowEpochMs: Long): StudyRecord? =
        dao.finishActive(nowEpochMs)?.toDomain()

    override suspend fun observeRecords(): List<StudyRecord> = dao.getRecords().map { it.toDomain() }

    override suspend fun deleteRecord(recordId: Long): Boolean =
        dao.deleteRecord(recordId) == 1
}

private fun ActiveSession.toEntity() = ActiveSessionEntity(
    id = id,
    noteText = noteText,
    startedAtEpochMs = startedAtEpochMs,
)

private fun ActiveSessionEntity.toDomain() = ActiveSession(
    id = id,
    noteText = noteText,
    startedAtEpochMs = startedAtEpochMs,
)

private fun StudyRecordEntity.toDomain() = StudyRecord(
    id = id,
    noteText = noteText,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    durationMs = durationMs,
)
