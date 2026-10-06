package com.example.studenttimetotalnote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.example.studenttimetotalnote.domain.DefaultStudyTimerRepository
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.StudyRecord
import com.example.studenttimetotalnote.domain.model.StudySnapshot
import com.example.studenttimetotalnote.domain.statisticsReport
import com.example.studenttimetotalnote.ui.home.HomeViewModel
import com.example.studenttimetotalnote.ui.statistics.StatisticsViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val zone = ZoneId.of("UTC")
    private val clock = MutableClock(Instant.parse("2026-10-06T12:00:00Z"), zone)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    private fun checkViewModels(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally {
            models.clear()
            runCurrent()
        }
    }
    @After fun teardown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> retain(model: T): T = model.also {
        models.put("model-${System.identityHashCode(it)}", it)
    }

    private fun home(store: FakeStudyTimerStore) = retain(
        HomeViewModel(DefaultStudyTimerRepository(store), clock),
    )

    private fun stats(
        store: FakeStudyTimerStore,
        kind: PeriodKind = PeriodKind.ALL,
        state: SavedStateHandle = SavedStateHandle(),
    ) = retain(StatisticsViewModel(
        repository = DefaultStudyTimerRepository(store), initialPeriod = kind,
        clock = clock, savedState = state, reportDispatcher = dispatcher,
    ))

    private fun record(id: Long = 1L, note: String = "Java", minutes: Long = 30L) =
        StudyRecord(id, note, clock.instant().minusSeconds(minutes * 60).toEpochMilli(),
            clock.instant().toEpochMilli())

    @Test fun homeRecoversRunningSessionWithoutDatabasePolling() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(
            activeSession = ActiveSession(noteText = "Java",
                startedAtEpochMs = clock.instant().minusSeconds(60).toEpochMilli()),
        ))
        val vm = home(store)
        runCurrent()
        assertEquals(60_000L, vm.uiState.value.elapsedMs)
        val reads = store.readCount
        clock.current = clock.current.plusSeconds(5)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(65_000L, vm.uiState.value.elapsedMs)
        assertEquals(reads, store.readCount)
        assertTrue(vm.uiState.value.isRunning)
    }

    @Test fun repeatedStartAndFinishClicksProduceExactlyOneRecord() = checkViewModels {
        val store = FakeStudyTimerStore()
        val vm = home(store)
        runCurrent()
        vm.onStartRequested()
        vm.onNoteChanged(" Java ")
        vm.onNoteDialogConfirmed(); vm.onNoteDialogConfirmed()
        runCurrent()
        assertEquals(1, store.beginCount)
        clock.current = clock.current.plusSeconds(120)
        vm.onFinishRequested(); vm.onFinishRequested()
        runCurrent()
        assertEquals(1, store.finishCount)
        assertFalse(vm.uiState.value.isRunning)
        assertEquals(120_000L, vm.uiState.value.todayTotalMs)
        assertEquals("Java", store.readSnapshot().records.single().noteText)
    }

    @Test fun lifecyclePauseStopsTickingAndResumeRestoresElapsedTime() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(
            activeSession = ActiveSession(noteText = "", startedAtEpochMs = clock.current.toEpochMilli()),
        ))
        val vm = home(store)
        runCurrent()
        vm.onLifecyclePaused()
        clock.current = clock.current.plusSeconds(10)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0L, vm.uiState.value.elapsedMs)
        vm.onLifecycleResumed(); runCurrent()
        assertEquals(10_000L, vm.uiState.value.elapsedMs)
    }

    @Test fun invalidFinishPreservesTheActiveRecordForRetry() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(
            activeSession = ActiveSession(noteText = "Java", startedAtEpochMs = clock.current.toEpochMilli()),
        ))
        val vm = home(store)
        runCurrent()
        clock.current = clock.current.minusSeconds(1)
        vm.onFinishRequested(); runCurrent()
        assertTrue(vm.uiState.value.isRunning)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals("保存失败，请重试", vm.uiState.value.feedbackMessage)
        assertTrue(store.readSnapshot().records.isEmpty())
    }

    @Test fun deleteAutomaticallyUpdatesHomeAndAllTimeStatistics() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(records = listOf(record())))
        val home = home(store)
        val statistics = stats(store)
        runCurrent()
        assertEquals(1_800_000L, home.uiState.value.todayTotalMs)
        statistics.openRecordsForNote("Java")
        statistics.requestDelete(1)
        statistics.confirmDelete()
        runCurrent()
        assertEquals(0L, home.uiState.value.todayTotalMs)
        assertEquals(0L, statistics.uiState.value.report!!.totalDurationMs)
        assertTrue(statistics.uiState.value.records.isEmpty())
        assertNull(statistics.uiState.value.openedNoteText)
        assertFalse(statistics.uiState.value.isDeleting)
    }

    @Test fun rapidSwitchesPublishOnlyTheFinalSelection() = checkViewModels {
        val vm = stats(FakeStudyTimerStore())
        runCurrent()
        vm.selectPeriod(PeriodKind.MONTH)
        vm.selectPeriod(PeriodKind.YEAR)
        vm.selectPeriod(PeriodKind.DAY)
        vm.selectPreviousPeriod()
        runCurrent()
        assertEquals(PeriodKind.DAY, vm.uiState.value.report!!.kind)
        assertEquals(LocalDate.of(2026, 10, 5), vm.uiState.value.report!!.period.startDate)
    }

    @Test fun selectedYearSurvivesViewModelRecreation() = checkViewModels {
        val saved = SavedStateHandle()
        val store = FakeStudyTimerStore()
        val first = stats(store, PeriodKind.YEAR, saved)
        runCurrent()
        first.selectPreviousPeriod(); first.selectPreviousPeriod()
        runCurrent()
        val restored = stats(store, PeriodKind.DAY, saved)
        runCurrent()
        assertEquals(PeriodKind.YEAR, restored.uiState.value.selectedPeriod)
        assertEquals(2024, restored.uiState.value.selectedDate.year)
    }

    @Test fun midnightMovesDefaultTodayButKeepsHistoricalSelection() = checkViewModels {
        clock.current = Instant.parse("2026-10-06T23:59:59Z")
        val store = FakeStudyTimerStore()
        val today = stats(store, PeriodKind.DAY)
        val history = stats(store, PeriodKind.DAY)
        runCurrent()
        history.selectPreviousPeriod(); runCurrent()
        clock.current = clock.current.plusSeconds(2)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(LocalDate.of(2026, 10, 7), today.uiState.value.selectedDate)
        assertEquals(LocalDate.of(2026, 10, 5), history.uiState.value.selectedDate)
    }

    @Test fun failedDeleteAfterSwitchDoesNotLeakAnErrorIntoNewPeriod() = checkViewModels {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStudyTimerStore(StudySnapshot(records = listOf(record())))
        store.deleteGate = gate
        store.failDelete = true
        val vm = stats(store)
        runCurrent()
        vm.requestDelete(1); vm.confirmDelete(); runCurrent()
        vm.selectPeriod(PeriodKind.YEAR); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(PeriodKind.YEAR, vm.uiState.value.report!!.kind)
        assertNull(vm.uiState.value.deleteError)
        assertFalse(vm.uiState.value.isDeleting)
        assertEquals(1, vm.uiState.value.records.size)
    }

    @Test fun readFailureCanBeRetriedWithoutLosingSelection() = checkViewModels {
        val store = FakeStudyTimerStore()
        store.failReads = true
        val vm = stats(store, PeriodKind.YEAR)
        runCurrent()
        assertNotNull(vm.uiState.value.loadError)
        store.failReads = false
        vm.refresh(); runCurrent()
        assertNull(vm.uiState.value.loadError)
        assertEquals(PeriodKind.YEAR, vm.uiState.value.report!!.kind)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun homeReadFailureBlocksWritesUntilItsStateIsRecovered() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(
            activeSession = ActiveSession(noteText = "Java",
                startedAtEpochMs = clock.current.minusSeconds(60).toEpochMilli()),
        ))
        store.failReads = true
        val vm = home(store)
        runCurrent()
        assertTrue(vm.uiState.value.isBusy)
        vm.onStartRequested()
        assertFalse(vm.uiState.value.noteDialogVisible)
        store.failReads = false
        vm.onLifecycleResumed(); runCurrent()
        assertFalse(vm.uiState.value.isBusy)
        assertTrue(vm.uiState.value.isRunning)
        assertEquals(60_000L, vm.uiState.value.elapsedMs)
    }

    @Test fun clearingViewModelsReleasesSnapshotSubscriptions() = checkViewModels {
        val store = FakeStudyTimerStore()
        home(store); stats(store)
        runCurrent()
        assertEquals(2, store.subscribers)
        models.clear(); runCurrent()
        assertEquals(0, store.subscribers)
    }

    @Test fun concurrentStartsCannotOverwriteAnExistingSession() = checkViewModels {
        val store = FakeStudyTimerStore()
        val repository = DefaultStudyTimerRepository(store)
        val successes = coroutineScope {
            (1..10).map { index -> async {
                runCatching { repository.beginSession("记录$index", clock.current) }.isSuccess
            } }.awaitAll()
        }
        assertEquals(1, successes.count { it })
        assertNotNull(store.readSnapshot().activeSession)
        assertTrue(store.readSnapshot().records.isEmpty())
    }

    @Test fun reportTotalAndDetailsUseTheSameCrossDaySnapshot() {
        val crossing = StudyRecord(1, "Java",
            Instant.parse("2026-10-05T23:30:00Z").toEpochMilli(),
            Instant.parse("2026-10-06T00:30:00Z").toEpochMilli())
        val report = statisticsReport(listOf(crossing, record(2)),
            PeriodKind.DAY, LocalDate.of(2026, 10, 6), clock.current, zone)
        assertEquals(3_600_000L, report.report.totalDurationMs)
        assertEquals(report.report.totalDurationMs, report.records.sumOf { it.durationInPeriodMs })
    }

    @Test fun deletionCancellationReleasesBusyStateWithoutReportingFailure() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(records = listOf(record())))
        store.deleteGate = CompletableDeferred()
        val vm = stats(store)
        runCurrent()
        vm.requestDelete(1); vm.confirmDelete(); runCurrent()
        assertTrue(vm.uiState.value.isDeleting)
        models.clear(); runCurrent()
        assertFalse(vm.uiState.value.isDeleting)
        assertNull(vm.uiState.value.deleteError)
        assertEquals(1, store.readSnapshot().records.size)
    }

    @Test fun pausingStatisticsReleasesItsObserverAndResumingRefreshesData() = checkViewModels {
        val store = FakeStudyTimerStore(StudySnapshot(records = listOf(record())))
        val vm = stats(store)
        runCurrent()
        vm.onLifecyclePaused(); runCurrent()
        assertEquals(0, store.subscribers)
        store.deleteRecord(1)
        vm.refresh(); runCurrent()
        assertEquals(1, store.subscribers)
        assertEquals(0L, vm.uiState.value.report!!.totalDurationMs)
    }
}

private class MutableClock(var current: Instant, private val zoneId: ZoneId) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
}
