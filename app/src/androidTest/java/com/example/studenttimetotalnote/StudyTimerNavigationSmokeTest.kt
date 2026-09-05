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
import java.time.Year
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
        val currentYear = Year.now().value
        composeRule.onNodeWithTag(HomeSemantics.TodaySummary).performClick()
        composeRule.onNodeWithTag(StatisticsSemantics.PeriodTabs).performClick()
        composeRule.onNodeWithText("年度").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag(StatisticsSemantics.YearSelector).assertIsDisplayed()
        composeRule.onNodeWithText("$currentYear 年").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.NextYear).performClick()
        composeRule.onNodeWithText("${currentYear + 1} 年").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsSemantics.PreviousYear).performClick()
        composeRule.onNodeWithText("$currentYear 年").assertIsDisplayed()
    }
}
