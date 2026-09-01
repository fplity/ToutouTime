package com.example.studenttimetotalnote.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.domain.model.PeriodReport
import com.example.studenttimetotalnote.domain.model.StudyRecord
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendPoint(
    val label: String,
    val durationMs: Long,
    val emphasized: Boolean = false,
)

data class StatisticsRecordItem(
    val id: Long,
    val noteText: String,
    val startedAtEpochMs: Long,
    val durationInPeriodMs: Long,
) {
    val displayNote: String
        get() = noteText.ifEmpty { "未备注" }
}

data class StatisticsUiState(
    val selectedPeriod: PeriodKind = PeriodKind.DAY,
    val report: PeriodReport? = null,
    val trend: List<TrendPoint> = emptyList(),
    val records: List<StatisticsRecordItem> = emptyList(),
    val openedNoteText: String? = null,
    val pendingDelete: StatisticsRecordItem? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
) {
    val openedRecords: List<StatisticsRecordItem>
        get() = openedNoteText?.let { note ->
            records.filter { it.noteText == note }
        }.orEmpty()
}

/** Loads reports and owns the deliberate, ID-scoped deletion flow. */
class StatisticsViewModel(
    private val repository: StudyTimerRepository,
    initialPeriod: PeriodKind = PeriodKind.DAY,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        StatisticsUiState(selectedPeriod = initialPeriod),
    )
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load(initialPeriod)
    }

    fun selectPeriod(kind: PeriodKind) {
        if (_uiState.value.selectedPeriod == kind && _uiState.value.report != null) return

        _uiState.update {
            it.copy(
                selectedPeriod = kind,
                report = null,
                trend = emptyList(),
                records = emptyList(),
                openedNoteText = null,
                pendingDelete = null,
                isLoading = true,
                isDeleting = false,
                deleteError = null,
            )
        }
        load(kind)
    }

    fun refresh() {
        val kind = _uiState.value.selectedPeriod
        _uiState.update {
            it.copy(
                pendingDelete = null,
                isLoading = true,
                isDeleting = false,
                deleteError = null,
            )
        }
        load(kind)
    }

    fun openRecordsForNote(noteText: String) {
        if (_uiState.value.records.none { it.noteText == noteText }) return
        _uiState.update {
            it.copy(
                openedNoteText = noteText,
                pendingDelete = null,
                deleteError = null,
            )
        }
    }

    fun closeRecordDetails() {
        if (_uiState.value.isDeleting) return
        _uiState.update {
            it.copy(
                openedNoteText = null,
                pendingDelete = null,
                deleteError = null,
            )
        }
    }

    fun requestDelete(recordId: Long) {
        if (_uiState.value.isDeleting) return
        val record = _uiState.value.records.firstOrNull { it.id == recordId } ?: return
        _uiState.update {
            it.copy(pendingDelete = record, deleteError = null)
        }
    }

    fun cancelDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(pendingDelete = null, deleteError = null) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val record = state.pendingDelete ?: return
        if (state.isDeleting) return

        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            runCatching {
                check(repository.deleteRecord(record.id)) { "Record no longer exists" }
                loadSnapshot(state.selectedPeriod)
            }.onSuccess { snapshot ->
                if (_uiState.value.selectedPeriod == state.selectedPeriod) {
                    val openedNote = state.openedNoteText?.takeIf { note ->
                        snapshot.records.any { it.noteText == note }
                    }
                    _uiState.update {
                        it.copy(
                            report = snapshot.report,
                            trend = snapshot.trend,
                            records = snapshot.records,
                            openedNoteText = openedNote,
                            pendingDelete = null,
                            isLoading = false,
                            isDeleting = false,
                            deleteError = null,
                        )
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = "删除失败，请重试",
                    )
                }
            }
        }
    }

    private fun load(kind: PeriodKind) {
        viewModelScope.launch {
            runCatching {
                loadSnapshot(kind)
            }.onSuccess { snapshot ->
                if (_uiState.value.selectedPeriod == kind) {
                    val openedNote = _uiState.value.openedNoteText?.takeIf { note ->
                        snapshot.records.any { it.noteText == note }
                    }
                    _uiState.update {
                        it.copy(
                            report = snapshot.report,
                            trend = snapshot.trend,
                            records = snapshot.records,
                            openedNoteText = openedNote,
                            pendingDelete = null,
                            isLoading = false,
                            isDeleting = false,
                            deleteError = null,
                        )
                    }
                }
            }.onFailure {
                if (_uiState.value.selectedPeriod == kind) {
                    _uiState.update {
                        it.copy(
                            report = null,
                            trend = emptyList(),
                            records = emptyList(),
                            openedNoteText = null,
                            pendingDelete = null,
                            isLoading = false,
                            isDeleting = false,
                            deleteError = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadSnapshot(kind: PeriodKind): StatisticsSnapshot {
        val now = clock.instant()
        val report = repository.report(kind, now, zone)
        val allRecords = repository.observeRecords()
        return StatisticsSnapshot(
            report = report,
            trend = buildTrend(allRecords, report, now, zone),
            records = recordsForReport(allRecords, report),
        )
    }
}

private data class StatisticsSnapshot(
    val report: PeriodReport,
    val trend: List<TrendPoint>,
    val records: List<StatisticsRecordItem>,
)

internal fun recordsForReport(
    records: List<StudyRecord>,
    report: PeriodReport,
): List<StatisticsRecordItem> = records.mapNotNull { record ->
    val overlapMs = overlapDuration(
        record = record,
        startInclusive = report.startInclusive,
        endExclusive = report.endExclusive,
    )
    if (overlapMs <= 0L) {
        null
    } else {
        StatisticsRecordItem(
            id = record.id,
            noteText = record.noteText,
            startedAtEpochMs = record.startedAtEpochMs,
            durationInPeriodMs = overlapMs,
        )
    }
}.sortedWith(
    compareByDescending<StatisticsRecordItem> { it.startedAtEpochMs }
        .thenByDescending { it.id },
)

internal fun buildTrend(
    records: List<StudyRecord>,
    report: PeriodReport,
    now: Instant,
    zone: ZoneId,
): List<TrendPoint> {
    val buckets = when (report.kind) {
        PeriodKind.DAY -> currentNaturalWeekBuckets(now, zone)
        PeriodKind.WEEK -> dailyBuckets(report.period.startDate, 7, zone)
        PeriodKind.MONTH -> monthlyBuckets(report, zone)
    }
    val durations = buckets.map { bucket ->
        records.sumOf { record ->
            overlapDuration(record, bucket.startInclusive, bucket.endExclusive)
        }
    }
    val emphasizedIndex = when (report.kind) {
        PeriodKind.DAY -> {
            val today = now.atZone(zone).toLocalDate()
            buckets.indexOfFirst { it.startDate == today }.coerceAtLeast(0)
        }
        PeriodKind.WEEK,
        PeriodKind.MONTH,
        -> durations.indices.maxByOrNull { durations[it] } ?: 0
    }
    return buckets.mapIndexed { index, bucket ->
        TrendPoint(
            label = bucket.label,
            durationMs = durations[index],
            emphasized = index == emphasizedIndex,
        )
    }
}

private data class TrendBucket(
    val label: String,
    val startDate: LocalDate,
    val startInclusive: Instant,
    val endExclusive: Instant,
)

private fun currentNaturalWeekBuckets(now: Instant, zone: ZoneId): List<TrendBucket> {
    val today = now.atZone(zone).toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return dailyBuckets(monday, 7, zone)
}

private fun dailyBuckets(
    startDate: LocalDate,
    count: Int,
    zone: ZoneId,
): List<TrendBucket> {
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    return List(count) { index ->
        val start = startDate.plusDays(index.toLong())
        val end = start.plusDays(1)
        TrendBucket(
            label = weekdayLabels.getOrElse(index) { start.dayOfMonth.toString() },
            startDate = start,
            startInclusive = start.atStartOfDay(zone).toInstant(),
            endExclusive = end.atStartOfDay(zone).toInstant(),
        )
    }
}

private fun monthlyBuckets(report: PeriodReport, zone: ZoneId): List<TrendBucket> {
    val startDate = report.period.startDate
    val endDate = report.period.endDateExclusive
    val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()
    return List(TREND_POINT_COUNT) { index ->
        val startOffset = index * totalDays / TREND_POINT_COUNT
        val endOffset = (index + 1) * totalDays / TREND_POINT_COUNT
        val start = startDate.plusDays(startOffset.toLong())
        val end = startDate.plusDays(endOffset.toLong())
        TrendBucket(
            label = start.dayOfMonth.toString(),
            startDate = start,
            startInclusive = start.atStartOfDay(zone).toInstant(),
            endExclusive = end.atStartOfDay(zone).toInstant(),
        )
    }
}

private fun overlapDuration(
    record: StudyRecord,
    startInclusive: Instant,
    endExclusive: Instant,
): Long {
    val overlapStart = maxOf(record.startedAtEpochMs, startInclusive.toEpochMilli())
    val overlapEnd = minOf(record.endedAtEpochMs, endExclusive.toEpochMilli())
    return (overlapEnd - overlapStart).coerceAtLeast(0L)
}

private const val TREND_POINT_COUNT = 7
