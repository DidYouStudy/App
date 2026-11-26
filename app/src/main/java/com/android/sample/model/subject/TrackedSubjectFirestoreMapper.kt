// Co-authored-by: Gemini 3.6 Flash
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper

/** Maps Firestore document data for tracked subjects. */
internal object TrackedSubjectFirestoreMapper {

  /**
   * Top-level field holding one entry per subject, keyed by [SubjectCatalogFirestoreMapper.key].
   */
  private const val FIELD_SUBJECTS = "subjects"

  /** Entry field holding the latest display name of the subject. */
  private const val FIELD_SPELLING = "spelling"

  /** Entry field holding whether the subject is tracked. */
  private const val FIELD_TRACKED = "tracked"

  /** Optional entry field holding the [SubjectPriority] level as an integer. */
  private const val FIELD_PRIORITY = "priority"

  /**
   * Encodes decision map into Firestore document fields.
   *
   * The entries carry no priority, so a merging write keeps any priority already stored.
   */
  fun encode(decisions: Map<String, Boolean>): Map<String, Any> {
    val subjectsMap =
        decisions.entries.associate { (spelling, tracked) ->
          SubjectCatalogFirestoreMapper.key(spelling) to
              mapOf(
                  FIELD_SPELLING to spelling.trim(),
                  FIELD_TRACKED to tracked,
              )
        }
    return mapOf(FIELD_SUBJECTS to subjectsMap)
  }

  /**
   * Encodes a priority change for one subject as Firestore document fields.
   *
   * Only the priority field of that subject's entry is included, so a merging write leaves the
   * tracked flag, the spelling and every other subject untouched.
   */
  fun encodePriority(subject: String, priority: SubjectPriority): Map<String, Any> =
      mapOf(
          FIELD_SUBJECTS to
              mapOf(
                  SubjectCatalogFirestoreMapper.key(subject) to
                      mapOf(FIELD_PRIORITY to priority.value)
              )
      )

  /**
   * Decodes Firestore document fields into [SubjectDecisions].
   *
   * @throws TrackedSubjectException with [TrackedSubjectError.INVALID_DATA] if the data is
   *   malformed, including a priority that is not an integer within [SubjectPriority.RANGE].
   */
  fun decode(data: Map<String, Any?>?): SubjectDecisions {
    if (data == null) return SubjectDecisions()
    try {
      if (data.keys != setOf(FIELD_SUBJECTS)) {
        throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
      }
      val subjectsRaw = data[FIELD_SUBJECTS]
      if (subjectsRaw !is Map<*, *>) {
        throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
      }
      val decisionsMap = mutableMapOf<String, SubjectDecision>()
      for ((key, value) in subjectsRaw) {
        if (key !is String || value !is Map<*, *>) {
          throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
        }
        val spelling = value[FIELD_SPELLING] as? String
        val tracked = value[FIELD_TRACKED] as? Boolean
        if (
            spelling == null ||
                tracked == null ||
                spelling.isBlank() ||
                key != SubjectCatalogFirestoreMapper.key(spelling)
        ) {
          throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
        }
        decisionsMap[key] =
            SubjectDecision(
                spelling = spelling,
                tracked = tracked,
                priority = decodePriority(value[FIELD_PRIORITY]),
            )
      }
      return SubjectDecisions(decisionsMap)
    } catch (e: TrackedSubjectException) {
      throw e
    } catch (e: Exception) {
      throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA, e)
    }
  }

  /** Returns null when [raw] is absent, or the priority it holds; any other value is invalid. */
  private fun decodePriority(raw: Any?): SubjectPriority? {
    if (raw == null) return null
    // Firestore returns integers as Long; accept Int too, but never fractional numbers.
    val level =
        when (raw) {
          is Long -> raw
          is Int -> raw.toLong()
          else -> throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
        }
    if (level !in SubjectPriority.RANGE.first.toLong()..SubjectPriority.RANGE.last.toLong()) {
      throw TrackedSubjectException(TrackedSubjectError.INVALID_DATA)
    }
    return SubjectPriority(level.toInt())
  }
}
