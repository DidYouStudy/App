// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.location

import kotlinx.coroutines.flow.Flow

/**
 * Source of the device's current position, as a reactive stream.
 *
 * ViewModels depend on this interface rather than on Google Play Services, so they stay
 * unit-testable without a device: production code uses a Fused Location implementation, tests use a
 * fake that emits whatever coordinates they set.
 */
interface LocationProvider {

  /**
   * Observes the device's position.
   *
   * Emits the latest known [Location], or `null` while no position is available: location
   * permission not granted, location services turned off, or no fix yet. A `null` can follow a
   * position, for example when the user turns location off.
   *
   * Collecting starts location updates and cancelling the collector stops them; implementations
   * must release their resources on cancellation. Collect it from a lifecycle-aware scope (for
   * example `stateIn(viewModelScope, SharingStarted.WhileSubscribed(...))`) so updates stop when
   * nobody is watching.
   */
  fun observeCurrentLocation(): Flow<Location?>
}
