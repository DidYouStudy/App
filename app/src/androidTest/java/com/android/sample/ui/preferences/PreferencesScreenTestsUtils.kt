package com.android.sample.ui.preferences

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction

object PreferencesScreenTestsUtils {
  internal fun ComposeTestRule.click(tag: String) {
    onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
  }

  internal fun ComposeTestRule.assertSelected(tag: String) {
    onNodeWithTag(tag).performScrollTo().assertIsSelected()
  }
}
