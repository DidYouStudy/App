// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [FakeLocationProvider]: the emissions other tests rely on when they inject
 * coordinates, or `null` for disabled GPS or missing permission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeLocationProviderTest {

  private val bcBuilding = Location(latitude = 46.5186, longitude = 6.5619)
  private val rolexCenter = Location(latitude = 46.5180, longitude = 6.5681)

  /**
   * Collects every emission of [provider] into the returned list until the test ends. The
   * unconfined dispatcher records each value as soon as it is set.
   */
  private fun TestScope.collectEmissions(provider: LocationProvider): List<Location?> {
    val emissions = mutableListOf<Location?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      provider.observeCurrentLocation().collect { emissions += it }
    }
    return emissions
  }

  @Test
  fun observeCurrentLocation_noInitialLocation_emitsNull() = runTest {
    val provider = FakeLocationProvider()

    assertNull(provider.observeCurrentLocation().first())
  }

  @Test
  fun observeCurrentLocation_initialLocation_emitsItFirst() = runTest {
    val provider = FakeLocationProvider(initialLocation = bcBuilding)

    assertEquals(bcBuilding, provider.observeCurrentLocation().first())
  }

  @Test
  fun observeCurrentLocation_newCoordinates_emitsEachInOrder() = runTest {
    val provider = FakeLocationProvider()
    val emissions = collectEmissions(provider)

    provider.location.value = bcBuilding
    provider.location.value = rolexCenter

    assertEquals(listOf(null, bcBuilding, rolexCenter), emissions)
  }

  @Test
  fun observeCurrentLocation_setToNullAfterFix_emitsNull() = runTest {
    val provider = FakeLocationProvider(initialLocation = bcBuilding)
    val emissions = collectEmissions(provider)

    provider.location.value = null

    assertEquals(listOf(bcBuilding, null), emissions)
  }

  @Test
  fun observeCurrentLocation_sameCoordinatesTwice_emitsOnce() = runTest {
    val provider = FakeLocationProvider()
    val emissions = collectEmissions(provider)

    provider.location.value = Location(latitude = 46.5186, longitude = 6.5619)
    provider.location.value = Location(latitude = 46.5186, longitude = 6.5619)

    assertEquals(listOf(null, bcBuilding), emissions)
  }

  @Test
  fun observeCurrentLocation_lateCollector_receivesOnlyLatestValue() = runTest {
    val provider = FakeLocationProvider()
    provider.location.value = bcBuilding
    provider.location.value = rolexCenter

    val emissions = collectEmissions(provider)

    assertEquals(listOf(rolexCenter), emissions)
  }
}
