// Co-authored-by: Claude Fable 5.2 <noreply@anthropic.com>
package com.android.sample.model.location

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Unit tests for [AndroidLocationProvider], with a mocked Fused Location client: the tests play the
 * role of Play Services by calling the [LocationCallback] the provider registered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {

  private val bcBuilding = Location(latitude = 46.5186, longitude = 6.5619)
  private val rolex = Location(latitude = 46.5183, longitude = 6.5681)

  private val client: FusedLocationProviderClient = mock {
    on { requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>()) }
        .doReturn(Tasks.forResult(null))
    on { removeLocationUpdates(any<LocationCallback>()) }.doReturn(Tasks.forResult(null))
  }

  /** What one collector of the provider received, and the job to cancel it. */
  private class Collection(val emissions: List<Location?>, val job: Job)

  /** Collects the provider in the background, eagerly, so emissions are visible straight away. */
  private fun TestScope.collect(provider: AndroidLocationProvider): Collection {
    val emissions = mutableListOf<Location?>()
    val job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          provider.observeCurrentLocation().toList(emissions)
        }
    return Collection(emissions, job)
  }

  /** Returns the callback the provider registered with the client. */
  private fun registeredCallback(): LocationCallback {
    val captor = argumentCaptor<LocationCallback>()
    verify(client).requestLocationUpdates(any<LocationRequest>(), captor.capture(), any<Looper>())
    return captor.firstValue
  }

  /** Builds the platform result Play Services would deliver for [location]. */
  private fun resultOf(location: Location): LocationResult =
      LocationResult.create(listOf(platformLocation(location.latitude, location.longitude)))

  private fun platformLocation(latitude: Double, longitude: Double): android.location.Location =
      android.location.Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
      }

  private fun availability(available: Boolean): LocationAvailability =
      mock<LocationAvailability> { on { isLocationAvailable } doReturn available }

  private fun providerWithPermission(granted: Boolean = true) =
      AndroidLocationProvider(client) { granted }

  @Test
  fun emitsNullUntilTheFirstFix() = runTest {
    val collection = collect(providerWithPermission())

    assertEquals(listOf<Location?>(null), collection.emissions)
  }

  @Test
  fun emitsEachNewPositionInOrder() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()

    callback.onLocationResult(resultOf(bcBuilding))
    callback.onLocationResult(resultOf(rolex))

    assertEquals(listOf(null, bcBuilding, rolex), collection.emissions)
  }

  @Test
  fun emitsNullWhenLocationBecomesUnavailable() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()

    callback.onLocationResult(resultOf(bcBuilding))
    callback.onLocationAvailability(availability(available = false))

    assertEquals(listOf(null, bcBuilding, null), collection.emissions)
  }

  @Test
  fun keepsThePositionWhenLocationIsReportedAvailable() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()

    callback.onLocationResult(resultOf(bcBuilding))
    callback.onLocationAvailability(availability(available = true))

    assertEquals(listOf(null, bcBuilding), collection.emissions)
  }

  @Test
  fun doesNotRepeatAnUnchangedPosition() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()

    callback.onLocationResult(resultOf(bcBuilding))
    callback.onLocationResult(resultOf(bcBuilding))

    assertEquals(listOf(null, bcBuilding), collection.emissions)
  }

  @Test
  fun ignoresAResultWithoutLocation() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()

    callback.onLocationResult(resultOf(bcBuilding))
    callback.onLocationResult(LocationResult.create(emptyList()))

    assertEquals(listOf(null, bcBuilding), collection.emissions)
  }

  @Test
  fun requestsBalancedPowerUpdatesEveryTenSeconds() = runTest {
    collect(providerWithPermission())

    val captor = argumentCaptor<LocationRequest>()
    verify(client).requestLocationUpdates(captor.capture(), any<LocationCallback>(), any<Looper>())
    assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, captor.firstValue.priority)
    assertEquals(10_000L, captor.firstValue.intervalMillis)
  }

  @Test
  fun removesItsCallbackWhenTheCollectorIsCancelled() = runTest {
    val collection = collect(providerWithPermission())
    val callback = registeredCallback()
    verify(client, never()).removeLocationUpdates(any<LocationCallback>())

    collection.job.cancel()

    verify(client).removeLocationUpdates(callback)
  }

  @Test
  fun registersOneCallbackPerCollector() = runTest {
    val provider = providerWithPermission()
    collect(provider)
    collect(provider)

    val captor = argumentCaptor<LocationCallback>()
    verify(client, org.mockito.kotlin.times(2))
        .requestLocationUpdates(any<LocationRequest>(), captor.capture(), any<Looper>())
    assertTrue(captor.firstValue !== captor.secondValue)
  }

  @Test
  fun withoutPermissionEmitsNullAndCompletesWithoutAskingForUpdates() = runTest {
    val collection = collect(providerWithPermission(granted = false))

    assertEquals(listOf<Location?>(null), collection.emissions)
    assertTrue(collection.job.isCompleted)
    verify(client, never())
        .requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
  }

  @Test
  fun checksThePermissionAgainOnEachCollection() = runTest {
    var granted = false
    val provider = AndroidLocationProvider(client) { granted }
    collect(provider)

    granted = true
    val collection = collect(provider)
    registeredCallback().onLocationResult(resultOf(bcBuilding))

    assertEquals(listOf(null, bcBuilding), collection.emissions)
  }

  @Test
  fun completesWithNullWhenThePermissionIsRevokedBeforeTheRequest() = runTest {
    whenever(
            client.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any<Looper>(),
            )
        )
        .doThrow(SecurityException("revoked"))

    val collection = collect(providerWithPermission())

    assertEquals(listOf<Location?>(null), collection.emissions)
    assertTrue(collection.job.isCompleted)
  }

  @Test
  fun completesWithNullWhenPlayServicesRejectsTheRequest() = runTest {
    whenever(
            client.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any<Looper>(),
            )
        )
        .doReturn(Tasks.forException(IllegalStateException("no Play Services")))

    val collection = collect(providerWithPermission())
    // Task listeners are posted to the main looper.
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(listOf<Location?>(null), collection.emissions)
    assertTrue(collection.job.isCompleted)
  }

  @Test
  fun toDomainLocationCopiesTheCoordinates() {
    assertEquals(
        bcBuilding,
        platformLocation(bcBuilding.latitude, bcBuilding.longitude).toDomainLocation(),
    )
  }

  @Test
  fun toDomainLocationRejectsInvalidCoordinates() {
    assertNull(platformLocation(Double.NaN, 6.5619).toDomainLocation())
  }

  @Test
  fun hasLocationPermissionIsFalseWithoutAnyLocationPermission() {
    val app = ApplicationProvider.getApplicationContext<Application>()

    assertFalse(hasLocationPermission(app))
  }

  @Test
  fun hasLocationPermissionAcceptsTheApproximatePermissionAlone() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    assertTrue(hasLocationPermission(app))
  }

  @Test
  fun hasLocationPermissionAcceptsThePrecisePermission() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    assertTrue(hasLocationPermission(app))
  }

  @Test
  fun createBuildsAProviderThatReportsNullWithoutPermission() = runTest {
    val app = ApplicationProvider.getApplicationContext<Application>()

    val collection = collect(AndroidLocationProvider.create(app))

    assertEquals(listOf<Location?>(null), collection.emissions)
    assertTrue(collection.job.isCompleted)
  }
}
