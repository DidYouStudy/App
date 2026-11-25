// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.sharedComponents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.android.sample.ui.theme.Dimens
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * UI tests for [UserAvatar]. Coil's loader is replaced by a fake that never uses the network and
 * counts the requests that started, succeeded and failed, so each test can wait for the state it is
 * about.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class UserAvatarTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val started = AtomicInteger()
  private val succeeded = AtomicInteger()
  private val failed = AtomicInteger()

  /** When true, the fake loader fails every request, like an unreachable picture. */
  private var loadFails = false

  /** Completed to let requests finish; replaced by a pending one to keep the avatar loading. */
  private var gate = CompletableDeferred(Unit)

  /** Replaces Coil's shared loader with one that serves a wide, plain red picture. */
  @Before
  fun setUp() {
    val fakeSource =
        object : Fetcher.Factory<Uri> {
          override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher =
              object : Fetcher {
                override suspend fun fetch(): FetchResult {
                  gate.await()
                  check(!loadFails) { "The picture cannot be loaded" }
                  // Twice as wide as high, so that filling a square avatar requires cropping.
                  val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
                  bitmap.eraseColor(Color.RED)
                  return ImageFetchResult(
                      image = bitmap.asImage(),
                      isSampled = false,
                      dataSource = DataSource.MEMORY,
                  )
                }
              }
        }
    val counter =
        object : EventListener() {
          override fun onStart(request: ImageRequest) {
            started.incrementAndGet()
          }

          override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            succeeded.incrementAndGet()
          }

          override fun onError(request: ImageRequest, result: ErrorResult) {
            failed.incrementAndGet()
          }
        }
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context).components { add(fakeSource) }.eventListener(counter).build()
    )
  }

  /** Puts Coil's shared loader back to its default for the other tests. */
  @After
  fun tearDown() {
    SingletonImageLoader.reset()
  }

  /** Renders a medium avatar whose fallback is a recognisable text. */
  private fun showAvatar(pictureUrl: String?) {
    composeTestRule.setContent { TestAvatar(pictureUrl) }
  }

  /** The avatar under test. */
  @Composable
  private fun TestAvatar(pictureUrl: String?) {
    UserAvatar(
        pictureUrl = pictureUrl,
        modifier = Modifier.size(Dimens.avatarSizeMedium).testTag(AVATAR),
    ) {
      Text(PROFILE_PICTURE_FALLBACK)
    }
  }

  /** A null or blank URL shows the fallback and never starts an image request. */
  @Test
  fun missingUrl_showsTheFallbackWithoutRequest() {
    composeTestRule.setContent {
      Column {
        TestAvatar(null)
        TestAvatar("  ")
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithText(PROFILE_PICTURE_FALLBACK).assertCountEquals(2)
    assertEquals(0, started.get())
  }

  /** The fallback stands in while the picture is still loading. */
  @Test
  fun pendingPicture_showsTheFallback() {
    gate = CompletableDeferred()
    showAvatar(PICTURE_URL)

    composeTestRule.waitUntil { started.get() > 0 }
    composeTestRule.onNodeWithText(PROFILE_PICTURE_FALLBACK).assertIsDisplayed()
    assertEquals(0, succeeded.get() + failed.get())
  }

  /** A picture that cannot be loaded leaves the fallback in place. */
  @Test
  fun failedPicture_showsTheFallback() {
    loadFails = true
    showAvatar(PICTURE_URL)

    composeTestRule.waitUntil { failed.get() > 0 }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(PROFILE_PICTURE_FALLBACK).assertIsDisplayed()
  }

  /** A loaded picture replaces the fallback, fills the avatar and is cropped to a circle. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun loadedPicture_replacesTheFallbackAndFillsACircle() {
    showAvatar(PICTURE_URL)

    composeTestRule.waitUntil { succeeded.get() > 0 }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(PROFILE_PICTURE_FALLBACK).assertDoesNotExist()

    val pixels = composeTestRule.onNodeWithTag(AVATAR).captureToImage().asAndroidBitmap()
    // Near the top edge: red only if the wide picture was cropped to fill, not fitted inside.
    assertEquals(Color.RED, pixels.getPixel(pixels.width / 2, pixels.height / 8))
    // The corner lies outside the circle, so the picture must not be drawn there.
    assertNotEquals(Color.RED, pixels.getPixel(0, 0))
  }

  private companion object {
    const val AVATAR = "userAvatarUnderTest"
    const val PROFILE_PICTURE_FALLBACK = "fallback"
    const val PICTURE_URL = "https://example.com/picture.jpg"
  }
}
