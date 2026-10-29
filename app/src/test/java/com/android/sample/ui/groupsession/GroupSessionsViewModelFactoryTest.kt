// Co-authored-by: GitHub Copilot
package com.android.sample.ui.groupsession

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests injected factory dependencies and provider-backed Compose ViewModel ownership. */
@RunWith(AndroidJUnit4::class)
class GroupSessionsViewModelFactoryTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val authRepository = TestAuthRepository()
  private val repository = InMemoryGroupSessionsRepository()
  private val factory = GroupSessionsViewModelFactory(authRepository, repository)

  @After
  fun clearProviders() {
    setAuthProviderRepository(null)
    setGroupSessionsProviderRepository(null)
  }

  /** Factory-created ViewModels observe the supplied repositories and current user. */
  @Test
  fun createUsesInjectedAuthenticationAndSessionsRepositories() {
    useEmptyComposition()
    authRepository.currentUser.value = account(ALICE)
    val session =
        session(
            id = "injected",
            creator = ALICE,
            startsAt = Instant.now().plusSeconds(60),
            endsAt = Instant.now().plusSeconds(600),
        )
    runBlocking { repository.saveSession(ALICE, session) }

    val viewModel = factory.create(GroupSessionsViewModel::class.java)

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      viewModel.uiState.value.sessions.map { it.session } == listOf(session)
    }
    assertEquals(
        listOf(GroupSessionItem(session, isMember = true, isCreator = true)),
        viewModel.uiState.value.sessions,
    )
  }

  /** Unsupported ViewModel types are rejected with the offending class name in the message. */
  @Test
  fun createRejectsAnotherViewModelTypeAndNamesIt() {
    val error =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }

    assertTrue(error.message.orEmpty().contains(OtherViewModel::class.java.name))
  }

  /**
   * The composable gets injected provider repositories and retains one instance across
   * recomposition.
   */
  @Test
  fun groupSessionsViewModelUsesProvidersAndSurvivesRecomposition() {
    authRepository.currentUser.value = account(ALICE)
    val providedRepository = InMemoryGroupSessionsRepository()
    val providedSession =
        session(
            id = "provided",
            creator = ALICE,
            startsAt = Instant.now().plusSeconds(60),
            endsAt = Instant.now().plusSeconds(600),
        )
    runBlocking { providedRepository.saveSession(ALICE, providedSession) }
    setAuthProviderRepository(authRepository)
    setGroupSessionsProviderRepository(providedRepository)
    val trigger = mutableStateOf(0)
    val received = mutableListOf<GroupSessionsViewModel>()

    composeTestRule.setContent {
      trigger.value
      received += groupSessionsViewModel()
    }
    composeTestRule.runOnIdle { trigger.value++ }
    composeTestRule.waitForIdle()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      received.firstOrNull()?.uiState?.value?.sessions?.map { it.session } ==
          listOf(providedSession)
    }

    assertTrue(received.size >= 2)
    assertTrue(received.all { it === received.first() })
    assertEquals(
        listOf(providedSession),
        received.first().uiState.value.sessions.map { it.session },
    )
  }

  private fun useEmptyComposition() {
    composeTestRule.setContent {}
  }

  private fun setAuthProviderRepository(repository: AuthRepository?) {
    AuthRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(AuthRepositoryProvider, repository)
    }
  }

  private fun setGroupSessionsProviderRepository(repository: GroupSessionsRepository?) {
    GroupSessionsRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(GroupSessionsRepositoryProvider, repository)
    }
  }

  private fun account(uid: String) = UserAccount(uid, "$uid@example.com", uid, null)

  private fun session(id: String, creator: String, startsAt: Instant, endsAt: Instant) =
      GroupSession(
          id = id,
          createdBy = creator,
          memberIds = listOf(creator),
          type = EventType.COURSE,
          subject = "Biology",
          startsAt = startsAt,
          endsAt = endsAt,
          createdAt = Instant.now(),
      )

  private class OtherViewModel : ViewModel()

  private companion object {
    const val ALICE = "alice"
  }
}
