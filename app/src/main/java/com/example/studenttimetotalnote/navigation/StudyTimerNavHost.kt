package com.example.studenttimetotalnote.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.ui.home.HomeScreen
import com.example.studenttimetotalnote.ui.home.HomeViewModel
import com.example.studenttimetotalnote.ui.statistics.StatisticsScreen
import com.example.studenttimetotalnote.ui.statistics.StatisticsViewModel

@Composable
fun StudyTimerNavHost(repository: StudyTimerRepository, modifier: Modifier = Modifier) {
    val factory = remember(repository) { StudyViewModelFactory(repository) }
    val home: HomeViewModel = viewModel(factory = factory)
    val statistics: StatisticsViewModel = viewModel(factory = factory)
    var inStatistics by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inStatistics) {
        if (!inStatistics) statistics.onLifecyclePaused()
    }
    BackHandler(enabled = inStatistics) { inStatistics = false }
    if (inStatistics) {
        StatisticsScreen(
            viewModel = statistics,
            onBack = { inStatistics = false },
            modifier = modifier,
        )
    } else {
        HomeScreen(
            viewModel = home,
            onOpenStatistics = { period: PeriodKind ->
                statistics.selectPeriod(period)
                inStatistics = true
            },
            modifier = modifier,
        )
    }
}

private class StudyViewModelFactory(
    private val repository: StudyTimerRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val model = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(repository)
            StatisticsViewModel::class.java -> StatisticsViewModel(
                repository = repository,
                savedState = extras.createSavedStateHandle(),
            )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return modelClass.cast(model) ?: error("Invalid ViewModel type")
    }
}
