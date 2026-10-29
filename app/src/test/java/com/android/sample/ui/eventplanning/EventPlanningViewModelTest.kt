// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.eventplanning

import androidx.lifecycle.ViewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.EventsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.verify

/**
 * Plain JVM tests for authenticated-user observation, subject suggestions, form and task updates,
 * event validation and saving, repository failures, and ViewModel factory behavior. The ViewModel
 * uses no Android framework class, so no Robolectric runner is needed. Only `Dispatchers.Main` has
 * to be replaced because `viewModelScope` runs on it.
 *
 * The repositories are Mockito mocks. `setUp` gives the events repository a happy-path behavior
 * (saves succeed, no subjects, the next event ID is "event-1"); tests re-stub what they need.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPlanningViewModelTest {

  /** Unsupported ViewModel type used to verify that the factory rejects unrelated requests. */
  private class OtherViewModel : ViewModel()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val currentUserFlow = MutableStateFlow<UserAccount?>(null)
  private lateinit var authRepository: AuthRepository
  private lateinit var eventsRepository: EventsRepository

  // Berlin is on UTC+1 on this date (summer time ended on 25 October 2026).
  private val zone = ZoneId.of("Europe/Berlin")
  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val clock = Clock.fixed(now, zone)
  private val date = LocalDate.of(2026, 10, 27)
  private val startTime = LocalTime.of(9, 0)
  private val endTime = LocalTime.of(11, 0)
  private val startsAt = Instant.parse("2026-10-27T08:00:00Z")
  private val endsAt = Instant.parse("2026-10-27T10:00:00Z")

  /** Installs the test dispatcher and configures repository mocks with successful defaults. */
  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock(AuthRepository::class.java)
    eventsRepository = mock(EventsRepository::class.java)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
    `when`(eventsRepository.newEventId()).thenReturn("event-1")
    `when`(eventsRepository.observeSubjects(any())).thenReturn(subjects())
    runBlocking { `when`(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit)) }
    currentUserFlow.value = account("user123")
  }

  /** Restores the process main dispatcher after each test. */
  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Creates a minimal account for authentication-flow tests. */
  private fun account(uid: String) =
      UserAccount(uid = uid, email = null, displayName = null, photoUrl = null)

  /** Creates a one-shot subject flow containing [subjects]. */
  private fun subjects(vararg subjects: String) = flowOf(Result.success(subjects.toList()))

  /** Creates a ViewModel with the configured repository mocks and fixed test clock. */
  private fun createViewModel() = EventPlanningViewModel(authRepository, eventsRepository, clock)

  /** Populates the form with valid study-session details for event-creation tests. */
  private fun EventPlanningViewModel.fillValidForm() {
    setType(EventType.STUDY_SESSION)
    setSubject("Algebra")
    setDate(date)
    setStartTime(startTime)
    setEndTime(endTime)
  }

  /** The events passed to `saveEvent` so far, which must have happened exactly [count] times. */
  private suspend fun savedEvents(count: Int = 1): List<Event> {
    val captor = argumentCaptor<Event>()
    verify(eventsRepository, times(count)).saveEvent(captor.capture())
    return captor.allValues
  }

  // ---------------------------------------------------------------- user observation

  /** A message from a failed authentication flow is surfaced in UI state. */
  @Test
  fun init_userFlowFailsWithMessage_exposesMessage() = runTest {
    `when`(authRepository.currentUser)
        .thenReturn(flow<UserAccount?> { throw RuntimeException("Auth flow error") })

    val viewModel = createViewModel()

    assertEquals("Auth flow error", viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isEventAdded)
  }

  /** A failed authentication flow without a message uses the default observation error. */
  @Test
  fun init_userFlowFailsWithoutMessage_usesDefaultMessage() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(authRepository.currentUser).thenReturn(flow<UserAccount?> { throw exception })

    val viewModel = createViewModel()

    assertEquals(
        EventPlanningViewModel.FAILED_TO_OBSERVE_USER_ERROR,
        viewModel.uiState.value.error,
    )
  }

  /**
   * Event creation is rejected when authentication observation failed before identifying a user.
   */
  @Test
  fun init_userFlowFails_noUserIsKnownAndAddingIsRejected() = runTest {
    `when`(authRepository.currentUser)
        .thenReturn(flow<UserAccount?> { throw RuntimeException("Auth flow error") })
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(EventPlanningViewModel.NO_SIGNED_IN_USER_ERROR, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** Cancellation of authentication observation is propagated without displaying an error. */
  @Test
  fun init_userFlowCancelled_doesNotExposeError() = runTest {
    `when`(authRepository.currentUser)
        .thenReturn(flow<UserAccount?> { throw CancellationException("cancelled") })

    val viewModel = createViewModel()

    assertNull(viewModel.uiState.value.error)
  }

  // ---------------------------------------------------------------- subject suggestions

  /** Subject suggestions follow sign-in changes and update when the current user's list changes. */
  @Test
  fun subjects_followTheSignedInUserAndTheirChanges() = runTest {
    val algebraSubjects = MutableStateFlow(Result.success(listOf("Algebra")))
    `when`(eventsRepository.observeSubjects("user123")).thenReturn(algebraSubjects)
    `when`(eventsRepository.observeSubjects("user456")).thenReturn(subjects("SwEnt"))
    val viewModel = createViewModel()
    assertEquals(listOf("Algebra"), viewModel.uiState.value.recommendedSubjects)

    algebraSubjects.value = Result.success(listOf("CompSec", "Algebra"))
    advanceUntilIdle()
    assertEquals(listOf("CompSec", "Algebra"), viewModel.uiState.value.recommendedSubjects)

    currentUserFlow.value = account("user456")
    advanceUntilIdle()
    assertEquals(listOf("SwEnt"), viewModel.uiState.value.recommendedSubjects)

    currentUserFlow.value = null
    advanceUntilIdle()
    assertEquals(emptyList<String>(), viewModel.uiState.value.recommendedSubjects)
  }

  /** A failed suggestions result leaves the suggestions empty without blocking the form. */
  @Test
  fun subjects_readFailure_leavesListEmptyWithoutError() = runTest {
    `when`(eventsRepository.observeSubjects("user123"))
        .thenReturn(flowOf(Result.failure(EventsException(EventsError.STORAGE_FAILURE))))

    val viewModel = createViewModel()

    assertEquals(emptyList<String>(), viewModel.uiState.value.recommendedSubjects)
    assertNull(viewModel.uiState.value.error)
  }

  // ---------------------------------------------------------------- form

  /** The ViewModel starts with default form values and no pending operation or error. */
  @Test
  fun initialState_hasDefaultFormValues() = runTest {
    val state = createViewModel().uiState.value

    assertEquals(EventType.STUDY_SESSION, state.type)
    assertEquals("", state.subject)
    assertNull(state.date)
    assertNull(state.startTime)
    assertNull(state.endTime)
    assertEquals("", state.location)
    assertEquals("", state.description)
    assertTrue(state.tasks.isEmpty())
    assertTrue(state.recommendedSubjects.isEmpty())
    assertFalse(state.isSaving)
    assertFalse(state.isEventAdded)
    assertNull(state.error)
  }

  /** Only the OTHER event type permits an omitted subject. */
  @Test
  fun subjectIsRequiredExceptForTypeOther() = runTest {
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.isSubjectRequired)

    viewModel.setType(EventType.OTHER)

    assertFalse(viewModel.uiState.value.isSubjectRequired)
  }

  /** Each form setter updates its corresponding state field. */
  @Test
  fun setters_updateEveryFormField() = runTest {
    val viewModel = createViewModel()
    val tasks = listOf(EventTask("Solve exercises 12, 13"), EventTask("Read chapter 3"))

    viewModel.setType(EventType.LAB)
    viewModel.setSubject("CompSec")
    viewModel.setDate(date)
    viewModel.setStartTime(startTime)
    viewModel.setEndTime(endTime)
    viewModel.setLocation("INF 1")
    viewModel.setDescription("Bring laptop")
    viewModel.setTasks(tasks)

    val state = viewModel.uiState.value
    assertEquals(EventType.LAB, state.type)
    assertEquals("CompSec", state.subject)
    assertEquals(date, state.date)
    assertEquals(startTime, state.startTime)
    assertEquals(endTime, state.endTime)
    assertEquals("INF 1", state.location)
    assertEquals("Bring laptop", state.description)
    assertEquals(tasks, state.tasks)
  }

  /** Date and time setters accept null to clear previously selected values. */
  @Test
  fun setters_acceptNullDateAndTimes() = runTest {
    val viewModel = createViewModel()
    viewModel.setDate(date)
    viewModel.setStartTime(startTime)
    viewModel.setEndTime(endTime)

    viewModel.setDate(null)
    viewModel.setStartTime(null)
    viewModel.setEndTime(null)

    assertNull(viewModel.uiState.value.date)
    assertNull(viewModel.uiState.value.startTime)
    assertNull(viewModel.uiState.value.endTime)
  }

  /** Editing a form clears any previous error and event-added signal. */
  @Test
  fun editingForm_clearsPreviousErrorAndAddedFlag() = runTest {
    val viewModel = createViewModel()
    viewModel.addEvent() // nothing filled in -> error
    assertNotNull(viewModel.uiState.value.error)

    viewModel.setSubject("Algebra")

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isEventAdded)
  }

  /** Clearing an error removes it from the current UI state. */
  @Test
  fun clearError_removesDisplayedError() = runTest {
    val viewModel = createViewModel()
    viewModel.addEvent()
    assertNotNull(viewModel.uiState.value.error)

    viewModel.clearError()

    assertNull(viewModel.uiState.value.error)
  }

  // ---------------------------------------------------------------- tasks

  /** Added task text is trimmed and appended as an open checklist item. */
  @Test
  fun addTask_appendsTrimmedOpenTask() = runTest {
    val viewModel = createViewModel()

    viewModel.addTask("  Solve exercises 12, 13  ")
    viewModel.addTask("Read chapter 3")

    assertEquals(
        listOf(EventTask("Solve exercises 12, 13"), EventTask("Read chapter 3")),
        viewModel.uiState.value.tasks,
    )
  }

  /** Blank task text is ignored and does not clear unrelated UI feedback. */
  @Test
  fun addTask_blankText_isIgnored() = runTest {
    val viewModel = createViewModel()
    viewModel.addEvent() // puts an error on screen
    assertNotNull(viewModel.uiState.value.error)

    viewModel.addTask("   ")

    assertTrue(viewModel.uiState.value.tasks.isEmpty())
    assertNotNull(viewModel.uiState.value.error) // untouched: nothing was edited
  }

  /** Removing a valid task index leaves the other tasks in order. */
  @Test
  fun removeTask_removesOnlyTheTaskAtIndex() = runTest {
    val viewModel = createViewModel()
    viewModel.setTasks(listOf(EventTask("a"), EventTask("b"), EventTask("c")))

    viewModel.removeTask(1)

    assertEquals(listOf(EventTask("a"), EventTask("c")), viewModel.uiState.value.tasks)
  }

  /** Removing an invalid task index leaves the checklist unchanged. */
  @Test
  fun removeTask_indexOutsideList_isIgnored() = runTest {
    val viewModel = createViewModel()
    val tasks = listOf(EventTask("a"), EventTask("b"))
    viewModel.setTasks(tasks)

    viewModel.removeTask(-1)
    viewModel.removeTask(2)

    assertEquals(tasks, viewModel.uiState.value.tasks)
  }

  /** Toggling a task records the test clock time and toggling again reopens it. */
  @Test
  fun toggleTask_completesThenReopensTheTask() = runTest {
    val viewModel = createViewModel()
    viewModel.setTasks(listOf(EventTask("a"), EventTask("b")))

    viewModel.toggleTask(1)
    assertNull(viewModel.uiState.value.tasks[0].completedAt)
    assertEquals(now, viewModel.uiState.value.tasks[1].completedAt)

    viewModel.toggleTask(1)
    assertNull(viewModel.uiState.value.tasks[1].completedAt)
  }

  /** Toggling an invalid task index leaves the checklist unchanged. */
  @Test
  fun toggleTask_indexOutsideList_isIgnored() = runTest {
    val viewModel = createViewModel()
    val tasks = listOf(EventTask("a"), EventTask("b"))
    viewModel.setTasks(tasks)

    viewModel.toggleTask(-1)
    viewModel.toggleTask(2)

    assertEquals(tasks, viewModel.uiState.value.tasks)
  }

  // ---------------------------------------------------------------- addEvent: rejected before
  // saving

  /** Event creation without a signed-in user reports an error and does not call the repository. */
  @Test
  fun addEvent_withoutSignedInUser_reportsErrorAndDoesNotSave() = runTest {
    currentUserFlow.value = null
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertEquals(EventPlanningViewModel.NO_SIGNED_IN_USER_ERROR, state.error)
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A sign-out observed before creation prevents saving under the former user's identity. */
  @Test
  fun addEvent_userSignsOutAfterCreation_reportsError() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    currentUserFlow.value = null

    viewModel.addEvent()

    assertEquals(EventPlanningViewModel.NO_SIGNED_IN_USER_ERROR, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A missing event date is rejected before the repository is called. */
  @Test
  fun addEvent_missingDate_reportsErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setDate(null)

    viewModel.addEvent()

    assertEquals(
        EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR,
        viewModel.uiState.value.error,
    )
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A missing event start time is rejected before the repository is called. */
  @Test
  fun addEvent_missingStartTime_reportsErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setStartTime(null)

    viewModel.addEvent()

    assertEquals(
        EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR,
        viewModel.uiState.value.error,
    )
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A missing event end time is rejected before the repository is called. */
  @Test
  fun addEvent_missingEndTime_reportsErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setEndTime(null)

    viewModel.addEvent()

    assertEquals(
        EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR,
        viewModel.uiState.value.error,
    )
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A blank subject is invalid for event types that require one. */
  @Test
  fun addEvent_blankSubjectForNonOtherType_exposesValidationMessage() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setSubject("   ")

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertTrue(state.error!!.contains("Subject"))
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** An event whose end time is not after its start time is rejected. */
  @Test
  fun addEvent_endNotAfterStart_exposesValidationMessage() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setEndTime(startTime)

    viewModel.addEvent()

    assertEquals("End must be after start", viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  // ---------------------------------------------------------------- addEvent: saving

  /** A valid event is saved with the signed-in owner, trimmed fields, and local-time conversion. */
  @Test
  fun addEvent_validForm_savesTrimmedEventForSignedInUser() = runTest {
    val viewModel = createViewModel()
    val tasks = listOf(EventTask("Solve exercises 12, 13"))
    viewModel.setType(EventType.EXAM)
    viewModel.setSubject("  Algebra  ")
    viewModel.setDate(date)
    viewModel.setStartTime(startTime)
    viewModel.setEndTime(endTime)
    viewModel.setLocation("  Room 1  ")
    viewModel.setDescription("  Bring ID  ")
    viewModel.setTasks(tasks)

    viewModel.addEvent()

    val saved = savedEvents().single()
    assertEquals("event-1", saved.id)
    assertEquals("user123", saved.ownerId)
    assertEquals(EventType.EXAM, saved.type)
    assertEquals("Algebra", saved.subject)
    assertEquals(startsAt, saved.startsAt) // 09:00 in Berlin
    assertEquals(endsAt, saved.endsAt) // 11:00 in Berlin
    assertEquals("Room 1", saved.location)
    assertEquals("Bring ID", saved.description)
    assertEquals(tasks, saved.tasks)
    assertEquals(now, saved.createdAt)
    assertEquals(now, saved.updatedAt)
  }

  /** Successful creation resets form fields, retains suggestions, and raises the added signal. */
  @Test
  fun addEvent_success_resetsFormKeepsSubjectsAndSignalsAdded() = runTest {
    `when`(eventsRepository.observeSubjects("user123")).thenReturn(subjects("Algebra"))
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addTask("Read chapter 3")

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertTrue(state.isEventAdded)
    assertFalse(state.isSaving)
    assertNull(state.error)
    assertEquals("", state.subject)
    assertNull(state.date)
    assertNull(state.startTime)
    assertNull(state.endTime)
    assertTrue(state.tasks.isEmpty())
    assertEquals(listOf("Algebra"), state.recommendedSubjects)
  }

  /** Acknowledging a handled creation clears the event-added signal. */
  @Test
  fun onEventAddedHandled_clearsTheAddedFlag() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addEvent()
    assertTrue(viewModel.uiState.value.isEventAdded)

    viewModel.onEventAddedHandled()

    assertFalse(viewModel.uiState.value.isEventAdded)
  }

  /** Editing the form after a successful creation clears the event-added signal. */
  @Test
  fun editingFormAfterSuccessfulAdd_clearsAddedFlag() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addEvent()
    assertTrue(viewModel.uiState.value.isEventAdded)

    viewModel.setLocation("Room 2")

    assertFalse(viewModel.uiState.value.isEventAdded)
  }

  /** Blank optional location and description values are stored as null. */
  @Test
  fun addEvent_blankOptionalFields_areStoredAsNull() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setLocation("   ")
    viewModel.setDescription("")

    viewModel.addEvent()

    val saved = savedEvents().single()
    assertNull(saved.location)
    assertNull(saved.description)
    assertTrue(viewModel.uiState.value.isEventAdded)
  }

  /** An OTHER event can be saved without a subject. */
  @Test
  fun addEvent_otherTypeWithoutSubject_isSavedWithNullSubject() = runTest {
    val viewModel = createViewModel()
    viewModel.setType(EventType.OTHER)
    viewModel.setDate(date)
    viewModel.setStartTime(startTime)
    viewModel.setEndTime(endTime)

    viewModel.addEvent()

    assertNull(savedEvents().single().subject)
    assertTrue(viewModel.uiState.value.isEventAdded)
  }

  /** Event ownership uses the latest signed-in account rather than an earlier account. */
  @Test
  fun addEvent_usesTheLatestSignedInUserAsOwner() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    currentUserFlow.value = account("user456")

    viewModel.addEvent()

    assertEquals("user456", savedEvents().single().ownerId)
  }

  /** Separate successful additions receive distinct repository-generated event IDs. */
  @Test
  fun addEvent_twice_createsTwoDistinctEvents() = runTest {
    `when`(eventsRepository.newEventId()).thenReturn("id-1", "id-2")
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addEvent()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(listOf("id-1", "id-2"), savedEvents(count = 2).map { it.id })
  }

  /** Retrying a failed save reuses its event ID to avoid creating a duplicate. */
  @Test
  fun addEvent_retryAfterFailure_reusesTheSameEventId() = runTest {
    `when`(eventsRepository.newEventId()).thenReturn("id-1", "id-2")
    `when`(eventsRepository.saveEvent(any()))
        .thenReturn(
            Result.failure(EventsException(EventsError.STORAGE_FAILURE)),
            Result.success(Unit),
        )
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()
    assertFalse(viewModel.uiState.value.isEventAdded)
    viewModel.addEvent()

    assertEquals(listOf("id-1", "id-1"), savedEvents(count = 2).map { it.id })
    verify(eventsRepository, times(1)).newEventId()
    assertTrue(viewModel.uiState.value.isEventAdded)
  }

  /** Resetting an unsaved form discards its pending ID so a later event receives a new one. */
  @Test
  fun resetForm_discardsFormAndStartsANewEventId() = runTest {
    `when`(eventsRepository.newEventId()).thenReturn("id-1", "id-2")
    `when`(eventsRepository.saveEvent(any()))
        .thenReturn(
            Result.failure(EventsException(EventsError.STORAGE_FAILURE)),
            Result.success(Unit),
        )
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addEvent()

    viewModel.resetForm()

    assertEquals("", viewModel.uiState.value.subject)
    assertNull(viewModel.uiState.value.error)
    viewModel.fillValidForm()
    viewModel.addEvent()
    assertEquals(listOf("id-1", "id-2"), savedEvents(count = 2).map { it.id })
  }

  /** Resetting the form preserves repository-provided subject suggestions. */
  @Test
  fun resetForm_keepsSubjectSuggestions() = runTest {
    `when`(eventsRepository.observeSubjects("user123")).thenReturn(subjects("Algebra"))
    val viewModel = createViewModel()
    viewModel.setSubject("typed but abandoned")

    viewModel.resetForm()

    assertEquals(listOf("Algebra"), viewModel.uiState.value.recommendedSubjects)
  }

  /** A second add request is ignored until the first repository save completes. */
  @Test
  fun addEvent_whileSaving_isIgnoredUntilSaveCompletes() = runTest {
    val gate = CompletableDeferred<Unit>()
    `when`(eventsRepository.saveEvent(any())).doSuspendableAnswer {
      gate.await()
      Result.success(Unit)
    }
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()
    assertTrue(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isEventAdded)

    viewModel.addEvent() // ignored: an add is already running
    verify(eventsRepository, times(1)).saveEvent(any())

    gate.complete(Unit)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaving)
    assertTrue(viewModel.uiState.value.isEventAdded)
  }

  // ---------------------------------------------------------------- addEvent: repository failures

  /** Known repository errors map to stable, user-facing messages. */
  @Test
  fun addEvent_repositoryFailure_exposesUserMessageForEachErrorCategory() = runTest {
    val expectations =
        mapOf(
            EventsError.INVALID_ARGUMENT to EventPlanningViewModel.INVALID_EVENT_ERROR,
            EventsError.NOT_FOUND to EventPlanningViewModel.EVENT_NOT_FOUND_ERROR,
            EventsError.PERMISSION_DENIED to EventPlanningViewModel.PERMISSION_DENIED_ERROR,
            EventsError.INVALID_EVENT_DATA to EventPlanningViewModel.INVALID_EVENT_DATA_ERROR,
            EventsError.STORAGE_FAILURE to EventPlanningViewModel.STORAGE_FAILURE_ERROR,
            EventsError.UNKNOWN to EventPlanningViewModel.FAILED_TO_ADD_EVENT_ERROR,
        )
    assertEquals(EventsError.values().toSet(), expectations.keys) // no category forgotten
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    for ((error, expectedMessage) in expectations) {
      `when`(eventsRepository.saveEvent(any())).thenReturn(Result.failure(EventsException(error)))

      viewModel.addEvent()

      val state = viewModel.uiState.value
      assertEquals(expectedMessage, state.error)
      assertFalse(state.isEventAdded)
      assertFalse(state.isSaving)
    }
  }

  /** An unexpected repository failure preserves its available message. */
  @Test
  fun addEvent_otherFailureWithMessage_exposesMessage() = runTest {
    `when`(eventsRepository.saveEvent(any()))
        .thenReturn(Result.failure(IllegalStateException("disk full")))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals("disk full", viewModel.uiState.value.error)
  }

  /** An unexpected repository failure without a message uses the default save error. */
  @Test
  fun addEvent_otherFailureWithoutMessage_usesDefaultMessage() = runTest {
    `when`(eventsRepository.saveEvent(any()))
        .thenReturn(Result.failure(RuntimeException(null as String?)))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(EventPlanningViewModel.FAILED_TO_ADD_EVENT_ERROR, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** A repository exception is surfaced with its message and clears the saving state. */
  @Test
  fun addEvent_repositoryThrowsWithMessage_exposesMessage() = runTest {
    `when`(eventsRepository.saveEvent(any())).thenThrow(IllegalStateException("boom"))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertEquals("boom", state.error)
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
  }

  /** A repository exception without a message uses the default save error. */
  @Test
  fun addEvent_repositoryThrowsWithoutMessage_usesDefaultMessage() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(eventsRepository.saveEvent(any())).thenThrow(exception)
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(EventPlanningViewModel.FAILED_TO_ADD_EVENT_ERROR, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** Cancellation from a repository save is not presented as an error and ends loading. */
  @Test
  fun addEvent_cancelled_isNotReportedAsErrorAndStopsSaving() = runTest {
    `when`(eventsRepository.saveEvent(any())).thenThrow(CancellationException("cancelled"))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertNull(state.error)
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
  }

  // factory tests

  /** The factory creates an event-planning ViewModel for its supported type. */
  @Test
  fun factory_createsEventPlanningViewModel() {
    val factory = EventPlanningViewModelFactory(authRepository, eventsRepository)

    val viewModel = factory.create(EventPlanningViewModel::class.java)

    assertNotNull(viewModel)
  }

  /** The factory rejects ViewModel classes it does not support. */
  @Test
  fun factory_rejectsUnsupportedViewModelClass() {
    val factory = EventPlanningViewModelFactory(authRepository, eventsRepository)

    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }

    assertTrue(exception.message!!.contains(OtherViewModel::class.java.name))
  }
}
