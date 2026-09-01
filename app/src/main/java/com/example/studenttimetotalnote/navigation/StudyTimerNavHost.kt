package com.example.studenttimetotalnote.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.studenttimetotalnote.domain.StudyTimerRepository
import com.example.studenttimetotalnote.domain.model.PeriodKind
import com.example.studenttimetotalnote.ui.home.HomeScreen
import com.example.studenttimetotalnote.ui.home.HomeViewModel
import com.example.studenttimetotalnote.ui.statistics.StatisticsScreen

private enum class StudyTimerDestination {
    HOME,
    STATISTICS,
}

@Composable
fun StudyTimerNavHost(
    repository: StudyTimerRepository,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? ComponentActivity
        ?: error("StudyTimerNavHost requires a ComponentActivity host")
    val homeViewModel = remember(activity, repository) {
        ViewModelProvider(activity, HomeViewModelFactory(repository))
            .get(HomeViewModel::class.java)
    }
    var destinationName by rememberSaveable {
        mutableStateOf(StudyTimerDestination.HOME.name)
    }
    var statisticsPeriodName by rememberSaveable {
        mutableStateOf(PeriodKind.DAY.name)
    }
    val destination = StudyTimerDestination.valueOf(destinationName)

    BackHandler(enabled = destination == StudyTimerDestination.STATISTICS) {
        destinationName = StudyTimerDestination.HOME.name
    }

    when (destination) {
        StudyTimerDestination.HOME -> HomeScreen(
            viewModel = homeViewModel,
            onOpenStatistics = { period ->
                statisticsPeriodName = period.name
                destinationName = StudyTimerDestination.STATISTICS.name
            },
            modifier = modifier,
        )

        StudyTimerDestination.STATISTICS -> StatisticsScreen(
            repository = repository,
            onBack = {
                destinationName = StudyTimerDestination.HOME.name
            },
            initialPeriod = PeriodKind.valueOf(statisticsPeriodName),
            modifier = modifier,
        )
    }
}

private class HomeViewModelFactory(
    private val repository: StudyTimerRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
