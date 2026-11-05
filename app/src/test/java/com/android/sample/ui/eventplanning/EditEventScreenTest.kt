// Co-authored-by: Copilot
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.PersonalEvent
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
  private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneId.of("Europe/Berlin"))
  private val storedEvent =
      PersonalEvent(
          id = "event-1",
          ownerId = "user-1",
          type = EventType.COURSE,
          subject = "Operating Systems",
          startsAt = Instant.parse("2026-10-27T08:00:00Z"),
          endsAt = Instant.parse("2026-10-27T10:00:00Z"),
          createdAt = Instant.parse("2026-10-20T09:00:00Z"),
          updatedAt = Instant.parse("2026-10-20T09:00:00Z"),
          location = "INF 1",
          description = "Bring lecture notes",
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
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() = EventPlanningViewModel(authRepository, eventsRepository, clock)

  /** Renders the shared stateless form explicitly in Edit mode for focused UI behavior tests. */
  private fun setEditStatelessScreen(
      uiState: EventPlanningUiState,
      onBackClick: () -> Unit = {},
      onSaveClick: () -> Unit = {},
      onDeleteClick: (() -> Unit)? = null,
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
          onLocationChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
          onSaveClick = onSaveClick,
          onDeleteClick = onDeleteClick,
          isEditing = true,
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
    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).assertTextContains("INF 1")
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).assertTextContains("Bring lecture notes")
    composeTestRule.onNodeWithText("Review chapter").performScrollTo().assertIsDisplayed()
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
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitUntil { viewModel.uiState.value.isEditing }

    composeTestRule
        .onNodeWithTag(Tags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.event_planning_edit_title))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertTextContains("27/10/2026")
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertTextContains("09:00")
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertTextContains("11:00")
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertTextContains("Operating Systems")
    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).assertTextContains("INF 1")
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
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitUntil { viewModel.uiState.value.isEditing }

    viewModel.setLocation("INF 2")
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    assertTrue(eventSaved)
    val savedEvent = org.mockito.kotlin.argumentCaptor<PersonalEvent>()
    runBlocking { verify(eventsRepository).saveEvent(savedEvent.capture()) }
    assertEquals(storedEvent.id, savedEvent.firstValue.id)
    assertEquals("INF 2", savedEvent.firstValue.location)
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
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitUntil { viewModel.uiState.value.isEditing }

    composeTestRule.onNodeWithTag(Tags.DELETE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()

    assertTrue(eventDeleted)
    runBlocking { verify(eventsRepository).deleteEvent("user-1", storedEvent.id) }
  }
}
