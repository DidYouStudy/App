// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Source of the device's internet connectivity, as a reactive stream.
 *
 * ViewModels depend on this interface rather than on Android's `ConnectivityManager`, so they stay
 * unit-testable without a device: production code uses [AndroidConnectivityObserver], tests use a
 * fake that emits whatever connectivity they set.
 *
 * It is a `fun interface`, so a test that only needs a fixed stream can also pass a lambda, for
 * example `ConnectivityObserver { flowOf(false) }`.
 */
fun interface ConnectivityObserver {

  /**
   * Observes whether the device can reach the internet.
   *
   * Emits the current state first, then every change: `true` while online and `false` while
   * offline. Equal consecutive values are emitted once.
   *
   * Collecting registers a platform callback and cancelling the collector removes it, so collect it
   * from a lifecycle-aware scope (for example while the screen is visible) to avoid keeping the
   * callback registered when nobody is watching.
   */
  fun observeIsOnline(): Flow<Boolean>
}
