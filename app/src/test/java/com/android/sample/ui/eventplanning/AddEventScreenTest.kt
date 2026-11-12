// Co-authored-by: Copilot
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddEventScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val currentUserFlow = MutableStateFlow<UserAccount?>(null)
  private lateinit var authRepository: AuthRepository
  private lateinit var eventsRepository: EventsRepository
  private lateinit var campusLocationRepository: CampusLocationRepository
  private lateinit var groupSessionsRepository: GroupSessionsRepository

  private val zone = ZoneId.of("Europe/Berlin")
  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val clock = Clock.fixed(now, zone)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock(AuthRepository::class.java)
    eventsRepository = mock(EventsRepository::class.java)
    campusLocationRepository = mock(CampusLocationRepository::class.java)
    groupSessionsRepository = mock(GroupSessionsRepository::class.java)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
    `when`(eventsRepository.newEventId()).thenReturn("event-1")
    `when`(eventsRepository.observeSubjects(any())).thenReturn(flowOf(Result.success(emptyList())))
    runBlocking {
      `when`(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit))
      `when`(campusLocationRepository.search(any())).thenReturn(Result.success(emptyList()))
    }
    currentUserFlow.value =
        UserAccount(uid = "user123", email = null, displayName = null, photoUrl = null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      EventPlanningViewModel(
          authRepository,
          eventsRepository,
          campusLocationRepository,
          groupSessionsRepository,
          clock,
      )

  /** Builds a minimal campus location for tests; only the name is usually relevant. */
  private fun sampleLocation(name: String): CampusLocation =
      CampusLocation(
          name = name,
          zone = "BC",
          type = CampusLocationType.LECTURE_ROOM,
          floor = null,
          location = Location(latitude = 46.52, longitude = 6.56),
      )

  @Test
  fun statelessScreenRendersTopBarFormAndSaveButtonWhenNotSaving() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var saveClicked = false
    val uiState = EventPlanningUiState(isSaving = false)

    composeTestRule.setContent {
      EventPlanningScreenStateless(
          uiState = uiState,
          onBackClick = {},
          onTypeChange = {},
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = { saveClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCREEN_CONTAINER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).assertIsDisplayed()

    val saveButton = composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON)
    saveButton.assertIsDisplayed().assertIsEnabled().performClick()
    assertTrue(saveClicked)

    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_save_button))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON_PROGRESS).assertDoesNotExist()
  }

  @Test
  fun statelessScreenDisablesSaveButtonAndDisplaysProgressWhenSaving() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState = EventPlanningUiState(isSaving = true)

    composeTestRule.setContent {
      EventPlanningScreenStateless(
          uiState = uiState,
          onBackClick = {},
          onTypeChange = {},
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = {},
      )
    }

    val saveButton = composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON)
    saveButton.assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON_PROGRESS).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_save_button))
        .assertDoesNotExist()
  }

  @Test
  fun statefulScreenWiresViewModelTopBarAndEventSavedCallback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var backClicked = false
    var eventSaved = false
    val viewModel = createViewModel()

    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          onBackClick = { backClicked = true },
          onEventSaved = { eventSaved = true },
      )
    }

    // Check TopAppBar title and Back button
    composeTestRule.onNodeWithTag(Tags.TOP_APP_BAR).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_add_title))

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.button_go_back))
        .performClick()
    assertTrue(backClicked)

    // Fill valid form fields via ViewModel and trigger addEvent
    viewModel.setType(EventType.STUDY_SESSION)
    viewModel.setSubject("Algebra")
    viewModel.setDate(LocalDate.of(2026, 10, 28))
    viewModel.setStartTime(LocalTime.of(9, 0))
    viewModel.setEndTime(LocalTime.of(11, 0))

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    // Event saved callback should be triggered via LaunchedEffect
    assertTrue(eventSaved)
  }

  @Test
  fun statefulScreenResetsPreviouslyLoadedEditForm() {
    val existingEvent =
        PersonalEvent(
            id = "existing-event",
            ownerId = "user123",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Algorithms",
                    startsAt = Instant.parse("2026-10-27T08:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T10:00:00Z"),
                    location = "INF 1",
                    description = "Review graphs",
                ),
            createdAt = Instant.parse("2026-10-20T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-20T09:00:00Z"),
        )
    runBlocking {
      `when`(eventsRepository.getEvent("user123", existingEvent.id))
          .thenReturn(Result.success(existingEvent))
    }
    val viewModel = createViewModel()
    viewModel.loadEvent(existingEvent.id)
    assertTrue(viewModel.uiState.value.isEditing)

    composeTestRule.setContent {
      AddEventScreen(viewModel = viewModel, onBackClick = {}, onEventSaved = {})
    }
    composeTestRule.waitForIdle()

    assertFalse(viewModel.uiState.value.isEditing)
    assertEquals(null, viewModel.uiState.value.date)
    assertEquals("", viewModel.uiState.value.subject)
    assertEquals("", viewModel.uiState.value.location)
  }

  @Test
  fun statefulScreenPickerCallbacksAndInputHandlersWork() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var pickType = false
    var pickDate = false
    var pickStartTime = false
    var pickEndTime = false
    var pickSubject = false
    val viewModel = createViewModel()

    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onPickTypeClick = { pickType = true },
          onPickDateClick = { pickDate = true },
          onPickStartTimeClick = { pickStartTime = true },
          onPickEndTimeClick = { pickEndTime = true },
          onPickSubjectClick = { pickSubject = true },
      )
    }

    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    assertTrue(pickType)

    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    assertTrue(pickDate)

    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    assertTrue(pickStartTime)

    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    assertTrue(pickEndTime)

    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()
    assertTrue(pickSubject)

    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).performClick()
    composeTestRule
        .onNodeWithText(context.getString(R.string.location_picker_dialog_title))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).performTextInput("Quiet zone")
    assertEquals("Quiet zone", viewModel.uiState.value.description)
  }

  /** Tapping a location picker result end to end sets the ViewModel's location. */
  @Test
  fun statefulScreenSelectingLocationFromPickerUpdatesViewModel() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val results = listOf(sampleLocation("BC 01"))
    runBlocking {
      `when`(campusLocationRepository.search(CampusLocationFilter(query = "BC")))
          .thenReturn(Result.success(results))
    }
    val viewModel = createViewModel()

    composeTestRule.setContent {
      AddEventScreen(viewModel = viewModel, onBackClick = {}, onEventSaved = {})
    }

    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).performClick()
    composeTestRule
        .onNodeWithText(context.getString(R.string.location_picker_search_placeholder))
        .performTextInput("BC")
    composeTestRule.onNodeWithText("BC 01").performClick()

    assertEquals("BC 01", viewModel.uiState.value.location)
  }

  /** Tapping the clear ("x") button on the location pill resets the ViewModel's location. */
  @Test
  fun statefulScreenClearingLocationUpdatesViewModel() {
    val viewModel = createViewModel()
    viewModel.setLocation("BC 01")

    composeTestRule.setContent {
      AddEventScreen(viewModel = viewModel, onBackClick = {}, onEventSaved = {})
    }

    composeTestRule
        .onNodeWithTag(
            com.android.sample.ui.sharedComponents.FormContentTestTags.LOCATION_CLEAR_BUTTON,
            useUnmergedTree = true,
        )
        .performClick()

    assertEquals("", viewModel.uiState.value.location)
  }

  @Test
  fun unsubmittedTaskTextSurvivesScreenRestoration() {
    val restorationTester = StateRestorationTester(composeTestRule)
    val draftTask = "Review analysis lecture notes"

    restorationTester.setContent {
      EventPlanningScreenStateless(
          uiState = EventPlanningUiState(),
          onBackClick = {},
          onTypeChange = {},
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = {},
      )
    }

    // Scroll to the task field before checking visibility and typing
    val taskInputNode = composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON)
    taskInputNode.performScrollTo().assertIsDisplayed().performTextInput(draftTask)
    taskInputNode.assertTextContains(draftTask)

    // Simulate configuration change / Activity recreation
    restorationTester.emulateSavedInstanceStateRestore()

    // Verify unsubmitted text was saved and restored
    composeTestRule
        .onNodeWithTag(Tags.ADD_TASK_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextContains(draftTask)
  }

  @Test
  fun restoredTaskCanBeSubmittedWithImeAction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val restorationTester = StateRestorationTester(composeTestRule)
    var submittedTask: String? = null
    val draftTask = "Prepare lab presentation"

    restorationTester.setContent {
      EventPlanningScreenStateless(
          uiState = EventPlanningUiState(),
          onBackClick = {},
          onTypeChange = {},
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = { task -> submittedTask = task },
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = {},
      )
    }

    val taskInputNode = composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON)
    taskInputNode.performScrollTo().performTextInput(draftTask)

    restorationTester.emulateSavedInstanceStateRestore()

    // Scroll back into view if needed and trigger keyboard 'Done'
    taskInputNode.performScrollTo().performImeAction()

    // Verify task passed to callback and input reverted to placeholder
    assertEquals(draftTask, submittedTask)
    taskInputNode.assertTextContains(
        context.getString(R.string.event_planning_add_task_placeholder)
    )
  }

  @Test
  fun displaysRequiredFieldsErrorBannerWhenValidationFails() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState =
        EventPlanningUiState(
            date = null,
            startTime = null,
            endTime = null,
            error = EventPlanningError.REQUIRED_FIELDS_MISSING,
        )

    composeTestRule.setContent {
      EventPlanningScreenStateless(
          uiState = uiState,
          onBackClick = {},
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
          onSaveClick = {},
      )
    }

    composeTestRule
        .onNodeWithTag(Tags.ERROR_TEXT)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_error_required_fields))
  }

  @Test
  fun displaysSaveFailedErrorBannerWhenFieldsCompleteButBackendFails() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState =
        EventPlanningUiState(
            type = EventType.OTHER,
            date = LocalDate.of(2026, 11, 1),
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            error = EventPlanningError.FAILED_TO_ADD_EVENT, // Complete fields with backend error
        )

    composeTestRule.setContent {
      EventPlanningScreenStateless(
          uiState = uiState,
          onBackClick = {},
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
          onSaveClick = {},
      )
    }

    composeTestRule
        .onNodeWithTag(Tags.ERROR_TEXT)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_error_failed_to_add_event))
  }

  @Test
  fun validationErrorDisappearsAfterFourSeconds() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = createViewModel()
    viewModel.addEvent()
    assertEquals(EventPlanningError.REQUIRED_FIELDS_MISSING, viewModel.uiState.value.error)
    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.setContent {
      AddEventScreen(viewModel = viewModel, onBackClick = {}, onEventSaved = {})
    }

    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(Tags.ERROR_TEXT)
        .assertTextEquals(context.getString(R.string.event_planning_error_required_fields))

    composeTestRule.mainClock.advanceTimeBy(3_900)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(Tags.ERROR_TEXT).assertIsDisplayed()

    composeTestRule.mainClock.advanceTimeBy(200)
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag(Tags.ERROR_TEXT).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun statefulScreenLaunchesInternalPickersWhenCallbacksAreNull() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = createViewModel()

    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          onBackClick = {},
          onEventSaved = {},
          onPickDateClick = null,
          onPickStartTimeClick = null,
          onPickEndTimeClick = null,
          onPickSubjectClick = null,
      )
    }

    // Trigger internal Date Picker dialog
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

    // Trigger internal Start Time Picker dialog
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

    // Trigger internal End Time Picker dialog
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

    // Trigger internal Subject Picker dialog
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()
    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
  }

  @Test
  fun addEventScreen_withGroupSessionKind_configuresGroupSessionAndHidesTasks() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          initialKind = EventKind.GROUP_SESSION,
          onBackClick = {},
          onEventSaved = {},
      )
    }

    assertEquals(EventKind.GROUP_SESSION, viewModel.uiState.value.eventKind)
    assertFalse(viewModel.uiState.value.showTasksSection)
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun addEventScreen_withGroupSessionKind_savesToGroupSessionsRepository() {
    runBlocking {
      `when`(groupSessionsRepository.newSessionId()).thenReturn("session-1")
      `when`(groupSessionsRepository.saveSession(any(), any())).thenReturn(Result.success(Unit))
    }
    val viewModel = createViewModel()
    var savedCalled = false

    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          initialKind = EventKind.GROUP_SESSION,
          onBackClick = {},
          onEventSaved = { savedCalled = true },
      )
    }

    viewModel.setDate(LocalDate.of(2026, 11, 10))
    viewModel.setStartTime(LocalTime.of(10, 0))
    viewModel.setEndTime(LocalTime.of(11, 0))
    viewModel.setSubject("Distributed Systems")

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    assertTrue(savedCalled)
    val captor = org.mockito.kotlin.argumentCaptor<com.android.sample.model.event.GroupSession>()
    runBlocking {
      org.mockito.kotlin
          .verify(groupSessionsRepository)
          .saveSession(org.mockito.kotlin.eq("user123"), captor.capture())
    }
    val savedSession = captor.firstValue
    assertEquals("session-1", savedSession.id)
    assertEquals("user123", savedSession.createdBy)
    assertEquals(listOf("user123"), savedSession.memberIds)
    assertEquals("Distributed Systems", savedSession.details.subject)
  }

  @Test
  fun addEventScreenPreview_rendersWithoutCrashing() {
    composeTestRule.setContent { AddEventScreenPreview() }
    composeTestRule.onNodeWithTag(Tags.SCREEN_CONTAINER).assertIsDisplayed()
  }

  @Test
  fun addEventScreen_backHandler_triggersOnBackClick() {
    var backClicked = false
    val viewModel = createViewModel()
    viewModel.setSubject("Draft Subject")
    var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null
    composeTestRule.setContent {
      backDispatcher =
          androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
              ?.onBackPressedDispatcher
      AddEventScreen(
          viewModel = viewModel,
          onBackClick = { backClicked = true },
          onEventSaved = {},
      )
    }

    composeTestRule.runOnUiThread { backDispatcher?.onBackPressed() }
    assertTrue(backClicked)
    assertTrue(viewModel.uiState.value.subject.isEmpty())
  }

  @Test
  fun addEventScreen_displaysGroupSessionTitle_whenInitialKindIsGroupSession() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val viewModel = createViewModel()
    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          initialKind = EventKind.GROUP_SESSION,
          onBackClick = {},
          onEventSaved = {},
      )
    }

    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.group_session_add_title))
  }

  /**
   * Verifies that the task management section is displayed when planning a personal event, but
   * hidden when planning a group session.
   */
  @Test
  fun addEventScreen_displaysTaskSection_forPersonalEvent() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          initialKind = EventKind.PERSONAL,
          onBackClick = {},
          onEventSaved = {},
      )
    }
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun addEventScreen_hidesTaskSection_forGroupSession() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      AddEventScreen(
          viewModel = viewModel,
          initialKind = EventKind.GROUP_SESSION,
          onBackClick = {},
          onEventSaved = {},
      )
    }
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
  }
}
