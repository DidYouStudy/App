// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Private event persistence with local-first writes. Server rejection/status UI is intentionally
 * outside this contract. The authenticated owner is checked even for cached reads.
 */
class EventsRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : EventsRepository {
  /** Observes strict overlap, including cross-midnight entries; malformed matching records fail. */
  override fun observeEvents(
      ownerId: String,
      from: Instant,
      to: Instant,
  ): Flow<Result<List<Event>>> =
      observe(ownerId) { emit ->
        requireArgument(to.isAfter(from))
        events(ownerId)
            .whereLessThan("startsAt", EventFirestoreMapper.exclusiveUpperBound(to))
            .whereGreaterThan("endsAt", EventFirestoreMapper.timestamp(from))
            .orderBy("startsAt")
            .orderBy("endsAt")
            .addSnapshotListener { snapshot, error ->
              emit(
                  capture {
                    checkOwner(ownerId)
                    if (error != null) throw error
                    requireNotNull(snapshot)
                        .documents
                        .map {
                          EventFirestoreMapper.decode(it.id, ownerId, requireNotNull(it.data))
                        }
                        .sortedWith(compareBy<Event>({ it.startsAt }, { it.endsAt }, { it.id }))
                  },
                  error != null,
              )
            }
      }

  /** Observes one summary document, not every event in the user's history. */
  override fun observeSubjects(ownerId: String): Flow<Result<List<String>>> =
      observe(ownerId) { emit ->
        subjects(ownerId).addSnapshotListener { snapshot, error ->
          emit(
              capture {
                checkOwner(ownerId)
                if (error != null) throw error
                SubjectCatalogFirestoreMapper.decode(requireNotNull(snapshot).data)
              },
              error != null,
          )
        }
      }

  /** Generates a stable ID locally, including when signed out or offline. */
  override fun newEventId(): String = UUID.randomUUID().toString()

  /** Reads from the server when available, falling back to the SDK cache offline. */
  override suspend fun getEvent(ownerId: String, eventId: String): Result<Event> = execute {
    validate(ownerId, eventId)
    val document = events(ownerId).document(eventId).get().await()
    checkOwner(ownerId)
    if (!document.exists()) throw EventsException(EventsError.NOT_FOUND)
    EventFirestoreMapper.decode(eventId, ownerId, requireNotNull(document.data))
  }

  /** Atomically queues the event replacement and the subject's latest spelling/recency. */
  override suspend fun saveEvent(event: Event): Result<Unit> = execute {
    validate(event.ownerId, event.id)
    val data = EventFirestoreMapper.encode(event)
    // Revalidate nested tasks too: callers may have supplied a mutable list to the domain model.
    EventFirestoreMapper.decode(event.id, event.ownerId, data)
    val batch = firestore.batch().set(events(event.ownerId).document(event.id), data)
    SubjectCatalogFirestoreMapper.encode(event)?.let {
      batch.set(subjects(event.ownerId), it, SetOptions.merge())
    }
    commitLocally(batch, event.ownerId, event.id)
  }

  /** Queues an idempotent deletion without removing historical subject suggestions. */
  override suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit> = execute {
    validate(ownerId, eventId)
    commitLocally(firestore.batch().delete(events(ownerId).document(eventId)), ownerId, eventId)
  }

  /**
   * A cache-only query queued after commit acts as a local-write barrier on Firestore's serial
   * worker queue. Unlike commit.await(), it does not wait for the server. A query also handles
   * deleting a missing document. Real-SDK offline/restart tests guard this SDK ordering assumption.
   * Cancelling the caller does not undo an already queued write.
   */
  private suspend fun commitLocally(batch: WriteBatch, owner: String, id: String) {
    val acknowledgement = batch.commit()
    events(owner).whereEqualTo(FieldPath.documentId(), id).get(Source.CACHE).await()
    if (acknowledgement.isComplete && !acknowledgement.isSuccessful) {
      throw acknowledgement.exception ?: EventsException(EventsError.STORAGE_FAILURE)
    }
    checkOwner(owner)
  }

  /** Registers both listeners per collector and closes them when cancelled or signed out. */
  private fun <T> observe(
      owner: String,
      subscribe: ((Result<T>, Boolean) -> Unit) -> ListenerRegistration,
  ): Flow<Result<T>> = callbackFlow {
    var registration: ListenerRegistration? = null
    val authListener = FirebaseAuth.AuthStateListener {
      if (it.currentUser?.uid != owner) {
        trySend(Result.failure(EventsException(EventsError.PERMISSION_DENIED)))
        close()
      }
    }
    try {
      validate(owner)
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

  /** Resolves the private collection; the parent user document need not exist. */
  private fun events(owner: String) =
      firestore.collection("users").document(owner).collection("events")

  /** Resolves the single subject summary document. */
  private fun subjects(owner: String) =
      firestore.collection("users").document(owner).collection("meta").document("subjects")

  /** Rejects invalid document path segments before contacting Firestore. */
  private fun validate(owner: String, id: String? = null) {
    requireArgument(
        owner.isNotBlank() && '/' !in owner && (id == null || (id.isNotBlank() && '/' !in id))
    )
    checkOwner(owner)
  }

  /** Prevents cached data or queued writes from bypassing the current user's ownership. */
  private fun checkOwner(owner: String) {
    if (auth.currentUser?.uid != owner) throw EventsException(EventsError.PERMISSION_DENIED)
  }

  /** Reports caller errors using the existing domain category. */
  private fun requireArgument(condition: Boolean) {
    if (!condition) throw EventsException(EventsError.INVALID_ARGUMENT)
  }

  /** Maps synchronous listener/mapping failures while preserving cancellation. */
  private fun <T> capture(block: () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(domainError(e))
      }

  /** Maps suspendable SDK failures while preserving cancellation. */
  private suspend fun <T> execute(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(domainError(e))
      }

  /** Keeps Firebase exceptions out of callers' error handling. */
  private fun domainError(e: Exception): EventsException =
      when (e) {
        is EventsException -> e
        is FirebaseFirestoreException ->
            EventsException(
                when (e.code) {
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
                  FirebaseFirestoreException.Code.UNAUTHENTICATED -> EventsError.PERMISSION_DENIED
                  else -> EventsError.STORAGE_FAILURE
                },
                e,
            )
        is IllegalArgumentException -> EventsException(EventsError.INVALID_ARGUMENT, e)
        else -> EventsException(EventsError.UNKNOWN, e)
      }
}
