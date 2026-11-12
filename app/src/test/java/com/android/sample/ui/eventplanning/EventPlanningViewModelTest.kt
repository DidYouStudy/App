// Co-authored-by: Copilot
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import androidx.lifecycle.ViewModel
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.preferences.UserPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.eq
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
class EventPlanningViewModelTest : EventPlanningViewModelTestBase() {

  /** Unsupported ViewModel type used to verify that the factory rejects unrelated requests. */
  private class OtherViewModel : ViewModel()

  /** Installs the test dispatcher and configures repository mocks with successful defaults. */
  @Before
  fun setUp() {
    authRepository = mock(AuthRepository::class.java)
    eventsRepository = mock(EventsRepository::class.java)
    groupSessionsRepository = mock(GroupSessionsRepository::class.java)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
    `when`(eventsRepository.newEventId()).thenReturn("event-1")
    `when`(eventsRepository.observeSubjects(any())).thenReturn(subjects())
    `when`(groupSessionsRepository.newSessionId()).thenReturn("session-1")
    runBlocking {
      `when`(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit))
      `when`(groupSessionsRepository.saveSession(any(), any())).thenReturn(Result.success(Unit))
      `when`(eventsRepository.deleteEvent(any(), any())).thenReturn(Result.success(Unit))
      `when`(groupSessionsRepository.deleteSession(any(), any())).thenReturn(Result.success(Unit))
      `when`(preferencesRepository.getPreferences(any()))
          .thenReturn(
              Result.success(
                  UserPreferences(
                      userId = "user123",
                      sessionLengthMinutes = 60,
                  )
              )
          )
    }
    currentUserFlow.value = account("user123")
  }

  /** Creates a one-shot subject flow containing [subjects]. */
  private fun subjects(vararg subjects: String) = flowOf(Result.success(subjects.toList()))

  // ---------------------------------------------------------------- user observation

  /** A failed authentication flow sets the observation error category. */
  @Test
  fun init_userFlowFails_setsObservationError() = runTest {
    `when`(authRepository.currentUser)
        .thenReturn(flow { throw RuntimeException("Auth flow error") })

    val viewModel = createViewModel()

    assertEquals(EventPlanningError.FAILED_TO_OBSERVE_USER, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isEventAdded)
  }

  /**
   * Event creation is rejected when authentication observation failed before identifying a user.
   */
  @Test
  fun init_userFlowFails_noUserIsKnownAndAddingIsRejected() = runTest {
    `when`(authRepository.currentUser)
        .thenReturn(flow { throw RuntimeException("Auth flow error") })
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** Multiple missing mandatory fields share one validation error. */
  @Test
  fun addEvent_multipleRequiredFieldsMissing_reportsOneErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()

    viewModel.addEvent()

    assertEquals(EventPlanningError.REQUIRED_FIELDS_MISSING, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** Cancellation of authentication observation is propagated without displaying an error. */
  @Test
  fun init_userFlowCancelled_doesNotExposeError() = runTest {
    `when`(authRepository.currentUser).thenReturn(flow { throw CancellationException("cancelled") })

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

  // ---------------------------------------------------------------- location search

  /** Builds a minimal campus location for tests; only the name is usually relevant. */
  private fun sampleLocation(name: String): CampusLocation =
      CampusLocation(
          name = name,
          zone = "AAB",
          type = CampusLocationType.LECTURE_ROOM,
          floor = null,
          location = Location(latitude = 46.52, longitude = 6.56),
      )

  /** A successful search result is exposed as the location picker's search results. */
  @Test
  fun searchLocations_success_updatesLocationSearchResults() = runTest {
    val results = listOf(sampleLocation("BC 01"), sampleLocation("BC 02"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.success(results))
    val viewModel = createViewModel()

    viewModel.searchLocations("BC")
    advanceUntilIdle()

    assertEquals(results, viewModel.uiState.value.locationSearchResults)
  }

  /** A failed search leaves the results empty without surfacing an error. */
  @Test
  fun searchLocations_failure_leavesResultsEmptyWithoutError() = runTest {
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.failure(RuntimeException("search failed")))
    val viewModel = createViewModel()

    viewModel.searchLocations("BC")
    advanceUntilIdle()

    assertEquals(emptyList<CampusLocation>(), viewModel.uiState.value.locationSearchResults)
    assertNull(viewModel.uiState.value.error)
  }

  /** A failed search after a successful one clears the stale results instead of keeping them. */
  @Test
  fun searchLocations_failureAfterSuccess_clearsStaleResults() = runTest {
    val results = listOf(sampleLocation("BC 01"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.success(results))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "ZZ")))
        .thenReturn(Result.failure(RuntimeException("search failed")))
    val viewModel = createViewModel()

    viewModel.searchLocations("BC")
    advanceUntilIdle()
    assertEquals(results, viewModel.uiState.value.locationSearchResults)

    viewModel.searchLocations("ZZ")
    advanceUntilIdle()

    assertEquals(emptyList<CampusLocation>(), viewModel.uiState.value.locationSearchResults)
    assertNull(viewModel.uiState.value.error)
  }

  /**
   * A blank query clears the results directly, without calling the repository: reopening the picker
   * after a previous search must not show stale results for a search box that is empty again.
   */
  @Test
  fun searchLocations_blankQuery_clearsResultsWithoutQueryingRepository() = runTest {
    val results = listOf(sampleLocation("BC 01"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.success(results))
    val viewModel = createViewModel()
    viewModel.searchLocations("BC")
    advanceUntilIdle()
    assertEquals(results, viewModel.uiState.value.locationSearchResults)

    viewModel.searchLocations("")

    assertEquals(emptyList<CampusLocation>(), viewModel.uiState.value.locationSearchResults)
    verify(campusLocationRepository, never()).search(CampusLocationFilter(query = ""))
  }

  /** A new search cancels a slower, still-running one, so its stale result is dropped. */
  @Test
  fun searchLocations_newQueryCancelsPreviousSlowQuery() = runTest {
    val staleGate = CompletableDeferred<Unit>()
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "stale")))
        .doSuspendableAnswer {
          staleGate.await()
          Result.success(listOf(sampleLocation("STALE 01")))
        }
    val freshResults = listOf(sampleLocation("BC 01"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "fresh")))
        .thenReturn(Result.success(freshResults))
    val viewModel = createViewModel()

    viewModel.searchLocations("stale")
    viewModel.searchLocations("fresh")
    advanceUntilIdle()
    staleGate.complete(Unit)
    advanceUntilIdle()

    assertEquals(freshResults, viewModel.uiState.value.locationSearchResults)
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
    assertEquals(EventKind.PERSONAL, state.eventKind)
    assertTrue(state.showTasksSection)
    assertEquals(EventType.LAB, state.type)
    assertEquals("CompSec", state.subject)
    assertEquals(date, state.date)
    assertEquals(startTime, state.startTime)
    assertEquals(endTime, state.endTime)
    assertEquals("INF 1", state.location)
    assertEquals("Bring laptop", state.description)
    assertEquals(tasks, state.tasks)
  }

  /** setEventKind updates eventKind and toggles showTasksSection. */
  @Test
  fun setEventKind_updatesEventKindAndShowTasksSection() = runTest {
    val viewModel = createViewModel()
    assertEquals(EventKind.PERSONAL, viewModel.uiState.value.eventKind)
    assertTrue(viewModel.uiState.value.showTasksSection)

    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventKind.GROUP_SESSION, state.eventKind)
    assertFalse(state.showTasksSection)
  }

  /** Switching event kind resets form fields and clears tasks. */
  @Test
  fun setEventKind_switchingKind_resetsFormAndClearsTasks() = runTest {
    val viewModel = createViewModel()
    viewModel.setSubject("Personal Study")
    viewModel.setLocation("BC 01")
    viewModel.addTask("Task 1")
    assertEquals(1, viewModel.uiState.value.tasks.size)

    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertEquals(EventKind.GROUP_SESSION, state.eventKind)
    assertTrue(state.tasks.isEmpty())
    assertTrue(state.subject.isEmpty())
    assertTrue(state.location.isEmpty())
    assertFalse(state.showTasksSection)
  }

  /** Calling setEventKind with the same kind preserves user edits. */
  @Test
  fun setEventKind_sameKind_preservesExistingInput() = runTest {
    val viewModel = createViewModel()
    viewModel.setSubject("Drafting proposal")

    viewModel.setEventKind(EventKind.PERSONAL)

    assertEquals("Drafting proposal", viewModel.uiState.value.subject)
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

  /** Task operations throw when the form is currently in group session mode. */
  @Test
  fun setTasks_throwsForGroupSession() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          viewModel.setTasks(listOf(EventTask("Task 1")))
        }
    assertEquals("Tasks are only supported for personal events", exception.message)
  }

  @Test
  fun addTask_throwsForGroupSession() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val exception =
        assertThrows(IllegalArgumentException::class.java) { viewModel.addTask("Task 1") }
    assertEquals("Tasks are only supported for personal events", exception.message)
  }

  @Test
  fun removeTask_throwsForGroupSession() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val exception = assertThrows(IllegalArgumentException::class.java) { viewModel.removeTask(0) }
    assertEquals("Tasks are only supported for personal events", exception.message)
  }

  @Test
  fun toggleTask_throwsForGroupSession() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)

    val exception = assertThrows(IllegalArgumentException::class.java) { viewModel.toggleTask(0) }
    assertEquals("Tasks are only supported for personal events", exception.message)
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
    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, state.error)
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

    assertEquals(EventPlanningError.NO_SIGNED_IN_USER, viewModel.uiState.value.error)
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
        EventPlanningError.REQUIRED_FIELDS_MISSING,
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
        EventPlanningError.REQUIRED_FIELDS_MISSING,
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
        EventPlanningError.REQUIRED_FIELDS_MISSING,
        viewModel.uiState.value.error,
    )
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** A blank mandatory subject reports the shared required-fields error. */
  @Test
  fun addEvent_blankSubjectForNonOtherType_reportsRequiredFieldsError() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setSubject("   ")

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.REQUIRED_FIELDS_MISSING, state.error)
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** An event whose end time is not after its start time is rejected. */
  @Test
  fun addEvent_endEqualsStart_reportsInvalidTimeRange() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.setEndTime(startTime)

    viewModel.addEvent()

    assertEquals(EventPlanningError.INVALID_TIME_RANGE, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** An event with a start time before the current time is rejected before saving. */
  @Test
  fun addEvent_startInPast_reportsErrorAndDoesNotSave() = runTest {
    val viewModel = createViewModel()
    viewModel.fillValidForm(date = date.minusDays(1))

    viewModel.addEvent()

    assertEquals(EventPlanningError.EVENT_IN_PAST, viewModel.uiState.value.error)
    verify(eventsRepository, never()).saveEvent(any())
  }

  /** Daylight-saving changes cannot turn an event into a duration longer than 24 hours. */
  @Test
  fun addEvent_durationOver24Hours_reportsErrorAndDoesNotSave() = runTest {
    val fallBackClock = Clock.fixed(Instant.parse("2026-10-24T22:00:00Z"), zone)
    val viewModel = createViewModel(clk = fallBackClock)
    viewModel.fillValidForm(
        date = LocalDate.of(2026, 10, 25),
        start = LocalTime.of(0, 30),
        end = LocalTime.MIDNIGHT,
    )

    viewModel.addEvent()

    assertEquals(EventPlanningError.EVENT_TOO_LONG, viewModel.uiState.value.error)
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
    assertEquals(EventType.EXAM, saved.details.type)
    assertEquals("Algebra", saved.details.subject)
    assertEquals(startsAt, saved.details.startsAt) // 09:00 in Berlin
    assertEquals(endsAt, saved.details.endsAt) // 11:00 in Berlin
    assertEquals("Room 1", saved.details.location)
    assertEquals("Bring ID", saved.details.description)
    assertEquals(tasks, saved.tasks)
    assertEquals(now, saved.createdAt)
    assertEquals(now, saved.updatedAt)
  }

  /**
   * Successful creation resets form fields, retains subject suggestions, clears location search
   * results, and raises the added signal.
   */
  @Test
  fun addEvent_success_resetsFormKeepsSubjectsAndSignalsAdded() = runTest {
    `when`(eventsRepository.observeSubjects("user123")).thenReturn(subjects("Algebra"))
    val locations = listOf(sampleLocation("BC 01"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.success(locations))
    val viewModel = createViewModel()
    viewModel.fillValidForm()
    viewModel.addTask("Read chapter 3")
    viewModel.searchLocations("BC")
    advanceUntilIdle()

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
    assertEquals(emptyList<CampusLocation>(), state.locationSearchResults)
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
    assertNull(saved.details.location)
    assertNull(saved.details.description)
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

    assertNull(savedEvents().single().details.subject)
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

  /**
   * Resetting the form preserves subject suggestions, since they come from a long-lived
   * subscription rather than the form being reset. Location search results are not preserved: they
   * are scoped to the picker dialog, which already clears them on close.
   */
  @Test
  fun resetForm_keepsSubjectSuggestionsButClearsLocationSearchResults() = runTest {
    `when`(eventsRepository.observeSubjects("user123")).thenReturn(subjects("Algebra"))
    val locations = listOf(sampleLocation("BC 01"))
    `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
        .thenReturn(Result.success(locations))
    val viewModel = createViewModel()
    viewModel.setSubject("typed but abandoned")
    viewModel.searchLocations("BC")
    advanceUntilIdle()

    viewModel.resetForm()

    assertEquals(listOf("Algebra"), viewModel.uiState.value.recommendedSubjects)
    assertEquals(emptyList<CampusLocation>(), viewModel.uiState.value.locationSearchResults)
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

  /** Known repository errors map to stable, user-facing error categories. */
  @Test
  fun addEvent_repositoryFailure_exposesUserMessageForEachErrorCategory() = runTest {
    val expectations =
        mapOf(
            EventsError.INVALID_ARGUMENT to EventPlanningError.INVALID_EVENT,
            EventsError.NOT_FOUND to EventPlanningError.EVENT_NOT_FOUND,
            EventsError.PERMISSION_DENIED to EventPlanningError.PERMISSION_DENIED,
            EventsError.INVALID_EVENT_DATA to EventPlanningError.INVALID_EVENT_DATA,
            EventsError.STORAGE_FAILURE to EventPlanningError.STORAGE_FAILURE,
            EventsError.UNKNOWN to EventPlanningError.FAILED_TO_ADD_EVENT,
        )
    assertEquals(EventsError.entries.toSet(), expectations.keys) // no category forgotten
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    for ((error, expectedError) in expectations) {
      `when`(eventsRepository.saveEvent(any())).thenReturn(Result.failure(EventsException(error)))

      viewModel.addEvent()

      val state = viewModel.uiState.value
      assertEquals(expectedError, state.error)
      assertFalse(state.isEventAdded)
      assertFalse(state.isSaving)
    }
  }

  /** An unexpected repository failure translates to the default save error category. */
  @Test
  fun addEvent_otherFailure_setsFailedToAddEventError() = runTest {
    `when`(eventsRepository.saveEvent(any()))
        .thenReturn(Result.failure(IllegalStateException("disk full")))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    assertEquals(EventPlanningError.FAILED_TO_ADD_EVENT, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  /** A repository exception translates to the default save error category and clears saving. */
  @Test
  fun addEvent_repositoryThrows_setsFailedToAddEventError() = runTest {
    `when`(eventsRepository.saveEvent(any())).thenThrow(IllegalStateException("boom"))
    val viewModel = createViewModel()
    viewModel.fillValidForm()

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertEquals(EventPlanningError.FAILED_TO_ADD_EVENT, state.error)
    assertFalse(state.isEventAdded)
    assertFalse(state.isSaving)
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

  // ---------------------------------------------------------------- session length and start time

  /**
   * Setting start time automatically populates end time to start time plus sessionLengthMinutes.
   */
  @Test
  fun setStartTime_automaticallySetsEndTimeBasedOnPreferencesSessionLength() = runTest {
    runBlocking {
      `when`(preferencesRepository.getPreferences("user123"))
          .thenReturn(
              Result.success(UserPreferences(userId = "user123", sessionLengthMinutes = 90))
          )
    }
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.setStartTime(LocalTime.of(14, 0))

    assertEquals(LocalTime.of(14, 0), viewModel.uiState.value.startTime)
    assertEquals(LocalTime.of(15, 30), viewModel.uiState.value.endTime)
  }

  /** When preferences fail to load, setting start time falls back to default session length. */
  @Test
  fun setStartTime_preferencesFailure_fallsBackToDefaultSessionLength() = runTest {
    runBlocking {
      `when`(preferencesRepository.getPreferences("user123"))
          .thenReturn(Result.failure(RuntimeException("Network failure")))
    }
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.setStartTime(LocalTime.of(10, 0))

    assertEquals(LocalTime.of(10, 0), viewModel.uiState.value.startTime)
    assertEquals(LocalTime.of(11, 0), viewModel.uiState.value.endTime)
    assertNull(viewModel.uiState.value.error)
  }

  /** Setting start time does not overwrite end time if a custom end time was already set. */
  @Test
  fun setStartTime_doesNotOverwriteCustomEndTime_whenCustomEndTimeIsSet() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    // User sets start time first -> auto fills 14:00 - 15:00
    viewModel.setStartTime(LocalTime.of(14, 0))
    assertEquals(LocalTime.of(15, 0), viewModel.uiState.value.endTime)

    // User manually sets custom end time -> 18:00
    viewModel.setEndTime(LocalTime.of(18, 0))
    assertEquals(LocalTime.of(18, 0), viewModel.uiState.value.endTime)

    // User changes start time to 15:00 -> custom end time 18:00 is preserved
    viewModel.setStartTime(LocalTime.of(15, 0))
    assertEquals(LocalTime.of(15, 0), viewModel.uiState.value.startTime)
    assertEquals(LocalTime.of(18, 0), viewModel.uiState.value.endTime)
  }

  // factory tests

  /** The factory creates an event-planning ViewModel for its supported type. */
  @Test
  fun factory_createsEventPlanningViewModel() {
    val factory =
        EventPlanningViewModelFactory(
            authRepository,
            eventsRepository,
            campusLocationRepository,
            groupSessionsRepository,
            preferencesRepository,
        )

    val viewModel = factory.create(EventPlanningViewModel::class.java)

    assertNotNull(viewModel)
  }

  /** The factory rejects ViewModel classes it does not support. */
  @Test
  fun factory_rejectsUnsupportedViewModelClass() {
    val factory =
        EventPlanningViewModelFactory(
            authRepository,
            eventsRepository,
            campusLocationRepository,
            groupSessionsRepository,
            preferencesRepository,
        )

    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }

    assertEquals("Unsupported ViewModel: ${OtherViewModel::class.java.name}", exception.message)
  }

  // ---------------------------------------------------------------- group sessions

  /** Adding a group session creates a session with the user as creator and member. */
  @Test
  fun addEvent_groupSession_savesWithCreatorAsOnlyMember() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)
    viewModel.fillValidForm()
    viewModel.setLocation("CM 3")
    viewModel.setDescription("Chapter 5 prep")

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertTrue(state.isEventAdded)
    assertNull(state.error)

    val captor = argumentCaptor<GroupSession>()
    verify(groupSessionsRepository).saveSession(eq("user123"), captor.capture())
    val saved = captor.firstValue
    assertEquals("user123", saved.createdBy)
    assertEquals(listOf("user123"), saved.memberIds)
    assertEquals(EventType.STUDY_SESSION, saved.details.type)
    assertEquals("Algebra", saved.details.subject)
    assertEquals("CM 3", saved.details.location)
    assertEquals("Chapter 5 prep", saved.details.description)
  }

  /** Editing a group session preserves existing member list and creation time. */
  @Test
  fun editEvent_groupSession_preservesExistingMembersAndCreatedAt() = runTest {
    val initialCreatedAt = Instant.parse("2026-10-01T10:00:00Z")
    val initial =
        GroupSession(
            id = "session-99",
            createdBy = "user123",
            memberIds = listOf("user123", "user456"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Initial Subject",
                    startsAt = startsAt,
                    endsAt = endsAt,
                ),
            createdAt = initialCreatedAt,
        )
    `when`(groupSessionsRepository.getSession("session-99")).thenReturn(Result.success(initial))

    val viewModel = createViewModel()
    viewModel.loadEvent("session-99", EventKind.GROUP_SESSION)

    val loadedState = viewModel.uiState.value
    assertTrue(loadedState.isEditing)
    assertEquals("Initial Subject", loadedState.subject)

    viewModel.setSubject("Updated Subject")
    viewModel.setLocation("New Room")
    viewModel.editEvent()

    val state = viewModel.uiState.value
    assertTrue(state.isEventEdited)

    val captor = argumentCaptor<GroupSession>()
    verify(groupSessionsRepository).saveSession(eq("user123"), captor.capture())
    val updated = captor.firstValue
    assertEquals("session-99", updated.id)
    assertEquals("user123", updated.createdBy)
    assertEquals(listOf("user123", "user456"), updated.memberIds)
    assertEquals(initialCreatedAt, updated.createdAt)
    assertEquals(now, updated.updatedAt)
    assertEquals("Updated Subject", updated.details.subject)
    assertEquals("New Room", updated.details.location)
  }

  /** Loading a group session with blank ID does nothing. */
  @Test
  fun loadEvent_blankId_doesNothing() {
    val viewModel = createViewModel()
    viewModel.loadEvent("   ", EventKind.GROUP_SESSION)
    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isEditing)
  }

  /** Loading a missing group session fails and resets editing state. */
  @Test
  fun loadEvent_groupSession_failsWhenNotFound() = runTest {
    `when`(groupSessionsRepository.getSession("missing-session"))
        .thenReturn(Result.failure(EventsException(EventsError.NOT_FOUND)))

    val viewModel = createViewModel()
    viewModel.loadEvent("missing-session", EventKind.GROUP_SESSION)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertFalse(state.isEditing)
    assertEquals(EventPlanningError.EVENT_NOT_FOUND, state.error)
  }

  /** loadEvent immediately clears prior event fields and sets loading state. */
  @Test
  fun loadEvent_resetsPreviousFormFieldsWhileLoading() = runTest {
    val fetchGate = CompletableDeferred<Unit>()
    `when`(groupSessionsRepository.getSession("session-async")).doSuspendableAnswer {
      fetchGate.await()
      Result.failure(EventsException(EventsError.NOT_FOUND))
    }

    val viewModel = createViewModel()
    viewModel.setSubject("Stale Event")
    viewModel.setLocation("Old Room")

    viewModel.loadEvent("session-async", EventKind.GROUP_SESSION)

    val loadingState = viewModel.uiState.value
    assertTrue(loadingState.isLoading)
    assertEquals(EventKind.GROUP_SESSION, loadingState.eventKind)
    assertTrue(loadingState.subject.isEmpty())
    assertTrue(loadingState.location.isEmpty())

    fetchGate.complete(Unit)
    advanceUntilIdle()

    val finishedState = viewModel.uiState.value
    assertFalse(finishedState.isLoading)
    assertEquals(EventPlanningError.EVENT_NOT_FOUND, finishedState.error)
  }

  /** Deleting a group session deletes from the group sessions repository. */
  @Test
  fun deleteEvent_groupSession_deletesFromGroupSessionsRepository() = runTest {
    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)

    viewModel.deleteEvent("session-1")

    val state = viewModel.uiState.value
    assertTrue(state.isEventDeleted)
    verify(groupSessionsRepository).deleteSession("user123", "session-1")
    verify(eventsRepository, never()).deleteEvent(any(), any())
  }

  /** Group session repository save failure surfaces error. */
  @Test
  fun addEvent_groupSession_repositoryFailure_exposesError() = runTest {
    `when`(groupSessionsRepository.saveSession(any(), any()))
        .thenReturn(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))

    val viewModel = createViewModel()
    viewModel.setEventKind(EventKind.GROUP_SESSION)
    viewModel.fillValidForm()

    viewModel.addEvent()

    val state = viewModel.uiState.value
    assertFalse(state.isEventAdded)
    assertEquals(EventPlanningError.STORAGE_FAILURE, state.error)
  }

  /**
   * Verifies that each [EventPlanningError] entry maps to its distinct, valid localized string
   * resource identifier.
   */
  @Test
  fun eventPlanningError_stringResource_mapsToExpectedResource() {
    val expectedMappings =
        mapOf(
            EventPlanningError.FAILED_TO_OBSERVE_USER to
                R.string.event_planning_error_failed_to_observe_user,
            EventPlanningError.NO_SIGNED_IN_USER to R.string.event_planning_error_no_signed_in_user,
            EventPlanningError.REQUIRED_FIELDS_MISSING to
                R.string.event_planning_error_required_fields,
            EventPlanningError.INVALID_TIME_RANGE to
                R.string.event_planning_error_invalid_time_range,
            EventPlanningError.EVENT_IN_PAST to R.string.event_planning_error_event_in_past,
            EventPlanningError.EVENT_TOO_LONG to R.string.event_planning_error_event_too_long,
            EventPlanningError.NO_EVENT_TO_EDIT to R.string.event_planning_error_no_event_to_edit,
            EventPlanningError.FAILED_TO_ADD_EVENT to
                R.string.event_planning_error_failed_to_add_event,
            EventPlanningError.FAILED_TO_EDIT_EVENT to
                R.string.event_planning_error_failed_to_edit_event,
            EventPlanningError.FAILED_TO_DELETE_EVENT to
                R.string.event_planning_error_failed_to_delete_event,
            EventPlanningError.FAILED_TO_LOAD_EVENT to
                R.string.event_planning_error_failed_to_load_event,
            EventPlanningError.INVALID_EVENT to R.string.event_planning_error_invalid_event,
            EventPlanningError.EVENT_NOT_FOUND to R.string.event_planning_error_event_not_found,
            EventPlanningError.PERMISSION_DENIED to R.string.event_planning_error_permission_denied,
            EventPlanningError.INVALID_EVENT_DATA to
                R.string.event_planning_error_invalid_event_data,
            EventPlanningError.STORAGE_FAILURE to R.string.event_planning_error_storage_failure,
        )

    assertEquals(EventPlanningError.entries.toSet(), expectedMappings.keys)
    assertEquals(expectedMappings.size, expectedMappings.values.toSet().size)

    for (error in EventPlanningError.entries) {
      assertEquals(
          "Mapping mismatch for $error",
          expectedMappings[error],
          error.stringResource,
      )
    }
  }
}
