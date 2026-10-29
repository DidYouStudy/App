// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.preferences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.ScheduleImporter
import com.android.sample.ui.auth.TestAuthRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** Tests the file picker launch and the result toast of [rememberScheduleImportLauncher]. */
@RunWith(AndroidJUnit4::class)
class ScheduleImportLauncherTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val validIcs =
      """
      BEGIN:VCALENDAR
      BEGIN:VEVENT
      UID:a@x
      SUMMARY:Algebra
      CATEGORIES:Cours
      DTSTART;TZID=Europe/Zurich:20261015T080000
      DURATION:PT2H
      END:VEVENT
      END:VCALENDAR
      """
          .trimIndent()

  private fun viewModel(): ScheduleImportViewModel {
    val auth = TestAuthRepository()
    auth.currentUser.value = UserAccount("user-1", null, null, null)
    return ScheduleImportViewModel(ScheduleImporter(InMemoryEventsRepository()), auth)
  }

  /** Shows a button that runs the launcher action, and returns the activity's shadow. */
  private fun setContent(viewModel: ScheduleImportViewModel) {
    composeTestRule.setContent {
      val launch = rememberScheduleImportLauncher(viewModel)
      Button(onClick = launch) { Text("import") }
    }
  }

  /** Clicks the button and returns the picker request the system would have received. */
  private fun clickAndGetPickerRequest(): Intent {
    composeTestRule.onNodeWithText("import").performClick()
    val started = shadowOf(composeTestRule.activity).nextStartedActivityForResult
    assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
    return started.intent
  }

  /** Writes [text] to a temporary file and returns its `file:` URI, readable by the resolver. */
  private fun fileUri(text: String): Uri =
      Uri.fromFile(
          File.createTempFile("schedule", ".ics").apply {
            writeText(text)
            deleteOnExit()
          }
      )

  /**
   * Waits for the background import: the file is read on an IO thread, and its result is posted to
   * the paused main looper, which must be drained by hand.
   */
  private fun awaitUntil(condition: () -> Boolean) {
    repeat(500) {
      shadowOf(Looper.getMainLooper()).idle()
      composeTestRule.waitForIdle()
      if (condition()) return
      Thread.sleep(10)
    }
    fail("Condition not met in time")
  }

  private fun pick(request: Intent, uri: Uri) =
      shadowOf(composeTestRule.activity)
          .receiveResult(request, Activity.RESULT_OK, Intent().setData(uri))

  @Test
  fun chosenFile_isImportedAndSuccessToastIsShown() {
    val viewModel = viewModel()
    setContent(viewModel)
    val uri = fileUri(validIcs)

    pick(clickAndGetPickerRequest(), uri)

    awaitUntil { ShadowToast.getLatestToast() != null }
    assertEquals("Imported 1 event", ShadowToast.getTextOfLatestToast())
    awaitUntil { viewModel.state.value == ScheduleImportState.Idle }
  }

  @Test
  fun invalidFile_showsFailureToast() {
    val viewModel = viewModel()
    setContent(viewModel)
    val uri = fileUri("nope")

    pick(clickAndGetPickerRequest(), uri)

    awaitUntil { ShadowToast.getLatestToast() != null }
    assertEquals(
        "Could not import this schedule. Choose a valid .ics file.",
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun cancelledPicker_importsNothingAndShowsNoToast() {
    val viewModel = viewModel()
    setContent(viewModel)

    val request = clickAndGetPickerRequest()
    shadowOf(composeTestRule.activity).receiveResult(request, Activity.RESULT_CANCELED, null)
    composeTestRule.waitForIdle()

    assertEquals(ScheduleImportState.Idle, viewModel.state.value)
    assertNull(ShadowToast.getLatestToast())
  }
}
