// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Unit tests for [TrackedSubjectFirestoreMapper] encoding and decoding. */
class TrackedSubjectFirestoreMapperTest {

  /** Encodes decision map into nested Firestore fields with trimmed spelling. */
  @Test
  fun encode_convertsDecisionsToMapWithTrimmedSpelling() {
    val decisions = mapOf(" Analysis I " to true, "Physics" to false)
    val encoded = TrackedSubjectFirestoreMapper.encode(decisions)

    val key1 = SubjectCatalogFirestoreMapper.key("Analysis I")
    val key2 = SubjectCatalogFirestoreMapper.key("Physics")

    val expected =
        mapOf(
            "subjects" to
                mapOf(
                    key1 to mapOf("spelling" to "Analysis I", "tracked" to true),
                    key2 to mapOf("spelling" to "Physics", "tracked" to false),
                )
        )

    assertEquals(expected, encoded)
  }

  /** Decoding null data returns empty SubjectDecisions. */
  @Test
  fun decode_nullData_returnsEmptySubjectDecisions() {
    val decoded = TrackedSubjectFirestoreMapper.decode(null)
    assertTrue(decoded.decisions.isEmpty())
  }

  /** Decoding valid Firestore payload converts entries correctly. */
  @Test
  fun decode_validData_returnsDecisions() {
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")
    val data =
        mapOf("subjects" to mapOf(key to mapOf("spelling" to "Analysis I", "tracked" to true)))

    val decoded = TrackedSubjectFirestoreMapper.decode(data)
    assertTrue(decoded.isTracked("Analysis I"))
  }

  /** Decoding payload with missing or invalid keys throws INVALID_DATA. */
  @Test
  fun decode_invalidSchema_throwsInvalidData() {
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")

    val invalidCases =
        listOf(
            mapOf("wrong_key" to emptyMap<String, Any>()),
            mapOf("subjects" to "not_a_map"),
            mapOf("subjects" to mapOf(123 to mapOf("spelling" to "Analysis", "tracked" to true))),
            mapOf("subjects" to mapOf(key to "not_a_map")),
            mapOf("subjects" to mapOf(key to mapOf("spelling" to "Analysis"))),
            mapOf("subjects" to mapOf(key to mapOf("tracked" to true))),
            mapOf("subjects" to mapOf(key to mapOf("spelling" to " ", "tracked" to true))),
            mapOf(
                "subjects" to
                    mapOf("wrong_key" to mapOf("spelling" to "Analysis I", "tracked" to true))
            ),
        )

    for (case in invalidCases) {
      try {
        TrackedSubjectFirestoreMapper.decode(case)
        fail("Expected INVALID_DATA for case: $case")
      } catch (e: TrackedSubjectException) {
        assertEquals(TrackedSubjectError.INVALID_DATA, e.error)
      }
    }
  }

  private fun payloadWithPriority(priority: Any?): Map<String, Any?> {
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")
    return mapOf(
        "subjects" to
            mapOf(
                key to mapOf("spelling" to "Analysis I", "tracked" to true, "priority" to priority)
            )
    )
  }

  /** Encoding a priority writes only the priority field of that subject's entry. */
  @Test
  fun encodePriority_containsOnlyThePriorityField() {
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")

    val encoded = TrackedSubjectFirestoreMapper.encodePriority("Analysis I", SubjectPriority(3))

    assertEquals(mapOf("subjects" to mapOf(key to mapOf("priority" to 3))), encoded)
  }

  /** Spelling and case variants of a subject are encoded under the same key. */
  @Test
  fun encodePriority_usesTheNormalizedSubjectKey() {
    val plain = TrackedSubjectFirestoreMapper.encodePriority("Analysis I", SubjectPriority(2))
    val variant = TrackedSubjectFirestoreMapper.encodePriority(" ANALYSIS i ", SubjectPriority(2))

    assertEquals(plain, variant)
  }

  /** Encoding decisions does not include a priority, so a merging write keeps the stored one. */
  @Test
  fun encode_doesNotContainPriority() {
    val encoded = TrackedSubjectFirestoreMapper.encode(mapOf("Analysis I" to true))

    val entry = (encoded["subjects"] as Map<*, *>).values.single() as Map<*, *>
    assertTrue("priority" !in entry.keys)
  }

  /** An entry without a priority field decodes with a null priority. */
  @Test
  fun decode_withoutPriority_returnsNullPriority() {
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")
    val data =
        mapOf("subjects" to mapOf(key to mapOf("spelling" to "Analysis I", "tracked" to true)))

    assertNull(TrackedSubjectFirestoreMapper.decode(data).priorityOf("Analysis I"))
  }

  /** A priority stored as Long, which is how Firestore returns integers, decodes correctly. */
  @Test
  fun decode_longPriority_returnsPriority() {
    val decoded = TrackedSubjectFirestoreMapper.decode(payloadWithPriority(4L))

    assertEquals(SubjectPriority(4), decoded.priorityOf("Analysis I"))
  }

  /** A priority stored as Int decodes correctly too. */
  @Test
  fun decode_intPriority_returnsPriority() {
    val decoded = TrackedSubjectFirestoreMapper.decode(payloadWithPriority(2))

    assertEquals(SubjectPriority(2), decoded.priorityOf("Analysis I"))
  }

  /** The lowest and highest levels decode correctly. */
  @Test
  fun decode_boundaryPriorities_areAccepted() {
    val low = TrackedSubjectFirestoreMapper.decode(payloadWithPriority(1L))
    val high = TrackedSubjectFirestoreMapper.decode(payloadWithPriority(5L))

    assertEquals(SubjectPriority(SubjectPriority.MIN), low.priorityOf("Analysis I"))
    assertEquals(SubjectPriority(SubjectPriority.MAX), high.priorityOf("Analysis I"))
  }

  /** Reading keeps the tracked flag next to the priority. */
  @Test
  fun decode_priorityDoesNotChangeTrackedFlag() {
    val decoded = TrackedSubjectFirestoreMapper.decode(payloadWithPriority(3L))

    assertTrue(decoded.isTracked("Analysis I"))
  }

  /** Out-of-range, fractional or non-numeric priorities are rejected as INVALID_DATA. */
  @Test
  fun decode_invalidPriority_throwsInvalidData() {
    val invalidPriorities: List<Any> = listOf(0L, 6L, -1L, 0, 6, 3.0, 3.5, "3", true)

    for (priority in invalidPriorities) {
      try {
        TrackedSubjectFirestoreMapper.decode(payloadWithPriority(priority))
        fail("Expected INVALID_DATA for priority: $priority")
      } catch (e: TrackedSubjectException) {
        assertEquals(TrackedSubjectError.INVALID_DATA, e.error)
      }
    }
  }
}
