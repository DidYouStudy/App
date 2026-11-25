// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.android.sample.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks how the Settings avatar asks Coil for the profile picture, with the image loader replaced
 * by a fake that records the requested URLs instead of using the network.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsProfilePictureTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** URLs the fake loader was asked for, in order. */
  private val requestedUrls = mutableListOf<String>()

  /** When true, the fake loader fails every request, like an unreachable picture. */
  private var loadFails = false

  /** When set, requests stay pending until it is completed, like a slow download. */
  private var loadGate: CompletableDeferred<Unit>? = null

  /** Replaces Coil's shared loader with one whose only source is the fake below. */
  @Before
  fun setUp() {
    val fakeSource =
        object : Fetcher.Factory<Uri> {
          override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher =
              object : Fetcher {
                override suspend fun fetch(): FetchResult {
                  requestedUrls += data.toString()
                  loadGate?.await()
                  check(!loadFails) { "The picture cannot be loaded" }
                  return ImageFetchResult(
                      image = ColorDrawable(Color.RED).asImage(),
                      isSampled = false,
                      dataSource = DataSource.MEMORY,
                  )
                }
              }
        }
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context).components { add(fakeSource) }.build()
    )
  }

  /** Puts Coil's shared loader back to its default for the other tests. */
  @After
  fun tearDown() {
    SingletonImageLoader.reset()
  }

  private fun showAvatar(profilePictureUrl: String) {
    composeTestRule.setContent {
      SettingsScreenStateless(profilePictureUrl = profilePictureUrl, isLoading = false)
    }
  }

  private fun assertAvatarIsDisplayed() {
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.settings_profile_picture))
        .assertIsDisplayed()
  }

  /** A non-blank URL is requested from the image loader exactly once. */
  @Test
  fun pictureUrl_isRequestedFromTheImageLoader() {
    showAvatar(PICTURE_URL)

    composeTestRule.waitUntil { requestedUrls.isNotEmpty() }
    assertEquals(listOf(PICTURE_URL), requestedUrls)
    assertAvatarIsDisplayed()
  }

  /** When the picture cannot be loaded, the default icon is still shown. */
  @Test
  fun failedPictureLoad_keepsTheDefaultIcon() {
    loadFails = true
    showAvatar(PICTURE_URL)

    composeTestRule.waitUntil { requestedUrls.isNotEmpty() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DEFAULT_AVATAR_ICON).assertIsDisplayed()
  }

  /** A changed URL shows the progress ring until the new picture has been downloaded. */
  @Test
  fun newPictureUrl_showsTheProgressRingUntilLoaded() {
    var url by mutableStateOf(PICTURE_URL)
    composeTestRule.setContent {
      SettingsScreenStateless(profilePictureUrl = url, isLoading = false)
    }
    waitForProgressRing(shown = false)

    val gate = CompletableDeferred<Unit>()
    loadGate = gate
    url = NEW_PICTURE_URL
    waitForProgressRing(shown = true)

    gate.complete(Unit)
    waitForProgressRing(shown = false)
  }

  /** A picture that cannot be loaded does not leave the progress ring spinning. */
  @Test
  fun failedPictureLoad_removesTheProgressRing() {
    val gate = CompletableDeferred<Unit>()
    loadGate = gate
    loadFails = true
    showAvatar(PICTURE_URL)
    waitForProgressRing(shown = true)

    gate.complete(Unit)

    waitForProgressRing(shown = false)
  }

  /** Waits until the avatar's progress ring is present or absent, as [shown] says. */
  private fun waitForProgressRing(shown: Boolean) {
    composeTestRule.waitUntil {
      val rings =
          composeTestRule
              .onAllNodesWithTag(SettingsScreenTestTags.PICTURE_UPLOADING_INDICATOR)
              .fetchSemanticsNodes()
      requestedUrls.isNotEmpty() && rings.isNotEmpty() == shown
    }
  }

  private companion object {
    const val PICTURE_URL = "https://example.com/profile.jpg"
    const val NEW_PICTURE_URL = "https://example.com/new-profile.jpg"
  }
}
