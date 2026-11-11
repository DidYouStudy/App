// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [sessionStatusLabel]: it picks the right unit (minutes, hours, days) and the right
 * plural form from the `group_session_status_in_*` plurals resources.
 */
@RunWith(AndroidJUnit4::class)
class SessionStatusLabelTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2026-10-29T12:00:00Z")

  /** Renders [sessionStatusLabel] for a session starting [offset] after [now]. */
  private fun labelFor(offset: Duration): String {
    var label = ""
    composeTestRule.setContent { label = sessionStatusLabel(start = now.plus(offset), now = now) }
    composeTestRule.waitForIdle()
    return label
  }

  @Test
  fun pastStart_isStarted() {
    assertEquals("Started", labelFor(Duration.ofMinutes(-10)))
  }

  @Test
  fun startNow_isStarted() {
    assertEquals("Started", labelFor(Duration.ZERO))
  }

  @Test
  fun oneMinute_usesSingularMinuteForm() {
    assertEquals("In 1 min", labelFor(Duration.ofMinutes(1)))
  }

  @Test
  fun underAnHour_isInMinutes() {
    assertEquals("In 59 min", labelFor(Duration.ofMinutes(59)))
  }

  @Test
  fun exactlyOneHour_usesSingularHourForm() {
    assertEquals("In 1 hour", labelFor(Duration.ofMinutes(60)))
  }

  @Test
  fun severalHours_usesPluralHourForm() {
    assertEquals("In 5 hours", labelFor(Duration.ofHours(5)))
  }

  @Test
  fun justUnderADay_staysInHours() {
    assertEquals("In 23 hours", labelFor(Duration.ofHours(24).minusMinutes(1)))
  }

  @Test
  fun exactlyOneDay_usesSingularDayForm() {
    assertEquals("In 1 day", labelFor(Duration.ofDays(1)))
  }

  @Test
  fun severalDays_usesPluralDayForm() {
    assertEquals("In 3 days", labelFor(Duration.ofDays(3)))
  }
}
