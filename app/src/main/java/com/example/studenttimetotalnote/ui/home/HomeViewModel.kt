package com.example.studenttimetotalnote.ui.home

import androidx.lifecycle.ViewModel
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.ActiveSession
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
) {
    val isRunning: Boolean
        get() = activeSession != null
}

/** Owns the home state so an active session can be recovered after recreation. */
class HomeViewModel(
    private val repository: StudyTimerRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var tickerToken = 0

    init {
        refreshHome()
    }

    fun onStartRequested() {
        if (_uiState.value.isRunning || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                noteDialogVisible = true,
                noteDraft = "",
                feedbackMessage = null,
            )
        }
    }

    fun onNoteChanged(note: String) {
        if (_uiState.value.noteDialogVisible && !_uiState.value.isBusy) {
            _uiState.update { it.copy(noteDraft = note) }
        }
    }

    fun onNoteDialogCancelled() {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                noteDialogVisible = false,
                noteDraft = "",
            )
        }
    }

    fun onNoteDialogConfirmed() {
        val state = _uiState.value
        if (!state.noteDialogVisible || state.isBusy || state.isRunning) return

        val note = state.noteDraft
        _uiState.update { it.copy(noteDialogVisible = false, isBusy = true) }
        scope.launch {
            try {
                val active = repository.beginSession(note, now())
                applyActive(active, now())
                _uiState.update { it.copy(isBusy = false, noteDraft = "") }
                startTicker()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        noteDialogVisible = true,
                        isBusy = false,
                        feedbackMessage = "暂时无法开始，请重试",
                    )
                }
            }
        }
    }

    fun onFinishRequested() {
        val state = _uiState.value
        if (!state.isRunning || state.isBusy) return

        stopTicker()
        _uiState.update { it.copy(isBusy = true) }
        scope.launch {
            try {
                repository.finishSession(now())
                val finishedAt = now()
                applyActive(null, finishedAt)
                refreshToday(finishedAt)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        feedbackMessage = "已保存",
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                refreshHome()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        feedbackMessage = "保存失败，请重试",
                    )
                }
            }
        }
    }

    /** Called by the host when the screen returns to the foreground. */
    fun onLifecycleResumed() {
        refreshHome()
    }

    fun onFeedbackConsumed() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun refreshHome() {
        scope.launch {
            val current = now()
            val active = repository.observeActiveSession()
            applyActive(active, current)
            refreshToday(current)
            if (active == null) stopTicker() else startTicker()
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return

        val token = ++tickerToken
        tickerJob = scope.launch {
            while (isActive && token == tickerToken) {
                val active = repository.observeActiveSession()
                if (active == null) {
                    applyActive(null, now())
                    break
                }
                applyActive(active, now())
                delay(TIMER_REFRESH_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerToken += 1
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun applyActive(active: ActiveSession?, current: Instant) {
        val elapsed = active?.let {
            (current.toEpochMilli() - it.startedAtEpochMs).coerceAtLeast(0L)
        } ?: 0L
        _uiState.update {
            it.copy(
                activeSession = active,
                elapsedMs = elapsed,
            )
        }
    }

    private suspend fun refreshToday(current: Instant) {
        val report = repository.todayReport(current, clock.zone)
        _uiState.update {
            it.copy(
                todayTotalMs = report.totalDurationMs,
                hasTodaySummary = report.hasData,
            )
        }
    }

    private fun now(): Instant = Instant.now(clock)

    override fun onCleared() {
        stopTicker()
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val TIMER_REFRESH_MS = 1_000L
    }
}
