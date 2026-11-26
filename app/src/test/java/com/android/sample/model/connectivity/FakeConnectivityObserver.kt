// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ConnectivityObserver] for unit tests: no network, no Android framework.
 *
 * Tests drive it by writing to [isOnline]: `false` simulates losing the connection and `true`
 * simulates getting it back. Every collector of [observeIsOnline] receives the current value
 * straight away, then each change.
 *
 * It behaves like the [MutableStateFlow] behind it: setting a value equal to the current one emits
 * nothing, and a collector that starts late only receives the latest value.
 *
 * @param initialOnline what collectors receive before the test sets anything; online by default.
 */
class FakeConnectivityObserver(initialOnline: Boolean = true) : ConnectivityObserver {

  /** Whether this observer reports a connection; set it to simulate losing or regaining it. */
  val isOnline: MutableStateFlow<Boolean> = MutableStateFlow(initialOnline)

  /**
   * How many collectors are currently active. Tests use it to check that the code under test
   * collects connectivity only while it needs to, and releases the collection afterwards.
   */
  val activeCollectors: Int
    get() = isOnline.subscriptionCount.value

  /** Returns [isOnline] as a read-only flow, so the code under test cannot change it. */
  override fun observeIsOnline(): Flow<Boolean> = isOnline.asStateFlow()
}

/**
 * Runs [block] with a [FakeConnectivityObserver] installed as the app-wide shared observer, and
 * always removes it afterwards so one test cannot leak connectivity into the next.
 *
 * @param initialOnline what the fake reports until [block] changes
 *   [FakeConnectivityObserver.isOnline].
 */
fun withSharedConnectivity(
    initialOnline: Boolean,
    block: (FakeConnectivityObserver) -> Unit,
) {
  val fake = FakeConnectivityObserver(initialOnline)
  ConnectivityObserverProvider.setObserver(fake)
  try {
    block(fake)
  } finally {
    ConnectivityObserverProvider.setObserver(null)
  }
}
