package com.example.studenttimetotalnote.domain.model

/** The one recoverable session that may be running at a time. */
data class ActiveSession(
    val id: Int = SINGLETON_ID,
    val noteText: String,
    val startedAtEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
