// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.firebase.Timestamp
import java.util.Locale

/** One summary document with independently mergeable, normalized subject entries. */
internal object SubjectCatalogFirestoreMapper {
  /** Hex UTF-8 keys avoid dots, slashes and reserved field names without hash collisions. */
  fun key(subject: String): String =
      subject.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8).joinToString("") {
        "%02x".format(it.toInt() and 255)
      }

  /** Returns only the entry being changed, so merging preserves other subjects offline. */
  fun encode(event: Event): Map<String, Any>? =
      event.subject?.trim()?.let {
        mapOf(
            "subjects" to
                mapOf(
                    key(it) to
                        mapOf(
                            "spelling" to it,
                            "usedAt" to EventFirestoreMapper.timestamp(event.updatedAt),
                        )
                )
        )
      }

  /** Reads suggestions in recency order, with spelling as the deterministic tie breaker. */
  fun decode(data: Map<String, Any?>?): List<String> = EventFirestoreMapper.decodeData {
    if (data == null) emptyList()
    else {
      val subjects = data["subjects"] as Map<*, *>
      subjects
          .map { (key, value) ->
            val entry = value as Map<*, *>
            val spelling = entry["spelling"] as String
            require(spelling.isNotBlank() && spelling == spelling.trim() && key == key(spelling))
            spelling to (entry["usedAt"] as Timestamp)
          }
          .sortedWith(
              compareByDescending<Pair<String, Timestamp>> { it.second }.thenBy { it.first }
          )
          .map { it.first }
    }
  }
}
