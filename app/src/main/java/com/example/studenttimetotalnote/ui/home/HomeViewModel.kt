package com.example.studenttimetotalnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.ActiveSession
import com.example.studenttimetotalnote.domain.model.StudySnapshot
import com.example.studenttimetotalnote.domain.todayReport
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val activeSession: ActiveSession? = null,
    val elapsedMs: Long = 0L,
    val todayTotalMs: Long = 0L,
    val hasTodaySummary: Boolean = false,
    val noteDialogVisible: Boolean = false,
    val noteDraft: String = "",
    val isBusy: Boolean = true,
    val feedbackMessage: String? = null,
) {
    val isRunning: Boolean get() = activeSession != null
}

/** Observes committed snapshots; clock ticks never query the database. */
class HomeViewModel(
    private val repository: StudyTimerRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var snapshot = StudySnapshot()
    private var summaryDate: LocalDate? = null
    private var summaryRecords = snapshot.records
    private var operationBusy = false
    private var loaded = false
    private var tickerJob: Job? = null
    private var observationJob: Job? = null

    init {
        observe()
        onLifecycleResumed()
    }

    private fun observe() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            try {
                repository.observeSnapshots().collect { data ->
                    snapshot = data
                    loaded = true
                    render()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(isBusy = operationBusy || !loaded,
                        feedbackMessage = "读取失败，请重新打开应用重试")
                }
            }
        }
    }

    fun onStartRequested() {
        if (uiState.value.isRunning || uiState.value.isBusy) return
        _uiState.update {
            it.copy(noteDialogVisible = true, noteDraft = "", feedbackMessage = null)
        }
    }

    fun onNoteChanged(note: String) {
        if (uiState.value.noteDialogVisible && !uiState.value.isBusy) {
            _uiState.update { it.copy(noteDraft = note) }
        }
    }

    fun onNoteDialogCancelled() {
        if (uiState.value.isBusy) return
        _uiState.update { it.copy(noteDialogVisible = false, noteDraft = "") }
    }

    fun onNoteDialogConfirmed() {
        val state = uiState.value
        if (!state.noteDialogVisible || state.isBusy || state.isRunning) return
        operationBusy = true
        _uiState.update { it.copy(noteDialogVisible = false, isBusy = true) }
        viewModelScope.launch {
            try {
                val active = repository.beginSession(state.noteDraft, clock.instant())
                snapshot = snapshot.copy(activeSession = active)
                _uiState.update { it.copy(noteDraft = "") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(noteDialogVisible = true, feedbackMessage = "暂时无法开始，请重试")
                }
            } finally {
                operationBusy = false
                render()
            }
        }
    }

    fun onFinishRequested() {
        if (!uiState.value.isRunning || uiState.value.isBusy) return
        operationBusy = true
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val record = repository.finishSession(clock.instant())
                snapshot = snapshot.copy(
                    activeSession = null,
                    records = if (record != null && snapshot.records.none { it.id == record.id }) {
                        snapshot.records + record
                    } else {
                        snapshot.records
                    },
                )
                _uiState.update { it.copy(feedbackMessage = "已保存") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(feedbackMessage = "保存失败，请重试") }
            } finally {
                operationBusy = false
                render()
            }
        }
    }

    fun onLifecycleResumed() {
        if (observationJob?.isActive != true) observe()
        refreshHome()
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                render()
                delay(1_000L)
            }
        }
    }

    fun onLifecyclePaused() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun onFeedbackConsumed() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun refreshHome() = render()

    private fun render() {
        val now = clock.instant()
        val date = now.atZone(clock.zone).toLocalDate()
        val summaryChanged = date != summaryDate || snapshot.records != summaryRecords
        val report = if (summaryChanged) todayReport(snapshot.records, now, clock.zone) else null
        summaryDate = date
        summaryRecords = snapshot.records
        _uiState.update { state ->
            state.copy(
                activeSession = snapshot.activeSession,
                elapsedMs = snapshot.activeSession?.let {
                    (now.toEpochMilli() - it.startedAtEpochMs).coerceAtLeast(0L)
                } ?: 0L,
                todayTotalMs = report?.totalDurationMs ?: state.todayTotalMs,
                hasTodaySummary = report?.hasData ?: state.hasTodaySummary,
                isBusy = operationBusy || !loaded,
            )
        }
    }
}
