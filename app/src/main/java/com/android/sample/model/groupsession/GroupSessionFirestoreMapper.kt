// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventFirestoreMapper
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession

/** Maps group sessions to their explicit Firestore wire format. */
internal object GroupSessionFirestoreMapper {
  const val COLLECTION = "groupSessions"

  /** Encodes a session without storing its document ID. */
  fun encode(session: GroupSession): Map<String, Any?> =
      with(session) {
        mapOf(
            "createdBy" to createdBy,
            "memberIds" to memberIds,
            "type" to type.name,
            "subject" to subject,
            "startsAt" to EventFirestoreMapper.timestamp(startsAt),
            "endsAt" to EventFirestoreMapper.timestamp(endsAt),
            "createdAt" to EventFirestoreMapper.timestamp(createdAt),
            "updatedAt" to EventFirestoreMapper.timestamp(updatedAt),
            "location" to location,
            "description" to description,
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
          type = EventType.valueOf(data["type"] as String),
          subject = data.optionalString("subject"),
          startsAt = instant(data["startsAt"]),
          endsAt = instant(data["endsAt"]),
          createdAt = instant(data["createdAt"]),
          updatedAt = instant(data["updatedAt"]),
          location = data.optionalString("location"),
          description = data.optionalString("description"),
      )
    }
  }
}
