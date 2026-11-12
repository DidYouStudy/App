// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.DidYouStudyApp
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.ui.auth.TestAuthRepository
import com.android.sample.ui.calendar.CalendarTestTags
import com.android.sample.ui.eventplanning.EventDetailsScreenTestTags as DetailsTags
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as EditTags
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/** Exercises Calendar, Details, and Edit through the production navigation graph. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CalendarEventNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val userId = "calendar-navigation-user"
  private val previousMonday =
      LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
  private val otherEvent = event("other-event", previousMonday, "Linear algebra")
  private val selectedEvent =
      event("calendar-event?selected=2", previousMonday.plusDays(1), "Operating systems")
  private val selectedCardTag = CalendarTestTags.eventCard(selectedEvent.id, dayIndex = 1)
  private val otherCardTag = CalendarTestTags.eventCard(otherEvent.id, dayIndex = 0)
  private val events = TestEventsRepository(InMemoryEventsRepository())

  /** Seeds two distinguishable cards and opens the week containing them. */
  @Before
  fun openCalendarWeek() {
    val auth =
        TestAuthRepository().apply { currentUser.value = UserAccount(userId, null, null, null) }
    val preferences = mock<PreferencesRepository>()
    runBlocking {
      whenever(preferences.getPreferences(userId))
          .thenReturn(Result.success(UserPreferences(userId)))
      events.saveEvents(listOf(otherEvent, selectedEvent)).getOrThrow()
    }
    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    ProfileRepositoryProvider.setRepository(mock<ProfileRepository>())
    EventsRepositoryProvider.setRepository(events)
    GroupSessionsRepositoryProvider.setRepository(InMemoryGroupSessionsRepository())

    composeRule.setContent { DidYouStudyApp() }
    composeRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeRule.onNodeWithTag(otherCardTag).assertIsDisplayed()
    composeRule.onNodeWithTag(selectedCardTag).assertIsDisplayed()
  }

  /** Clears the provider overrides so each test starts with a fresh signed-in session. */
  @After
  fun clearProviders() {
    AuthRepositoryProvider.setRepository(null)
    PreferencesRepositoryProvider.setRepository(null)
    ProfileRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
    GroupSessionsRepositoryProvider.setRepository(null)
  }

  /** Repeated card and Back taps still take a single trip to Details and back. */
  @Test
  fun repeatedCardAndBackTapsReturnToTheSelectedCalendarWeek() {
    composeRule.onNodeWithTag(selectedCardTag).performTouchInput { repeat(3) { click(center) } }
    composeRule.onNodeWithTag(DetailsTags.SUBJECT).assertTextContains("Operating systems")

    composeRule.onNodeWithTag(DetailsTags.BACK_BUTTON).performTouchInput {
      repeat(3) { click(center) }
    }

    assertCalendarWeekIsDisplayed()
    composeRule.onNodeWithTag(selectedCardTag).assertIsDisplayed()
  }

  /** Edit loads the selected event, and Back discards edits and returns to its Details. */
  @Test
  fun editBackDiscardsChangesAndReturnsToDetails() {
    openEdit()
    replaceDescription("Unsaved description")

    composeRule.onNodeWithTag(EditTags.BACK_BUTTON).performClick()

    composeRule.onNodeWithTag(DetailsTags.DESCRIPTION).assertTextContains("Review the notes")
    composeRule.onNodeWithTag(DetailsTags.BACK_BUTTON).performClick()
    assertCalendarWeekIsDisplayed()
  }

  /** System Back follows the same two steps as the in-app back arrows. */
  @Test
  fun systemBackFromEditReturnsThroughDetailsToCalendar() {
    openEdit()

    pressSystemBack()
    composeRule.onNodeWithTag(DetailsTags.SUBJECT).assertTextContains("Operating systems")
    pressSystemBack()

    assertCalendarWeekIsDisplayed()
  }

  /** Saving updates the same event and displays its persisted values on return to Details. */
  @Test
  fun editSaveReturnsToDetailsWithTheUpdatedEvent() {
    openEdit()
    replaceDescription("Bring the practice exam")

    composeRule.onNodeWithTag(EditTags.SAVE_BUTTON).performTouchInput {
      repeat(3) { click(center) }
    }

    composeRule.onNodeWithTag(DetailsTags.DESCRIPTION).assertTextContains("Bring the practice exam")
    assertEquals(1, events.saveAttempts)
    val savedEvent = runBlocking { events.getEvent(userId, selectedEvent.id).getOrThrow() }
    assertEquals("Bring the practice exam", savedEvent.details.description)
    composeRule.onNodeWithTag(DetailsTags.BACK_BUTTON).performClick()
    assertCalendarWeekIsDisplayed()
  }

  /** Deleting in Details returns to Calendar and removes the selected card. */
  @Test
  fun detailsDeleteReturnsToCalendar() {
    openDetails()

    composeRule.onNodeWithTag(DetailsTags.DELETE_BUTTON).performTouchInput {
      repeat(3) { click(center) }
    }

    assertCalendarWeekIsDisplayed()
    composeRule.onNodeWithTag(selectedCardTag).assertDoesNotExist()
    assertEquals(1, events.deleteAttempts)
  }

  /** Deleting in Edit also removes the deleted event's Details entry. */
  @Test
  fun editDeleteReturnsDirectlyToCalendar() {
    openEdit()

    composeRule.onNodeWithTag(EditTags.DELETE_BUTTON).performTouchInput {
      repeat(3) { click(center) }
    }

    assertCalendarWeekIsDisplayed()
    composeRule.onNodeWithTag(selectedCardTag).assertDoesNotExist()
    assertEquals(1, events.deleteAttempts)
  }

  /** A failed write keeps the user on the screen where the action started. */
  @Test
  fun failedWritesKeepTheCurrentScreenAndEvent() {
    events.rejectWrites = true
    openDetails()

    composeRule.onNodeWithTag(DetailsTags.DELETE_BUTTON).performClick()
    composeRule.onNodeWithTag(DetailsTags.SUBJECT).assertTextContains("Operating systems")
    assertEquals(1, events.deleteAttempts)
    composeRule.onNodeWithTag(DetailsTags.EDIT_BUTTON).performClick()
    replaceDescription("Unsaved description")

    composeRule.onNodeWithTag(EditTags.SAVE_BUTTON).performClick()
    composeRule.onNodeWithTag(EditTags.ERROR_TEXT).assertIsDisplayed()
    assertEquals(1, events.saveAttempts)
    composeRule.onNodeWithTag(EditTags.DELETE_BUTTON).performClick()
    composeRule.onNodeWithTag(EditTags.ERROR_TEXT).assertIsDisplayed()
    assertEquals(2, events.deleteAttempts)

    val storedEvent = runBlocking { events.getEvent(userId, selectedEvent.id).getOrThrow() }
    assertEquals(selectedEvent, storedEvent)
  }

  /** Opens the second card and verifies its identity from the rendered subject. */
  private fun openDetails() {
    composeRule.onNodeWithTag(selectedCardTag).performClick()
    composeRule.onNodeWithTag(DetailsTags.SUBJECT).assertTextContains("Operating systems")
  }

  /** Opens Edit and verifies the form was populated for the selected event. */
  private fun openEdit() {
    openDetails()
    composeRule.onNodeWithTag(DetailsTags.EDIT_BUTTON).performClick()
    composeRule.onNodeWithTag(EditTags.SUBJECT_SELECTOR).assertTextContains("Operating systems")
  }

  /** Edits a field whose value is also visible on Details. */
  private fun replaceDescription(value: String) {
    composeRule
        .onNodeWithTag(EditTags.DESCRIPTION_INPUT)
        .performScrollTo()
        .performTextReplacement(value)
  }

  /** Confirms Calendar kept its week and its unrelated event. */
  private fun assertCalendarWeekIsDisplayed() {
    composeRule.onNodeWithTag(CalendarTestTags.dayNumber(previousMonday)).assertIsDisplayed()
    composeRule.onNodeWithTag(otherCardTag).assertIsDisplayed()
  }

  /** Sends Back through the activity dispatcher used by the navigation graph. */
  private fun pressSystemBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
  }

  /** Places a two-hour card within the calendar's initial visible time range. */
  private fun event(id: String, day: LocalDate, subject: String): PersonalEvent {
    val start = day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
    return PersonalEvent(
        id = id,
        ownerId = userId,
        details =
            EventDetails(
                type = EventType.STUDY_SESSION,
                subject = subject,
                startsAt = start,
                endsAt = start.plus(2, ChronoUnit.HOURS),
                description = "Review the notes",
            ),
        createdAt = start,
    )
  }

  /** Uses normal in-memory reads and records writes, which can be made to fail. */
  private class TestEventsRepository(private val delegate: EventsRepository) :
      EventsRepository by delegate {
    var rejectWrites = false
    var saveAttempts = 0
      private set

    var deleteAttempts = 0
      private set

    override suspend fun saveEvent(event: PersonalEvent): Result<Unit> {
      saveAttempts++
      if (rejectWrites) return Result.failure(EventsException(EventsError.STORAGE_FAILURE))
      return delegate.saveEvent(event)
    }

    override suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit> {
      deleteAttempts++
      if (rejectWrites) return Result.failure(EventsException(EventsError.STORAGE_FAILURE))
      return delegate.deleteEvent(ownerId, eventId)
    }
  }
}
