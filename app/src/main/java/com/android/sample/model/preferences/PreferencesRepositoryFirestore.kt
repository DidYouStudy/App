// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [PreferencesRepository].
 *
 * Stores and retrieves preferences under the `preferences` collection in Cloud Firestore.
 *
 * @param firestore The [FirebaseFirestore] instance used for database operations.
 */
class PreferencesRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : PreferencesRepository {

  private val collection = firestore.collection("preferences")

  /**
   * Fetches the preferences for a given user from Firestore.
   *
   * @param userId The ID of the user whose preferences are requested. Must not be blank.
   * @return A [Result] containing [UserPreferences] if found, `null` if the document does not
   *   exist, or a [Result.failure] if an error occurs.
   * @throws CancellationException if the coroutine is cancelled during execution.
   */
  override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
      try {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val documentSnapshot = collection.document(userId).get().await()
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
   * Saves the provided user preferences to Firestore.
   *
   * @param preferences The [UserPreferences] instance to persist. [UserPreferences.userId] must not
   *   be blank.
   * @return A [Result] containing [Unit] on success or [Result.failure] on error.
   * @throws CancellationException if the coroutine is canceled during execution.
   */
  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
      try {
        require(preferences.userId.isNotBlank()) { "User ID must not be blank" }
        collection.document(preferences.userId).set(preferences).await()
        Result.success(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
