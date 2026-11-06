package org.nsa.hall

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise saved navigation and the actual lifecycle-owned window recomposer. */
@RunWith(AndroidJUnit4::class)
class NavigationRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsAndRuntimeCoordinatorSurviveActivityRecreation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HallApplication
        val coordinator = app.sessionController
        compose.waitUntil(20000) {
            compose.onAllNodesWithText("进入游戏大厅").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("我的游戏").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("进入游戏大厅").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("进入游戏大厅").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("设备支持").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("设备支持").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { assertSame(coordinator, (it.application as HallApplication).sessionController) }
    }
}
