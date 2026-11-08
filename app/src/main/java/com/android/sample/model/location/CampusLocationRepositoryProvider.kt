// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.android.sample.R

/** Supplies the application's shared campus location repository; tests can replace it. */
object CampusLocationRepositoryProvider {
  private var instance: CampusLocationRepository? = null

  /**
   * Returns the shared repository reading the bundled `campus_locations.json`, or the one set for
   * tests. Only the application context is kept, so no activity is leaked.
   */
  @Synchronized
  fun getRepository(context: Context): CampusLocationRepository {
    instance?.let {
      return it
    }
    val appContext = context.applicationContext
    val repository =
        LocalCampusLocationRepository(
            readJson = {
              appContext.resources.openRawResource(R.raw.campus_locations).bufferedReader().use {
                it.readText()
              }
            }
        )
    instance = repository
    return repository
  }

  /**
   * Replaces the shared repository so tests can supply a fake. `null` removes the replacement: the
   * next [getRepository] call creates a fresh local repository.
   */
  @VisibleForTesting
  @Synchronized
  fun setRepository(repository: CampusLocationRepository?) {
    instance = repository
  }
}
