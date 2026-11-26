// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Collects this observer only while a UI state has at least one collector, and reports each value.
 *
 * It suspends for as long as the caller's scope lives, so launch it from `viewModelScope`. The
 * observer emits the current connectivity as soon as collection starts, so coming back to a screen
 * never reports an outdated value; collection is cancelled when the last UI collector leaves.
 *
 * @param subscriptionCount the `subscriptionCount` of the ViewModel's UI state flow.
 * @param onOnlineChanged called with `true` while online and `false` while offline.
 */
suspend fun ConnectivityObserver.collectWhileObserved(
    subscriptionCount: StateFlow<Int>,
    onOnlineChanged: (Boolean) -> Unit,
) {
  subscriptionCount
      .map { it > 0 }
      .distinctUntilChanged()
      .collectLatest { isObserved ->
        // The previous block is cancelled when the flag flips, so nothing is collected while the
        // state has no collector.
        if (isObserved) observeIsOnline().collect(onOnlineChanged)
      }
}
