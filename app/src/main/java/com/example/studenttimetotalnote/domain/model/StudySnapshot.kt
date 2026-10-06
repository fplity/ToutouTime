package com.example.studenttimetotalnote.domain.model

/** Active and completed sessions read from one database transaction. */
data class StudySnapshot(
    val activeSession: ActiveSession? = null,
    val records: List<StudyRecord> = emptyList(),
)
