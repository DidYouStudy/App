// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityObserver] backed by Android's [ConnectivityManager].
 *
 * The device counts as online only when its default network has internet access that the system has
 * validated. A network that is connected but cannot reach the internet (for example a Wi-Fi behind
 * a captive portal) is reported as offline, since nothing could be fetched through it.
 *
 * Each collector of [observeIsOnline] registers its own default-network callback (the flow is
 * cold), and the callback is removed as soon as the collector is cancelled. This needs the
 * `ACCESS_NETWORK_STATE` permission, which is declared in the manifest and granted at install.
 *
 * @param connectivityManager the system service that delivers the network updates.
 */
class AndroidConnectivityObserver(
    private val connectivityManager: ConnectivityManager,
) : ConnectivityObserver {

  /** Emits the current connectivity first, then each change, skipping repeated equal values. */
  override fun observeIsOnline(): Flow<Boolean> = connectivityUpdates().distinctUntilChanged()

  /**
   * The raw stream behind [observeIsOnline]: registers a default-network callback while collected
   * and removes it when the collector is cancelled or the flow closes.
   *
   * If the system refuses the registration because the permission is missing, the flow reports
   * online and completes: a wrong "offline" notice would be worse than no notice, because the app
   * could not tell the user's connection is fine.
   */
  private fun connectivityUpdates(): Flow<Boolean> = callbackFlow {
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          // Also runs right after a network becomes available, so it covers "connection returned".
          override fun onCapabilitiesChanged(
              network: Network,
              networkCapabilities: NetworkCapabilities,
          ) {
            trySend(networkCapabilities.hasValidatedInternet())
          }

          override fun onLost(network: Network) {
            trySend(false)
          }
        }

    try {
      // The callback is only invoked while a default network exists, so the state when there is
      // none (offline from the start) has to be reported explicitly.
      val activeCapabilities =
          connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
      trySend(activeCapabilities.hasValidatedInternet())
      connectivityManager.registerDefaultNetworkCallback(callback)
    } catch (_: SecurityException) {
      trySend(true)
      close()
      return@callbackFlow
    }

    // Runs on cancellation and on close().
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }

  companion object {
    /**
     * Creates the production observer. Only the application context is used, so no activity is
     * leaked.
     */
    fun create(context: Context): AndroidConnectivityObserver =
        AndroidConnectivityObserver(
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        )
  }
}

/**
 * Returns whether these capabilities describe a network with internet access that the system has
 * validated. `null` (no network at all) is not online.
 */
internal fun NetworkCapabilities?.hasValidatedInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
