// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Supplies the application's single shared [ConnectivityObserver]; tests can replace it.
 *
 * Every screen and ViewModel takes its observer from here, so the app registers one platform
 * network callback instead of one per ViewModel.
 */
object ConnectivityObserverProvider {
  private var instance: ConnectivityObserver? = null

  /**
   * Returns the shared observer, or the one set for tests. It is created on first use, from the
   * application context of [context], so no activity is leaked.
   */
  @Synchronized
  fun getObserver(context: Context): ConnectivityObserver =
      instance
          ?: SharedConnectivityObserver(
                  upstream = AndroidConnectivityObserver.create(context),
                  scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
              )
              .also { instance = it }

  /**
   * Replaces the shared observer, so tests can supply a fake without a platform callback. `null`
   * removes the replacement: the next [getObserver] call creates a fresh shared observer.
   */
  @VisibleForTesting
  @Synchronized
  fun setObserver(observer: ConnectivityObserver?) {
    instance = observer
  }
}
