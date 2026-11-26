// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.profile.ImageCompressor
import com.android.sample.model.profile.ProfilePictureError
import com.android.sample.model.profile.ProfilePictureException
import com.android.sample.model.profile.ProfilePictureRepository
import com.android.sample.model.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

/**
 * Tests how the stateful [SettingsScreen] uses the system photo picker: tapping "Change profile
 * picture" opens it for images, and its answer reaches the [SettingsViewModel].
 *
 * The picker is not replaced. As in `ScheduleImportLauncherTest`, the test reads the request the
 * system would have received and answers it by hand, so the real launcher code runs.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenPictureTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private lateinit var imageCompressor: ImageCompressor

  /**
   * Shows the real screen on a real ViewModel. Compression is the first step of a picture change
   * and always fails here, so a pick ends in the upload error message and nothing is uploaded.
   */
  @Before
  fun setUp() {
    val authRepository = mock<AuthRepository>()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow(null))
    whenever(authRepository.currentUserId).thenReturn(USER_ID)
    imageCompressor = mock()
    runBlocking {
      whenever(imageCompressor.compress(any()))
          .thenReturn(Result.failure(ProfilePictureException(ProfilePictureError.INVALID_IMAGE)))
    }
    val viewModel =
        SettingsViewModel(
            authRepository,
            mock<ProfileRepository>(),
            mock<ProfilePictureRepository>(),
            imageCompressor,
        )
    composeTestRule.setContent { SettingsScreen(viewModel = viewModel) }
  }

  /** Taps the button and returns the picker request the system would have received. */
  private fun tapChangePictureAndGetPickerRequest(): Intent {
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON)
        .performClick()
    return checkNotNull(shadowOf(composeTestRule.activity).nextStartedActivityForResult).intent
  }

  /** The error message the screen shows when a picture change fails, in the device language. */
  private fun uploadFailedMessage() =
      context.getString(SettingsError.UPLOAD_PICTURE_FAILED.toMessageRes())

  /** Tapping the button opens the picker once, for images only. */
  @Test
  fun changePictureButton_opensThePickerForImagesOnly() {
    val request = tapChangePictureAndGetPickerRequest()

    assertEquals("image/*", request.type)
  }

  /** The chosen image reaches the ViewModel as its URI string, and the change goes ahead. */
  @Test
  fun pickedImage_isHandedToTheViewModelAsAString() {
    val request = tapChangePictureAndGetPickerRequest()

    shadowOf(composeTestRule.activity)
        .receiveResult(request, Activity.RESULT_OK, Intent().setData(Uri.parse(PICKED_URI)))

    // The failing compression ends the change with the error message, which the screen shows.
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(uploadFailedMessage()).fetchSemanticsNodes().isNotEmpty()
    }
    verifyBlocking(imageCompressor) { compress(PICKED_URI) }
  }

  /** A picker closed without a choice changes nothing: no compression and no error. */
  @Test
  fun closedPicker_changesNothing() {
    val request = tapChangePictureAndGetPickerRequest()

    shadowOf(composeTestRule.activity).receiveResult(request, Activity.RESULT_CANCELED, null)
    composeTestRule.waitForIdle()

    verifyBlocking(imageCompressor, never()) { compress(any()) }
    assertEquals(
        0,
        composeTestRule.onAllNodesWithText(uploadFailedMessage()).fetchSemanticsNodes().size,
    )
  }

  private companion object {
    const val USER_ID = "user123"
    const val PICKED_URI = "content://media/picker/1"
  }
}
