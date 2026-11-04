// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventFirestoreMapper
import com.android.sample.model.event.GroupSession

/** Maps group sessions to their explicit Firestore wire format. */
internal object GroupSessionFirestoreMapper {
  const val COLLECTION = "groupSessions"

  /** Encodes a session without storing its document ID. */
  fun encode(session: GroupSession): Map<String, Any?> =
      with(session) {
        EventFirestoreMapper.encodeDetails(details) +
            mapOf(
                "createdBy" to createdBy,
                "memberIds" to memberIds,
                "createdAt" to EventFirestoreMapper.timestamp(createdAt),
                "updatedAt" to EventFirestoreMapper.timestamp(updatedAt),
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
          createdAt = instant(data["createdAt"]),
          updatedAt = instant(data["updatedAt"]),
      )
    }
  }
}
