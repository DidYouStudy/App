// Co-authored-by: Copilot
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.GroupSession
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditEventScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()
  private val currentUser = MutableStateFlow<UserAccount?>(null)
  private val authRepository: AuthRepository = mock()
  private val eventsRepository: EventsRepository = mock()
  private val campusLocationRepository: CampusLocationRepository = mock()
  private val groupSessionsRepository: GroupSessionsRepository = mock()
  private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneId.of("Europe/Berlin"))
  private val storedEvent =
      PersonalEvent(
          id = "event-1",
          ownerId = "user-1",
          details =
              EventDetails(
                  type = EventType.COURSE,
                  subject = "Operating Systems",
                  startsAt = Instant.parse("2026-10-27T08:00:00Z"),
                  endsAt = Instant.parse("2026-10-27T10:00:00Z"),
                  location = "INF 1",
                  description = "Bring lecture notes",
              ),
          createdAt = Instant.parse("2026-10-20T09:00:00Z"),
          updatedAt = Instant.parse("2026-10-20T09:00:00Z"),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    currentUser.value = UserAccount("user-1", null, null, null)
    whenever(authRepository.currentUser).thenReturn(currentUser)
    whenever(eventsRepository.observeSubjects(any()))
        .thenReturn(flowOf(Result.success(emptyList())))
    runBlocking {
      whenever(eventsRepository.getEvent("user-1", storedEvent.id))
          .thenReturn(Result.success(storedEvent))
      whenever(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit))
      whenever(eventsRepository.deleteEvent("user-1", storedEvent.id))
          .thenReturn(Result.success(Unit))
      whenever(campusLocationRepository.search(any())).thenReturn(Result.success(emptyList()))
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): EventPlanningViewModel {
    val viewModel =
        EventPlanningViewModel(
            authRepository,
            eventsRepository,
            campusLocationRepository,
            groupSessionsRepository,
            clock,
        )
    testDispatcher.scheduler.advanceUntilIdle()
    return viewModel
  }

  private fun finishPendingComposeAndViewModelWork() {
    composeTestRule.waitForIdle()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()
  }

  /** Builds a minimal campus location for tests; only the name is usually relevant. */
  private fun sampleLocation(name: String): CampusLocation =
      CampusLocation(
          name = name,
          zone = "BC",
          type = CampusLocationType.LECTURE_ROOM,
          floor = null,
          location = Location(latitude = 46.52, longitude = 6.56),
      )

  /** Renders the shared stateless form explicitly in Edit mode for focused UI behavior tests. */
  private fun setEditStatelessScreen(
      uiState: EventPlanningUiState,
      onBackClick: () -> Unit = {},
      onSaveClick: () -> Unit = {},
      onDeleteClick: (() -> Unit)? = null,
      isEditing: Boolean = true,
      isLoading: Boolean = false,
  ) {
    composeTestRule.setContent {
      EventPlanningScreenStateless(
          uiState = uiState,
          onBackClick = onBackClick,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = onSaveClick,
          onDeleteClick = onDeleteClick,
          isEditing = isEditing,
          isLoading = isLoading,
      )
    }
  }

  @Test
  fun editStatelessScreenRendersLoadedEventAndWiresActions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var backClicked = false
    var saveClicked = false
    var deleteClicked = false
    setEditStatelessScreen(
        uiState =
            EventPlanningUiState(
                type = EventType.COURSE,
                subject = "Operating Systems",
                date = LocalDate.of(2026, 10, 27),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0),
                location = "INF 1",
                description = "Bring lecture notes",
                tasks = listOf(EventTask("Review chapter")),
                isEditing = true,
            ),
        onBackClick = { backClicked = true },
        onSaveClick = { saveClicked = true },
        onDeleteClick = { deleteClicked = true },
    )

    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_edit_title))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertTextContains("27/10/2026")
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertTextContains("09:00")
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertTextContains("11:00")
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertTextContains("Operating Systems")
    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).assertTextContains("INF 1")
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).assertTextContains("Bring lecture notes")
    assertTrue(
        composeTestRule.onAllNodesWithText("Review chapter").fetchSemanticsNodes().isNotEmpty()
    )
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsEnabled().performClick()
    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(Tags.BACK_BUTTON).performClick()

    assertTrue(saveClicked)
    assertTrue(deleteClicked)
    assertTrue(backClicked)
  }

  @Test
  fun editStatelessScreenDisablesActionsUntilEventIsLoaded() {
    setEditStatelessScreen(
        uiState = EventPlanningUiState(isEditing = false, isLoading = true),
        onDeleteClick = {},
        isLoading = true,
    )

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).assertIsNotEnabled()
    assertTrue(
        composeTestRule.onAllNodesWithTag(Tags.SCROLL_CONTAINER).fetchSemanticsNodes().isEmpty()
    )
  }

  @Test
  fun editStatelessScreenShowsDeleteProgressAndDisablesActionsWhileDeleting() {
    setEditStatelessScreen(
        uiState = EventPlanningUiState(isEditing = true, isDeleting = true),
        onDeleteClick = {},
    )

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON_PROGRESS).assertIsDisplayed()
  }

  @Test
  fun editStatelessScreenHidesDeleteActionWhenNoCallbackIsProvided() {
    setEditStatelessScreen(uiState = EventPlanningUiState(isEditing = true))

    assertTrue(
        composeTestRule.onAllNodesWithTag(Tags.DELETE_BUTTON).fetchSemanticsNodes().isEmpty()
    )
  }

  @Test
  fun editStatelessScreenShowsSpecificErrorForInvalidEvent() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    setEditStatelessScreen(uiState = EventPlanningUiState(error = EventPlanningError.INVALID_EVENT))

    composeTestRule
        .onNodeWithTag(Tags.ERROR_TEXT)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_error_invalid_event))
  }

  @Test
  fun sharedScreenBuiltInPickersReturnSelectedValues() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var selectedDate: LocalDate? = null
    var selectedStartTime: LocalTime? = null
    var selectedEndTime: LocalTime? = null
    var selectedSubject: String? = null

    composeTestRule.setContent {
      EventPlanningScreen(
          uiState =
              EventPlanningUiState(
                  date = LocalDate.of(2026, 10, 27),
                  startTime = LocalTime.of(9, 0),
                  endTime = LocalTime.of(11, 0),
                  subject = "Operating Systems",
                  recommendedSubjects = listOf("Operating Systems"),
                  isEditing = true,
              ),
          onBackClick = {},
          onTypeChange = {},
          onDateSelected = { selectedDate = it },
          onStartTimeSelected = { selectedStartTime = it },
          onEndTimeSelected = { selectedEndTime = it },
          onSubjectSelected = { selectedSubject = it },
          onLocationChange = {},
          onLocationSearchQueryChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = {},
          onErrorDismiss = {},
          onDeleteClick = {},
          isEditing = true,
      )
    }

    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals(LocalDate.of(2026, 10, 27), selectedDate)

    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals(LocalTime.of(9, 0), selectedStartTime)

    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals(LocalTime.of(11, 0), selectedEndTime)

    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals("Operating Systems", selectedSubject)
  }

  @Test
  fun editScreenInvokesExternalPickerCallbacks() {
    val viewModel = createViewModel()
    var typePickerOpened = false
    var datePickerOpened = false
    var startTimePickerOpened = false
    var endTimePickerOpened = false
    var subjectPickerOpened = false

    composeTestRule.setContent {
      EditEventScreen(
          eventId = storedEvent.id,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
          onPickTypeClick = { typePickerOpened = true },
          onPickDateClick = { datePickerOpened = true },
          onPickStartTimeClick = { startTimePickerOpened = true },
          onPickEndTimeClick = { endTimePickerOpened = true },
          onPickSubjectClick = { subjectPickerOpened = true },
      )
    }
    finishPendingComposeAndViewModelWork()
    assertTrue(viewModel.uiState.value.isEditing)

    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()

    assertTrue(typePickerOpened)
    assertTrue(datePickerOpened)
    assertTrue(startTimePickerOpened)
    assertTrue(endTimePickerOpened)
    assertTrue(subjectPickerOpened)
  }

  /** Tapping a location picker result end to end sets the ViewModel's location. */
  @Test
  fun editScreenSelectingLocationFromPickerUpdatesViewModel() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val results = listOf(sampleLocation("BC 01"))
    runBlocking {
      whenever(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
          .thenReturn(Result.success(results))
    }
    val viewModel = createViewModel()

    composeTestRule.setContent {
      EditEventScreen(
          eventId = storedEvent.id,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
      )
    }
    finishPendingComposeAndViewModelWork()

    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).performScrollTo().performClick()
    finishPendingComposeAndViewModelWork()
    composeTestRule
        .onNodeWithText(context.getString(R.string.location_picker_search_placeholder))
        .performTextInput("BC")
    finishPendingComposeAndViewModelWork()
    composeTestRule.onNodeWithText("BC 01").performClick()

    assertEquals("BC 01", viewModel.uiState.value.location)
  }

  @Test
  fun loadsExistingEventAndBackDoesNotSaveChanges() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = createViewModel()
    var backClicked = false
    composeTestRule.setContent {
      EditEventScreen(
          eventId = storedEvent.id,
          viewModel = viewModel,
          onBackClick = { backClicked = true },
          onEventSaved = {},
          onEventDelete = {},
      )
    }
    finishPendingComposeAndViewModelWork()
    assertTrue(viewModel.uiState.value.isEditing)

    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_edit_title))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertTextContains("27/10/2026")
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertTextContains("09:00")
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertTextContains("11:00")
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertTextContains("Operating Systems")
    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).assertTextContains("INF 1")
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).assertTextContains("Bring lecture notes")
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).assertIsDisplayed()

    composeTestRule.onNodeWithTag(Tags.BACK_BUTTON).performClick()

    assertTrue(backClicked)
    runBlocking { verify(eventsRepository, never()).saveEvent(any()) }
  }

  @Test
  fun saveButtonPersistsEditedEventAndSignalsSuccess() {
    val viewModel = createViewModel()
    var eventSaved = false
    composeTestRule.setContent {
      EditEventScreen(
          eventId = storedEvent.id,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = { eventSaved = true },
          onEventDelete = {},
      )
    }
    finishPendingComposeAndViewModelWork()
    assertTrue(viewModel.uiState.value.isEditing)

    viewModel.setLocation("INF 2")
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    assertTrue(eventSaved)
    val savedEvent = org.mockito.kotlin.argumentCaptor<PersonalEvent>()
    runBlocking { verify(eventsRepository).saveEvent(savedEvent.capture()) }
    assertEquals(storedEvent.id, savedEvent.firstValue.id)
    assertEquals("INF 2", savedEvent.firstValue.details.location)
  }

  @Test
  fun deleteButtonDeletesTheLoadedEventAndSignalsSuccess() {
    val viewModel = createViewModel()
    var eventDeleted = false
    composeTestRule.setContent {
      EditEventScreen(
          eventId = storedEvent.id,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = { eventDeleted = true },
      )
    }
    finishPendingComposeAndViewModelWork()
    assertTrue(viewModel.uiState.value.isEditing)

    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    assertTrue(eventDeleted)
    runBlocking { verify(eventsRepository).deleteEvent("user-1", storedEvent.id) }
  }

  @Test
  fun loadsAndDisplaysGroupSession_whenEventKindIsGroupSession() {
    val session =
        GroupSession(
            id = "session-1",
            createdBy = "user-1",
            memberIds = listOf("user-1"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Computer Networks",
                    startsAt = Instant.parse("2026-10-27T14:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T16:00:00Z"),
                    location = "BC 01",
                    description = "Midterm study",
                ),
            createdAt = Instant.parse("2026-10-20T09:00:00Z"),
        )
    runBlocking {
      whenever(groupSessionsRepository.getSession("session-1")).thenReturn(Result.success(session))
    }

    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "session-1",
          eventKind = EventKind.GROUP_SESSION,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
      )
    }

    finishPendingComposeAndViewModelWork()

    assertEquals(EventKind.GROUP_SESSION, viewModel.uiState.value.eventKind)
    assertFalse(viewModel.uiState.value.showTasksSection)
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertTextContains("Computer Networks")
  }

  @Test
  fun saveButton_updatesGroupSessionInGroupSessionsRepository() {
    val session =
        GroupSession(
            id = "session-1",
            createdBy = "user-1",
            memberIds = listOf("user-1", "user-2"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Original Subject",
                    startsAt = Instant.parse("2026-10-27T14:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T16:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-20T09:00:00Z"),
        )
    runBlocking {
      whenever(groupSessionsRepository.getSession("session-1")).thenReturn(Result.success(session))
      whenever(groupSessionsRepository.saveSession(any(), any())).thenReturn(Result.success(Unit))
    }

    var saved = false
    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "session-1",
          eventKind = EventKind.GROUP_SESSION,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = { saved = true },
          onEventDelete = {},
      )
    }

    finishPendingComposeAndViewModelWork()

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    finishPendingComposeAndViewModelWork()

    assertTrue(saved)
    val captor = org.mockito.kotlin.argumentCaptor<GroupSession>()
    runBlocking {
      verify(groupSessionsRepository).saveSession(org.mockito.kotlin.eq("user-1"), captor.capture())
    }
    val savedSession = captor.firstValue
    assertEquals("session-1", savedSession.id)
    assertEquals("user-1", savedSession.createdBy)
    assertEquals(listOf("user-1", "user-2"), savedSession.memberIds)
    assertEquals("Original Subject", savedSession.details.subject)
  }

  @Test
  fun editEventScreenPreview_rendersWithoutCrashing() {
    composeTestRule.setContent { EditEventScreenPreview() }
    composeTestRule.onNodeWithTag(Tags.SCREEN_CONTAINER).assertIsDisplayed()
  }

  @Test
  fun deleteButton_deletesGroupSessionFromGroupSessionsRepository() {
    val session =
        GroupSession(
            id = "session-1",
            createdBy = "user-1",
            memberIds = listOf("user-1"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Subject",
                    startsAt = Instant.parse("2026-10-27T14:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T16:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-20T09:00:00Z"),
        )
    runBlocking {
      whenever(groupSessionsRepository.getSession("session-1")).thenReturn(Result.success(session))
      whenever(groupSessionsRepository.deleteSession(any(), any())).thenReturn(Result.success(Unit))
    }

    var deleted = false
    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "session-1",
          eventKind = EventKind.GROUP_SESSION,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = { deleted = true },
      )
    }

    finishPendingComposeAndViewModelWork()

    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).performClick()
    finishPendingComposeAndViewModelWork()

    assertTrue(deleted)
    runBlocking { verify(groupSessionsRepository).deleteSession("user-1", "session-1") }
  }

  @Test
  fun editEventScreen_displaysGroupSessionTitle_whenEventKindIsGroupSession() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val session =
        GroupSession(
            id = "session-1",
            createdBy = "user-1",
            memberIds = listOf("user-1"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Subject",
                    startsAt = Instant.parse("2026-10-27T14:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T16:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-20T09:00:00Z"),
        )
    runBlocking {
      whenever(groupSessionsRepository.getSession("session-1")).thenReturn(Result.success(session))
    }

    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "session-1",
          eventKind = EventKind.GROUP_SESSION,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
      )
    }

    finishPendingComposeAndViewModelWork()

    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.group_session_edit_title))
  }

  @Test
  fun eventPlanningScreenStateless_displaysAddGroupSessionTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val addState = EventPlanningUiState(eventKind = EventKind.GROUP_SESSION)
    setEditStatelessScreen(uiState = addState, isEditing = false)
    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.group_session_add_title))
  }

  @Test
  fun eventPlanningScreenStateless_displaysEditGroupSessionTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val editState = EventPlanningUiState(eventKind = EventKind.GROUP_SESSION, isEditing = true)
    setEditStatelessScreen(uiState = editState, isEditing = true)
    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.group_session_edit_title))
  }

  /**
   * Verifies that the task management section is displayed when editing a personal event, but
   * hidden when editing a group session.
   */
  @Test
  fun editEventScreen_displaysTaskSection_forPersonalEvent() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "personal-1",
          eventKind = EventKind.PERSONAL,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
      )
    }
    finishPendingComposeAndViewModelWork()
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun editEventScreen_hidesTaskSection_forGroupSession() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      EditEventScreen(
          eventId = "group-1",
          eventKind = EventKind.GROUP_SESSION,
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onEventDelete = {},
      )
    }
    finishPendingComposeAndViewModelWork()
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
  }
}
