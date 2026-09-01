package com.example.studenttimetotalnote

import android.app.Application
import androidx.room.Room
import com.example.studenttimetotalnote.data.local.RoomStudyTimerStore
import com.example.studenttimetotalnote.data.local.StudyTimerDatabase
import com.example.studenttimetotalnote.domain.DefaultStudyTimerRepository
import com.example.studenttimetotalnote.domain.StudyTimerRepository

class StudyTimerApplication : Application() {
    val repository: StudyTimerRepository by lazy {
        DefaultStudyTimerRepository(
            RoomStudyTimerStore(database),
        )
    }

    private val database: StudyTimerDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            StudyTimerDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }

    private companion object {
        const val DATABASE_NAME = "study_timer.db"
    }
}
