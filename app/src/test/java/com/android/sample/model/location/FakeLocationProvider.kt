// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [LocationProvider] for unit tests: no GPS, no Play Services, no permissions.
 *
 * Tests drive it by writing to [location]. A [Location] simulates a new fix and `null` simulates
 * location being unavailable (GPS turned off or permission denied). Every collector of
 * [observeCurrentLocation] receives the current value straight away, then each change.
 *
 * It behaves like the [MutableStateFlow] behind it: setting a value equal to the current one emits
 * nothing, and a collector that starts late only receives the latest value.
 *
 * @param initialLocation what collectors receive before the test sets anything; `null` (no fix yet)
 *   by default.
 */
class FakeLocationProvider(initialLocation: Location? = null) : LocationProvider {

  /** The position this provider reports; set it to simulate movement or a lost fix. */
  val location: MutableStateFlow<Location?> = MutableStateFlow(initialLocation)

  /** Returns [location] as a read-only flow, so the code under test cannot change it. */
  override fun observeCurrentLocation(): Flow<Location?> = location.asStateFlow()
}
