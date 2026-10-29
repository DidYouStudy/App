// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.eventplanning

import com.android.sample.model.event.Event
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests of loading, editing, deleting and the next-day rule for end times. Adding (without the
 * next-day rule), the form, tasks and subject suggestions are covered by
 * [EventPlanningViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPlanningViewModelEditTest : EventPlanningViewModelTestBase() {

  private val subjectFlows = mutableMapOf<String, MutableStateFlow<Result<List<String>>>>()
  private var idCounter = 0

  private val createdAt = Instant.parse("2026-10-20T09:00:00Z")

  @Before
  fun setUp() {
    authRepository = mock()
    eventsRepository = mock()
    whenever(authRepository.currentUser).thenReturn(currentUserFlow)
    whenever(eventsRepository.observeSubjects(any())).thenAnswer {
      subjectsOf(it.getArgument<String>(0))
    }
    whenever(eventsRepository.newEventId()).thenAnswer { "new-event-${++idCounter}" }
    runBlocking {
      whenever(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit))
      whenever(eventsRepository.getEvent(any(), any()))
          .thenReturn(Result.failure(EventsException(EventsError.NOT_FOUND)))
      whenever(eventsRepository.deleteEvent(any(), any())).thenReturn(Result.success(Unit))
    }
    currentUserFlow.value = account("user123")
  }

  /** The subject suggestions the mocked repository emits for [ownerId]; tests can change them. */
  private fun subjectsOf(ownerId: String) =
      subjectFlows.getOrPut(ownerId) { MutableStateFlow(Result.success(emptyList())) }

  private fun storedEvent(
      id: String = "stored-1",
      type: EventType = EventType.LAB,
      subject: String? = "CompSec",
      location: String? = "INF 1",
      description: String? = "Bring laptop",
      startsAt: Instant = this.startsAt,
      endsAt: Instant = this.endsAt,
  ) =
      Event(
          id = id,
          ownerId = "user123",
          type = type,
          subject = subject,
          startsAt = startsAt,
          endsAt = endsAt,
          createdAt = createdAt,
          updatedAt = createdAt,
          location = location,
          description = description,
          tasks = tasks,
      )

  /** A view model with the stored event already loaded into its form. */
  private suspend fun loadedViewModel(event: Event = storedEvent()): EventPlanningViewModel {
    whenever(eventsRepository.getEvent("user123", event.id)).thenReturn(Result.success(event))
    val viewModel = createViewModel()
    viewModel.loadEvent(event.id)
    return viewModel
  }

  // ---------------------------------------------------------------- loadEvent

  @Test
  fun loadEvent_fillsFormFromStoredEventInLocalTime() = runTest {
    val viewModel = loadedViewModel()

    val state = viewModel.uiState.value
    assertEquals(EventType.LAB, state.type)
    assertEquals("CompSec", state.subject)
    assertEquals(LocalDate.of(2026, 10, 27), state.date)
    assertEquals(LocalTime.of(9, 0), state.startTime)
    assertEquals(LocalTime.of(11, 0), state.endTime)
    assertEquals("INF 1", state.location)
    assertEquals("Bring laptop", state.description)
    assertEquals(tasks, state.tasks)
    assertTrue(state.isEditing)
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun loadEvent_eventWithoutOptionalFields_fillsEmptyStrings() = runTest {
    val viewModel =
        loadedViewModel(
            storedEvent(type = EventType.OTHER, subject = null, location = null, description = null)
        )

    val state = viewModel.uiState.value
    assertEquals("", state.subject)
    assertEquals("", state.location)
    assertEquals("", state.description)
    assertTrue(state.isEditing)
  }

  @Test
  fun loadEvent_withoutSignedInUser_reportsErrorAndDoesNotLoad() = runTest {
    currentUserFlow.value = null
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")

    assertEquals(EventPlanningViewModel.NO_SIGNED_IN_USER_ERROR, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
    verify(eventsRepository, never()).getEvent(any(), any())
  }

  @Test
  fun loadEvent_unknownId_reportsEventNotFound() = runTest {
    val viewModel = createViewModel()

    viewModel.loadEvent("missing")

    val state = viewModel.uiState.value
    assertEquals(EventPlanningViewModel.EVENT_NOT_FOUND_ERROR, state.error)
    assertFalse(state.isEditing)
    assertFalse(state.isLoading)
  }

  @Test
  fun loadEvent_otherFailureWithoutMessage_usesDefaultMessage() = runTest {
    whenever(eventsRepository.getEvent(any(), any()))
        .thenReturn(Result.failure(RuntimeException(null as String?)))
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")

    assertEquals(EventPlanningViewModel.FAILED_TO_LOAD_EVENT_ERROR, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun loadEvent_repositoryThrowsWithMessage_exposesMessage() = runTest {
    whenever(eventsRepository.getEvent(any(), any())).thenThrow(IllegalStateException("boom"))
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")

    assertEquals("boom", viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun loadEvent_repositoryThrowsWithoutMessage_usesDefaultMessage() = runTest {
    whenever(eventsRepository.getEvent(any(), any()))
        .thenThrow(object : RuntimeException(null as String?) {})
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")

    assertEquals(EventPlanningViewModel.FAILED_TO_LOAD_EVENT_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun loadEvent_cancelled_isNotReportedAsErrorAndStopsLoading() = runTest {
    whenever(eventsRepository.getEvent(any(), any())).thenThrow(CancellationException("cancelled"))
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun loadEvent_isLoadingUntilTheEventArrives() = runTest {
    val gate = CompletableDeferred<Unit>()
    whenever(eventsRepository.getEvent(any(), any())).doSuspendableAnswer {
      gate.await()
      Result.success(storedEvent())
    }
    val viewModel = createViewModel()

    viewModel.loadEvent("stored-1")
    assertTrue(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.isBusy)

    gate.complete(Unit)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.isEditing)
  }

  // ---------------------------------------------------------------- editEvent

  @Test
  fun editEvent_withoutLoadedEvent_reportsErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()

    viewModel.editEvent()

    assertEquals(EventPlanningViewModel.NO_EVENT_TO_EDIT_ERROR, viewModel.uiState.value.error)
    assertNothingSaved()
  }

  @Test
  fun editEvent_replacesTheEventKeepingIdAndCreationTime() = runTest {
    val viewModel = loadedViewModel()
    viewModel.setSubject("  Algebra  ")
    viewModel.setEndTime(LocalTime.of(12, 0))
    viewModel.setLocation("")

    viewModel.editEvent()

    val saved = savedEvents().single()
    assertEquals("stored-1", saved.id)
    assertEquals("user123", saved.ownerId)
    assertEquals("Algebra", saved.subject)
    assertEquals(startsAt, saved.startsAt)
    assertEquals(Instant.parse("2026-10-27T11:00:00Z"), saved.endsAt)
    assertNull(saved.location)
    assertEquals(createdAt, saved.createdAt)
    assertEquals(now, saved.updatedAt)
    verify(eventsRepository, never()).newEventId()
  }

  @Test
  fun editEvent_success_resetsFormKeepsSubjectsAndSignalsEdited() = runTest {
    subjectsOf("user123").value = Result.success(listOf("CompSec"))
    val viewModel = loadedViewModel()

    viewModel.editEvent()

    val state = viewModel.uiState.value
    assertTrue(state.isEventEdited)
    assertFalse(state.isEventAdded)
    assertFalse(state.isEditing)
    assertFalse(state.isSaving)
    assertNull(state.error)
    assertEquals("", state.subject)
    assertNull(state.date)
    assertEquals(listOf("CompSec"), state.recommendedSubjects)
  }

  @Test
  fun editEvent_afterSuccess_noEventIsLoadedAnymore() = runTest {
    val viewModel = loadedViewModel()
    viewModel.editEvent()

    viewModel.editEvent()

    assertEquals(EventPlanningViewModel.NO_EVENT_TO_EDIT_ERROR, viewModel.uiState.value.error)
    verify(eventsRepository, times(1)).saveEvent(any())
  }

  @Test
  fun editEvent_invalidForm_exposesValidationMessage() = runTest {
    val viewModel = loadedViewModel()
    viewModel.setSubject("   ")

    viewModel.editEvent()

    assertTrue(viewModel.uiState.value.error!!.contains("Subject"))
    assertNothingSaved()
  }

  @Test
  fun editEvent_failure_keepsFormSoItCanBeRetriedUnderTheSameId() = runTest {
    whenever(eventsRepository.saveEvent(any()))
        .thenReturn(
            Result.failure(EventsException(EventsError.STORAGE_FAILURE)),
            Result.success(Unit),
        )
    val viewModel = loadedViewModel()
    viewModel.setSubject("Algebra")

    viewModel.editEvent()

    val failed = viewModel.uiState.value
    assertEquals(EventPlanningViewModel.STORAGE_FAILURE_ERROR, failed.error)
    assertTrue(failed.isEditing)
    assertFalse(failed.isEventEdited)
    assertEquals("Algebra", failed.subject)

    viewModel.editEvent()

    assertEquals(listOf("stored-1", "stored-1"), savedEvents(count = 2).map { it.id })
    assertTrue(viewModel.uiState.value.isEventEdited)
  }

  @Test
  fun editEvent_unknownFailure_usesEditSpecificDefaultMessage() = runTest {
    whenever(eventsRepository.saveEvent(any()))
        .thenReturn(Result.failure(EventsException(EventsError.UNKNOWN)))
    val viewModel = loadedViewModel()

    viewModel.editEvent()

    assertEquals(EventPlanningViewModel.FAILED_TO_EDIT_EVENT_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun addEvent_afterLoadingAnEvent_createsANewEventAndKeepsTheOriginal() = runTest {
    val viewModel = loadedViewModel()

    viewModel.addEvent()

    val added = savedEvents().single()
    assertEquals("new-event-1", added.id)
    assertNotEquals("stored-1", added.id)
    assertEquals(now, added.createdAt)
    assertTrue(viewModel.uiState.value.isEventAdded)
    assertFalse(viewModel.uiState.value.isEventEdited)
    verify(eventsRepository, never()).deleteEvent(any(), any())
  }

  @Test
  fun resetForm_forgetsTheLoadedEvent() = runTest {
    val viewModel = loadedViewModel()

    viewModel.resetForm()
    viewModel.editEvent()

    assertFalse(viewModel.uiState.value.isEditing)
    assertEquals(EventPlanningViewModel.NO_EVENT_TO_EDIT_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun saving_whileLoading_isIgnored() = runTest {
    val gate = CompletableDeferred<Unit>()
    whenever(eventsRepository.getEvent(any(), any())).doSuspendableAnswer {
      gate.await()
      Result.success(storedEvent())
    }
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.loadEvent("stored-1")

    viewModel.addEvent()

    assertNothingSaved()
    assertNull(viewModel.uiState.value.error)
    gate.complete(Unit)
    advanceUntilIdle()
  }

  // ---------------------------------------------------------------- deleteEvent

  @Test
  fun deleteEvent_withoutSignedInUser_reportsErrorAndDoesNotDelete() = runTest {
    currentUserFlow.value = null
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertEquals(EventPlanningViewModel.NO_SIGNED_IN_USER_ERROR, viewModel.uiState.value.error)
    verify(eventsRepository, never()).deleteEvent(any(), any())
  }

  @Test
  fun deleteEvent_removesTheEventResetsFormAndSignalsDeleted() = runTest {
    subjectsOf("user123").value = Result.success(listOf("CompSec"))
    val viewModel = loadedViewModel()

    viewModel.deleteEvent("stored-1")

    verify(eventsRepository, times(1)).deleteEvent("user123", "stored-1")
    val state = viewModel.uiState.value
    assertTrue(state.isEventDeleted)
    assertFalse(state.isDeleting)
    assertFalse(state.isEditing)
    assertEquals("", state.subject)
    assertNull(state.error)
    assertEquals(
        listOf("CompSec"),
        state.recommendedSubjects,
    ) // deleting events keeps their subjects
  }

  @Test
  fun deleteEvent_afterwards_noEventIsLoadedAnymore() = runTest {
    val viewModel = loadedViewModel()
    viewModel.deleteEvent("stored-1")

    viewModel.editEvent()

    assertEquals(EventPlanningViewModel.NO_EVENT_TO_EDIT_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun deleteEvent_missingEvent_stillSucceeds() = runTest {
    val viewModel = createViewModel()

    viewModel.deleteEvent("missing")

    assertTrue(viewModel.uiState.value.isEventDeleted)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun deleteEvent_repositoryFailure_exposesUserMessageAndKeepsForm() = runTest {
    val viewModel = loadedViewModel()
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenReturn(Result.failure(EventsException(EventsError.PERMISSION_DENIED)))

    viewModel.deleteEvent("stored-1")

    val state = viewModel.uiState.value
    assertEquals(EventPlanningViewModel.PERMISSION_DENIED_ERROR, state.error)
    assertFalse(state.isEventDeleted)
    assertFalse(state.isDeleting)
    assertTrue(state.isEditing)
  }

  @Test
  fun deleteEvent_unknownFailure_usesDeleteSpecificDefaultMessage() = runTest {
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenReturn(Result.failure(EventsException(EventsError.UNKNOWN)))
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertEquals(
        EventPlanningViewModel.FAILED_TO_DELETE_EVENT_ERROR,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun deleteEvent_otherFailureWithMessage_exposesMessage() = runTest {
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenReturn(Result.failure(IllegalStateException("disk full")))
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertEquals("disk full", viewModel.uiState.value.error)
  }

  @Test
  fun deleteEvent_repositoryThrowsWithMessage_exposesMessage() = runTest {
    whenever(eventsRepository.deleteEvent(any(), any())).thenThrow(IllegalStateException("boom"))
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertEquals("boom", viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isDeleting)
  }

  @Test
  fun deleteEvent_repositoryThrowsWithoutMessage_usesDefaultMessage() = runTest {
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenThrow(object : RuntimeException(null as String?) {})
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertEquals(
        EventPlanningViewModel.FAILED_TO_DELETE_EVENT_ERROR,
        viewModel.uiState.value.error,
    )
  }

  @Test
  fun deleteEvent_cancelled_isNotReportedAsErrorAndStopsDeleting() = runTest {
    whenever(eventsRepository.deleteEvent(any(), any()))
        .thenThrow(CancellationException("cancelled"))
    val viewModel = createViewModel()

    viewModel.deleteEvent("stored-1")

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isEventDeleted)
    assertFalse(viewModel.uiState.value.isDeleting)
  }

  @Test
  fun deleteEvent_whileDeleting_isIgnoredAndBlocksSaving() = runTest {
    val gate = CompletableDeferred<Unit>()
    whenever(eventsRepository.deleteEvent(any(), any())).doSuspendableAnswer {
      gate.await()
      Result.success(Unit)
    }
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.deleteEvent("stored-1")
    assertTrue(viewModel.uiState.value.isDeleting)

    viewModel.deleteEvent("stored-1") // ignored: a delete is already running
    viewModel.addEvent() // ignored too
    verify(eventsRepository, times(1)).deleteEvent(any(), any())
    assertNothingSaved()

    gate.complete(Unit)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isEventDeleted)
  }

  @Test
  fun deleteEvent_whileSaving_isIgnored() = runTest {
    val gate = CompletableDeferred<Unit>()
    whenever(eventsRepository.saveEvent(any())).doSuspendableAnswer {
      gate.await()
      Result.success(Unit)
    }
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addEvent()
    assertTrue(viewModel.uiState.value.isSaving)

    viewModel.deleteEvent("stored-1")

    verify(eventsRepository, never()).deleteEvent(any(), any())
    gate.complete(Unit)
    advanceUntilIdle()
  }

  // ---------------------------------------------------------------- results

  @Test
  fun onEventEditedHandled_clearsTheEditedFlag() = runTest {
    val viewModel = loadedViewModel()
    viewModel.editEvent()
    assertTrue(viewModel.uiState.value.isEventEdited)

    viewModel.onEventEditedHandled()

    assertFalse(viewModel.uiState.value.isEventEdited)
  }

  @Test
  fun onEventDeletedHandled_clearsTheDeletedFlag() = runTest {
    val viewModel = createViewModel()
    viewModel.deleteEvent("stored-1")
    assertTrue(viewModel.uiState.value.isEventDeleted)

    viewModel.onEventDeletedHandled()

    assertFalse(viewModel.uiState.value.isEventDeleted)
  }

  @Test
  fun editingFormAfterEditOrDelete_clearsTheResultFlags() = runTest {
    val viewModel = loadedViewModel()
    viewModel.editEvent()
    assertTrue(viewModel.uiState.value.isEventEdited)
    viewModel.setLocation("Room 2")
    assertFalse(viewModel.uiState.value.isEventEdited)

    viewModel.deleteEvent("stored-1")
    assertTrue(viewModel.uiState.value.isEventDeleted)
    viewModel.setLocation("Room 3")
    assertFalse(viewModel.uiState.value.isEventDeleted)
  }

  // ---------------------------------------------------------------- events that end the next day

  @Test
  fun addEvent_endTimeBeforeStartTime_endsTheNextDay() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm(start = LocalTime.of(22, 0), end = LocalTime.of(1, 0))

    viewModel.addEvent()

    val saved = savedEvents().single()
    assertEquals(Instant.parse("2026-10-27T21:00:00Z"), saved.startsAt) // 22:00 on the 27th
    assertEquals(Instant.parse("2026-10-28T00:00:00Z"), saved.endsAt) // 01:00 on the 28th
    assertTrue(viewModel.uiState.value.isEventAdded)
  }

  @Test
  fun addEvent_endTimeEqualToStartTime_isStillRejected() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm(start = LocalTime.of(9, 0), end = LocalTime.of(9, 0))

    viewModel.addEvent()

    assertEquals("End must be after start", viewModel.uiState.value.error)
    assertNothingSaved()
  }

  @Test
  fun addEvent_endTimeAfterStartTime_staysOnTheSameDay() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm(start = LocalTime.of(9, 0), end = LocalTime.of(11, 0))

    viewModel.addEvent()

    val saved = savedEvents().single()
    assertEquals(startsAt, saved.startsAt)
    assertEquals(endsAt, saved.endsAt)
  }

  @Test
  fun addEvent_overnightAcrossTheEndOfSummerTime_usesLocalClockTimes() = runTest {
    val viewModel = createViewModel()
    // Clocks go back from 03:00 to 02:00 on 25 October 2026: 22:00 is UTC+2, 04:00 is UTC+1.
    viewModel.fillValidForm(
        date = LocalDate.of(2026, 10, 24),
        start = LocalTime.of(22, 0),
        end = LocalTime.of(4, 0),
    )

    viewModel.addEvent()

    val saved = savedEvents().single()
    assertEquals(Instant.parse("2026-10-24T20:00:00Z"), saved.startsAt)
    assertEquals(Instant.parse("2026-10-25T03:00:00Z"), saved.endsAt)
  }

  @Test
  fun editEvent_changedToOvernight_endsTheNextDay() = runTest {
    val viewModel = loadedViewModel()
    viewModel.setStartTime(LocalTime.of(23, 0))
    viewModel.setEndTime(LocalTime.of(7, 0))

    viewModel.editEvent()

    val saved = savedEvents().single()
    assertEquals("stored-1", saved.id)
    assertEquals(Instant.parse("2026-10-27T22:00:00Z"), saved.startsAt)
    assertEquals(Instant.parse("2026-10-28T06:00:00Z"), saved.endsAt)
  }

  @Test
  fun editEvent_storedOvernightEvent_roundTripsUnchanged() = runTest {
    val overnight =
        storedEvent(
            startsAt = Instant.parse("2026-10-27T21:00:00Z"),
            endsAt = Instant.parse("2026-10-28T00:00:00Z"),
        )
    val viewModel = loadedViewModel(overnight)
    assertEquals(LocalDate.of(2026, 10, 27), viewModel.uiState.value.date)
    assertEquals(LocalTime.of(22, 0), viewModel.uiState.value.startTime)
    assertEquals(LocalTime.of(1, 0), viewModel.uiState.value.endTime)

    viewModel.editEvent()

    val saved = savedEvents().single()
    assertEquals(overnight.startsAt, saved.startsAt)
    assertEquals(overnight.endsAt, saved.endsAt)
  }
}
