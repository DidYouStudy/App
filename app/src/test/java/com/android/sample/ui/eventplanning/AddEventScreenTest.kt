// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
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

  private val zone = ZoneId.of("Europe/Berlin")
  private val now = Instant.parse("2026-10-27T12:00:00Z")
  private val clock = Clock.fixed(now, zone)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock(AuthRepository::class.java)
    eventsRepository = mock(EventsRepository::class.java)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
    `when`(eventsRepository.newEventId()).thenReturn("event-1")
    `when`(eventsRepository.observeSubjects(any())).thenReturn(flowOf(Result.success(emptyList())))
    runBlocking { `when`(eventsRepository.saveEvent(any())).thenReturn(Result.success(Unit)) }
    currentUserFlow.value =
        UserAccount(uid = "user123", email = null, displayName = null, photoUrl = null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() = EventPlanningViewModel(authRepository, eventsRepository, clock)

  @Test
  fun statelessScreenRendersTopBarFormAndSaveButtonWhenNotSaving() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var saveClicked = false
    val uiState = EventPlanningUiState(isSaving = false)

    composeTestRule.setContent {
      AddEventScreenStateless(
          uiState = uiState,
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationChange = {},
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
      AddEventScreenStateless(
          uiState = uiState,
          onTypeClick = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationChange = {},
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
    viewModel.setDate(LocalDate.of(2026, 10, 27))
    viewModel.setStartTime(LocalTime.of(9, 0))
    viewModel.setEndTime(LocalTime.of(11, 0))

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    // Event saved callback should be triggered via LaunchedEffect
    assertTrue(eventSaved)
  }

  @Test
  fun statefulScreenPickerCallbacksAndInputHandlersWork() {
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

    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).performTextInput("Library")
    assertEquals("Library", viewModel.uiState.value.location)

    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).performTextInput("Quiet zone")
    assertEquals("Quiet zone", viewModel.uiState.value.description)
  }
}
