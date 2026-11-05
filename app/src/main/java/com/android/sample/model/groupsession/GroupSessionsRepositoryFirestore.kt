// Co-authored-by: GitHub Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventFirestoreMapper
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.GroupSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Firestore-backed implementation of [GroupSessionsRepository]. */
class GroupSessionsRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : GroupSessionsRepository {
  /** Resolves the shared public collection only after caller validation has completed. */
  private fun sessions() = firestore.collection(GroupSessionFirestoreMapper.COLLECTION)

  /** Observes signed-in users' public sessions that have not ended, in stable schedule order. */
  override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
      observe { emit ->
        sessions()
            .whereGreaterThan("endsAt", EventFirestoreMapper.timestamp(endingAfter))
            .addSnapshotListener { snapshot, error ->
              emit(
                  capture {
                    checkSignedIn()
                    if (error != null) throw error
                    // Sorting on the device keeps this query free of a composite index.
                    // This list is shared, so one bad document must not break it for every user.
                    requireNotNull(snapshot)
                        .documents
                        .mapNotNull { document ->
                          document.data?.let { data ->
                            try {
                              GroupSessionFirestoreMapper.decode(document.id, data)
                            } catch (exception: EventsException) {
                              if (exception.error == EventsError.INVALID_EVENT_DATA) null
                              else throw exception
                            }
                          }
                        }
                        .sortedWith(
                            compareBy<GroupSession>(
                                { it.startsAt },
                                { it.endsAt },
                                { it.id },
                            )
                        )
                  },
                  error != null,
              )
            }
      }

  /** Generates a unique session ID locally without contacting Firestore. */
  override fun newSessionId(): String = UUID.randomUUID().toString()

  /** Reads a session from Firestore and reports missing or malformed documents explicitly. */
  override suspend fun getSession(sessionId: String): Result<GroupSession> = execute {
    validateIds(sessionId)
    checkSignedIn()
    val snapshot = sessions().document(sessionId).get().await()
    if (!snapshot.exists()) throw EventsException(EventsError.NOT_FOUND)
    GroupSessionFirestoreMapper.decode(sessionId, requireNotNull(snapshot.data))
  }

  /** Creates or edits a session atomically, waiting for the server transaction to finish. */
  override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> = execute {
    validateIds(userId, session.id, session.createdBy, *session.memberIds.toTypedArray())
    checkOwner(userId)
    if (userId != session.createdBy) throw EventsException(EventsError.PERMISSION_DENIED)
    val document = sessions().document(session.id)
    firestore
        .runTransaction { transaction ->
          val snapshot = transaction.get(document)
          if (!snapshot.exists()) {
            if (session.memberIds != listOf(session.createdBy)) {
              throw EventsException(EventsError.PERMISSION_DENIED)
            }
            transaction.set(document, GroupSessionFirestoreMapper.encode(session))
          } else {
            val stored =
                GroupSessionFirestoreMapper.decode(session.id, requireNotNull(snapshot.data))
            if (stored.createdBy != userId || session.createdBy != stored.createdBy) {
              throw EventsException(EventsError.PERMISSION_DENIED)
            }
            transaction.update(document, editableFields(session))
          }
          Unit
        }
        .await()
  }

  /** Deletes an existing session atomically after verifying its creator. */
  override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> = execute {
    validateIds(userId, sessionId)
    checkOwner(userId)
    val document = sessions().document(sessionId)
    firestore
        .runTransaction { transaction ->
          val snapshot = transaction.get(document)
          if (snapshot.exists()) {
            if (snapshot.getString("createdBy") != userId) {
              throw EventsException(EventsError.PERMISSION_DENIED)
            }
            transaction.delete(document)
          }
          Unit
        }
        .await()
  }

  /**
   * Adds a member atomically, preserving concurrent updates and treating repeat joins as no-ops.
   */
  override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> = execute {
    validateIds(userId, sessionId)
    checkOwner(userId)
    val document = sessions().document(sessionId)
    firestore
        .runTransaction { transaction ->
          val snapshot = transaction.get(document)
          val stored = decodeExisting(sessionId, snapshot.data, snapshot.exists())
          if (userId !in stored.memberIds) {
            if (stored.memberIds.size >= GroupSession.MAX_MEMBERS) {
              throw EventsException(EventsError.PERMISSION_DENIED)
            }
            transaction.update(document, "memberIds", FieldValue.arrayUnion(userId))
          }
          Unit
        }
        .await()
  }

