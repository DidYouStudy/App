// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** Unit tests for [showNotImplementedToast] and [rememberNotImplementedToast]. */
@RunWith(AndroidJUnit4::class)
class NotImplementedToastTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val expectedMessage = context.getString(R.string.toast_not_implemented)

  @Test
  fun showNotImplementedToastDisplaysShortNotImplementedMessage() {
    showNotImplementedToast(context)
    ShadowLooper.idleMainLooper()

    assertEquals(expectedMessage, ShadowToast.getTextOfLatestToast())
    assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().duration)
  }

  @Test
  fun rememberedHandlerShowsToastOnlyWhenClicked() {
    composeTestRule.setContent {
      val onClick = rememberNotImplementedToast()
      Button(onClick = onClick, modifier = Modifier.testTag(BUTTON_TAG)) { Text("Soon") }
    }

    // Composing alone must not show anything.
    assertNull(ShadowToast.getLatestToast())

    composeTestRule.onNodeWithTag(BUTTON_TAG).performClick()
    composeTestRule.waitForIdle()

    assertEquals(expectedMessage, ShadowToast.getTextOfLatestToast())
  }

  @Test
  fun rememberedHandlerShowsToastOnEveryClick() {
    composeTestRule.setContent {
      Button(onClick = rememberNotImplementedToast(), modifier = Modifier.testTag(BUTTON_TAG)) {
        Text("Soon")
      }
    }

    repeat(3) { composeTestRule.onNodeWithTag(BUTTON_TAG).performClick() }
    composeTestRule.waitForIdle()

    assertEquals(3, ShadowToast.shownToastCount())
  }

  @Test
  fun rememberedHandlerIsStableAcrossRecompositions() {
    val handlers = mutableListOf<() -> Unit>()
    var trigger by mutableIntStateOf(0)
    composeTestRule.setContent {
      // Read the state so the block recomposes when it changes.
      trigger.let { handlers += rememberNotImplementedToast() }
    }

    composeTestRule.runOnIdle { trigger++ }
    composeTestRule.waitForIdle()

    assertEquals(2, handlers.size)
    assertSame(handlers[0], handlers[1])
  }

  private companion object {
    const val BUTTON_TAG = "not_implemented_button"
  }
}
