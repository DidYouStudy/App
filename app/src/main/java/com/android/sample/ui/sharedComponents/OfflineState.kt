// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.sharedComponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.connectivity.ConnectivityObserverProvider
import kotlinx.coroutines.flow.map

/**
 * Whether the device is offline, read from the app-wide shared [ConnectivityObserver].
 *
 * Connectivity is collected only while the screen is at least started, so nothing is observed when
 * the screen is in the background. It is `false` until the first value arrives, which happens right
 * away because the observer reports the current state first. Pass it to the `isOffline` parameter
 * of a top bar to show the offline banner.
 *
 * @param observer where connectivity comes from; the shared observer by default.
 */
@Composable
fun rememberIsOffline(
    observer: ConnectivityObserver = ConnectivityObserverProvider.getObserver(LocalContext.current)
): Boolean {
  val isOffline: State<Boolean> =
      remember(observer) { observer.observeIsOnline().map { isOnline -> !isOnline } }
          .collectAsStateWithLifecycle(initialValue = false)
  return isOffline.value
}
