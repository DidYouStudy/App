// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.preferences

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [PreferencesRepository].
 *
 * Stores and retrieves preferences under the `preferences` collection in Cloud Firestore.
 *
 * @param firestore The [FirebaseFirestore] instance used for database operations.
 */
class PreferencesRepositoryFirestore(private val firestore: FirebaseFirestore) :
    PreferencesRepository {

  private val collection = firestore.collection("preferences")

  /**
   * Fetches the preferences for a given user from Firestore.
   *
   * Tries to fetch from Firestore first and falls back to local cache ([Source.CACHE]) if the
   * network request fails while offline.
   *
   * @param userId The ID of the user whose preferences are requested. Must not be blank.
   * @return A [Result] containing [UserPreferences] if found, `null` if the document does not
   *   exist, or a [Result.failure] if an error occurs.
   * @throws CancellationException if the coroutine is cancelled during execution.
   */
  override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
      try {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val doc = collection.document(userId)
        val documentSnapshot =
            try {
              doc.get().await()
            } catch (e: Exception) {
              if (e is CancellationException) throw e
              try {
                doc.get(Source.CACHE).await()
              } catch (_: Exception) {
                throw e
              }
            }
        if (documentSnapshot.exists()) {
          Result.success(documentSnapshot.toObject(UserPreferences::class.java))
        } else {
          Result.success(null)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * Saves the provided user preferences, local-first.
   *
   * Success means the write is stored in the local Firestore cache and queued for sync; it does not
   * wait for the server, so it also completes while offline. A server-side rejection of the queued
   * write is not reported here (the same contract as the events repository).
   *
   * @param preferences The [UserPreferences] instance to persist. [UserPreferences.userId] must not
   *   be blank.
   * @return A [Result] containing [Unit] on success or [Result.failure] on error.
   * @throws CancellationException if the coroutine is canceled during execution.
   */
  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
      try {
        require(preferences.userId.isNotBlank()) { "User ID must not be blank" }
        commitLocally(preferences)
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /**
   * Queues the write and returns once it has been applied to the local cache.
   *
   * The `set()` Task only completes on server acknowledgement, so awaiting it hangs offline.
   * Instead, a cache-only query queued right after `set()` acts as a local-write barrier on
   * Firestore's serial worker queue: it completes after the write is applied locally, without
   * waiting for the server. This mirrors `EventsRepositoryFirestore.commitLocally`. A query (rather
   * than a document read) is used so a missing document yields an empty snapshot instead of
   * throwing. Cancelling the caller does not undo an already queued write.
   *
   * @param preferences The preferences to write; [UserPreferences.userId] is the document ID.
   * @throws Exception the acknowledgement's exception if the write has already failed, or the
   *   barrier's exception if the cache read fails.
   */
  private suspend fun commitLocally(preferences: UserPreferences) {
    val id = preferences.userId
    // Not awaited: the Task completes only once the server acknowledges the write.
    val acknowledgement = collection.document(id).set(preferences)
    collection.whereEqualTo(FieldPath.documentId(), id).get(Source.CACHE).await()
    // The write may already have been rejected by now; surface that instead of reporting success.
    if (acknowledgement.isComplete && !acknowledgement.isSuccessful) {
      throw acknowledgement.exception ?: IllegalStateException("Failed to save preferences")
    }
  }
}
