// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.session

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests minute-boundary scheduling from an injected clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTickerTest {
  /** Ticks after forty seconds, then every sixty seconds, from a partial minute. */
  @Test
  fun partialMinuteEmissions() = runTest {
    assertTickTimes("2026-10-28T12:00:20Z", listOf(40_000L, 100_000L, 160_000L))
  }

  /** Ticks every sixty seconds when the clock is exactly on a boundary. */
  @Test
  fun exactMinuteEmissions() = runTest {
    assertTickTimes("2026-10-28T12:00:00Z", listOf(60_000L, 120_000L, 180_000L))
  }

  /** Rounds a sub-millisecond wait up so emissions do not precede the boundary. */
  @Test
  fun subMillisecondRemainderWaitsUntilBoundary() = runTest {
    assertTickTimes(
        "2026-10-28T12:00:59.999500Z",
        listOf(1L, 60_001L, 120_001L),
    )
  }

  private suspend fun kotlinx.coroutines.test.TestScope.assertTickTimes(
      base: String,
      expected: List<Long>,
  ) {
    val baseInstant = Instant.parse(base)
    val clock =
        object : Clock() {
          override fun getZone() = ZoneOffset.UTC

          override fun withZone(zone: java.time.ZoneId) = this

          override fun instant() = baseInstant.plusMillis(testScheduler.currentTime)
        }
    val actual = mutableListOf<Long>()
    minuteTicks(clock).take(3).collect { actual += testScheduler.currentTime }
    assertEquals(expected, actual)
  }
}
