// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Firestore-backed group session repository placeholder for repository tests. */
class GroupSessionsRepositoryFirestore(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : GroupSessionsRepository {
  override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
      throw NotImplementedError()

  override fun newSessionId(): String = throw NotImplementedError()

  override suspend fun getSession(sessionId: String): Result<GroupSession> =
      throw NotImplementedError()

  override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
      throw NotImplementedError()

  override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
      throw NotImplementedError()

  override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
      throw NotImplementedError()

  override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
      throw NotImplementedError()
}
