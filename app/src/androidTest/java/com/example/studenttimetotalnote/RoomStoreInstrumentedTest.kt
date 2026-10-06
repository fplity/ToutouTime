package com.example.studenttimetotalnote

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.studenttimetotalnote.data.local.RoomStudyTimerStore
import com.example.studenttimetotalnote.data.local.StudyTimerDatabase
import com.example.studenttimetotalnote.domain.DefaultStudyTimerRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate in-memory database; never touches the installed app's study records. */
@RunWith(AndroidJUnit4::class)
class RoomStoreInstrumentedTest {
    private lateinit var database: StudyTimerDatabase
    private lateinit var repository: DefaultStudyTimerRepository
    private val start = Instant.parse("2026-10-06T12:00:00Z")

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StudyTimerDatabase::class.java,
        ).build()
        repository = DefaultStudyTimerRepository(RoomStudyTimerStore(database))
    }

    @After fun teardown() { database.close() }

    @Test fun finishCommitsRecordAndClearsActiveInOneSnapshot() = runBlocking {
        repository.beginSession("Java", start)
        assertNotNull(repository.readSnapshot().activeSession)
        val record = repository.finishSession(start.plusSeconds(120))
        val committed = repository.readSnapshot()
        assertNull(committed.activeSession)
        assertEquals(record, committed.records.single())
        assertEquals(120_000L, committed.records.single().durationMs)
        assertNull(repository.finishSession(start.plusSeconds(180)))
    }

    @Test fun deletingOneRecordKeepsOtherRecordsAndTheActiveSession() = runBlocking {
        repository.beginSession("Java", start)
        val first = repository.finishSession(start.plusSeconds(120))!!
        repository.beginSession("数学", start.plusSeconds(180))
        val second = repository.finishSession(start.plusSeconds(240))!!
        val active = repository.beginSession("阅读", start.plusSeconds(300))
        assertTrue(repository.deleteRecord(first.id))
        val snapshot = repository.readSnapshot()
        assertEquals(active, snapshot.activeSession)
        assertEquals(listOf(second), snapshot.records)
    }
}
