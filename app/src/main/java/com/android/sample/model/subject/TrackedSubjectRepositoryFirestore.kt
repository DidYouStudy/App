// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [TrackedSubjectRepository].
 *
 * Stores decisions under `users/{ownerId}/meta/trackedSubjects`. Local-first writes complete when
 * stored in the local cache, without waiting for server confirmation.
 */
class TrackedSubjectRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : TrackedSubjectRepository {

  override fun observeDecisions(ownerId: String): Flow<Result<SubjectDecisions>> =
      observe(ownerId) { emit ->
        trackedSubjectsDoc(ownerId).addSnapshotListener { snapshot, error ->
          emit(
              capture {
                checkOwner(ownerId)
                if (error != null) throw error
                TrackedSubjectFirestoreMapper.decode(snapshot?.data)
              },
              error != null,
          )
        }
      }

  override suspend fun saveDecisions(
      ownerId: String,
      decisions: Map<String, Boolean>,
  ): Result<Unit> = execute {
    validateOwner(ownerId)
    if (decisions.isEmpty()) return@execute

    val seenKeys = mutableSetOf<String>()
    for (spelling in decisions.keys) {
      requireArgument(spelling.isNotBlank())
      val key = SubjectCatalogFirestoreMapper.key(spelling)
      requireArgument(seenKeys.add(key))
    }

    val encoded = TrackedSubjectFirestoreMapper.encode(decisions)
    val docRef = trackedSubjectsDoc(ownerId)
    val acknowledgement = docRef.set(encoded, SetOptions.merge())

    metaCollection(ownerId)
        .whereEqualTo(FieldPath.documentId(), "trackedSubjects")
        .get(Source.CACHE)
        .await()

    if (acknowledgement.isComplete && !acknowledgement.isSuccessful) {
      throw acknowledgement.exception
          ?: TrackedSubjectException(TrackedSubjectError.STORAGE_FAILURE)
    }
    checkOwner(ownerId)
  }

  private fun trackedSubjectsDoc(ownerId: String) =
      metaCollection(ownerId).document("trackedSubjects")

  private fun metaCollection(ownerId: String) =
      firestore.collection("users").document(ownerId).collection("meta")

  private fun validateOwner(ownerId: String) {
    requireArgument(ownerId.isNotBlank() && '/' !in ownerId)
    checkOwner(ownerId)
  }

  private fun checkOwner(ownerId: String) {
    if (auth.currentUser?.uid != ownerId) {
      throw TrackedSubjectException(TrackedSubjectError.PERMISSION_DENIED)
    }
  }

  private fun requireArgument(condition: Boolean) {
    if (!condition) throw TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT)
  }

  private fun <T> observe(
      ownerId: String,
      subscribe: ((Result<T>, Boolean) -> Unit) -> ListenerRegistration,
  ): Flow<Result<T>> = callbackFlow {
    var registration: ListenerRegistration? = null
    val authListener = FirebaseAuth.AuthStateListener {
      if (it.currentUser?.uid != ownerId) {
        trySend(Result.failure(TrackedSubjectException(TrackedSubjectError.PERMISSION_DENIED)))
        close()
      }
    }
    try {
      validateOwner(ownerId)
      registration = subscribe { result, terminal ->
        trySend(result)
        if (terminal) close()
      }
      auth.addAuthStateListener(authListener)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      trySend(Result.failure(domainError(e)))
      close()
    }
    awaitClose {
      registration?.remove()
      auth.removeAuthStateListener(authListener)
    }
  }
      .distinctUntilChanged()

  private fun <T> capture(block: () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(domainError(e))
      }

  private suspend fun <T> execute(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(domainError(e))
      }

  private fun domainError(e: Exception): TrackedSubjectException =
      when (e) {
        is TrackedSubjectException -> e
        is FirebaseFirestoreException ->
            TrackedSubjectException(
                when (e.code) {
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
                  FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                      TrackedSubjectError.PERMISSION_DENIED
                  else -> TrackedSubjectError.STORAGE_FAILURE
                },
                e,
            )
        is IllegalArgumentException ->
            TrackedSubjectException(TrackedSubjectError.INVALID_ARGUMENT, e)
        else -> TrackedSubjectException(TrackedSubjectError.UNKNOWN, e)
      }
}
