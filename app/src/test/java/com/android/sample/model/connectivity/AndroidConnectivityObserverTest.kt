// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [AndroidConnectivityObserver], with a mocked [ConnectivityManager]: the tests play
 * the role of the system by calling the [ConnectivityManager.NetworkCallback] the observer
 * registered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidConnectivityObserverTest {

  private val network: Network = mock()
  private val connectivityManager: ConnectivityManager = mock()

  /** What one collector of the observer received, and the job to cancel it. */
  private class Collection(val emissions: List<Boolean>, val job: Job)

  /** Collects the observer in the background, eagerly, so emissions are visible straight away. */
  private fun TestScope.collect(observer: AndroidConnectivityObserver): Collection {
    val emissions = mutableListOf<Boolean>()
    val job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          observer.observeIsOnline().toList(emissions)
        }
    return Collection(emissions, job)
  }

  /** Builds capabilities as the system would report them for a network. */
  private fun capabilities(internet: Boolean, validated: Boolean): NetworkCapabilities = mock {
    on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn internet
    on { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } doReturn validated
  }

  private val onlineCapabilities = capabilities(internet = true, validated = true)

  /**
   * Makes [capabilities] describe the device's active network when the observer starts; `null`
   * means the system knows nothing about that network.
   */
  private fun givenActiveNetwork(capabilities: NetworkCapabilities?) {
    whenever(connectivityManager.activeNetwork).doReturn(network)
    whenever(connectivityManager.getNetworkCapabilities(anyOrNull())).doReturn(capabilities)
  }

  /**
   * Makes the system refuse the callback registration, as it does without the permission. The
   * registration returns nothing, so the stub uses Mockito's "do" style.
   */
  private fun givenRegistrationIsRefused() {
    doThrow(SecurityException("missing ACCESS_NETWORK_STATE"))
        .whenever(connectivityManager)
        .registerDefaultNetworkCallback(anyCallback())
  }

  private fun anyCallback() = any<ConnectivityManager.NetworkCallback>()

  private fun observer() = AndroidConnectivityObserver(connectivityManager)

  /** Returns the callback the observer registered with the system. */
  private fun registeredCallback(): ConnectivityManager.NetworkCallback {
    val captor = argumentCaptor<ConnectivityManager.NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(captor.capture())
    return captor.firstValue
  }

  // ---------------------------------------------------------------------------------------------
  // The state reported when collection starts
  // ---------------------------------------------------------------------------------------------

  @Test
  fun emitsOnlineFirstWhenTheActiveNetworkHasValidatedInternet() = runTest {
    givenActiveNetwork(onlineCapabilities)

    val collection = collect(observer())

    assertEquals(listOf(true), collection.emissions)
  }

  /** Without any default network the system never calls back, so the observer must say so. */
  @Test
  fun emitsOfflineFirstWhenThereIsNoActiveNetwork() = runTest {
    // The mock answers null for the active network, like a device with no connection at all.
    val collection = collect(observer())

    assertEquals(listOf(false), collection.emissions)
  }

  @Test
  fun emitsOfflineFirstWhenTheActiveNetworkHasNoKnownCapabilities() = runTest {
    givenActiveNetwork(null)

    val collection = collect(observer())

    assertEquals(listOf(false), collection.emissions)
  }

  /** A Wi-Fi behind a captive portal is connected but cannot fetch anything. */
  @Test
  fun emitsOfflineFirstWhenTheNetworkIsNotValidated() = runTest {
    givenActiveNetwork(capabilities(internet = true, validated = false))

    val collection = collect(observer())

    assertEquals(listOf(false), collection.emissions)
  }

  @Test
  fun emitsOfflineFirstWhenTheNetworkHasNoInternetCapability() = runTest {
    givenActiveNetwork(capabilities(internet = false, validated = true))

    val collection = collect(observer())

    assertEquals(listOf(false), collection.emissions)
  }

  @Test
  fun registersADefaultNetworkCallbackWhileCollecting() = runTest {
    givenActiveNetwork(onlineCapabilities)

    collect(observer())

    verify(connectivityManager).registerDefaultNetworkCallback(anyCallback())
  }

  // ---------------------------------------------------------------------------------------------
  // Changes reported by the system
  // ---------------------------------------------------------------------------------------------

  @Test
  fun emitsOfflineWhenTheNetworkIsLost() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val collection = collect(observer())

    registeredCallback().onLost(network)

    assertEquals(listOf(true, false), collection.emissions)
  }

  /** The "connection returned" case: started offline, then a validated network appears. */
  @Test
  fun emitsOnlineWhenAValidatedNetworkAppearsAfterStartingOffline() = runTest {
    val collection = collect(observer())

    registeredCallback().onCapabilitiesChanged(network, onlineCapabilities)

    assertEquals(listOf(false, true), collection.emissions)
  }

  @Test
  fun emitsOfflineWhenTheNetworkLosesItsValidation() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val collection = collect(observer())
    val unvalidated = capabilities(internet = true, validated = false)

    registeredCallback().onCapabilitiesChanged(network, unvalidated)

    assertEquals(listOf(true, false), collection.emissions)
  }

  @Test
  fun emitsOnlineAgainWhenValidationIsRegained() = runTest {
    givenActiveNetwork(capabilities(internet = true, validated = false))
    val collection = collect(observer())

    registeredCallback().onCapabilitiesChanged(network, onlineCapabilities)

    assertEquals(listOf(false, true), collection.emissions)
  }

  @Test
  fun followsALossAndARegainInOrder() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val collection = collect(observer())
    val callback = registeredCallback()

    callback.onLost(network)
    callback.onCapabilitiesChanged(network, onlineCapabilities)
    callback.onLost(network)

    assertEquals(listOf(true, false, true, false), collection.emissions)
  }

  @Test
  fun doesNotRepeatAnUnchangedOnlineState() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val collection = collect(observer())
    val callback = registeredCallback()

    // The system reports every capability change, most of them irrelevant to the online state.
    callback.onCapabilitiesChanged(network, onlineCapabilities)
    callback.onCapabilitiesChanged(network, onlineCapabilities)

    assertEquals(listOf(true), collection.emissions)
  }

  @Test
  fun doesNotRepeatAnUnchangedOfflineState() = runTest {
    val collection = collect(observer())
    val callback = registeredCallback()

    callback.onLost(network)
    callback.onLost(network)

    assertEquals(listOf(false), collection.emissions)
  }

  // ---------------------------------------------------------------------------------------------
  // Registration lifecycle
  // ---------------------------------------------------------------------------------------------

  @Test
  fun keepsItsCallbackRegisteredWhileTheCollectorIsActive() = runTest {
    givenActiveNetwork(onlineCapabilities)

    collect(observer())

    verify(connectivityManager, never()).unregisterNetworkCallback(anyCallback())
  }

  @Test
  fun removesItsCallbackWhenTheCollectorIsCancelled() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val collection = collect(observer())
    val callback = registeredCallback()

    collection.job.cancel()

    verify(connectivityManager).unregisterNetworkCallback(callback)
  }

  @Test
  fun registersOneCallbackPerCollector() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val observer = observer()
    collect(observer)
    collect(observer)

    val captor = argumentCaptor<ConnectivityManager.NetworkCallback>()
    verify(connectivityManager, times(2)).registerDefaultNetworkCallback(captor.capture())
    assertNotSame(captor.firstValue, captor.secondValue)
  }

  @Test
  fun cancellingOneCollectorKeepsTheOtherOneRegistered() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val observer = observer()
    val first = collect(observer)
    val second = collect(observer)
    val captor = argumentCaptor<ConnectivityManager.NetworkCallback>()
    verify(connectivityManager, times(2)).registerDefaultNetworkCallback(captor.capture())

    first.job.cancel()

    verify(connectivityManager).unregisterNetworkCallback(captor.firstValue)
    verify(connectivityManager, never()).unregisterNetworkCallback(captor.secondValue)
    captor.secondValue.onLost(network)
    assertEquals(listOf(true, false), second.emissions)
  }

  @Test
  fun aCollectorThatStartsLaterReceivesTheCurrentStateAgain() = runTest {
    givenActiveNetwork(onlineCapabilities)
    val observer = observer()
    collect(observer).job.cancel()

    // The connection dropped while nobody was collecting.
    givenActiveNetwork(null)
    val second = collect(observer)

    assertEquals(listOf(false), second.emissions)
  }

  // ---------------------------------------------------------------------------------------------
  // Missing permission
  // ---------------------------------------------------------------------------------------------

  /** A wrong "offline" notice is worse than none, so an unavailable state reads as online. */
  @Test
  fun reportsOnlineAndCompletesWhenRegistrationIsRefusedForLackOfPermission() = runTest {
    givenActiveNetwork(onlineCapabilities)
    givenRegistrationIsRefused()

    val collection = collect(observer())

    assertEquals(listOf(true), collection.emissions)
    assertTrue(collection.job.isCompleted)
  }

  /** Even a device that is really offline ends up reported as online without the permission. */
  @Test
  fun endsOnlineWhenRegistrationIsRefusedEvenIfNoNetworkIsActive() = runTest {
    givenRegistrationIsRefused()

    val collection = collect(observer())

    assertEquals(true, collection.emissions.last())
    assertTrue(collection.job.isCompleted)
  }

  @Test
  fun reportsOnlineAndCompletesWhenReadingTheActiveNetworkIsRefused() = runTest {
    whenever(connectivityManager.activeNetwork).doThrow(SecurityException("missing permission"))

    val collection = collect(observer())

    assertEquals(listOf(true), collection.emissions)
    assertTrue(collection.job.isCompleted)
    verify(connectivityManager, never()).registerDefaultNetworkCallback(anyCallback())
  }

  @Test
  fun doesNotUnregisterACallbackThatWasNeverRegistered() = runTest {
    givenActiveNetwork(onlineCapabilities)
    givenRegistrationIsRefused()

    collect(observer())

    verify(connectivityManager, never()).unregisterNetworkCallback(anyCallback())
  }

  // ---------------------------------------------------------------------------------------------
  // hasValidatedInternet
  // ---------------------------------------------------------------------------------------------

  @Test
  fun hasValidatedInternetIsFalseWithoutCapabilities() {
    val missing: NetworkCapabilities? = null

    assertFalse(missing.hasValidatedInternet())
  }

  @Test
  fun hasValidatedInternetIsTrueForValidatedInternet() {
    assertTrue(capabilities(internet = true, validated = true).hasValidatedInternet())
  }

  @Test
  fun hasValidatedInternetIsFalseWhenOnlyInternetIsPresent() {
    assertFalse(capabilities(internet = true, validated = false).hasValidatedInternet())
  }

  @Test
  fun hasValidatedInternetIsFalseWhenOnlyValidationIsPresent() {
    assertFalse(capabilities(internet = false, validated = true).hasValidatedInternet())
  }

  @Test
  fun hasValidatedInternetIsFalseWithNeitherCapability() {
    assertFalse(capabilities(internet = false, validated = false).hasValidatedInternet())
  }

  // ---------------------------------------------------------------------------------------------
  // Production factory
  // ---------------------------------------------------------------------------------------------

  /** Robolectric supplies a real [ConnectivityManager], so this runs the production path. */
  @Test
  fun createBuildsAnObserverThatReportsAStateAndStopsWhenCancelled() = runTest {
    val app = ApplicationProvider.getApplicationContext<Application>()

    val collection = collect(AndroidConnectivityObserver.create(app))

    assertEquals(1, collection.emissions.size)
    collection.job.cancel()
    assertTrue(collection.job.isCancelled)
  }
}
