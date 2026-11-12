// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline-first implementation of [PreferencesRepository].
 *
 * Local preferences stored on the device act as the authoritative reference. When preferences are
 * saved or retrieved, any difference with online Firestore is synchronized by updating online
 * Firestore to match the local preferences. When local preferences are absent (such as on a fresh
 * device), online preferences are retrieved and cached locally.
 */
class PreferencesRepositorySync(
    private val localStore: PreferencesLocalStore,
    private val remoteRepository: PreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PreferencesRepository {

  /** Synchronous check against local device storage. */
  override fun hasPreferences(userId: String): Boolean = localStore.has(userId)

  override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
      withContext(ioDispatcher) {
        if (userId.isBlank()) {
          return@withContext Result.failure(IllegalArgumentException("User ID must not be blank"))
        }

        val localPrefs = localStore.get(userId)
        if (localPrefs != null) {
          // Local preferences exist and serve as the reference. Sync online if differing.
          syncRemoteIfDifferent(localPrefs)
          return@withContext Result.success(localPrefs)
        }

        // Local does not exist (e.g. fresh installation). Fall back to remote and cache if present.
        val remoteResult = remoteRepository.getPreferences(userId)
        remoteResult.onSuccess { remotePrefs ->
          if (remotePrefs != null) {
            localStore.save(remotePrefs)
          }
        }
        remoteResult
      }

  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
      withContext(ioDispatcher) {
        if (preferences.userId.isBlank()) {
          return@withContext Result.failure(IllegalArgumentException("User ID must not be blank"))
        }

        // Save to local store first (offline reference)
        localStore.save(preferences)

        // Attempt remote save to keep online updated
        try {
          remoteRepository.savePreferences(preferences)
        } catch (_: Exception) {
          // Offline: local preference remains the reference and will sync when online
        }
        Result.success(Unit)
      }

  private suspend fun syncRemoteIfDifferent(localPrefs: UserPreferences) {
    try {
      val remoteResult = remoteRepository.getPreferences(localPrefs.userId)
      val remotePrefs = remoteResult.getOrNull()
      if (remotePrefs != localPrefs) {
        // Online differs or is missing: update online to match local reference
        remoteRepository.savePreferences(localPrefs)
      }
    } catch (_: Exception) {
      // Offline: ignored, will sync on next fetch or save
    }
  }
}
