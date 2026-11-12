// Co-authored-by: Claude Fable 5.2 <noreply@anthropic.com>
package com.android.sample.model.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [LocationProvider] backed by Google Play Services' Fused Location Provider.
 *
 * Each collector of [observeCurrentLocation] registers its own location callback (the flow is
 * cold), asking for a balanced-power position every [UPDATE_INTERVAL_MILLIS]. The callback is
 * removed as soon as the collector is cancelled.
 *
 * This class only reads the permission state, it never asks the user for it: requesting the runtime
 * permission is the UI's job. Without permission the flow emits `null` and completes, so collect
 * again once the permission has been granted.
 *
 * @param client the Fused Location client that delivers the updates.
 * @param hasLocationPermission returns whether the app may read the location right now; checked
 *   every time a collection starts.
 */
class AndroidLocationProvider(
    private val client: FusedLocationProviderClient,
    private val hasLocationPermission: () -> Boolean,
) : LocationProvider {

  /**
   * Emits `null` first (no fix yet), then every new position, and `null` again whenever Play
   * Services reports that location is unavailable (for example location turned off).
   *
   * The flow completes after a `null` when updates cannot start: permission missing, or Play
   * Services rejecting the request. Equal consecutive values are emitted once, and a slow collector
   * only receives the latest value.
   */
  override fun observeCurrentLocation(): Flow<Location?> =
      locationUpdates().conflate().distinctUntilChanged()

  /**
   * The raw stream behind [observeCurrentLocation]: registers a location callback while collected
   * and removes it when the collector is cancelled or the flow closes.
   */
  // The permission is checked through hasLocationPermission right before the request, which lint
  // cannot see; a permission revoked in between is caught as a SecurityException.
  @SuppressLint("MissingPermission")
  private fun locationUpdates(): Flow<Location?> = callbackFlow {
    send(null)
    if (!hasLocationPermission()) {
      close()
      return@callbackFlow
    }

    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.toDomainLocation()?.let { trySend(it) }
          }

          override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) trySend(null)
          }
        }

    try {
      client
          .requestLocationUpdates(LOCATION_REQUEST, callback, Looper.getMainLooper())
          .addOnFailureListener { close() }
    } catch (_: SecurityException) {
      close()
    }

    // Runs on cancellation and on close(); removing a callback that was never registered
    // is a no-op for Play Services.
    awaitClose { client.removeLocationUpdates(callback) }
  }

  companion object {
    /** Time between two location updates: the low end of the 10 to 15 s the feature asks for. */
    const val UPDATE_INTERVAL_MILLIS = 10_000L

    /** Balanced power: roughly block-level accuracy, mostly from Wi-Fi and cell towers. */
    private val LOCATION_REQUEST: LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .build()

    /**
     * Creates the production provider. Only the application context is kept, so no activity is
     * leaked.
     */
    fun create(context: Context): AndroidLocationProvider {
      val appContext = context.applicationContext
      return AndroidLocationProvider(
          client = LocationServices.getFusedLocationProviderClient(appContext),
          hasLocationPermission = { hasLocationPermission(appContext) },
      )
    }
  }
}

/**
 * Returns whether the app holds a location permission. Either the precise or the approximate one is
 * enough: Android lets the user grant only the approximate one.
 */
internal fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Converts a platform location to the domain [Location], or `null` when its coordinates are not
 * valid (the domain type rejects out-of-range and NaN values).
 */
internal fun android.location.Location.toDomainLocation(): Location? =
    try {
      Location(latitude = latitude, longitude = longitude)
    } catch (_: IllegalArgumentException) {
      null
    }
