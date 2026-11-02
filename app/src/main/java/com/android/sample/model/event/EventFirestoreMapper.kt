// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Explicit wire format; missing optional values default, malformed values fail the whole read. */
internal object EventFirestoreMapper {
  /** Serializes caller-supplied UTC timestamps at Firestore microsecond precision. */
  fun encode(event: Event): Map<String, Any?> =
      with(event) {
        mapOf(
            "ownerId" to ownerId,
            "type" to type.name,
            "subject" to subject,
            "startsAt" to timestamp(startsAt),
            "endsAt" to timestamp(endsAt),
            "createdAt" to timestamp(createdAt),
            "updatedAt" to timestamp(updatedAt),
            "location" to location,
            "description" to description,
            "importKey" to importKey,
            "tasks" to
                tasks.map {
                  mapOf("text" to it.text, "completedAt" to it.completedAt?.let(::timestamp))
                },
        )
      }

  /** Uses the document path as the event ID and verifies the stored owner. */
  fun decode(id: String, owner: String, data: Map<String, Any?>): Event = decodeData {
    require(data["ownerId"] == owner)
    Event(
        id,
        owner,
        EventType.valueOf(data["type"] as String),
        data.optionalString("subject"),
        instant(data["startsAt"]),
        instant(data["endsAt"]),
        instant(data["createdAt"]),
        instant(data["updatedAt"]),
        data.optionalString("location"),
        data.optionalString("description"),
        if (!data.containsKey("tasks")) emptyList()
        else
            (data["tasks"] as List<*>).map {
              val task = it as Map<*, *>
              EventTask(task["text"] as String, task["completedAt"]?.let(::instant))
            },
        data.optionalString("importKey"),
    )
  }

  /** Normalizes to server microseconds so local and synchronized values use the same precision. */
  fun timestamp(value: Instant) = Timestamp(value.epochSecond, value.nano / 1000 * 1000)

  /** Rounds an exclusive upper bound up so sub-microsecond query boundaries remain strict. */
  fun exclusiveUpperBound(value: Instant): Timestamp {
    val floor = value.truncatedTo(ChronoUnit.MICROS)
    return timestamp(if (value == floor) value else floor.plusNanos(1000))
  }

  /** Converts a stored timestamp without using the device time zone. */
  private fun instant(value: Any?): Instant =
      (value as Timestamp).let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }

  /** Optional strings tolerate absence and null, but reject incorrect field types. */
  private fun Map<String, Any?>.optionalString(key: String): String? =
      get(key)?.let { it as String }

  /** Makes malformed document failures independent of Firebase and casting exceptions. */
  internal fun <T> decodeData(block: () -> T): T =
      try {
        block()
      } catch (e: Exception) {
        throw EventsException(EventsError.INVALID_EVENT_DATA, e)
      }
}
