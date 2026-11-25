// Co-authored-by: Copilot
// Co-authored-by: Claude Code
package com.android.sample.model.profile

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Firestore implementation of [ProfileRepository].
 *
 * Profiles are stored in `profiles/{userId}`. Firebase access stays in this model-layer class so
 * ViewModels depend only on [ProfileRepository].
 *
 * @param firestore Firestore client used for profile reads and transactions.
 * @param updateTimeoutMillis longest time [updateUsername] waits for its transaction before giving
 *   up with [ProfileError.OFFLINE]; transactions need the server, so offline they would otherwise
 *   keep retrying.
 */
class ProfileRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val updateTimeoutMillis: Long = UPDATE_TIMEOUT_MILLIS,
) : ProfileRepository {
  private val collection = firestore.collection("profiles")

  /** Username reservations stored in `usernames/{lowercase username}` as `{uid}`. */
  private val usernames by lazy { firestore.collection("usernames") }

  /**
   * Reads a profile document without creating one.
   *
   * The server is read first; the local cache is used only when the server is unreachable
   * ([FirebaseFirestoreException.Code.UNAVAILABLE], e.g. offline), so other errors such as
   * `PERMISSION_DENIED` are never hidden by stale data.
   *
   * @param userId stable authentication identifier and profile document ID.
   * @return [Result.success] containing the stored profile, or `null` when absent; [Result.failure]
   *   containing [ProfileException] with a stable [ProfileError] category ([ProfileError.OFFLINE]
   *   when the server is unreachable and nothing is cached).
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun getProfile(userId: String): Result<UserProfile?> {
    if (userId.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return execute {
      val snapshot = getWithCacheFallback(collection.document(userId))
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
   * @return [Result.success] containing the existing or newly created profile; [Result.failure]
   *   containing [ProfileException] with a stable [ProfileError] category.
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
   * Changes a profile's username while keeping usernames unique.
   *
   * Each username is reserved by a `usernames/{lowercase name}` document holding the owner's UID.
   * One transaction checks the reservation, frees the previous one, claims the new one and updates
   * the profile, so two users can never hold the same name.
   *
   * @param userId stable authentication identifier and profile document ID.
   * @param username requested username; validated with [UsernamePolicy.validate].
   * @return [Result.success] containing the updated profile; [Result.failure] containing
   *   [ProfileException] with [ProfileError.INVALID_USER_ID], [ProfileError.INVALID_USERNAME],
   *   [ProfileError.USERNAME_TAKEN], [ProfileError.OFFLINE] (also when the transaction takes longer
   *   than [updateTimeoutMillis]) or a storage failure category.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> {
    if (userId.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    val validName =
        UsernamePolicy.validate(username)
            ?: return Result.failure(ProfileException(ProfileError.INVALID_USERNAME))
    return execute { runUsernameTransactionWithTimeout(userId, validName) }
  }

  /**
   * Runs [applyUsernameChange] in a transaction, giving up after [updateTimeoutMillis].
   *
   * Transactions need the server, so offline they would otherwise keep retrying; on timeout the
   * failure is reported as [ProfileError.OFFLINE].
   *
   * @param userId profile document ID.
   * @param validName username already validated with [UsernamePolicy.validate].
   * @return the updated profile.
   * @throws ProfileException on timeout ([ProfileError.OFFLINE]) or any transaction failure.
   */
  private suspend fun runUsernameTransactionWithTimeout(
      userId: String,
      validName: String,
  ): UserProfile {
    // Set once we give up waiting: a transaction that has not started writing by then aborts, so
    // a late retry by Firestore cannot change the name after "offline" was reported. Retrying the
    // same name afterwards is safe: the reservation already belongs to this user.
    val gaveUp = AtomicBoolean(false)
    return try {
      withTimeout(updateTimeoutMillis) {
        firestore
            .runTransaction { transaction ->
              if (gaveUp.get()) throw ProfileException(ProfileError.OFFLINE)
              applyUsernameChange(transaction, userId, validName)
            }
            .await()
      }
    } catch (timeout: TimeoutCancellationException) {
      gaveUp.set(true)
      throw ProfileException(ProfileError.OFFLINE, timeout)
    }
  }

  /**
   * Body of the username transaction: checks the reservation, frees the previous one, claims the
   * new one and updates the profile.
   *
   * @param transaction the active Firestore transaction.
   * @param userId profile document ID.
   * @param validName username already validated with [UsernamePolicy.validate].
   * @return the profile carrying the new username.
   * @throws ProfileException with [ProfileError.INVALID_PROFILE_DATA] when the profile is missing,
   *   or [ProfileError.USERNAME_TAKEN] when another user owns the name.
   */
  private fun applyUsernameChange(
      transaction: Transaction,
      userId: String,
      validName: String,
  ): UserProfile {
    val profileDocument = collection.document(userId)
    val newKey = UsernamePolicy.key(validName)
    val newReservation = usernames.document(newKey)
    // Firestore requires every read before the first write.
    val profileSnapshot = transaction.get(profileDocument)
    val reservationSnapshot = transaction.get(newReservation)
    if (!profileSnapshot.exists()) {
      throw ProfileException(ProfileError.INVALID_PROFILE_DATA)
    }
    val profile = profileSnapshot.toUserProfile(userId)
    val reservationExists = reservationSnapshot.exists()
    if (reservationExists && reservationSnapshot.getString("uid") != userId) {
      throw ProfileException(ProfileError.USERNAME_TAKEN)
    }
    val oldKey = profile.username.takeIf { it.isNotBlank() }?.let(UsernamePolicy::key)
    if (oldKey != null && oldKey != newKey) {
      transaction.delete(usernames.document(oldKey))
    }
    if (!reservationExists) {
      transaction.set(newReservation, mapOf("uid" to userId))
    }
    transaction.update(profileDocument, "username", validName)
    return profile.copy(username = validName)
  }

  /**
   * Sets only the `profilePictureUrl` field of an existing profile document.
   *
   * Firestore's `update` fails when the document is missing, so a profile is never created here
   * without its `uid`; that case is reported as [ProfileError.FIRESTORE_FAILURE].
   *
   * @param userId stable authentication identifier and profile document ID.
   * @param url URL the profile picture can be loaded from.
   * @return [Result.success] once saved; [Result.failure] containing [ProfileException] with a
   *   stable [ProfileError] category.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> {
    if (userId.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return execute {
      collection.document(userId).update(UserProfile::profilePictureUrl.name, url).await()
      Unit
    }
  }

  /**
   * Reads [document] from the server, using the local cache only if the server is unreachable.
   *
   * @param document the profile document to read.
   * @return the server snapshot, or the cached snapshot when the server read fails with
   *   [FirebaseFirestoreException.Code.UNAVAILABLE].
   * @throws FirebaseFirestoreException the original server error when it is not `UNAVAILABLE`, or
   *   when nothing is cached either (the cache error is discarded so callers see the real cause).
   * @throws CancellationException when the coroutine is cancelled.
   */
  private suspend fun getWithCacheFallback(document: DocumentReference): DocumentSnapshot =
      try {
        document.get().await()
      } catch (exception: FirebaseFirestoreException) {
        if (exception.code != FirebaseFirestoreException.Code.UNAVAILABLE) throw exception
        try {
          document.get(Source.CACHE).await()
        } catch (cacheError: Exception) {
          if (cacheError is CancellationException) throw cacheError
          throw exception
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
      // An unreachable server is reported separately so the UI can say "offline", not "failed".
      val error =
          if (exception.code == FirebaseFirestoreException.Code.UNAVAILABLE) ProfileError.OFFLINE
          else ProfileError.FIRESTORE_FAILURE
      Result.failure(ProfileException(error, exception))
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

  private companion object {
    /** Default [updateTimeoutMillis]: a bit above the ~8 s an offline transaction takes to fail. */
    const val UPDATE_TIMEOUT_MILLIS = 10_000L
  }
}
