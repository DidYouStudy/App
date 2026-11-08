// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [CampusLocationRepository] backed by the `campus_locations.json` file bundled with the app.
 *
 * The file is read and parsed once, on [ioDispatcher], the first time a function is called. The
 * parsed locations, their normalized names and the sorted building codes are then kept in memory,
 * so later calls only filter. A failed load is not cached: the next call tries again, which
 * recovers from a transient read error at the cost of repeating a deterministic failure.
 *
 * @param readJson Returns the full text of the JSON file. It is injected so unit tests do not need
 *   an Android `Context`; the app passes a reader of the raw resource.
 * @param ioDispatcher Dispatcher used to read and parse the file.
 */
class LocalCampusLocationRepository(
    private val readJson: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CampusLocationRepository {

  /** A location with its name already normalized for matching, computed once at load. */
  private class Entry(val location: CampusLocation, val normalizedName: String)

  /** Everything derived from the file, built once. */
  private class Index(val entries: List<Entry>, val zones: List<String>)

  /** Guards [cache] so concurrent first calls load the file only once. */
  private val loadMutex = Mutex()

  /** The loaded data, or `null` until the first successful load. */
  @Volatile private var cache: Index? = null

  override suspend fun getAllLocations(): Result<List<CampusLocation>> =
      load().map { index -> index.entries.map { it.location } }

  override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
      load().map { index -> applyFilter(index.entries, filter) }

  override suspend fun getZones(): Result<List<String>> = load().map { it.zones }

  /**
   * Returns the cached index, loading and parsing the file first if needed. Any failure (unreadable
   * file, malformed JSON, invalid coordinates) is returned as [Result.failure]; cancellation is
   * rethrown so the caller's coroutine still stops.
   */
  private suspend fun load(): Result<Index> {
    cache?.let {
      return Result.success(it)
    }
    return loadMutex.withLock {
      // Another caller may have finished loading while this one waited for the lock.
      cache?.let {
        return@withLock Result.success(it)
      }
      try {
        val index = withContext(ioDispatcher) { buildIndex(parse(readJson())) }
        cache = index
        Result.success(index)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  private companion object {
    /** Unknown keys are ignored so the data file can gain fields without breaking old apps. */
    val json = Json { ignoreUnknownKeys = true }

    /** Parses the JSON text into domain locations. Throws if the text or a value is invalid. */
    fun parse(text: String): List<CampusLocation> =
        json.decodeFromString<List<CampusLocationJson>>(text).map { it.toDomain() }

    /**
     * Normalizes every name once, and lists the building codes sorted, without duplicates that
     * differ only by case (the zone filter ignores case, so such codes would be the same choice).
     */
    fun buildIndex(locations: List<CampusLocation>): Index =
        Index(
            entries = locations.map { Entry(it, normalizeName(it.name)) },
            zones = locations.map { it.zone }.distinctBy { it.lowercase() }.sorted(),
        )

    /**
     * Keeps the locations that match every criterion set in [filter]. With a text query the result
     * is ranked: exact name matches first, then names that start with the text, then the others.
     * The sort is stable, so locations of the same rank keep the order of the data file.
     */
    fun applyFilter(entries: List<Entry>, filter: CampusLocationFilter): List<CampusLocation> {
      val query = filter.query?.let(::normalizeName).orEmpty()
      // A blank zone (e.g. an empty "no building" field) means any building, like null.
      val zone = filter.zone?.trim().orEmpty()
      val matches = entries.filter { entry ->
        (zone.isEmpty() || entry.location.zone.equals(zone, ignoreCase = true)) &&
            (filter.types.isEmpty() || entry.location.type in filter.types) &&
            (query.isEmpty() || entry.normalizedName.contains(query))
      }
      val ranked = if (query.isEmpty()) matches else matches.sortedBy { rank(it, query) }
      return ranked.map { it.location }
    }

    /** 0 for an exact name match, 1 when the name starts with [query], 2 otherwise. */
    fun rank(entry: Entry, query: String): Int =
        when {
          entry.normalizedName == query -> 0
          entry.normalizedName.startsWith(query) -> 1
          else -> 2
        }

    /** Lowercases [text] and removes all whitespace, so "CM 1 121" and "cm1121" compare equal. */
    fun normalizeName(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()
  }
}

/** Shape of one entry of `campus_locations.json`; only used for parsing. */
@Serializable
private class CampusLocationJson(
    val name: String,
    val zone: String,
    val type: String? = null,
    val floor: Int? = null,
    val latitude: Double,
    val longitude: Double,
) {
  /** Maps the file entry to the domain model; an unrecognized type becomes UNKNOWN. */
  fun toDomain(): CampusLocation =
      CampusLocation(
          name = name,
          zone = zone,
          type = CampusLocationType.fromString(type),
          floor = floor,
          location = Location(latitude, longitude),
      )
}
