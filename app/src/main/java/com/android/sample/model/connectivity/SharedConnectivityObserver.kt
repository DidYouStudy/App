// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/**
 * [ConnectivityObserver] that lets any number of collectors share one [upstream] collection.
 *
 * [AndroidConnectivityObserver] registers one platform callback per collector. This wrapper keeps a
 * single upstream collection alive while at least one collector is active, so the whole app holds
 * one network callback however many screens observe connectivity at the same time.
 *
 * A collector that starts while another is active receives the latest value straight away. When the
 * last collector stops, the upstream is released and the remembered value is dropped, so the next
 * collector never sees an outdated state before the fresh one.
 *
 * @param upstream the observer whose collection is shared.
 * @param scope scope that runs the shared upstream collection; it should live as long as the app.
 */
class SharedConnectivityObserver(upstream: ConnectivityObserver, scope: CoroutineScope) :
    ConnectivityObserver {

  private val shared: Flow<Boolean> =
      upstream
          .observeIsOnline()
          .shareIn(
              scope = scope,
              started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
              replay = 1,
          )

  /** Emits the current connectivity first, then each change, from the single shared upstream. */
  override fun observeIsOnline(): Flow<Boolean> = shared
}
