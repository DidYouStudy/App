// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.groupsession

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.profile.UsernamePolicy
import com.android.sample.ui.theme.Dimens
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

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

  /** A session card shows the host's picture, else their initial, also when it fails to load. */
  @Test
  fun hostLine_showsThePictureOrTheInitial() {
    showCards(
        card("1", hostName = "Dana", pictureUrl = PICTURE_URL),
        card("2", hostName = "Eve", pictureUrl = BROKEN_URL),
        card("3", hostName = "Finn", pictureUrl = null),
    )

    waitForPicture("D", GroupSessionScreenTestTags.HOST_AVATAR)

    initial("E", GroupSessionScreenTestTags.HOST_AVATAR).assertCountEquals(1)
    initial("F", GroupSessionScreenTestTags.HOST_AVATAR).assertCountEquals(1)
    assertEquals(setOf(PICTURE_URL, BROKEN_URL), requested.toSet())
    val avatars = composeTestRule.onAllNodesWithTag(GroupSessionScreenTestTags.HOST_AVATAR)
    avatars.assertCountEquals(3)
    repeat(3) {
      avatars[it]
          .assertWidthIsEqualTo(Dimens.avatarSizeSmall)
          .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_hosted_by, "Dana"))
        .assertExists()
  }

  /** An unknown host has an avatar without an initial, and your own session has no avatar. */
  @Test
  fun hostLine_hasNoInitialForAnUnknownHostAndNoAvatarOnYourOwnSession() {
    showCards(
        card("1", hostName = null, pictureUrl = null),
        card("2", hostName = null, pictureUrl = null, isCreator = true),
    )

    composeTestRule.onAllNodesWithTag(GroupSessionScreenTestTags.HOST_AVATAR).assertCountEquals(1)
    composeTestRule
        .onAllNodes(
            hasAnyAncestor(hasTestTag(GroupSessionScreenTestTags.HOST_AVATAR)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        )
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_host_self))
        .assertExists()
    assertEquals(emptyList<String>(), requested.toList())
  }

  /** The longest host name stays on one line beside the avatar, cut off with an ellipsis. */
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE) // Measures text as a device does.
  fun hostLine_cutsOffALongNameBesideTheAvatar() {
    val name = "W".repeat(UsernamePolicy.MAX_LENGTH)
    composeTestRule.setContent {
      GroupSessionCard(
          item = card("1", hostName = name, pictureUrl = null),
          now = NOW,
          onJoin = {},
          onLeave = {},
          onEdit = {},
          modifier = Modifier.width(NARROW_CARD_WIDTH),
      )
    }
    val hostLine =
        composeTestRule.onNodeWithText(context.getString(R.string.group_session_hosted_by, name))

    val layouts = mutableListOf<TextLayoutResult>()
    hostLine.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertEquals(1, layouts.single().lineCount)
    assertTrue(layouts.single().isLineEllipsized(0))

    val avatar = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.HOST_AVATAR)
    avatar.assertWidthIsEqualTo(Dimens.avatarSizeSmall)
    val textBounds = hostLine.getUnclippedBoundsInRoot()
    assertTrue(avatar.getUnclippedBoundsInRoot().right <= textBounds.left)
    assertTrue(textBounds.right <= NARROW_CARD_WIDTH)
  }

  /** Shows [items] as session cards, all composed so that off-screen ones can be asserted. */
  private fun showCards(vararg items: GroupSessionItem) {
    composeTestRule.setContent {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        items.forEach {
          GroupSessionCard(item = it, now = NOW, onJoin = {}, onLeave = {}, onEdit = {})
        }
      }
    }
  }

  /** A card of a session you have not joined, or of your own session when [isCreator]. */
  private fun card(
      id: String,
      hostName: String?,
      pictureUrl: String?,
      isCreator: Boolean = false,
  ) =
      GroupSessionItem(
          session =
              GroupSession(
                  id = id,
                  createdBy = "host-$id",
                  memberIds = listOf("host-$id"),
                  details =
                      EventDetails(
                          type = EventType.STUDY_SESSION,
                          subject = "Physics",
                          startsAt = NOW.plusSeconds(3600),
                          endsAt = NOW.plusSeconds(7200),
                      ),
                  createdAt = NOW,
              ),
          isMember = isCreator,
          isCreator = isCreator,
          hostName = hostName,
          hostPictureUrl = pictureUrl,
      )

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
    val NOW: Instant = Instant.parse("2026-01-05T12:00:00Z")
    const val PICTURE_URL = "https://example.com/picture.jpg"
    const val BROKEN_URL = "https://example.com/broken.jpg"
    /** The width of a card on a 320 dp phone, inside the list's padding. */
    val NARROW_CARD_WIDTH = 288.dp
  }
}
