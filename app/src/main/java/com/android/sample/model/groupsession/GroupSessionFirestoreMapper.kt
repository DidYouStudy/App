// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

/** Maps group sessions to their explicit Firestore wire format. */
internal object GroupSessionFirestoreMapper {
  const val COLLECTION = "groupSessions"

  /** Encodes a session without storing its document ID. */
  fun encode(session: GroupSession): Map<String, Any?> = TODO("Not implemented")

  /** Decodes a session using its document ID and stored fields. */
  fun decode(id: String, data: Map<String, Any?>): GroupSession = TODO("Not implemented")
}
