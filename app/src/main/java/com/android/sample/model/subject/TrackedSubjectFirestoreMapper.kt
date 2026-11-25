// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper

/** Maps Firestore document data for tracked subjects. */
internal object TrackedSubjectFirestoreMapper {

  /** Encodes decision map into Firestore document fields. */
  fun encode(decisions: Map<String, Boolean>): Map<String, Any> {
    val subjectsMap =
        decisions.entries.associate { (spelling, tracked) ->
          SubjectCatalogFirestoreMapper.key(spelling) to
              mapOf(
                  "spelling" to spelling.trim(),
                  "tracked" to tracked,
              )
        }
    return mapOf("subjects" to subjectsMap)
  }

  /** Decodes Firestore document fields into [SubjectDecisions]. */
  fun decode(data: Map<String, Any?>?): SubjectDecisions {
    if (data == null) return SubjectDecisions()
    try {
      if (data.keys != setOf("subjects")) {
        throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
      }
      val subjectsRaw = data["subjects"]
      if (subjectsRaw !is Map<*, *>) {
        throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
      }
      val decisionsMap = mutableMapOf<String, SubjectDecision>()
      for ((key, value) in subjectsRaw) {
        if (key !is String || value !is Map<*, *>) {
          throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
        }
        val spelling = value["spelling"] as? String
        val tracked = value["tracked"] as? Boolean
        if (
            spelling == null ||
                tracked == null ||
                spelling.isBlank() ||
                key != SubjectCatalogFirestoreMapper.key(spelling)
        ) {
          throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
        }
        decisionsMap[key] = SubjectDecision(spelling = spelling, tracked = tracked)
      }
      return SubjectDecisions(decisionsMap)
    } catch (e: TrackedSubjectException) {
      throw e
    } catch (e: Exception) {
      throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA, e)
    }
  }
}
