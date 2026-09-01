package com.example.studenttimetotalnote.domain.model

/** A completed half-open study interval: [startedAtEpochMs, endedAtEpochMs). */
data class StudyRecord(
    val id: Long = 0L,
    val noteText: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long = endedAtEpochMs - startedAtEpochMs,
) {
    init {
        require(endedAtEpochMs >= startedAtEpochMs) { "A study record cannot end before it starts" }
        require(durationMs == endedAtEpochMs - startedAtEpochMs) {
            "durationMs must equal the record interval"
        }
    }
}
