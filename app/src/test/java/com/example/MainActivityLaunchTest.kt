package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `main activity launches cleanly and completes onboarding without ANR`() {
    assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("onboarding_initialize_button")
        .fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("onboarding_initialize_button")
      .performScrollTo()
      .assertIsDisplayed()
      .performClick()

    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("scan_folder_button")
        .fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("scan_folder_button").assertIsDisplayed()
    composeRule.onNodeWithTag("add_exe_button").assertIsDisplayed()
  }
}
