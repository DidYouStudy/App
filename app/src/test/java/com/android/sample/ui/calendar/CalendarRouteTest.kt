// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventsRepository
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
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

/** Tests the calendar route's lifecycle-driven date refresh. */
@RunWith(AndroidJUnit4::class)
class CalendarRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Polling while resumed updates today's date after local midnight without user input. */
  @Test
  fun routeAutomaticallyUpdatesTodayAfterMidnight() {
    val clock = MutableClock(Instant.parse("2026-10-28T23:59:00Z"))
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

    clock.currentInstant = Instant.parse("2026-10-29T00:01:00Z")
    shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
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
