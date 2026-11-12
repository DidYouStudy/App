// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.PersonalEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * UI tests for the Event details screen.
 *
 * The first group exercises the stateless UI with explicit state, the second group exercises the
 * stateful [EventDetailsScreen] wired to a real [EventDetailsViewModel] backed by mocked
 * repositories.
 *
 * Runs on the JVM with Robolectric. A test main dispatcher makes the ViewModel coroutines run
 * eagerly, so state changes are visible right after an interaction. The phone-sized screen keeps
 * the whole layout on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class EventDetailsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var authRepository: AuthRepository
  private lateinit var eventsRepository: EventsRepository

  private companion object {
    const val USER_ID = SAMPLE_OWNER_ID
    const val EVENT_ID = SAMPLE_EVENT_ID
    const val LOAD_ERROR = "Load failed (test)"
    const val DELETE_ERROR = "Delete failed (test)"
    const val SAVE_ERROR = "Save failed (test)"
  }

  // Helpers

  /** Shows the stateless screen; every callback records its name in [events]. */
  private fun setStatelessContent(
      events: MutableList<String> = mutableListOf(),
      display: EventDetailsDisplay? = sampleDisplay(),
      isLoading: Boolean = false,
      isBusy: Boolean = false,
      isDeleting: Boolean = false,
  ) {
    composeTestRule.setContent {
      EventDetailsStateless(
          display = display,
          isLoading = isLoading,
          isBusy = isBusy,
          isDeleting = isDeleting,
          onBack = { events += "back" },
          onToggleTask = { events += "toggle$it" },
          onEdit = { events += "edit" },
          onDelete = { events += "delete" },
      )
    }
  }

  /** Shows the stateful screen; every navigation callback records its name in [events]. */
  private fun setStatefulContent(
      viewModel: EventDetailsViewModel,
      events: MutableList<String> = mutableListOf(),
  ) {
    composeTestRule.setContent {
      EventDetailsScreen(
          eventId = EVENT_ID,
          onNavigateBack = { events += "back" },
          onEdit = { events += "edit:$it" },
          viewModel = viewModel,
      )
    }
  }

  private fun createViewModel() = EventDetailsViewModel(authRepository, eventsRepository)

  private fun stubGetEvent(result: Result<PersonalEvent>) {
    runBlocking { whenever(eventsRepository.getEvent(USER_ID, EVENT_ID)).thenReturn(result) }
  }

  /** Lets pending ViewModel work and recompositions finish. */
  private fun settle() {
    testDispatcher.scheduler.advanceUntilIdle()
    composeTestRule.waitForIdle()
  }

  /**
   * Stops the test clock from running past the snackbar's timeout, then advances far enough for it
   * to be composed and animated in. Call before the action that triggers the snackbar.
   */
  private fun holdSnackbarOnScreen() {
    composeTestRule.mainClock.autoAdvance = false
  }

  private fun showSnackbar() {
    composeTestRule.mainClock.advanceTimeBy(1_000)
  }

  private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    val user = UserAccount(uid = USER_ID, email = null, displayName = null, photoUrl = null)
    authRepository = mock()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow<UserAccount?>(user))
    eventsRepository = mock()
    runBlocking {
      whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
          .thenReturn(Result.success(Unit))
      whenever(eventsRepository.deleteEvent(any(), any())).thenReturn(Result.success(Unit))
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // Stateless screen: what is displayed

  @Test
  fun headerShowsEventDetailsTitleAndBackButton() {
    setStatelessContent()

    // The medium top app bar composes its title twice (collapsed and expanded).
    composeTestRule
        .onAllNodesWithTag(EventDetailsScreenTestTags.TITLE)
        .assertAny(hasText(string(R.string.event_details_title)))
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.BACK_BUTTON).assertIsDisplayed()
  }

  @Test
  fun loadedEventShowsAllItsDetails() {
    setStatelessContent()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.TYPE_BADGE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.SUBJECT)
        .assertIsDisplayed()
        .assertTextEquals("Algebra")
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DATE_TIME).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            string(R.string.event_details_date_time_range, "Nov 2, 2026", "10:15", "12:00")
        )
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOCATION).assertIsDisplayed()
    composeTestRule.onNodeWithText("CM5").assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DESCRIPTION_LABEL).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.event_details_description_label))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DESCRIPTION).assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Exercise session focusing on the new concepts of week 8.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.TASKS_LABEL).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.event_details_tasks_label)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.NOT_FOUND).assertDoesNotExist()
  }

  @Test
  fun everyTaskIsListedWithItsCompletionState() {
    setStatelessContent(
        display =
            sampleDisplay(
                tasks =
                    listOf(
                        EventTaskDisplay("Solve exercises 12, 13", false),
                        EventTaskDisplay("Read chapter 3 of textbook", true),
                    )
            )
    )

    composeTestRule.onNodeWithText("Solve exercises 12, 13").assertIsDisplayed()
    composeTestRule.onNodeWithText("Read chapter 3 of textbook").assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).assertIsOff()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(1)).assertIsOn()
  }

  @Test
  fun missingOptionalFieldsShowTheirFallbacks() {
    setStatelessContent(display = sampleDisplay(title = null, location = null, description = null))

    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.SUBJECT)
        .assertTextEquals(string(R.string.event_details_subject_fallback))
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOCATION).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(string(R.string.event_details_location_fallback))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.DESCRIPTION)
        .assertTextEquals(string(R.string.event_details_description_fallback))
  }

  @Test
  fun eventWithoutTasksHidesTheTasksSection() {
    setStatelessContent(display = sampleDisplay(tasks = emptyList()))

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.TASKS_LABEL).assertDoesNotExist()
    composeTestRule
        .onAllNodesWithTag(EventDetailsScreenTestTags.taskCheckbox(0))
        .assertCountEquals(0)
  }

  @Test
  fun actionButtonsShowTheirLabels() {
    setStatelessContent()

    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.event_details_edit))
    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.event_details_delete))
  }

  // Stateless screen: loading, missing event and busy states

  @Test
  fun loadingWithoutEventShowsSpinnerAndNoDetails() {
    setStatelessContent(display = null, isLoading = true, isBusy = true)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.NOT_FOUND).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.SUBJECT).assertDoesNotExist()
  }

  @Test
  fun missingEventShowsNotFoundMessage() {
    setStatelessContent(display = null)

    composeTestRule
        .onNodeWithTag(EventDetailsScreenTestTags.NOT_FOUND)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.event_details_not_found))
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun actionsAreDisabledWithoutAnEvent() {
    setStatelessContent(display = null)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun actionsAreEnabledForALoadedEvent() {
    setStatelessContent()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).assertIsEnabled()
  }

  @Test
  fun busyStateDisablesActionsAndTaskCheckboxes() {
    setStatelessContent(isBusy = true)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).assertIsNotEnabled()
  }

  @Test
  fun deletingShowsProgressInsteadOfTheDeleteLabel() {
    setStatelessContent(isBusy = true, isDeleting = true)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.event_details_delete)).assertDoesNotExist()
    composeTestRule.onNodeWithText(string(R.string.event_details_edit)).assertIsDisplayed()
  }

  @Test
  fun notDeletingShowsNoProgress() {
    setStatelessContent(isDeleting = false)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_PROGRESS).assertDoesNotExist()
  }

  // Stateless screen: each control triggers exactly its own callback

  @Test
  fun backButtonInvokesOnlyTheBackCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(listOf("back"), events)
  }

  @Test
  fun editButtonInvokesOnlyTheEditCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).performClick()

    assertEquals(listOf("edit"), events)
  }

  @Test
  fun deleteButtonInvokesOnlyTheDeleteCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).performClick()

    assertEquals(listOf("delete"), events)
  }

  @Test
  fun togglingATaskReportsItsIndex() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(1)).performClick()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).performClick()

    assertEquals(listOf("toggle1", "toggle0"), events)
  }

  @Test
  fun disabledActionsDoNotInvokeTheirCallbacks() {
    val events = mutableListOf<String>()
    setStatelessContent(events, isBusy = true)

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).performClick()

    assertTrue(events.isEmpty())
  }

  @Test
  fun defaultCallbacksDoNothingAndDoNotCrash() {
    composeTestRule.setContent { EventDetailsStateless(display = sampleDisplay()) }

    listOf(
            EventDetailsScreenTestTags.BACK_BUTTON,
            EventDetailsScreenTestTags.taskCheckbox(0),
            EventDetailsScreenTestTags.EDIT_BUTTON,
            EventDetailsScreenTestTags.DELETE_BUTTON,
        )
        .forEach { tag -> composeTestRule.onNodeWithTag(tag).performClick() }

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.SCREEN).assertIsDisplayed()
  }

  // Stateful screen: wiring to the ViewModel

  @Test
  fun statefulScreenLoadsAndShowsTheEvent() {
    stubGetEvent(Result.success(samplePersonalEvent()))
    setStatefulContent(createViewModel())
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.SUBJECT).assertTextEquals("Algebra")
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DATE_TIME).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.LOADING).assertDoesNotExist()
    verifyBlocking(eventsRepository) { getEvent(USER_ID, EVENT_ID) }
  }

  @Test
  fun statefulScreenShowsLoadErrorInSnackbarAndNotFoundMessage() {
    holdSnackbarOnScreen()
    stubGetEvent(Result.failure(RuntimeException(LOAD_ERROR)))
    setStatefulContent(createViewModel())
    settle()
    showSnackbar()

    composeTestRule
        .onNodeWithText(string(R.string.event_planning_error_failed_to_load_event))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.NOT_FOUND).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun statefulEditForwardsTheEventId() {
    val events = mutableListOf<String>()
    stubGetEvent(Result.success(samplePersonalEvent()))
    setStatefulContent(createViewModel(), events)
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).performClick()

    assertEquals(listOf("edit:$EVENT_ID"), events)
  }

  @Test
  fun statefulBackButtonInvokesTheNavigationCallback() {
    val events = mutableListOf<String>()
    stubGetEvent(Result.success(samplePersonalEvent()))
    setStatefulContent(createViewModel(), events)
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(listOf("back"), events)
  }

  @Test
  fun togglingATaskUpdatesTheScreenAndSavesTheChecklist() {
    stubGetEvent(Result.success(samplePersonalEvent()))
    setStatefulContent(createViewModel())
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).performClick()
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).assertIsOn()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(1)).assertIsOff()
    verifyBlocking(eventsRepository) {
      updateEventTasks(
          eq(USER_ID),
          eq(EVENT_ID),
          argThat { this[0].isCompleted && !this[1].isCompleted },
          any(),
      )
    }
  }

  @Test
  fun failedTaskSaveRollsBackTheCheckboxAndShowsTheError() {
    stubGetEvent(Result.success(samplePersonalEvent()))
    runBlocking {
      whenever(eventsRepository.updateEventTasks(any(), any(), any(), any()))
          .thenReturn(Result.failure(RuntimeException(SAVE_ERROR)))
    }
    setStatefulContent(createViewModel())
    settle()
    holdSnackbarOnScreen()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    showSnackbar()

    composeTestRule
        .onNodeWithText(string(R.string.event_planning_error_failed_to_edit_event))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.taskCheckbox(0)).assertIsOff()
  }

  @Test
  fun successfulDeleteDeletesThroughTheRepositoryAndNavigatesBack() {
    val events = mutableListOf<String>()
    stubGetEvent(Result.success(samplePersonalEvent()))
    setStatefulContent(createViewModel(), events)
    settle()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).performClick()
    settle()

    assertEquals(listOf("back"), events)
    verifyBlocking(eventsRepository) { deleteEvent(USER_ID, EVENT_ID) }
  }

  @Test
  fun failedDeleteShowsTheErrorAndStaysOnTheScreen() {
    val events = mutableListOf<String>()
    stubGetEvent(Result.success(samplePersonalEvent()))
    runBlocking {
      whenever(eventsRepository.deleteEvent(any(), any()))
          .thenReturn(Result.failure(RuntimeException(DELETE_ERROR)))
    }
    setStatefulContent(createViewModel(), events)
    settle()
    holdSnackbarOnScreen()

    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.DELETE_BUTTON).performClick()
    testDispatcher.scheduler.advanceUntilIdle()
    showSnackbar()

    composeTestRule
        .onNodeWithText(string(R.string.event_planning_error_failed_to_delete_event))
        .assertIsDisplayed()
    assertFalse(events.contains("back"))
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.SUBJECT).assertIsDisplayed()
  }
}
