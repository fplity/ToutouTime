package com.example.studenttimetotalnote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.studenttimetotalnote.ui.home.HomeSemantics
import com.example.studenttimetotalnote.ui.components.StudyTimerSemantics
import com.example.studenttimetotalnote.ui.statistics.StatisticsSemantics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyTimerNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnIdleHome() {
        composeRule.onNodeWithText("偷偷时间").assertIsDisplayed()
        composeRule.onNodeWithText("准备专注").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeRule.onNodeWithText("开始学习").assertIsDisplayed()
        composeRule.onNodeWithText("查看今日记录").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.Screen).assertDoesNotExist()
    }

    @Test
    fun startOpensNoteDialogAndCancelReturnsToIdle() {
        composeRule.onNodeWithTag(StudyTimerSemantics.PrimaryButton).performClick()
        composeRule.onNodeWithTag(HomeSemantics.NoteDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeSemantics.NoteDialogConfirm).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeSemantics.NoteDialogCancel).performClick()
        composeRule.onNodeWithTag(HomeSemantics.NoteDialog).assertDoesNotExist()
        composeRule.onNodeWithText("开始学习").assertIsDisplayed()
    }

    @Test
    fun todayEntryAndHeaderIconNavigateToStatisticsAndBack() {
        composeRule.onNodeWithTag(HomeSemantics.TodaySummary).performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.Screen).assertIsDisplayed()
        composeRule.onNodeWithText("统计").assertIsDisplayed()
        composeRule.onNodeWithText("上一周").assertDoesNotExist()
        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("上一周").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.Back).performClick()
        composeRule.onNodeWithText("偷偷时间").assertIsDisplayed()

        composeRule.onNodeWithTag(HomeSemantics.StatisticsButton).performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.Back).performClick()
        composeRule.onNodeWithText("偷偷时间").assertIsDisplayed()
    }

    @Test
    fun annualPeriodShowsTheExactYearAndSwitchesToAFutureYear() {
        val currentYear = LocalDate.now().year
        composeRule.onNodeWithTag(HomeSemantics.TodaySummary).performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("年度").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(StatisticsSemantics.PeriodSelector).assertIsDisplayed()
        composeRule.onNodeWithText("$currentYear 年").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.NextPeriod).performClick()
        composeRule.onNodeWithText("${currentYear + 1} 年").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.PreviousPeriod).performClick()
        composeRule.onNodeWithText("$currentYear 年").assertIsDisplayed()
    }

    @Test
    fun allTimePeriodShowsEveryRecordScopeWithoutNavigationArrows() {
        composeRule.onNodeWithTag(HomeSemantics.TodaySummary).performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("使用以来").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(StatisticsSemantics.PeriodSelector).assertIsDisplayed()
        composeRule.onNodeWithText("使用以来").assertIsDisplayed()
        composeRule.onNodeWithText("全部学习记录").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.PreviousPeriod).assertDoesNotExist()
        composeRule.onNodeWithTag(StatisticsSemantics.NextPeriod).assertDoesNotExist()
    }

    @Test
    fun dayWeekAndMonthPeriodsCanMoveBackwardAndForward() {
        val today = LocalDate.now()
        composeRule.onNodeWithTag(HomeSemantics.TodaySummary).performClick()

        composeRule.onNodeWithTag(StatisticsSemantics.PeriodSelector).assertIsDisplayed()
        composeRule.onNodeWithText(fullDateTitle(today)).assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.PreviousPeriod).performClick()
        composeRule.onNodeWithText(fullDateTitle(today.minusDays(1))).assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("今日").performClick()
        composeRule.onNodeWithText(fullDateTitle(today)).assertIsDisplayed()

        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("上一周").performClick()
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val previousWeek = currentWeek.minusWeeks(1)
        composeRule.onNodeWithText(weekTitle(previousWeek)).assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.NextPeriod).performClick()
        composeRule.onNodeWithText(weekTitle(currentWeek)).assertIsDisplayed()
        composeRule.onNodeWithText("${weekYearContext(currentWeek)} · 本周实时累计")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("上个月").performClick()
        val currentMonth = today.withDayOfMonth(1)
        val previousMonth = currentMonth.minusMonths(1)
        composeRule.onNodeWithText(monthTitle(previousMonth)).assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.NextPeriod).performClick()
        composeRule.onNodeWithText(monthTitle(currentMonth)).assertIsDisplayed()
        composeRule.onNodeWithText("本月 · 实时累计").assertIsDisplayed()
    }

    private fun fullDateTitle(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

    private fun monthTitle(date: LocalDate): String =
        "${date.year}年${date.monthValue}月"

    private fun weekTitle(start: LocalDate): String {
        val end = start.plusDays(6)
        return "${start.monthValue}月${start.dayOfMonth}日 — " +
            "${end.monthValue}月${end.dayOfMonth}日"
    }

    private fun weekYearContext(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.year == end.year) "${start.year}年" else "${start.year}—${end.year}年"
    }
}
