// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [SharedConnectivityObserver]: one upstream collection for all collectors. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedConnectivityObserverTest {

  /** Collects [shared] into a list until the returned job is cancelled. */
  private fun TestScope.collect(
      shared: ConnectivityObserver,
      into: MutableList<Boolean>,
  ): Job =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        shared.observeIsOnline().collect { into += it }
      }

  private fun TestScope.sharedOver(upstream: ConnectivityObserver): SharedConnectivityObserver =
      SharedConnectivityObserver(
          upstream,
          backgroundScope + UnconfinedTestDispatcher(testScheduler),
      )

  @Test
  fun severalCollectorsShareASingleUpstreamCollection() = runTest {
    val upstream = FakeConnectivityObserver()
    val shared = sharedOver(upstream)

    collect(shared, mutableListOf())
    collect(shared, mutableListOf())
    collect(shared, mutableListOf())

    assertEquals(1, upstream.activeCollectors)
  }

  @Test
  fun everyCollectorReceivesEachChange() = runTest {
    val upstream = FakeConnectivityObserver()
    val shared = sharedOver(upstream)
    val first = mutableListOf<Boolean>()
    val second = mutableListOf<Boolean>()
    collect(shared, first)
    collect(shared, second)

    upstream.isOnline.value = false
    upstream.isOnline.value = true

    assertEquals(listOf(true, false, true), first)
    assertEquals(listOf(true, false, true), second)
  }

  @Test
  fun aCollectorThatStartsLateGetsTheCurrentStateFirst() = runTest {
    val upstream = FakeConnectivityObserver()
    val shared = sharedOver(upstream)
    collect(shared, mutableListOf())
    upstream.isOnline.value = false

    val late = mutableListOf<Boolean>()
    collect(shared, late)

    assertEquals(listOf(false), late)
  }

  @Test
  fun upstreamStopsWhenTheLastCollectorLeaves() = runTest {
    val upstream = FakeConnectivityObserver()
    val shared = sharedOver(upstream)
    val first = collect(shared, mutableListOf())
    val second = collect(shared, mutableListOf())

    first.cancel()
    assertEquals(1, upstream.activeCollectors)
    second.cancel()

    assertEquals(0, upstream.activeCollectors)
  }

  @Test
  fun aCollectorAfterAPauseNeverSeesTheOutdatedValue() = runTest {
    val upstream = FakeConnectivityObserver()
    val shared = sharedOver(upstream)
    collect(shared, mutableListOf()).cancel()
    upstream.isOnline.value = false

    val after = mutableListOf<Boolean>()
    collect(shared, after)

    assertEquals(listOf(false), after)
  }
}
