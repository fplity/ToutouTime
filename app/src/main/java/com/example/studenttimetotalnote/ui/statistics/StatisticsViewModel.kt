package com.example.studenttimetotalnote.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studenttimetotalnote.domain.StatisticsRecordItem
import com.example.studenttimetotalnote.domain.TrendPoint
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.canShiftReportDate
import com.example.studenttimetotalnote.domain.defaultReportDate
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.shiftReportDate
import com.example.studenttimetotalnote.domain.statisticsReport
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatisticsUiState(
    val today: LocalDate,
    val selectedDate: LocalDate = today,
    val selectedPeriod: PeriodKind = PeriodKind.DAY,
    val report: PeriodReport? = null,
    val trend: List<TrendPoint> = emptyList(),
    val records: List<StatisticsRecordItem> = emptyList(),
    val openedNoteText: String? = null,
    val pendingDelete: StatisticsRecordItem? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val loadError: String? = null,
) {
    val openedRecords: List<StatisticsRecordItem>
        get() = openedNoteText?.let { note -> records.filter { it.noteText == note } }.orEmpty()
    val canSelectPreviousPeriod: Boolean
        get() = canShiftReportDate(selectedPeriod, selectedDate, -1)
    val canSelectNextPeriod: Boolean
        get() = canShiftReportDate(selectedPeriod, selectedDate, 1)
}

private data class Selection(val kind: PeriodKind, val date: LocalDate, val followDefault: Boolean)

/** A single cancellable stream owns reports; the saved selection survives recreation. */
class StatisticsViewModel(
    private val repository: StudyTimerRepository,
    initialPeriod: PeriodKind = PeriodKind.DAY,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = clock.zone,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val reportDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val initialKind = savedState.get<String>("period")?.let {
        runCatching { PeriodKind.valueOf(it) }.getOrNull()
    } ?: initialPeriod
    private val initialDate = savedState.get<String>("date")?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }?.takeIf { it.year in 1..9998 }
        ?: defaultReportDate(initialKind, clock.instant(), zone)
    private val selection = MutableStateFlow(
        Selection(initialKind, initialDate, savedState["followDefault"] ?: true),
    )
    private val _uiState = MutableStateFlow(
        StatisticsUiState(
            today = clock.instant().atZone(zone).toLocalDate(),
            selectedDate = initialDate,
            selectedPeriod = initialKind,
        ),
    )
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        refresh()
    }

    fun selectPeriod(kind: PeriodKind) {
        select(Selection(kind, defaultReportDate(kind, clock.instant(), zone), true))
    }

    fun selectPreviousPeriod() = shift(-1)
    fun selectNextPeriod() = shift(1)

    private fun shift(steps: Int) {
        val current = uiState.value
        if (!canShiftReportDate(current.selectedPeriod, current.selectedDate, steps)) return
        select(Selection(
            current.selectedPeriod,
            shiftReportDate(current.selectedPeriod, current.selectedDate, steps),
            false,
        ))
    }

    private fun select(target: Selection) {
        if (selection.value == target && uiState.value.loadError == null) return
        savedState["period"] = target.kind.name
        savedState["date"] = target.date.toString()
        savedState["followDefault"] = target.followDefault
        _uiState.update {
            it.copy(
                selectedPeriod = target.kind,
                selectedDate = target.date,
                report = null,
                trend = emptyList(),
                records = emptyList(),
                openedNoteText = null,
                pendingDelete = null,
                isLoading = true,
                loadError = null,
                deleteError = null,
            )
        }
        selection.value = target
        if (observationJob?.isActive != true) refresh()
    }

    fun refresh() {
        observationJob?.cancel()
        _uiState.update { it.copy(loadError = null, isLoading = true) }
        observationJob = viewModelScope.launch {
            val dates = flow {
                while (true) {
                    emit(clock.instant().atZone(zone).toLocalDate())
                    delay(1_000L)
                }
            }.distinctUntilChanged()
            try {
                combine(repository.observeSnapshots(), selection, dates) { snapshot, selected, date ->
                    Triple(snapshot, selected, date)
                }.collectLatest { (snapshot, selected, today) ->
                    val now = clock.instant()
                    val date = if (selected.followDefault) {
                        defaultReportDate(selected.kind, now, zone)
                    } else selected.date
                    val computed = withContext(reportDispatcher) {
                        statisticsReport(snapshot.records, selected.kind, date, now, zone)
                    }
                    if (selection.value != selected) return@collectLatest
                    _uiState.update { state ->
                        state.copy(
                            today = today,
                            selectedDate = date,
                            selectedPeriod = selected.kind,
                            report = computed.report,
                            trend = computed.trend,
                            records = computed.records,
                            openedNoteText = state.openedNoteText?.takeIf { note ->
                                computed.records.any { it.noteText == note }
                            },
                            pendingDelete = state.pendingDelete?.let { pending ->
                                computed.records.firstOrNull { it.id == pending.id }
                            },
                            isLoading = false,
                            loadError = null,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, loadError = "读取失败，点击重试") }
            }
        }
    }

    fun onLifecyclePaused() {
        observationJob?.cancel()
        observationJob = null
    }

    fun openRecordsForNote(noteText: String) {
        if (uiState.value.records.none { it.noteText == noteText } || uiState.value.isDeleting) return
        _uiState.update { it.copy(openedNoteText = noteText, pendingDelete = null, deleteError = null) }
    }

    fun closeRecordDetails() {
        if (!uiState.value.isDeleting) {
            _uiState.update { it.copy(openedNoteText = null, pendingDelete = null, deleteError = null) }
        }
    }

    fun requestDelete(recordId: Long) {
        if (uiState.value.isDeleting) return
        val record = uiState.value.records.firstOrNull { it.id == recordId } ?: return
        _uiState.update { it.copy(pendingDelete = record, deleteError = null) }
    }

    fun cancelDelete() {
        if (!uiState.value.isDeleting) {
            _uiState.update { it.copy(pendingDelete = null, deleteError = null) }
        }
    }

    fun confirmDelete() {
        val record = uiState.value.pendingDelete ?: return
        if (uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                // Already absent is an idempotent success: it cannot contribute to reports.
                repository.deleteRecord(record.id)
                _uiState.update { it.copy(pendingDelete = null, deleteError = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    if (it.pendingDelete?.id == record.id) it.copy(deleteError = "删除失败，请重试") else it
                }
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
