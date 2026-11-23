// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import org.junit.Assert.assertEquals
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
}
