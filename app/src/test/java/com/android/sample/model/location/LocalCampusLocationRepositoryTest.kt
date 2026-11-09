// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LocalCampusLocationRepository]: parsing, matching (case, spaces, AND), ranking of
 * exact matches, zones, caching and failures. The JSON is injected, so no Android is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalCampusLocationRepositoryTest {

  /** A small data set: two buildings, a near-duplicate pair for ranking, and one unknown type. */
  private val sampleJson =
      """
      [
        {"name": "BC 010", "zone": "BC", "type": "LECTURE_ROOM", "floor": 0,
         "latitude": 46.52, "longitude": 6.56},
        {"name": "BC 01", "zone": "BC", "type": "LECTURE_ROOM", "floor": 0,
         "latitude": 46.52, "longitude": 6.56},
        {"name": "CM 1 121", "zone": "CM", "type": "LIBRARY", "floor": 1,
         "latitude": 46.51, "longitude": 6.57},
        {"name": "CM 2 10", "zone": "CM", "type": "LAB", "floor": null,
         "latitude": 46.51, "longitude": 6.57, "extra": "ignored"},
        {"name": "ZZ 1", "zone": "ZZ", "type": "SOMETHING_NEW", "floor": 2,
         "latitude": 46.50, "longitude": 6.58}
      ]
      """
          .trimIndent()

  private fun repo(json: String = sampleJson) = LocalCampusLocationRepository({ json })

  private suspend fun LocalCampusLocationRepository.names(filter: CampusLocationFilter) =
      search(filter).getOrThrow().map { it.name }

  @Test
  fun getAllLocations_parsesEveryFieldInFileOrder() = runTest {
    val all = repo().getAllLocations().getOrThrow()

    assertEquals(listOf("BC 010", "BC 01", "CM 1 121", "CM 2 10", "ZZ 1"), all.map { it.name })
    val library = all[2]
    assertEquals("CM", library.zone)
    assertEquals(CampusLocationType.LIBRARY, library.type)
    assertEquals(1, library.floor)
    assertEquals(Location(46.51, 6.57), library.location)
    assertNull(all[3].floor)
  }

  @Test
  fun getAllLocations_unrecognizedTypeBecomesUnknown() = runTest {
    val last = repo().getAllLocations().getOrThrow().last()

    assertEquals(CampusLocationType.UNKNOWN, last.type)
  }

  @Test
  fun getAllLocations_missingTypeBecomesUnknown() = runTest {
    val json = """[{"name": "A 1", "zone": "A", "latitude": 1.0, "longitude": 2.0}]"""

    assertEquals(CampusLocationType.UNKNOWN, repo(json).getAllLocations().getOrThrow()[0].type)
  }

  @Test
  fun search_emptyFilter_returnsEverything() = runTest {
    val repo = repo()

    assertEquals(
        repo.getAllLocations().getOrThrow(),
        repo.search(CampusLocationFilter()).getOrThrow(),
    )
  }

  @Test
  fun search_blankQuery_isNoTextFilter() = runTest {
    assertEquals(5, repo().names(CampusLocationFilter(query = "   ")).size)
  }

  @Test
  fun search_query_ignoresCaseAndSpaces() = runTest {
    val repo = repo()

    assertEquals(listOf("CM 1 121"), repo.names(CampusLocationFilter(query = "cm1121")))
    assertEquals(listOf("CM 1 121"), repo.names(CampusLocationFilter(query = " Cm 1  121 ")))
  }

  @Test
  fun search_query_matchesPartially() = runTest {
    assertEquals(listOf("CM 1 121", "CM 2 10"), repo().names(CampusLocationFilter(query = "cm")))
  }

  @Test
  fun search_query_ranksExactMatchBeforePartialOnes() = runTest {
    // "BC 010" comes first in the file but only contains "bc01"; "BC 01" is the exact match.
    assertEquals(listOf("BC 01", "BC 010"), repo().names(CampusLocationFilter(query = "BC 01")))
  }

  @Test
  fun search_query_keepsFileOrderWhenThereIsNoExactMatch() = runTest {
    assertEquals(listOf("BC 010", "BC 01"), repo().names(CampusLocationFilter(query = "bc0")))
  }

  @Test
  fun search_zone_isExactAndIgnoresCase() = runTest {
    val repo = repo()

    assertEquals(listOf("CM 1 121", "CM 2 10"), repo.names(CampusLocationFilter(zone = "cm")))
    assertTrue(repo.names(CampusLocationFilter(zone = "C")).isEmpty())
  }

  @Test
  fun search_blankZone_isAnyBuilding() = runTest {
    val repo = repo()

    assertEquals(5, repo.names(CampusLocationFilter(zone = "")).size)
    assertEquals(5, repo.names(CampusLocationFilter(zone = "   ")).size)
  }

  @Test
  fun search_zoneWithSurroundingSpaces_isTrimmed() = runTest {
    assertEquals(
        listOf("CM 1 121", "CM 2 10"),
        repo().names(CampusLocationFilter(zone = " cm ")),
    )
  }

  @Test
  fun search_types_keepsOnlyListedTypes() = runTest {
    val repo = repo()

    assertEquals(
        listOf("CM 1 121", "CM 2 10"),
        repo.names(
            CampusLocationFilter(types = setOf(CampusLocationType.LIBRARY, CampusLocationType.LAB))
        ),
    )
  }

  @Test
  fun search_everyTypeExceptLab_hidesLaboratories() = runTest {
    val allButLab = CampusLocationType.entries.toSet() - CampusLocationType.LAB

    assertFalse("CM 2 10" in repo().names(CampusLocationFilter(types = allButLab)))
  }

  @Test
  fun search_combinesAllCriteriaWithAnd() = runTest {
    val repo = repo()
    val filter =
        CampusLocationFilter(query = "cm", zone = "CM", types = setOf(CampusLocationType.LIBRARY))

    assertEquals(listOf("CM 1 121"), repo.names(filter))
    assertTrue(repo.names(filter.copy(zone = "BC")).isEmpty())
  }

  @Test
  fun search_noMatch_isSuccessfulEmptyList() = runTest {
    val result = repo().search(CampusLocationFilter(query = "nothing"))

    assertTrue(result.isSuccess)
    assertTrue(result.getOrThrow().isEmpty())
  }

  @Test
  fun getZones_returnsDistinctSortedCodes() = runTest {
    assertEquals(listOf("BC", "CM", "ZZ"), repo().getZones().getOrThrow())
  }

  @Test
  fun getZones_codesAreAcceptedByTheZoneFilter() = runTest {
    val repo = repo()

    repo.getZones().getOrThrow().forEach { zone ->
      assertTrue(repo.names(CampusLocationFilter(zone = zone)).isNotEmpty())
    }
  }

  @Test
  fun load_readsTheFileOnlyOnce() = runTest {
    val reads = AtomicInteger()
    val repo =
        LocalCampusLocationRepository({
          reads.incrementAndGet()
          sampleJson
        })

    repo.getAllLocations()
    repo.search(CampusLocationFilter(query = "cm"))
    repo.getZones()

    assertEquals(1, reads.get())
  }

  @Test
  fun load_concurrentFirstCalls_readTheFileOnlyOnce() = runTest {
    val reads = AtomicInteger()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val repo =
        LocalCampusLocationRepository(
            {
              reads.incrementAndGet()
              sampleJson
            },
            dispatcher,
        )

    val calls = List(5) { async { repo.getAllLocations() } }
    advanceUntilIdle()

    assertTrue(calls.all { it.await().isSuccess })
    assertEquals(1, reads.get())
  }

  @Test
  fun malformedJson_returnsFailureOnEveryFunction() = runTest {
    val repo = repo("not json")

    assertTrue(repo.getAllLocations().isFailure)
    assertTrue(repo.search(CampusLocationFilter()).isFailure)
    assertTrue(repo.getZones().isFailure)
  }

  @Test
  fun invalidCoordinates_returnFailure() = runTest {
    val json = """[{"name": "A 1", "zone": "A", "latitude": 123.0, "longitude": 2.0}]"""

    assertTrue(repo(json).getAllLocations().exceptionOrNull() is IllegalArgumentException)
  }

  /** A room without a name cannot be searched or shown, so the whole load must fail. */
  @Test
  fun nullName_returnsFailure() = runTest {
    val json = """[{"name": null, "zone": "A", "latitude": 46.5, "longitude": 6.5}]"""

    assertTrue(repo(json).getAllLocations().isFailure)
  }

  /** A room without coordinates cannot be placed on the map, so the whole load must fail. */
  @Test
  fun nullLatitudeOrLongitude_returnsFailure() = runTest {
    val nullLatitude = """[{"name": "A 1", "zone": "A", "latitude": null, "longitude": 6.5}]"""
    val nullLongitude = """[{"name": "A 1", "zone": "A", "latitude": 46.5, "longitude": null}]"""

    assertTrue(repo(nullLatitude).getAllLocations().isFailure)
    assertTrue(repo(nullLongitude).getAllLocations().isFailure)
  }

  @Test
  fun unreadableFile_returnsFailureWithTheCause() = runTest {
    val repo = LocalCampusLocationRepository({ throw IOException("disk") })

    assertEquals("disk", repo.getAllLocations().exceptionOrNull()?.message)
  }

  @Test
  fun failedLoad_isNotCached_nextCallRetries() = runTest {
    val reads = AtomicInteger()
    val repo =
        LocalCampusLocationRepository({
          if (reads.incrementAndGet() == 1) throw IOException("first try") else sampleJson
        })

    assertTrue(repo.getAllLocations().isFailure)
    assertTrue(repo.getAllLocations().isSuccess)
  }

  @Test
  fun search_query_ranksPrefixMatchesBeforeInnerMatches() = runTest {
    val json =
        """
        [
          {"name": "X CM 1", "zone": "X", "latitude": 1.0, "longitude": 2.0},
          {"name": "CM 1 121", "zone": "CM", "latitude": 1.0, "longitude": 2.0},
          {"name": "CM 1", "zone": "CM", "latitude": 1.0, "longitude": 2.0}
        ]
        """
            .trimIndent()

    assertEquals(
        listOf("CM 1", "CM 1 121", "X CM 1"),
        repo(json).names(CampusLocationFilter(query = "cm 1")),
    )
  }

  @Test
  fun zones_differingOnlyByCase_areListedOnceAndFilterBothRooms() = runTest {
    val json =
        """
        [
          {"name": "A 1", "zone": "CM", "latitude": 1.0, "longitude": 2.0},
          {"name": "A 2", "zone": "cm", "latitude": 1.0, "longitude": 2.0}
        ]
        """
            .trimIndent()
    val repo = repo(json)

    assertEquals(listOf("CM"), repo.getZones().getOrThrow())
    assertEquals(listOf("A 1", "A 2"), repo.names(CampusLocationFilter(zone = "CM")))
  }

  @Test
  fun validRowBeforeInvalidRow_failsTheWholeLoad() = runTest {
    val json =
        """
        [
          {"name": "A 1", "zone": "A", "latitude": 1.0, "longitude": 2.0},
          {"name": "B 1", "zone": "B", "latitude": 999.0, "longitude": 2.0}
        ]
        """
            .trimIndent()
    val repo = repo(json)

    assertTrue(repo.getAllLocations().isFailure)
    assertTrue(repo.getZones().isFailure)
  }

  @Test
  fun cancellation_isRethrownNotWrappedInAFailure() = runTest {
    val repo = LocalCampusLocationRepository({ throw CancellationException("stop") })

    val thrown = runCatching { repo.getAllLocations() }.exceptionOrNull()

    assertTrue(thrown is CancellationException)
  }
}
