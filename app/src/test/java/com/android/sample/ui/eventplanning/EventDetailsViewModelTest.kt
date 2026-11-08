// Co-authored-by: Gemini AI Agent
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
import com.android.sample.model.event.PersonalEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock()
    eventsRepository = mock()
    whenever(authRepository.currentUser).thenReturn(currentUserFlow)
    currentUserFlow.value = user
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() = EventDetailsViewModel(authRepository, eventsRepository, clock)

  @Test
  fun init_userFlowFailsWithMessage_exposesErrorInState() = runTest {
    whenever(authRepository.currentUser)
        .thenReturn(flow<UserAccount?> { throw RuntimeException("Auth error") })

    val viewModel = createViewModel()
    assertEquals("Auth error", viewModel.uiState.value.error)
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
    assertEquals(EventDetailsViewModel.NO_SIGNED_IN_USER_ERROR, state.error)
  }

  @Test
  fun loadEvent_repositoryFailure_exposesMappedError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.failure(EventsException(EventsError.NOT_FOUND)))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    val state = viewModel.uiState.value
    assertNull(state.event)
    assertEquals(EventDetailsViewModel.EVENT_NOT_FOUND_ERROR, state.error)
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
    assertEquals("Unexpected load error", state.error)
  }

  @Test
  fun deleteEvent_noEventLoaded_setsError() = runTest {
    val viewModel = createViewModel()
    viewModel.deleteEvent()

    assertEquals(EventDetailsViewModel.NO_EVENT_TO_DELETE_ERROR, viewModel.uiState.value.error)
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
    assertEquals(EventDetailsViewModel.NO_SIGNED_IN_USER_ERROR, state.error)
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
    assertEquals(EventDetailsViewModel.PERMISSION_DENIED_ERROR, state.error)
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
    assertEquals("Unexpected delete error", state.error)
  }

  @Test
  fun toggleTask_validIndex_togglesCompletionAndSaves() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val updatedEvent = viewModel.uiState.value.event
    assertNotNull(updatedEvent)
    assertTrue(updatedEvent!!.tasks[0].isCompleted)
    assertEquals(now, updatedEvent.tasks[0].completedAt)

    val captor = argumentCaptor<PersonalEvent>()
    verify(eventsRepository).saveEvent(captor.capture())
    assertEquals(updatedEvent, captor.firstValue)

    // Toggle back
    viewModel.toggleTask(0)
    val toggledBackEvent = viewModel.uiState.value.event
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

    verify(eventsRepository, never()).saveEvent(any())
  }

  @Test
  fun toggleTask_saveFails_revertsEventAndSetsError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.saveEvent(any()))
        .thenReturn(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertEquals(EventDetailsViewModel.STORAGE_FAILURE_ERROR, state.error)
  }

  @Test
  fun toggleTask_cancellationException_cancelsCoroutineWithoutSettingError() = runTest {
    whenever(eventsRepository.getEvent("user123", "event-123"))
        .thenReturn(Result.success(sampleEvent))
    whenever(eventsRepository.saveEvent(any())).thenThrow(CancellationException("cancelled"))

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
    whenever(eventsRepository.saveEvent(any())).thenThrow(RuntimeException("Unexpected save error"))

    val viewModel = createViewModel()
    viewModel.loadEvent("event-123")

    viewModel.toggleTask(0)

    val state = viewModel.uiState.value
    assertEquals(sampleEvent, state.event)
    assertEquals("Unexpected save error", state.error)
  }

  @Test
  fun clearError_clearsErrorFromState() = runTest {
    val viewModel = createViewModel()
    viewModel.deleteEvent() // sets NO_EVENT_TO_DELETE_ERROR

    assertEquals(EventDetailsViewModel.NO_EVENT_TO_DELETE_ERROR, viewModel.uiState.value.error)

    viewModel.clearError()
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun factory_createsViewModel_forValidClass() {
    val factory = EventDetailsViewModelFactory(authRepository, eventsRepository, clock)
    val viewModel = factory.create(EventDetailsViewModel::class.java)
    assertNotNull(viewModel)
  }

  @Test
  fun factory_rejectsUnsupportedClass() {
    val factory = EventDetailsViewModelFactory(authRepository, eventsRepository, clock)
    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }
    assertTrue(exception.message!!.contains("Unsupported ViewModel"))
  }
}
