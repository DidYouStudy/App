// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.eventplanning

import androidx.lifecycle.ViewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.GroupSession
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.profile.FakeProfileRepository
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.UserProfile
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [EventDetailsViewModel], testing event loading, event deletion, task management,
 * error handling, and factory creation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val currentUserFlow = MutableStateFlow<UserAccount?>(null)
  private lateinit var authRepository: AuthRepository
  private lateinit var eventsRepository: EventsRepository
  private lateinit var groupSessionsRepository: InMemoryGroupSessionsRepository
  private lateinit var profileRepository: FakeProfileRepository

  private val zone = ZoneId.of("Europe/Berlin")
  private val now = Instant.parse("2026-11-02T10:00:00Z")
  private val clock = Clock.fixed(now, zone)
  private val user = UserAccount("user123", "user@example.com", "Test User", null)

  private val sampleDetails =
      EventDetails(
          type = EventType.EXERCISE_SESSION,
          subject = "Algebra",
          startsAt = Instant.parse("2026-11-02T10:15:00Z"),
          endsAt = Instant.parse("2026-11-02T12:00:00Z"),
          location = "CM5",
          description = "Exercise session focusing on the new concepts of week 8.",
      )

  private val sampleEvent =
      PersonalEvent(
          id = "event-123",
          ownerId = "user123",
          details = sampleDetails,
          createdAt = now,
          updatedAt = now,
          tasks =
              listOf(
                  EventTask("Solve exercises 12, 13"),
                  EventTask("Read chapter 3 of textbook"),
              ),
      )

  private class OtherViewModel : ViewModel()

  private companion object {
    const val SESSION_ID = "session-1"
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock()
    eventsRepository = mock()
    groupSessionsRepository = InMemoryGroupSessionsRepository()
    profileRepository = FakeProfileRepository()
    whenever(authRepository.currentUser).thenReturn(currentUserFlow)
    currentUserFlow.value = user
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(groups: GroupSessionsRepository = groupSessionsRepository) =
      EventDetailsViewModel(
          authRepository,
          eventsRepository,
          groups,
          profileRepository,
          clock,
      )

  @Test
  fun init_userFlowFailsWithMessage_exposesErrorInState() = runTest {
    whenever(authRepository.currentUser)
        .thenReturn(flow<UserAccount?> { throw RuntimeException("Auth error") })

    val viewModel = createViewModel()
    assertEquals(EventPlanningError.FAILED_TO_OBSERVE_USER, viewModel.uiState.value.error)
  }

  @Test
  fun loadEvent_validId_loadsEventSuccessfully() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun loadEvent_blankId_isIgnored() = runTest {
    val viewModel = createViewModel()
    viewModel.loadEvent("   ")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertFalse(state.isLoading)
  }

  @Test
  fun loadEvent_sameIdAlreadyLoaded_isIgnored() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    verify(eventsRepository, times(1)).getEvent("user123", "event-123")

    viewModel.loadEvent("event-123")
    verify(eventsRepository, times(1)).getEvent("user123", "event-123")
  }

  @Test
  fun loadEvent_noSignedInUser_failsWithError() = runTest {
    currentUserFlow.value = null
    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, state.error)
  }

  @Test
  fun loadEvent_repositoryFailure_exposesMappedError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.failure(EventsException(EventsError.NOT_FOUND)))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertEquals(EventPlanningError.EVENT_NOT_FOUND, state.error)
  }

  @Test
  fun loadEvent_cancellationException_cancelsCoroutineWithoutSettingError() = runTest {
    whenever(eventsRepository.getEvent(any(), any())).thenThrow(CancellationException("cancelled"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertNull(state.error)
  }

  @Test
  fun loadEvent_unexpectedException_setsError() = runTest {
    whenever(eventsRepository.getEvent(any(), any()))
        .thenThrow(RuntimeException("Unexpected load error"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertFalse(state.isLoading)
    assertEquals(EventPlanningError.FAILED_TO_LOAD_EVENT, state.error)
  }

  @Test
  fun deleteEvent_noEventLoaded_setsError() = runTest {
    val viewModel = createViewModel()
    viewModel.deleteEvent()

    assertEquals(EventPlanningError.NO_EVENT_TO_EDIT, viewModel.uiState.value.error)
  }

  @Test
  fun deleteEvent_noSignedInUser_setsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    currentUserFlow.value = null
    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertFalse(state.isDeleting)
    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, state.error)
  }

  @Test
  fun deleteEvent_loadedEvent_deletesSuccessfullyAndSetsFlag() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.deleteEvent("user123", "event-123")).thenReturn(Result.success(Unit))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertFalse(state.isDeleting)
    assertTrue(state.isEventDeleted)
    assertNull(state.error)

    viewModel.onEventDeletedHandled()
    assertFalse(viewModel.uiState.value.isEventDeleted)
  }

  @Test
  fun deleteEvent_repositoryFailure_setsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.deleteEvent("user123", "event-123"))
        .thenReturn(Result.failure(EventsException(EventsError.PERMISSION_DENIED)))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertFalse(state.isDeleting)
    assertFalse(state.isEventDeleted)
    assertEquals(EventPlanningError.PERMISSION_DENIED, state.error)
  }

  @Test
  fun deleteEvent_cancellationException_cancelsCoroutineWithoutSettingError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenThrow(CancellationException("cancelled"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertNull(state.error)
  }

  @Test
  fun deleteEvent_unexpectedException_setsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenThrow(RuntimeException("Unexpected delete error"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertFalse(state.isDeleting)
    assertEquals(EventPlanningError.FAILED_TO_DELETE_EVENT, state.error)
  }

  @Test
  fun toggleTask_validIndex_togglesCompletionAndSaves() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
        .thenReturn(Result.success(Unit))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val updatedEvent = viewModel.uiState.value.event as PersonalEvent?
    assertNotNull(updatedEvent)
    assertTrue(updatedEvent!!.tasks[0].isCompleted)
    assertEquals(now, updatedEvent.tasks[0].completedAt)

    verify(eventsRepository).updateEventTasks("user123", "event-123", updatedEvent.tasks, now)
    verify(eventsRepository, never()).saveEvent(any())

    // Toggle back
    viewModel.toggleTask(0)
    val toggledBackEvent = viewModel.uiState.value.event as PersonalEvent?
    assertNotNull(toggledBackEvent)
    assertFalse(toggledBackEvent!!.tasks[0].isCompleted)
    assertNull(toggledBackEvent.tasks[0].completedAt)
  }

  @Test
  fun toggleTask_invalidIndex_isIgnored() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(-1)
    viewModel.toggleTask(100)

    verify(eventsRepository, never()).updateEventTasks(any(), any(), any(), any())
  }

  /** A displayed checklist cannot be saved over a pending refresh or delete. */
  @Test
  fun toggleTask_whileRefreshingOrDeleting_ignoresTaps() = runTest {
    val refresh = CompletableDeferred<PersonalEvent>()
    val delete = CompletableDeferred<Unit>()
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.deleteEvent("user123", "event-123")).doSuspendableAnswer {
      Result.success(delete.await())
    }
    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    whenever(eventsRepository.getEvent("user123", "event-123")).doSuspendableAnswer {
      Result.success(refresh.await())
    }

    viewModel.loadEvent("event-123", refresh = true)
    assertTrue(viewModel.uiState.value.isLoading)
    viewModel.toggleTask(0)
    assertEquals(sampleEvent, viewModel.uiState.value.event)
    val edited = sampleEvent.copy(details = sampleDetails.copy(description = "Saved edit"))
    refresh.complete(edited)

    viewModel.deleteEvent()
    assertTrue(viewModel.uiState.value.isDeleting)
    viewModel.toggleTask(0)
    assertEquals(edited, viewModel.uiState.value.event)
    delete.complete(Unit)

    verify(eventsRepository, never()).updateEventTasks(any(), any(), any(), any())
    verify(eventsRepository, never()).saveEvent(any())
    assertNull(viewModel.uiState.value.event)
  }

  /** Without a loaded event there is no checklist to save. */
  @Test
  fun toggleTask_noEventLoaded_isIgnored() = runTest {
    val viewModel = createViewModel()

    viewModel.toggleTask(0)

    assertNull(viewModel.uiState.value.event)
    verify(eventsRepository, never()).updateEventTasks(any(), any(), any(), any())
  }

  /** A refresh waits for a pending checklist save, so it cannot overwrite the toggled task. */
  @Test
  fun loadEvent_whileChecklistUpdatePending_isIgnoredUntilSaved() = runTest {
    val update = CompletableDeferred<Unit>()
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.updateEventTasks(any(), any(), any(), any())).doSuspendableAnswer {
      Result.success(update.await())
    }
    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)
    assertTrue(viewModel.uiState.value.isUpdatingTasks)
    assertTrue(viewModel.uiState.value.isBusy)
    viewModel.loadEvent("event-123", refresh = true)
    verify(eventsRepository, times(1)).getEvent("user123", "event-123")

    update.complete(Unit)
    assertFalse(viewModel.uiState.value.isUpdatingTasks)
    viewModel.loadEvent("event-123", refresh = true)
    verify(eventsRepository, times(2)).getEvent("user123", "event-123")
  }

  @Test
  fun toggleTask_saveFails_revertsEventAndSetsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
        .thenReturn(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertEquals(EventPlanningError.STORAGE_FAILURE, state.error)
  }

  @Test
  fun toggleTask_cancellationException_cancelsCoroutineWithoutSettingError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
        .thenThrow(CancellationException("cancelled"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")
    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertNull(state.error)
  }

  @Test
  fun toggleTask_unexpectedException_revertsEventAndSetsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
        .thenThrow(RuntimeException("Unexpected save error"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertEquals(EventPlanningError.FAILED_TO_EDIT_EVENT, state.error)
  }

  @Test
  fun clearError_clearsErrorFromState() = runTest {
    val viewModel = createViewModel()
    viewModel.deleteEvent() // sets NO_EVENT_TO_EDIT error

    assertEquals(EventPlanningError.NO_EVENT_TO_EDIT, viewModel.uiState.value.error)

    viewModel.clearError()
    assertNull(viewModel.uiState.value.error)
  }

  // Group sessions

  /** Stores a session whose members keep exactly the given stored order. */
  private fun seedSession(
      memberIds: List<String> = listOf("host"),
      id: String = SESSION_ID,
      createdBy: String = "host",
  ): GroupSession {
    val session = sampleGroupSession(id = id, createdBy = createdBy, memberIds = memberIds)
    runBlocking { groupSessionsRepository.saveSession(createdBy, session).getOrThrow() }
    return session
  }

  /** Returns a mock group repository whose getSession gives [result]. */
  private fun groupRepositoryReturning(result: Result<GroupSession>): GroupSessionsRepository {
    val repository: GroupSessionsRepository = mock()
    runBlocking { whenever(repository.getSession(any())).thenReturn(result) }
    return repository
  }

  /** A group session is loaded into the state with no error. */
  @Test
  fun loadEvent_groupSession_exposesSession() = runTest {
    val session = seedSession()
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(session, state.event)
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  /** Members are listed host first, then in stored order, with the host flagged. */
  @Test
  fun loadEvent_groupSession_membersAreHostFirstThenStoredOrder() = runTest {
    seedSession(memberIds = listOf("zoe", "host", "adam"))
    profileRepository.setUsername("host", "Hosty")
    profileRepository.setUsername("zoe", "Zoe")
    profileRepository.setUsername("adam", "Adam")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    assertEquals(
        listOf(
            EventMember("host", "Hosty", isHost = true),
            EventMember("zoe", "Zoe", isHost = false),
            EventMember("adam", "Adam", isHost = false),
        ),
        viewModel.uiState.value.members,
    )
  }

  /** A host-only session has exactly one member, flagged as host. */
  @Test
  fun loadEvent_groupSessionHostOnly_hasSingleHostMember() = runTest {
    seedSession(memberIds = listOf("host"))
    profileRepository.setUsername("host", "Hosty")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    assertEquals(listOf(EventMember("host", "Hosty", true)), viewModel.uiState.value.members)
  }

  /** The signed-in user's own row is flagged, and no other row is. */
  @Test
  fun loadEvent_groupSessionCurrentUserIsMember_flagsOnlyTheirRow() = runTest {
    seedSession(memberIds = listOf("host", "user123", "zoe"))
    profileRepository.setUsername("host", "Hosty")
    profileRepository.setUsername("user123", "Me")
    profileRepository.setUsername("zoe", "Zoe")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    assertEquals(
        listOf(
            EventMember("host", "Hosty", isHost = true, isCurrentUser = false),
            EventMember("user123", "Me", isHost = false, isCurrentUser = true),
            EventMember("zoe", "Zoe", isHost = false, isCurrentUser = false),
        ),
        viewModel.uiState.value.members,
    )
  }

  /** When the signed-in user hosts the session, the host row is both host and current user. */
  @Test
  fun loadEvent_groupSessionCurrentUserIsHost_hostRowIsCurrentUser() = runTest {
    seedSession(createdBy = "user123", memberIds = listOf("user123", "zoe"))
    profileRepository.setUsername("user123", "Me")
    profileRepository.setUsername("zoe", "Zoe")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val host = viewModel.uiState.value.members.first()
    assertEquals("user123", host.userId)
    assertTrue(host.isHost)
    assertTrue(host.isCurrentUser)
    assertFalse(viewModel.uiState.value.members[1].isCurrentUser)
  }

  /** A member without a profile gets a null username and no error. */
  @Test
  fun loadEvent_groupSessionMissingProfile_usernameIsNull() = runTest {
    seedSession(memberIds = listOf("host", "bob"))
    profileRepository.setUsername("host", "Hosty")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    val bob = state.members.single { it.userId == "bob" }
    assertNull(bob.username)
    assertFalse(bob.isUnavailable)
    assertNull(state.error)
  }

  /** A blank or whitespace username is shown as missing. */
  @Test
  fun loadEvent_groupSessionBlankUsername_usernameIsNull() = runTest {
    seedSession(memberIds = listOf("host", "bob", "carl"))
    profileRepository.setUsername("bob", "")
    profileRepository.setUsername("carl", "   ")
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val members = viewModel.uiState.value.members
    assertNull(members.single { it.userId == "bob" }.username)
    assertNull(members.single { it.userId == "carl" }.username)
    assertFalse(members.any { it.isUnavailable })
  }

  /** A failed or throwing profile lookup hides only that username and sets no error. */
  @Test
  fun loadEvent_groupSessionFailedProfile_doesNotSetError() = runTest {
    seedSession(memberIds = listOf("host", "bob", "carl"))
    profileRepository.setUsername("host", "Hosty")
    profileRepository.enqueue(
        "bob",
        Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)),
    )
    profileRepository.throwFor("carl", RuntimeException("boom"))
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(listOf("Hosty", null, null), state.members.map { it.username })
    assertEquals(listOf(false, true, true), state.members.map { it.isUnavailable })
    assertEquals(3, state.members.size)
    assertNull(state.error)
    assertFalse(state.isLoading)
  }

  /** Each distinct member is looked up exactly once. */
  @Test
  fun loadEvent_groupSession_looksUpEachMemberOnce() = runTest {
    seedSession(memberIds = listOf("host", "bob", "carl"))
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    assertEquals(listOf("bob", "carl", "host"), profileRepository.getProfileUserIds.sorted())
  }

  /** While a profile lookup is pending the screen stays loading and shows no session yet. */
  @Test
  fun loadEvent_groupSessionLookupPending_staysLoadingUntilDone() = runTest {
    val session = seedSession(memberIds = listOf("host", "bob"))
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.setUsername("host", "Hosty")
    profileRepository.hold("bob", pending)
    val viewModel = createViewModel()

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val loading = viewModel.uiState.value
    assertTrue(loading.isLoading)
    assertNull(loading.event)
    assertTrue(loading.members.isEmpty())

    pending.complete(Result.success(UserProfile(uid = "bob", username = "Bob")))

    val done = viewModel.uiState.value
    assertFalse(done.isLoading)
    assertEquals(session, done.event)
    assertEquals(listOf("Hosty", "Bob"), done.members.map { it.username })
    assertNull(done.error)
  }

  /** A missing session maps to the event-not-found error. */
  @Test
  fun loadEvent_groupSessionNotFound_exposesNotFound() = runTest {
    val viewModel = createViewModel()

    viewModel.loadEvent("unknown", EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.EVENT_NOT_FOUND, state.error)
    assertNull(state.event)
    assertTrue(state.members.isEmpty())
    assertFalse(state.isLoading)
  }

  /** A storage failure maps to the storage-failure error. */
  @Test
  fun loadEvent_groupSessionStorageFailure_exposesStorageFailure() = runTest {
    val groups =
        groupRepositoryReturning(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))
    val viewModel = createViewModel(groups)

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.STORAGE_FAILURE, state.error)
    assertNull(state.event)
    assertTrue(state.members.isEmpty())
    assertFalse(state.isLoading)
  }

  /** Any other failure maps to the generic load error. */
  @Test
  fun loadEvent_groupSessionGenericFailure_exposesFailedToLoad() = runTest {
    val groups = groupRepositoryReturning(Result.failure(IllegalStateException("boom")))
    val viewModel = createViewModel(groups)

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.FAILED_TO_LOAD_EVENT, state.error)
    assertNull(state.event)
    assertTrue(state.members.isEmpty())
    assertFalse(state.isLoading)
  }

  /** Loading a group session while signed out fails without reading the session. */
  @Test
  fun loadEvent_groupSessionSignedOut_failsWithoutReading() = runTest {
    currentUserFlow.value = null
    val groups: GroupSessionsRepository = mock()
    val viewModel = createViewModel(groups)

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, state.error)
    assertFalse(state.isLoading)
    assertNull(state.event)
    verifyNoInteractions(groups)
  }

  /** The same group session is not fetched again unless a refresh is requested. */
  @Test
  fun loadEvent_groupSessionSameIdAndKind_reloadsOnlyWithRefresh() = runTest {
    val session = seedSession()
    val groups = groupRepositoryReturning(Result.success(session))
    val viewModel = createViewModel(groups)

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)
    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)
    verify(groups, times(1)).getSession(SESSION_ID)

    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION, refresh = true)
    verify(groups, times(2)).getSession(SESSION_ID)
  }

  /** A personal event with the same id as the displayed session still loads and clears members. */
  @Test
  fun loadEvent_personalAfterGroupSession_loadsAndClearsMembers() = runTest {
    seedSession(memberIds = listOf("host", "bob"), id = "event-123")
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    val viewModel = createViewModel()
    viewModel.loadEvent("event-123", EventKind.GROUP_SESSION)
    assertEquals(2, viewModel.uiState.value.members.size)

    viewModel.loadEvent("event-123", EventKind.PERSONAL)

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertTrue(state.members.isEmpty())
    assertNull(state.error)
    verify(eventsRepository, times(1)).getEvent("user123", "event-123")
  }

  /** A loaded personal event has no members and never touches group or profile repositories. */
  @Test
  fun loadEvent_personalEvent_hasNoMembersAndSkipsGroupAndProfile() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    val groups: GroupSessionsRepository = mock()
    val viewModel = createViewModel(groups)

    viewModel.loadEvent("event-123")

    assertEquals(sampleEvent, viewModel.uiState.value.event)
    assertTrue(viewModel.uiState.value.members.isEmpty())
    verifyNoInteractions(groups)
    assertTrue(profileRepository.getProfileUserIds.isEmpty())
  }

  /** Deleting while a group session is shown does nothing at all. */
  @Test
  fun deleteEvent_groupSessionLoaded_isIgnored() = runTest {
    val session = seedSession(memberIds = listOf("host", "bob"))
    val viewModel = createViewModel()
    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)
    val before = viewModel.uiState.value

    viewModel.deleteEvent()

    val state = viewModel.uiState.value
    assertEquals(before, state)
    assertEquals(session, state.event)
    assertNull(state.error)
    assertFalse(state.isEventDeleted)
    assertFalse(state.isDeleting)
    verify(eventsRepository, never()).deleteEvent(any(), any())
    assertEquals(session, groupSessionsRepository.getSession(SESSION_ID).getOrThrow())
  }

  /** Toggling a task while a group session is shown does nothing. */
  @Test
  fun toggleTask_groupSessionLoaded_isIgnored() = runTest {
    val session = seedSession()
    val viewModel = createViewModel()
    viewModel.loadEvent(SESSION_ID, EventKind.GROUP_SESSION)

    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertEquals(session, state.event)
    assertNull(state.error)
    verify(eventsRepository, never()).updateEventTasks(any(), any(), any(), any())
  }

  @Test
  fun factory_createsViewModel_forValidClass() {
    val factory =
        EventDetailsViewModelFactory(
            authRepository,
            eventsRepository,
            groupSessionsRepository,
            profileRepository,
            clock,
        )
    val viewModel = factory.create(EventDetailsViewModel::class.java)
    assertNotNull(viewModel)
  }

  @Test
  fun factory_rejectsUnsupportedClass() {
    val factory =
        EventDetailsViewModelFactory(
            authRepository,
            eventsRepository,
            groupSessionsRepository,
            profileRepository,
            clock,
        )
    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }
    assertTrue(exception.message!!.contains("Unsupported ViewModel"))
  }
}
