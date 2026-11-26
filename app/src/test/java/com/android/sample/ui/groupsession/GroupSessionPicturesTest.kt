// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.groupsession

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the profile pictures of group sessions. Coil's loader is replaced by a fake that
 * never uses the network, records the requested URLs and fails for [BROKEN_URL].
 */
@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class GroupSessionPicturesTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  private val requested = ConcurrentLinkedQueue<String>()

  /** Replaces Coil's shared loader with the fake one. */
  @Before
  fun setUp() {
    val fakeSource =
        object : Fetcher.Factory<Uri> {
          override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher =
              object : Fetcher {
                override suspend fun fetch(): FetchResult {
                  requested += data.toString()
                  check(data.toString() != BROKEN_URL) { "The picture cannot be loaded" }
                  return ImageFetchResult(
                      image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImage(),
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

  /** A roster row shows the picture, and keeps the initial when it has none or it fails to load. */
  @Test
  fun participantRows_showThePictureOrTheInitial() {
    val rows =
        listOf(
            participant("Ann", PICTURE_URL),
            participant("Ben", BROKEN_URL),
            participant("Cleo", null),
        )
    composeTestRule.setContent {
      GroupSessionParticipantsSheet(
          state =
              GroupSessionParticipantsSheet(
                  sessionSubject = "Physics",
                  participantCount = rows.size,
                  rows = rows,
                  isLoading = false,
              ),
          onDismiss = {},
      )
    }

    waitForPicture("A", GroupSessionParticipantsTestTags.AVATAR)

    initial("B", GroupSessionParticipantsTestTags.AVATAR).assertCountEquals(1)
    initial("C", GroupSessionParticipantsTestTags.AVATAR).assertCountEquals(1)
    assertEquals(setOf(PICTURE_URL, BROKEN_URL), requested.toSet())
    val avatars = composeTestRule.onAllNodesWithTag(GroupSessionParticipantsTestTags.AVATAR)
    avatars.assertCountEquals(3)
    repeat(3) {
      avatars[it].assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }
  }

  /** The initial [letter] shown inside an avatar tagged [avatarTag]. */
  private fun initial(letter: String, avatarTag: String) =
      composeTestRule.onAllNodes(
          hasText(letter) and hasAnyAncestor(hasTestTag(avatarTag)),
          useUnmergedTree = true,
      )

  /** Waits until the working picture was requested and has replaced the initial [letter]. */
  private fun waitForPicture(letter: String, avatarTag: String) {
    composeTestRule.waitUntil {
      PICTURE_URL in requested && initial(letter, avatarTag).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.waitForIdle()
  }

  /** A roster member who is not the host. */
  private fun participant(name: String, pictureUrl: String?) =
      GroupSessionParticipant(
          userId = name,
          username = name,
          isHost = false,
          isCurrentUser = false,
          profilePictureUrl = pictureUrl,
      )

  private companion object {
    const val PICTURE_URL = "https://example.com/picture.jpg"
    const val BROKEN_URL = "https://example.com/broken.jpg"
  }
}
