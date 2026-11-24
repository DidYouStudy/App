// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventFirestoreMapper
import com.android.sample.model.event.GroupSession

/** Maps group sessions to their explicit Firestore wire format. */
internal object GroupSessionFirestoreMapper {
  const val COLLECTION = "groupSessions"

  /** Creation time field; `firestore.rules` requires the server time when a session is created. */
  const val CREATED_AT = "createdAt"

  /** Last-edit time field; `firestore.rules` requires the server time on every creator write. */
  const val UPDATED_AT = "updatedAt"

  /** Encodes a session without storing its document ID. */
  fun encode(session: GroupSession): Map<String, Any?> =
      with(session) {
        EventFirestoreMapper.encodeDetails(details) +
            mapOf(
                "createdBy" to createdBy,
                "memberIds" to memberIds,
                CREATED_AT to EventFirestoreMapper.timestamp(createdAt),
                UPDATED_AT to EventFirestoreMapper.timestamp(updatedAt),
            )
      }

  /** Decodes a session using its document ID and stored fields. */
  fun decode(id: String, data: Map<String, Any?>): GroupSession = EventFirestoreMapper.decodeData {
    with(EventFirestoreMapper) {
      val memberIds = (data["memberIds"] as List<*>).map { it as String }
      GroupSession(
          id = id,
          createdBy = data["createdBy"] as String,
          memberIds = memberIds,
          details = decodeDetails(data),
          createdAt = instant(data[CREATED_AT]),
          updatedAt = instant(data[UPDATED_AT]),
      )
    }
  }
}