  /** Removes a member atomically; the creator cannot leave and non-member leaves are no-ops. */
  override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> = execute {
    validateIds(userId, sessionId)
    checkOwner(userId)
    val document = sessions().document(sessionId)
    firestore
        .runTransaction { transaction ->
          val snapshot = transaction.get(document)
          val stored = decodeExisting(sessionId, snapshot.data, snapshot.exists())
          if (userId == stored.createdBy) throw EventsException(EventsError.INVALID_ARGUMENT)
          if (userId in stored.memberIds) {
            transaction.update(document, "memberIds", FieldValue.arrayRemove(userId))
          }
          Unit
        }
        .await()
  }

  /** Builds the only fields a creator is allowed to edit on an existing session. */
  private fun editableFields(session: GroupSession): Map<String, Any?> =
      GroupSessionFirestoreMapper.encode(session).filterKeys { it in EDITABLE_FIELDS }

  /** Decodes a transaction read and reports a missing document as NOT_FOUND. */
  private fun decodeExisting(
      id: String,
      data: Map<String, Any?>?,
      exists: Boolean,
  ): GroupSession {
    if (!exists) throw EventsException(EventsError.NOT_FOUND)
    return GroupSessionFirestoreMapper.decode(id, requireNotNull(data))
  }

  /** Validates caller and document IDs before any Firestore operation. */
  private fun validateIds(vararg ids: String) {
    if (ids.any { it.isBlank() || '/' in it }) {
      throw EventsException(EventsError.INVALID_ARGUMENT)
    }
  }

  /** Requires an authenticated user for public reads and writes. */
  private fun checkSignedIn() {
    if (auth.currentUser?.uid.isNullOrBlank()) {
      throw EventsException(EventsError.PERMISSION_DENIED)
    }
  }

  /** Requires [userId] to match the currently authenticated Firebase user. */
  private fun checkOwner(userId: String) {
    if (auth.currentUser?.uid != userId) {
      throw EventsException(EventsError.PERMISSION_DENIED)
    }
  }

  /** Registers a live query and closes it when collection or authentication ends. */
  private fun <T> observe(
      subscribe: ((Result<T>, Boolean) -> Unit) -> ListenerRegistration
  ): Flow<Result<T>> = callbackFlow {
    var registration: ListenerRegistration? = null
    val authListener = FirebaseAuth.AuthStateListener {
      if (it.currentUser?.uid.isNullOrBlank()) {
        trySend(Result.failure(EventsException(EventsError.PERMISSION_DENIED)))
        close()
      }
    }
    try {
      checkSignedIn()
      registration = subscribe { result, terminal ->
        trySend(result)
        if (terminal) close()
      }
      auth.addAuthStateListener(authListener)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      trySend(Result.failure(domainError(exception)))
      close()
    }
    awaitClose {
      registration?.remove()
      auth.removeAuthStateListener(authListener)
    }
  }
      .distinctUntilChanged()

  /** Converts synchronous listener or decoding failures into repository results. */
  private fun <T> capture(block: () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(domainError(exception))
      }

  /** Converts suspendable failures into repository results while preserving cancellation. */
  private suspend fun <T> execute(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(domainError(exception))
      }

  /** Maps persistence failures to the stable event-domain categories. */
  private fun domainError(exception: Exception): EventsException =
      when (exception) {
        is EventsException -> exception
        is FirebaseFirestoreException ->
            EventsException(
                when (exception.code) {
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
                  FirebaseFirestoreException.Code.UNAUTHENTICATED -> EventsError.PERMISSION_DENIED
                  else -> EventsError.STORAGE_FAILURE
                },
                exception,
            )
        is IllegalArgumentException -> EventsException(EventsError.INVALID_ARGUMENT, exception)
        else -> EventsException(EventsError.UNKNOWN, exception)
      }

  private companion object {
    val EDITABLE_FIELDS =
        setOf("type", "subject", "startsAt", "endsAt", "updatedAt", "location", "description")
  }
}
