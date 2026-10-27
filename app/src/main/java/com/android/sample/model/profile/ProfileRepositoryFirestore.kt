// Co-authored-by: Copilot
package com.android.sample.model.profile

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [ProfileRepository].
 *
 * Profiles are stored in `profiles/{userId}`. Firebase access stays in this model-layer class so
 * ViewModels depend only on [ProfileRepository].
 *
 * @param firestore Firestore client used for profile reads and transactions.
 */
class ProfileRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ProfileRepository {
  private val collection = firestore.collection("profiles")

  /**
   * Reads a profile document without creating one.
   *
   * @param userId stable authentication identifier and profile document ID.
   * @return [Result.success] containing the stored profile, or `null` when absent; [Result.failure]
   *   containing [ProfileException] with a stable [ProfileError] category.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun getProfile(userId: String): Result<UserProfile?> {
    if (userId.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return execute {
      val snapshot = collection.document(userId).get().await()
      if (!snapshot.exists()) {
        null
      } else {
        snapshot.toUserProfile(userId)
      }
    }
  }

  /**
   * Creates a profile unless a document already exists.
   *
   * The transaction makes the existence check and write atomic. Callers should normally invoke this
   * method only after [getProfile] returns `null`; the transaction read is an internal race-safety
   * check. When another request has already created the profile, the existing document is returned
   * unchanged.
   *
   * @param userId stable authentication identifier and profile document ID.
   * @return [Result.success] containing the existing or newly created profile;
   *   [Result.failure] containing [ProfileException] with a stable [ProfileError] category.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun createProfile(userId: String): Result<UserProfile> {
    if (userId.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return execute {
      val document = collection.document(userId)
      firestore
          .runTransaction { transaction ->
            val snapshot = transaction.get(document)
            if (snapshot.exists()) {
              snapshot.toUserProfile(userId)
            } else {
              val profile = UserProfile(uid = userId)
              transaction.set(document, profile)
              profile
            }
          }
          .await()
    }
  }

  /**
   * Executes a Firestore operation and maps persistence failures to profile-domain errors.
   *
   * Successful values are wrapped in [Result.success]. Coroutine cancellation is rethrown so
   * callers retain structured-concurrency behavior. Known profile and Firestore exceptions keep
   * their specific error categories; other exceptions become [ProfileError.UNKNOWN].
   *
   * @param operation suspendable Firestore operation to execute.
   * @return successful operation value or a [ProfileException]-backed failure.
   * @throws CancellationException when the coroutine is cancelled.
   */
  private suspend fun <T> execute(operation: suspend () -> T): Result<T> {
    return try {
      Result.success(operation())
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: ProfileException) {
      Result.failure(exception)
    } catch (exception: FirebaseFirestoreException) {
      Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE, exception))
    } catch (exception: Exception) {
      Result.failure(ProfileException(ProfileError.UNKNOWN, exception))
    }
  }

  /**
   * Converts a Firestore document into a profile and verifies document ownership.
   *
   * Deserialization failures and UID mismatches are reported as
   * [ProfileError.INVALID_PROFILE_DATA]. The requested UID is checked against the stored profile
   * UID to prevent a document from being returned for the wrong authenticated user.
   *
   * @param userId UID requested by the repository operation.
   * @return deserialized profile belonging to [userId].
   * @throws ProfileException when conversion fails, produces no profile, or the UID mismatches.
   * @throws CancellationException when conversion is cancelled.
   */
  private fun DocumentSnapshot.toUserProfile(userId: String): UserProfile {
    val profile =
        try {
          toObject(UserProfile::class.java)
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          throw ProfileException(ProfileError.INVALID_PROFILE_DATA, exception)
        } ?: throw ProfileException(ProfileError.INVALID_PROFILE_DATA)
    if (profile.uid != userId) {
      throw ProfileException(ProfileError.INVALID_PROFILE_DATA)
    }
    return profile
  }
}
