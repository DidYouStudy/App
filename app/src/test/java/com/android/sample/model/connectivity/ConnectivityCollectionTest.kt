// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [collectWhileObserved]: connectivity is collected only while UI is observing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityCollectionTest {

  @Test
  fun doesNotCollectWhileNobodyObservesTheState() = runTest {
    val observer = FakeConnectivityObserver()
    val subscriptions = MutableStateFlow(0)
    val values = mutableListOf<Boolean>()

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      observer.collectWhileObserved(subscriptions) { values += it }
    }

    assertEquals(0, observer.activeCollectors)
    assertEquals(emptyList<Boolean>(), values)
  }

  @Test
  fun reportsTheCurrentValueAndChangesWhileObserved() = runTest {
    val observer = FakeConnectivityObserver()
    val subscriptions = MutableStateFlow(0)
    val values = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      observer.collectWhileObserved(subscriptions) { values += it }
    }

    subscriptions.value = 1
    observer.isOnline.value = false
    observer.isOnline.value = true

    assertEquals(listOf(true, false, true), values)
    assertEquals(1, observer.activeCollectors)
  }

  @Test
  fun stopsCollectingWhenTheLastObserverLeavesAndResumesWithTheFreshState() = runTest {
    val observer = FakeConnectivityObserver()
    val subscriptions = MutableStateFlow(1)
    val values = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      observer.collectWhileObserved(subscriptions) { values += it }
    }

    subscriptions.value = 0
    assertEquals(0, observer.activeCollectors)
    observer.isOnline.value = false
    assertEquals(listOf(true), values)

    subscriptions.value = 1
    assertEquals(listOf(true, false), values)
  }
}
