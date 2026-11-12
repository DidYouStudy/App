// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventsRepository
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.Screen
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

/** Tests the calendar route's lifecycle-driven date refresh. */
@RunWith(AndroidJUnit4::class)
class CalendarRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Routes each add-menu option to its matching callback. */
  @Test
  fun routeForwardsBothAddMenuCallbacks() {
    var eventClicks = 0
    var groupClicks = 0
    val authRepository = mock<AuthRepository>()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow<UserAccount?>(null))
    val viewModel = CalendarViewModel(authRepository, mock<EventsRepository>(), Clock.systemUTC())
    val lifecycleOwner = TestLifecycleOwner()
    composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        CalendarRoute(
            viewModel = viewModel,
            onAddEventClick = { eventClicks++ },
            onAddGroupSessionClick = { groupClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule.runOnIdle {
      assertEquals(1, eventClicks)
      assertEquals(0, groupClicks)
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()
    composeTestRule.runOnIdle {
      assertEquals(1, eventClicks)
      assertEquals(1, groupClicks)
    }
  }

  /** Uses the route's default Event action to navigate to AddEvent. */
  @Test
  fun routeDefaultEventOptionNavigatesToAddEvent() {
    val navigationActions = mock<NavigationActions>()
    val authRepository = mock<AuthRepository>()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow<UserAccount?>(null))
    val viewModel = CalendarViewModel(authRepository, mock<EventsRepository>(), Clock.systemUTC())
    val lifecycleOwner = TestLifecycleOwner()
    composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        CalendarRoute(navigationActions = navigationActions, viewModel = viewModel)
      }
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    verify(navigationActions).navigateTo(Screen.AddEvent)
  }

  /** Closes the Event menu option safely when the route has no navigation action. */
  @Test
  fun routeDefaultEventOptionWithoutNavigationDoesNothing() {
    val authRepository = mock<AuthRepository>()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow<UserAccount?>(null))
    val viewModel = CalendarViewModel(authRepository, mock<EventsRepository>(), Clock.systemUTC())
    val lifecycleOwner = TestLifecycleOwner()
    composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        CalendarRoute(viewModel = viewModel)
      }
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Polling while resumed updates today's date after local midnight without user input. */
  @Test
  fun routeAutomaticallyUpdatesTodayAfterMidnight() {
    val clock = MutableClock(Instant.parse("2026-10-28T23:59:59Z"))
    val authRepository = mock<AuthRepository>()
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow<UserAccount?>(null))
    val viewModel = CalendarViewModel(authRepository, mock<EventsRepository>(), clock)
    val lifecycleOwner = TestLifecycleOwner()
    composeTestRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        CalendarRoute(viewModel = viewModel)
      }
    }
    assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)

    val midnightDelayMillis = viewModel.uiState.value.midnightDelay.inWholeMilliseconds
    clock.currentInstant = Instant.parse("2026-10-29T00:00:00Z")
    shadowOf(Looper.getMainLooper()).idleFor(midnightDelayMillis, TimeUnit.MILLISECONDS)
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.today)
    }
  }

  /** A clock whose instant can be moved across midnight while the route is running. */
  private class MutableClock(
      var currentInstant: Instant,
      private val zone: ZoneId = ZoneOffset.UTC,
  ) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
  }

  /** Lifecycle owner held at RESUMED so route polling runs deterministically in this test. */
  private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
      get() = registry
  }
}
