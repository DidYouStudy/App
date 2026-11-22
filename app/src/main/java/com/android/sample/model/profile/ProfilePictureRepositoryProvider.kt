// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Application-level wiring for the profile picture repository.
 *
 * Like [ProfileRepositoryProvider], it hands out one process-shared instance. Unlike it, creating
 * the repository needs a [Context], only to read its Storage path and content type resources; the
 * context is not retained.
 */
object ProfilePictureRepositoryProvider {
  private var instance: ProfilePictureRepository? = null

  /**
   * Returns the process-shared Storage repository, or the one set for tests.
   *
   * @param context used to create the repository on first use; not retained.
   * @return the cached [ProfilePictureRepository] implementation.
   */
  @Synchronized
  fun getRepository(context: Context): ProfilePictureRepository {
    instance?.let {
      return it
    }
    return ProfilePictureRepositoryStorage(context).also { instance = it }
  }

  /**
   * Replaces the shared repository, so tests that start the whole app can supply a fake or an
   * emulator-backed one without initializing Cloud Storage. `null` removes the replacement: the
   * next [getRepository] call creates a fresh Storage repository.
   */
  @VisibleForTesting
  @Synchronized
  fun setRepository(repository: ProfilePictureRepository?) {
    instance = repository
  }
}
